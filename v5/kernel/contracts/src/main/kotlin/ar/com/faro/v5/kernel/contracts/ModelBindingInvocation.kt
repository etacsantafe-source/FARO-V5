package ar.com.faro.v5.kernel.contracts

data class ModelBindingInvocation(
    val request: ReasoningRequest,
    val promptRef: PromptRef,
    val promptFingerprint: PromptFingerprint,
    val contextPack: ContextPack,
) {
    init {
        val systemPrompts =
            contextPack.fragments.filter {
                it.contextClass == ContextClass.SYSTEM_PROMPT
            }
        val currentRequests =
            contextPack.fragments.filter {
                it.contextClass == ContextClass.CURRENT_REQUEST
            }

        require(systemPrompts.size == 1) {
            "model binding invocation requires exactly one system prompt fragment"
        }
        require(currentRequests.size == 1) {
            "model binding invocation requires exactly one current request fragment"
        }
        require(systemPrompts.single().required) {
            "system prompt fragment must be required"
        }
        require(currentRequests.single().required) {
            "current request fragment must be required"
        }
        require(currentRequests.single().payload == request.input) {
            "current request fragment must match the exact reasoning request input"
        }
        require(
            PromptArtifact(
                ref = promptRef,
                content = systemPrompts.single().payload,
            ).fingerprint == promptFingerprint
        ) {
            "system prompt fragment must match the frozen prompt fingerprint"
        }
    }
}

sealed interface ModelBindingInvocationResult {
    data class Success(
        val output: CanonicalPayload,
        val usage: AiUsage,
    ) : ModelBindingInvocationResult

    data class Failure(
        val failure: AiFailure,
    ) : ModelBindingInvocationResult
}

interface ModelBindingReasoningPort {
    val bindingId: ModelBindingId

    fun reason(
        invocation: ModelBindingInvocation,
        cancellationSignal: CancellationSignal,
    ): ModelBindingInvocationResult
}

interface ModelBindingRegistry {
    fun resolve(bindingId: ModelBindingId): ModelBindingReasoningPort?
    fun bindingIds(): List<ModelBindingId>
}
