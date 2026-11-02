package ar.com.faro.v5.surface

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5HomeReadCoordinatorTest {
    @Test
    fun realZeroAndUnavailableOwnerRemainDifferent() = runBlocking {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(
                FaroV5HomeIndicator.AVISOS to FaroV5HomeIndicatorOwner {
                    FaroV5HomeCount(0, 123L, "notice.read")
                },
                FaroV5HomeIndicator.GUARDADOS to FaroV5HomeIndicatorOwner { null },
            ),
        )
        val counts = adapter.readIndicators()
        assertEquals("0", faroV5HomeCountText(counts[FaroV5HomeIndicator.AVISOS]))
        assertEquals("No disponible", faroV5HomeCountText(counts[FaroV5HomeIndicator.GUARDADOS]))
        assertEquals("No disponible", faroV5HomeCountText(counts[FaroV5HomeIndicator.PENDIENTES]))
        assertEquals(1, counts.size)
    }

    @Test
    fun oneOwnerFailureDoesNotMaskOtherAuthorizedOwners() = runBlocking {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(
                FaroV5HomeIndicator.PENDIENTES to FaroV5HomeIndicatorOwner {
                    throw SecurityException("permission revoked")
                },
                FaroV5HomeIndicator.ACTIVIDAD_RECIENTE to FaroV5HomeIndicatorOwner {
                    FaroV5HomeCount(3, 456L, "history.query")
                },
            ),
        )
        val counts = adapter.readIndicators()
        assertTrue(FaroV5HomeIndicator.PENDIENTES !in counts)
        assertEquals(3, counts[FaroV5HomeIndicator.ACTIVIDAD_RECIENTE]?.count)
        assertEquals(456L, counts[FaroV5HomeIndicator.ACTIVIDAD_RECIENTE]?.asOfEpochMillis)
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to FaroV5HomeIndicatorOwner {
                throw CancellationException("host changed")
            }),
        )
        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.readIndicators() }
        }
    }

    @Test
    fun emptyOwnersCannotProduceInventedCounts() = runBlocking {
        val counts = FaroV5HomeReadCoordinator(emptyMap()).readIndicators()
        assertTrue(counts.isEmpty())
    }
}
