package ar.com.faro.v5.platform.web

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.kernel.contracts.*
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Fixed HTTPS provider endpoints. No arbitrary URL, redirect, port or user-controlled host.
 * These are OpenAI-compatible chat completions; provider choice remains a trusted binding.
 */
enum class ApprovedChatEndpoint(val url: String) {
    GROQ("https://api.groq.com/openai/v1/chat/completions"),
    OPENAI("https://api.openai.com/v1/chat/completions"),
    GEMINI_COMPAT("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),
}

/**
 * F1 real transport implementing the neutral ModelBindingReasoningPort.
 * ComposedModelGateway MUST authorize route, budget, capabilities and egress before invoking.
 * Device-local credentials are read through the least-authority SecretReader, never embedded.
 * V2 remains dormant: this adapter does not install itself into an active Android graph.
 */
class OpenAiCompatibleModelBinding internal constructor(
    private val calls: Call.Factory,
    override val bindingId: ModelBindingId,
    private val endpoint: ApprovedChatEndpoint,
    private val model: String,
    private val secrets: SecretReader,
    private val credentialId: SecretId,
    private val maxOutputTokens: Int,
) : ModelBindingReasoningPort {
    constructor(
        bindingId: ModelBindingId,
        endpoint: ApprovedChatEndpoint,
        model: String,
        secrets: SecretReader,
        credentialId: SecretId,
        ioPolicy: IoExecutionPolicy,
        maxOutputTokens: Int = 512,
    ) : this(
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(ioPolicy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(ioPolicy.totalTimeoutMillis, TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build(),
        bindingId, endpoint, model, secrets, credentialId, maxOutputTokens,
    )

    init {
        require(model.matches(Regex("[A-Za-z0-9._:/-]{1,128}")))
        require(maxOutputTokens in 1..4096)
    }

    override fun reason(
        invocation: ModelBindingInvocation,
        cancellationSignal: CancellationSignal,
    ): ModelBindingInvocationResult {
        if (cancellationSignal.isCancelled()) return fail(AiFailureKind.CANCELLED)
        val fragments = invocation.contextPack.fragments
        // Defense in depth: UNKNOWN, SECRET and RESTRICTED never leave this remote adapter.
        // PRIVATE requires explicit prior approval from the gateway's trusted egress policy.
        if (fragments.any { it.egressSensitivity in FORBIDDEN })
            return fail(AiFailureKind.POLICY_EGRESS_BLOCKED)

        val ordered = fragments.filter { it.contextClass == ContextClass.SYSTEM_PROMPT } +
            fragments.filter {
                it.contextClass != ContextClass.SYSTEM_PROMPT &&
                    it.contextClass != ContextClass.CURRENT_REQUEST
            } + fragments.filter { it.contextClass == ContextClass.CURRENT_REQUEST }
        val entries = mutableListOf<Pair<String, String>>()
        for (fragment in ordered) {
            if (fragment.payload.formatId != "text/plain")
                return fail(AiFailureKind.UNSUPPORTED_CAPABILITY)
            val bytes = fragment.payload.copyBytes()
            val text = try { strictUtf8(bytes) } finally { bytes.fill(0) }
                ?: return fail(AiFailureKind.TERMINAL_REQUEST_ERROR)
            entries += (if (fragment.contextClass == ContextClass.SYSTEM_PROMPT) "system" else "user") to text
        }
        val messages = buildJsonArray {
            for ((role, content) in entries) add(buildJsonObject {
                put("role", JsonPrimitive(role))
                put("content", JsonPrimitive(content))
            })
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("stream", JsonPrimitive(false))
            put("max_tokens", JsonPrimitive(maxOutputTokens))
            put("messages", messages)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        if (body.size > MAX_REQUEST_BYTES) {
            body.fill(0)
            return fail(AiFailureKind.CONTEXT_TOO_LARGE)
        }
        val secret = try { secrets.readValueOrNull(credentialId) }
            catch (_: Exception) { null }
        if (secret == null) {
            body.fill(0)
            return fail(AiFailureKind.AUTH_MISSING)
        }
        val secretBytes = secret.copyBytes()
        val token = try {
            strictUtf8(secretBytes)?.takeIf {
                it.matches(Regex("[A-Za-z0-9._-]{8,256}"))
            }
        } finally { secretBytes.fill(0) }
        if (token == null) {
            body.fill(0)
            return fail(AiFailureKind.AUTH_MISSING)
        }
        return try {
            if (cancellationSignal.isCancelled()) return fail(AiFailureKind.CANCELLED)
            val request = Request.Builder().url(endpoint.url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_TYPE)).build()
            val call = calls.newCall(request)
            when (val result = await(call, cancellationSignal)) {
                is CallResult.Complete -> result.response.use { response ->
                    if (cancellationSignal.isCancelled()) return@use fail(AiFailureKind.CANCELLED)
                    if (!response.isSuccessful) return@use httpFailure(response)
                    if (response.header("Content-Type")?.substringBefore(';')
                            ?.trim()?.lowercase() != "application/json")
                        return@use fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
                    val bytes = boundedResponse(response)
                        ?: return@use fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
                    try { decode(bytes) } finally { bytes.fill(0) }
                }
                is CallResult.Error -> ioFailure(result.error)
                CallResult.Cancelled -> fail(AiFailureKind.CANCELLED)
            }
        } catch (_: RuntimeException) {
            fail(AiFailureKind.CONNECT, RetryDisposition.RETRYABLE)
        } finally { body.fill(0) }
    }

    private fun decode(bytes: ByteArray): ModelBindingInvocationResult {
        val text = strictUtf8(bytes)
            ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
        return try {
            val root = Json.parseToJsonElement(text) as? JsonObject
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val choices = root["choices"] as? JsonArray
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val choice = choices.singleOrNull() as? JsonObject
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            // A truncated or tool-call answer must not become a durable COMPLETED reply.
            val finish = (choice["finish_reason"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.contentOrNull
            if (finish != "stop") return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val message = choice["message"] as? JsonObject
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val role = (message["role"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.contentOrNull
            if (role != "assistant") return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val content = message["content"] as? JsonPrimitive
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val answer = content.takeIf { it.isString }?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.length <= MAX_ANSWER_CHARS }
                ?: return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            val usage = root["usage"] as? JsonObject
            val input = usage?.get("prompt_tokens")?.let {
                (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.longOrNull
            }
            val output = usage?.get("completion_tokens")?.let {
                (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.longOrNull
            }
            if ((input != null && input < 0L) || (output != null && output < 0L))
                return fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE)
            ModelBindingInvocationResult.Success(
                CanonicalPayload.fromEncoded("text/plain", answer.toByteArray(StandardCharsets.UTF_8)),
                AiUsage(inputTokens = input, outputTokens = output),
            )
        } catch (_: Exception) { fail(AiFailureKind.INVALID_STRUCTURED_RESPONSE) }
    }

    private fun boundedResponse(response: Response): ByteArray? {
        if (response.body.contentLength() > MAX_RESPONSE_BYTES) return null
        return try {
            response.body.byteStream().use { stream ->
                val buffer = ByteArray(MAX_RESPONSE_BYTES + 1)
                try {
                    var total = 0
                    while (total < buffer.size) {
                        val n = stream.read(buffer, total, buffer.size - total)
                        if (n < 0) break
                        if (n > 0) total += n
                    }
                    if (total > MAX_RESPONSE_BYTES) null else buffer.copyOf(total)
                } finally { buffer.fill(0) }
            }
        } catch (_: IOException) { null }
    }

    private fun httpFailure(response: Response): ModelBindingInvocationResult {
        val after = response.header("Retry-After")?.toLongOrNull()
            ?.takeIf { it in 1L..300L }?.times(1000L)
        return when (response.code) {
            401, 403 -> fail(AiFailureKind.AUTH_REJECTED)
            429 -> fail(AiFailureKind.RATE_LIMIT, RetryDisposition.RETRYABLE, after)
            408, 504 -> fail(AiFailureKind.TIMEOUT, RetryDisposition.RETRYABLE)
            in 500..599 -> fail(AiFailureKind.TRANSIENT_SERVER, RetryDisposition.RETRYABLE)
            else -> fail(AiFailureKind.TERMINAL_REQUEST_ERROR)
        }
    }

    private fun ioFailure(error: IOException): ModelBindingInvocationResult =
        when (error) {
            is UnknownHostException -> fail(AiFailureKind.DNS, RetryDisposition.RETRYABLE)
            is SocketTimeoutException, is InterruptedIOException ->
                fail(AiFailureKind.TIMEOUT, RetryDisposition.RETRYABLE)
            is ConnectException -> fail(AiFailureKind.CONNECT, RetryDisposition.RETRYABLE)
            else -> fail(AiFailureKind.CONNECT, RetryDisposition.RETRYABLE)
        }

    private fun await(call: Call, cancellation: CancellationSignal): CallResult {
        val outcome = AtomicReference<CallResult?>()
        val done = CountDownLatch(1)
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (outcome.compareAndSet(null, CallResult.Error(e))) done.countDown()
                }
                override fun onResponse(call: Call, response: Response) {
                    if (outcome.compareAndSet(null, CallResult.Complete(response))) done.countDown()
                    else response.close()
                }
            })
        } catch (error: RuntimeException) {
            return CallResult.Error(IOException("provider enqueue failed", error))
        }
        while (true) {
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) {
                val prior = outcome.getAndSet(CallResult.Cancelled)
                call.cancel()
                if (prior is CallResult.Complete) prior.response.close()
                return CallResult.Cancelled
            }
            try {
                if (done.await(100L, TimeUnit.MILLISECONDS))
                    return outcome.get() ?: CallResult.Error(IOException("missing result"))
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    private fun strictUtf8(bytes: ByteArray): String? =
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { null }

    private fun fail(
        kind: AiFailureKind,
        retry: RetryDisposition = RetryDisposition.NOT_RETRYABLE,
        after: Long? = null,
    ): ModelBindingInvocationResult.Failure =
        ModelBindingInvocationResult.Failure(AiFailure(kind, retry, after))

    private sealed interface CallResult {
        data class Complete(val response: Response) : CallResult
        data class Error(val error: IOException) : CallResult
        data object Cancelled : CallResult
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FORBIDDEN = setOf(
            EgressSensitivity.SECRET, EgressSensitivity.UNCLASSIFIED, EgressSensitivity.RESTRICTED,
        )
        private const val MAX_REQUEST_BYTES = 64 * 1024
        private const val MAX_RESPONSE_BYTES = 128 * 1024
        private const val MAX_ANSWER_CHARS = 32 * 1024
    }
}
