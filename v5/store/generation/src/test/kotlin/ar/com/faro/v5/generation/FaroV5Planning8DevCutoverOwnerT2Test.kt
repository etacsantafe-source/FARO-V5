package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** A5 new seam: real stage-journal owner feeds canonical switcher. */
class FaroV5Planning8DevCutoverOwnerT2Test {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val lease = MaterialWriteGateQuiescence(gate)
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val replay by lazy { FileGenerationReplayFence(root) }
    private val op = MaintenanceOperationId("a5-real-dev-journal-cutover")
    private val descriptor = StorageGenerationDescriptor(
        StorageGenerationId("a5-dev-reality8-validated-target"), "dev-a5-stage8-v1", 100L,
    )

    private fun preparedFromRealA5Owner(): ActiveGenerationPointer {
        val old = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(
                StorageGenerationId("a5-dev-active-seven"), "a5-existing7", 1L,
            )
        )
        val before = checkNotNull(pointer.compareAndSet(null, old.descriptor.generationId))
        FaroV5Planning8DevStageJournalOwner(root, lease)
            .prepareFresh(op, descriptor, before)
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)!!.phase)
        return before
    }

    private fun cutover(): FaroV5Planning8DevCutoverOwner =
        FaroV5Planning8DevCutoverOwner(
            root, gate, lease, verifier, certifiedHost = true,
        )

    @Test fun producerJournalPromotesFiveRealStoresAndFencesBeforeAcceptingWrites() {
        val before = preparedFromRealA5Owner()
        val oldFile = layout.requireStaged(before.generationId).directory
            .resolve(GenerationStoreFiles.REALITY)
        val priorBytes = Files.readAllBytes(oldFile)
        val result = cutover().activateValidated(op, descriptor, before)
        assertTrue(result is GenerationActivationResult.Activated)
        assertEquals(descriptor.generationId, pointer.read()!!.generationId)
        assertEquals(before.sequence + 1L, pointer.read()!!.sequence)
        assertArrayEquals(priorBytes, Files.readAllBytes(oldFile))
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)!!.phase)
        assertTrue(journal.read(op)!!.materialWritesAccepted)
        assertTrue(replay.isInstalled(MaintenanceOperationKind.RESTORE, descriptor.generationId))
        FaroV5GenerationStoreGraph.openActivePlanning8ForCertifiedHost(
            root, layout.requireStaged(descriptor.generationId),
            pointer.read()!!.sequence, verifier, gate, v2HostCertified = true,
        ).use { graph ->
            assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
            assertNotNull(graph.workspace)
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun restartAfterPointerPublicationReconcilesSameJournalAndInstallsFence() {
        val before = preparedFromRealA5Owner()
        val crashing = FaroV5Planning8DevCutoverOwner(
            root, gate, lease, verifier, true,
            { point ->
                if (point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
                    error("injected crash after published Reality8 pointer")
            },
            { true },
        )
        assertThrows(IllegalStateException::class.java) {
            crashing.activateValidated(op, descriptor, before)
        }
        assertEquals(descriptor.generationId, pointer.read()!!.generationId)
        assertEquals(before.sequence + 1L, pointer.read()!!.sequence)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(op)!!.phase)
        assertFalse(replay.isInstalled(MaintenanceOperationKind.RESTORE, descriptor.generationId))
        val recovered = cutover().recover(op, descriptor, before)
        assertTrue(recovered is GenerationActivationResult.Activated)
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)!!.phase)
        assertTrue(replay.isInstalled(MaintenanceOperationKind.RESTORE, descriptor.generationId))
        assertEquals(before.sequence + 1L, pointer.read()!!.sequence)
    }

    @Test fun unhealthyActiveEightRollsBackToPreviousRealitySevenBeforeAnyWrites() {
        val before = preparedFromRealA5Owner()
        val unhealthy = FaroV5Planning8DevCutoverOwner(
            root, gate, lease, verifier, true, {}, { false },
        )
        val result = unhealthy.activateValidated(op, descriptor, before)
        assertTrue(result is GenerationActivationResult.RolledBack)
        assertEquals(before.generationId, pointer.read()!!.generationId)
        assertEquals(before.sequence + 2L, pointer.read()!!.sequence)
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(op)!!.phase)
        assertFalse(journal.read(op)!!.materialWritesAccepted)
        assertFalse(replay.isInstalled(MaintenanceOperationKind.RESTORE, descriptor.generationId))
        FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
            layout.requireStaged(before.generationId), verifier, gate,
        ).use { graph ->
            assertEquals(7, graph.reality.maintenance.inspect().schemaVersion)
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun staleAbaPointerAndUncertifiedHostBothFailBeforeAnySwitch() {
        val before = preparedFromRealA5Owner()
        val blocked = FaroV5Planning8DevCutoverOwner(
            root, gate, lease, verifier, certifiedHost = false,
        )
        assertThrows(IllegalStateException::class.java) {
            blocked.activateValidated(op, descriptor, before)
        }
        val foreign = StorageGenerationId("a5-dev-temp-foreign")
        checkNotNull(pointer.compareAndSet(before.generationId, foreign))
        val afterAba = checkNotNull(pointer.compareAndSet(foreign, before.generationId))
        assertNotEquals(before, afterAba)
        assertThrows(IllegalStateException::class.java) {
            cutover().activateValidated(op, descriptor, before)
        }
        assertEquals(afterAba, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)!!.phase)
        assertFalse(replay.isInstalled(MaintenanceOperationKind.RESTORE, descriptor.generationId))
    }
}
