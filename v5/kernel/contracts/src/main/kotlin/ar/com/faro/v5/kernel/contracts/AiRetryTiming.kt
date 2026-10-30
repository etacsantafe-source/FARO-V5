package ar.com.faro.v5.kernel.contracts

data class AiRetryTimingConfig(
    val baseDelayMillis: Long,
    val maxDelayMillis: Long,
    val maxSameBindingRetries: Int,
    val maxJitterMillis: Long,
) {
    init {
        require(baseDelayMillis > 0L) {
            "retry base delay must be positive"
        }
        require(maxDelayMillis >= baseDelayMillis) {
            "retry max delay must cover base delay"
        }
        require(maxSameBindingRetries >= 0) {
            "max same-binding retries must be non-negative"
        }
        require(maxJitterMillis >= 0L) {
            "retry jitter must be non-negative"
        }
        require(maxJitterMillis <= maxDelayMillis) {
            "retry jitter cannot exceed max delay"
        }
    }
}

fun interface AiRetryJitterSource {
    fun nextMillis(maxInclusiveMillis: Long): Long
}

data class AiRetryDelay(
    val delayMillis: Long,
) {
    init {
        require(delayMillis >= 0L) {
            "retry delay must be non-negative"
        }
    }
}

fun interface AiRetryTimingPolicy {
    fun delayFor(
        retryOrdinal: Int,
        serverMinimumDelayMillis: Long?,
        config: AiRetryTimingConfig,
        jitterSource: AiRetryJitterSource,
    ): AiRetryDelay?
}


enum class AiRetryWaitResult {
    COMPLETED,
    CANCELLED,
}

fun interface AiRetryWaitPort {
    fun await(
        delay: AiRetryDelay,
        cancellationSignal: CancellationSignal,
    ): AiRetryWaitResult
}
