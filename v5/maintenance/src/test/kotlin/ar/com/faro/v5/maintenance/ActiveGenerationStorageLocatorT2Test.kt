package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ActiveGenerationStorageLocatorT2Test {
    @Test
    fun `initial bootstrap creates deterministic active generation and hashed directory`(
        @TempDir tempDir: Path,
    ) {
        val locator = ActiveGenerationStorageLocator(tempDir)

        val first =
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )
        val second =
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )

        assertEquals(first, second)
        assertEquals("initial-generation-v1", locator.activeGenerationId())
        assertEquals(first, locator.activeGenerationDirectory())
        assertTrue(first.startsWith(tempDir.toAbsolutePath().normalize().resolve("generations")))
        assertFalse(first.fileName.toString().contains("initial-generation-v1"))
        GenerationStoreFiles.ALL.forEach { fileName ->
            assertTrue(Files.isRegularFile(first.resolve(fileName)))
        }
    }


    @Test
    fun `interrupted initial bootstrap before descriptor publish is completed safely`(
        @TempDir tempDir: Path,
    ) {
        val initialId = StorageGenerationId("initial-generation-v1")
        val layout = FileStorageGenerationLayout(tempDir)
        val partialDirectory = layout.expectedDirectory(initialId)
        Files.createDirectory(partialDirectory)

        val locator = ActiveGenerationStorageLocator(tempDir)
        val recovered =
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )

        assertEquals(partialDirectory, recovered)
        assertEquals(initialId.value, locator.activeGenerationId())
        GenerationStoreFiles.ALL.forEach { fileName ->
            assertTrue(Files.isRegularFile(recovered.resolve(fileName)))
        }
    }

    @Test
    fun `incomplete initial bootstrap with unexpected file still fails closed`(
        @TempDir tempDir: Path,
    ) {
        val initialId = StorageGenerationId("initial-generation-v1")
        val layout = FileStorageGenerationLayout(tempDir)
        val partialDirectory = layout.expectedDirectory(initialId)
        Files.createDirectory(partialDirectory)
        Files.writeString(partialDirectory.resolve("unexpected.bin"), "not-bootstrap-state")

        val locator = ActiveGenerationStorageLocator(tempDir)

        assertThrows(IllegalStateException::class.java) {
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )
        }
        assertEquals(null, FileActiveGenerationPointer(tempDir).read())
    }

    @Test
    fun `interrupted pristine initial bootstrap publishes the exact staged generation`(
        @TempDir tempDir: Path,
    ) {
        val initialId = StorageGenerationId("initial-generation-v1")
        val layout = FileStorageGenerationLayout(tempDir)
        val staged =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = initialId,
                    schemaSetFingerprint = "schema-v1",
                    createdAtEpochMillis = 0L,
                )
            )
        GenerationAuthorityStoreGraph.open(staged).use { graph ->
            assertTrue(graph.verifyFreshForReset())
        }

        assertEquals(null, FileActiveGenerationPointer(tempDir).read())

        val locator = ActiveGenerationStorageLocator(tempDir)
        val recovered =
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )

        assertEquals(staged.directory, recovered)
        assertEquals(initialId.value, locator.activeGenerationId())
        assertEquals(staged.directory, locator.activeGenerationDirectory())
    }

    @Test
    fun `interrupted bootstrap with competing generation still fails closed`(
        @TempDir tempDir: Path,
    ) {
        val layout = FileStorageGenerationLayout(tempDir)
        val initial =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = StorageGenerationId("initial-generation-v1"),
                    schemaSetFingerprint = "schema-v1",
                    createdAtEpochMillis = 0L,
                )
            )
        GenerationAuthorityStoreGraph.open(initial).use { graph ->
            assertTrue(graph.verifyFreshForReset())
        }
        layout.stage(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("competing-generation"),
                schemaSetFingerprint = "schema-v1",
                createdAtEpochMillis = 1L,
            )
        )

        val locator = ActiveGenerationStorageLocator(tempDir)

        assertThrows(IllegalStateException::class.java) {
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )
        }
        assertEquals(null, FileActiveGenerationPointer(tempDir).read())
    }

    @Test
    fun `missing pointer with existing generation fails closed instead of reinitializing`(
        @TempDir tempDir: Path,
    ) {
        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("stale-generation"),
                schemaSetFingerprint = "schema-v1",
                createdAtEpochMillis = 1L,
            )
        )
        val locator = ActiveGenerationStorageLocator(tempDir)

        assertThrows(IllegalStateException::class.java) {
            locator.ensureInitialGenerationAndGetDirectory(
                "initial-generation-v1",
                "schema-v1",
            )
        }
    }

    @Test
    fun `locator follows durable pointer switch instead of caching old path`(
        @TempDir tempDir: Path,
    ) {
        val locator = ActiveGenerationStorageLocator(tempDir)
        val oldDir =
            locator.ensureInitialGenerationAndGetDirectory(
                "generation-old",
                "schema-v1",
            )

        val target = StorageGenerationId("generation-new")
        val layout = FileStorageGenerationLayout(tempDir)
        val staged =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = target,
                    schemaSetFingerprint = "schema-v1",
                    createdAtEpochMillis = 20L,
                )
            )
        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(StorageGenerationId("generation-old"), target)

        assertEquals("generation-new", locator.activeGenerationId())
        assertEquals(staged.directory, locator.activeGenerationDirectory())
        assertNotEquals(oldDir, locator.activeGenerationDirectory())
    }
}
