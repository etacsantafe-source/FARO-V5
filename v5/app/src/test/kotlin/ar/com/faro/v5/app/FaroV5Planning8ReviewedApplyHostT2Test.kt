package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Real Workspace4 + Reality8 SQLite mutations/receipts, but only JVM TEST
 * Android facts fixtures. No physical user tap or production/DEV UI wiring.
 */
class FaroV5Planning8ReviewedApplyHostT2Test {
    @TempDir lateinit var root: Path
    private val appId = "ar.com.faro.alpha.v2coredev"

    private inner class Case(
        val monthly: Boolean = false,
        approve: Boolean = true,
        failReceipt: Boolean = false,
        receiptClock: (() -> Long)? = null,
    ) : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val generation = StorageGenerationId("n033c2b4-real-apply-generation")
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val realityPath = root.resolve("reality.db").toString()
        val workspacePath = root.resolve("workspace.db").toString()
        val tx = TransactionId("tx-real-planning8")
        val reality = RealityStore(
            realityPath, planningStage8 = true, materialWriteGate = gate,
            transactionIdFactory = { tx },
            receiptIdFactory = {
                if (failReceipt) error("injected-receipt-failure")
                ReceiptId("receipt-real-planning8")
            },
            receiptClockEpochMillis = { receiptClock?.invoke() ?: 2000L },
        )
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val operation = if (monthly) MonthlyPlanOperations.ITEM_ADD else ReminderOperations.CREATE
        val descriptor = OperationContractDescriptor(
            operation, if (monthly) MonthlyPlanOperations.CAPABILITY_ID
                else ReminderOperations.CAPABILITY_ID,
            if (monthly) MonthlyPlanOperations.EXECUTOR_ID else ReminderOperations.EXECUTOR_ID,
            ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
            ContractFingerprint("n033c2b4-real-host-" + operation.value),
            ContractExposure.BINDABLE,
        )
        val catalog = OperationContractLookupPort { id ->
            descriptor.takeIf { it.operationId == id }
        }
        val conversation = ConversationId("real-planning8-host-chat")
        var ready = true
        var epoch: String? = "host-epoch-1"
        var observedUid = 10542
        val facts = AndroidSandboxFactsReadPort {
            AndroidSandboxProcessFacts(
                processUid = observedUid, applicationUid = 10542,
                processIsApplicationUid = true, processIsIsolated = false,
                processIsSdkSandbox = false, applicationId = appId,
            )
        }
        val sandbox = FaroV5AndroidSandboxPrincipal(
            appId, pinned, pointer, "host-epoch-1", { epoch }, facts,
        )
        val plan: PreparedPlan
        val review: ConversationPlanReviewSnapshot
        val host: FaroV5Planning8ReviewedApplyHost

