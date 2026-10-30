package ar.com.faro.v5.kernel.contracts

data class ReasoningContextPreparationRequest(
    val request: ReasoningRequest,
    val promptRef: PromptRef,
    val contextualFragments: List<ContextFragment>,
    val budget: ContextBudget,
    val promptEstimatedTokens: Int,
    val requestEstimatedTokens: Int,
    val promptSensitivity: EgressSensitivity,
    val requestSensitivity: EgressSensitivity,
) {
    init {
        require(promptEstimatedTokens > 0) {
            "prompt estimated tokens must be positive"
        }
        require(requestEstimatedTokens > 0) {
            "request estimated tokens must be positive"
        }
        require(
            contextualFragments.none {
                it.contextClass == ContextClass.SYSTEM_PROMPT ||
                    it.contextClass == ContextClass.CURRENT_REQUEST
            }
        ) {
            "contextual fragments cannot inject system prompt or current request classes"
        }
    }
}

sealed interface ReasoningContextPreparationResult {
    data class Ready(
        val invocation: ModelBindingInvocation,
    ) : ReasoningContextPreparationResult

    data class PromptMissing(
        val promptRef: PromptRef,
    ) : ReasoningContextPreparationResult

    data class RequiredContentTooLarge(
        val requiredFragmentIds: List<ContextFragmentId>,
    ) : ReasoningContextPreparationResult
}

fun interface ReasoningContextPreparer {
    fun prepare(
        request: ReasoningContextPreparationRequest,
        promptRegistry: PromptRegistry,
        assembler: ContextAssembler,
    ): ReasoningContextPreparationResult
}
