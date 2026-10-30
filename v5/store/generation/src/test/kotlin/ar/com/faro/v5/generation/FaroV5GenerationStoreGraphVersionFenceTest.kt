package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Shared production generation graph pre-open guard. Tests must not initialize SQLite JNI. */
class FaroV5GenerationStoreGraphVersionFenceTest {
    @TempDir lateinit var root: Path

    private fun descriptor(id: String) = StorageGenerationDescriptor(
        generationId = StorageGenerationId(id),
        schemaSetFingerprint = "schema-v2-test",
        createdAtEpochMillis = 1L,
    )

    @Test fun fullyMaterializedV2FilesCannotOpenThroughLegacyAppGraph() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stageWorkspaceV2(descriptor("v2-marked-app"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.createFile(staged.directory.resolve(name))
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.open(staged.directory, staged.descriptor.generationId.value)
        }
        assertEquals(7L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun orphanWorkspaceDbCannotTriggerLegacyAppGraphOpen() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stage(descriptor("orphan-db-app"))
        GenerationStoreFiles.LEGACY_V1.forEach { name ->
            Files.createFile(staged.directory.resolve(name))
        }
        Files.createFile(staged.directory.resolve(GenerationStoreFiles.WORKSPACE))
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.open(staged.directory, staged.descriptor.generationId.value)
        }
        assertEquals(6L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun orphanWorkspaceWalCannotTriggerLegacyAppGraphOpen() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stage(descriptor("orphan-wal-app"))
        GenerationStoreFiles.LEGACY_V1.forEach { name ->
            Files.createFile(staged.directory.resolve(name))
        }
        val wal = Files.writeString(staged.directory.resolve("workspace.db-wal"), "original")
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.open(staged.directory, staged.descriptor.generationId.value)
        }
        assertTrue(Files.isRegularFile(wal))
        assertEquals("original", Files.readString(wal))
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun preOpenRefusesMissingDirectoryWithoutCreatingAnything() {
        val missing = root.resolve("absent")
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.open(missing, "id")
        }
        assertFalse(Files.exists(missing))
    }
    @Test fun emptyV2PlaceholdersAreRejectedBeforeOpeningAnySQLite() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stageWorkspaceV2(descriptor("empty-v2-placeholder"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.createFile(staged.directory.resolve(name))
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertTrue(GenerationStoreFiles.WORKSPACE_V2.all {
            Files.size(staged.directory.resolve(it)) == 0L
        })
    }

    @Test fun nonSqliteV2StoreContentsAreRejectedWithoutImplicitMigration() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stageWorkspaceV2(descriptor("malformed-v2-legacy"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.write(staged.directory.resolve(name), ByteArray(200) { 42 })
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertTrue(GenerationStoreFiles.WORKSPACE_V2.all {
            Files.size(staged.directory.resolve(it)) == 200L
        })
    }
}
