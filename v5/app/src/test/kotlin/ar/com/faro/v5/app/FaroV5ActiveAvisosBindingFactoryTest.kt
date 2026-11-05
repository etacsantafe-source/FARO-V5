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
import ar.com.faro.v5.surface.FaroV5AvisoImportance
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5ActiveAvisosBindingFactoryTest {
    private fun notice(
        id: String,
        materiality: NoticeMateriality = NoticeMateriality.NOTICE,
        sensitivity: NoticeSensitivity = NoticeSensitivity.NORMAL,
        relevance: NoticeRelevance = NoticeRelevance.OPEN,
        attention: NoticeAttention = NoticeAttention.UNSEEN,
    ) = NoticeSnapshot(
        noticeId = NoticeId.of(id),
        noticeKey = NoticeKey.of("key-$id"),
        sourceRef = SourceRef.of("source-$id"),
        occurrenceRef = OccurrenceRef.of("occ-$id"),
        materiality = materiality,
        sensitivity = sensitivity,
        summary = NoticeSummary.of("Original privado $id"),
        destinationRef = DestinationRef.of("avisos"),
        relevance = relevance,
        attention = attention,
        revision = 1L,
        resolutionReason = if (relevance == NoticeRelevance.RESOLVED)
            NoticeResolutionReason.SOURCE_RESOLVED else null,
    )

    private fun port(
        supplier: Supplier<List<NoticeSnapshot>>,
        active: Supplier<String> = Supplier { "g1" },
        ready: BooleanSupplier = BooleanSupplier { true },
    ) = FaroV5ActiveAvisosBindingFactory.create(
        supplier, "g1", active, ready, emptyFlow(),
    )

    @Test
    fun realOwnerReadFiltersResolvedDismissedAndKeepsSeenUnmutated() = runBlocking {
        val attended = notice("a", materiality = NoticeMateriality.NEEDS_ATTENTION)
        val seen = notice("b", attention = NoticeAttention.SEEN)
        val rows = listOf(
            seen, attended, notice("c", relevance = NoticeRelevance.RESOLVED),
            notice("d", attention = NoticeAttention.DISMISSED), attended,
        )
        val page = port(Supplier { rows }).readNotices()!!
        assertTrue(page.complete)
        assertEquals(listOf("a", "b"), page.items.map { it.opaqueId })
        assertEquals(FaroV5AvisoImportance.REQUIERE_ATENCION, page.items.first().importance)
        assertEquals(NoticeAttention.SEEN, seen.attention)
        assertEquals(NoticeAttention.UNSEEN, attended.attention)

        // A pointer revoked during the actual owner read must suppress the page.
        var pointerCurrent = true
        val revokedDuringRead = FaroV5ActiveAvisosBindingFactory.createPinned(
            Supplier { pointerCurrent = false; rows },
            "g1", Supplier { "g1" }, BooleanSupplier { true },
            BooleanSupplier { pointerCurrent }, emptyFlow(),
        )
        assertNull(revokedDuringRead.readNotices())
    }

    @Test
    fun sensitiveSummariesAreRedactedEvenWhenPriorityRequiresAttention() = runBlocking {
        val page = port(Supplier {
            listOf(notice("secret", materiality = NoticeMateriality.NEEDS_ATTENTION,
                sensitivity = NoticeSensitivity.SENSITIVE))
        }).readNotices()!!
        assertEquals("Aviso privado", page.items.single().summary)
        assertFalse(page.items.single().summary.contains("Original privado"))
    }

    @Test
    fun authorizedZeroAndUnavailabilityAreDifferentAndNeverReadOnDeniedAuthority() = runBlocking {
        assertTrue(port(Supplier { emptyList() }).readNotices()!!.items.isEmpty())
        var reads = 0
        assertNull(port(Supplier { reads++; emptyList() }, ready = BooleanSupplier { false })
            .readNotices())
        assertEquals(0, reads)
        assertNull(port(Supplier { throw IllegalStateException("closed") }).readNotices())
        var probes = 0
        assertNull(port(Supplier { listOf(notice("a")) }, active = Supplier {
            probes++
            if (probes == 1) "g1" else "g2"
        }).readNotices())
        assertEquals(2, probes)

        // Same generation ID after A->B->A is not proof that this graph is current.
        var pointerCurrent = false
        var oldStoreReads = 0
        val pinned = FaroV5ActiveAvisosBindingFactory.createPinned(
            Supplier { oldStoreReads++; listOf(notice("old")) },
            "g1", Supplier { "g1" }, BooleanSupplier { true },
            BooleanSupplier { pointerCurrent }, emptyFlow(),
        )
        assertNull(pinned.readNotices())
        assertEquals(0, oldStoreReads)
        pointerCurrent = true
        assertEquals(listOf("old"), pinned.readNotices()!!.items.map { it.opaqueId })
        assertEquals(1, oldStoreReads)
        pointerCurrent = false
        assertNull(pinned.readNotices())
        assertEquals(1, oldStoreReads)
    }

    @Test
    fun boundedProjectionNeverClaimsCompleteWhenOverHundredOwnerNotices() = runBlocking {
        val page = port(Supplier { (1..102).map { notice("notice-$it") } }).readNotices()!!
        assertEquals(100, page.items.size)
        assertFalse(page.complete)
    }
}
