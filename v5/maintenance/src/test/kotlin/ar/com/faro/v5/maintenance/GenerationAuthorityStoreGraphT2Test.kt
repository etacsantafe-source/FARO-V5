package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GenerationAuthorityStoreGraphT2Test {
    @Test
    fun `authority graph opens all canonical stores in one staged generation`(
        @TempDir tempDir: Path,
    ) {
        val layout = FileStorageGenerationLayout(tempDir)
        val staged =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = StorageGenerationId("generation-authority-test"),
                    schemaSetFingerprint = "schema-v1",
                    createdAtEpochMillis = 10L,
                )
            )

        GenerationAuthorityStoreGraph.open(staged).use { graph ->
            assertEquals(staged.descriptor.generationId, graph.generationId)
            assertEquals(staged.directory.toAbsolutePath().normalize(), graph.directory)
            assertTrue(graph.verifyHealthy())
            assertTrue(graph.verifyFreshForReset())
        }

        val canonicalFiles =
            listOf(
                GenerationStoreFiles.REALITY,
                GenerationStoreFiles.COMMERCIAL_EVIDENCE,
                GenerationStoreFiles.WEB_EVIDENCE,
                GenerationStoreFiles.AUXILIARY,
            )
        assertEquals(4, canonicalFiles.toSet().size)
        canonicalFiles.forEach { fileName ->
            assertTrue(Files.isRegularFile(staged.directory.resolve(fileName)), fileName)
        }
    }

    @Test
    fun `existing authority graph fails closed when a canonical store file is missing`(
        @TempDir tempDir: Path,
    ) {
        val layout = FileStorageGenerationLayout(tempDir)
        val staged =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = StorageGenerationId("generation-missing-store"),
                    schemaSetFingerprint = "schema-v1",
                    createdAtEpochMillis = 11L,
                )
            )
        GenerationAuthorityStoreGraph.open(staged).use { graph ->
            assertTrue(graph.verifyHealthy())
        }
        Files.delete(staged.directory.resolve(GenerationStoreFiles.WEB_EVIDENCE))

        assertThrows(IllegalStateException::class.java) {
            GenerationAuthorityStoreGraph.openExisting(staged).close()
        }
        assertTrue(
            !Files.exists(staged.directory.resolve(GenerationStoreFiles.WEB_EVIDENCE))
        )
    }
}
