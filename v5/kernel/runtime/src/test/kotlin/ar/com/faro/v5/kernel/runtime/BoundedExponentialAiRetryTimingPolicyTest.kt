package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiRetryJitterSource
import ar.com.faro.v5.kernel.contracts.AiRetryTimingConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BoundedExponentialAiRetryTimingPolicyTest {
    private val config = AiRetryTimingConfig(
        baseDelayMillis = 1_000,
        maxDelayMillis = 8_000,
        maxSameBindingRetries = 4,
        maxJitterMillis = 500,
    )

    @Test
    fun `backoff grows exponentially and stays capped`() {
        val zeroJitter = AiRetryJitterSource { 0L }

        assertEquals(
            1_000L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(1, null, config, zeroJitter)
                ?.delayMillis,
        )
        assertEquals(
            2_000L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(2, null, config, zeroJitter)
                ?.delayMillis,
        )
        assertEquals(
            4_000L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(3, null, config, zeroJitter)
                ?.delayMillis,
        )
        assertEquals(
            8_000L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(4, null, config, zeroJitter)
                ?.delayMillis,
        )
    }

    @Test
    fun `jitter is added but client backoff remains capped`() {
        val maxJitter = AiRetryJitterSource { upper -> upper }

        assertEquals(
            1_500L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(1, null, config, maxJitter)
                ?.delayMillis,
        )
        assertEquals(
            8_000L,
            BoundedExponentialAiRetryTimingPolicy
                .delayFor(4, null, config, maxJitter)
                ?.delayMillis,
        )
    }

    @Test
    fun `server retry after acts as minimum delay even above client cap`() {
        val delay = BoundedExponentialAiRetryTimingPolicy.delayFor(
            retryOrdinal = 1,
            serverMinimumDelayMillis = 30_000,
            config = config,
            jitterSource = AiRetryJitterSource { 0L },
        )

        assertEquals(30_000L, delay?.delayMillis)
    }

    @Test
    fun `retry beyond configured maximum is not scheduled`() {
        assertNull(
            BoundedExponentialAiRetryTimingPolicy.delayFor(
                retryOrdinal = 5,
                serverMinimumDelayMillis = null,
                config = config,
                jitterSource = AiRetryJitterSource { 0L },
            )
        )
    }

    @Test
    fun `out of range jitter fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedExponentialAiRetryTimingPolicy.delayFor(
                retryOrdinal = 1,
                serverMinimumDelayMillis = null,
                config = config,
                jitterSource = AiRetryJitterSource { it + 1L },
            )
        }
    }
}
