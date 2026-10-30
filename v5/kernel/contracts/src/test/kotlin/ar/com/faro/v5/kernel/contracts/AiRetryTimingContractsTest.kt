package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiRetryTimingContractsTest {
    @Test
    fun `retry timing config is bounded and non-negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiRetryTimingConfig(
                baseDelayMillis = 1000,
                maxDelayMillis = 500,
                maxSameBindingRetries = 1,
                maxJitterMillis = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRetryTimingConfig(
                baseDelayMillis = 1000,
                maxDelayMillis = 4000,
                maxSameBindingRetries = -1,
                maxJitterMillis = 0,
            )
        }
    }
}
