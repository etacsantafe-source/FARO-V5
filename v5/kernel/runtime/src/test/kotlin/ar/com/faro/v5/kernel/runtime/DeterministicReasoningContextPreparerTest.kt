package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelRequestId
import ar.com.faro.v5.kernel.contracts.ModelRequirements
import ar.com.faro.v5.kernel.contracts.PromptArtifact
import ar.com.faro.v5.kernel.contracts.PromptId
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptVersion
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationRequest
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationResult
import ar.com.faro.v5.kernel.contracts.ReasoningRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeterministicReasoningContextPreparerTest {
    private val assembler = DeterministicContextAssembler()

    @Test
    fun `preparation freezes exact prompt and request inside bounded context`() {
        val prompt = prompt()
        val request = reasoningRequest()
        val optional = ContextFragment(
            id = ContextFragmentId("reality:balance"),
            contextClass = ContextClass.REALITY,
            payload = payload("balance"),
            estimatedTokens = 10,
            priority = 5,
            required = false,
            egressSensitivity = EgressSensitivity.PRIVATE,
        )

        val result = DeterministicReasoningContextPreparer.prepare(
            request = preparationRequest(
                request = request,
                promptRef = prompt.ref,
                contextualFragments = listOf(optional),
                budget = ContextBudget(100, 10_000, 5),
            ),
            promptRegistry = ImmutablePromptRegistry.of(listOf(prompt)),
            assembler = assembler,
        )

        val ready = assertInstanceOf(
            ReasoningContextPreparationResult.Ready::class.java,
            result,
        )
        val invocation = ready.invocation

        assertEquals(prompt.ref, invocation.promptRef)
        assertEquals(prompt.fingerprint, invocation.promptFingerprint)
        assertEquals(
            listOf(
                ContextClass.SYSTEM_PROMPT,
                ContextClass.CURRENT_REQUEST,
                ContextClass.REALITY,
            ),
            invocation.contextPack.fragments.map { it.contextClass },
        )
        assertTrue(invocation.contextPack.fragments[0].required)
        assertTrue(invocation.contextPack.fragments[1].required)
        assertEquals(prompt.content, invocation.contextPack.fragments[0].payload)
        assertEquals(request.input, invocation.contextPack.fragments[1].payload)
    }

    @Test
    fun `missing exact prompt is explicit`() {
        val request = reasoningRequest()
        val missing = PromptRef(
            promptId = PromptId("missing"),
            version = PromptVersion(3),
        )

        val result = DeterministicReasoningContextPreparer.prepare(
            request = preparationRequest(
                request = request,
                promptRef = missing,
                contextualFragments = emptyList(),
                budget = ContextBudget(100, 10_000, 5),
            ),
            promptRegistry = ImmutablePromptRegistry.of(emptyList()),
            assembler = assembler,
        )

        val failure = assertInstanceOf(
            ReasoningContextPreparationResult.PromptMissing::class.java,
            result,
        )
        assertEquals(missing, failure.promptRef)
    }

    @Test
    fun `required overflow is explicit and never drops required context`() {
        val prompt = prompt()
        val request = reasoningRequest()
        val requiredReality = ContextFragment(
            id = ContextFragmentId("reality:required"),
            contextClass = ContextClass.REALITY,
            payload = payload("required reality"),
            estimatedTokens = 80,
            priority = 1,
            required = true,
            egressSensitivity = EgressSensitivity.PRIVATE,
        )

        val result = DeterministicReasoningContextPreparer.prepare(
            request = preparationRequest(
                request = request,
                promptRef = prompt.ref,
                contextualFragments = listOf(requiredReality),
                budget = ContextBudget(
                    maxEstimatedTokens = 30,
                    maxBytes = 10_000,
                    maxFragments = 5,
                ),
            ),
            promptRegistry = ImmutablePromptRegistry.of(listOf(prompt)),
            assembler = assembler,
        )

        val failure = assertInstanceOf(
            ReasoningContextPreparationResult.RequiredContentTooLarge::class.java,
            result,
        )
        assertTrue(ContextFragmentId("reality:required") in failure.requiredFragmentIds)
    }

    @Test
    fun `context caller cannot inject another system prompt or current request`() {
        val illegal = ContextFragment(
            id = ContextFragmentId("fake-system"),
            contextClass = ContextClass.SYSTEM_PROMPT,
            payload = payload("fake"),
            estimatedTokens = 1,
            required = true,
            egressSensitivity = EgressSensitivity.PUBLIC,
        )

        assertThrows(IllegalArgumentException::class.java) {
            preparationRequest(
                request = reasoningRequest(),
                promptRef = prompt().ref,
                contextualFragments = listOf(illegal),
                budget = ContextBudget(100, 10_000, 5),
            )
        }
    }

    private fun preparationRequest(
        request: ReasoningRequest,
        promptRef: PromptRef,
        contextualFragments: List<ContextFragment>,
        budget: ContextBudget,
    ): ReasoningContextPreparationRequest =
        ReasoningContextPreparationRequest(
            request = request,
            promptRef = promptRef,
            contextualFragments = contextualFragments,
            budget = budget,
            promptEstimatedTokens = 10,
            requestEstimatedTokens = 10,
            promptSensitivity = EgressSensitivity.PUBLIC,
            requestSensitivity = EgressSensitivity.PRIVATE,
        )

    private fun reasoningRequest(): ReasoningRequest =
        ReasoningRequest(
            requestId = ModelRequestId("request-1"),
            profileId = ModelProfileId("conversation"),
            requirements = ModelRequirements(
                requiredCapabilities = setOf(ModelCapability.TEXT),
            ),
            input = payload("hello"),
        )

    private fun prompt(): PromptArtifact =
        PromptArtifact(
            ref = PromptRef(
                promptId = PromptId("conversation-core"),
                version = PromptVersion(2),
            ),
            content = payload("system prompt"),
        )

    private fun payload(value: String): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = "text/plain",
            bytes = value.toByteArray(),
        )
}
