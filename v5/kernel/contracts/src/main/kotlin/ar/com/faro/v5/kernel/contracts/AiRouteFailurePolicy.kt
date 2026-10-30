package ar.com.faro.v5.kernel.contracts

enum class AiRouteFailureAction {
    RETRY_SAME_BINDING,
    TRY_NEXT_BINDING,
    DEFER,
    STOP,
}

data class AiRouteFailureDecision(
    val action: AiRouteFailureAction,
    val retryAfterMillis: Long? = null,
) {
    init {
        if (retryAfterMillis != null) {
            require(retryAfterMillis > 0L)
            require(action == AiRouteFailureAction.DEFER)
        }
    }
}

fun interface AiRouteFailurePolicy {
    fun decide(
        failure: AiFailure,
        hasNextBinding: Boolean,
        sameBindingRetryAllowed: Boolean,
    ): AiRouteFailureDecision
}
