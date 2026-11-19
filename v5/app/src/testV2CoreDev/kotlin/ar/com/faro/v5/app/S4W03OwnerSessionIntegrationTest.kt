package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.reminders.api.*
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03OwnerSessionIntegrationTest {
    @TempDir lateinit var root: Path

    private inner class Session : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root)
        val gate = SingleProcessMaterialWriteGate()
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selection = try {
            val planning = StorageGenerationId("d-integration-planning")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(planning, "planning", 1L),
            )
            val finance = runtime.start(
                MaintenanceOperationId("d-integration-finance"),
                StorageGenerationDescriptor(StorageGenerationId("d-integration-finance"), "finance", 2L),
                checkNotNull(pointer.compareAndSet(null, planning)), 3L,
            ) { true }
            runtime.startAuxiliary4(
                MaintenanceOperationId("d-integration-aux"),
                StorageGenerationDescriptor(StorageGenerationId("d-integration-aux"), "auxiliary", 4L),
                finance.pointer, 5L,
            ) { true }
        } catch (failure: Throwable) {
            runtime.close()
            throw failure
        }
        val graph = runtime.openCommittedFinance9(selection)
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("w03-chat")
        var foreground = true
        var sandboxCurrent = true

        init {
            runBlocking {
                assertTrue(workspace.createAndSelect(chat, 7L, IdempotencyKey("select-d-chat"))
                    is ConversationSelectionResult.Saved)
            }
        }

        val actions = S4W03OwnerSessionActions(
            selection.pointer, pointer, workspace, graph.reality, graph.auxiliary, gate,
            "fixture-android-principal", "fixture-epoch",
            { sandboxCurrent && runtime.isCurrent(selection) }, { foreground },
        )

        fun matterCreate() = MatterReviewIntent(
            MatterId.of("m1"), 0L, 10L,
            MatterReviewChange.Create(MatterReviewForm(MatterTitle.of("Revisar plano"), MatterDetail.of("Taller"))),
        )

        fun apply(review: ConversationPlanReviewSnapshot): ConversationApplyHostOutcome.Committed {
            val approved = checkNotNull(actions.decideNow(review, true))
            val committed = assertInstanceOf(ConversationApplyHostOutcome.Committed::class.java, actions.applyNow(approved))
            assertEquals(committed.receipt, graph.reality.executionReceiptLookup.find(committed.receipt.transactionId))
            return committed
        }

        override fun close() {
            actions.close()
            runtime.close()
        }
    }

    @Test fun linkedMonthlyReminderRoutesOnceAndResolvesAfterReviewedAttendance() = Session().use { rig ->
        val actions = rig.actions
        assertTrue(actions.grantMatterReadAfterExplicitRequest())
        rig.apply(checkNotNull(actions.submitMatter(rig.matterCreate(), "create-matter")))
        assertEquals("m1", actions.matterSnapshot()?.matters?.single()?.id?.value)
        assertTrue(actions.grantReminderReadAfterExplicitRequest())
        val rule = ReminderRule(
            ReminderRuleId.of("r1"), "Revisar plazo", "UTC",
            LocalDate.of(2027, 1, 31), LocalTime.of(9, 0),
            ReminderRecurrence.MONTHLY, true, "m1", 15L,
        )
        val pending = checkNotNull(actions.submitReminder(S4W03ReminderIntent(0L, S4W03ReminderChange.Create(rule)), "rule"))
        val approved = checkNotNull(actions.decideNow(pending, true))
        assertTrue(actions.applyNow(approved) is ConversationApplyHostOutcome.Denied)
        assertTrue(actions.reminderSnapshot()?.rules?.isEmpty() == true)
        assertTrue(actions.authorizeMatterReference(MatterId.of("m1")))
        val created = assertInstanceOf(ConversationApplyHostOutcome.Committed::class.java, actions.applyNow(approved))
        assertEquals(created.receipt, rig.graph.reality.executionReceiptLookup.find(created.receipt.transactionId))

        val due = Instant.parse("2027-02-28T09:00:00Z").toEpochMilli()
        val next = ReminderCivilExpansion.expand(rule, due, due + 1).single()
        val routed = assertInstanceOf(
            S4W03NoticeReconcileResult.Saved::class.java, actions.reconcileLocalNoticesAfterExplicitRequest(due, due + 1),
        )
        assertEquals(1, routed.created)
        assertEquals(next.id.value, rig.graph.auxiliary.readNotices().single().occurrenceRef.value)
        assertEquals(0, assertInstanceOf(S4W03NoticeReconcileResult.Saved::class.java,
            actions.reconcileLocalNoticesAfterExplicitRequest(due, due + 1)).created)
        val acknowledged = ReminderOccurrence(
            next.id, rule.id, due, ReminderOccurrenceStatus.ACKNOWLEDGED, due + 10, due + 10,
        )
        val attendance = checkNotNull(actions.submitReminder(
            S4W03ReminderIntent(1L, S4W03ReminderChange.Attend(acknowledged)), "attend",
        ))
        rig.apply(attendance)
        assertEquals(1, assertInstanceOf(S4W03NoticeReconcileResult.Saved::class.java,
            actions.reconcileLocalNoticesAfterExplicitRequest(due, due + 1)).resolved)
        assertEquals(NoticeRelevance.RESOLVED, rig.graph.auxiliary.readNotices().single().relevance)
        assertTrue(rig.graph.auxiliary.readNotificationProjectionCandidateIds(8).isEmpty())
        assertEquals(MatterStatus.OPEN, actions.matterSnapshot()?.matters?.single()?.status)
    }

    @Test fun pauseAndSandboxRevocationRequireFreshViewAuthorityForDurableActions() = Session().use { rig ->
        val actions = rig.actions
        assertNull(actions.matterSnapshot())
        assertTrue(actions.grantMatterReadAfterExplicitRequest())
        val review = checkNotNull(actions.submitMatter(rig.matterCreate(), "review"))
        rig.foreground = false
        assertNull(actions.decideNow(review, true))
        actions.clearViewGrants()
        rig.foreground = true
        assertNull(actions.matterSnapshot())
        assertNull(actions.decideNow(review, true))
        assertTrue(actions.grantMatterReadAfterExplicitRequest())
        val approved = checkNotNull(actions.decideNow(review, true))
        rig.sandboxCurrent = false
        assertTrue(actions.applyNow(approved) is ConversationApplyHostOutcome.Denied)
        assertTrue(rig.graph.reality.matterPersistence.read().matters.isEmpty())
    }

    @Test fun ownerLifecycleUsesReviewAndReceiptsAndRejectsOtherChatOrAnAbaPointer() = Session().use { rig ->
        val actions = rig.actions
        assertTrue(actions.grantMatterReadAfterExplicitRequest())
        rig.apply(checkNotNull(actions.submitMatter(rig.matterCreate(), "m")))
        val edit = MatterReviewIntent(
            MatterId.of("m1"), 1L, 12L,
            MatterReviewChange.Transition(MatterStatus.OPEN, MatterStatus.WAITING, "Esperar respuesta"),
        )
        val review = checkNotNull(actions.submitMatter(edit, "wait"))
        runBlocking {
            assertTrue(actions.reviews(ConversationId("other-chat")).isEmpty())
        }
        rig.apply(review)
        val matter = requireNotNull(actions.matterSnapshot()).matters.single()
        assertEquals(MatterStatus.WAITING, matter.status)
        assertEquals(2, matter.details.history.size)
        val pinned = rig.selection.pointer.generationId
        val other = StorageGenerationId("other")
        checkNotNull(rig.pointer.compareAndSet(pinned, other))
        checkNotNull(rig.pointer.compareAndSet(other, pinned))
        assertNull(actions.matterSnapshot())
        assertFalse(actions.grantReminderReadAfterExplicitRequest())
        assertEquals(S4W03NoticeReconcileResult.Denied, actions.reconcileLocalNoticesAfterExplicitRequest(0L, 1L))
    }
}
