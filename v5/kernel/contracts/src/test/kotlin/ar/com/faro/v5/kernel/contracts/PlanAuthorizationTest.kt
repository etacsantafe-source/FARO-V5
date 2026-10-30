package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlanAuthorizationTest {
    @Test
    fun `authorization reference is tied to one exact plan fingerprint`() {
        val approved = PlanAuthorizationRef(PlanFingerprint("plan-a"))
        val same = PlanAuthorizationRef(PlanFingerprint("plan-a"))
        val changed = PlanAuthorizationRef(PlanFingerprint("plan-b"))

        assertEquals(approved, same)
        assertNotEquals(approved, changed)
    }
}
