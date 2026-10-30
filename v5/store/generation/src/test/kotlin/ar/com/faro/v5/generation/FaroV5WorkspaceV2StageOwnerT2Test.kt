package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
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

/** Linux/glibc T2: native SQLite bundled driver must not run on Termux JVM. */
class FaroV5WorkspaceV2StageOwnerT2Test {
    @TempDir lateinit var root: Path

    private fun desc(id: String) = StorageGenerationDescriptor(
        StorageGenerationId(id), "schema-v2-no-cutover", 123L,
    )

    @Test fun freshStageCreatesExactlyFiveHealthyStoresButNoActivePointer() {
        val d = desc("fresh-stage-owner")
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        assertEquals(d, FileStorageGenerationLayout(root).requireStaged(d.generationId).descriptor)
        assertEquals(7L, Files.list(staged.directory).use { it.count() })
        assertTrue(GenerationStoreFiles.WORKSPACE_V2.all {
            Files.size(staged.directory.resolve(it)) >= 100L
        })
        assertEquals(null, FileActiveGenerationPointer(root).read())
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
            assertTrue(graph.verifyHealthy())
            assertEquals(0L, graph.workspace!!.snapshot().revision)
        }
    }

    @Test fun markerBoundV2CannotSilentlyBecomeActiveOrBeRestaged() {
        val d = desc("stage-once")
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.size(staged.directory.resolve(it))
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        }
        assertEquals(before, GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.size(staged.directory.resolve(it))
        })
        assertEquals(null, FileActiveGenerationPointer(root).read())
    }

    @Test fun failureAfterFirstDatabaseLeavesIsolatedPartialStageNotAutoDeleted() {
        val d = desc("partial-stage")
        val owner = FaroV5WorkspaceV2StageOwner(root) { file ->
            if (file == GenerationStoreFiles.REALITY) error("injected-stage-interruption")
        }
        assertThrows(IllegalStateException::class.java) { owner.stageFreshAndVerify(d) }
        val staged = FileStorageGenerationLayout(root).requireStaged(d.generationId)
        assertTrue(Files.size(staged.directory.resolve(GenerationStoreFiles.REALITY)) > 0L)
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertEquals(null, FileActiveGenerationPointer(root).read())
        assertThrows(IllegalStateException::class.java) {
            FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        }
    }

    @Test fun existingLegacyStageCannotBeUpgradedAsNewV2() {
        val d = desc("legacy-first")
        val legacy = FileStorageGenerationLayout(root).stage(d)
        assertThrows(IllegalStateException::class.java) {
            FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        }
        assertFalse(Files.exists(legacy.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertEquals(1L, Files.list(legacy.directory).use { it.count() })
        assertEquals(null, FileActiveGenerationPointer(root).read())
    }
}
