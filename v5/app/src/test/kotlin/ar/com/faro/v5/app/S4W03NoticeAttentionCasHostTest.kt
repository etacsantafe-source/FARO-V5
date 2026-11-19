package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionCasResult
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03NoticeAttentionCasHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(
            root, gate, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val selection = try {
            val source = StorageGenerationId("d2b1-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(source, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("d2b1-activation"),
                StorageGenerationDescriptor(
                    StorageGenerationId("d2b1-active"), "attention", 2L,
                ),
                requireNotNull(pointer.compareAndSet(null, source)), 3L,
            ) { true }
        } catch (e: Throwable) {
            runtime.close()
            throw e
        }
        val graph = runtime.openCommittedFinance9(selection)
        val auxiliary = graph.auxiliary
        val reality = graph.reality
        var allowRead = true
        var allowSeen = true
        var allowDismiss = true
        var foreground = true
        var epoch = "epoch"
        val host = S4W03NoticeAttentionCasHost(
            selection.pointer, pointer, auxiliary, gate,
            "trusted-attention", "epoch", { epoch },
            { runtime.isCurrent(selection) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, scope, _ ->
                val allowed = when (scope.operationId) {
                    OperationId("notice.read") -> allowRead
                    OperationId("notice.seen.set") -> allowSeen
                    OperationId("notice.dismiss") -> allowDismiss
                    else -> false
                }
                if (allowed) "policy-current" else null
            },
        )
        val seed = auxiliary.routeReminderLocalOnly(
            NoticeCandidate(
                noticeKey = NoticeKey.of("reminder:d2b1-occ"),
                sourceRef = SourceRef.of("reminder:d2b1-rule"),
                occurrenceRef = OccurrenceRef.of("d2b1-occ"),
                materiality = NoticeMateriality.NOTICE,
                sensitivity = NoticeSensitivity.NORMAL,
                summary = NoticeSummary.of("Reunión importante"),
                destinationRef = DestinationRef.of("faro:avisos.local"),
            ),
        ) { true }
        val noticeId = seed.notice.noticeId
        override fun close() {
            host.close()
            runtime.close()
        }
    }

    @Test fun independentSeenAndDismissClicksPersistWhileSourceOwnersRemainIntact() =
        Rig().use { rig ->
            val beforeReminder = rig.reality.planningDueReadOwnersForTrustedHost()
                .reminders.read()
            val beforeMatter = rig.reality.matterPersistence.read()
            val seen = rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.SEEN)
                as S4W03NoticeAttentionCasResult.Saved
            assertEquals(NoticeAttention.SEEN, seen.notice.attention)
            assertEquals(2L, seen.notice.revision)
            assertEquals(1, rig.auxiliary.readNotices().count {
                it.relevance == NoticeRelevance.OPEN &&
                    it.attention != NoticeAttention.DISMISSED
            })
            val dismissed = rig.host.explicitClick(
                rig.noticeId, 2L, NoticeAttention.DISMISSED,
            ) as S4W03NoticeAttentionCasResult.Saved
            assertEquals(NoticeAttention.DISMISSED, dismissed.notice.attention)
            assertEquals(3L, dismissed.notice.revision)
            assertEquals(0, rig.auxiliary.readNotices().count {
                it.relevance == NoticeRelevance.OPEN &&
                    it.attention != NoticeAttention.DISMISSED
            })
            assertEquals(beforeReminder, rig.reality.planningDueReadOwnersForTrustedHost()
                .reminders.read())
            assertEquals(beforeMatter, rig.reality.matterPersistence.read())
            assertTrue(rig.auxiliary.readNotificationProjectionCandidateIds(8).isEmpty())
        }

    @Test fun readWithoutScopedWriteGrantAndForegroundEpochAreDenied() =
        Rig().use { rig ->
            rig.allowDismiss = false
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.DISMISSED))
            rig.allowDismiss = true
            rig.allowRead = false
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.DISMISSED))
            rig.allowRead = true
            rig.foreground = false
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.SEEN))
            rig.foreground = true
            rig.epoch = "revoked"
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.SEEN))
            assertEquals(1L, rig.auxiliary.readNotices().single().revision)
            assertEquals(NoticeAttention.UNSEEN, rig.auxiliary.readNotices().single().attention)
        }

    @Test fun staleAndTerminalResultsDoNotFakeDurableReceipts() = Rig().use { rig ->
        val first = rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.SEEN)
            as S4W03NoticeAttentionCasResult.Saved
        assertEquals(S4W03NoticeAttentionCasResult.Stale(2L),
            rig.host.explicitClick(rig.noticeId, 1L, NoticeAttention.DISMISSED))
        assertEquals(S4W03NoticeAttentionCasResult.Terminal,
            rig.host.explicitClick(rig.noticeId, 2L, NoticeAttention.SEEN))
        val second = rig.host.explicitClick(rig.noticeId, 2L, NoticeAttention.DISMISSED)
            as S4W03NoticeAttentionCasResult.Saved
        assertEquals(3L, second.notice.revision)
        assertEquals(S4W03NoticeAttentionCasResult.Terminal,
            rig.host.explicitClick(rig.noticeId, 3L, NoticeAttention.DISMISSED))
        assertEquals(3L, rig.auxiliary.readNotices().single().revision)
        assertEquals(NoticeAttention.SEEN, first.notice.attention)
    }
}