        init {
            val payload = if (monthly) MonthlyPlanItemAddPayload.payload(
                MonthlyPlanItem(
                    MonthlyPlanItemId.of("monthly-rent"), "Alquiler", YearMonth.of(2026, 11),
                    LocalDate.of(2026, 11, 25), MonthlyPlanItemStatus.PLANNED,
                    10000L, "ARS", null, 1000L,
                )
            ) else ReminderCreatePayload.payload(
                ReminderRule(
                    ReminderRuleId.of("remember-appointment"), "Recordar cita", "UTC",
                    LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
                    ReminderRecurrence.ONCE, true, null, 1000L,
                )
            )
            val dependency = if (monthly) MonthlyPlanOwnerDependency.stamp(
                reality.planningBackup.snapshot().monthlyPlan,
            ) else ReminderOwnerDependency.stamp(
                reality.planningBackup.snapshot().reminders,
            )
            plan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("create-planning-object"),
                PreparedOperation(descriptor, payload, DependencyStampSet.of(dependency),
                    IdempotencyKey("real-planning-host-operation")),
                emptySet(),
            ))
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("real-planning-host-chat-create"),
                )
            } is ConversationSelectionResult.Saved)
            val token = runBlocking { workspace.readTimeline(conversation) }!!.authorityToken
            val proposed = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "real-planning-review", token, workspace.snapshot().revision,
                plan.planFingerprint, "Confirmar alta planificada",
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("real-planning-create-review"),
            )) as WorkspacePlanReviewWriteResult.Saved
            review = if (approve) {
                (runBlocking {
                    workspace.decide(
                        ConversationPlanReviewIntent.Approve(proposed.review.context()),
                        IdempotencyKey("real-planning-human-approved"),
                    )
                } as ConversationReviewDecision.Saved).review
            } else proposed.review
            host = FaroV5Planning8ReviewedApplyHost(
                generationId = generation.value, pinned = pinned, activePointer = pointer,
                workspace = workspace, reality = reality, materialGate = gate,
                sandbox = sandbox, catalog = catalog, expectedHostEpoch = "host-epoch-1",
                hostEpochOrNull = { epoch }, hostReady = { ready },
            )
        }

        fun apply() = host.executeAfterExplicitHumanRequest(review.context())

        override fun close() {
            host.close()
            workspace.close()
            reality.close()
        }
    }

    @Test fun canonicalReminderCreateCommitsOnceWithRealReceiptAndReopens() {
        val path = root.resolve("reality.db").toString()
        val workspacePath = root.resolve("workspace.db").toString()
        lateinit var receipt: ExecutionReceipt
        Case().use { f ->
            val committed = f.apply() as ConversationApplyHostOutcome.Committed
            receipt = committed.receipt
            assertEquals(ConversationPlanReviewState.COMMITTED, committed.review.state)
            assertEquals(receipt, committed.review.receipt)
            assertEquals(f.tx, receipt.transactionId)
            assertEquals("remember-appointment",
                f.reality.planningBackup.snapshot().reminders.rules.single().id.value)
            assertEquals(receipt, f.reality.executionReceiptLookup.find(f.tx))
            assertTrue(f.reality.planningBackup.snapshot().monthlyPlan.items.isEmpty())
            assertTrue(f.apply() is ConversationApplyHostOutcome.Denied)
            assertEquals(1, f.reality.planningBackup.snapshot().reminders.rules.size)
        }
        RealityStore(path, planningStage8 = true).use { reality ->
            assertEquals("remember-appointment",
                reality.planningBackup.snapshot().reminders.rules.single().id.value)
            assertEquals(receipt, reality.executionReceiptLookup.find(receipt.transactionId))
        }
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
        ).use { workspace ->
            assertEquals(ConversationPlanReviewState.COMMITTED,
                runBlocking { workspace.reviews(conversationId()) }.single().state)
        }
    }

    private fun conversationId() = ConversationId("real-planning8-host-chat")

    @Test fun canonicalMonthlyItemAddCommitsPlannedOnlyAndReceipt() {
        Case(monthly = true).use { f ->
            val committed = f.apply() as ConversationApplyHostOutcome.Committed
            assertEquals(f.tx, committed.receipt.transactionId)
            assertEquals(ConversationPlanReviewState.COMMITTED, committed.review.state)
            assertEquals("monthly-rent",
                f.reality.planningBackup.snapshot().monthlyPlan.items.single().id.value)
            assertEquals(MonthlyPlanItemStatus.PLANNED,
                f.reality.planningBackup.snapshot().monthlyPlan.items.single().status)
            assertTrue(f.reality.planningBackup.snapshot().reminders.rules.isEmpty())
            assertNotNull(f.reality.executionReceiptLookup.find(f.tx))
        }
    }

    @Test fun pendingHumanReviewNeverExecutesOrCreatesReceipt() {
        Case(approve = false).use { f ->
            assertTrue(f.apply() is ConversationApplyHostOutcome.Denied)
            assertTrue(f.reality.planningBackup.snapshot().reminders.rules.isEmpty())
            assertNull(f.reality.executionReceiptLookup.find(f.tx))
            assertEquals(ConversationPlanReviewState.PENDING,
                runBlocking { f.workspace.reviews(f.conversation) }.single().state)
        }
    }

    @Test fun discardedApprovedReviewCannotCreatePlanningObject() {
        Case().use { f ->
            assertTrue(runBlocking {
                f.workspace.decide(
                    ConversationPlanReviewIntent.Discard(f.review.context()),
                    IdempotencyKey("real-planning-discard"),
                )
            } is ConversationReviewDecision.Saved)
            assertTrue(f.apply() is ConversationApplyHostOutcome.Denied)
            assertTrue(f.reality.planningBackup.snapshot().reminders.rules.isEmpty())
            assertNull(f.reality.executionReceiptLookup.find(f.tx))
        }
    }

    @Test fun receiptFailureRollsBackObjectAndLeavesNoFalseReceipt() {
        Case(failReceipt = true).use { f ->
            val outcome = f.apply()
            assertEquals(ConversationApplyHostOutcome.RecoveryRequired, outcome)
            assertTrue(f.reality.planningBackup.snapshot().reminders.rules.isEmpty())
            assertNull(f.reality.executionReceiptLookup.find(f.tx))
            assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN,
                runBlocking { f.workspace.reviews(f.conversation) }.single().state)
        }
    }

    @Test fun concurrentHostCloseWaitsUntilRealSqlCommitAndReviewReconciliation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Case(receiptClock = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "receipt commit blocked" }
            2000L
        }).use { f ->
            val pool = Executors.newFixedThreadPool(2)
            try {
                val applying = pool.submit<ConversationApplyHostOutcome> { f.apply() }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val closeStarted = CountDownLatch(1)
                val closing = pool.submit {
                    closeStarted.countDown()
                    f.host.close()
                }
                assertTrue(closeStarted.await(10, TimeUnit.SECONDS))
                assertFalse(closing.isDone)
                release.countDown()
                val committed = applying.get(10, TimeUnit.SECONDS)
                assertTrue(committed is ConversationApplyHostOutcome.Committed)
                closing.get(10, TimeUnit.SECONDS)
                assertNotNull(f.reality.executionReceiptLookup.find(f.tx))
                assertEquals(1, f.reality.planningBackup.snapshot().reminders.rules.size)
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }
}
