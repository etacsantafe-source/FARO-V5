package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretStore
import ar.com.faro.v5.control.spi.SecretValue
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.NeverCancelled
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroqHttpsTransportTest {
    @Test
    fun `official HTTPS request injects credential and preserves disabled-provider wire policy`() {
        val capturedAuthorization = AtomicReference<String?>()
        val capturedUrl = AtomicReference<String?>()
        val capturedBody = AtomicReference<String?>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    capturedAuthorization.set(request.header("Authorization"))
                    capturedUrl.set(request.url.toString())
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    capturedBody.set(buffer.readUtf8())
                    response(
                        request = request,
                        code = 200,
                        body =
                            """{"choices":[{"message":{"content":"respuesta"}}],"usage":{"prompt_tokens":3,"completion_tokens":2}}""",
                    )
                }
                .build()

        val result =
            transport(client).complete(
                request = request(),
                cancellationSignal = NeverCancelled,
            )

        val success =
            assertInstanceOf(
                GroqChatTransportResult.Success::class.java,
                result,
            )
        assertEquals("respuesta", success.responseText)
        assertEquals(3L, success.inputTokens)
        assertEquals(2L, success.outputTokens)
        assertEquals("Bearer unit-test-key", capturedAuthorization.get())
        assertEquals(GroqHttpsTransport.GROQ_CHAT_COMPLETIONS_URL, capturedUrl.get())

        val body = checkNotNull(capturedBody.get())
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"tool_choice\":\"none\""))
        assertTrue(body.contains("\"citation_options\":\"disabled\""))
        assertFalse(body.contains("\"tools\""))
        assertFalse(body.contains("browser"))
        assertFalse(body.contains("compound_custom"))
    }

    @Test
    fun `missing or unsafe credential fails before HTTP`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(
                        chain.request(),
                        200,
                        """{"choices":[{"message":{"content":"unexpected"}}]}""",
                    )
                }
                .build()

        val missing =
            transport(
                client = client,
                store = FakeSecretStore(null),
            ).complete(request(), NeverCancelled)
        assertTransportFailure(missing, GroqTransportFailureKind.AUTH_MISSING)

        val unsafe =
            transport(
                client = client,
                store = FakeSecretStore("bad\nkey".toByteArray(StandardCharsets.UTF_8)),
            ).complete(request(), NeverCancelled)
        assertTransportFailure(unsafe, GroqTransportFailureKind.AUTH_MISSING)

        assertEquals(0, calls.get())
    }

    @Test
    fun `429 retry-after seconds are bounded and preserved`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("rate limited")
                        .header("Retry-After", "2")
                        .body("{}".toResponseBody())
                        .build()
                }
                .build()

        val result = transport(client).complete(request(), NeverCancelled)
        val failure =
            assertInstanceOf(
                GroqChatTransportResult.HttpFailure::class.java,
                result,
            )
        assertEquals(429, failure.statusCode)
        assertEquals(2_000L, failure.retryAfterMillis)
    }

    @Test
    fun `oversized and malformed success responses fail closed`() {
        val oversizedClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        body = "x".repeat(256),
                    )
                }
                .build()
        val oversized =
            transport(
                client = oversizedClient,
                maxResponseBytes = 64,
            ).complete(request(), NeverCancelled)
        assertTransportFailure(oversized, GroqTransportFailureKind.INVALID_RESPONSE)

        val malformedClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(chain.request(), 200, "{}")
                }
                .build()
        val malformed =
            transport(malformedClient).complete(request(), NeverCancelled)
        assertTransportFailure(malformed, GroqTransportFailureKind.INVALID_RESPONSE)
    }

    @Test
    fun `network exceptions stay typed and private messages are not surfaced`() {
        val dnsClient =
            OkHttpClient.Builder()
                .addInterceptor { throw UnknownHostException("PRIVATE_DNS_DETAIL") }
                .build()
        val dns = transport(dnsClient).complete(request(), NeverCancelled)
        assertTransportFailure(dns, GroqTransportFailureKind.DNS)
        assertFalse(dns.toString().contains("PRIVATE_DNS_DETAIL"))

        val timeoutClient =
            OkHttpClient.Builder()
                .addInterceptor { throw SocketTimeoutException("PRIVATE_TIMEOUT_DETAIL") }
                .build()
        val timeout = transport(timeoutClient).complete(request(), NeverCancelled)
        assertTransportFailure(timeout, GroqTransportFailureKind.TIMEOUT)
        assertFalse(timeout.toString().contains("PRIVATE_TIMEOUT_DETAIL"))
    }

    @Test
    fun `cancellation stops before or immediately after enqueue`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(
                        chain.request(),
                        200,
                        """{"choices":[{"message":{"content":"late"}}]}""",
                    )
                }
                .build()

        val before =
            transport(client).complete(
                request(),
                CancellationSignal { true },
            )
        assertTransportFailure(before, GroqTransportFailureKind.CANCELLED)
        assertEquals(0, calls.get())

        var checks = 0
        val afterEnqueue =
            transport(client).complete(
                request(),
                CancellationSignal {
                    checks += 1
                    checks >= 2
                },
            )
        assertTransportFailure(afterEnqueue, GroqTransportFailureKind.CANCELLED)
    }

    @Test
    fun `request bound fails before HTTP and rendering never contains credential`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(
                        chain.request(),
                        200,
                        """{"choices":[{"message":{"content":"unexpected"}}]}""",
                    )
                }
                .build()
        val transport =
            transport(
                client = client,
                maxRequestBytes = 10,
            )

        val result = transport.complete(request(), NeverCancelled)

        assertTransportFailure(result, GroqTransportFailureKind.REQUEST_TOO_LARGE)
        assertEquals(0, calls.get())
        assertFalse(transport.toString().contains("unit-test-key"))
    }

    private fun transport(
        client: OkHttpClient,
        store: SecretStore =
            FakeSecretStore("unit-test-key".toByteArray(StandardCharsets.UTF_8)),
        maxRequestBytes: Int = 4096,
        maxResponseBytes: Int = 4096,
    ): GroqHttpsTransport =
        GroqHttpsTransport(
            callFactory = client,
            secretReader = store,
            codec =
                GroqChatJsonCodec(
                    maxRequestBytes = maxRequestBytes,
                    maxResponseBytes = maxResponseBytes,
                ),
            maxResponseBytes = maxResponseBytes,
            credentialId = SecretId(GroqHttpsTransport.DEFAULT_CREDENTIAL_ID),
        )

    private fun request(): GroqChatCompletionRequest =
        GroqChatCompletionRequest(
            modelId = "configured-model",
            messages =
                listOf(
                    GroqChatMessage(GroqChatRole.SYSTEM, "system"),
                    GroqChatMessage(GroqChatRole.USER, "request"),
                ),
        )

    private fun response(
        request: Request,
        code: Int,
        body: String,
    ): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody())
            .build()

    private fun assertTransportFailure(
        result: GroqChatTransportResult,
        kind: GroqTransportFailureKind,
    ) {
        val failure =
            assertInstanceOf(
                GroqChatTransportResult.TransportFailure::class.java,
                result,
            )
        assertEquals(kind, failure.kind)
    }

    private class FakeSecretStore(
        bytes: ByteArray?,
    ) : SecretStore {
        private val value = bytes?.copyOf()

        override fun read(id: SecretId): SecretStore.ReadResult =
            value?.let {
                SecretStore.ReadResult.available(SecretValue.copyOf(it))
            } ?: SecretStore.ReadResult.missing()

        override fun put(
            id: SecretId,
            value: SecretValue,
        ): SecretStore.MutationResult =
            SecretStore.MutationResult.stored()

        override fun delete(id: SecretId): SecretStore.MutationResult =
            SecretStore.MutationResult.missing()
    }
}
