package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * C128 F1+F2: true native five-SQLite-store RESTORE through the actual
 * Maintenance pointer/journal/replay coordinator. No Android device, no
 * production V2 certification flag, no mock store health.
 */
class FaroV5FiveOwnerRestoreLifecycleT2Test {
    @TempDir lateinit var root: Path

    private val old = StorageGenerationId("c128-legacy-active")
    private val target = StorageGenerationId("c128-five-owner-restore")
    private val op = MaintenanceOperationId("c128-restore")
    private val gate = SingleProcessMaterialWriteGate()
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val replay by lazy { FileGenerationReplayFence(root) }
    private val planVerifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }

    private fun descriptor(id: StorageGenerationId) =
        StorageGenerationDescriptor(id, "c128-frozen-schema", 4L)

    private fun materializeLegacy(path: Path) {
        RealityStore(path.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(path.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(path.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(path.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
    }

    private fun materializeFive(path: Path) {
        materializeLegacy(path)
        ConversationWorkspaceStore(path.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
    }

    private fun prepareValidatedRestore() {
        val previous = layout.stage(descriptor(old))
        materializeLegacy(previous.directory)
        val fresh = layout.stageWorkspaceV2(descriptor(target))
        materializeFive(fresh.directory)
        assertNotNull(pointer.compareAndSet(null, old))
        var entry = BootstrapJournalEntry(
            op, MaintenanceOperationKind.RESTORE, old, target, BootstrapPhase.PREPARING
        )
        journal.create(entry)
        for (phase in listOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
            val next = BootstrapTransitionPolicy.transition(entry, phase)
            assertTrue(journal.compareAndSet(entry, next))
            entry = next
        }
    }

    private fun openVerifiedGeneration(id: StorageGenerationId): Boolean {
        check(pointer.read()?.generationId == id)
        val staged = layout.requireStaged(id)
        val graph = if (id == old)
            FaroV5GenerationStoreGraph.open(staged.directory, id.value)
        else FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
            staged, planVerifier, gate
        )
        return graph.use {
            it.generationId == id.value &&
                (id != target || it.workspace?.snapshot()?.revision == 0L) &&
                it.verifyHealthy() &&
                pointer.read()?.generationId == id
        }
    }

    private fun coordinator(
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        targetUnhealthy: Boolean = false,
        projections: MutableList<StorageGenerationId>,
        activations: MutableList<StorageGenerationId>,
    ): ValidatedGenerationActivationCoordinator =
        ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = MaterialWriteGateQuiescence(gate),
            generationSwitch = GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                assertEquals(MaintenanceOperationKind.RESTORE, kind)
                activations.add(id)
                if (id == target && targetUnhealthy) false
                else openVerifiedGeneration(id)
            },
            replayFence = replay,
            projections = GenerationProjectionReconciliationPort { kind, id ->
                if (id == target) {
                    assertTrue(replay.isInstalled(kind, target),
                        "replay fence must be durable before target OS projection")
                }
                assertEquals(id, pointer.read()!!.generationId)
                projections.add(id)
            },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                if (kind != MaintenanceOperationKind.RESTORE || id != target) false
                else {
                    val stage = layout.requireStaged(id)
                    FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                        stage, planVerifier, gate
                    ).use { it.workspace != null && it.verifyHealthy() }
                }
            }
        )

    @Test fun fullRestoreOpensRealFiveOwnerGraphAndFencesBeforeProjectionAndWrites() {
        prepareValidatedRestore()
        val activations = mutableListOf<StorageGenerationId>()
        val projections = mutableListOf<StorageGenerationId>()
        val result = coordinator(projections = projections, activations = activations).activate(op)
        assertEquals(
            GenerationActivationResult.Activated(op, MaintenanceOperationKind.RESTORE, target, false),
            result
        )
        assertEquals(target, pointer.read()!!.generationId)
        val record = journal.read(op)!!
        assertEquals(BootstrapPhase.COMPLETED, record.phase)
        assertTrue(record.materialWritesAccepted)
        assertTrue(replay.isInstalled(MaintenanceOperationKind.RESTORE, target))
        assertEquals(listOf(target), activations)
        assertEquals(listOf(target), projections)
        assertTrue(openVerifiedGeneration(target))
    }

    @Test fun crashAfterPointerPublicationRecoversJournalFiveStoresAndReplayExactlyOnce() {
        prepareValidatedRestore()
        val activations = mutableListOf<StorageGenerationId>()
        val projections = mutableListOf<StorageGenerationId>()
        val crashing = coordinator(
            fault = { point ->
                if (point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
                    error("C128 injected crash after durable pointer")
            },
            projections = projections,
            activations = activations,
        )
        assertThrows(IllegalStateException::class.java) { crashing.activate(op) }
        assertEquals(target, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(op)!!.phase)
        assertFalse(replay.isInstalled(MaintenanceOperationKind.RESTORE, target))

        val recovered = coordinator(
            projections = projections,
            activations = activations,
        ).recover(op)
        assertEquals(
            GenerationActivationResult.Activated(op, MaintenanceOperationKind.RESTORE, target, true),
            recovered
        )
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)!!.phase)
        assertTrue(journal.read(op)!!.materialWritesAccepted)
        assertTrue(replay.isInstalled(MaintenanceOperationKind.RESTORE, target))
        assertEquals(listOf(target), activations)
        assertEquals(listOf(target), projections)
        assertTrue(openVerifiedGeneration(target))
    }

    @Test fun failedPostSwitchHealthRestoresRealLegacyGraphBeforeTargetWrites() {
        prepareValidatedRestore()
        val activations = mutableListOf<StorageGenerationId>()
        val projections = mutableListOf<StorageGenerationId>()
        val result = coordinator(
            targetUnhealthy = true,
            projections = projections,
            activations = activations,
        ).activate(op)
        assertTrue(result is GenerationActivationResult.RolledBack)
        val rollback = result as GenerationActivationResult.RolledBack
        assertEquals(GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED, rollback.failedStep)
        assertEquals(old, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(op)!!.phase)
        assertFalse(journal.read(op)!!.materialWritesAccepted)
        assertFalse(replay.isInstalled(MaintenanceOperationKind.RESTORE, target))
        assertEquals(listOf(target, old), activations)
        assertEquals(listOf(old), projections)
        assertTrue(openVerifiedGeneration(old))
    }
}
