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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** NEW human Review is simulated in JVM only: no production Android tap. */
class FaroV5Planning8UndoReviewedApplyHostT2Test {
    @TempDir lateinit var root: Path

    private inner class Scenario(
        private val monthly: Boolean = false,
        approveUndo: Boolean = true,
        private val fakeReceipt: Boolean = false,
        private val fakeReview: Boolean = false,
        failUndoReceipt: Boolean = false,
    ) : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val generation = StorageGenerationId("n033c3-undo-reviewed-gen")
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val appId = "ar.com.faro.alpha.v2coredev"
        var hostEpoch: String? = "n033c3-review-host-epoch"
        var ready = true
        private val transactionCounter = AtomicInteger(0)
        private val receiptCounter = AtomicInteger(0)
        val reality = RealityStore(
            root.resolve("reality.db").toString(),
            planningStage8 = true, materialWriteGate = gate,
            transactionIdFactory = {
                TransactionId("n033c3-undo-tx-" + transactionCounter.incrementAndGet())
            },
            receiptIdFactory = {
                val index = receiptCounter.incrementAndGet()
                if (failUndoReceipt && index == 2) error("injected-undo-receipt-failure")
                ReceiptId("n033c3-undo-receipt-" + index)
            },
            receiptClockEpochMillis = { 2000L },
        )
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace.db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        val conversation = ConversationId("n033c3-undo-real-host-chat")
        val originalOperation = if (monthly) MonthlyPlanOperations.ITEM_ADD
            else ReminderOperations.CREATE
        val createDescriptor = OperationContractDescriptor(
            originalOperation,
            if (monthly) MonthlyPlanOperations.CAPABILITY_ID else ReminderOperations.CAPABILITY_ID,
            if (monthly) MonthlyPlanOperations.EXECUTOR_ID else ReminderOperations.EXECUTOR_ID,
            ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
            ContractFingerprint("n033c3-original-" + originalOperation.value),
            ContractExposure.BINDABLE,
        )
        val undoDescriptor = OperationContractDescriptor(
            OperationId("undo.execute"), CapabilityId("N-016"),
            ExecutorId("undo-coordinator"), ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"), ContractFingerprint("n033c3-undo-execute"),
            ContractExposure.BINDABLE,
        )
        val catalog = OperationContractLookupPort { id ->
            listOf(createDescriptor, undoDescriptor).singleOrNull { it.operationId == id }
        }
        val rule = ReminderRule(
            ReminderRuleId.of("undo-appointment"), "Turno médico", "UTC",
            LocalDate.of(2026, 11, 30), LocalTime.of(10, 0),
            ReminderRecurrence.ONCE, true, null, 1000L,
        )
        val item = MonthlyPlanItem(
            MonthlyPlanItemId.of("undo-rent"), "Alquiler", YearMonth.of(2026, 11),
            LocalDate.of(2026, 11, 30), MonthlyPlanItemStatus.PLANNED,
            25000L, "ARS", null, 1000L,
        )
        val originalPayload = if (monthly) MonthlyPlanItemAddPayload.payload(item)
            else ReminderCreatePayload.payload(rule)
        val originalReview: ConversationPlanReviewSnapshot
        val originalReceipt: ExecutionReceipt
        val undoReview: ConversationPlanReviewSnapshot
        val undoPlan: PreparedPlan
        val undoHost: FaroV5Planning8ReviewedApplyHost

        private fun makeHost(): FaroV5Planning8ReviewedApplyHost {
            val facts = AndroidSandboxFactsReadPort {
                AndroidSandboxProcessFacts(
                    processUid = 10542, applicationUid = 10542,
                    processIsApplicationUid = true, processIsIsolated = false,
                    processIsSdkSandbox = false, applicationId = appId,
                )
            }
            val sandbox = FaroV5AndroidSandboxPrincipal(
                appId, pinned, pointer, "n033c3-review-host-epoch",
                { hostEpoch }, facts,
            )
            return FaroV5Planning8ReviewedApplyHost(
                generationId = generation.value, pinned = pinned,
                activePointer = pointer, workspace = workspace, committedPlans = workspace,
                reality = reality, materialGate = gate, sandbox = sandbox,
                catalog = catalog, expectedHostEpoch = "n033c3-review-host-epoch",
                hostEpochOrNull = { hostEpoch }, hostReady = { ready },
            )
        }

