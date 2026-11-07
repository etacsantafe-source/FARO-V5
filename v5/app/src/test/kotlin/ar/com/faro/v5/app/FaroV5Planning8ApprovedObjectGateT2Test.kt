package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Native Workspace human Review + durable pointer; ACL is a TEST ONLY probe. */
class FaroV5Planning8ApprovedObjectGateT2Test {
    @TempDir lateinit var root: Path
    private val generation = StorageGenerationId("planning-eight")

    private fun reminder(linkedMatter: String? = null) = ReminderRule(
        ReminderRuleId.of("rule-a"), "Recordar turno", "America/Argentina/Cordoba",
        LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
        ReminderRecurrence.ONCE, true, linkedMatter, 1000L,
    )

    private fun monthly(paid: Boolean = false) = MonthlyPlanItem(
        MonthlyPlanItemId.of("bill-a"), "Factura mensual", YearMonth.of(2026, 11),
        LocalDate.of(2026, 11, 21),
        if (paid) MonthlyPlanItemStatus.PAID_CONFIRMED else MonthlyPlanItemStatus.PLANNED,
        null, null, if (paid) 1001L else null, 1002L,
    )

    private fun descriptor(reminder: Boolean): OperationContractDescriptor =
        OperationContractDescriptor(
            if (reminder) ReminderOperations.CREATE else MonthlyPlanOperations.ITEM_ADD,
            if (reminder) ReminderOperations.CAPABILITY_ID else MonthlyPlanOperations.CAPABILITY_ID,
            if (reminder) ReminderOperations.EXECUTOR_ID else MonthlyPlanOperations.EXECUTOR_ID,
            ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
            ContractFingerprint(if (reminder) "test-reminder-create-v1" else "test-monthly-add-v1"),
            ContractExposure.BINDABLE,
        )

    private fun plan(
        contract: OperationContractDescriptor,
        payload: CanonicalPayload,
        multi: Boolean = false,
    ): PreparedPlan {
        fun node(id: String) = ApplyPlanNode(
            PlanNodeId(id), PreparedOperation(
                contract, payload, DependencyStampSet.of(
                    DependencyStamp(AuthorityBoundaryId("reality.planning8"),
                        DependencyKey("owner"), DependencyStampValue("revision:0"))
                ), IdempotencyKey("op-" + id),
            ), emptySet(),
        )
        return if (multi) PreparedPlan.of(node("first"), node("second"))
            else PreparedPlan.of(node("first"))
    }

    private fun reminderPlan(linkedMatter: String? = null): PreparedPlan =
        plan(descriptor(true), ReminderCreatePayload.payload(reminder(linkedMatter)))

    private fun monthlyPlan(paid: Boolean = false): PreparedPlan =
        plan(descriptor(false), MonthlyPlanItemAddPayload.payload(monthly(paid)))

    private class Case(
        val workspace: ConversationWorkspaceStore,
        val review: ConversationPlanReviewSnapshot,
        val pointer: FileActiveGenerationPointer,
    ) : AutoCloseable {
        override fun close() = workspace.close()
    }

