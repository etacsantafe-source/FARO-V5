package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03AuxiliaryNoticeAttentionCasTest {
    @TempDir lateinit var root: Path

    private fun candidate() = NoticeCandidate(
        noticeKey = NoticeKey.of("reminder:occur-attention"),
        sourceRef = SourceRef.of("reminder:rule-attention"),
        occurrenceRef = OccurrenceRef.of("occur-attention"),
        materiality = NoticeMateriality.NOTICE,
        sensitivity = NoticeSensitivity.NORMAL,
        summary = NoticeSummary.of("Llevar documentación"),
        destinationRef = DestinationRef.of("faro:avisos.local"),
    )

    private fun seed(store: AuxiliaryDeliveryStore) =
        store.routeReminderLocalOnly(candidate()) { true }

    @Test fun seenPreservesVisibilityAndPersistsWithoutOs() {
        val file = root.resolve("seen.db").toString()
        val notice = AuxiliaryDeliveryStore(file).use { store ->
            val created = seed(store)
            val result = store.mutateReminderNoticeAttention(
                created.notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true } as S4W03NoticeAttentionCasResult.Saved
            assertEquals(NoticeAttention.SEEN, result.notice.attention)
            assertEquals(2L, result.notice.revision)
            assertEquals(NoticeRelevance.OPEN, result.notice.relevance)
            assertEquals(1, store.readNotices().count {
                it.relevance == NoticeRelevance.OPEN &&
                    it.attention != NoticeAttention.DISMISSED
            })
            assertTrue(store.beginNotificationDispatch(
                created.deliveryIntent.deliveryIntentId,
            ) is NotificationDispatchClaim.NotEligible)
            result.notice
        }
        AuxiliaryDeliveryStore(file).use { reopened ->
            assertEquals(notice, reopened.readNotices().single())
            assertEquals(3, reopened.schemaVersion())
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test fun dismissIsTerminalAndNeverRemovesSourceNotice() {
        AuxiliaryDeliveryStore(root.resolve("dismiss.db").toString()).use { store ->
            val created = seed(store)
            val seen = store.mutateReminderNoticeAttention(
                created.notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true } as S4W03NoticeAttentionCasResult.Saved
            val dismissed = store.mutateReminderNoticeAttention(
                created.notice.noticeId, seen.notice.revision, NoticeAttention.DISMISSED,
            ) { true } as S4W03NoticeAttentionCasResult.Saved
            assertEquals(NoticeAttention.DISMISSED, dismissed.notice.attention)
            assertEquals(3L, dismissed.notice.revision)
            assertEquals(1, store.readNotices().size)
            assertEquals(0, store.readNotices().count {
                it.relevance == NoticeRelevance.OPEN &&
                    it.attention != NoticeAttention.DISMISSED
            })
            assertEquals(S4W03NoticeAttentionCasResult.Terminal,
                store.mutateReminderNoticeAttention(
                    created.notice.noticeId, 3L, NoticeAttention.SEEN,
                ) { true })
            assertEquals(3L, store.readNotices().single().revision)
        }
    }

    @Test fun concurrentOrDuplicateClicksNeverForgeSuccess() {
        AuxiliaryDeliveryStore(root.resolve("stale.db").toString()).use { store ->
            val n = seed(store).notice
            val first = store.mutateReminderNoticeAttention(
                n.noticeId, n.revision, NoticeAttention.DISMISSED,
            ) { true } as S4W03NoticeAttentionCasResult.Saved
            assertEquals(S4W03NoticeAttentionCasResult.Stale(first.notice.revision),
                store.mutateReminderNoticeAttention(
                    n.noticeId, n.revision, NoticeAttention.SEEN,
                ) { true })
            assertEquals(S4W03NoticeAttentionCasResult.Terminal,
                store.mutateReminderNoticeAttention(
                    n.noticeId, first.notice.revision, NoticeAttention.DISMISSED,
                ) { true })
            assertEquals(first.notice, store.readNotices().single())
        }
    }

    @Test fun policyRevocationImmediatelyBeforeCommitRollsBack() {
        AuxiliaryDeliveryStore(root.resolve("revoke.db").toString()).use { store ->
            val n = seed(store).notice
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                store.mutateReminderNoticeAttention(n.noticeId, 1L, NoticeAttention.SEEN) {
                    false
                })
            var checks = 0
            val result = store.mutateReminderNoticeAttention(
                n.noticeId, 1L, NoticeAttention.SEEN,
            ) { ++checks < 3 }
            assertEquals(S4W03NoticeAttentionCasResult.Denied, result)
            assertEquals(3, checks)
            assertEquals(n, store.readNotices().single())
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test fun onlyOpenSuppressedReminderNoticesCanBeChanged() {
        AuxiliaryDeliveryStore(root.resolve("only-local.db").toString()).use { store ->
            val valid = seed(store)
            val missing = NoticeId.of("no-such-notice")
            assertEquals(S4W03NoticeAttentionCasResult.NotFound,
                store.mutateReminderNoticeAttention(missing, 1L, NoticeAttention.DISMISSED) {
                    true
                })
            store.resolveReminderLocalOnly(valid.notice.noticeId) { true }
            assertEquals(S4W03NoticeAttentionCasResult.Terminal,
                store.mutateReminderNoticeAttention(
                    valid.notice.noticeId, 2L, NoticeAttention.DISMISSED,
                ) { true })
            assertEquals(NoticeAttention.UNSEEN, store.readNotices().single().attention)
            assertEquals(NoticeRelevance.RESOLVED, store.readNotices().single().relevance)
        }
        AuxiliaryDeliveryStore(root.resolve("other-source.db").toString()).use { store ->
            val external = store.route(
                NoticeCandidate(
                    noticeKey = NoticeKey.of("radar:price"),
                    sourceRef = SourceRef.of("radar:result"),
                    occurrenceRef = OccurrenceRef.of("occ-radar"),
                    materiality = NoticeMateriality.NOTICE,
                    sensitivity = NoticeSensitivity.NORMAL,
                    summary = NoticeSummary.of("Precio actualizado"),
                    destinationRef = DestinationRef.of("faro:radar"),
                ),
                NoticeMaterializationIds(
                    NoticeId.of("notice-radar"),
                    DeliveryIntentId.of("delivery-radar"),
                    ar.com.faro.v5.kernel.contracts.EffectId("effect-radar"),
                ),
            ) as NoticeRouteResult.Materialized
            assertEquals(S4W03NoticeAttentionCasResult.Denied,
                store.mutateReminderNoticeAttention(
                    external.notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true })
            assertEquals(NoticeAttention.UNSEEN, store.readNotices().single().attention)
        }
    }
}
