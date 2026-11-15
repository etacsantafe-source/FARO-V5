package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Seven D2 gates, including a REAL Workspace4 canonical Review, no Apply. */
class S4W00D2CompositeApprovedReviewScopeTest {
    @TempDir lateinit var root: Path

    private class Rig(
        val generation: StorageGenerationId,
        val pointer: FileActiveGenerationPointer,
        val workspace: ConversationWorkspaceStore,
        val plan: PreparedPlan,
        val review: ConversationPlanReviewSnapshot,
        val reminderContract: OperationContractDescriptor,
        val monthlyContract: OperationContractDescriptor,
    ) : AutoCloseable {
        fun official(id: OperationId): OperationContractDescriptor? = when (id) {
            ReminderOperations.CREATE -> reminderContract
            MonthlyPlanOperations.ITEM_ADD -> monthlyContract
            else -> null
        }
        fun reader(
            catalog: OperationContractLookupPort = OperationContractLookupPort { official(it) },
            pointerPort: ActiveGenerationPointerPort = pointer,
            ready: () -> Boolean = { true },
            source: TrustedConversationApplyReviewPort = workspace,
        ) = S4W00D2CompositeApprovedReviewScope(
            generation.value, pointerPort, ready, source, catalog,
        )
        override fun close() { workspace.close() }
    }

