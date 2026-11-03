package ar.com.faro.v5.surface

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Contract test: same readPort instance must not retain a count after owner
 * revocation has emitted an invalidation. This is NOT a Compose/physical test. */
class FaroV5HomeInvalidationTest {
    @Test
    fun samePortRevocationMakesPreviouslyReadNoticeUnavailable() = runBlocking {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var authorized = true
        var reads = 0
        val port = FaroV5HomeReadCoordinator(
            owners = mapOf(
                FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                    expectedCanonicalSource = "notice.read",
                    readPort = FaroV5HomeIndicatorOwner {
                        reads++
                        FaroV5HomeCount(3, 100L, "notice.read")
                    },
                    authorityStamp = FaroV5HomeAuthorityStamp {
                        if (authorized) "generation-1" else null
                    },
                ),
            ),
            invalidations = changes,
        )
        val values = mutableListOf<String>()
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            port.invalidations.onStart { emit(Unit) }.collectLatest {
                values.add(faroV5HomeCountText(port.readIndicators()[FaroV5HomeIndicator.AVISOS]))
            }
        }
        withTimeout(2_000L) { while (values.size < 1) yield() }
        assertEquals(listOf("3"), values)
        authorized = false
        changes.emit(Unit)
        withTimeout(2_000L) { while (values.size < 2) yield() }
        assertEquals(listOf("3", "No disponible"), values)
        assertEquals(1, reads)
        reader.cancelAndJoin()
    }
}
