package ar.com.faro.v5.delivery

import ar.com.faro.v5.delivery.api.DeliveryDesiredAction
import ar.com.faro.v5.delivery.api.DeliveryEffectState
import ar.com.faro.v5.delivery.api.DeliveryIntent
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.DeliveryOperations
import ar.com.faro.v5.delivery.api.DeliverySink
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeReader
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeResolutionReason
import ar.com.faro.v5.delivery.api.NoticeResolveResult
import ar.com.faro.v5.delivery.api.NoticeResolver
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.delivery.spi.DeliveryPersistencePort
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoticeDeliveryServiceTest {
    @Test
    fun operationDescriptorsMatchFrozenN032Profiles() {
        assertEquals("notice.read", DeliveryOperations.READ_DESCRIPTOR.operationId.value)
        assertEquals(ExecutionProfile.READ_ONLY, DeliveryOperations.READ_DESCRIPTOR.executionProfile)
        assertEquals("notice.route", DeliveryOperations.ROUTE_DESCRIPTOR.operationId.value)
        assertEquals(
            ExecutionProfile.AUXILIARY_MUTATION,
            DeliveryOperations.ROUTE_DESCRIPTOR.executionProfile,
        )
        assertEquals("notice.resolve", DeliveryOperations.RESOLVE_DESCRIPTOR.operationId.value)
        assertEquals(
            ExecutionProfile.AUXILIARY_MUTATION,
            DeliveryOperations.RESOLVE_DESCRIPTOR.executionProfile,
        )
        assertEquals(
            ExecutionProfile.EXTERNAL_EFFECT,
            DeliveryOperations.DISPATCH_DESCRIPTOR.executionProfile,
        )
        assertEquals(
            ExecutionProfile.AUXILIARY_MUTATION,
            DeliveryOperations.RECONCILE_DESCRIPTOR.executionProfile,
        )
        assertEquals("delivery", DeliveryOperations.ROUTE_DESCRIPTOR.canonicalExecutor.value)
    }

    @Test
    fun silentCandidateCreatesNoDurableNoticeOrDeliveryIntent() {
        val store = FakeDeliveryPersistence()
        var idsRequested = 0
        val router = NoticeRouter(store) {
            idsRequested += 1
            ids("silent")
        }

        val result = router.route(candidate(materiality = NoticeMateriality.SILENT))

        assertInstanceOf(NoticeRouteResult.SuppressedSilent::class.java, result)
        assertEquals(0, idsRequested)
        assertTrue(store.readNotices().isEmpty())
    }

    @Test
    fun materialCandidateCreatesOneNoticeAndOneStableDeliveryIntent() {
        val store = FakeDeliveryPersistence()
        val router = NoticeRouter(store) { ids("first") }

        val result = assertInstanceOf(NoticeRouteResult.Materialized::class.java, 
            router.route(candidate(materiality = NoticeMateriality.NEEDS_ATTENTION))
        )

        assertTrue(result.created)
        assertEquals(NoticeRelevance.OPEN, result.notice.relevance)
        assertEquals(NoticeAttention.UNSEEN, result.notice.attention)
        assertEquals(1L, result.notice.revision)
        assertEquals(DeliverySink.ANDROID_NOTIFICATION, result.deliveryIntent.sink)
        assertEquals(DeliveryDesiredAction.UPSERT, result.deliveryIntent.desiredAction)
        assertEquals(DeliveryEffectState.PLANNED, result.deliveryIntent.state)
        assertEquals(result.notice.noticeId, result.deliveryIntent.noticeId)
    }

    @Test
    fun duplicateOccurrenceCoalescesToExistingNoticeWithoutResurrectionOrDuplication() {
        val store = FakeDeliveryPersistence()
        var sequence = 0
        val router = NoticeRouter(store) {
            sequence += 1
            ids("attempt-$sequence")
        }
        val first = assertInstanceOf(NoticeRouteResult.Materialized::class.java, router.route(candidate()))
        val second = assertInstanceOf(NoticeRouteResult.Materialized::class.java, router.route(candidate()))

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.notice.noticeId, second.notice.noticeId)
        assertEquals(first.deliveryIntent.deliveryIntentId, second.deliveryIntent.deliveryIntentId)
        assertEquals(1, store.readNotices().size)
    }

    @Test
    fun resolverPreservesAttentionAndMakesResolutionIdempotent() {
        val store = FakeDeliveryPersistence()
        val routed =
            assertInstanceOf(
                NoticeRouteResult.Materialized::class.java,
                NoticeRouter(store) { ids("resolve") }.route(candidate()),
            )
        val resolver = NoticeResolver(store)

        val resolved =
            assertInstanceOf(
                NoticeResolveResult.Resolved::class.java,
                resolver.resolve(routed.notice.noticeId, NoticeResolutionReason.SOURCE_RESOLVED),
            )
        assertEquals(NoticeRelevance.RESOLVED, resolved.notice.relevance)
        assertEquals(NoticeResolutionReason.SOURCE_RESOLVED, resolved.notice.resolutionReason)
        assertEquals(NoticeAttention.UNSEEN, resolved.notice.attention)
        assertEquals(2L, resolved.notice.revision)
        assertEquals(DeliveryDesiredAction.CANCEL, resolved.deliveryIntent.desiredAction)

        val replay =
            assertInstanceOf(
                NoticeResolveResult.AlreadyResolved::class.java,
                resolver.resolve(routed.notice.noticeId, NoticeResolutionReason.REPLACED),
            )
        assertEquals(NoticeResolutionReason.SOURCE_RESOLVED, replay.notice.resolutionReason)
        assertEquals(2L, replay.notice.revision)
    }

    @Test
    fun readIsPureAndDoesNotAlterAttentionState() {
        val store = FakeDeliveryPersistence()
        NoticeRouter(store) { ids("read") }.route(candidate())
        val reader = NoticeReader(store)

        val before = reader.read()
        val after = reader.read()

        assertEquals(before, after)
        assertEquals(NoticeAttention.UNSEEN, after.single().attention)
    }

    private fun candidate(
        materiality: NoticeMateriality = NoticeMateriality.NOTICE,
    ) = NoticeCandidate(
        noticeKey = NoticeKey.of("radar.price-change"),
        sourceRef = SourceRef.of("radar:result:42"),
        occurrenceRef = OccurrenceRef.of("occurrence:2026-10-23T12:00"),
        materiality = materiality,
        sensitivity = NoticeSensitivity.NORMAL,
        summary = NoticeSummary.of("Bajó un precio observado."),
        destinationRef = DestinationRef.of("commercial:evidence:42"),
    )

    private fun ids(suffix: String) = NoticeMaterializationIds(
        noticeId = NoticeId.of("notice-$suffix"),
        deliveryIntentId = DeliveryIntentId.of("delivery-$suffix"),
        effectId = EffectId("effect-$suffix"),
    )
}

