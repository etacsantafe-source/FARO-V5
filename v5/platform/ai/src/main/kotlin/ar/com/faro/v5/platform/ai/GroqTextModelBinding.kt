package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocation
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocationResult
import ar.com.faro.v5.kernel.contracts.ModelBindingReasoningPort
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.RetryDisposition
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class GroqChatRole {
    SYSTEM,
    USER,
}

data class GroqChatMessage(
    val role: GroqChatRole,
    val content: String,
) {
    init {
        require(content.isNotBlank()) {
            "Groq chat message content must not be blank"
        }
    }

    override fun toString(): String =
        "GroqChatMessage(role=$role, content=[REDACTED])"
}

/**
 * Provider-specific protocol object.
 *
 * Deliberately text-only: there are no provider Web, browser, code, MCP or tool fields
 * in this first Groq adapter slice.
 */
data class GroqChatCompletionRequest(
    val modelId: String,
    val messages: List<GroqChatMessage>,
) {
    init {
        require(modelId.isNotBlank() && modelId.length <= 256) {
            "Groq model id must be non-blank and bounded"
        }
        require(messages.isNotEmpty()) {
            "Groq chat request must contain messages"
        }
        require(messages.first().role == GroqChatRole.SYSTEM) {
            "Groq chat request must begin with the frozen system prompt"
        }
        require(messages.count { it.role == GroqChatRole.SYSTEM } == 1) {
            "Groq chat request must contain exactly one system message"
        }
        require(messages.last().role == GroqChatRole.USER) {
            "Groq chat request must end with the exact current request"
        }
    }

    override fun toString(): String =
        "GroqChatCompletionRequest(modelId=$modelId, messages=${messages.size})"
}

enum class GroqTransportFailureKind {
    OFFLINE,
    DNS,
    CONNECT,
    TIMEOUT,
    AUTH_MISSING,
    REQUEST_TOO_LARGE,
    INVALID_RESPONSE,
    CANCELLED,
}

sealed interface GroqChatTransportResult {
    data class Success(
        val responseText: String,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
    ) : GroqChatTransportResult {
        init {
            require(inputTokens == null || inputTokens >= 0L) {
                "Groq input usage must be non-negative"
            }
            require(outputTokens == null || outputTokens >= 0L) {
                "Groq output usage must be non-negative"
            }
        }

        override fun toString(): String =
            "Success(responseText=[REDACTED], inputTokens=$inputTokens, outputTokens=$outputTokens)"
    }

    data class HttpFailure(
        val statusCode: Int,
        val retryAfterMillis: Long? = null,
    ) : GroqChatTransportResult {
        init {
            require(statusCode in 100..599) {
                "HTTP status must be valid"
            }
            require(retryAfterMillis == null || retryAfterMillis > 0L) {
                "retry-after must be positive when present"
            }
        }
    }

    data class TransportFailure(
        val kind: GroqTransportFailureKind,
    ) : GroqChatTransportResult
}

fun interface GroqChatTransport {
    fun complete(
        request: GroqChatCompletionRequest,
        cancellationSignal: CancellationSignal,
    ): GroqChatTransportResult
}

data class GroqTextBindingConfig(
    val bindingId: ModelBindingId,
    val modelId: String,
) {
    init {
        require(modelId.isNotBlank() && modelId.length <= 256) {
            "Groq model id must be non-blank and bounded"
        }
    }
}

/**
 * First FARO V5 Groq binding.
 *
 * This adapter intentionally supports TEXT only. Capability routing remains canonical
 * in the kernel and any request requiring tools, structured output, multimodal input,
 * streaming or provider-specific reasoning controls fails closed before transport.
 */
