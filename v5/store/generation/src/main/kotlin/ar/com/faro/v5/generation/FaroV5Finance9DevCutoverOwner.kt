package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationPreSwitchAdmissionPort
import ar.com.faro.v5.maintenance.GenerationProjectionReconciliationPort
import ar.com.faro.v5.maintenance.GenerationRuntimeActivationPort
import ar.com.faro.v5.maintenance.GenerationSwitchCoordinator
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.ValidatedGenerationActivationCoordinator
import java.nio.file.Path

/**
 * Maintenance for an isolated, single-process Finance DEV root. The caller owns
 * the exclusive root lease, closes the prior session, and pins the trusted origin
 * durably before starting. Only maintenance results leave this owner.
 */
internal class FaroV5Finance9DevCutoverOwner(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val planVerifier: PreparedPlanEnvelopeVerificationPort,
    private val assertExclusiveDevHost: () -> Unit,
    private val fault: (GenerationSwitchFaultPoint) -> Unit = {},
    private val targetRuntimeHealthy: () -> Boolean = { true },
    private val auxiliary4: Boolean = false,
) {
    fun activateValidated(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
    ): GenerationActivationResult = execute(operationId, staged, origin, recovery = false)

    fun recover(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
    ): GenerationActivationResult = execute(operationId, staged, origin, recovery = true)

    private fun execute(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        recovery: Boolean,
    ): GenerationActivationResult = gate.withQuiescedMaterialWrites {
        assertExclusiveDevHost()
        val targetId = staged.descriptor.generationId
        check(targetId != origin.generationId)
        val target = ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), targetId)
        val rollback = ActiveGenerationPointer(Math.addExact(origin.sequence, 2L), origin.generationId)
        val pointer = FileActiveGenerationPointer(root)
        val journal = FileBootstrapJournal(root)
        val layout = FileStorageGenerationLayout(root)
        val canonical = layout.requireStaged(targetId)
        check(canonical.descriptor == staged.descriptor &&
            canonical.directory == staged.directory.toAbsolutePath().normalize())
        val record = checkNotNull(journal.read(operationId))
        check(record.kind == MaintenanceOperationKind.RESTORE &&
            record.previousGenerationId == origin.generationId && record.targetGenerationId == targetId)
        check(journal.pending().all { it == record })

        val observed = pointer.read()
        if (!recovery) check(record.phase == BootstrapPhase.VALIDATED && observed == origin)
        check(when (record.phase) {
            BootstrapPhase.VALIDATED -> observed == origin
            BootstrapPhase.SWITCH_PREPARED -> observed == origin || observed == target
            BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED,
            BootstrapPhase.COMPLETED -> observed == target
            BootstrapPhase.ROLLBACK_PREPARED -> observed == target || observed == rollback
            BootstrapPhase.ROLLED_BACK_POST_SWITCH, BootstrapPhase.ROLLBACK_VERIFIED -> observed == rollback
            else -> false
        }) { "Finance9 cutover journal and full pointer do not agree" }

        val quiescence = MaterialWriteGateQuiescence(gate)
        if (record.phase == BootstrapPhase.VALIDATED) {
            check(FaroV5Finance9DevCutoverPreflight(root, quiescence)
                .inspect(operationId, canonical, origin, auxiliary4) ==
                FaroV5Finance9DevCutoverPreflight.Observation.OBSERVED_SEALED_STAGED9_ONLY)
        }

        val fence = FileGenerationReplayFence(root)
        check(!fence.isInstalled(MaintenanceOperationKind.MIGRATION, targetId))
        val replayed = fence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)
        if (replayed) {
            check(observed == target && record.phase in setOf(
                BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED,
            )) { "Finance9 replay fence conflicts with this cutover" }
        }
        if (record.materialWritesAccepted) check(replayed)

        fun checkActive(expected: ActiveGenerationPointer): Boolean {
            assertExclusiveDevHost()
            check(pointer.read() == expected)
            val candidate = layout.requireStaged(expected.generationId)
            val isTarget = expected == target
            if (isTarget) check(targetRuntimeHealthy() && candidate == canonical)
            val finance9 = isTarget || auxiliary4
            val graph = when {
                isTarget && auxiliary4 -> FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Auxiliary4Dev(
                    root, candidate, expected.sequence, planVerifier, gate,
                )
                finance9 -> FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Dev(
                    root, candidate, expected.sequence, planVerifier, gate,
                )
                else -> FaroV5GenerationStoreGraph.reopenVerifiedActivePlanning8(
                    root, candidate, expected.sequence, planVerifier, gate,
                )
            }
            return graph.use {
                it.workspace != null && it.reality.maintenance.inspect().schemaVersion ==
                    (if (finance9) 9 else 8) && it.verifyHealthy() && pointer.read() == expected
            }
        }

        // Once a replay fence exists, refuse degraded health before entering any
        // code path that could interpret it as a new pre-fence rollback.
        if (replayed && !record.materialWritesAccepted) check(checkActive(target))

        val coordinator = ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = quiescence,
            generationSwitch = GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                kind == MaintenanceOperationKind.RESTORE && when (id) {
                    targetId -> checkActive(target)
                    origin.generationId -> checkActive(rollback)
                    else -> false
                }
            },
            replayFence = fence,
            projections = GenerationProjectionReconciliationPort { _, _ -> Unit },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                kind == MaintenanceOperationKind.RESTORE && id == targetId &&
                    when (pointer.read()) {
                        origin -> {
                            assertExclusiveDevHost()
                            val pending = checkNotNull(journal.read(operationId))
                            check(pending.previousGenerationId == origin.generationId &&
                                pending.targetGenerationId == targetId &&
                                !pending.materialWritesAccepted &&
                                pending.phase in setOf(BootstrapPhase.VALIDATED, BootstrapPhase.SWITCH_PREPARED) &&
                                journal.pending().singleOrNull() == pending)
                            check(layout.requireStaged(targetId) == canonical)
                            if (auxiliary4) {
                                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                                    canonical, allowAuxiliary3 = false,
                                )
                            } else {
                                FaroV5GenerationStoreGraph.requireSealedFinance9ForDevCutover(canonical)
                            }
                            pointer.read() == origin
                        }
                        target -> checkActive(target)
                        else -> false
                    }
            },
        )
        val result = if (recovery) coordinator.recover(operationId) else coordinator.activate(operationId)
        when (result) {
            is GenerationActivationResult.Activated -> {
                val completed = checkNotNull(journal.read(operationId))
                check(pointer.read() == target && completed.phase == BootstrapPhase.COMPLETED &&
                    completed.materialWritesAccepted && fence.isInstalled(MaintenanceOperationKind.RESTORE, targetId))
            }
            is GenerationActivationResult.RolledBack -> {
                check(pointer.read() == rollback &&
                    journal.read(operationId)?.phase == BootstrapPhase.ROLLBACK_VERIFIED &&
                    !fence.isInstalled(MaintenanceOperationKind.RESTORE, targetId))
            }
            is GenerationActivationResult.Blocked -> Unit
        }
        result
    }
}
