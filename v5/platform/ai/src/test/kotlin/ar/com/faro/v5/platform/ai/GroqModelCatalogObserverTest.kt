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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class GroqModelCatalogObserverTest {
    @Test
    fun `authenticated GET observes models without mutating configuration`() {
        val capturedMethod = AtomicReference<String?>()
        val capturedUrl = AtomicReference<String?>()
        val capturedAuthorization = AtomicReference<String?>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    capturedMethod.set(request.method)
                    capturedUrl.set(request.url.toString())
                    capturedAuthorization.set(request.header("Authorization"))
                    response(
                        request,
                        200,
                        """
                        {
                          "object":"list",
                          "data":[
                            {
                              "id":"configured-model",
                              "object":"model",
                              "active":true,
                              "context_window":131072
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }
                .build()

        val result = observer(client).observe(NeverCancelled)

        val success =
            assertInstanceOf(
                GroqModelCatalogObservationResult.Success::class.java,
                result,
            )
        assertEquals(
            listOf(
                GroqObservedModel(
                    modelId = "configured-model",
                    active = true,
                    contextWindowTokens = 131072,
                )
            ),
            success.models,
        )
        assertEquals("GET", capturedMethod.get())
        assertEquals(GroqModelCatalogObserver.GROQ_MODELS_URL, capturedUrl.get())
        assertEquals("Bearer unit-test-key", capturedAuthorization.get())
    }

    @Test
    fun `missing or unsafe credential fails before HTTP`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(chain.request(), 200, """{"data":[]}""")
                }
                .build()

        val missing =
            observer(
                client = client,
                store = FakeSecretStore(null),
            ).observe(NeverCancelled)
        assertTransportFailure(missing, GroqTransportFailureKind.AUTH_MISSING)

        val unsafe =
            observer(
                client = client,
                store =
                    FakeSecretStore(
                        "bad\r\nheader".toByteArray(StandardCharsets.UTF_8)
                    ),
            ).observe(NeverCancelled)
        assertTransportFailure(unsafe, GroqTransportFailureKind.AUTH_MISSING)

        assertEquals(0, calls.get())
    }

    @Test
    fun `authentication failures remain HTTP observations rather than model absence`() {
        listOf(401, 403).forEach { status ->
            val client =
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        response(chain.request(), status, """{"error":"denied"}""")
                    }
                    .build()

            val result = observer(client).observe(NeverCancelled)
            val failure =
                assertInstanceOf(
                    GroqModelCatalogObservationResult.HttpFailure::class.java,
                    result,
                )
            assertEquals(status, failure.statusCode)
        }
    }

    @Test
    fun `429 preserves bounded retry-after without retrying`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("rate limited")
                        .header("Retry-After", "3")
                        .body("{}".toResponseBody())
                        .build()
                }
                .build()

        val result = observer(client).observe(NeverCancelled)
        val failure =
            assertInstanceOf(
                GroqModelCatalogObservationResult.HttpFailure::class.java,
                result,
            )
        assertEquals(429, failure.statusCode)
        assertEquals(3_000L, failure.retryAfterMillis)
        assertEquals(1, calls.get())
    }

    @Test
    fun `oversized and malformed success bodies fail closed`() {
        val oversizedClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(chain.request(), 200, "x".repeat(257))
                }
                .build()
        val oversized =
            observer(
                client = oversizedClient,
                maxResponseBytes = 256,
            ).observe(NeverCancelled)
        assertTransportFailure(
            oversized,
            GroqTransportFailureKind.INVALID_RESPONSE,
        )

        val malformedClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(chain.request(), 200, "{}")
                }
                .build()
        val malformed = observer(malformedClient).observe(NeverCancelled)
        assertTransportFailure(
            malformed,
            GroqTransportFailureKind.INVALID_RESPONSE,
        )
    }

    @Test
    fun `network failures are typed and exception details remain private`() {
        val dnsClient =
            OkHttpClient.Builder()
                .addInterceptor {
                    throw UnknownHostException("PRIVATE_DNS_DETAIL")
                }
                .build()
        val dns = observer(dnsClient).observe(NeverCancelled)
        assertTransportFailure(dns, GroqTransportFailureKind.DNS)
        assertFalse(dns.toString().contains("PRIVATE_DNS_DETAIL"))

        val timeoutClient =
            OkHttpClient.Builder()
                .addInterceptor {
                    throw SocketTimeoutException("PRIVATE_TIMEOUT_DETAIL")
                }
                .build()
        val timeout = observer(timeoutClient).observe(NeverCancelled)
        assertTransportFailure(timeout, GroqTransportFailureKind.TIMEOUT)
        assertFalse(timeout.toString().contains("PRIVATE_TIMEOUT_DETAIL"))
    }

    @Test
    fun `cancellation prevents or cancels provider observation`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(chain.request(), 200, """{"data":[]}""")
                }
                .build()

        val before =
            observer(client).observe(
                CancellationSignal { true }
            )
        assertTransportFailure(before, GroqTransportFailureKind.CANCELLED)
        assertEquals(0, calls.get())

        var checks = 0
        val afterEnqueue =
            observer(client).observe(
                CancellationSignal {
                    checks += 1
                    checks >= 2
                }
            )
        assertTransportFailure(
            afterEnqueue,
            GroqTransportFailureKind.CANCELLED,
        )
    }

    private fun observer(
        client: OkHttpClient,
        store: SecretStore =
            FakeSecretStore("unit-test-key".toByteArray(StandardCharsets.UTF_8)),
        maxResponseBytes: Int = 4096,
    ): GroqModelCatalogObserver =
        GroqModelCatalogObserver(
            callFactory = client,
            secretReader = store,
            codec = GroqModelCatalogCodec(maxBytes = maxResponseBytes),
            maxResponseBytes = maxResponseBytes,
            credentialId = SecretId(GroqHttpsTransport.DEFAULT_CREDENTIAL_ID),
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
        result: GroqModelCatalogObservationResult,
        kind: GroqTransportFailureKind,
    ) {
        val failure =
            assertInstanceOf(
                GroqModelCatalogObservationResult.TransportFailure::class.java,
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
