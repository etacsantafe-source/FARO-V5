package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.kernel.contracts.AiFailureKind
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
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroqTextModelBindingTest {
    @Test
    fun `text invocation maps frozen prompt context and current request without provider tools`() {
        var captured: GroqChatCompletionRequest? = null
        val binding =
            binding { request, _ ->
                captured = request
                GroqChatTransportResult.Success(
                    responseText = "respuesta",
                    inputTokens = 12,
                    outputTokens = 4,
                )
            }

        val result = binding.reason(invocation(), CancellationSignal { false })

        val success =
            assertInstanceOf(
                ModelBindingInvocationResult.Success::class.java,
                result,
            )
        assertEquals("respuesta", text(success.output))
        assertEquals(12L, success.usage.inputTokens)
        assertEquals(4L, success.usage.outputTokens)

        val request = checkNotNull(captured)
        assertEquals("model-from-binding-config", request.modelId)
        assertEquals(
            listOf(
                GroqChatMessage(GroqChatRole.SYSTEM, "frozen system"),
                GroqChatMessage(
                    GroqChatRole.USER,
                    "FARO_CONTEXT_DATA[EVIDENCE]\nuntrusted evidence",
                ),
                GroqChatMessage(GroqChatRole.USER, "current request"),
            ),
            request.messages,
        )
        assertEquals(1, request.messages.count { it.role == GroqChatRole.SYSTEM })
    }

    @Test
    fun `unsupported provider capability fails before transport`() {
        var called = false
        val binding =
            binding { _, _ ->
                called = true
                GroqChatTransportResult.Success("unexpected")
            }

        val result =
            binding.reason(
                invocation(
                    capabilities =
                        setOf(
                            ModelCapability.TEXT,
                            ModelCapability.TOOL_CALLING,
                        )
                ),
                CancellationSignal { false },
            )

        assertFalse(called)
        assertFailure(
            result,
            AiFailureKind.UNSUPPORTED_CAPABILITY,
            RetryDisposition.NOT_RETRYABLE,
        )
    }

    @Test
    fun `429 becomes rate limit and preserves positive retry after`() {
        val result =
            binding { _, _ ->
                GroqChatTransportResult.HttpFailure(
                    statusCode = 429,
                    retryAfterMillis = 2_000,
                )
            }.reason(invocation(), CancellationSignal { false })

        val failure = assertFailure(
            result,
            AiFailureKind.RATE_LIMIT,
            RetryDisposition.RETRYABLE,
        )
        assertEquals(2_000L, failure.failure.retryAfterMillis)
    }

    @Test
    fun `498 and 503 are provider capacity rather than blind same binding success`() {
        listOf(498, 503).forEach { status ->
            val result =
                binding { _, _ ->
                    GroqChatTransportResult.HttpFailure(status)
                }.reason(invocation(), CancellationSignal { false })

            assertFailure(
                result,
                AiFailureKind.PROVIDER_CAPACITY,
                RetryDisposition.RETRYABLE,
            )
        }
    }

    @Test
    fun `413 requests deterministic reprepare instead of truncating in provider adapter`() {
        val result =
            binding { _, _ ->
                GroqChatTransportResult.HttpFailure(413)
            }.reason(invocation(), CancellationSignal { false })

        assertFailure(
            result,
            AiFailureKind.CONTEXT_TOO_LARGE,
            RetryDisposition.REQUIRES_REPREPARE,
        )
    }

    @Test
    fun `authentication failures remain typed and non retryable`() {
        listOf(401, 403).forEach { status ->
            val result =
                binding { _, _ ->
                    GroqChatTransportResult.HttpFailure(status)
                }.reason(invocation(), CancellationSignal { false })

            assertFailure(
                result,
                AiFailureKind.AUTH_REJECTED,
                RetryDisposition.NOT_RETRYABLE,
            )
        }
    }

    @Test
    fun `missing credential is explicit human action without network semantics leaking to kernel`() {
        val result =
            binding { _, _ ->
                GroqChatTransportResult.TransportFailure(
                    GroqTransportFailureKind.AUTH_MISSING
                )
            }.reason(invocation(), CancellationSignal { false })

        assertFailure(
            result,
            AiFailureKind.AUTH_MISSING,
            RetryDisposition.REQUIRES_HUMAN_ACTION,
        )
    }

    @Test
    fun `request and response transport bounds map without provider detail leakage`() {
        val tooLarge =
            binding { _, _ ->
                GroqChatTransportResult.TransportFailure(
                    GroqTransportFailureKind.REQUEST_TOO_LARGE
                )
            }.reason(invocation(), CancellationSignal { false })
        assertFailure(
            tooLarge,
            AiFailureKind.CONTEXT_TOO_LARGE,
            RetryDisposition.REQUIRES_REPREPARE,
        )

        val invalid =
            binding { _, _ ->
                GroqChatTransportResult.TransportFailure(
                    GroqTransportFailureKind.INVALID_RESPONSE
                )
            }.reason(invocation(), CancellationSignal { false })
        assertFailure(
            invalid,
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
            RetryDisposition.NOT_RETRYABLE,
        )
    }

    @Test
    fun `non text context fails closed before transport`() {
        var called = false
        val binding =
            binding { _, _ ->
                called = true
                GroqChatTransportResult.Success("unexpected")
            }
        val invocation =
            invocation(
                evidencePayload =
                    CanonicalPayload.fromEncoded(
                        formatId = "application/json",
                        bytes = "{}".toByteArray(),
                    )
            )

        val result = binding.reason(invocation, CancellationSignal { false })

        assertFalse(called)
        assertFailure(
            result,
            AiFailureKind.TERMINAL_REQUEST_ERROR,
            RetryDisposition.NOT_RETRYABLE,
        )
    }

    @Test
    fun `cancellation is checked before transport and after transport`() {
        var called = false
        val before =
            binding { _, _ ->
                called = true
                GroqChatTransportResult.Success("unexpected")
            }.reason(invocation(), CancellationSignal { true })

        assertFalse(called)
        assertFailure(
            before,
            AiFailureKind.CANCELLED,
            RetryDisposition.NOT_RETRYABLE,
        )

        var checks = 0
        val after =
            binding { _, _ ->
                GroqChatTransportResult.Success("late")
            }.reason(
                invocation(),
                CancellationSignal {
                    checks += 1
                    checks >= 2
                },
            )

        assertFailure(
            after,
            AiFailureKind.CANCELLED,
            RetryDisposition.NOT_RETRYABLE,
        )
    }

    @Test
    fun `blank provider success is rejected rather than invented into an answer`() {
        val result =
            binding { _, _ ->
                GroqChatTransportResult.Success("   ")
            }.reason(invocation(), CancellationSignal { false })

        assertFailure(
            result,
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
            RetryDisposition.NOT_RETRYABLE,
        )
    }

    private fun binding(
        transport: GroqChatTransport,
    ): GroqTextModelBinding =
        GroqTextModelBinding(
            config =
                GroqTextBindingConfig(
                    bindingId = ModelBindingId("groq-text"),
                    modelId = "model-from-binding-config",
                ),
            transport = transport,
        )

    private fun invocation(
        capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
        evidencePayload: CanonicalPayload = payload("untrusted evidence"),
    ): ModelBindingInvocation {
        val promptRef =
            PromptRef(
                promptId = PromptId("conversation-core"),
                version = PromptVersion(3),
            )
        val prompt =
            PromptArtifact(
                ref = promptRef,
                content = payload("frozen system"),
            )
        val request =
            ReasoningRequest(
                requestId = ModelRequestId("request-1"),
                profileId = ModelProfileId("conversation"),
                requirements =
                    ModelRequirements(
                        requiredCapabilities = capabilities,
                    ),
                input = payload("current request"),
            )

        return ModelBindingInvocation(
            request = request,
            promptRef = prompt.ref,
            promptFingerprint = prompt.fingerprint,
            contextPack =
                ContextPack(
                    fragments =
                        listOf(
                            ContextFragment(
                                id = ContextFragmentId("system"),
                                contextClass = ContextClass.SYSTEM_PROMPT,
                                payload = prompt.content,
                                estimatedTokens = 3,
                                priority = 100,
                                required = true,
                                egressSensitivity = EgressSensitivity.PUBLIC,
                            ),
                            ContextFragment(
                                id = ContextFragmentId("evidence"),
                                contextClass = ContextClass.EVIDENCE,
                                payload = evidencePayload,
                                estimatedTokens = 3,
                                priority = 10,
                                required = false,
                                egressSensitivity = EgressSensitivity.PUBLIC,
                            ),
                            ContextFragment(
                                id = ContextFragmentId("request"),
                                contextClass = ContextClass.CURRENT_REQUEST,
                                payload = request.input,
                                estimatedTokens = 3,
                                priority = 90,
                                required = true,
                                egressSensitivity = EgressSensitivity.PRIVATE,
                            ),
                        ),
                    omittedFragmentIds = emptyList(),
                ),
        )
    }

    private fun payload(value: String): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = "text/plain",
            bytes = value.toByteArray(StandardCharsets.UTF_8),
        )

    private fun text(payload: CanonicalPayload): String =
        String(payload.copyBytes(), StandardCharsets.UTF_8)

    private fun assertFailure(
        result: ModelBindingInvocationResult,
        kind: AiFailureKind,
        disposition: RetryDisposition,
    ): ModelBindingInvocationResult.Failure {
        val failure =
            assertInstanceOf(
                ModelBindingInvocationResult.Failure::class.java,
                result,
            )
        assertEquals(kind, failure.failure.kind)
        assertEquals(disposition, failure.failure.retryDisposition)
        return failure
    }
}
