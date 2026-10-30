package ar.com.faro.v5.delivery

import ar.com.faro.v5.delivery.spi.WallClockAlarmIdentity
import ar.com.faro.v5.delivery.spi.WallClockAlarmPrecision
import ar.com.faro.v5.delivery.spi.WallClockAlarmProjectionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WallClockAlarmProjectionPortTest {
    @Test
    fun acceptsOpaqueIdentityAndExplicitExactPrecision() {
        val identity =
            WallClockAlarmIdentity(
                workKind = "delivery.notification.converge",
                semanticId = "delivery-42",
            )
        val request =
            WallClockAlarmProjectionRequest(
                identity = identity,
                triggerAtEpochMillis = 1_800_000_000_000L,
                precision = WallClockAlarmPrecision.EXACT_REQUIRED,
            )

        assertEquals("delivery.notification.converge", request.identity.workKind)
        assertEquals("delivery-42", request.identity.semanticId)
        assertEquals(WallClockAlarmPrecision.EXACT_REQUIRED, request.precision)
    }

    @Test
    fun rejectsNonOpaqueSchedulingIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            WallClockAlarmIdentity("delivery notification", "delivery-42")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallClockAlarmIdentity("delivery.notification", "../truth")
        }
    }

    @Test
    fun rejectsNonPositiveWallClockTrigger() {
        val identity = WallClockAlarmIdentity("delivery.notification", "delivery-42")
        assertThrows(IllegalArgumentException::class.java) {
            WallClockAlarmProjectionRequest(
                identity = identity,
                triggerAtEpochMillis = 0L,
                precision = WallClockAlarmPrecision.INEXACT,
            )
        }
    }
}
