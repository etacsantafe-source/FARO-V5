package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
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

class S4W03ReminderNoticeReconcileHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(
            root, gate, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val selection = try {
            val source = StorageGenerationId("w03-d2a-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(source, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("w03-d2a-activation"),
                StorageGenerationDescriptor(
                    StorageGenerationId("w03-d2a-active"), "notices", 2L,
                ),
                requireNotNull(pointer.compareAndSet(null, source)), 3L,
            ) { true }
        } catch (e: Throwable) {
            runtime.close()
            throw e
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val auxiliary = graph.auxiliary
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("notice-chat")
        var foreground = true
        var policyAllows = true
        var noticeAllows = true
        var linkAllows = false
        var epoch = "epoch"
        val catalog = BuiltInContractCatalog.create()

        val reminderHost = S4W03ReminderReviewHost(
            selection.pointer, pointer, workspace, reality, gate, catalog,
            "trusted-reminder-d2a", "epoch", { "epoch" }, { runtime.isCurrent(selection) },
            { foreground }, Planning8TrustedObjectPolicyPort { _, _, _ -> "allowed-reminder" },
        )
        val noticeHost = S4W03ReminderNoticeReconcileHost(
            selection.pointer, pointer, reality, auxiliary, gate,
            "trusted-notice-d2a", "epoch", { epoch }, { runtime.isCurrent(selection) },
            { foreground }, Planning8TrustedObjectPolicyPort { _, scope, _ ->
                if (!policyAllows ||
                    (scope.operationId == OperationId("notice.route") && !noticeAllows) ||
                    (scope.operationId == OperationId("matter.read") && !linkAllows)
                ) null else "allowed-notice"
            },
        )

        init {
            runBlocking {
                assertTrue(workspace.createAndSelect(
                    chat, 5L, IdempotencyKey("select-notice"),
                ) is ConversationSelectionResult.Saved)
            }
        }

        fun rule(link: String? = null) = ReminderRule(
            ReminderRuleId.of("rule-w03d2"),
            "Presentar documentación",
            "UTC",
            LocalDate.of(2026, 11, 30),
            LocalTime.of(9, 0),
            ReminderRecurrence.ONCE,
            true,
            link,
            100L,
        )

        fun due(): Long = LocalDate.of(2026, 11, 30).atTime(9, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()

        fun snapshot() = reality.planningDueReadOwnersForTrustedHost().reminders.read()

        fun applyReminder(change: S4W03ReminderChange, key: String) {
            val pending = (reminderHost.submit(
                chat, S4W03ReminderIntent(snapshot().revision, change), key,
            ) as WorkspacePlanReviewWriteResult.Saved).review
            val approved = (runBlocking { workspace.decide(
                ConversationPlanReviewIntent.Approve(pending.context()),
                IdempotencyKey("approve-reminder-" + key),
            ) } as ConversationReviewDecision.Saved).review
            assertTrue(reminderHost.executeAfterExplicitConfirmation(
                approved.context(),
            ) is ConversationApplyHostOutcome.Committed)
        }

        fun createMatter() {
            val host = S4W03MatterReviewHost(
                selection.pointer, pointer, workspace, reality, gate, catalog,
                "trusted-matter-d2a", "epoch", { "epoch" }, { runtime.isCurrent(selection) },
                { foreground }, Planning8TrustedObjectPolicyPort { _, _, _ -> "allowed-matter" },
            )
            try {
                val intent = MatterReviewIntent(
                    MatterId.of("linked-matter"),
                    reality.matterPersistence.read().revision, 100L,
                    MatterReviewChange.Create(MatterReviewForm(
                        MatterTitle.of("Asunto pendiente"),
                        MatterDetail.of("Trámite"),
                    )),
                )
                val pending = (host.submit(chat, intent, "create-linked-matter")
                    as WorkspacePlanReviewWriteResult.Saved).review
                val approved = (runBlocking { workspace.decide(
                    ConversationPlanReviewIntent.Approve(pending.context()),
                    IdempotencyKey("approve-linked-matter"),
                ) } as ConversationReviewDecision.Saved).review
                assertTrue(host.executeAfterExplicitConfirmation(
                    approved.context(),
                ) is ConversationApplyHostOutcome.Committed)
            } finally {
                host.close()
            }
        }

        fun createReminder(link: String? = null) =
            applyReminder(S4W03ReminderChange.Create(rule(link)), "create-reminder")

        fun acknowledge() {
            val saved = snapshot().rules.single()
            val due = due()
            val occ = ReminderCivilExpansion.expand(saved, due, due + 1L).single()
            applyReminder(S4W03ReminderChange.Attend(ReminderOccurrence(
                occ.id, saved.id, due, ReminderOccurrenceStatus.ACKNOWLEDGED,
                due + 1_000L, due + 1_000L,
            )), "ack-reminder")
        }

        override fun close() {
            noticeHost.close()
            reminderHost.close()
            runtime.close()
        }
    }

    @Test fun realReminderCreatesDurableAuxiliaryNoticeWithoutOSAndReplayIsIdempotent() =
        Rig().use { rig ->
            rig.createReminder()
            val before = rig.snapshot()
            val first = rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
                as S4W03NoticeReconcileResult.Saved
            assertTrue(first.complete)
            assertEquals(1, first.created)
            assertEquals(0, first.resolved)
            val notice = rig.auxiliary.readNotices().single()
            assertEquals(NoticeRelevance.OPEN, notice.relevance)
            assertEquals(NoticeAttention.UNSEEN, notice.attention)
            val deliveryId = rig.auxiliary.readNotificationProjectionCandidateIds(10)
            assertTrue(deliveryId.isEmpty())
            val second = rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
                as S4W03NoticeReconcileResult.Saved
            assertEquals(0, second.created)
            assertEquals(notice.noticeId, rig.auxiliary.readNotices().single().noticeId)
            assertEquals(before, rig.snapshot())
            assertEquals(1L, rig.snapshot().revision)
        }

    @Test fun revokedSourceNoticeForegroundAndHostEpochFailClosed() = Rig().use { rig ->
        rig.createReminder()
        rig.policyAllows = false
        assertTrue(rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            is S4W03NoticeReconcileResult.Denied)
        rig.policyAllows = true
        rig.noticeAllows = false
        assertTrue(rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            is S4W03NoticeReconcileResult.Denied)
        rig.noticeAllows = true
        rig.foreground = false
        assertTrue(rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            is S4W03NoticeReconcileResult.Denied)
        rig.foreground = true
        rig.epoch = "stale"
        assertTrue(rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            is S4W03NoticeReconcileResult.Denied)
        assertTrue(rig.auxiliary.readNotices().isEmpty())
    }

    @Test fun independentLinkedMatterGrantReturnsExplicitPartialThenRoutes() = Rig().use { rig ->
        rig.createMatter()
        rig.createReminder("linked-matter")
        val beforeMatter = rig.reality.matterPersistence.read()
        val beforeReminder = rig.snapshot()
        val skipped = rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            as S4W03NoticeReconcileResult.Saved
        assertFalse(skipped.complete)
        assertEquals(1, skipped.linkedSourcesOmitted)
        assertTrue(rig.auxiliary.readNotices().isEmpty())
        rig.linkAllows = true
        val allowed = rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            as S4W03NoticeReconcileResult.Saved
        assertTrue(allowed.complete)
        assertEquals(1, allowed.created)
        assertEquals(NoticeSensitivity.SENSITIVE, rig.auxiliary.readNotices().single().sensitivity)
        assertEquals(beforeMatter, rig.reality.matterPersistence.read())
        assertEquals(beforeReminder, rig.snapshot())
    }

    @Test fun sourceAckResolvesLocalNoticeWithoutAlteringReminderOrDispatch() = Rig().use { rig ->
        rig.createReminder()
        assertEquals(1, (rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            as S4W03NoticeReconcileResult.Saved).created)
        val sourceBeforeAck = rig.snapshot()
        val emptyWindow = rig.noticeHost.reconcile(rig.due() + 1L, rig.due() + 2L)
            as S4W03NoticeReconcileResult.Saved
        assertEquals(0, emptyWindow.resolved)
        assertEquals(NoticeRelevance.OPEN, rig.auxiliary.readNotices().single().relevance)
        rig.acknowledge()
        val acknowledged = rig.snapshot()
        assertEquals(2L, acknowledged.revision)
        val result = rig.noticeHost.reconcile(rig.due(), rig.due() + 1L)
            as S4W03NoticeReconcileResult.Saved
        assertEquals(1, result.resolved)
        assertEquals(0, result.created)
        assertEquals(NoticeRelevance.RESOLVED, rig.auxiliary.readNotices().single().relevance)
        assertEquals(NoticeResolutionReason.SOURCE_RESOLVED,
            rig.auxiliary.readNotices().single().resolutionReason)
        assertEquals(acknowledged, rig.snapshot())
        assertEquals(1L, sourceBeforeAck.revision)
    }
}
