package ar.com.faro.v5.maintenance

/**
 * Platform/app-owned hook used after the durable pointer switch.
 *
 * Implementations reopen the runtime against [targetGenerationId] and prove required stores are
 * healthy. They must not create a second authority: the active-generation pointer remains the
 * selector of record.
 */
fun interface GenerationRuntimeActivationPort {
    fun activateAndVerify(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    ): Boolean
}

/**
 * Installs the replay fence required before restored/migrated state may accept material writes.
 * Implementations must be idempotent because startup recovery can call this again.
 */
fun interface GenerationReplayFencePort {
    fun install(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    )
}

/**
 * Reprojects OS/auxiliary state from the newly active authority. This hook is idempotent and may
 * not emit historical side effects merely because restored state is old/overdue.
 */
fun interface GenerationProjectionReconciliationPort {
    fun reconcile(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    )
}

/**
 * Pre-switch host readiness is checked BEFORE publishing the active pointer.
 * It must refuse a generation format the installed host cannot reopen.
 * This is not an activation switch or a replay fence.
 */
fun interface GenerationPreSwitchAdmissionPort {
    fun canOpenAsActive(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    ): Boolean
}

enum class GenerationActivationBlockReason {
    UNSUPPORTED_OPERATION_KIND,
    NOT_VALIDATED,
    TARGET_RUNTIME_UNSUPPORTED,
    ACTIVE_GENERATION_CHANGED,
    TARGET_NOT_STAGED,
    RUNTIME_HEALTH_FAILED,
    REPLAY_FENCE_FAILED,
    PROJECTION_RECONCILIATION_FAILED,
    ROLLED_BACK,
}

sealed interface GenerationActivationResult {
    data class Activated(
        val operationId: MaintenanceOperationId,
        val kind: MaintenanceOperationKind,
        val targetGenerationId: StorageGenerationId,
        val recovered: Boolean,
    ) : GenerationActivationResult

    data class Blocked(
        val operationId: MaintenanceOperationId,
        val reason: GenerationActivationBlockReason,
        val failureClass: String? = null,
    ) : GenerationActivationResult

    data class RolledBack(
        val operationId: MaintenanceOperationId,
        val kind: MaintenanceOperationKind,
        val restoredGenerationId: StorageGenerationId,
        val failedStep: GenerationActivationBlockReason,
        val failureClass: String? = null,
    ) : GenerationActivationResult
}

/**
 * Bridges A4/A5 semantic validation to the A3 durable generation switch.
 *
 * RESTORE/MIGRATION only. RESET owns a stricter no-rollback lifecycle in ResetLifecycleCoordinator.
 */
