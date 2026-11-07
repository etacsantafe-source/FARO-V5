package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*

enum class ApplyTransactionRejection {
    IDENTITY_PLAN_MISMATCH,
    BINDING_SET_PLAN_MISMATCH,
    TRANSACTION_SCOPE_MISMATCH,
    BINDING_REJECTED,
    OUTBOX_TRANSACTION_MISMATCH,
    COMMIT_RECEIPT_MISMATCH,
    AUTHORIZATION_REJECTED,
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

/**
 * Optional trusted authorization check in the real transaction scope.
 * A false decision rolls Reality back without a receipt. The host providing
 * this port must serialize revocation/Review and verify its own policy: this
 * hook alone does not provide a durable or cross-process Planning8 ACL.
 */
fun interface ApplyTransactionAuthorizationPort {
    fun isAuthorized(
        preparedPlan: PreparedPlanView,
        identity: ApplyAttemptIdentity,
        node: ApplyPlanNode,
        transactionScope: RealityTransactionScope,
    ): Boolean
}

class ApplyTransactionExecutor(
    private val transactions: AtomicApplyTransactionPort,
    private val commits: ApplyCommitPort,
    private val authorization: ApplyTransactionAuthorizationPort? = null,
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
                val checkedNodes = mutableListOf<ApplyPlanNode>()

                resolvedBindings.bindingsInPlanOrder.forEach { resolved ->
                    val node = preparedPlan.applyPlan.node(resolved.nodeId)
                        ?: return@run AtomicApplyTransactionDecision.Rollback(
                            ApplyTransactionExecution.Rejected(
                                ApplyTransactionRejection.BINDING_SET_PLAN_MISMATCH,
                                resolved.nodeId,
                            )
                        )

                    if (authorization?.isAuthorized(preparedPlan, identity, node, scope) == false) {
                        return@run AtomicApplyTransactionDecision.Rollback(
                            ApplyTransactionExecution.Rejected(
                                ApplyTransactionRejection.AUTHORIZATION_REJECTED,
                                node.nodeId,
                            )
                        )
                    }
                    checkedNodes += node

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

                // Re-evaluate after the bindings and before any receipt/outbox
                // commit. A revoke during a binding must not publish success.
                // Host-level serialization is still required to cover the
                // final-check-to-SQL-COMMIT interval across owners/processes.
                if (authorization != null) {
                    for (node in checkedNodes) {
                        if (!authorization.isAuthorized(preparedPlan, identity, node, scope)) {
                            return@run AtomicApplyTransactionDecision.Rollback(
                                ApplyTransactionExecution.Rejected(
                                    ApplyTransactionRejection.AUTHORIZATION_REJECTED,
                                    node.nodeId,
                                )
                            )
                        }
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
