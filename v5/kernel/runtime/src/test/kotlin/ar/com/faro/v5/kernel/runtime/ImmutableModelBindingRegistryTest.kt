package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocation
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocationResult
import ar.com.faro.v5.kernel.contracts.ModelBindingReasoningPort
import ar.com.faro.v5.kernel.contracts.ModelCapability
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImmutableModelBindingRegistryTest {
    @Test
    fun `registry resolves exact opaque binding and exposes deterministic ids`() {
        val z = StubBinding(ModelBindingId("z-binding"))
        val a = StubBinding(ModelBindingId("a-binding"))
        val registry = ImmutableModelBindingRegistry(listOf(z, a))

        assertEquals(a, registry.resolve(ModelBindingId("a-binding")))
        assertNull(registry.resolve(ModelBindingId("missing")))
        assertEquals(
            listOf(ModelBindingId("a-binding"), ModelBindingId("z-binding")),
            registry.bindingIds(),
        )
    }

    @Test
    fun `duplicate binding ids fail closed`() {
        val first = StubBinding(ModelBindingId("same"))
        val second = StubBinding(ModelBindingId("same"))

        assertThrows(IllegalArgumentException::class.java) {
            ImmutableModelBindingRegistry(listOf(first, second))
        }
    }

    @Test
    fun `binding invocation remains typed and receives cancellation signal`() {
        val binding = StubBinding(ModelBindingId("remote-fast"))
        val cancelled = CancellationSignal { true }

        val result = binding.reason(invocation(), cancelled)

        assertTrue(binding.sawCancellation)
        assertEquals(
            ModelBindingInvocationResult.Failure(
                AiFailure(
                    kind = AiFailureKind.CANCELLED,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )
            ),
            result,
        )
    }

    private fun invocation(): ModelBindingInvocation {
        val request = ReasoningRequest(
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

    private class StubBinding(
        override val bindingId: ModelBindingId,
    ) : ModelBindingReasoningPort {
        var sawCancellation: Boolean = false

        override fun reason(
            invocation: ModelBindingInvocation,
            cancellationSignal: CancellationSignal,
        ): ModelBindingInvocationResult {
            sawCancellation = cancellationSignal.isCancelled()
            if (sawCancellation) {
                return ModelBindingInvocationResult.Failure(
                    AiFailure(
                        kind = AiFailureKind.CANCELLED,
                        retryDisposition = RetryDisposition.NOT_RETRYABLE,
                    )
                )
            }
            return ModelBindingInvocationResult.Success(
                output = invocation.request.input,
                usage = AiUsage(
                    inputTokens = 1,
                    outputTokens = 1,
                ),
            )
        }
    }
}
