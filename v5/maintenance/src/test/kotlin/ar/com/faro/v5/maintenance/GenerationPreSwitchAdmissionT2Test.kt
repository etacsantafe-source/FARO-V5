package ar.com.faro.v5.maintenance

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** V2-like unsupported host admission must deny BEFORE pointer publish. */
class GenerationPreSwitchAdmissionT2Test {
    @TempDir lateinit var root: Path
    private val source = StorageGenerationId("c120-live-v1")
    private val target = StorageGenerationId("c120-unsupported-v2")
    private val operation = MaintenanceOperationId("c120-op")

    private class Hooks {
        var opened = 0
        var replayed = 0
        var projected = 0
        var checks = 0
    }

    private fun setup(
        phase: BootstrapPhase,
        admitted: Boolean = false,
    ): Triple<ValidatedGenerationActivationCoordinator, FileActiveGenerationPointer, Pair<FileBootstrapJournal, Hooks>> {
        val layout = FileStorageGenerationLayout(root)
        layout.stage(StorageGenerationDescriptor(source, "v1", 1L))
        layout.stage(StorageGenerationDescriptor(target, "v2-not-active", 2L))
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.compareAndSet(null, source) != null)
        val journal = FileBootstrapJournal(root)
        journal.create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE,
            source, target, phase,
        ))
        val hooks = Hooks()
        val gate = GenerationPreSwitchAdmissionPort { _, generation ->
            hooks.checks++
            generation == target && admitted
        }
        val coordinator = ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = MaterialWriteQuiescencePortFromBlock,
            generationSwitch = GenerationSwitchCoordinator(journal, pointer, layout),
            runtime = GenerationRuntimeActivationPort { _, _ -> hooks.opened++; true },
            replayFence = GenerationReplayFencePort { _, _ -> hooks.replayed++ },
            projections = GenerationProjectionReconciliationPort { _, _ -> hooks.projected++ },
            preSwitchAdmission = gate,
        )
        return Triple(coordinator, pointer, journal to hooks)
    }

    @Test fun validatedTargetDeniedWithoutJournalOrPointerChange() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.VALIDATED)
        assertEquals(
            GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED
            ), coordinator.activate(operation)
        )
        assertEquals(source, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.VALIDATED, other.first.read(operation)!!.phase)
        assertFalse(other.first.read(operation)!!.materialWritesAccepted)
        assertEquals(1, other.second.checks)
        assertEquals(0, other.second.opened + other.second.replayed + other.second.projected)
    }

    @Test fun switchPreparedStillPrePointerDeniedWithoutAnySideEffects() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.SWITCH_PREPARED)
        assertEquals(
            GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED
            ), coordinator.recover(operation)
        )
        assertEquals(source, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, other.first.read(operation)!!.phase)
        assertEquals(0, other.second.opened + other.second.replayed + other.second.projected)
    }

    @Test fun recoveryScanCannotPromoteUnsupportedValidatedGeneration() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.VALIDATED)
        assertEquals(
            listOf(GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED
            )), coordinator.recoverPending()
        )
        assertEquals(source, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.VALIDATED, other.first.read(operation)!!.phase)
        assertEquals(0, other.second.opened + other.second.replayed + other.second.projected)
    }

    @Test fun postSwitchHealthVerifiedHostDowngradeRollsBackBeforeWriteFence() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.HEALTH_VERIFIED)
        check(pointer.compareAndSet(source, target) != null)
        assertEquals(
            GenerationActivationResult.RolledBack(
                operation,
                MaintenanceOperationKind.RESTORE,
                source,
                GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            ), coordinator.recover(operation)
        )
        assertEquals(source, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, other.first.read(operation)!!.phase)
        assertFalse(other.first.read(operation)!!.materialWritesAccepted)
        assertEquals(1, other.second.opened)
        assertEquals(1, other.second.projected)
        assertEquals(0, other.second.replayed)
    }

    @Test fun completedButUnfencedCannotResumeWritesWithoutHostSupport() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.COMPLETED)
        check(pointer.compareAndSet(source, target) != null)
        assertEquals(
            GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED
            ), coordinator.recover(operation)
        )
        assertEquals(target, pointer.read()!!.generationId)
        assertFalse(other.first.read(operation)!!.materialWritesAccepted)
        assertEquals(0, other.second.opened + other.second.replayed + other.second.projected)
    }

    @Test fun completedPreviouslyFencedStillRequiresCurrentHostCompatibility() {
        val (coordinator, pointer, other) = setup(BootstrapPhase.COMPLETED)
        check(pointer.compareAndSet(source, target) != null)
        val prior = other.first.read(operation)!!
        check(other.first.compareAndSet(prior, BootstrapTransitionPolicy.acceptMaterialWrites(prior)))
        assertEquals(
            GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED
            ), coordinator.recover(operation)
        )
        assertEquals(target, pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.COMPLETED, other.first.read(operation)!!.phase)
        assertEquals(0, other.second.opened + other.second.replayed + other.second.projected)
    }

    private object MaterialWriteQuiescencePortFromBlock : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T = block()
    }
}
