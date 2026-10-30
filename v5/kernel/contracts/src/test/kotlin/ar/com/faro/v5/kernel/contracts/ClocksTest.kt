package ar.com.faro.v5.kernel.contracts

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClocksTest {
    @Test
    fun `civil time keeps its explicit zone and monotonic time stays independent`() {
        val civil = object : CivilClock {
            override val zoneId: ZoneId = ZoneId.of("America/Argentina/Cordoba")
            override fun now(): Instant = Instant.parse("2026-10-21T20:00:00Z")
        }
        val monotonic = MonotonicClock { 123_456L }

        assertEquals("America/Argentina/Cordoba", civil.nowZoned().zone.id)
        assertEquals(123_456L, monotonic.readNanos())
    }
}
