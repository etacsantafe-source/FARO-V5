package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContextAssembler
import ar.com.faro.v5.kernel.contracts.ContextAssemblyResult
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocation
import ar.com.faro.v5.kernel.contracts.PromptRegistry
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationRequest
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationResult
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparer

object DeterministicReasoningContextPreparer : ReasoningContextPreparer {
    override fun prepare(
        request: ReasoningContextPreparationRequest,
        promptRegistry: PromptRegistry,
        assembler: ContextAssembler,
    ): ReasoningContextPreparationResult {
        val prompt =
            promptRegistry.resolve(request.promptRef)
                ?: return ReasoningContextPreparationResult.PromptMissing(
                    promptRef = request.promptRef,
                )

        val promptFragment = ContextFragment(
            id = ContextFragmentId(
                "prompt:" +
                    request.promptRef.promptId.value +
                    ":v" +
                    request.promptRef.version.value
            ),
            contextClass = ContextClass.SYSTEM_PROMPT,
            payload = prompt.content,
            estimatedTokens = request.promptEstimatedTokens,
            priority = Int.MAX_VALUE,
            required = true,
            egressSensitivity = request.promptSensitivity,
        )
        val currentRequestFragment = ContextFragment(
            id = ContextFragmentId(
                "request:" + request.request.requestId.value
            ),
            contextClass = ContextClass.CURRENT_REQUEST,
            payload = request.request.input,
            estimatedTokens = request.requestEstimatedTokens,
            priority = Int.MAX_VALUE - 1,
            required = true,
            egressSensitivity = request.requestSensitivity,
        )

        return when (
            val assembly = assembler.assemble(
                fragments =
                    listOf(promptFragment, currentRequestFragment) +
                        request.contextualFragments,
                budget = request.budget,
            )
        ) {
            is ContextAssemblyResult.RequiredContentTooLarge ->
                ReasoningContextPreparationResult.RequiredContentTooLarge(
                    requiredFragmentIds = assembly.requiredFragmentIds,
                )

            is ContextAssemblyResult.Ready ->
                ReasoningContextPreparationResult.Ready(
                    invocation = ModelBindingInvocation(
                        request = request.request,
                        promptRef = prompt.ref,
                        promptFingerprint = prompt.fingerprint,
                        contextPack = assembly.pack,
                    )
                )
        }
    }
}
