package ar.com.faro.v5.platform.web

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.control.spi.SecretValue
import ar.com.faro.v5.evidence.WebProviderUsage
import ar.com.faro.v5.evidence.WebSearchFailureKind
import ar.com.faro.v5.evidence.WebSearchPolicy
import ar.com.faro.v5.evidence.WebSearchRequest
import ar.com.faro.v5.evidence.WebSearchResult
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.NeverCancelled
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

class TavilyWebSearcherTest {
    @Test
    fun `public search uses fixed basic-cost request and returns bounded evidence`() {
        val capturedAuth = AtomicReference<String?>()
        val capturedUrl = AtomicReference<String?>()
        val capturedBody = AtomicReference<String?>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    capturedAuth.set(request.header("Authorization"))
                    capturedUrl.set(request.url.toString())
                    capturedBody.set(request.bodyText())
                    response(
                        request = request,
                        code = 200,
                        body =
                            """
                            {
                              "query":"faro architecture",
                              "results":[
                                {
                                  "title":"FARO source",
                                  "url":"https://example.com/faro",
                                  "content":"bounded public snippet",
                                  "score":0.92
                                }
                              ],
                              "request_id":"req-123",
                              "usage":{"credits":1}
                            }
                            """.trimIndent(),
                    )
                }
                .build()

        val result =
            searcher(
                client = client,
                secretReader = reader("tvly-unit-test-key"),
            ).search(
                request = publicRequest("faro architecture"),
                policy = policy(),
                cancellationSignal = NeverCancelled,
            )

        val success =
            assertInstanceOf(WebSearchResult.Success::class.java, result)
        assertEquals(TavilyWebSearcher.SEARCH_URL, capturedUrl.get())
        assertEquals("Bearer tvly-unit-test-key", capturedAuth.get())
        val body = requireNotNull(capturedBody.get())
        assertTrue(body.contains("\"search_depth\":\"basic\""))
        assertTrue(body.contains("\"topic\":\"general\""))
        assertTrue(body.contains("\"max_results\":5"))
        assertTrue(body.contains("\"include_answer\":false"))
        assertTrue(body.contains("\"include_raw_content\":false"))
        assertTrue(body.contains("\"include_images\":false"))
        assertTrue(body.contains("\"include_image_descriptions\":false"))
        assertTrue(body.contains("\"include_favicon\":false"))
        assertTrue(body.contains("\"auto_parameters\":false"))
        assertTrue(body.contains("\"include_usage\":true"))

