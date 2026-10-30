package ar.com.faro.v5.platform.web

import ar.com.faro.v5.evidence.EvidenceContentTrust
import ar.com.faro.v5.evidence.WebFetchFailureKind
import ar.com.faro.v5.evidence.WebFetchMethod
import ar.com.faro.v5.evidence.WebFetchPolicy
import ar.com.faro.v5.evidence.WebFetchRequest
import ar.com.faro.v5.evidence.WebFetchResult
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.NeverCancelled
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OkHttpSafeHttpFetcherTest {
    @Test
    fun `GET records canonical provenance digest and untrusted body`() {
        val capturedAuthorization = AtomicReference<String?>()
        val capturedCookie = AtomicReference<String?>()
        val capturedEncoding = AtomicReference<String?>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedAuthorization.set(chain.request().header("Authorization"))
                    capturedCookie.set(chain.request().header("Cookie"))
                    capturedEncoding.set(chain.request().header("Accept-Encoding"))
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain; charset=utf-8",
                        body = "external page".encodeToByteArray(),
                    )
                }
                .build()

        val result =
            fetcher(client).fetch(
                request = WebFetchRequest("https://example.com/page"),
                policy = policy(),
                cancellationSignal = NeverCancelled,
            )

        val success = assertInstanceOf(WebFetchResult.Success::class.java, result)
        val source = success.source
        assertEquals("https://example.com/page", source.requestedUrl)
        assertEquals("https://example.com/page", source.finalUrl)
        assertEquals("text/plain", source.mediaType)
        assertEquals(200, source.statusCode)
        assertEquals(0, source.redirectCount)
        assertEquals(1234L, source.acquiredAtEpochMillis)
        assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, source.trust)
        assertEquals("external page", source.body?.copyBytes()?.decodeToString())
        assertEquals(
            sha256("external page".encodeToByteArray()),
            source.contentSha256Hex,
        )
        assertNull(capturedAuthorization.get())
        assertNull(capturedCookie.get())
        assertEquals("gzip", capturedEncoding.get())
    }

    @Test
    fun `manual redirect is revalidated and provenance retains final URL`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    when (calls.incrementAndGet()) {
                        1 ->
                            Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(302)
                                .message("redirect")
                                .header("Location", "/final")
                                .body(ByteArray(0).toResponseBody())
                                .build()

                        else ->
                            response(
                                request = chain.request(),
                                code = 200,
                                contentType = "text/plain",
                                body = "ok".encodeToByteArray(),
                            )
                    }
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest("https://example.com/start"),
                policy(),
                NeverCancelled,
            )

        val success = assertInstanceOf(WebFetchResult.Success::class.java, result)
        assertEquals("https://example.com/final", success.source.finalUrl)
        assertEquals(1, success.source.redirectCount)
        assertEquals(2, calls.get())
    }

    @Test
    fun `redirect to blocked literal destination fails before second call`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("redirect")
                        .header("Location", "https://127.0.0.1/private")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest("https://example.com/start"),
                policy(),
                NeverCancelled,
            )

        assertFailure(result, WebFetchFailureKind.REDIRECT_BLOCKED)
        assertEquals(1, calls.get())
    }

    @Test
    fun `gzip is decoded under independent wire and decoded bounds`() {
        val compressed = gzip("compressed evidence".encodeToByteArray())
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain",
                        contentEncoding = "gzip",
                        body = compressed,
                    )
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest("https://example.com/data"),
                policy(maxWireBytes = compressed.size + 8, maxDecodedBytes = 128),
                NeverCancelled,
            )

        val success = assertInstanceOf(WebFetchResult.Success::class.java, result)
        assertEquals(
            "compressed evidence",
            success.source.body?.copyBytes()?.decodeToString(),
        )
    }

    @Test
    fun `wire and decoded size limits fail closed`() {
        val rawClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain",
                        body = ByteArray(65) { 1 },
                    )
                }
                .build()

        val rawResult =
            fetcher(rawClient).fetch(
                WebFetchRequest("https://example.com/raw"),
                policy(maxWireBytes = 64, maxDecodedBytes = 128),
                NeverCancelled,
            )
        assertFailure(rawResult, WebFetchFailureKind.RESPONSE_TOO_LARGE)

        val expanded = ByteArray(4096) { 'a'.code.toByte() }
        val compressed = gzip(expanded)
        val gzipClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain",
                        contentEncoding = "gzip",
                        body = compressed,
                    )
                }
                .build()

        val decodedResult =
            fetcher(gzipClient).fetch(
                WebFetchRequest("https://example.com/gzip"),
                policy(
                    maxWireBytes = compressed.size + 8,
                    maxDecodedBytes = 256,
                ),
                NeverCancelled,
            )
        assertFailure(decodedResult, WebFetchFailureKind.RESPONSE_TOO_LARGE)
    }

    @Test
    fun `content type and content encoding policy fail closed`() {
        val badType =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "application/octet-stream",
                        body = byteArrayOf(1, 2, 3),
                    )
                }
                .build()
        assertFailure(
            fetcher(badType).fetch(
                WebFetchRequest("https://example.com/file"),
                policy(),
                NeverCancelled,
            ),
            WebFetchFailureKind.CONTENT_TYPE_REJECTED,
        )

        val badEncoding =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain",
                        contentEncoding = "br",
                        body = byteArrayOf(1, 2, 3),
                    )
                }
                .build()
        assertFailure(
            fetcher(badEncoding).fetch(
                WebFetchRequest("https://example.com/file"),
                policy(),
                NeverCancelled,
            ),
            WebFetchFailureKind.CONTENT_ENCODING_REJECTED,
        )
    }

    @Test
    fun `HTTP failure preserves bounded retry-after`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("rate limited")
                        .header("Retry-After", "3")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest("https://example.com/rate"),
                policy(),
                NeverCancelled,
            )

        val failure = assertInstanceOf(WebFetchResult.Failure::class.java, result)
        assertEquals(WebFetchFailureKind.HTTP_STATUS, failure.kind)
        assertEquals(429, failure.statusCode)
        assertEquals(3000L, failure.retryAfterMillis)
    }

    @Test
    fun `HEAD records provenance without retaining content`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/html",
                        body = "ignored".encodeToByteArray(),
                    )
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest(
                    url = "https://example.com/head",
                    method = WebFetchMethod.HEAD,
                ),
                policy(),
                NeverCancelled,
            )

        val success = assertInstanceOf(WebFetchResult.Success::class.java, result)
        assertNull(success.source.body)
        assertNull(success.source.contentSha256Hex)
        assertEquals(WebFetchMethod.HEAD, success.source.method)
    }

    @Test
    fun `cancellation before HTTP performs no call`() {
        val calls = AtomicInteger(0)
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    response(
                        request = chain.request(),
                        code = 200,
                        contentType = "text/plain",
                        body = "late".encodeToByteArray(),
                    )
                }
                .build()

        val result =
            fetcher(client).fetch(
                WebFetchRequest("https://example.com/cancel"),
                policy(),
                CancellationSignal { true },
            )

        assertFailure(result, WebFetchFailureKind.CANCELLED)
        assertEquals(0, calls.get())
    }

    private fun fetcher(client: OkHttpClient): OkHttpSafeHttpFetcher =
        OkHttpSafeHttpFetcher(
            callFactory = client,
            clock = { 1234L },
        )

    private fun policy(
        maxWireBytes: Int = 4096,
        maxDecodedBytes: Int = 4096,
    ): WebFetchPolicy =
        WebFetchPolicy(
            maxWireBytes = maxWireBytes,
            maxDecodedBytes = maxDecodedBytes,
            maxRedirects = 3,
            allowedMediaTypes = setOf("text/plain", "text/html", "application/json"),
            policyVersion = "p2-test",
            fetcherVersion = "okhttp-safe-v1",
        )

    private fun response(
        request: Request,
        code: Int,
        contentType: String,
        body: ByteArray,
        contentEncoding: String? = null,
    ): Response {
        val builder =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .header("Content-Type", contentType)
                .body(body.toResponseBody())
        if (contentEncoding != null) {
            builder.header("Content-Encoding", contentEncoding)
        }
        return builder.build()
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(bytes)
            }
            output.toByteArray()
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun assertFailure(
        result: WebFetchResult,
        kind: WebFetchFailureKind,
    ) {
        val failure = assertInstanceOf(WebFetchResult.Failure::class.java, result)
        assertEquals(kind, failure.kind)
    }
}
