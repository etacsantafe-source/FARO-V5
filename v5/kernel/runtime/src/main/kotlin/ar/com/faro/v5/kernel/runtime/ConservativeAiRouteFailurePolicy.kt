package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiRouteFailureAction
import ar.com.faro.v5.kernel.contracts.AiRouteFailureDecision
import ar.com.faro.v5.kernel.contracts.AiRouteFailurePolicy
import ar.com.faro.v5.kernel.contracts.RetryDisposition

object ConservativeAiRouteFailurePolicy : AiRouteFailurePolicy {
    override fun decide(
        failure: AiFailure,
        hasNextBinding: Boolean,
        sameBindingRetryAllowed: Boolean,
    ): AiRouteFailureDecision =
        when (failure.kind) {
            AiFailureKind.CANCELLED,
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
            AiFailureKind.BUDGET_BLOCKED,
            AiFailureKind.TERMINAL_REQUEST_ERROR -> stop()

            AiFailureKind.RATE_LIMIT,
            AiFailureKind.PROVIDER_CAPACITY ->
                if (hasNextBinding) next() else deferOrStop(failure)

            AiFailureKind.MODEL_UNAVAILABLE,
            AiFailureKind.MODEL_DEPRECATED,
            AiFailureKind.UNSUPPORTED_CAPABILITY,
            AiFailureKind.AUTH_MISSING,
            AiFailureKind.AUTH_REJECTED,
            AiFailureKind.POLICY_EGRESS_BLOCKED,
            AiFailureKind.CONTEXT_TOO_LARGE ->
                if (hasNextBinding) next() else stop()

            AiFailureKind.TIMEOUT,
            AiFailureKind.TRANSIENT_SERVER ->
                when {
                    failure.retryDisposition == RetryDisposition.RETRYABLE &&
                        sameBindingRetryAllowed -> retrySame()
                    hasNextBinding -> next()
                    else -> deferOrStop(failure)
                }

            AiFailureKind.OFFLINE,
            AiFailureKind.DNS,
            AiFailureKind.CONNECT ->
                if (hasNextBinding) next() else deferOrStop(failure)
        }

    private fun deferOrStop(failure: AiFailure): AiRouteFailureDecision =
        if (failure.retryDisposition == RetryDisposition.RETRYABLE) {
            AiRouteFailureDecision(
                action = AiRouteFailureAction.DEFER,
                retryAfterMillis = failure.retryAfterMillis,
            )
        } else {
            stop()
        }

    private fun retrySame() =
        AiRouteFailureDecision(AiRouteFailureAction.RETRY_SAME_BINDING)

    private fun next() =
        AiRouteFailureDecision(AiRouteFailureAction.TRY_NEXT_BINDING)

    private fun stop() =
        AiRouteFailureDecision(AiRouteFailureAction.STOP)
}