class ValidatedGenerationActivationCoordinator(
    private val journal: BootstrapJournalPort,
    private val activePointer: ActiveGenerationPointerPort,
    private val generationLayout: FileStorageGenerationLayout,
    private val quiescence: MaterialWriteQuiescencePort,
    private val generationSwitch: GenerationSwitchCoordinator,
    private val runtime: GenerationRuntimeActivationPort,
    private val replayFence: GenerationReplayFencePort,
    private val projections: GenerationProjectionReconciliationPort,
    // Mandatory for every runtime. No permissive default can bypass V2 readiness.
    private val preSwitchAdmission: GenerationPreSwitchAdmissionPort,
) {
    fun activate(
        operationId: MaintenanceOperationId,
    ): GenerationActivationResult =
        quiescence.withMaterialWritesQuiesced(operationId) {
            continueActivation(operationId, recovered = false)
        }

    fun recover(
        operationId: MaintenanceOperationId,
    ): GenerationActivationResult =
        quiescence.withMaterialWritesQuiesced(operationId) {
            continueActivation(operationId, recovered = true)
        }

    fun recoverPending(): List<GenerationActivationResult> =
        journal.pending()
            .filter { entry ->
                entry.kind != MaintenanceOperationKind.RESET &&
                    entry.phase in setOf(
                        BootstrapPhase.VALIDATED,
                        BootstrapPhase.SWITCH_PREPARED,
                        BootstrapPhase.SWITCHED,
                        BootstrapPhase.HEALTH_VERIFIED,
                        BootstrapPhase.COMPLETED,
                        BootstrapPhase.ROLLBACK_PREPARED,
                        BootstrapPhase.ROLLED_BACK_POST_SWITCH,
                    )
            }
            .map { entry -> recover(entry.operationId) }

    private fun continueActivation(
        operationId: MaintenanceOperationId,
        recovered: Boolean,
    ): GenerationActivationResult {
        var current =
            checkNotNull(journal.read(operationId)) {
                "bootstrap journal operation is missing"
            }

        if (current.kind == MaintenanceOperationKind.RESET) {
            return GenerationActivationResult.Blocked(
                operationId,
                GenerationActivationBlockReason.UNSUPPORTED_OPERATION_KIND,
            )
        }

        if (current.phase == BootstrapPhase.ROLLED_BACK_POST_SWITCH) {
            return verifyRolledBackRuntime(
                current,
                GenerationActivationBlockReason.ROLLED_BACK,
                null,
            )
        }
        if (current.phase == BootstrapPhase.ROLLBACK_VERIFIED) {
            return rolledBackResult(
                current,
                GenerationActivationBlockReason.ROLLED_BACK,
                null,
            )
        }

        if (current.phase == BootstrapPhase.COMPLETED && current.materialWritesAccepted) {
            if (!hostCanOpen(current)) {
                return GenerationActivationResult.Blocked(
                    operationId, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
                )
            }
            check(activePointer.read()?.generationId == current.targetGenerationId) {
                "completed activation does not match active generation"
            }
            return GenerationActivationResult.Activated(
                operationId = operationId,
                kind = current.kind,
                targetGenerationId = current.targetGenerationId,
                recovered = true,
            )
        }

        if (
            current.phase !in setOf(
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
                BootstrapPhase.SWITCHED,
                BootstrapPhase.HEALTH_VERIFIED,
                BootstrapPhase.CLEANUP_PENDING,
                BootstrapPhase.COMPLETED,
                BootstrapPhase.ROLLBACK_PREPARED,
            )
        ) {
            return GenerationActivationResult.Blocked(
                operationId,
                GenerationActivationBlockReason.NOT_VALIDATED,
            )
        }

        if (current.phase == BootstrapPhase.VALIDATED) {
            if (activePointer.read()?.generationId != current.previousGenerationId) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.ACTIVE_GENERATION_CHANGED,
                )
            }
            try {
                generationLayout.requireStaged(current.targetGenerationId)
            } catch (failure: Exception) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.TARGET_NOT_STAGED,
                    failure::class.java.simpleName,
                )
            }

            if (!hostCanOpen(current)) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
                )
            }
            current = transition(current, BootstrapPhase.SWITCH_PREPARED)
        }

        // A crash may have published the target pointer BEFORE advancing the
        // SWITCH_PREPARED journal. In that case do not block required recovery
        // and rollback. Guard only the pre-pointer state.
        if (current.phase == BootstrapPhase.SWITCH_PREPARED &&
            activePointer.read()?.generationId == current.previousGenerationId &&
            !hostCanOpen(current)
        ) {
            return GenerationActivationResult.Blocked(
                operationId,
                GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            )
        }

        current = generationSwitch.recover(operationId)

        if (current.phase == BootstrapPhase.ROLLBACK_PREPARED) {
            current = generationSwitch.recover(operationId)
        }
        if (current.phase == BootstrapPhase.ROLLED_BACK_POST_SWITCH) {
            return verifyRolledBackRuntime(
                current,
                GenerationActivationBlockReason.ROLLED_BACK,
                null,
            )
        }

        if (current.phase == BootstrapPhase.SWITCH_PREPARED) {
            current = generationSwitch.switch(operationId)
        }

        if (current.phase == BootstrapPhase.SWITCHED) {
            val health =
                try {
                    runtime.activateAndVerify(current.kind, current.targetGenerationId)
                } catch (failure: Exception) {
                    return rollback(
                        current,
                        GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED,
                        failure,
                    )
                }
            if (!health) {
                return rollback(
                    current,
                    GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED,
                    null,
                )
            }
            current = transition(current, BootstrapPhase.HEALTH_VERIFIED)
        }

        if (current.phase == BootstrapPhase.HEALTH_VERIFIED) {
            if (!hostCanOpen(current)) {
                // The pointer was already switched, but no target material writes
                // have been admitted. A host upgrade/downgrade or interrupted V2
                // promotion may remove runtime support between health and replay.
                // Recover the previous authority instead of stranding startup.
                // Once writes were admitted, rollback is NEVER automatic.
                return if (BootstrapTransitionPolicy.previousGenerationMayBeReactivated(current)) {
                    rollback(current, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED, null)
                } else {
                    GenerationActivationResult.Blocked(
                        operationId, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
                    )
                }
            }
            if (!current.materialWritesAccepted) {
                try {
                    replayFence.install(current.kind, current.targetGenerationId)
                } catch (failure: Exception) {
                    return rollback(
                        current,
                        GenerationActivationBlockReason.REPLAY_FENCE_FAILED,
                        failure,
                    )
                }

                try {
                    projections.reconcile(current.kind, current.targetGenerationId)
                } catch (failure: Exception) {
                    return rollback(
                        current,
                        GenerationActivationBlockReason.PROJECTION_RECONCILIATION_FAILED,
                        failure,
                    )
                }

                current = generationSwitch.markMaterialWritesAccepted(operationId)
            }
            current = transition(current, BootstrapPhase.COMPLETED)
        }

        if (current.phase == BootstrapPhase.COMPLETED && !current.materialWritesAccepted) {
            if (!hostCanOpen(current)) {
                return GenerationActivationResult.Blocked(
                    operationId, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
                )
            }
            val health =
                try {
                    runtime.activateAndVerify(current.kind, current.targetGenerationId)
                } catch (failure: Exception) {
                    return GenerationActivationResult.Blocked(
                        operationId,
                        GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED,
                        failure::class.java.simpleName,
                    )
                }
            if (!health) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED,
                )
            }
            try {
                replayFence.install(current.kind, current.targetGenerationId)
            } catch (failure: Exception) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.REPLAY_FENCE_FAILED,
                    failure::class.java.simpleName,
                )
            }
            try {
                projections.reconcile(current.kind, current.targetGenerationId)
            } catch (failure: Exception) {
                return GenerationActivationResult.Blocked(
                    operationId,
                    GenerationActivationBlockReason.PROJECTION_RECONCILIATION_FAILED,
                    failure::class.java.simpleName,
                )
            }
            current = generationSwitch.markMaterialWritesAccepted(operationId)
        }
        check(current.phase == BootstrapPhase.COMPLETED) {
            "validated generation activation did not converge to COMPLETED"
        }
        check(current.materialWritesAccepted)
        check(activePointer.read()?.generationId == current.targetGenerationId) {
            "activated generation pointer drifted before write fence"
        }

        return GenerationActivationResult.Activated(
            operationId = operationId,
            kind = current.kind,
            targetGenerationId = current.targetGenerationId,
            recovered = recovered,
        )
    }

    private fun hostCanOpen(entry: BootstrapJournalEntry): Boolean =
        try {
            preSwitchAdmission.canOpenAsActive(entry.kind, entry.targetGenerationId)
        } catch (_: Exception) {
            false
        }

    private fun rollback(
        failed: BootstrapJournalEntry,
        reason: GenerationActivationBlockReason,
        failure: Exception?,
    ): GenerationActivationResult {
        check(!failed.materialWritesAccepted) {
            "automatic rollback is forbidden after material writes"
        }
        check(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(failed)) {
            "failed activation is no longer eligible for rollback"
        }

        val rolledBack = generationSwitch.rollbackToPreviousIfEligible(failed.operationId)
        val previous =
            checkNotNull(rolledBack.previousGenerationId) {
                "rolled-back activation requires previous generation"
            }

        return verifyRolledBackRuntime(
            rolledBack,
            reason,
            failure,
        )
    }

    private fun verifyRolledBackRuntime(
        rolledBack: BootstrapJournalEntry,
        reason: GenerationActivationBlockReason,
        failure: Exception?,
    ): GenerationActivationResult.RolledBack {
        val previous =
            checkNotNull(rolledBack.previousGenerationId) {
                "rolled-back activation requires previous generation"
            }
        check(activePointer.read()?.generationId == previous) {
            "rolled-back activation pointer does not select previous generation"
        }

        val oldHealthy =
            try {
                runtime.activateAndVerify(rolledBack.kind, previous)
            } catch (reopenFailure: Exception) {
                throw IllegalStateException(
                    "previous generation pointer was restored but runtime reopen failed",
                    reopenFailure,
                )
            }
        check(oldHealthy) {
            "previous generation pointer was restored but runtime health failed"
        }

        try {
            projections.reconcile(rolledBack.kind, previous)
        } catch (projectionFailure: Exception) {
            throw IllegalStateException(
                "previous generation was restored but OS projection reconciliation failed",
                projectionFailure,
            )
        }

        val verified =
            transition(
                rolledBack,
                BootstrapPhase.ROLLBACK_VERIFIED,
            )
        return rolledBackResult(verified, reason, failure)
    }

    private fun rolledBackResult(
        rolledBack: BootstrapJournalEntry,
        reason: GenerationActivationBlockReason,
        failure: Exception?,
    ): GenerationActivationResult.RolledBack {
        check(rolledBack.phase == BootstrapPhase.ROLLBACK_VERIFIED) {
            "rolled-back activation result requires verified rollback"
        }
        val previous =
            checkNotNull(rolledBack.previousGenerationId) {
                "rolled-back activation requires previous generation"
            }
        check(activePointer.read()?.generationId == previous) {
            "verified rollback pointer does not select previous generation"
        }
        return GenerationActivationResult.RolledBack(
            operationId = rolledBack.operationId,
            kind = rolledBack.kind,
            restoredGenerationId = previous,
            failedStep = reason,
            failureClass = failure?.let { it::class.java.simpleName },
        )
    }

    private fun transition(
        current: BootstrapJournalEntry,
        next: BootstrapPhase,
    ): BootstrapJournalEntry {
        val updated = BootstrapTransitionPolicy.transition(current, next)
        if (!journal.compareAndSet(current, updated)) {
            val observed =
                checkNotNull(journal.read(current.operationId)) {
                    "bootstrap journal disappeared during activation"
                }
            check(observed == updated) {
                "bootstrap journal changed unexpectedly during activation"
            }
        }
        return updated
    }
}
