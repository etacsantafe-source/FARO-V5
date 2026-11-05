package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.RealityTimedReceiptCursor
import ar.com.faro.v5.store.reality.RealityTimedReceiptHistoryReadPort
import ar.com.faro.v5.store.reality.RealityTimedReceiptPage
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FaroV5RealityTimedActivityReadSourceTest {
    private fun empty(legacy: Boolean = false, cursor: RealityTimedReceiptCursor? = null) =
        RealityTimedReceiptPage(emptyList(), cursor, legacy)

    @Test
    fun missingAclNeverReadsReality() {
        val reads = AtomicInteger(0)
        val source = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                reads.incrementAndGet()
                empty()
            }, { "gen-1" }, { null }, { true },
        )
        assertNull(source.readAuthorized())
        assertEquals(0, reads.get())
    }

    @Test
    fun authorizedReadPreservesRealityOnlyAndLegacyCoverage() {
        val cursor = RealityTimedReceiptCursor(567L, "tx-a")
        var requestedLimit = 0
        val source = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort { query ->
                requestedLimit = query.limit
                empty(legacy = true, cursor = cursor)
            }, { "gen-1" }, { "acl-v1" }, { true },
        )
        val result = requireNotNull(source.readAuthorized(limit = 3))
        assertEquals(3, requestedLimit)
        assertTrue(result.legacyUndated)
        assertEquals(cursor, result.nextCursor)
        assertEquals("REALITY_ONLY_NOT_FEDERATED", result.coverage)
        assertEquals("reality.execution_receipt", result.canonicalSource)
        assertFalse(result.globallyComplete)
    }

    @Test
    fun revocationDuringOwnerQuerySuppressesEntirePage() {
        var acl = "acl-1"
        val source = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                acl = "acl-2"
                empty()
            }, { "gen-1" }, { acl }, { true },
        )
        assertNull(source.readAuthorized())
    }

    @Test
    fun pointerAbaRevocationDuringQueryDoesNotReuseGenerationId() {
        var pointerCurrent = true
        val source = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                pointerCurrent = false // A->B->A: generation ID matches, sequence does not
                empty()
            }, { "gen-1" }, { "acl-1" }, { pointerCurrent },
        )
        assertNull(source.readAuthorized())
    }

    @Test
    fun ownerExceptionFailsClosedAndCancellationPropagates() {
        val broken = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                throw IllegalStateException("store closed")
            }, { "gen-1" }, { "acl-1" }, { true },
        )
        assertNull(broken.readAuthorized())
        val cancelled = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                throw CancellationException("cancel")
            }, { "gen-1" }, { "acl-1" }, { true },
        )
        assertThrows(CancellationException::class.java) { cancelled.readAuthorized() }
    }

    @Test
    fun rejectsInvalidLimitBeforeOwnerRead() {
        val reads = AtomicInteger(0)
        val source = FaroV5RealityTimedActivityReadSource(
            "gen-1", RealityTimedReceiptHistoryReadPort {
                reads.incrementAndGet()
                empty()
            }, { "gen-1" }, { "acl-1" }, { true },
        )
        assertThrows(IllegalArgumentException::class.java) { source.readAuthorized(limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { source.readAuthorized(limit = 101) }
        assertEquals(0, reads.get())
    }
}
