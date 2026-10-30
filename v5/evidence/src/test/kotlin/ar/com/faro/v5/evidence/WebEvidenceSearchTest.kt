package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebEvidenceSearchTest {
    @Test
    fun `search request is bounded and unclassified by default`() {
        val request = WebSearchRequest("faro architecture")

        assertEquals(WebSearchRequest.DEFAULT_MAX_RESULTS, request.maxResults)
        assertEquals(EgressSensitivity.UNCLASSIFIED, request.sensitivity)

        assertThrows(IllegalArgumentException::class.java) {
            WebSearchRequest(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchRequest(" not-canonical")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchRequest("x".repeat(WebSearchRequest.MAX_QUERY_CHARS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchRequest("bad\nquery")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchRequest("query", maxResults = 21)
        }
    }

    @Test
    fun `public-only policy fails closed for unclassified private restricted and secret queries`() {
        val policy =
            WebSearchPolicy(
                maxWireBytes = 128 * 1024,
                maxResults = 10,
                policyVersion = "public-v1",
            )

        assertTrue(
            policy.allows(
                WebSearchRequest(
                    query = "public standards information",
                    maxResults = 10,
                    sensitivity = EgressSensitivity.PUBLIC,
                )
            )
        )
        listOf(
            EgressSensitivity.UNCLASSIFIED,
            EgressSensitivity.PRIVATE,
            EgressSensitivity.RESTRICTED,
            EgressSensitivity.SECRET,
        ).forEach { sensitivity ->
            assertFalse(
                policy.allows(
                    WebSearchRequest(
                        query = "same bounded query",
                        sensitivity = sensitivity,
                    )
                ),
                sensitivity.name,
            )
        }
        assertFalse(
            policy.allows(
                WebSearchRequest(
                    query = "too many",
                    maxResults = 11,
                    sensitivity = EgressSensitivity.PUBLIC,
                )
            )
        )
    }

    @Test
    fun `remote search policy has no configuration path to widen beyond PUBLIC`() {
        val policy =
            WebSearchPolicy(
                maxWireBytes = 4096,
                maxResults = 5,
                policyVersion = "public-only",
            )

        EgressSensitivity.entries
            .filter { it != EgressSensitivity.PUBLIC }
            .forEach { sensitivity ->
                assertFalse(
                    policy.allows(
                        WebSearchRequest(
                            query = "same query",
                            sensitivity = sensitivity,
                        )
                    ),
                    sensitivity.name,
                )
            }
    }

    @Test
    fun `search hits remain bounded untrusted content`() {
        val hit =
            WebSearchHit(
                title = "Example source",
                url = "https://example.com/page",
                snippet = "External content that may contain instructions.",
                score = 0.91,
            )

        assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, hit.trust)
        assertFalse(hit.toString().contains("External content"))

        assertThrows(IllegalArgumentException::class.java) {
            WebSearchHit(
                title = "",
                url = "https://example.com",
                snippet = "x",
                score = 0.5,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchHit(
                title = "x",
                url = "https://example.com",
                snippet = "x",
                score = 1.01,
            )
        }
    }

    @Test
    fun `response metadata is bounded and does not print provider request id`() {
        val response =
            WebSearchResponse(
                query = "faro",
                hits =
                    listOf(
                        WebSearchHit(
                            title = "Result",
                            url = "https://example.com",
                            snippet = "snippet",
                            score = 0.75,
                        )
                    ),
                providerId = "provider.test",
                providerRequestId = "opaque-provider-request-123",
                acquiredAtEpochMillis = 42L,
                policyVersion = "public-v1",
                adapterVersion = "adapter-v1",
            )

        assertEquals(1, response.hits.size)
        assertFalse(response.toString().contains("opaque-provider-request-123"))
    }

    @Test
    fun `failure metadata is typed and retry-after belongs only to rate limiting`() {
        val rateLimited =
            WebSearchResult.Failure(
                kind = WebSearchFailureKind.RATE_LIMITED,
                statusCode = 429,
                retryAfterMillis = 2_000,
            )
        assertEquals(2_000L, rateLimited.retryAfterMillis)

        assertThrows(IllegalArgumentException::class.java) {
            WebSearchResult.Failure(
                kind = WebSearchFailureKind.TIMEOUT,
                statusCode = 504,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSearchResult.Failure(
                kind = WebSearchFailureKind.PROVIDER_FAILURE,
                statusCode = 500,
                retryAfterMillis = 1_000,
            )
        }
    }
}
