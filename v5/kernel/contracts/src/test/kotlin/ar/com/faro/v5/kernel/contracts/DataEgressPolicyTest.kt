package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DataEgressPolicyTest {
    @Test
    fun `secret can never be allowlisted for remote model`() {
        assertThrows(IllegalArgumentException::class.java) {
            DataEgressPolicyConfig(
                remoteAllowed = setOf(EgressSensitivity.PUBLIC, EgressSensitivity.SECRET),
                onDeviceAllowed = setOf(EgressSensitivity.PUBLIC),
            )
        }
    }

    @Test
    fun `unclassified can never be allowlisted for on-device model`() {
        assertThrows(IllegalArgumentException::class.java) {
            DataEgressPolicyConfig(
                remoteAllowed = emptySet(),
                onDeviceAllowed = setOf(EgressSensitivity.UNCLASSIFIED),
            )
        }
    }
}
