package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionRequest
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionResult
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutor
import ar.com.faro.v5.kernel.contracts.AiRouteFailureAction
import ar.com.faro.v5.kernel.contracts.AiRouteFailurePolicy
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocationResult
import ar.com.faro.v5.kernel.contracts.ModelBindingRegistry
import ar.com.faro.v5.kernel.contracts.RetryDisposition

object DeterministicAiFallbackExecutor : AiFallbackExecutor {
    override fun execute(
        request: AiFallbackExecutionRequest,
        registry: ModelBindingRegistry,
        failurePolicy: AiRouteFailurePolicy,
        cancellationSignal: CancellationSignal,
    ): AiFallbackExecutionResult {
        val ordered = request.routePlan.authorizedBindings
        val attempted = mutableListOf<ModelBindingId>()

        if (cancellationSignal.isCancelled()) {
            return stopped(
                bindingId = null,
                failure = cancelledFailure(),
                attempted = attempted,
            )
        }

        if (ordered.isEmpty()) {
            return stopped(
                bindingId = null,
                failure = unavailableFailure(),
                attempted = attempted,
            )
        }

        ordered.forEachIndexed { index, binding ->
            if (cancellationSignal.isCancelled()) {
                return stopped(
                    bindingId = binding.bindingId,
                    failure = cancelledFailure(),
                    attempted = attempted,
                )
            }

            attempted += binding.bindingId
            val result =
                registry.resolve(binding.bindingId)
                    ?.reason(
                        invocation = request.invocation,
                        cancellationSignal = cancellationSignal,
                    )
                    ?: ModelBindingInvocationResult.Failure(
                        unavailableFailure()
                    )

            when (result) {
                is ModelBindingInvocationResult.Success ->
                    return AiFallbackExecutionResult.Success(
                        bindingId = binding.bindingId,
                        output = result.output,
                        usage = result.usage,
                        attemptedBindingIds = attempted.toList(),
                    )

                is ModelBindingInvocationResult.Failure -> {
                    val hasNext = index < ordered.lastIndex
                    val decision =
                        failurePolicy.decide(
                            failure = result.failure,
                            hasNextBinding = hasNext,
                            sameBindingRetryAllowed =
                                request.sameBindingRetryAllowed,
                        )

                    when (decision.action) {
                        AiRouteFailureAction.TRY_NEXT_BINDING -> {
                            check(hasNext) {
                                "failure policy requested next binding when none exists"
                            }
                        }

                        AiRouteFailureAction.RETRY_SAME_BINDING ->
                            return AiFallbackExecutionResult.RetrySameBinding(
                                bindingId = binding.bindingId,
                                failure = result.failure,
                                attemptedBindingIds = attempted.toList(),
                            )

                        AiRouteFailureAction.DEFER ->
                            return AiFallbackExecutionResult.Deferred(
                                bindingId = binding.bindingId,
                                failure = result.failure,
                                retryAfterMillis = decision.retryAfterMillis,
                                attemptedBindingIds = attempted.toList(),
                            )

                        AiRouteFailureAction.STOP ->
                            return stopped(
                                bindingId = binding.bindingId,
                                failure = result.failure,
                                attempted = attempted,
                            )
                    }
                }
            }
        }

        return stopped(
            bindingId = attempted.lastOrNull(),
            failure = unavailableFailure(),
            attempted = attempted,
        )
    }

    private fun stopped(
        bindingId: ModelBindingId?,
        failure: AiFailure,
        attempted: List<ModelBindingId>,
    ): AiFallbackExecutionResult.Stopped =
        AiFallbackExecutionResult.Stopped(
            bindingId = bindingId,
            failure = failure,
            attemptedBindingIds = attempted.toList(),
        )

    private fun cancelledFailure(): AiFailure =
        AiFailure(
            kind = AiFailureKind.CANCELLED,
            retryDisposition = RetryDisposition.NOT_RETRYABLE,
        )

    private fun unavailableFailure(): AiFailure =
        AiFailure(
            kind = AiFailureKind.MODEL_UNAVAILABLE,
            retryDisposition = RetryDisposition.NOT_RETRYABLE,
        )
}
