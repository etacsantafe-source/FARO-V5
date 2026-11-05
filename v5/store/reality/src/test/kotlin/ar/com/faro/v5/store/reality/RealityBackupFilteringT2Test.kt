package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.ApplyPlan
import ar.com.faro.v5.kernel.contracts.ApplyPlanNode
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.OutboxEffectIntent
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PlanNodeId
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.PreparedPlanView
import ar.com.faro.v5.kernel.contracts.ReceiptId
import ar.com.faro.v5.kernel.contracts.TransactionId
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RealityBackupFilteringT2Test {
    @Test
    fun `backup retains committed receipt but strips prepared attempts and outbox effects`() {
        val sourceDb =
            Files.createTempDirectory("faro-v5-reality-backup-source")
                .resolve("reality.db")
        val ids =
            ArrayDeque(
                listOf(
                    TransactionId("tx-prepared-only"),
                    TransactionId("tx-committed"),
                )
            )

        lateinit var snapshot: RealityBackupSnapshot
        RealityStore(
            fileName = sourceDb.toString(),
            transactionIdFactory = { ids.removeFirst() },
            receiptIdFactory = { ReceiptId("receipt-committed") },
            receiptClockEpochMillis = { 5_432L },
        ).use { source ->
            val preparedOnly =
                source.applyAttemptIdentities.resolve(
                    preparedPlan(
                        planFingerprint = PlanFingerprint("plan-prepared-only"),
                        idempotencyKey = IdempotencyKey("idem-prepared-only"),
                    )
                )
            val committed =
                source.applyAttemptIdentities.resolve(
                    preparedPlan(
                        planFingerprint = PlanFingerprint("plan-committed"),
                        idempotencyKey = IdempotencyKey("idem-committed"),
                    )
                )

            source.run(committed.transactionId) { scope ->
                source.commit(
                    transactionScope = scope,
                    identity = committed,
                    outboxIntents =
                        listOf(
                            OutboxEffectIntent(
                                effectId = EffectId("effect-historical"),
                                sourceTransactionId = committed.transactionId,
                                idempotencyKey = IdempotencyKey("effect-idem-historical"),
                                payload =
                                    CanonicalPayload.fromEncoded(
                                        "fixture.effect.v1",
                                        byteArrayOf(1, 2, 3),
                                    ),
                            )
                        ),
                )
                // Even after the receipt row is written, the outer Reality
                // transaction is not durable until SQLite COMMIT.
                assertThrows(IllegalStateException::class.java) {
                    source.receiptHistory.query(RealityReceiptHistoryQuery())
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            val sourceInspection = source.maintenance.inspect()
            assertEquals(2L, sourceInspection.applyAttemptCount)
            assertEquals(1L, sourceInspection.executionReceiptCount)
            assertEquals(1L, sourceInspection.outboxEffectCount)

            val committedPage = source.receiptHistory.query(RealityReceiptHistoryQuery())
            assertEquals(listOf("tx-committed"), committedPage.receipts.map { it.transactionId.value })
            assertEquals("receipt-committed", committedPage.receipts.single().receiptId.value)
            assertEquals("reality.execution_receipt", committedPage.canonicalSource)
            assertFalse(committedPage.hasCommitTimestamps)
            val timed = source.timedReceiptHistory.query(RealityTimedReceiptQuery())
            assertEquals(5_432L, timed.receipts.single().recordedAtEpochMillis)
            assertFalse(timed.legacyUndated)
            assertNull(committedPage.nextCursor)

            snapshot = source.backup.snapshot()
            assertEquals(1, snapshot.committedReceipts.size)
            assertEquals("tx-committed", snapshot.committedReceipts.single().transactionId)
            assertEquals("receipt-committed", snapshot.committedReceipts.single().receiptId)
            assertEquals(5_432L, snapshot.committedReceipts.single().commitRecordedAtEpochMillis)
            assertEquals(
                preparedOnly.transactionId,
                source.applyAttemptLookup.find(preparedOnly.transactionId)?.transactionId,
            )
        }

        val targetDb =
            Files.createTempDirectory("faro-v5-reality-backup-target")
                .resolve("reality.db")
        RealityStore(targetDb.toString()).use { target ->
            assertEquals(snapshot, target.backup.restoreOrVerify(snapshot))

            val targetInspection = target.maintenance.inspect()
            assertEquals(1L, targetInspection.applyAttemptCount)
            assertEquals(1L, targetInspection.executionReceiptCount)
            assertEquals(0L, targetInspection.outboxEffectCount)
            assertNull(target.applyAttemptLookup.find(TransactionId("tx-prepared-only")))
            assertEquals(
                "tx-committed",
                target.applyAttemptLookup
                    .find(TransactionId("tx-committed"))
                    ?.transactionId
                    ?.value,
            )
            assertEquals(snapshot, target.backup.snapshot())
            assertEquals(5_432L, target.timedReceiptHistory.query(RealityTimedReceiptQuery())
                .receipts.single().recordedAtEpochMillis)
            assertEquals(
                listOf("tx-committed"),
                target.receiptHistory.query(RealityReceiptHistoryQuery())
                    .receipts.map { it.transactionId.value },
            )
        }
    }

    @Test
    fun `receipt pages use stable transaction cursor without inventing recency`() {
        val initialFile = Files.createTempDirectory("faro-v5-history-seed").resolve("reality.db")
        val empty = RealityStore(initialFile.toString()).use { it.backup.snapshot() }
        val restored = empty.copy(
            committedReceipts = listOf("tx-c", "tx-a", "tx-b").sorted().map { id ->
                RealityCommittedReceiptBackup("receipt-${id}", id, "fingerprint-${id}")
            },
        )
        val file = Files.createTempDirectory("faro-v5-history-restore").resolve("reality.db")
        val store = RealityStore(file.toString())
        try {
            store.backup.restoreOrVerify(restored)
            val first = store.receiptHistory.query(RealityReceiptHistoryQuery(limit = 2))
            assertEquals(listOf("tx-a", "tx-b"), first.receipts.map { it.transactionId.value })
            val legacy = store.timedReceiptHistory.query(RealityTimedReceiptQuery())
            assertTrue(legacy.legacyUndated)
            assertTrue(legacy.receipts.isEmpty())
            assertEquals("tx-b", first.nextCursor)
            assertFalse(first.hasCommitTimestamps)
            val second = store.receiptHistory.query(
                RealityReceiptHistoryQuery(afterTransactionId = first.nextCursor, limit = 2)
            )
            assertEquals(listOf("tx-c"), second.receipts.map { it.transactionId.value })
            assertNull(second.nextCursor)
            val exhausted = store.receiptHistory.query(
                RealityReceiptHistoryQuery(afterTransactionId = "tx-z")
            )
            assertEquals(emptyList<Any>(), exhausted.receipts)
            assertNull(exhausted.nextCursor)
            assertThrows(IllegalArgumentException::class.java) {
                RealityReceiptHistoryQuery(limit = 0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                RealityReceiptHistoryQuery(afterTransactionId = "")
            }
        } finally {
            store.close()
        }
        assertThrows(IllegalStateException::class.java) {
            store.receiptHistory.query(RealityReceiptHistoryQuery())
        }
    }


    @Test
    fun rollbackNeverExposesRecordedReceiptTime() {
        RealityStore(":memory:", receiptClockEpochMillis = { 900L },
            transactionIdFactory = { TransactionId("tx-rollback") },
            receiptIdFactory = { ReceiptId("receipt-rollback") }).use { store ->
            val identity = store.applyAttemptIdentities.resolve(
                preparedPlan(
                    planFingerprint = PlanFingerprint("plan-rollback"),
                    idempotencyKey = IdempotencyKey("idem-rollback"),
                )
            )
            store.run(identity.transactionId) { scope ->
                store.commit(scope, identity, emptyList())
                assertThrows(IllegalStateException::class.java) {
                    store.timedReceiptHistory.query(RealityTimedReceiptQuery())
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            val result = store.timedReceiptHistory.query(RealityTimedReceiptQuery())
            assertTrue(result.receipts.isEmpty())
            assertFalse(result.legacyUndated)
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
        }
    }

    private fun preparedPlan(
        planFingerprint: PlanFingerprint,
        idempotencyKey: IdempotencyKey,
    ): PreparedPlanView {
        val contract =
            OperationContractDescriptor(
                operationId = OperationId("fixture.reality.mutation"),
                capabilityId = CapabilityId("fixture-capability"),
                canonicalExecutor = ExecutorId("fixture-executor"),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
                contractVersion = ContractVersion("1"),
                contractFingerprint = ContractFingerprint("fixture-contract-v1"),
                exposure = ContractExposure.INTERNAL_ONLY,
            )
        val operation =
            PreparedOperation(
                contract = contract,
                payload =
                    CanonicalPayload.fromEncoded(
                        "fixture.reality.v1",
                        byteArrayOf(7),
                    ),
                dependencyStamps = DependencyStampSet.empty(),
                idempotencyKey = idempotencyKey,
            )
        val plan =
            ApplyPlan.of(
                ApplyPlanNode(
                    nodeId = PlanNodeId("fixture-node"),
                    preparedOperation = operation,
                    dependsOn = emptySet(),
                )
            )
        return object : PreparedPlanView {
            override val applyPlan: ApplyPlan = plan
            override val planFingerprint: PlanFingerprint = planFingerprint
        }
    }
}
