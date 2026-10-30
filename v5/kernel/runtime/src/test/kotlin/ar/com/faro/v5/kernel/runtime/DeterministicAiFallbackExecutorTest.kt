package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetedRoutePlan
import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionRequest
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionResult
import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocation
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocationResult
import ar.com.faro.v5.kernel.contracts.ModelBindingReasoningPort
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelRequestId
import ar.com.faro.v5.kernel.contracts.ModelRequirements
import ar.com.faro.v5.kernel.contracts.PromptArtifact
import ar.com.faro.v5.kernel.contracts.PromptId
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptVersion
import ar.com.faro.v5.kernel.contracts.ReasoningRequest
import ar.com.faro.v5.kernel.contracts.RetryDisposition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeterministicAiFallbackExecutorTest {
    @Test
    fun `rate limit falls through to next authorized binding`() {
        val first = StubBinding(
            id = "first",
            result = failure(
                AiFailureKind.RATE_LIMIT,
                RetryDisposition.RETRYABLE,
                retryAfterMillis = 30_000,
            ),
        )
        val second = StubBinding(
            id = "second",
            result = success("ok"),
        )

        val result = execute(
            bindings = listOf(binding("first"), binding("second")),
            ports = listOf(first, second),
            sameBindingRetryAllowed = true,
        )

        val success = assertInstanceOf(
            AiFallbackExecutionResult.Success::class.java,
            result,
        )
        assertEquals(ModelBindingId("second"), success.bindingId)
        assertEquals(
            listOf(ModelBindingId("first"), ModelBindingId("second")),
            success.attemptedBindingIds,
        )
        assertTrue(success.fallbackUsed)
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `timeout returns retry-same-binding instead of retrying immediately`() {
        val only = StubBinding(
            id = "only",
            result = failure(
                AiFailureKind.TIMEOUT,
                RetryDisposition.RETRYABLE,
            ),
        )

        val result = execute(
            bindings = listOf(binding("only")),
            ports = listOf(only),
            sameBindingRetryAllowed = true,
        )

        val retry = assertInstanceOf(
            AiFallbackExecutionResult.RetrySameBinding::class.java,
            result,
        )
        assertEquals(ModelBindingId("only"), retry.bindingId)
        assertEquals(1, only.calls)
    }

    @Test
    fun `rate limit without fallback defers and preserves retry-after`() {
        val only = StubBinding(
            id = "only",
            result = failure(
                AiFailureKind.RATE_LIMIT,
                RetryDisposition.RETRYABLE,
                retryAfterMillis = 45_000,
            ),
        )

        val result = execute(
            bindings = listOf(binding("only")),
            ports = listOf(only),
            sameBindingRetryAllowed = false,
        )

        val deferred = assertInstanceOf(
            AiFallbackExecutionResult.Deferred::class.java,
            result,
        )
        assertEquals(45_000L, deferred.retryAfterMillis)
        assertEquals(1, only.calls)
    }

    @Test
    fun `missing preferred registry binding may fall through to next authorized binding`() {
        val second = StubBinding(
            id = "second",
            result = success("ok"),
        )

        val result = execute(
            bindings = listOf(binding("missing"), binding("second")),
            ports = listOf(second),
            sameBindingRetryAllowed = false,
        )

        val success = assertInstanceOf(
            AiFallbackExecutionResult.Success::class.java,
            result,
        )
        assertEquals(ModelBindingId("second"), success.bindingId)
        assertTrue(success.fallbackUsed)
    }

    @Test
    fun `cancellation before execution invokes no binding`() {
        val only = StubBinding(
            id = "only",
            result = success("should-not-run"),
        )
        val request = request(
            bindings = listOf(binding("only")),
            sameBindingRetryAllowed = true,
        )
        val result = DeterministicAiFallbackExecutor.execute(
            request = request,
            registry = ImmutableModelBindingRegistry(listOf(only)),
            failurePolicy = ConservativeAiRouteFailurePolicy,
            cancellationSignal = CancellationSignal { true },
        )

        val stopped = assertInstanceOf(
            AiFallbackExecutionResult.Stopped::class.java,
            result,
        )
        assertEquals(AiFailureKind.CANCELLED, stopped.failure.kind)
        assertEquals(0, only.calls)
        assertTrue(stopped.attemptedBindingIds.isEmpty())
    }

    @Test
    fun `preferred success does not mark fallback`() {
        val first = StubBinding("first", success("ok"))

        val result = execute(
            bindings = listOf(binding("first")),
            ports = listOf(first),
            sameBindingRetryAllowed = false,
        )

        val success = assertInstanceOf(
            AiFallbackExecutionResult.Success::class.java,
            result,
        )
        assertFalse(success.fallbackUsed)
    }

    private fun execute(
        bindings: List<ModelProfileBinding>,
        ports: List<ModelBindingReasoningPort>,
        sameBindingRetryAllowed: Boolean,
    ): AiFallbackExecutionResult =
        DeterministicAiFallbackExecutor.execute(
            request = request(bindings, sameBindingRetryAllowed),
            registry = ImmutableModelBindingRegistry(ports),
            failurePolicy = ConservativeAiRouteFailurePolicy,
            cancellationSignal = CancellationSignal { false },
        )

    private fun request(
        bindings: List<ModelProfileBinding>,
        sameBindingRetryAllowed: Boolean,
    ): AiFallbackExecutionRequest =
        AiFallbackExecutionRequest(
            routePlan = AiBudgetedRoutePlan(
                authorizedBindings = bindings,
                budgetRejections = emptyList(),
            ),
            invocation = modelBindingInvocation(),
            sameBindingRetryAllowed = sameBindingRetryAllowed,
        )

    private fun reasoningRequest(): ReasoningRequest =
        ReasoningRequest(
            requestId = ModelRequestId("request-1"),
            profileId = ModelProfileId("conversation"),
            requirements = ModelRequirements(
                requiredCapabilities = setOf(ModelCapability.TEXT),
            ),
            input = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = "hello".toByteArray(),
            ),
        )

    private fun modelBindingInvocation(): ModelBindingInvocation {
        val request = reasoningRequest()
        val promptRef = PromptRef(
            promptId = PromptId("conversation-core"),
            version = PromptVersion(1),
        )
        val prompt = PromptArtifact(
            ref = promptRef,
            content = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = "system".toByteArray(),
            ),
        )
        return ModelBindingInvocation(
            request = request,
            promptRef = prompt.ref,
            promptFingerprint = prompt.fingerprint,
            contextPack = ContextPack(
                fragments = listOf(
                    ContextFragment(
                        id = ContextFragmentId("prompt"),
                        contextClass = ContextClass.SYSTEM_PROMPT,
                        payload = prompt.content,
                        estimatedTokens = 1,
                        priority = 2,
                        required = true,
                        egressSensitivity = EgressSensitivity.PUBLIC,
                    ),
                    ContextFragment(
                        id = ContextFragmentId("request"),
                        contextClass = ContextClass.CURRENT_REQUEST,
                        payload = request.input,
                        estimatedTokens = 1,
                        priority = 1,
                        required = true,
                        egressSensitivity = EgressSensitivity.PRIVATE,
                    ),
                ),
                omittedFragmentIds = emptyList(),
            ),
        )
    }

    private fun binding(id: String): ModelProfileBinding =
        ModelProfileBinding(
            bindingId = ModelBindingId(id),
            target = DataEgressTarget.REMOTE_MODEL,
        )

    private fun success(text: String): ModelBindingInvocationResult =
        ModelBindingInvocationResult.Success(
            output = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = text.toByteArray(),
            ),
            usage = AiUsage(
                inputTokens = 1,
                outputTokens = 1,
            ),
        )

    private fun failure(
        kind: AiFailureKind,
        disposition: RetryDisposition,
        retryAfterMillis: Long? = null,
    ): ModelBindingInvocationResult =
        ModelBindingInvocationResult.Failure(
            AiFailure(
                kind = kind,
                retryDisposition = disposition,
                retryAfterMillis = retryAfterMillis,
            )
        )

    private class StubBinding(
        id: String,
        private val result: ModelBindingInvocationResult,
    ) : ModelBindingReasoningPort {
        override val bindingId = ModelBindingId(id)
        var calls: Int = 0

        override fun reason(
            invocation: ModelBindingInvocation,
            cancellationSignal: CancellationSignal,
        ): ModelBindingInvocationResult {
            calls += 1
            return result
        }
    }
}
