package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyRetryReceiptGateTest {
    @Test
    fun samePreparedPlanKeepsStableTransactionIdentityAcrossRetries() {
        val plan = plan(PlanFingerprint("plan-current"))
        val identities = StableIdentityFake()
        val gate = ApplyRetryReceiptGate(
            identities = identities,
            receipts = ExecutionReceiptLookupPort { null },
        )

        val first = gate.resolve(plan)
        val second = gate.resolve(plan)

        assertEquals(first, second)
        assertEquals(
            ApplyRetryResolution.Proceed(
                ApplyAttemptIdentity(
                    transactionId = TransactionId("tx-1"),
                    planFingerprint = plan.planFingerprint,
                )
            ),
            first,
        )
    }

    @Test
    fun matchingCommittedReceiptShortCircuitsRetry() {
        val plan = plan(PlanFingerprint("plan-current"))
        val identity = ApplyAttemptIdentity(
            transactionId = TransactionId("tx-existing"),
            planFingerprint = plan.planFingerprint,
        )
        val receipt = ExecutionReceipt(
            receiptId = ReceiptId("receipt-existing"),
            transactionId = identity.transactionId,
            planFingerprint = plan.planFingerprint,
        )
        val gate = ApplyRetryReceiptGate(
            identities = ApplyAttemptIdentityPort { identity },
            receipts = ExecutionReceiptLookupPort { receipt },
        )

        assertEquals(
            ApplyRetryResolution.AlreadyCommitted(receipt),
            gate.resolve(plan),
        )
    }

    @Test
    fun identityBoundToDifferentPlanFailsBeforeReceiptLookup() {
        val plan = plan(PlanFingerprint("plan-current"))
        var receiptLookups = 0
        val gate = ApplyRetryReceiptGate(
            identities = ApplyAttemptIdentityPort {
                ApplyAttemptIdentity(
                    transactionId = TransactionId("tx-conflict"),
                    planFingerprint = PlanFingerprint("other-plan"),
                )
            },
            receipts = ExecutionReceiptLookupPort {
                receiptLookups += 1
                null
            },
        )

        assertEquals(
            ApplyRetryResolution.Rejected(
                ApplyRetryRejection.IDENTITY_PLAN_MISMATCH
            ),
            gate.resolve(plan),
        )
        assertEquals(0, receiptLookups)
    }

    @Test
    fun receiptReturnedForDifferentTransactionFailsClosed() {
        val plan = plan(PlanFingerprint("plan-current"))
        val identity = ApplyAttemptIdentity(
            transactionId = TransactionId("tx-expected"),
            planFingerprint = plan.planFingerprint,
        )
        val gate = ApplyRetryReceiptGate(
            identities = ApplyAttemptIdentityPort { identity },
            receipts = ExecutionReceiptLookupPort {
                ExecutionReceipt(
                    receiptId = ReceiptId("receipt-wrong-tx"),
                    transactionId = TransactionId("tx-other"),
                    planFingerprint = plan.planFingerprint,
                )
            },
        )

        assertEquals(
            ApplyRetryResolution.Rejected(
                ApplyRetryRejection.RECEIPT_TRANSACTION_MISMATCH
            ),
            gate.resolve(plan),
        )
    }

    @Test
    fun sameTransactionWithDifferentPlanFingerprintFailsClosed() {
        val plan = plan(PlanFingerprint("plan-current"))
        val identity = ApplyAttemptIdentity(
            transactionId = TransactionId("tx-existing"),
            planFingerprint = plan.planFingerprint,
        )
        val gate = ApplyRetryReceiptGate(
            identities = ApplyAttemptIdentityPort { identity },
            receipts = ExecutionReceiptLookupPort {
                ExecutionReceipt(
                    receiptId = ReceiptId("receipt-existing"),
                    transactionId = identity.transactionId,
                    planFingerprint = PlanFingerprint("different-plan"),
                )
            },
        )

        assertEquals(
            ApplyRetryResolution.Rejected(
                ApplyRetryRejection.RECEIPT_PLAN_CONFLICT
            ),
            gate.resolve(plan),
        )
    }

    private fun plan(fingerprint: PlanFingerprint): PreparedPlanView {
        val descriptor = OperationContractDescriptor(
            operationId = OperationId("fixture.retry"),
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
                    contract = descriptor,
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
            override val planFingerprint = fingerprint
        }
    }

    private class StableIdentityFake : ApplyAttemptIdentityPort {
        private val identities = linkedMapOf<PlanFingerprint, ApplyAttemptIdentity>()

        override fun resolve(preparedPlan: PreparedPlanView): ApplyAttemptIdentity =
            identities.getOrPut(preparedPlan.planFingerprint) {
                ApplyAttemptIdentity(
                    transactionId = TransactionId("tx-" + (identities.size + 1)),
                    planFingerprint = preparedPlan.planFingerprint,
                )
            }
    }
}
