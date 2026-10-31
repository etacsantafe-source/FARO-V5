package ar.com.faro.v5.platform.web

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.control.spi.SecretValue
import ar.com.faro.v5.kernel.contracts.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenAiCompatibleModelBindingTest {
    @Test
    fun `real compatible transport sends frozen prompt and request, reads secret, decodes answer`() {
        val seenUrl = AtomicReference<String>()
        val seenAuth = AtomicReference<String>()
        val seenBody = AtomicReference<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            seenUrl.set(chain.request().url.toString())
            seenAuth.set(chain.request().header("Authorization"))
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            seenBody.set(buffer.readUtf8())
            reply(chain.request(), 200,
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Hola FARO"}}],"usage":{"prompt_tokens":12,"completion_tokens":4}}""")
        }.build()
        val result = binding(client).reason(invocation(), NeverCancelled)
        val success = assertInstanceOf(ModelBindingInvocationResult.Success::class.java, result)
        assertEquals("Hola FARO", success.output.copyBytes().decodeToString())
        assertEquals(12L, success.usage.inputTokens)
        assertEquals(4L, success.usage.outputTokens)
        assertEquals(ApprovedChatEndpoint.GROQ.url, seenUrl.get())
        assertEquals("Bearer test-credential-123", seenAuth.get())
        assertTrue(seenBody.get().contains("\"model\":\"test-model\""))
        assertTrue(seenBody.get().contains("\"role\":\"system\""))
        assertTrue(seenBody.get().contains("\"role\":\"user\""))
        assertFalse(result.toString().contains("test-credential-123"))
    }

    @Test
    fun `GPT OSS Groq request excludes reasoning and uses low effort`() {
        val seenBody = AtomicReference<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            seenBody.set(buffer.readUtf8())
            reply(chain.request(), 200,
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Listo"}}]}""")
        }.build()
        val binding = OpenAiCompatibleModelBinding(
            client, ModelBindingId("groq-test"), ApprovedChatEndpoint.GROQ,
            "openai/gpt-oss-120b", SecretReader {
                SecretValue.copyOf("test-credential-123".encodeToByteArray())
            }, SecretId("faro-test-secret"), 2048,
        )
        assertInstanceOf(ModelBindingInvocationResult.Success::class.java,
            binding.reason(invocation(), NeverCancelled))
        assertTrue(seenBody.get().contains("\\"include_reasoning\\":false"))
        assertTrue(seenBody.get().contains("\\"reasoning_effort\\":\\"low\\""))
        assertTrue(seenBody.get().contains("\\"max_tokens\\":2048"))
    }

    @Test
    fun `truncated completion cannot be committed as a complete assistant reply`() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            reply(chain.request(), 200,
                """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"partial"}}]}""")
        }.build()
        assertEquals(
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
            failureKind(binding(client).reason(invocation(), NeverCancelled)),
        )
    }

    @Test
    fun `missing credential blocks before network`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            reply(chain.request(), 200, "{}")
        }.build()
        val result = binding(client, SecretReader { null }).reason(invocation(), NeverCancelled)
        assertEquals(AiFailureKind.AUTH_MISSING, failureKind(result))
        assertEquals(0, calls.get())
    }

    @Test
    fun `revocation during Keystore read prevents a provider request`() {
        val allowed = AtomicBoolean(true)
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            reply(chain.request(), 200, "{}")
        }.build()
        val binding = OpenAiCompatibleModelBinding(
            client, ModelBindingId("groq-test"), ApprovedChatEndpoint.GROQ,
            "test-model", SecretReader {
                allowed.set(false)
                SecretValue.copyOf("test-credential-123".encodeToByteArray())
            }, SecretId("faro-test-secret"), 512,
            authorizationStillValid = { allowed.get() },
        )
        assertEquals(
            AiFailureKind.POLICY_EGRESS_BLOCKED,
            failureKind(binding.reason(invocation(), NeverCancelled)),
        )
        assertEquals(0, calls.get())
    }

    @Test
    fun `unclassified fragment blocks before secret and network`() {
        val calls = AtomicInteger()
        val reads = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            reply(chain.request(), 200, "{}")
        }.build()
        val result = binding(client, SecretReader {
            reads.incrementAndGet()
            SecretValue.copyOf("test-credential-123".encodeToByteArray())
        }).reason(invocation(EgressSensitivity.UNCLASSIFIED), NeverCancelled)
        assertEquals(AiFailureKind.POLICY_EGRESS_BLOCKED, failureKind(result))
        assertEquals(0, calls.get())
        assertEquals(0, reads.get())
    }

    @Test
    fun `rate limit maps typed retry after without repeating request`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            reply(chain.request(), 429, "{}", "3")
        }.build()
        val result = binding(client).reason(invocation(), NeverCancelled)
        val failure = assertInstanceOf(ModelBindingInvocationResult.Failure::class.java, result)
        assertEquals(AiFailureKind.RATE_LIMIT, failure.failure.kind)
        assertEquals(3000L, failure.failure.retryAfterMillis)
        assertEquals(1, calls.get())
    }

    @Test
    fun `malformed response fails closed`() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            reply(chain.request(), 200, """{"choices":[{"message":{"content":null}}]}""")
        }.build()
        assertEquals(
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
            failureKind(binding(client).reason(invocation(), NeverCancelled)),
        )
    }

    @Test
    fun `cancelled request reads no credential and makes no call`() {
        val reads = AtomicInteger()
        val client = OkHttpClient()
        val result = binding(client, SecretReader {
            reads.incrementAndGet()
            null
        }).reason(invocation(), CancellationSignal { true })
        assertEquals(AiFailureKind.CANCELLED, failureKind(result))
        assertEquals(0, reads.get())
    }

    private fun binding(
        client: OkHttpClient,
        secrets: SecretReader = SecretReader {
            SecretValue.copyOf("test-credential-123".encodeToByteArray())
        },
    ) = OpenAiCompatibleModelBinding(
        client, ModelBindingId("groq-test"), ApprovedChatEndpoint.GROQ,
        "test-model", secrets, SecretId("faro-test-secret"), 512,
    )

    private fun invocation(sensitivity: EgressSensitivity = EgressSensitivity.PUBLIC): ModelBindingInvocation {
        val prompt = CanonicalPayload.fromEncoded("text/plain", "You are FARO".encodeToByteArray())
        val input = CanonicalPayload.fromEncoded("text/plain", "Hola".encodeToByteArray())
        val promptRef = PromptRef(PromptId("faro-system"), PromptVersion(1))
        val artifact = PromptArtifact(promptRef, prompt)
        val pack = ContextPack(
            listOf(
                ContextFragment(
                    ContextFragmentId("system"), ContextClass.SYSTEM_PROMPT, prompt,
                    5, required = true, egressSensitivity = EgressSensitivity.PUBLIC,
                ),
                ContextFragment(
                    ContextFragmentId("current"), ContextClass.CURRENT_REQUEST, input,
                    2, required = true, egressSensitivity = sensitivity,
                ),
            ),
            emptyList(),
        )
        return ModelBindingInvocation(
            ReasoningRequest(ModelRequestId("req-test"), ModelProfileId("profile-test"),
                ModelRequirements(setOf(ModelCapability.TEXT)), input),
            promptRef, artifact.fingerprint, pack,
        )
    }

    private fun failureKind(result: ModelBindingInvocationResult): AiFailureKind =
        assertInstanceOf(ModelBindingInvocationResult.Failure::class.java, result).failure.kind

    private fun reply(
        request: Request,
        code: Int,
        body: String,
        retryAfter: String? = null,
    ): Response {
        val builder = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code)
            .message("test").header("Content-Type", "application/json")
            .body(body.toResponseBody())
        if (retryAfter != null) builder.header("Retry-After", retryAfter)
        return builder.build()
    }
}