        private fun createReview(
            plan: PreparedPlan,
            reviewId: String,
            approved: Boolean,
        ): ConversationPlanReviewSnapshot {
            val token = requireNotNull(runBlocking {
                workspace.readTimeline(conversation)
            }).authorityToken
            val proposed = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, reviewId, token, workspace.snapshot().revision,
                plan.planFingerprint, "Confirmar " + reviewId,
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("create-review-" + reviewId),
            )) as WorkspacePlanReviewWriteResult.Saved
            return if (approved) (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(proposed.review.context()),
                    IdempotencyKey("approve-review-" + reviewId),
                )
            } as ConversationReviewDecision.Saved).review else proposed.review
        }

        init {
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("undo-chat-create"),
                )
            } is ConversationSelectionResult.Saved)
            val createStamp = if (monthly) MonthlyPlanOwnerDependency.stamp(
                reality.planningBackup.snapshot().monthlyPlan,
            ) else ReminderOwnerDependency.stamp(
                reality.planningBackup.snapshot().reminders,
            )
            val originalPlan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("create-original"),
                PreparedOperation(
                    createDescriptor, originalPayload,
                    DependencyStampSet.of(createStamp),
                    IdempotencyKey("n033c3-create-original"),
                ),
                emptySet(),
            ))
            val approvedOriginal = createReview(originalPlan, "source-created", true)
            val created = makeHost().use { it.executeAfterExplicitHumanRequest(
                approvedOriginal.context(),
            ) }
            assertTrue(created is ConversationApplyHostOutcome.Committed)
            created as ConversationApplyHostOutcome.Committed
            originalReview = created.review
            originalReceipt = created.receipt

            val oldReceipt = if (fakeReceipt)
                originalReceipt.copy(receiptId = ReceiptId("wrong-receipt"))
                else originalReceipt
            val oldContext = if (fakeReview)
                originalReview.context().copy(reviewId = "wrong-origin-review")
                else originalReview.context()
            val intent = UndoExecuteIntent(
                oldContext, oldReceipt, originalOperation,
                if (monthly) item.id.value else rule.id.value,
                originalPayload, 3000L,
            )
            val undoStamp = if (monthly) MonthlyPlanOwnerDependency.stamp(
                reality.planningBackup.snapshot().monthlyPlan,
            ) else ReminderOwnerDependency.stamp(
                reality.planningBackup.snapshot().reminders,
            )
            undoPlan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("undo-original"),
                PreparedOperation(
                    undoDescriptor, UndoExecutePayload.payload(intent),
                    DependencyStampSet.of(undoStamp),
                    IdempotencyKey("n033c3-undo-new-review"),
                ),
                emptySet(),
            ))
            undoReview = createReview(undoPlan, "new-human-undo", approveUndo)
            undoHost = makeHost()
        }

        fun undo() = undoHost.executeAfterExplicitHumanRequest(undoReview.context())
        fun transactionTwo(): TransactionId = TransactionId("n033c3-undo-tx-2")
        override fun close() {
            undoHost.close()
            workspace.close()
            reality.close()
        }
    }

    @Test fun newHumanApprovedUndoReminderCommitsSeparateReceiptAndReopens() {
        val path = root.resolve("reality.db").toString()
        lateinit var source: ExecutionReceipt
        lateinit var undo: ExecutionReceipt
        Scenario().use { s ->
            assertNotNull(s.workspace.loadCommitted(s.originalReview.context()))
            assertNull(s.workspace.load(s.originalReview.context()))
            val result = s.undo() as ConversationApplyHostOutcome.Committed
            source = s.originalReceipt
            undo = result.receipt
            assertEquals(ConversationPlanReviewState.COMMITTED, result.review.state)
            assertNotEquals(source.transactionId, undo.transactionId)
            assertNotEquals(source.receiptId, undo.receiptId)
            assertFalse(s.reality.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(source, s.reality.executionReceiptLookup.find(source.transactionId))
            assertEquals(undo, s.reality.executionReceiptLookup.find(undo.transactionId))
            assertTrue(s.undo() is ConversationApplyHostOutcome.Denied)
            assertEquals(2L, s.reality.planningBackup.snapshot().reminders.revision)
        }
        RealityStore(path, planningStage8 = true).use { reloaded ->
            assertFalse(reloaded.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(source, reloaded.executionReceiptLookup.find(source.transactionId))
            assertEquals(undo, reloaded.executionReceiptLookup.find(undo.transactionId))
        }
    }

    @Test fun newHumanApprovedUndoMonthlyCancelsPlanWithoutFinancePayment() {
        Scenario(monthly = true).use { s ->
            val result = s.undo() as ConversationApplyHostOutcome.Committed
            assertNotEquals(s.originalReceipt.transactionId, result.receipt.transactionId)
            val actual = s.reality.planningBackup.snapshot()
            assertEquals(MonthlyPlanItemStatus.CANCELLED, actual.monthlyPlan.items.single().status)
            assertNull(actual.monthlyPlan.items.single().paymentConfirmedAtEpochMillis)
            assertEquals(25000L, actual.monthlyPlan.items.single().expectedAmountMinor)
            assertEquals(2L, actual.monthlyPlan.revision)
        }
    }

    @Test fun pendingUndoReviewHasNoSecondAttemptOrEffect() {
        Scenario(approveUndo = false).use { s ->
            assertTrue(s.undo() is ConversationApplyHostOutcome.Denied)
            assertNull(s.reality.applyAttemptLookup.find(s.transactionTwo()))
            assertNull(s.reality.executionReceiptLookup.find(s.transactionTwo()))
            assertTrue(s.reality.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(ConversationPlanReviewState.PENDING,
                runBlocking { s.workspace.reviews(s.conversation) }
                    .single { it.reviewId == "new-human-undo" }.state)
        }
    }

    @Test fun forgedOriginReceiptIsDeniedBeforeStartingNewApply() {
        Scenario(fakeReceipt = true).use { s ->
            assertTrue(s.undo() is ConversationApplyHostOutcome.Denied)
            assertNull(s.reality.applyAttemptLookup.find(s.transactionTwo()))
            assertTrue(s.reality.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(s.originalReceipt,
                s.reality.executionReceiptLookup.find(s.originalReceipt.transactionId))
        }
    }

    @Test fun forgedCommittedWorkspaceReviewIsDeniedBeforeNewApply() {
        Scenario(fakeReview = true).use { s ->
            assertTrue(s.undo() is ConversationApplyHostOutcome.Denied)
            assertNull(s.reality.applyAttemptLookup.find(s.transactionTwo()))
            assertTrue(s.reality.planningBackup.snapshot().reminders.rules.single().enabled)
        }
    }

    @Test fun receiptFailureRollsBackNewUndoWithoutTouchingOldReceipt() {
        Scenario(failUndoReceipt = true).use { s ->
            val result = s.undo()
            assertEquals(ConversationApplyHostOutcome.RecoveryRequired, result)
            assertTrue(s.reality.planningBackup.snapshot().reminders.rules.single().enabled)
            assertNull(s.reality.executionReceiptLookup.find(s.transactionTwo()))
            assertEquals(s.originalReceipt,
                s.reality.executionReceiptLookup.find(s.originalReceipt.transactionId))
            assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN,
                runBlocking { s.workspace.reviews(s.conversation) }
                    .single { it.reviewId == "new-human-undo" }.state)
        }
    }
}
