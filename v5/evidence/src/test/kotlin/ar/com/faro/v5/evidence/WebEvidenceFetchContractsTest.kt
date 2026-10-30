package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebEvidenceFetchContractsTest {
    @Test
    fun `web fetch request rejects control characters and oversized urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebFetchRequest("https://example.com/\ninternal")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebFetchRequest("https://" + "a".repeat(WebFetchRequest.MAX_URL_CHARS))
        }
    }

    @Test
    fun `web fetch policy canonicalizes media types and enforces bounds`() {
        val policy =
            WebFetchPolicy(
                maxWireBytes = 1024,
                maxDecodedBytes = 2048,
                maxRedirects = 3,
                allowedMediaTypes = setOf(" Text/HTML ", "APPLICATION/JSON"),
                policyVersion = "p2-a1",
                fetcherVersion = "safe-http-v1",
            )

        assertEquals(
            setOf("text/html", "application/json"),
            policy.allowedMediaTypes,
        )

        assertThrows(IllegalArgumentException::class.java) {
            WebFetchPolicy(
                maxWireBytes = 0,
                maxDecodedBytes = 1,
                maxRedirects = 0,
                allowedMediaTypes = setOf("text/plain"),
                policyVersion = "p",
                fetcherVersion = "f",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebFetchPolicy(
                maxWireBytes = 1,
                maxDecodedBytes = 1,
                maxRedirects = WebFetchPolicy.MAX_REDIRECTS + 1,
                allowedMediaTypes = setOf("text/plain"),
                policyVersion = "p",
                fetcherVersion = "f",
            )
        }
    }

    @Test
    fun `GET source requires bounded body digest and remains untrusted`() {
        val body =
            CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = "external page".encodeToByteArray(),
            )
        val source =
            WebFetchedSource(
                requestedUrl = "https://example.com/start",
                finalUrl = "https://example.com/final",
                method = WebFetchMethod.GET,
                statusCode = 200,
                mediaType = "text/plain",
                redirectCount = 1,
                acquiredAtEpochMillis = 1234,
                contentSha256Hex = "a".repeat(64),
                body = body,
                policyVersion = "p2-a1",
                fetcherVersion = "safe-http-v1",
            )

        assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, source.trust)
        assertEquals(body, source.body)
        assertTrue(!source.toString().contains("external page"))
        assertTrue(!source.toString().contains("example.com"))

        assertThrows(IllegalArgumentException::class.java) {
            WebFetchedSource(
                requestedUrl = "https://example.com",
                finalUrl = "https://example.com",
                method = WebFetchMethod.GET,
                statusCode = 200,
                mediaType = "text/plain",
                redirectCount = 0,
                acquiredAtEpochMillis = 1,
                contentSha256Hex = null,
                body = null,
                policyVersion = "p",
                fetcherVersion = "f",
            )
        }
    }

    @Test
    fun `HEAD source may retain provenance without body`() {
        val source =
            WebFetchedSource(
                requestedUrl = "https://example.com",
                finalUrl = "https://example.com",
                method = WebFetchMethod.HEAD,
                statusCode = 200,
                mediaType = "text/html",
                redirectCount = 0,
                acquiredAtEpochMillis = 1,
                contentSha256Hex = null,
                body = null,
                policyVersion = "p",
                fetcherVersion = "f",
            )

        assertEquals(null, source.body)
        assertEquals(null, source.contentSha256Hex)
        assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, source.trust)
    }

    @Test
    fun `failure status metadata cannot contradict failure kind`() {
        val httpFailure =
            WebFetchResult.Failure(
                kind = WebFetchFailureKind.HTTP_STATUS,
                statusCode = 429,
                retryAfterMillis = 2000,
            )
        assertEquals(429, httpFailure.statusCode)

        assertThrows(IllegalArgumentException::class.java) {
            WebFetchResult.Failure(
                kind = WebFetchFailureKind.TIMEOUT,
                statusCode = 500,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebFetchResult.Failure(
                kind = WebFetchFailureKind.HTTP_STATUS,
            )
        }
    }
}
