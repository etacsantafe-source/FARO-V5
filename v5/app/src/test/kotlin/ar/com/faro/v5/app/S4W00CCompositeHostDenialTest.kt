package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * S4 W00-C NEGATIVE TEST ONLY. The existing host supports ONE reviewed object;
 * a reviewed composite with TWO existing real Planning8 owners must fail closed
 * before BEGIN_APPLY/Reality mutation. No Household+Finance authority is proven.
 */
class S4W00CCompositeHostDenialTest {
    @TempDir lateinit var root: Path

    @Test
    fun humanApprovedReminderThenMonthlyCompositeIsDeniedWithoutAttemptOrReceipt() {
        verifyDenied(reverse = false)
    }

    @Test
    fun humanApprovedMonthlyThenReminderCompositeIsDeniedWithoutAttemptOrReceipt() {
        verifyDenied(reverse = true)
    }

    private fun verifyDenied(reverse: Boolean) {
        val suffix = if (reverse) "reverse" else "forward"
        val gate = SingleProcessMaterialWriteGate()
        val generation = StorageGenerationId("s4-w00c-generation-" + suffix)
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val reality = RealityStore(
            root.resolve("reality-" + suffix + ".db").toString(),
            planningStage8 = true,
            materialWriteGate = gate,
            transactionIdFactory = { TransactionId("tx-w00c-" + suffix) },
            receiptIdFactory = { ReceiptId("receipt-w00c-" + suffix) },
        )
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace-" + suffix + ".db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        try {
            val reminderContract = OperationContractDescriptor(
                ReminderOperations.CREATE, ReminderOperations.CAPABILITY_ID,
                ReminderOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"), ContractFingerprint("w00c-reminder-v1"),
                ContractExposure.BINDABLE,
            )
            val monthlyContract = OperationContractDescriptor(
                MonthlyPlanOperations.ITEM_ADD, MonthlyPlanOperations.CAPABILITY_ID,
                MonthlyPlanOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"), ContractFingerprint("w00c-monthly-v1"),
                ContractExposure.BINDABLE,
            )
            val catalog = OperationContractLookupPort { id ->
                when (id) {
                    ReminderOperations.CREATE -> reminderContract
                    MonthlyPlanOperations.ITEM_ADD -> monthlyContract
                    else -> null
                }
            }
            val reminder = ReminderRule(
                ReminderRuleId.of("w00c-reminder"), "Recordatorio de prueba", "UTC",
                LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
                ReminderRecurrence.ONCE, true, null, 1000L,
            )
            val monthly = MonthlyPlanItem(
                MonthlyPlanItemId.of("w00c-monthly"), "Cuota de prueba",
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 25),
                MonthlyPlanItemStatus.PLANNED, 10000L, "ARS", null, 1000L,
            )
            val reminderPrepared = PreparedOperation(
                reminderContract, ReminderCreatePayload.payload(reminder),
                DependencyStampSet.of(ReminderOwnerDependency.stamp(
                    reality.planningBackup.snapshot().reminders,
                )),
                IdempotencyKey("w00c-reminder-" + suffix),
            )
            val monthlyPrepared = PreparedOperation(
                monthlyContract, MonthlyPlanItemAddPayload.payload(monthly),
                DependencyStampSet.of(MonthlyPlanOwnerDependency.stamp(
                    reality.planningBackup.snapshot().monthlyPlan,
                )),
                IdempotencyKey("w00c-monthly-" + suffix),
            )
            val firstId = PlanNodeId("first")
            val first = ApplyPlanNode(
                firstId,
                if (reverse) monthlyPrepared else reminderPrepared,
                emptySet(),
            )
            val second = ApplyPlanNode(
                PlanNodeId("second"),
                if (reverse) reminderPrepared else monthlyPrepared,
                setOf(firstId),
            )
            val plan = PreparedPlan.of(first, second)
            val conversation = ConversationId("w00c-chat-" + suffix)
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("w00c-create-" + suffix)
                )
            } is ConversationSelectionResult.Saved)
            val token = runBlocking { workspace.readTimeline(conversation) }!!.authorityToken
            val proposed = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "w00c-review-" + suffix, token, workspace.snapshot().revision,
                plan.planFingerprint, "Confirmar dos operaciones distintas",
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("w00c-propose-" + suffix),
            )) as WorkspacePlanReviewWriteResult.Saved
            val approved = (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(proposed.review.context()),
                    IdempotencyKey("w00c-approve-" + suffix),
                )
            } as ConversationReviewDecision.Saved).review
            assertEquals(ConversationPlanReviewState.APPROVED, approved.state)
            val appId = "ar.com.faro.alpha.v2coredev"
            val facts = AndroidSandboxFactsReadPort {
                AndroidSandboxProcessFacts(
                    processUid = 10542, applicationUid = 10542,
                    processIsApplicationUid = true, processIsIsolated = false,
                    processIsSdkSandbox = false, applicationId = appId,
                )
            }
            val sandbox = FaroV5AndroidSandboxPrincipal(
                appId, pinned, pointer, "w00c-epoch", { "w00c-epoch" }, facts,
            )
            FaroV5Planning8ReviewedApplyHost(
                generationId = generation.value, pinned = pinned, activePointer = pointer,
                workspace = workspace, committedPlans = workspace,
                reality = reality, materialGate = gate,
                sandbox = sandbox, catalog = catalog, expectedHostEpoch = "w00c-epoch",
                hostEpochOrNull = { "w00c-epoch" }, hostReady = { true },
            ).use { host ->
                val outcome = host.executeAfterExplicitHumanRequest(approved.context())
                assertTrue(outcome is ConversationApplyHostOutcome.Denied)
                val snapshot = reality.planningBackup.snapshot()
                assertTrue(snapshot.reminders.rules.isEmpty())
                assertTrue(snapshot.monthlyPlan.items.isEmpty())
                assertNull(reality.applyAttemptLookup.find(TransactionId("tx-w00c-" + suffix)))
                assertNull(reality.executionReceiptLookup.find(TransactionId("tx-w00c-" + suffix)))
                assertEquals(ConversationPlanReviewState.APPROVED,
                    runBlocking { workspace.reviews(conversation) }.single().state)
                assertEquals("ok", reality.integrityCheck())
            }
        } finally {
            workspace.close()
            reality.close()
        }
    }
}
