package ar.com.faro.v5.kernel.contracts

data class AiFallbackExecutionRequest(
    val routePlan: AiBudgetedRoutePlan,
    val invocation: ModelBindingInvocation,
    val sameBindingRetryAllowed: Boolean,
)

sealed interface AiFallbackExecutionResult {
    val attemptedBindingIds: List<ModelBindingId>

    data class Success(
        val bindingId: ModelBindingId,
        val output: CanonicalPayload,
        val usage: AiUsage,
        override val attemptedBindingIds: List<ModelBindingId>,
    ) : AiFallbackExecutionResult {
        init {
            require(attemptedBindingIds.isNotEmpty()) {
                "successful fallback execution must record at least one attempt"
            }
            require(attemptedBindingIds.last() == bindingId) {
                "successful binding must be the last attempted binding"
            }
        }

        val fallbackUsed: Boolean
            get() = attemptedBindingIds.first() != bindingId
    }

    data class RetrySameBinding(
        val bindingId: ModelBindingId,
        val failure: AiFailure,
        override val attemptedBindingIds: List<ModelBindingId>,
    ) : AiFallbackExecutionResult

    data class Deferred(
        val bindingId: ModelBindingId?,
        val failure: AiFailure,
        val retryAfterMillis: Long?,
        override val attemptedBindingIds: List<ModelBindingId>,
    ) : AiFallbackExecutionResult {
        init {
            if (retryAfterMillis != null) {
                require(retryAfterMillis > 0L) {
                    "deferred retry-after must be positive"
                }
            }
        }
    }

    data class Stopped(
        val bindingId: ModelBindingId?,
        val failure: AiFailure,
        override val attemptedBindingIds: List<ModelBindingId>,
    ) : AiFallbackExecutionResult
}

fun interface AiFallbackExecutor {
    fun execute(
        request: AiFallbackExecutionRequest,
        registry: ModelBindingRegistry,
        failurePolicy: AiRouteFailurePolicy,
        cancellationSignal: CancellationSignal,
    ): AiFallbackExecutionResult
}