    private fun openCase(plan: PreparedPlan, approve: Boolean = true): Case {
        val pointer = FileActiveGenerationPointer(root)
        assertNotNull(pointer.compareAndSet(null, generation))
        val ws = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace.db").toString(), CanonicalPreparedPlanEnvelopeVerifier,
        )
        try {
            val conversationId = ConversationId("planning-chat")
            assertTrue(kotlinx.coroutines.runBlocking {
                ws.createAndSelect(conversationId, 100L, IdempotencyKey("create-chat"))
            } is ConversationSelectionResult.Saved)
            val proposal = ws.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversationId, "review-planning", ws.draftSnapshot(conversationId)!!.authorityToken,
                ws.snapshot().revision, plan.planFingerprint, "Confirmar alta planificada",
                PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("create-review"),
            )) as WorkspacePlanReviewWriteResult.Saved
            val review = if (approve) {
                (kotlinx.coroutines.runBlocking {
                    ws.decide(ConversationPlanReviewIntent.Approve(proposal.review.context()),
                        IdempotencyKey("human-approval"))
                } as ConversationReviewDecision.Saved).review
            } else proposal.review
            return Case(ws, review, pointer)
        } catch (failure: Throwable) {
            ws.close()
            throw failure
        }
    }

    private fun guard(
        case: Case,
        plan: PreparedPlan,
        acl: Planning8ObjectAclReadPort? = null,
        ready: () -> Boolean = { true },
        withCatalog: Boolean = true,
    ): FaroV5Planning8ApprovedObjectGate {
        val descriptor = plan.applyPlan.nodesInTopologicalOrder().first().preparedOperation.contract
        return FaroV5Planning8ApprovedObjectGate(
            generation.value, case.pointer, ready, case.workspace,
            if (withCatalog) OperationContractLookupPort { id ->
                descriptor.takeIf { it.operationId == id }
            } else null,
            acl,
        )
    }

    private fun grantFor(expectedId: String, expectedOp: OperationId): Planning8ObjectAclReadPort =
        Planning8ObjectAclReadPort { scope, pointer ->
            if (scope.objectId == expectedId && scope.operationId == expectedOp &&
                pointer.generationId == generation
            ) "owner-epoch-1" else null
        }

    @Test fun approvedReminderExactObjectPassesReadOnlyCheck() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            assertTrue(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun approvedMonthlyPlanExactObjectPassesReadOnlyCheck() {
        val plan = monthlyPlan()
        openCase(plan).use { c ->
            assertTrue(guard(c, plan, grantFor("bill-a", MonthlyPlanOperations.ITEM_ADD))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun missingAclOrOfficialCatalogDeniesByDefault() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            assertFalse(guard(c, plan).hasCurrentApprovedObjectReview(c.review.context()))
            assertFalse(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE),
                withCatalog = false).hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun otherObjectOrOperationDoesNotInheritGrant() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            assertFalse(guard(c, plan, grantFor("other-rule", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
            assertFalse(guard(c, plan, grantFor("rule-a", MonthlyPlanOperations.ITEM_ADD))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun revocationBetweenReadsDeniesOldEpoch() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            var reads = 0
            val acl = Planning8ObjectAclReadPort { _, _ ->
                reads++
                if (reads == 1) "owner-epoch-1" else null
            }
            assertFalse(guard(c, plan, acl).hasCurrentApprovedObjectReview(c.review.context()))
            assertEquals(2, reads)
        }
    }

    @Test fun pointerSwitchAwayAndBackDeniesAbaEvenIfSameGeneration() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            val start = c.pointer.read()!!
            val acl = Planning8ObjectAclReadPort { _, _ ->
                assertNotNull(c.pointer.compareAndSet(generation, StorageGenerationId("other")))
                assertNotNull(c.pointer.compareAndSet(StorageGenerationId("other"), generation))
                "owner-epoch-1"
            }
            assertFalse(guard(c, plan, acl).hasCurrentApprovedObjectReview(c.review.context()))
            assertTrue(c.pointer.read()!!.sequence > start.sequence)
        }
    }

    @Test fun pendingHumanReviewNeverPasses() {
        val plan = reminderPlan()
        openCase(plan, approve = false).use { c ->
            assertFalse(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun staleOrForgedReviewContextNeverPasses() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            val g = guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
            assertFalse(g.hasCurrentApprovedObjectReview(
                c.review.context().copy(expectedRevision = 0L)))
            assertFalse(g.hasCurrentApprovedObjectReview(
                c.review.context().copy(reviewId = "other")))
        }
    }

    @Test fun multiNodePlanIsNotSilentlyAuthorizedAsOneObject() {
        val descriptor = descriptor(true)
        val plan = plan(descriptor, ReminderCreatePayload.payload(reminder()), multi = true)
        openCase(plan).use { c ->
            assertFalse(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun corruptedReminderPayloadDeniesDespiteValidEnvelope() {
        val desc = descriptor(true)
        val plan = plan(desc, CanonicalPayload.fromEncoded(ReminderCreatePayload.FORMAT_ID,
            byteArrayOf(0, 1, 2)))
        openCase(plan).use { c ->
            assertFalse(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun reminderLinkedToMatterRequiresSeparateObjectAcl() {
        val plan = reminderPlan(linkedMatter = "matter-private")
        openCase(plan).use { c ->
            assertFalse(guard(c, plan, grantFor("rule-a", ReminderOperations.CREATE))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun monthlyPaymentConfirmedCannotBeSpoofedAsPlannedAdd() {
        val plan = monthlyPlan(paid = true)
        openCase(plan).use { c ->
            assertFalse(guard(c, plan, grantFor("bill-a", MonthlyPlanOperations.ITEM_ADD))
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }

    @Test fun discardedHumanReviewDuringAclReadIsDenied() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            var discarded = false
            val acl = Planning8ObjectAclReadPort { _, _ ->
                if (!discarded) {
                    val decision = kotlinx.coroutines.runBlocking {
                        c.workspace.decide(
                            ConversationPlanReviewIntent.Discard(c.review.context()),
                            IdempotencyKey("discard-during-acl"),
                        )
                    }
                    assertTrue(decision is ConversationReviewDecision.Saved)
                    discarded = true
                }
                "owner-epoch-1"
            }
            assertFalse(guard(c, plan, acl)
                .hasCurrentApprovedObjectReview(c.review.context()))
            assertTrue(discarded)
            assertEquals(ConversationPlanReviewState.DISCARDED,
                kotlinx.coroutines.runBlocking {
                    c.workspace.reviews(c.review.conversationId).single().state
                })
        }
    }

    @Test fun hostReadinessLostDuringAclCheckDenies() {
        val plan = reminderPlan()
        openCase(plan).use { c ->
            var ready = true
            val acl = Planning8ObjectAclReadPort { _, _ ->
                ready = false
                "owner-epoch-1"
            }
            assertFalse(guard(c, plan, acl, { ready })
                .hasCurrentApprovedObjectReview(c.review.context()))
        }
    }
}
