package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyRecoveryResolverTest {
    @Test
    fun compatibleReceiptWinsBeforeAttemptLookup() {
        val plan = plan()
        var attemptLookups = 0
        val receipt = ExecutionReceipt(
            ReceiptId("receipt-1"),
            TransactionId("tx-1"),
            plan.planFingerprint,
        )
        val resolver = resolver(
            plan = plan,
            receipt = receipt,
            attemptLookup = {
                attemptLookups += 1
                error("attempt lookup must not run after committed receipt")
            },
        )

        val result = resolver.resolve(plan)

        assertEquals(
            ApplyRecoveryResolution.Committed(receipt),
            result,
        )
        assertEquals(ExecutionOutcomeState.COMMITTED, result.outcome.state)
        assertEquals(EffectCertainty.EFFECT_COMMITTED, result.outcome.effectCertainty)
        assertEquals(0, attemptLookups)
    }

    @Test
    fun missingReceiptAndAttemptIsTypedNotStarted() {
        val plan = plan()
        val result = resolver(plan).resolve(plan)

        assertEquals(ExecutionOutcomeState.NOT_STARTED, result.outcome.state)
        assertEquals(RetryDisposition.RETRYABLE, result.outcome.retryDisposition)
        assertEquals(EffectCertainty.NO_EFFECT_KNOWN, result.outcome.effectCertainty)
    }

    @Test
    fun preparedWithoutReceiptRequiresRevalidationWithSameIdentity() {
        val plan = plan()
        val attempt = ApplyAttemptSnapshot(
            transactionId = TransactionId("tx-1"),
            planFingerprint = plan.planFingerprint,
            state = ApplyAttemptJournalState.PREPARED,
        )
        val result = resolver(plan, attempt = attempt).resolve(plan)

        assertEquals(
            ApplyRecoveryResolution.PreparedForRevalidation(attempt),
            result,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(RetryDisposition.RETRYABLE, result.outcome.retryDisposition)
        assertEquals(EffectCertainty.EFFECT_UNKNOWN_RECOVERING, result.outcome.effectCertainty)
    }

    @Test
    fun committedAttemptWithoutReceiptNeverBecomesFakeSuccess() {
        val plan = plan()
        val attempt = ApplyAttemptSnapshot(
            transactionId = TransactionId("tx-1"),
            planFingerprint = plan.planFingerprint,
            state = ApplyAttemptJournalState.COMMITTED,
        )
        val result = resolver(plan, attempt = attempt).resolve(plan)

        assertEquals(
            ApplyRecoveryRejection.COMMITTED_ATTEMPT_WITHOUT_RECEIPT,
            (result as ApplyRecoveryResolution.Rejected).reason,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(RetryDisposition.REQUIRES_HUMAN_ACTION, result.outcome.retryDisposition)
    }

    @Test
    fun incompatibleReceiptFailsClosedBeforeAttemptLookup() {
        val plan = plan()
        var attemptLookups = 0
        val result = resolver(
            plan = plan,
            receipt = ExecutionReceipt(
                ReceiptId("receipt-conflict"),
                TransactionId("tx-1"),
                PlanFingerprint("other-plan"),
            ),
            attemptLookup = {
                attemptLookups += 1
                null
            },
        ).resolve(plan)

        assertEquals(
            ApplyRecoveryRejection.RETRY_GATE_REJECTED,
            (result as ApplyRecoveryResolution.Rejected).reason,
        )
        assertEquals(ExecutionOutcomeState.REJECTED, result.outcome.state)
        assertEquals(0, attemptLookups)
    }

    @Test
    fun attemptFingerprintMismatchFailsClosed() {
        val plan = plan()
        val result = resolver(
            plan = plan,
            attempt = ApplyAttemptSnapshot(
                transactionId = TransactionId("tx-1"),
                planFingerprint = PlanFingerprint("other-plan"),
                state = ApplyAttemptJournalState.PREPARED,
            ),
        ).resolve(plan)

        assertEquals(
            ApplyRecoveryRejection.ATTEMPT_PLAN_MISMATCH,
            (result as ApplyRecoveryResolution.Rejected).reason,
        )
        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, result.outcome.state)
        assertEquals(RetryDisposition.REQUIRES_HUMAN_ACTION, result.outcome.retryDisposition)
    }

    private fun resolver(
        plan: PreparedPlanView,
        receipt: ExecutionReceipt? = null,
        attempt: ApplyAttemptSnapshot? = null,
        attemptLookup: ((TransactionId) -> ApplyAttemptSnapshot?)? = null,
    ): ApplyRecoveryResolver {
        val retryGate = ApplyRetryReceiptGate(
            identities = ApplyAttemptIdentityPort {
                ApplyAttemptIdentity(TransactionId("tx-1"), plan.planFingerprint)
            },
            receipts = ExecutionReceiptLookupPort { receipt },
        )
        return ApplyRecoveryResolver(
            retryGate = retryGate,
            attempts = ApplyAttemptLookupPort { tx ->
                attemptLookup?.invoke(tx) ?: attempt
            },
        )
    }

    private fun plan(): PreparedPlanView {
        val contract = OperationContractDescriptor(
            operationId = OperationId("fixture.recovery"),
            capabilityId = CapabilityId("N-025"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("contract-current"),
            exposure = ContractExposure.BINDABLE,
        )
        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("node-a"),
                preparedOperation = PreparedOperation(
                    contract = contract,
                    payload = CanonicalPayload.fromEncoded(
                        "fixture-v1",
                        "amount=100".toByteArray(),
                    ),
                    dependencyStamps = DependencyStampSet.of(
                        DependencyStamp(
                            authorityBoundary = AuthorityBoundaryId("reality.finance"),
                            dependencyKey = DependencyKey("entity:fixture"),
                            observedValue = DependencyStampValue("revision:1"),
                        )
                    ),
                    idempotencyKey = IdempotencyKey("intent-a"),
                ),
                dependsOn = emptySet(),
            )
        )
        return object : PreparedPlanView {
            override val applyPlan = applyPlan
            override val planFingerprint = PlanFingerprint("plan-current")
        }
    }
}
