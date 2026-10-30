package ar.com.faro.v5.maintenance

enum class GenerationSwitchFaultPoint {
    AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE,
    AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE,
}

class GenerationSwitchCoordinator(
    private val journal: BootstrapJournalPort,
    private val activePointer: ActiveGenerationPointerPort,
    private val generationLayout: FileStorageGenerationLayout,
    private val faultInjector: (GenerationSwitchFaultPoint) -> Unit = {},
) {
    fun switch(
        operationId: MaintenanceOperationId,
    ): BootstrapJournalEntry {
        val current =
            checkNotNull(journal.read(operationId)) {
                "bootstrap journal operation is missing"
            }
        check(current.phase == BootstrapPhase.SWITCH_PREPARED) {
            "generation switch requires SWITCH_PREPARED"
        }
        check(!current.materialWritesAccepted) {
            "generation switch cannot begin after material-write fence"
        }

        generationLayout.requireStaged(current.targetGenerationId)

        val pointer = activePointer.read()
        check(pointer?.generationId == current.previousGenerationId) {
            "active generation does not match bootstrap previous generation"
        }

        checkNotNull(
            activePointer.compareAndSet(
                expectedGenerationId = current.previousGenerationId,
                targetGenerationId = current.targetGenerationId,
            )
        ) {
            "active generation changed during switch"
        }

        faultInjector(
            GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
        )

        val switched =
            BootstrapTransitionPolicy.transition(
                current,
                BootstrapPhase.SWITCHED,
            )
        if (!journal.compareAndSet(current, switched)) {
            val observed = journal.read(operationId)
            check(observed == switched) {
                "bootstrap journal changed unexpectedly after pointer publish"
            }
        }
        return switched
    }

    fun rollbackToPreviousIfEligible(
        operationId: MaintenanceOperationId,
    ): BootstrapJournalEntry {
        val current =
            checkNotNull(journal.read(operationId)) {
                "bootstrap journal operation is missing"
            }
        check(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(current)) {
            "previous generation is not eligible for reactivation"
        }
        val previous =
            checkNotNull(current.previousGenerationId) {
                "rollback requires a previous generation"
            }
        check(activePointer.read()?.generationId == current.targetGenerationId) {
            "rollback requires target generation to be active"
        }
        generationLayout.requireStaged(previous)

        val prepared =
            BootstrapTransitionPolicy.transition(
                current,
                BootstrapPhase.ROLLBACK_PREPARED,
            )
        if (!journal.compareAndSet(current, prepared)) {
            val observed = journal.read(operationId)
            check(observed == prepared) {
                "bootstrap journal changed unexpectedly while preparing rollback"
            }
        }

        checkNotNull(
            activePointer.compareAndSet(
                expectedGenerationId = current.targetGenerationId,
                targetGenerationId = previous,
            )
        ) {
            "active generation changed during rollback"
        }

        faultInjector(
            GenerationSwitchFaultPoint.AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
        )

        val rolledBack =
            BootstrapTransitionPolicy.transition(
                prepared,
                BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            )
        if (!journal.compareAndSet(prepared, rolledBack)) {
            val observed = journal.read(operationId)
            check(observed == rolledBack) {
                "bootstrap journal changed unexpectedly after rollback pointer publish"
            }
        }
        return rolledBack
    }

    fun recover(
        operationId: MaintenanceOperationId,
    ): BootstrapJournalEntry {
        val current =
            checkNotNull(journal.read(operationId)) {
                "bootstrap journal operation is missing"
            }

        val pointerId = activePointer.read()?.generationId

        return when (current.phase) {
            BootstrapPhase.SWITCH_PREPARED -> {
                when (pointerId) {
                    current.previousGenerationId -> current
                    current.targetGenerationId -> {
                        generationLayout.requireStaged(current.targetGenerationId)
                        val switched =
                            BootstrapTransitionPolicy.transition(
                                current,
                                BootstrapPhase.SWITCHED,
                            )
                        if (!journal.compareAndSet(current, switched)) {
                            val observed = journal.read(operationId)
                            check(observed == switched) {
                                "bootstrap recovery raced with unexpected journal mutation"
                            }
                        }
                        switched
                    }
                    else -> error(
                        "active generation is inconsistent with SWITCH_PREPARED journal"
                    )
                }
            }

            BootstrapPhase.SWITCHED,
            BootstrapPhase.HEALTH_VERIFIED,
            BootstrapPhase.CLEANUP_PENDING -> {
                check(pointerId == current.targetGenerationId) {
                    "post-switch journal does not match active generation"
                }
                generationLayout.requireStaged(current.targetGenerationId)
                current
            }

            BootstrapPhase.ROLLBACK_PREPARED -> {
                val previous =
                    checkNotNull(current.previousGenerationId) {
                        "rollback recovery requires previous generation"
                    }
                generationLayout.requireStaged(previous)
                when (pointerId) {
                    current.targetGenerationId -> {
                        checkNotNull(
                            activePointer.compareAndSet(
                                expectedGenerationId = current.targetGenerationId,
                                targetGenerationId = previous,
                            )
                        ) {
                            "active generation changed while recovering rollback"
                        }
                        val rolledBack =
                            BootstrapTransitionPolicy.transition(
                                current,
                                BootstrapPhase.ROLLED_BACK_POST_SWITCH,
                            )
                        check(journal.compareAndSet(current, rolledBack)) {
                            "rollback recovery journal changed unexpectedly"
                        }
                        rolledBack
                    }

                    previous -> {
                        val rolledBack =
                            BootstrapTransitionPolicy.transition(
                                current,
                                BootstrapPhase.ROLLED_BACK_POST_SWITCH,
                            )
                        if (!journal.compareAndSet(current, rolledBack)) {
                            val observed = journal.read(operationId)
                            check(observed == rolledBack) {
                                "rollback recovery raced with unexpected journal mutation"
                            }
                        }
                        rolledBack
                    }

                    else -> error(
                        "active generation is inconsistent with ROLLBACK_PREPARED journal"
                    )
                }
            }

            BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            BootstrapPhase.ROLLBACK_VERIFIED -> {
                val previous =
                    checkNotNull(current.previousGenerationId) {
                        "rolled-back recovery requires previous generation"
                    }
                generationLayout.requireStaged(previous)
                check(pointerId == previous) {
                    "rolled-back bootstrap does not match previous generation"
                }
                check(!current.materialWritesAccepted) {
                    "rolled-back bootstrap cannot have accepted target material writes"
                }
                current
            }

            BootstrapPhase.PREPARING,
            BootstrapPhase.STAGED,
            BootstrapPhase.VALIDATED -> {
                check(pointerId == current.previousGenerationId) {
                    "pre-switch journal does not match active generation"
                }
                current
            }

            BootstrapPhase.COMPLETED -> {
                check(pointerId == current.targetGenerationId) {
                    "completed bootstrap does not match active generation"
                }
                generationLayout.requireStaged(current.targetGenerationId)
                current
            }

            BootstrapPhase.ABORTED_PRE_SWITCH -> {
                check(pointerId == current.previousGenerationId) {
                    "aborted pre-switch bootstrap does not match active generation"
                }
                current
            }
        }
    }

    fun markMaterialWritesAccepted(
        operationId: MaintenanceOperationId,
    ): BootstrapJournalEntry {
        val current =
            checkNotNull(journal.read(operationId)) {
                "bootstrap journal operation is missing"
            }
        if (current.materialWritesAccepted) {
            return current
        }

        val fenced = BootstrapTransitionPolicy.acceptMaterialWrites(current)
        if (!journal.compareAndSet(current, fenced)) {
            val observed =
                checkNotNull(journal.read(operationId)) {
                    "bootstrap journal disappeared while installing material-write fence"
                }
            check(observed == fenced) {
                "bootstrap journal changed unexpectedly while installing material-write fence"
            }
        }
        return fenced
    }
}
