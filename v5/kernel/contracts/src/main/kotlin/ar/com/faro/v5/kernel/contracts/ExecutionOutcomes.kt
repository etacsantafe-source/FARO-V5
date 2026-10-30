package ar.com.faro.v5.kernel.contracts

enum class ExecutionOutcomeState {
    NOT_STARTED,
    APPLYING,
    RECOVERING_UNKNOWN,
    REJECTED,
    COMMITTED,
    PARTIAL_COMPENSATION_REQUIRED,
}

enum class RetryDisposition {
    RETRYABLE,
    NOT_RETRYABLE,
    REQUIRES_REPREPARE,
    REQUIRES_HUMAN_ACTION,
}

enum class EffectCertainty {
    NO_EFFECT_KNOWN,
    EFFECT_COMMITTED,
    EFFECT_REJECTED,
    EFFECT_UNKNOWN_RECOVERING,
    PARTIAL_EFFECT,
}


data class ExecutionOutcome(
    val state: ExecutionOutcomeState,
    val retryDisposition: RetryDisposition,
    val effectCertainty: EffectCertainty,
)