class GroqTextModelBinding(
    private val config: GroqTextBindingConfig,
    private val transport: GroqChatTransport,
) : ModelBindingReasoningPort {
    override val bindingId: ModelBindingId
        get() = config.bindingId

    override fun reason(
        invocation: ModelBindingInvocation,
        cancellationSignal: CancellationSignal,
    ): ModelBindingInvocationResult {
        if (cancellationSignal.isCancelled()) {
            return failure(
                kind = AiFailureKind.CANCELLED,
                retryDisposition = RetryDisposition.NOT_RETRYABLE,
            )
        }

        if (
            invocation.request.requirements.requiredCapabilities.any {
                it != ModelCapability.TEXT
            }
        ) {
            return failure(
                kind = AiFailureKind.UNSUPPORTED_CAPABILITY,
                retryDisposition = RetryDisposition.NOT_RETRYABLE,
            )
        }

        val request =
            buildRequest(invocation)
                ?: return failure(
                    kind = AiFailureKind.TERMINAL_REQUEST_ERROR,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )

        val transportResult =
            try {
                transport.complete(
                    request = request,
                    cancellationSignal = cancellationSignal,
                )
            } catch (_: RuntimeException) {
                return failure(
                    kind = AiFailureKind.TERMINAL_REQUEST_ERROR,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )
            }

        if (cancellationSignal.isCancelled()) {
            return failure(
                kind = AiFailureKind.CANCELLED,
                retryDisposition = RetryDisposition.NOT_RETRYABLE,
            )
        }

        return when (transportResult) {
            is GroqChatTransportResult.Success ->
                success(transportResult)

            is GroqChatTransportResult.HttpFailure ->
                mapHttpFailure(transportResult)

            is GroqChatTransportResult.TransportFailure ->
                mapTransportFailure(transportResult.kind)
        }
    }

    private fun buildRequest(
        invocation: ModelBindingInvocation,
    ): GroqChatCompletionRequest? {
        val prompt =
            invocation.contextPack.fragments.single {
                it.contextClass == ContextClass.SYSTEM_PROMPT
            }
        val currentRequest =
            invocation.contextPack.fragments.single {
                it.contextClass == ContextClass.CURRENT_REQUEST
            }

        val promptText = decodeText(prompt) ?: return null
        val requestText = decodeText(currentRequest) ?: return null

        val messages = mutableListOf(
            GroqChatMessage(
                role = GroqChatRole.SYSTEM,
                content = promptText,
            )
        )

        invocation.contextPack.fragments
            .filter {
                it.contextClass != ContextClass.SYSTEM_PROMPT &&
                    it.contextClass != ContextClass.CURRENT_REQUEST
            }
            .forEach { fragment ->
                val text = decodeText(fragment) ?: return null
                messages +=
                    GroqChatMessage(
                        role = GroqChatRole.USER,
                        content =
                            "FARO_CONTEXT_DATA[" +
                                fragment.contextClass.name +
                                "]\n" +
                                text,
                    )
            }

        messages +=
            GroqChatMessage(
                role = GroqChatRole.USER,
                content = requestText,
            )

        return GroqChatCompletionRequest(
            modelId = config.modelId,
            messages = messages.toList(),
        )
    }

    private fun decodeText(fragment: ContextFragment): String? {
        if (fragment.payload.formatId != TEXT_FORMAT) {
            return null
        }

        val bytes = fragment.payload.copyBytes()
        return try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    private fun success(
        result: GroqChatTransportResult.Success,
    ): ModelBindingInvocationResult {
        if (result.responseText.isBlank()) {
            return failure(
                kind = AiFailureKind.INVALID_STRUCTURED_RESPONSE,
                retryDisposition = RetryDisposition.NOT_RETRYABLE,
            )
        }

        return ModelBindingInvocationResult.Success(
            output =
                CanonicalPayload.fromEncoded(
                    formatId = TEXT_FORMAT,
                    bytes = result.responseText.toByteArray(StandardCharsets.UTF_8),
                ),
            usage =
                AiUsage(
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                ),
        )
    }

    private fun mapHttpFailure(
        failure: GroqChatTransportResult.HttpFailure,
    ): ModelBindingInvocationResult =
        when (failure.statusCode) {
            401, 403 ->
                failure(
                    kind = AiFailureKind.AUTH_REJECTED,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )

            404 ->
                failure(
                    kind = AiFailureKind.MODEL_UNAVAILABLE,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )

            408 ->
                failure(
                    kind = AiFailureKind.TIMEOUT,
                    retryDisposition = RetryDisposition.RETRYABLE,
                )

            413 ->
                failure(
                    kind = AiFailureKind.CONTEXT_TOO_LARGE,
                    retryDisposition = RetryDisposition.REQUIRES_REPREPARE,
                )

            429 ->
                failure(
                    kind = AiFailureKind.RATE_LIMIT,
                    retryDisposition = RetryDisposition.RETRYABLE,
                    retryAfterMillis = failure.retryAfterMillis,
                )

            498, 503 ->
                failure(
                    kind = AiFailureKind.PROVIDER_CAPACITY,
                    retryDisposition = RetryDisposition.RETRYABLE,
                    retryAfterMillis = failure.retryAfterMillis,
                )

            499 ->
                failure(
                    kind = AiFailureKind.CANCELLED,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )

            in 500..599,
            424 ->
                failure(
                    kind = AiFailureKind.TRANSIENT_SERVER,
                    retryDisposition = RetryDisposition.RETRYABLE,
                    retryAfterMillis = failure.retryAfterMillis,
                )

            else ->
                failure(
                    kind = AiFailureKind.TERMINAL_REQUEST_ERROR,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )
        }

    private fun mapTransportFailure(
        kind: GroqTransportFailureKind,
    ): ModelBindingInvocationResult =
        when (kind) {
            GroqTransportFailureKind.OFFLINE ->
                failure(AiFailureKind.OFFLINE, RetryDisposition.RETRYABLE)

            GroqTransportFailureKind.DNS ->
                failure(AiFailureKind.DNS, RetryDisposition.RETRYABLE)

            GroqTransportFailureKind.CONNECT ->
                failure(AiFailureKind.CONNECT, RetryDisposition.RETRYABLE)

            GroqTransportFailureKind.TIMEOUT ->
                failure(AiFailureKind.TIMEOUT, RetryDisposition.RETRYABLE)

            GroqTransportFailureKind.AUTH_MISSING ->
                failure(
                    AiFailureKind.AUTH_MISSING,
                    RetryDisposition.REQUIRES_HUMAN_ACTION,
                )

            GroqTransportFailureKind.REQUEST_TOO_LARGE ->
                failure(
                    AiFailureKind.CONTEXT_TOO_LARGE,
                    RetryDisposition.REQUIRES_REPREPARE,
                )

            GroqTransportFailureKind.INVALID_RESPONSE ->
                failure(
                    AiFailureKind.INVALID_STRUCTURED_RESPONSE,
                    RetryDisposition.NOT_RETRYABLE,
                )

            GroqTransportFailureKind.CANCELLED ->
                failure(
                    AiFailureKind.CANCELLED,
                    RetryDisposition.NOT_RETRYABLE,
                )
        }

    private fun failure(
        kind: AiFailureKind,
        retryDisposition: RetryDisposition,
        retryAfterMillis: Long? = null,
    ): ModelBindingInvocationResult.Failure =
        ModelBindingInvocationResult.Failure(
            AiFailure(
                kind = kind,
                retryDisposition = retryDisposition,
                retryAfterMillis = retryAfterMillis,
            )
        )

    companion object {
        private const val TEXT_FORMAT = "text/plain"
    }
}