        assertEquals(1, success.response.hits.size)
        assertEquals(
            "https://example.com/faro",
            success.response.hits.single().url,
        )
        assertEquals(
            WebProviderUsage("credit", 1),
            success.response.providerUsage,
        )
        assertEquals("tavily", success.response.providerId)
        assertEquals(1234L, success.response.acquiredAtEpochMillis)
        assertFalse(success.toString().contains("tvly-unit-test-key"))
    }

    @Test
    fun `non-public request is blocked before credential read and network`() {
        val credentialReads = AtomicInteger(0)
        val networkCalls = AtomicInteger(0)
        val reader =
            SecretReader {
                credentialReads.incrementAndGet()
                SecretValue.copyOf("should-not-read".encodeToByteArray())
            }
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    networkCalls.incrementAndGet()
                    response(chain.request(), 200, """{"query":"x","results":[]}""")
                }
                .build()

        listOf(
            EgressSensitivity.UNCLASSIFIED,
            EgressSensitivity.PRIVATE,
            EgressSensitivity.RESTRICTED,
            EgressSensitivity.SECRET,
        ).forEach { sensitivity ->
            val result =
                searcher(client, reader).search(
                    WebSearchRequest(
                        query = "same query",
                        sensitivity = sensitivity,
                    ),
                    policy(),
                    NeverCancelled,
                )
            assertFailure(result, WebSearchFailureKind.POLICY_EGRESS_BLOCKED)
        }

        assertEquals(0, credentialReads.get())
        assertEquals(0, networkCalls.get())
    }

    @Test
    fun `missing or unsafe credential performs no network call`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(chain.request(), 200, """{"query":"x","results":[]}""")
                }
                .build()

        val missing =
            searcher(client, SecretReader { null }).search(
                publicRequest("missing key"),
                policy(),
                NeverCancelled,
            )
        assertFailure(missing, WebSearchFailureKind.AUTH_MISSING)

        val unsafe =
            searcher(
                client,
                reader("bad\r\nheader"),
            ).search(
                publicRequest("unsafe key"),
                policy(),
                NeverCancelled,
            )
        assertFailure(unsafe, WebSearchFailureKind.AUTH_MISSING)

        assertEquals(0, calls.get())
    }

    @Test
    fun `unsafe result URLs are removed from provider output`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        chain.request(),
                        200,
                        """
                        {
                          "query":"safe urls",
                          "results":[
                            {"title":"safe","url":"https://example.com/a","content":"ok","score":0.9},
                            {"title":"loopback","url":"https://127.0.0.1/private","content":"bad","score":0.8},
                            {"title":"credential","url":"https://example.com/a?token=secret","content":"bad","score":0.7},
                            {"title":"downgrade","url":"http://example.com/a","content":"bad","score":0.6}
                          ]
                        }
                        """.trimIndent(),
                    )
                }
                .build()

        val result =
            searcher(client, reader("key")).search(
                publicRequest("safe urls", maxResults = 4),
                policy(maxResults = 4),
                NeverCancelled,
            )

        val success =
            assertInstanceOf(WebSearchResult.Success::class.java, result)
        assertEquals(1, success.response.hits.size)
        assertEquals("https://example.com/a", success.response.hits.single().url)
    }

    @Test
    fun `provider HTTP failures map to typed auth rate and quota failures`() {
        val cases =
            listOf(
                Triple(401, WebSearchFailureKind.AUTH_REJECTED, null),
                Triple(403, WebSearchFailureKind.AUTH_REJECTED, null),
                Triple(432, WebSearchFailureKind.QUOTA_EXHAUSTED, null),
                Triple(433, WebSearchFailureKind.QUOTA_EXHAUSTED, null),
                Triple(500, WebSearchFailureKind.PROVIDER_FAILURE, null),
            )

        cases.forEach { (status, kind, _) ->
            val client =
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        response(chain.request(), status, "{}")
                    }
                    .build()
            val result =
                searcher(client, reader("key")).search(
                    publicRequest("status $status"),
                    policy(),
                    NeverCancelled,
                )
            val failure =
                assertInstanceOf(WebSearchResult.Failure::class.java, result)
            assertEquals(kind, failure.kind)
            assertEquals(status, failure.statusCode)
        }

        val rateClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("rate limited")
                        .header("Retry-After", "3")
                        .header("Content-Type", "application/json")
                        .body("{}".toResponseBody())
                        .build()
                }
                .build()
        val rate =
            searcher(rateClient, reader("key")).search(
                publicRequest("rate"),
                policy(),
                NeverCancelled,
            )
        val failure =
            assertInstanceOf(WebSearchResult.Failure::class.java, rate)
        assertEquals(WebSearchFailureKind.RATE_LIMITED, failure.kind)
        assertEquals(429, failure.statusCode)
        assertEquals(3_000L, failure.retryAfterMillis)
    }

    @Test
    fun `malformed wrong media type and oversized responses fail closed`() {
        val malformed =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(chain.request(), 200, "{not-json")
                }
                .build()
        assertFailure(
            searcher(malformed, reader("key")).search(
                publicRequest("malformed"),
                policy(),
                NeverCancelled,
            ),
            WebSearchFailureKind.INVALID_RESPONSE,
        )

        val wrongMedia =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("ok")
                        .header("Content-Type", "text/plain")
                        .body("""{"query":"wrong media","results":[]}""".toResponseBody())
                        .build()
                }
                .build()
        assertFailure(
            searcher(wrongMedia, reader("key")).search(
                publicRequest("wrong media"),
                policy(),
                NeverCancelled,
            ),
            WebSearchFailureKind.INVALID_RESPONSE,
        )

        val oversized =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        chain.request(),
                        200,
                        "x".repeat(2048),
                    )
                }
                .build()
        assertFailure(
            searcher(oversized, reader("key")).search(
                publicRequest("oversized"),
                policy(maxWireBytes = 1024),
                NeverCancelled,
            ),
            WebSearchFailureKind.RESPONSE_TOO_LARGE,
        )
    }

    @Test
    fun `cancellation before search performs no credential read or network`() {
        val reads = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val reader =
            SecretReader {
                reads.incrementAndGet()
                SecretValue.copyOf("key".encodeToByteArray())
            }
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(chain.request(), 200, """{"query":"cancel","results":[]}""")
                }
                .build()

        val result =
            searcher(client, reader).search(
                publicRequest("cancel"),
                policy(),
                CancellationSignal { true },
            )

        assertFailure(result, WebSearchFailureKind.CANCELLED)
        assertEquals(0, reads.get())
        assertEquals(0, calls.get())
    }

    private fun searcher(
        client: OkHttpClient,
        secretReader: SecretReader,
    ): TavilyWebSearcher =
        TavilyWebSearcher(
            callFactory = client,
            secretReader = secretReader,
            clock = { 1234L },
            credentialId = SecretId(TavilyWebSearcher.DEFAULT_CREDENTIAL_ID),
        )

    private fun reader(value: String): SecretReader =
        SecretReader {
            SecretValue.copyOf(
                value.toByteArray(StandardCharsets.UTF_8)
            )
        }

    private fun publicRequest(
        query: String,
        maxResults: Int = 5,
    ): WebSearchRequest =
        WebSearchRequest(
            query = query,
            maxResults = maxResults,
            sensitivity = EgressSensitivity.PUBLIC,
        )

    private fun policy(
        maxWireBytes: Int = 128 * 1024,
        maxResults: Int = 5,
    ): WebSearchPolicy =
        WebSearchPolicy(
            maxWireBytes = maxWireBytes,
            maxResults = maxResults,
            policyVersion = "public-test-v1",
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
            .header("Content-Type", "application/json")
            .body(body.toResponseBody())
            .build()

    private fun Request.bodyText(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun assertFailure(
        result: WebSearchResult,
        kind: WebSearchFailureKind,
    ) {
        val failure =
            assertInstanceOf(WebSearchResult.Failure::class.java, result)
        assertEquals(kind, failure.kind)
    }
}
