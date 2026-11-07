package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N033C2B2 readiness-only negatives live outside N033C1's immutable 6-test suite. */
class FaroV5ActiveReality8HostReadinessT2Test {
    @TempDir lateinit var root: Path
    private val shared = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private fun fresh(id: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "n033c2b2-staged", 2026L),
        )
    private fun planning8(id: String): StagedStorageGeneration = fresh(id).also { staged ->
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged).use { graph ->
            assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
            assertTrue(graph.verifyHealthy())
        }
    }
    private fun activate(stage: StagedStorageGeneration): Long =
        checkNotNull(FileActiveGenerationPointer(root)
            .compareAndSet(null, stage.descriptor.generationId)).sequence

    @Test fun hostReadinessBooleanAcceptsOnlyHealthyExactActiveReality8() {
        val stage = planning8("c2b2-host-ready")
        val pinned = activate(stage)
        val pointerBefore = FileActiveGenerationPointer(root).read()
        assertTrue(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, stage, pinned, verifier, shared,
        ))
        assertEquals(pointerBefore, FileActiveGenerationPointer(root).read())
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, stage, pinned + 1L, verifier, shared,
        ))
    }

    @Test fun hostReadinessRejectsSchemaSevenAndStaleAbaWithoutMigration() {
        val legacy = fresh("c2b2-host-schema7")
        val before = Files.readAllBytes(legacy.directory.resolve(GenerationStoreFiles.REALITY))
        val first = activate(legacy)
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, legacy, first, verifier, shared,
        ))
        assertTrue(before.contentEquals(
            Files.readAllBytes(legacy.directory.resolve(GenerationStoreFiles.REALITY))
        ))
        val foreign = StorageGenerationId("c2b2-foreign")
        val pointer = FileActiveGenerationPointer(root)
        assertNotNull(pointer.compareAndSet(legacy.descriptor.generationId, foreign))
        assertNotNull(pointer.compareAndSet(foreign, legacy.descriptor.generationId))
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, legacy, first, verifier, shared,
        ))
    }

}
