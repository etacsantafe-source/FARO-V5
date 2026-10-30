package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiRetryDelay
import ar.com.faro.v5.kernel.contracts.AiRetryJitterSource
import ar.com.faro.v5.kernel.contracts.AiRetryTimingConfig
import ar.com.faro.v5.kernel.contracts.AiRetryTimingPolicy

object BoundedExponentialAiRetryTimingPolicy : AiRetryTimingPolicy {
    override fun delayFor(
        retryOrdinal: Int,
        serverMinimumDelayMillis: Long?,
        config: AiRetryTimingConfig,
        jitterSource: AiRetryJitterSource,
    ): AiRetryDelay? {
        require(retryOrdinal > 0) {
            "retry ordinal must be one-based"
        }
        if (serverMinimumDelayMillis != null) {
            require(serverMinimumDelayMillis >= 0L) {
                "server minimum retry delay must be non-negative"
            }
        }
        if (retryOrdinal > config.maxSameBindingRetries) {
            return null
        }

        var exponential = config.baseDelayMillis
        repeat(retryOrdinal - 1) {
            exponential =
                if (exponential >= config.maxDelayMillis / 2L) {
                    config.maxDelayMillis
                } else {
                    (exponential * 2L).coerceAtMost(config.maxDelayMillis)
                }
        }

        val jitter = jitterSource.nextMillis(config.maxJitterMillis)
        require(jitter in 0L..config.maxJitterMillis) {
            "retry jitter source returned out-of-range value"
        }

        val clientDelay =
            if (jitter > config.maxDelayMillis - exponential) {
                config.maxDelayMillis
            } else {
                exponential + jitter
            }

        return AiRetryDelay(
            delayMillis = maxOf(
                clientDelay,
                serverMinimumDelayMillis ?: 0L,
            )
        )
    }
}
