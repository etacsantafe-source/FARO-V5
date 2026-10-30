package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApplyTransactionExecutorTest {
    @Test
    fun bindingsAndCommitArtifactsRunInsideSingleTransactionThenReturnReceipt() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val calls = mutableListOf<String>()
        val plan = plan()
        val outbox = effect("effect-a", "tx-1")
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, scope ->
                    assertTrue(tx.active)
                    assertEquals(TransactionId("tx-1"), scope.transactionId)
                    calls += "a"
                    OperationBindingExecutionResult.CompletedWithOutbox(listOf(outbox))
                },
                binding("fixture.b", "planning") { _, _ ->
                    assertTrue(tx.active)
                    calls += "b"
                    OperationBindingExecutionResult.Completed
                },
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.Completed(
                ExecutionReceipt(
                    ReceiptId("receipt-tx-1"),
                    TransactionId("tx-1"),
                    plan.planFingerprint,
                )
            ),
            result,
        )
        assertEquals(listOf("a", "b"), calls)
        assertEquals(listOf(outbox), commit.lastOutbox)
        assertEquals(1, commit.calls)
        assertEquals(1, tx.entries)
        assertEquals(1, tx.commits)
        assertEquals(0, tx.rollbacks)
        assertFalse(tx.active)
    }

    @Test
    fun rejectedBindingRollsBackAndNeverPersistsCommitArtifacts() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val calls = mutableListOf<String>()
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, _ ->
                    calls += "a"
                    OperationBindingExecutionResult.Rejected("owner_rejected")
                },
                binding("fixture.b", "planning") { _, _ ->
                    calls += "b"
                    OperationBindingExecutionResult.Completed
                },
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.BINDING_REJECTED,
                PlanNodeId("a"),
                "owner_rejected",
            ),
            result,
        )
        assertEquals(listOf("a"), calls)
        assertEquals(0, commit.calls)
        assertEquals(0, tx.commits)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun bindingSetMismatchFailsBeforeTransactionEntry() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance"),
                binding("fixture.b", "planning"),
            ),
        )
        val reversed = ApplyBindingResolution.Resolved(
            resolved.bindingsInPlanOrder.reversed()
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            reversed,
        )

        assertEquals(
            ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.BINDING_SET_PLAN_MISMATCH
            ),
            result,
        )
        assertEquals(0, tx.entries)
        assertEquals(0, commit.calls)
    }

    @Test
    fun transactionScopeIdentityMismatchRollsBackBeforeBindingExecution() {
        val tx = RecordingTransactionPort(TransactionId("tx-other"))
        val commit = RecordingCommitPort(tx)
        var calls = 0
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, _ ->
                    calls += 1
                    OperationBindingExecutionResult.Completed
                },
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.TRANSACTION_SCOPE_MISMATCH
            ),
            result,
        )
        assertEquals(0, calls)
        assertEquals(0, commit.calls)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun outboxFromDifferentTransactionRollsBackBeforeCommitArtifacts() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, _ ->
                    OperationBindingExecutionResult.CompletedWithOutbox(
                        listOf(effect("effect-wrong", "tx-other"))
                    )
                },
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.OUTBOX_TRANSACTION_MISMATCH,
                PlanNodeId("a"),
            ),
            result,
        )
        assertEquals(0, commit.calls)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun mismatchedReceiptRollsBackInsteadOfPublishingSuccess() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(
            tx = tx,
            returnedTransactionId = TransactionId("tx-other"),
        )
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance"),
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.Rejected(
                ApplyTransactionRejection.COMMIT_RECEIPT_MISMATCH
            ),
            result,
        )
        assertEquals(1, commit.calls)
        assertEquals(0, tx.commits)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun bindingExceptionCausesTransactionRollbackAndNoCommitArtifacts() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, _ -> error("boom") },
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )
        assertEquals(
            ApplyTransactionExecution.RecoveryRequired(
                TransactionId("tx-1"),
                ApplyTransactionRecoveryReason.TRANSACTION_EXECUTION_UNCERTAIN,
            ),
            result,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(EffectCertainty.EFFECT_UNKNOWN_RECOVERING, result.outcome.effectCertainty)
        assertEquals(0, commit.calls)
        assertEquals(0, tx.commits)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun commitPortExceptionCausesTransactionRollback() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx, throwOnCommit = true)
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance"),
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )
        assertEquals(
            ApplyTransactionExecution.RecoveryRequired(
                TransactionId("tx-1"),
                ApplyTransactionRecoveryReason.TRANSACTION_EXECUTION_UNCERTAIN,
            ),
            result,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(RetryDisposition.RETRYABLE, result.outcome.retryDisposition)
        assertEquals(1, commit.calls)
        assertEquals(0, tx.commits)
        assertEquals(1, tx.rollbacks)
    }

    @Test
    fun cancellationBecomesTypedRecoveryRequired() {
        val tx = RecordingTransactionPort()
        val commit = RecordingCommitPort(tx)
        val plan = plan()
        val resolved = resolve(
            plan,
            listOf(
                binding("fixture.a", "finance") { _, _ ->
                    throw java.util.concurrent.CancellationException("cancelled")
                },
                binding("fixture.b", "planning"),
            ),
        )

        val result = ApplyTransactionExecutor(tx, commit).execute(
            plan,
            ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint),
            resolved,
        )

        assertEquals(
            ApplyTransactionExecution.RecoveryRequired(
                TransactionId("tx-1"),
                ApplyTransactionRecoveryReason.TRANSACTION_EXECUTION_UNCERTAIN,
            ),
            result,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(EffectCertainty.EFFECT_UNKNOWN_RECOVERING, result.outcome.effectCertainty)
        assertEquals(0, commit.calls)
        assertEquals(1, tx.rollbacks)
    }

    private fun resolve(
        plan: PreparedPlanView,
        bindings: List<OperationBindingPort>,
    ): ApplyBindingResolution.Resolved =
        ApplyBindingResolutionGate(bindings).resolve(plan) as ApplyBindingResolution.Resolved

    private fun binding(
        operationId: String,
        executor: String,
        body: (PreparedOperation, RealityTransactionScope) -> OperationBindingExecutionResult =
            { _, _ -> OperationBindingExecutionResult.Completed },
    ) = object : OperationBindingPort {
        override val descriptor = OperationBindingDescriptor(
            OperationId(operationId),
            ExecutorId(executor),
            ExecutionProfile.REALITY_MUTATION,
        )

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult =
            body(preparedOperation, transactionScope)
    }

    private fun effect(
        effectId: String,
        transactionId: String,
    ) = OutboxEffectIntent(
        effectId = EffectId(effectId),
        sourceTransactionId = TransactionId(transactionId),
        idempotencyKey = IdempotencyKey("idem-" + effectId),
        payload = CanonicalPayload.fromEncoded(
            "effect-v1",
            effectId.toByteArray(),
        ),
    )

    private fun plan(): PreparedPlanView {
        val a = node("a", "fixture.a", "finance", emptySet())
        val b = node("b", "fixture.b", "planning", setOf(PlanNodeId("a")))
        val applyPlan = ApplyPlan.of(b, a)
        return object : PreparedPlanView {
            override val applyPlan = applyPlan
            override val planFingerprint = PlanFingerprint("plan-tx")
        }
    }

    private fun node(
        nodeId: String,
        operationId: String,
        executor: String,
        dependsOn: Set<PlanNodeId>,
    ) = ApplyPlanNode(
        PlanNodeId(nodeId),
        PreparedOperation(
            OperationContractDescriptor(
                OperationId(operationId),
                CapabilityId("N-025"),
                ExecutorId(executor),
                ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"),
                ContractFingerprint("contract-" + operationId),
                ContractExposure.BINDABLE,
            ),
            CanonicalPayload.fromEncoded("fixture-v1", operationId.toByteArray()),
            DependencyStampSet.of(
                DependencyStamp(
                    AuthorityBoundaryId("reality." + executor),
                    DependencyKey("entity:" + operationId),
                    DependencyStampValue("revision:1"),
                )
            ),
            IdempotencyKey("intent-" + operationId),
        ),
        dependsOn,
    )

    private class RecordingCommitPort(
        private val tx: RecordingTransactionPort,
        private val returnedTransactionId: TransactionId? = null,
        private val throwOnCommit: Boolean = false,
    ) : ApplyCommitPort {
        var calls = 0
        var lastOutbox: List<OutboxEffectIntent> = emptyList()

        override fun commit(
            transactionScope: RealityTransactionScope,
            identity: ApplyAttemptIdentity,
            outboxIntents: List<OutboxEffectIntent>,
        ): ExecutionReceipt {
            calls += 1
            assertTrue(tx.active)
            assertEquals(identity.transactionId, transactionScope.transactionId)
            lastOutbox = outboxIntents.toList()
            if (throwOnCommit) error("commit failed")
            return ExecutionReceipt(
                receiptId = ReceiptId("receipt-" + identity.transactionId.value),
                transactionId = returnedTransactionId ?: identity.transactionId,
                planFingerprint = identity.planFingerprint,
            )
        }
    }

    private class RecordingTransactionPort(
        private val scopeTransactionId: TransactionId? = null,
    ) : AtomicApplyTransactionPort {
        var entries = 0
        var commits = 0
        var rollbacks = 0
        var active = false

        override fun <T> run(
            transactionId: TransactionId,
            block: (RealityTransactionScope) -> AtomicApplyTransactionDecision<T>,
        ): T {
            entries += 1
            active = true
            val scope = object : RealityTransactionScope {
                override val transactionId =
                    scopeTransactionId ?: transactionId
            }
            try {
                return when (val decision = block(scope)) {
                    is AtomicApplyTransactionDecision.Commit -> {
                        commits += 1
                        decision.value
                    }
                    is AtomicApplyTransactionDecision.Rollback -> {
                        rollbacks += 1
                        decision.value
                    }
                }
            } catch (failure: Throwable) {
                rollbacks += 1
                throw failure
            } finally {
                active = false
            }
        }
    }
}
