package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03ReminderReviewHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(
            root, gate, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val selection = try {
            val start = StorageGenerationId("reminder-d1-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(start, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("reminder-d1-cutover"),
                StorageGenerationDescriptor(
                    StorageGenerationId("reminder-d1-active"), "reminders", 2L,
                ),
                requireNotNull(pointer.compareAndSet(null, start)), 3L,
            ) { true }
        } catch (e: Throwable) {
            runtime.close()
            throw e
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("reminder-d1-chat")
        var policyAllows = true
        var linkAllows = false
        var foreground = true
        val policy = Planning8TrustedObjectPolicyPort { _, scope, _ ->
            if (!policyAllows || (
                scope.operationId == OperationId("matter.read") && !linkAllows
            )) null else "allow-current"
        }
        val host = S4W03ReminderReviewHost(
            selection.pointer, pointer, workspace, reality, gate, BuiltInContractCatalog.create(),
            "reminder-test-principal", "epoch", { "epoch" },
            { runtime.isCurrent(selection) }, { foreground }, policy,
        )

        init { select(chat) }

        fun select(conversation: ConversationId) = runBlocking {
            assertTrue(workspace.createAndSelect(
                conversation, 5L, IdempotencyKey("select-" + conversation.value),
            ) is ConversationSelectionResult.Saved)
        }

        fun rule(
            id: String = "r1",
            title: String = "Pagar el lunes",
            matter: String? = null,
            updated: Long = 100L,
        ) = ReminderRule(
            ReminderRuleId.of(id), title, "UTC",
            LocalDate.of(2026, 11, 30), LocalTime.of(9, 0),
            ReminderRecurrence.MONTHLY, true, matter, updated,
        )

        fun submit(
            key: String,
            change: S4W03ReminderChange = S4W03ReminderChange.Create(rule()),
            revision: Long = reality.planningDueReadOwnersForTrustedHost().reminders.read().revision,
        ): ConversationPlanReviewSnapshot {
            val intent = S4W03ReminderIntent(revision, change)
            return (host.submit(chat, intent, key) as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun approve(r: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(r.context()),
                    IdempotencyKey("approve-" + r.reviewId),
                )
            } as ConversationReviewDecision.Saved).review

        fun discard(r: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Discard(r.context()),
                    IdempotencyKey("discard-" + r.reviewId),
                )
            } as ConversationReviewDecision.Saved).review

        fun apply(r: ConversationPlanReviewSnapshot) =
            host.executeAfterExplicitConfirmation(r.context())

        fun createMatter(id: String = "m1") {
            val matterHost = S4W03MatterReviewHost(
                selection.pointer, pointer, workspace, reality, gate,
                BuiltInContractCatalog.create(),
                "trusted-matter-principal", "epoch", { "epoch" },
                { runtime.isCurrent(selection) }, { foreground },
                Planning8TrustedObjectPolicyPort { _, _, _ -> "matter-current" },
            )
            try {
                val intent = MatterReviewIntent(
                    MatterId.of(id), reality.matterPersistence.read().revision, 100L,
                    MatterReviewChange.Create(
                        MatterReviewForm(
                            MatterTitle.of("Asunto vinculado"),
                            MatterDetail.of("Datos"),
                        ),
                    ),
                )
                val pending = (matterHost.submit(
                    chat, intent, "linked-matter-create",
                ) as WorkspacePlanReviewWriteResult.Saved).review
                val confirmed = approve(pending)
                assertTrue(matterHost.executeAfterExplicitConfirmation(
                    confirmed.context(),
                ) is ConversationApplyHostOutcome.Committed)
            } finally {
                matterHost.close()
            }
        }

        fun ack(r: ReminderRule): ReminderOccurrence {
            val due = r.anchorLocalDate.atTime(r.anchorLocalTime)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            val projected = ReminderCivilExpansion.expand(r, due, due + 1L).single()
            return ReminderOccurrence(
                projected.id, r.id, due, ReminderOccurrenceStatus.ACKNOWLEDGED,
                due + 5000L, due + 5000L,
            )
        }

        override fun close() {
            host.close()
            runtime.close()
        }
    }

    @Test fun createEditAndDurableReceipts() = Rig().use { rig ->
        val pending = rig.submit("create")
        assertEquals(pending, rig.submit("create"))
        assertTrue(rig.apply(pending) is ConversationApplyHostOutcome.Denied)
        val ready = rig.approve(pending)
        val created = rig.apply(ready) as ConversationApplyHostOutcome.Committed
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        val original = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single()
        val edited = rig.approve(rig.submit(
            "update", S4W03ReminderChange.Update(
                original, original.copy(title = "Título modificado", updatedAtEpochMillis = 200L),
            ),
        ))
        val updated = rig.apply(edited) as ConversationApplyHostOutcome.Committed
        assertEquals(2L, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().revision)
        assertEquals("Título modificado", rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single().title)
        for (receipt in listOf(created.receipt, updated.receipt)) {
            assertEquals(receipt, rig.reality.executionReceiptLookup.find(receipt.transactionId))
        }
    }

    @Test fun cancelKeepsRuleAndAcknowledgedHistory() = Rig().use { rig ->
        rig.apply(rig.approve(rig.submit("create")))
        val rule = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single()
        assertTrue(rig.apply(rig.approve(rig.submit(
            "attend", S4W03ReminderChange.Attend(rig.ack(rule)),
        ))) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(rig.approve(rig.submit(
            "cancel", S4W03ReminderChange.Cancel(rule, 201L),
        ))) is ConversationApplyHostOutcome.Committed)
        val saved = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read()
        assertEquals(3L, saved.revision)
        assertFalse(saved.rules.single().enabled)
        assertEquals(ReminderOccurrenceStatus.ACKNOWLEDGED, saved.occurrences.single().status)
    }

    @Test fun attendRejectsForgedCivilIdAndCannotDoubleAcknowledge() = Rig().use { rig ->
        rig.apply(rig.approve(rig.submit("create")))
        val rule = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single()
        val valid = rig.ack(rule)
        val forged = valid.copy(id = ReminderOccurrenceId.of("fake"))
        assertFalse(S4W03ReminderIntent(1L,
            S4W03ReminderChange.Attend(forged)).validOn(rig.reality.planningDueReadOwnersForTrustedHost().reminders.read()))
        val ready = rig.approve(rig.submit(
            "valid-ack", S4W03ReminderChange.Attend(valid),
        ))
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Committed)
        assertFalse(S4W03ReminderIntent(2L,
            S4W03ReminderChange.Attend(valid)).validOn(rig.reality.planningDueReadOwnersForTrustedHost().reminders.read()))
        assertEquals(1, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().occurrences.size)
    }

    @Test fun concurrentStaleAndDiscardNeverOverwrite() = Rig().use { rig ->
        rig.apply(rig.approve(rig.submit("create")))
        val old = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single()
        val first = rig.approve(rig.submit(
            "edit-one", S4W03ReminderChange.Update(old,
                old.copy(title = "Uno", updatedAtEpochMillis = 201L)),
        ))
        val stale = rig.approve(rig.submit(
            "edit-two", S4W03ReminderChange.Update(old,
                old.copy(title = "Dos", updatedAtEpochMillis = 202L)),
        ))
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(stale) is ConversationApplyHostOutcome.Denied)
        val discarded = rig.discard(rig.submit(
            "discard", S4W03ReminderChange.Cancel(
                rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single(), 500L,
            ),
        ))
        assertTrue(rig.apply(discarded) is ConversationApplyHostOutcome.Denied)
        assertEquals("Uno", rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single().title)
    }

    @Test fun foregroundPolicyAndSelectionDeny() = Rig().use { rig ->
        val ready = rig.approve(rig.submit("deny"))
        rig.policyAllows = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.policyAllows = true
        rig.foreground = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.foreground = true
        rig.select(ConversationId("another-reminder-chat"))
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().revision)
    }

    @Test fun linkedMatterRequiresIndependentGrant() = Rig().use { rig ->
        rig.createMatter("m1")
        val pending = rig.submit("linked", S4W03ReminderChange.Create(rig.rule(matter = "m1")))
        val ready = rig.approve(pending)
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().revision)
        rig.linkAllows = true
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Committed)
        assertEquals("m1", rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().rules.single().linkedMatterId)
        assertEquals(1L, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read().revision)
        assertEquals(1L, rig.reality.matterPersistence.read().revision)
    }

    @Test fun canonicalTamperingAndReconcileOnlyFailClosed() = Rig().use { rig ->
        val before = rig.reality.planningDueReadOwnersForTrustedHost().reminders.read()
        val intent = S4W03ReminderIntent(0L,
            S4W03ReminderChange.Create(rig.rule()))
        val original = S4W03ReminderMutationPayload.encode(intent)
        assertEquals(intent, S4W03ReminderMutationPayload.decode(original))
        assertNull(S4W03ReminderMutationPayload.decode(CanonicalPayload.fromEncoded(
            S4W03ReminderMutationPayload.FORMAT_ID,
            original.copyBytes() + byteArrayOf(1),
        )))
        val pending = rig.approve(rig.submit("receipt-only"))
        assertTrue(rig.host.reconcileCommittedOnly(
            pending.context(),
        ) is ConversationApplyHostOutcome.Denied)
        assertEquals(before, rig.reality.planningDueReadOwnersForTrustedHost().reminders.read())
    }
}
