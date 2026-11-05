package ar.com.faro.v5.surface

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5HomeReadCoordinatorTest {
    private fun owner(source: String, block: suspend () -> FaroV5HomeCount?) =
        FaroV5HomeOwnerBinding(
            source,
            FaroV5HomeIndicatorOwner { block() },
            FaroV5HomeAuthorityStamp { "authorized-generation-1" },
        )

    @Test
    fun realZeroAndUnavailableOwnerRemainDifferent() = runBlocking {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(
                FaroV5HomeIndicator.AVISOS to owner("notice.read") {
                    FaroV5HomeCount(0, 123L, "notice.read")
                },
                FaroV5HomeIndicator.GUARDADOS to owner("saved.canonical") { null },
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
                FaroV5HomeIndicator.PENDIENTES to owner("attention.read") {
                    throw SecurityException("permission revoked")
                },
                FaroV5HomeIndicator.ACTIVIDAD_RECIENTE to owner("history.query") {
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
    fun miswiredOwnerProvenanceIsUnavailableNotWrongCount() = runBlocking {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to owner("notice.read") {
                FaroV5HomeCount(8, 10L, "attention.read")
            }),
        )
        assertTrue(adapter.readIndicators().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            owner(" ") { FaroV5HomeCount(1, 1L, "notice.read") }
        }
        // Keep the JUnit method return type Unit; non-void tests are not discovered.
        Unit
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to owner("notice.read") {
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
    @Test
    fun revokedAuthorityDoesNotReadOwnerOrExposeStaleCount() = runBlocking {
        var ownerReads = 0
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                "notice.read",
                FaroV5HomeIndicatorOwner {
                    ownerReads++
                    FaroV5HomeCount(7, 100L, "notice.read")
                },
                FaroV5HomeAuthorityStamp { null },
            )),
        )
        assertTrue(adapter.readIndicators().isEmpty())
        assertEquals(0, ownerReads)
    }

    @Test
    fun generationSwitchDuringSuspendedReadFailsClosed() = runBlocking {
        var probes = 0
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.PENDIENTES to FaroV5HomeOwnerBinding(
                "attention.read",
                FaroV5HomeIndicatorOwner { FaroV5HomeCount(5, 100L, "attention.read") },
                FaroV5HomeAuthorityStamp {
                    probes++
                    if (probes == 1) "generation-1" else "generation-2"
                },
            )),
        )
        assertTrue(adapter.readIndicators().isEmpty())
        assertEquals(2, probes)
    }

    @Test
    fun revokedAuthorityAfterReadFailsClosed() = runBlocking {
        var probes = 0
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                "notice.read",
                FaroV5HomeIndicatorOwner { FaroV5HomeCount(3, 100L, "notice.read") },
                FaroV5HomeAuthorityStamp {
                    probes++
                    if (probes == 1) "generation-1" else null
                },
            )),
        )
        assertTrue(adapter.readIndicators().isEmpty())
    }

    @Test
    fun authorityProbeFailureIsUnknownWithoutMaskingOtherOwner() = runBlocking {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(
                FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                    "notice.read",
                    FaroV5HomeIndicatorOwner { FaroV5HomeCount(3, 100L, "notice.read") },
                    FaroV5HomeAuthorityStamp { throw SecurityException("revoked") },
                ),
                FaroV5HomeIndicator.ACTIVIDAD_RECIENTE to owner("history.query") {
                    FaroV5HomeCount(2, 101L, "history.query")
                },
            ),
        )
        val counts = adapter.readIndicators()
        assertTrue(FaroV5HomeIndicator.AVISOS !in counts)
        assertEquals(2, counts[FaroV5HomeIndicator.ACTIVIDAD_RECIENTE]?.count)
    }

    @Test
    fun authorityProbeCancellationPropagates() {
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                "notice.read",
                FaroV5HomeIndicatorOwner { FaroV5HomeCount(1, 100L, "notice.read") },
                FaroV5HomeAuthorityStamp { throw CancellationException("host switched") },
            )),
        )
        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.readIndicators() }
        }
        Unit
    }


    @Test
    fun earlierOwnerRevokedByLaterOwnerIsNotReturnedFromBatch() = runBlocking {
        var noticeGranted = true
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(
                FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                    "notice.read",
                    FaroV5HomeIndicatorOwner { FaroV5HomeCount(4, 100L, "notice.read") },
                    FaroV5HomeAuthorityStamp { if (noticeGranted) "generation-1:acl" else null },
                ),
                FaroV5HomeIndicator.PENDIENTES to FaroV5HomeOwnerBinding(
                    "attention.read",
                    FaroV5HomeIndicatorOwner {
                        noticeGranted = false
                        FaroV5HomeCount(2, 101L, "attention.read")
                    },
                    FaroV5HomeAuthorityStamp { "generation-1:attention-acl" },
                ),
            ),
        )
        val counts = adapter.readIndicators()
        assertTrue(FaroV5HomeIndicator.AVISOS !in counts)
        assertEquals(2, counts[FaroV5HomeIndicator.PENDIENTES]?.count)
    }

    @Test
    fun generationChangedAfterOwnerReadButBeforeReturnIsSuppressed() = runBlocking {
        var probes = 0
        val adapter = FaroV5HomeReadCoordinator(
            mapOf(FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                "notice.read",
                FaroV5HomeIndicatorOwner { FaroV5HomeCount(8, 200L, "notice.read") },
                FaroV5HomeAuthorityStamp {
                    probes++
                    if (probes <= 2) "generation-1" else "generation-2"
                },
            )),
        )
        assertTrue(adapter.readIndicators().isEmpty())
        assertEquals(3, probes)
    }

}
