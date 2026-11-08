package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Five NEW REAL SQLite cases, not the previously certified C2A fixture. */
class FaroV5Planning8DevStageJournalOwnerT2Test {
    @TempDir lateinit var root: Path
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val op = MaintenanceOperationId("a5-dev-stage8-journal-1")
    private val target = StorageGenerationDescriptor(
        StorageGenerationId("a5-dev-stage8-target-1"), "a5-dev-stage8-frozen", 10L,
    )

    private fun activeSeven(): ActiveGenerationPointer {
        val old = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(
                StorageGenerationId("a5-dev-existing7"), "old-schema7", 1L,
            )
        )
        return checkNotNull(pointer.compareAndSet(null, old.descriptor.generationId))
    }

    @Test fun freshReality8JournalIsValidatedWithFiveStoresAndOldPointerIntact() {
        val active = activeSeven()
        val oldReality = layout.requireStaged(active.generationId).directory
            .resolve(GenerationStoreFiles.REALITY)
        val original = Files.readAllBytes(oldReality)
        val staged = FaroV5Planning8DevStageJournalOwner(root, lease)
            .prepareFresh(op, target, active)
        assertEquals(target, staged.descriptor)
        assertEquals(active, pointer.read())
        assertArrayEquals(original, Files.readAllBytes(oldReality))
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)!!.phase)
        assertFalse(journal.read(op)!!.materialWritesAccepted)
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged).use {
            assertNotNull(it.workspace)
            assertEquals(8, it.reality.maintenance.inspect().schemaVersion)
            assertTrue(it.verifyHealthy())
        }
    }

    @Test fun secondPreparationCannotAdoptExistingTargetOrJournal() {
        val active = activeSeven()
        val owner = FaroV5Planning8DevStageJournalOwner(root, lease)
        val staged = owner.prepareFresh(op, target, active)
        val file = staged.directory.resolve(GenerationStoreFiles.REALITY)
        val before = Files.readAllBytes(file)
        assertThrows(IllegalStateException::class.java) {
            owner.prepareFresh(op, target, active)
        }
        assertThrows(IllegalStateException::class.java) {
            owner.prepareFresh(MaintenanceOperationId("a5-foreign-new-op"), target, active)
        }
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals(active, pointer.read())
    }

    @Test fun interruptedFreshSQLiteRetainsPreparingJournalAndQuarantinedResidue() {
        val active = activeSeven()
        val owner = FaroV5Planning8DevStageJournalOwner(
            root, lease,
            { d ->
                FaroV5WorkspaceV2StageOwner(root) { file ->
                    if (file == GenerationStoreFiles.REALITY)
                        error("injected crash inside fresh materialization")
                }.stageFreshPlanning8AndVerify(d)
            }, {}
        )
        assertThrows(IllegalStateException::class.java) {
            owner.prepareFresh(op, target, active)
        }
        assertEquals(BootstrapPhase.PREPARING, journal.read(op)!!.phase)
        assertEquals(active, pointer.read())
        assertTrue(Files.exists(layout.requireStaged(target.generationId)
            .directory.resolve(GenerationStoreFiles.REALITY)))
        assertThrows(IllegalStateException::class.java) {
            FaroV5Planning8DevStageJournalOwner(root, lease)
                .recoverValidatedStage(op, target, active)
        }
    }

    @Test fun crashAfterStagedRecoversExactlyAndIdempotentlyWithoutRestaging() {
        val active = activeSeven()
        val crash = FaroV5Planning8DevStageJournalOwner(
            root, lease,
            { d -> FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(d) },
            { error("injected crash after STAGED journal commit") },
        )
        assertThrows(IllegalStateException::class.java) {
            crash.prepareFresh(op, target, active)
        }
        assertEquals(BootstrapPhase.STAGED, journal.read(op)!!.phase)
        val existing = layout.requireStaged(target.generationId)
        val oldBytes = Files.readAllBytes(existing.directory.resolve(GenerationStoreFiles.REALITY))
        val restarted = FaroV5Planning8DevStageJournalOwner(root, lease)
        assertEquals(existing, restarted.recoverValidatedStage(op, target, active))
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)!!.phase)
        assertEquals(existing, restarted.recoverValidatedStage(op, target, active))
        assertArrayEquals(oldBytes, Files.readAllBytes(
            existing.directory.resolve(GenerationStoreFiles.REALITY)))
        assertEquals(active, pointer.read())
    }

    @Test fun pointerDriftDuringStageLeavesOnlyPreparingAndNeverPromotesTarget() {
        val active = activeSeven()
        val foreign = StorageGenerationId("a5-dev-foreign-active-id")
        val raced = FaroV5Planning8DevStageJournalOwner(
            root, lease,
            { d ->
                FaroV5WorkspaceV2StageOwner(root) { file ->
                    if (file == GenerationStoreFiles.REALITY)
                        checkNotNull(pointer.compareAndSet(active.generationId, foreign))
                }.stageFreshPlanning8AndVerify(d)
            }, {},
        )
        assertThrows(IllegalStateException::class.java) {
            raced.prepareFresh(op, target, active)
        }
        assertEquals(foreign, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.PREPARING, journal.read(op)!!.phase)
        assertNotEquals(target.generationId, pointer.read()!!.generationId)
        assertThrows(IllegalStateException::class.java) {
            FaroV5Planning8DevStageJournalOwner(root, lease)
                .recoverValidatedStage(op, target, active)
        }
    }
}
