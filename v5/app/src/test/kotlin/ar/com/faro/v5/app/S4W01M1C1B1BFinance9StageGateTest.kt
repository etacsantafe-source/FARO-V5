package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * JVM-only simulated stage gate: fake callback and fake principal DO NOT
 * establish genuine Android biometric consent, issuance or productive Apply.
 */
class S4W01M1C1B1BFinance9StageGateTest {
    @TempDir lateinit var root: Path

    private inner class Rig(label: String) {
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + label),
        ))
        var epoch: String? = "epoch-1"
        var ready = true
        var calls = 0
        val plan = PreparedPlan.of(ApplyPlanNode(
            PlanNodeId("finance"),
            PreparedOperation(
                requireNotNull(BuiltInContractCatalog.create().current(FinanceOperations.ACCOUNT_CREATE)),
                FinanceAccountCreatePayload.payload(FinanceAccountCreate(
                    FinanceAccountId.of("account-" + label),
                    FinanceAccountDisplayName.of("Efectivo"),
                    FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 0L,
                )),
                DependencyStampSet.empty(), IdempotencyKey("intent-" + label),
            ), emptySet(),
        ))
        val identity = ApplyAttemptIdentity(
            TransactionId("tx-" + label), plan.planFingerprint,
        )
        val receipt = ExecutionReceipt(
            ReceiptId("receipt-" + label), identity.transactionId, plan.planFingerprint,
        )
        val match = S4W01M1C1AReviewPresenceMatch(
            reviewId = "review-" + label, reviewRevision = 1L,
            planFingerprint = plan.planFingerprint,
            operationId = FinanceOperations.ACCOUNT_CREATE.value,
            objectScope = "account-" + label,
            challengeDigestHex = "a".repeat(64),
            signatureDigestHex = "b".repeat(64),
            pointer = pinned, applicationUid = 10123,
        )
        val grant = Finance9TrustedStageGrant(
            principalHash = "c".repeat(64),
            generationId = pinned.generationId.value,
            pointerSequence = pinned.sequence,
            operationId = match.operationId, objectScope = match.objectScope,
            grantId = "grant-" + label, expectedPolicyEpoch = 1L,
        )
        val scope = object : RealityTransactionScope {
            override val transactionId = identity.transactionId
        }
        fun gate(
            grantArg: Finance9TrustedStageGrant = grant,
            matchArg: S4W01M1C1AReviewPresenceMatch = match,
            port: RealityFinance9TrustedStageGrantPort =
                RealityFinance9TrustedStageGrantPort { _, _, _, _, _ ->
                    calls++
                    true
                },
        ) = S4W01M1C1B1BFinance9StageGate(
            pinned, pointer, matchArg, grantArg, 10123,
            "epoch-1", { epoch }, { ready }, port, { 1500L },
        )
        fun execute(
            gate: S4W01M1C1B1BFinance9StageGate,
            receiptArg: ExecutionReceipt = receipt,
        ) = gate.allowCommit(
            plan, identity, receiptArg, plan.applyPlan.nodesInTopologicalOrder(),
            scope,
        )
    }

    @Test fun h01MatchingStageGateCallsRealityPortOnce() {
        val rig = Rig("success")
        rig.gate().use { gate ->
            assertTrue(rig.execute(gate))
            assertEquals(1, rig.calls)
        }
    }

    @Test fun h02SecondInvocationCannotReuseSameGate() {
        val rig = Rig("replay")
        rig.gate().use { gate ->
            assertTrue(rig.execute(gate))
            assertFalse(rig.execute(gate))
            assertEquals(1, rig.calls)
        }
    }

    @Test fun h03ForeignReceiptOrFingerprintNeverConsumesGrant() {
        val rig = Rig("receipt")
        rig.gate().use { gate ->
            assertFalse(rig.execute(gate, rig.receipt.copy(transactionId = TransactionId("foreign"))))
            assertEquals(0, rig.calls)
        }
        rig.gate().use { gate ->
            assertFalse(rig.execute(gate, rig.receipt.copy(planFingerprint = PlanFingerprint("d".repeat(64)))))
            assertEquals(0, rig.calls)
        }
    }

    @Test fun h04ScopeAndGenerationMismatchesDeny() {
        val rig = Rig("wrong")
        rig.gate(grantArg = rig.grant.copy(objectScope = "other")).use { gate ->
            assertFalse(rig.execute(gate))
        }
        rig.gate(grantArg = rig.grant.copy(pointerSequence = rig.pinned.sequence + 1)).use { gate ->
            assertFalse(rig.execute(gate))
        }
        assertEquals(0, rig.calls)
    }

    @Test fun h05PointerAbaAndHostRevocationDeny() {
        val rig = Rig("aba")
        val gate = rig.gate()
        val next = requireNotNull(rig.pointer.compareAndSet(
            rig.pinned.generationId, StorageGenerationId("other"),
        ))
        assertNotNull(rig.pointer.compareAndSet(
            next.generationId, rig.pinned.generationId,
        ))
        assertFalse(rig.execute(gate))
        assertEquals(0, rig.calls)
        rig.epoch = "epoch-revoked"
        assertFalse(rig.execute(rig.gate()))
        assertEquals(0, rig.calls)
    }

    @Test fun h06DownstreamExceptionPropagatesForExecutorRecovery() {
        val rig = Rig("throw")
        rig.gate(port = RealityFinance9TrustedStageGrantPort { _, _, _, _, _ ->
            throw IllegalStateException("simulated SQLite post receipt failure")
        }).use { gate ->
            assertThrows(IllegalStateException::class.java) {
                rig.execute(gate)
            }
        }
    }
}
