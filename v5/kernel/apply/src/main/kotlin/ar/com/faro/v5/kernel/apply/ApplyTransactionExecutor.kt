package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*

enum class ApplyTransactionRejection {
    IDENTITY_PLAN_MISMATCH,
    BINDING_SET_PLAN_MISMATCH,
    TRANSACTION_SCOPE_MISMATCH,
    BINDING_REJECTED,
    OUTBOX_TRANSACTION_MISMATCH,
    COMMIT_RECEIPT_MISMATCH,
}

enum class ApplyTransactionRecoveryReason {
    TRANSACTION_EXECUTION_UNCERTAIN,
}

sealed interface ApplyTransactionExecution {
    val outcome: ExecutionOutcome

    data class Completed(
        val receipt: ExecutionReceipt,
    ) : ApplyTransactionExecution {
        override val outcome = ExecutionOutcome(
            state = ExecutionOutcomeState.COMMITTED,
            retryDisposition = RetryDisposition.NOT_RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_COMMITTED,
        )
    }

    data class Rejected(
        val reason: ApplyTransactionRejection,
        val nodeId: PlanNodeId? = null,
        val code: String? = null,
    ) : ApplyTransactionExecution {
        override val outcome = when (reason) {
            ApplyTransactionRejection.BINDING_REJECTED ->
                ExecutionOutcome(
                    state = ExecutionOutcomeState.REJECTED,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                    effectCertainty = EffectCertainty.EFFECT_REJECTED,
                )

            else ->
                ExecutionOutcome(
                    state = ExecutionOutcomeState.REJECTED,
                    retryDisposition = RetryDisposition.REQUIRES_HUMAN_ACTION,
                    effectCertainty = EffectCertainty.NO_EFFECT_KNOWN,
                )
        }
    }

    data class RecoveryRequired(
        val transactionId: TransactionId,
        val reason: ApplyTransactionRecoveryReason,
    ) : ApplyTransactionExecution {
        override val outcome = ExecutionOutcome(
            state = ExecutionOutcomeState.RECOVERING_UNKNOWN,
            retryDisposition = RetryDisposition.RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        )
    }
}

class ApplyTransactionExecutor(
    private val transactions: AtomicApplyTransactionPort,
    private val commits: ApplyCommitPort,
) {
    fun execute(
        preparedPlan: PreparedPlanView,
        identity: ApplyAttemptIdentity,
        resolvedBindings: ApplyBindingResolution.Resolved,
    ): ApplyTransactionExecution {
        if (identity.planFingerprint != preparedPlan.planFingerprint) {
            return ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.IDENTITY_PLAN_MISMATCH,
            )
        }

        if (
            resolvedBindings.bindingsInPlanOrder.map { it.nodeId } !=
            preparedPlan.applyPlan.topologicalOrder
        ) {
            return ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.BINDING_SET_PLAN_MISMATCH,
            )
        }

        return try {
            transactions.run(identity.transactionId) { scope ->
                if (scope.transactionId != identity.transactionId) {
                    return@run AtomicApplyTransactionDecision.Rollback(
                        ApplyTransactionExecution.Rejected(
                            ApplyTransactionRejection.TRANSACTION_SCOPE_MISMATCH,
                        )
                    )
                }

                val outboxIntents = mutableListOf<OutboxEffectIntent>()

                resolvedBindings.bindingsInPlanOrder.forEach { resolved ->
                    val node = preparedPlan.applyPlan.node(resolved.nodeId)
                        ?: return@run AtomicApplyTransactionDecision.Rollback(
                            ApplyTransactionExecution.Rejected(
                                ApplyTransactionRejection.BINDING_SET_PLAN_MISMATCH,
                                resolved.nodeId,
                            )
                        )

                    when (
                        val result = resolved.binding.execute(
                            node.preparedOperation,
                            scope,
                        )
                    ) {
                        OperationBindingExecutionResult.Completed -> Unit

                        is OperationBindingExecutionResult.CompletedWithOutbox -> {
                            if (
                                result.outboxIntents.any {
                                    it.sourceTransactionId != identity.transactionId
                                }
                            ) {
                                return@run AtomicApplyTransactionDecision.Rollback(
                                    ApplyTransactionExecution.Rejected(
                                        ApplyTransactionRejection.OUTBOX_TRANSACTION_MISMATCH,
                                        resolved.nodeId,
                                    )
                                )
                            }
                            outboxIntents += result.outboxIntents
                        }

                        is OperationBindingExecutionResult.Rejected ->
                            return@run AtomicApplyTransactionDecision.Rollback(
                                ApplyTransactionExecution.Rejected(
                                    ApplyTransactionRejection.BINDING_REJECTED,
                                    resolved.nodeId,
                                    result.code,
                                )
                            )
                    }
                }

                val receipt = commits.commit(
                    transactionScope = scope,
                    identity = identity,
                    outboxIntents = outboxIntents.toList(),
                )

                if (
                    receipt.transactionId != identity.transactionId ||
                    receipt.planFingerprint != identity.planFingerprint
                ) {
                    return@run AtomicApplyTransactionDecision.Rollback(
                        ApplyTransactionExecution.Rejected(
                            ApplyTransactionRejection.COMMIT_RECEIPT_MISMATCH,
                        )
                    )
                }

                AtomicApplyTransactionDecision.Commit(
                    ApplyTransactionExecution.Completed(receipt)
                )
            }
        } catch (_: Exception) {
            ApplyTransactionExecution.RecoveryRequired(
                transactionId = identity.transactionId,
                reason = ApplyTransactionRecoveryReason.TRANSACTION_EXECUTION_UNCERTAIN,
            )
        }
    }
}