private class FakeDeliveryPersistence : DeliveryPersistencePort {
    private data class Row(
        val notice: NoticeSnapshot,
        val intent: DeliveryIntent,
    )

    private val byOccurrence = linkedMapOf<String, Row>()

    override fun readNotices(): List<NoticeSnapshot> = byOccurrence.values.map { it.notice }

    override fun route(
        candidate: NoticeCandidate,
        newIds: NoticeMaterializationIds,
    ): NoticeRouteResult {
        val key = candidate.noticeKey.value + "|" + candidate.occurrenceRef.value
        val existing = byOccurrence[key]
        if (existing != null) {
            return NoticeRouteResult.Materialized(
                notice = existing.notice,
                deliveryIntent = existing.intent,
                created = false,
            )
        }

        val notice = NoticeSnapshot(
            noticeId = newIds.noticeId,
            noticeKey = candidate.noticeKey,
            sourceRef = candidate.sourceRef,
            occurrenceRef = candidate.occurrenceRef,
            materiality = candidate.materiality,
            sensitivity = candidate.sensitivity,
            summary = candidate.summary,
            destinationRef = candidate.destinationRef,
            relevance = NoticeRelevance.OPEN,
            attention = NoticeAttention.UNSEEN,
            revision = 1L,
        )
        val intent = DeliveryIntent(
            deliveryIntentId = newIds.deliveryIntentId,
            effectId = newIds.effectId,
            noticeId = notice.noticeId,
            sink = DeliverySink.ANDROID_NOTIFICATION,
            desiredAction = DeliveryDesiredAction.UPSERT,
            state = DeliveryEffectState.PLANNED,
        )
        byOccurrence[key] = Row(notice, intent)
        return NoticeRouteResult.Materialized(notice, intent, created = true)
    }

    override fun resolve(
        noticeId: NoticeId,
        reason: NoticeResolutionReason,
    ): NoticeResolveResult {
        val entry =
            byOccurrence.entries.firstOrNull { it.value.notice.noticeId == noticeId }
                ?: return NoticeResolveResult.NotFound
        val current = entry.value
        if (current.notice.relevance == NoticeRelevance.RESOLVED) {
            return NoticeResolveResult.AlreadyResolved(current.notice, current.intent)
        }

        val notice =
            current.notice.copy(
                relevance = NoticeRelevance.RESOLVED,
                revision = current.notice.revision + 1,
                resolutionReason = reason,
            )
        val intent =
            current.intent.copy(
                desiredAction = DeliveryDesiredAction.CANCEL,
                state =
                    if (current.intent.state == DeliveryEffectState.POSTED_TO_OS) {
                        DeliveryEffectState.PLANNED
                    } else {
                        current.intent.state
                    },
            )
        byOccurrence[entry.key] = Row(notice, intent)
        return NoticeResolveResult.Resolved(notice, intent)
    }
}
