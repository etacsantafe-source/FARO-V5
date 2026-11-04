package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeResolveResult
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeResolutionReason
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.surface.FaroV5HomeIndicator
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.function.BooleanSupplier
import java.util.function.LongSupplier
import java.util.function.Supplier

class FaroV5HomeNoticeBindingFactoryTest {
    private fun notice(
        id: String,
        relevance: NoticeRelevance = NoticeRelevance.OPEN,
        attention: NoticeAttention = NoticeAttention.UNSEEN,
    ) = NoticeSnapshot(
        noticeId = NoticeId.of(id),
        noticeKey = NoticeKey.of("key-$id"),
        sourceRef = SourceRef.of("source-$id"),
        occurrenceRef = OccurrenceRef.of("occ-$id"),
        materiality = NoticeMateriality.NOTICE,
        sensitivity = NoticeSensitivity.NORMAL,
        summary = NoticeSummary.of("Notice $id"),
        destinationRef = DestinationRef.of("avisos"),
        relevance = relevance,
        attention = attention,
        revision = 1L,
        resolutionReason = if (relevance == NoticeRelevance.RESOLVED) {
            NoticeResolutionReason.SOURCE_RESOLVED
        } else null,
    )

    private fun port(
        source: Supplier<List<NoticeSnapshot>>,
        active: Supplier<String> = Supplier { "gen-1" },
        ready: BooleanSupplier = BooleanSupplier { true },
    ) = FaroV5HomeNoticeBindingFactory.create(
        source, "gen-1", active, ready, LongSupplier { 321L },
    )

    @Test
    fun onlyActiveNonDismissedNoticesCountWithoutChangingAttention() = runBlocking {
        val open = notice("a")
        val seen = notice("b", attention = NoticeAttention.SEEN)
        val input = listOf(
            open, seen, notice("c", attention = NoticeAttention.DISMISSED),
            notice("d", relevance = NoticeRelevance.RESOLVED), open,
        )
        val result = port(Supplier { input }).readIndicators()
        assertEquals(2, result[FaroV5HomeIndicator.AVISOS]?.count)
        assertEquals(321L, result[FaroV5HomeIndicator.AVISOS]?.asOfEpochMillis)
        assertEquals("notice.read", result[FaroV5HomeIndicator.AVISOS]?.canonicalSource)
        assertEquals(NoticeAttention.UNSEEN, open.attention)
        assertEquals(NoticeAttention.SEEN, seen.attention)
        assertEquals(1, result.size)
    }

    @Test
    fun zeroFromAuthorizedEmptyStoreIsRealZero() = runBlocking {
        val result = port(Supplier { emptyList() }).readIndicators()
        assertEquals(0, result[FaroV5HomeIndicator.AVISOS]?.count)
        assertTrue(FaroV5HomeIndicator.PENDIENTES !in result)
    }

    @Test
    fun recoveryGateDeniesStoreRead() = runBlocking {
        val gate = FaroV5StartupReadinessGate(SingleProcessMaterialWriteGate())
        gate.localReady()
        var maintenanceComplete = true
        var reads = 0
        val readPort = port(Supplier {
            reads++
            listOf(notice("a"))
        }, ready = BooleanSupplier {
            maintenanceComplete && gate.snapshot().isLocalReady
        })
        assertEquals(1, readPort.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
        maintenanceComplete = false
        gate.beginRecovery()
        assertTrue(readPort.readIndicators().isEmpty())
        assertEquals(1, reads)
        // Maintenance completion cannot authorize reads until owner LOCAL_READY.
        maintenanceComplete = true
        assertTrue(readPort.readIndicators().isEmpty())
        assertEquals(1, reads)
        gate.localReady()
        assertEquals(1, readPort.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
        assertEquals(2, reads)
    }

    @Test
    fun sameHomePortRecountsRealAuxiliaryCommitAndResolution() = runBlocking {
        val dir = Files.createTempDirectory("faro-home-notice-commit")
        try {
            AuxiliaryDeliveryStore(dir.resolve("auxiliary.db").toString()).use { store ->
                val gate = FaroV5StartupReadinessGate(SingleProcessMaterialWriteGate())
                gate.localReady()
                val readPort = FaroV5HomeNoticeBindingFactory.create(
                    Supplier { store.readNotices() },
                    "gen-1", Supplier { "gen-1" },
                    BooleanSupplier { gate.snapshot().isLocalReady },
                    LongSupplier { 321L },
                    FaroV5HomeReadinessInvalidations.from(gate, store),
                )
                assertEquals(0, readPort.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
                val changes = Channel<Unit>(Channel.UNLIMITED)
                val observer = launch { readPort.invalidations.collect { changes.send(Unit) } }
                try {
                    yield()
                    yield()
                    val candidate = NoticeCandidate(
                        noticeKey = NoticeKey.of("home.test"),
                        sourceRef = SourceRef.of("home:source"),
                        occurrenceRef = OccurrenceRef.of("home:occurrence"),
                        materiality = NoticeMateriality.NOTICE,
                        sensitivity = NoticeSensitivity.NORMAL,
                        summary = NoticeSummary.of("Una novedad real"),
                        destinationRef = DestinationRef.of("avisos"),
                    )
                    val ids = NoticeMaterializationIds(
                        noticeId = NoticeId.of("home-notice-1"),
                        deliveryIntentId = ar.com.faro.v5.delivery.api.DeliveryIntentId.of("home-delivery-1"),
                        effectId = EffectId("home-effect-1"),
                    )
                    store.route(candidate, ids)
                    withTimeout(3000) { changes.receive() }
                    assertEquals(1, readPort.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
                    assertTrue(store.resolve(ids.noticeId, NoticeResolutionReason.SOURCE_RESOLVED)
                        is NoticeResolveResult.Resolved)
                    withTimeout(3000) { changes.receive() }
                    assertEquals(0, readPort.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
                } finally {
                    observer.cancel()
                }
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun generationChangeDuringReadInvalidatesResult() = runBlocking {
        var probes = 0
        val result = port(
            Supplier { listOf(notice("a")) },
            active = Supplier {
                probes++
                if (probes == 1) "gen-1" else "gen-2"
            },
        ).readIndicators()
        assertTrue(result.isEmpty())
        assertEquals(2, probes)
    }

    @Test
    fun storeReadFailureIsUnknownNotZero() = runBlocking {
        val result = port(Supplier { throw IllegalStateException("store unavailable") })
            .readIndicators()
        assertTrue(result.isEmpty())
    }
}
