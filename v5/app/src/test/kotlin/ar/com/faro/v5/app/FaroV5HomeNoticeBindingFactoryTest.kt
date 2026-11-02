package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        var reads = 0
        val result = port(Supplier {
            reads++
            listOf(notice("a"))
        }, ready = BooleanSupplier { false }).readIndicators()
        assertTrue(result.isEmpty())
        assertEquals(0, reads)
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