    private fun rig(
        directory: Path = root,
        reverse: Boolean = false,
        count: Int = 2,
        badPayload: Boolean = false,
        approve: Boolean = true,
    ): Rig {
        val generation = StorageGenerationId("s4-w00d2-generation")
        val pointer = FileActiveGenerationPointer(directory)
        requireNotNull(pointer.compareAndSet(null, generation))
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            directory.resolve("workspace.db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        val reminderContract = OperationContractDescriptor(
            ReminderOperations.CREATE, ReminderOperations.CAPABILITY_ID,
            ReminderOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"), ContractFingerprint("w00d2-reminder-v1"),
            ContractExposure.BINDABLE,
        )
        val monthlyContract = OperationContractDescriptor(
            MonthlyPlanOperations.ITEM_ADD, MonthlyPlanOperations.CAPABILITY_ID,
            MonthlyPlanOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"), ContractFingerprint("w00d2-monthly-v1"),
            ContractExposure.BINDABLE,
        )
        val reminder = ReminderRule(
            ReminderRuleId.of("d2-reminder"), "Prueba D2", "UTC",
            LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
            ReminderRecurrence.ONCE, true, null, 1000L,
        )
        val monthly = MonthlyPlanItem(
            MonthlyPlanItemId.of("d2-monthly"), "Cuota prevista",
            YearMonth.of(2026, 11), LocalDate.of(2026, 11, 25),
            MonthlyPlanItemStatus.PLANNED, 10000L, "ARS", null, 1000L,
        )
        val reminderPrepared = PreparedOperation(
            reminderContract, ReminderCreatePayload.payload(reminder),
            DependencyStampSet.empty(), IdempotencyKey("d2-reminder-idem"),
        )
        val monthlyPrepared = PreparedOperation(
            monthlyContract,
            if (badPayload) CanonicalPayload.fromEncoded("unknown-format", byteArrayOf(4, 5))
            else MonthlyPlanItemAddPayload.payload(monthly),
            DependencyStampSet.empty(), IdempotencyKey("d2-monthly-idem"),
        )
        val firstId = PlanNodeId("d2-first")
        val first = ApplyPlanNode(
            firstId, if (reverse) monthlyPrepared else reminderPrepared, emptySet(),
        )
        val secondId = PlanNodeId("d2-second")
        val second = ApplyPlanNode(
            secondId, if (reverse) reminderPrepared else monthlyPrepared, setOf(firstId),
        )
        val nodes = mutableListOf(first)
        if (count >= 2) nodes += second
        if (count == 3) nodes += ApplyPlanNode(
            PlanNodeId("d2-third"), reminderPrepared, setOf(secondId),
        )
        val plan = PreparedPlan.of(*nodes.toTypedArray())
        val conversation = ConversationId("d2-chat")
        assertTrue(runBlocking {
            workspace.createAndSelect(conversation, 100L, IdempotencyKey("d2-create"))
        } is ConversationSelectionResult.Saved)
        val token = runBlocking { workspace.readTimeline(conversation) }!!.authorityToken
        val proposed = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            conversation, "d2-review", token, workspace.snapshot().revision,
            plan.planFingerprint, "Revisión humana D2 de prueba",
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("d2-propose"),
        )) as WorkspacePlanReviewWriteResult.Saved
        val review = if (approve) {
            (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(proposed.review.context()),
                    IdempotencyKey("d2-approve"),
                )
            } as ConversationReviewDecision.Saved).review
        } else proposed.review
        return Rig(generation, pointer, workspace, plan, review,
            reminderContract, monthlyContract)
    }

    @Test
    fun approvedCanonicalWorkspacePairYieldsTwoReadOnlyScopesWithoutApply() {
        rig().use { r ->
            val scopes = r.reader().read(r.review.context())
            assertNotNull(scopes)
            assertEquals(listOf(ReminderOperations.CREATE, MonthlyPlanOperations.ITEM_ADD),
                scopes!!.map { it.operationId })
            assertEquals(listOf("d2-reminder", "d2-monthly"), scopes.map { it.objectId })
            assertEquals(listOf(PlanNodeId("d2-first"), PlanNodeId("d2-second")),
                scopes.map { it.nodeId })
            assertEquals(ConversationPlanReviewState.APPROVED,
                runBlocking { r.workspace.reviews(r.review.conversationId) }.single().state)
        }
    }

    @Test
    fun pendingAndDiscardedReviewsAreDenied() {
        rig(root.resolve("pending"), approve = false).use { r ->
            assertNull(r.reader().read(r.review.context()))
        }
        rig(root.resolve("discarded"), approve = false).use { r ->
            val changed = runBlocking {
                r.workspace.decide(
                    ConversationPlanReviewIntent.Discard(r.review.context()),
                    IdempotencyKey("d2-discard"),
                )
            }
            assertTrue(changed is ConversationReviewDecision.Saved)
            assertNull(r.reader().read(r.review.context()))
        }
    }

    @Test
    fun descriptorRevocationDuringSecondLookupDenies() {
        rig().use { r ->
            var calls = 0
            val changing = OperationContractLookupPort { id ->
                calls += 1
                if (calls > 2) null else r.official(id)
            }
            assertNull(r.reader(catalog = changing).read(r.review.context()))
            assertTrue(calls >= 3)
        }
    }

    @Test
    fun generationOrHostLifecycleDriftDenies() {
        rig().use { r ->
            var reads = 0
            val changedPointer = object : ActiveGenerationPointerPort {
                override fun read(): ActiveGenerationPointer? {
                    reads += 1
                    return if (reads > 1) null else r.pointer.read()
                }
                override fun compareAndSet(
                    expectedGenerationId: StorageGenerationId?,
                    targetGenerationId: StorageGenerationId,
                ): ActiveGenerationPointer? = null
            }
            assertNull(r.reader(pointerPort = changedPointer).read(r.review.context()))
            var checks = 0
            assertNull(r.reader(ready = { ++checks == 1 }).read(r.review.context()))
        }
    }

    @Test
    fun oneOrThreeNodesOrInvalidPayloadFailClosed() {
        rig(root.resolve("one"), count = 1).use { r ->
            assertNull(r.reader().read(r.review.context()))
        }
        rig(root.resolve("three"), count = 3).use { r ->
            assertNull(r.reader().read(r.review.context()))
        }
        rig(root.resolve("payload"), badPayload = true).use { r ->
            assertNull(r.reader().read(r.review.context()))
        }
    }

    @Test
    fun reviewOrEnvelopeChangingBetweenReadsDeniesWithoutAdvancing() {
        rig().use { r ->
            for (mutateEnvelope in listOf(false, true)) {
                var reads = 0
                var advances = 0
                val scripted = object : TrustedConversationApplyReviewPort {
                    override fun load(context: ConversationPlanReviewContext):
                        TrustedApprovedConversationPlan? {
                        reads += 1
                        val actual = r.workspace.load(context) ?: return null
                        if (reads == 1) return actual
                        return if (mutateEnvelope) TrustedApprovedConversationPlan(
                            actual.review, actual.envelope() + byteArrayOf(0),
                        ) else TrustedApprovedConversationPlan(
                            actual.review.copy(state = ConversationPlanReviewState.DISCARDED),
                            actual.envelope(),
                        )
                    }
                    override fun advance(
                        intent: ConversationPlanReviewIntent,
                        key: IdempotencyKey,
                        attempts: ApplyAttemptLookupPort,
                        receipts: ExecutionReceiptLookupPort,
                    ): ConversationReviewDecision {
                        advances++
                        return ConversationReviewDecision.Rejected
                    }
                }
                assertNull(r.reader(source = scripted).read(r.review.context()))
                assertEquals(2, reads)
                assertEquals(0, advances)
            }
        }
    }

    @Test
    fun reversedDagOrderRetainsEachNodeOwnerAndObjectIdentity() {
        rig(reverse = true).use { r ->
            val scopes = r.reader().read(r.review.context())
            assertNotNull(scopes)
            assertEquals(listOf(MonthlyPlanOperations.ITEM_ADD, ReminderOperations.CREATE),
                scopes!!.map { it.operationId })
            assertEquals(listOf("d2-monthly", "d2-reminder"), scopes.map { it.objectId })
            assertEquals(listOf(PlanNodeId("d2-first"), PlanNodeId("d2-second")),
                scopes.map { it.nodeId })
            assertNotEquals(scopes[0].authorityId, scopes[1].authorityId)
        }
    }
}
