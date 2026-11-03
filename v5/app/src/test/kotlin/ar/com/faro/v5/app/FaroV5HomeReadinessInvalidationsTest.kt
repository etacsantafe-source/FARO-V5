package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.surface.FaroV5HomeIndicator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier
import java.util.function.LongSupplier
import java.util.function.Supplier

class FaroV5HomeReadinessInvalidationsTest {
    @Test
    fun samePortReceivesRealOwnerRecoveryAndDropsPreviouslyVisibleNotices() = runBlocking {
        val gate = FaroV5StartupReadinessGate(SingleProcessMaterialWriteGate())
        gate.localReady()
        val notice = NoticeSnapshot(
            NoticeId.of("n1"), NoticeKey.of("key-n1"),
            SourceRef.of("source-n1"), OccurrenceRef.of("occ-n1"),
            NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
            NoticeSummary.of("Notice n1"), DestinationRef.of("avisos"),
            NoticeRelevance.OPEN, NoticeAttention.UNSEEN, 1L, null
        )
        val port = FaroV5HomeNoticeBindingFactory.create(
            Supplier { listOf(notice, notice, notice) },
            "gen-1", Supplier { "gen-1" },
            BooleanSupplier { gate.snapshot().isLocalReady },
            LongSupplier { 321L },
            FaroV5HomeReadinessInvalidations.from(gate)
        )
        assertEquals(1, port.readIndicators()[FaroV5HomeIndicator.AVISOS]?.count)
        val observed = Channel<Unit>(Channel.UNLIMITED)
        val job = launch { port.invalidations.collect { observed.send(Unit) } }
        yield()
        gate.beginRecovery()
        withTimeout(2000) { observed.receive() }
        assertFalse(port.readIndicators().containsKey(FaroV5HomeIndicator.AVISOS))
        job.cancel()
    }
}
