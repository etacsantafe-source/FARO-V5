package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
import ar.com.faro.v5.kernel.contracts.EffectId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03AuxiliaryReminderLocalNoticeTest {
    @TempDir lateinit var root: Path

    private fun candidate(title: String = "Recordatorio", occ: String = "occ-1") =
        NoticeCandidate(
            noticeKey = NoticeKey.of("reminder:" + occ),
            sourceRef = SourceRef.of("reminder:rule-1"),
            occurrenceRef = OccurrenceRef.of(occ),
            materiality = NoticeMateriality.NOTICE,
            sensitivity = NoticeSensitivity.NORMAL,
            summary = NoticeSummary.of(title),
            destinationRef = DestinationRef.of("faro:avisos.local"),
        )

    @Test fun localNoticeSurvivesReopenAndCannotDispatchToAndroid() {
        val path = root.resolve("local.db").toString()
        val first = AuxiliaryDeliveryStore(path).use { store ->
            val out = store.routeReminderLocalOnly(candidate()) { true }
            assertTrue(out.created)
            assertEquals(NoticeAttention.UNSEEN, out.notice.attention)
            assertEquals(NoticeRelevance.OPEN, out.notice.relevance)
            assertEquals(DeliveryEffectState.SUPPRESSED_POLICY, out.deliveryIntent.state)
            assertTrue(store.beginNotificationDispatch(out.deliveryIntent.deliveryIntentId)
                is NotificationDispatchClaim.NotEligible)
            out
        }
        AuxiliaryDeliveryStore(path).use { reopened ->
            assertEquals(listOf(first.notice), reopened.readNotices())
            assertEquals(first.deliveryIntent,
                reopened.readDeliveryIntent(first.deliveryIntent.deliveryIntentId))
            assertTrue(reopened.beginNotificationDispatch(first.deliveryIntent.deliveryIntentId)
                is NotificationDispatchClaim.NotEligible)
            assertEquals(3, reopened.schemaVersion())
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test fun replayCoalescesAndPreservesStableIdentityAndSuppressedOs() {
        AuxiliaryDeliveryStore(root.resolve("replay.db").toString()).use { store ->
            val first = store.routeReminderLocalOnly(candidate()) { true }
            val again = store.routeReminderLocalOnly(candidate()) { true }
            assertFalse(again.created)
            assertEquals(first.notice.noticeId, again.notice.noticeId)
            assertEquals(first.deliveryIntent.deliveryIntentId, again.deliveryIntent.deliveryIntentId)
            assertEquals(first.deliveryIntent.effectId, again.deliveryIntent.effectId)
            assertEquals(1, store.readNotices().size)
            assertEquals(1, store.deliveryIntentCount())
            val updated = store.routeReminderLocalOnly(candidate("Título actualizado")) { true }
            assertFalse(updated.created)
            assertEquals(2L, updated.notice.revision)
            assertEquals(first.notice.noticeId, updated.notice.noticeId)
            assertEquals(DeliveryEffectState.SUPPRESSED_POLICY, updated.deliveryIntent.state)
        }
    }

    @Test fun denialAndFailureBeforeIntentCommitRollBackAllRows() {
        val path = root.resolve("rollback.db").toString()
        AuxiliaryDeliveryStore(path).use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.routeReminderLocalOnly(candidate()) { false }
            }
            assertTrue(store.readNotices().isEmpty())
        }
        AuxiliaryDeliveryStore(path, faultInjector = { point ->
            if (point == AuxiliaryDeliveryFaultPoint.AFTER_NOTICE_INSERT)
                error("injected crash between notice and intent")
        }).use { failed ->
            assertThrows(IllegalStateException::class.java) {
                failed.routeReminderLocalOnly(candidate()) { true }
            }
            assertTrue(failed.readNotices().isEmpty())
            assertEquals("ok", failed.integrityCheck())
        }
        AuxiliaryDeliveryStore(path).use { reopened ->
            assertTrue(reopened.readNotices().isEmpty())
            assertEquals(0, reopened.deliveryIntentCount())
        }
    }

    @Test fun resolvePreservesLocalHistoryWithoutDispatchOrSourceDeletion() {
        val path = root.resolve("resolve.db").toString()
        val committed = AuxiliaryDeliveryStore(path).use { store ->
            val created = store.routeReminderLocalOnly(candidate()) { true }
            val resolved = store.resolveReminderLocalOnly(created.notice.noticeId) { true }
            assertTrue(resolved is NoticeResolveResult.Resolved)
            val after = store.readNotices().single()
            assertEquals(NoticeRelevance.RESOLVED, after.relevance)
            assertEquals(NoticeResolutionReason.SOURCE_RESOLVED, after.resolutionReason)
            assertEquals(NoticeAttention.UNSEEN, after.attention)
            assertEquals(2L, after.revision)
            assertEquals(DeliveryDesiredAction.CANCEL,
                store.readDeliveryIntent(created.deliveryIntent.deliveryIntentId)?.desiredAction)
            assertEquals(DeliveryEffectState.SUPPRESSED_POLICY,
                store.readDeliveryIntent(created.deliveryIntent.deliveryIntentId)?.state)
            assertTrue(store.resolveReminderLocalOnly(created.notice.noticeId) { true }
                is NoticeResolveResult.AlreadyResolved)
            assertThrows(IllegalStateException::class.java) {
                store.routeReminderLocalOnly(candidate()) { true }
            }
            after
        }
        AuxiliaryDeliveryStore(path).use { store ->
            assertEquals(committed, store.readNotices().single())
        }
    }

    @Test fun legacyPlannedOsPathCannotBeHijackedByLocalReminderRoute() {
        AuxiliaryDeliveryStore(root.resolve("legacy.db").toString()).use { store ->
            val ordinary = store.route(
                candidate(),
                NoticeMaterializationIds(
                    NoticeId.of("legacy-notice"),
                    DeliveryIntentId.of("legacy-delivery"),
                    EffectId("legacy-effect"),
                ),
            ) as NoticeRouteResult.Materialized
            assertEquals(DeliveryEffectState.PLANNED, ordinary.deliveryIntent.state)
            assertThrows(IllegalStateException::class.java) {
                store.routeReminderLocalOnly(candidate()) { true }
            }
            assertEquals(1, store.readNotices().size)
            assertEquals(DeliveryEffectState.PLANNED,
                store.readDeliveryIntent(ordinary.deliveryIntent.deliveryIntentId)?.state)
            assertThrows(IllegalArgumentException::class.java) {
                store.routeReminderLocalOnly(candidate().copy(
                    sourceRef = SourceRef.of("radar:source"),
                )) { true }
            }
        }
    }
}
