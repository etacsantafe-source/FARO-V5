package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlatformAvailabilityTest {
    @Test
    fun idsRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformRequirementId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformFeatureId("")
        }
    }

    @Test
    fun availableFeatureCannotCarryBlockers() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformAvailability(
                featureId = PlatformFeatureId("notifications"),
                state = PlatformAvailabilityState.AVAILABLE,
                blockers = setOf(PlatformRequirementId("post_notifications")),
            )
        }
    }

    @Test
    fun deniedRequirementCanDegradeFeatureHonestly() {
        val requirement = PlatformRequirementId("post_notifications")
        val status = PlatformRequirementStatus(
            requirementId = requirement,
            state = PlatformRequirementState.DENIED,
        )
        val availability = PlatformAvailability(
            featureId = PlatformFeatureId("notifications"),
            state = PlatformAvailabilityState.DEGRADED,
            blockers = setOf(requirement),
        )

        assertEquals(PlatformRequirementState.DENIED, status.state)
        assertEquals(PlatformAvailabilityState.DEGRADED, availability.state)
        assertEquals(setOf(requirement), availability.blockers)
    }
}
