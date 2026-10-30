package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.EgressSensitivity

data class WebSearchRequest(
    val query: String,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val sensitivity: EgressSensitivity = EgressSensitivity.UNCLASSIFIED,
) {
    init {
        require(query.isNotBlank()) {
            "web search query must not be blank"
        }
        require(query == query.trim()) {
            "web search query must be canonical"
        }
        require(query.length <= MAX_QUERY_CHARS) {
            "web search query exceeds bound"
        }
        require(query.none { it.code < 0x20 || it.code == 0x7f }) {
            "web search query must not contain control characters"
        }
        require(maxResults in 1..MAX_RESULTS) {
            "web search result limit is invalid"
        }
    }

    companion object {
        const val MAX_QUERY_CHARS = 400
        const val MAX_RESULTS = 20
        const val DEFAULT_MAX_RESULTS = 5
    }
}

class WebSearchPolicy(
    val maxWireBytes: Int,
    val maxResults: Int,
    val policyVersion: String,
) {
    init {
        require(maxWireBytes in 1..MAX_RESPONSE_BYTES) {
            "web search response bound must be positive and bounded"
        }
        require(maxResults in 1..WebSearchRequest.MAX_RESULTS) {
            "web search policy result limit is invalid"
        }
        require(policyVersion.isNotBlank() && policyVersion.length <= MAX_VERSION_CHARS) {
            "web search policy version must be non-blank and bounded"
        }
    }

    fun allows(request: WebSearchRequest): Boolean =
        request.maxResults <= maxResults &&
            request.sensitivity == EgressSensitivity.PUBLIC

    companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_VERSION_CHARS = 64
    }
}

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val score: Double?,
    val trust: EvidenceContentTrust = EvidenceContentTrust.UNTRUSTED_CONTENT,
) {
    init {
        require(title.isNotBlank() && title.length <= MAX_TITLE_CHARS) {
            "web search hit title is invalid"
        }
        require(url.isNotBlank() && url.length <= WebFetchRequest.MAX_URL_CHARS) {
            "web search hit url is invalid"
        }
        require(url.none { it.code < 0x20 || it.code == 0x7f }) {
            "web search hit url must not contain control characters"
        }
        require(snippet.length <= MAX_SNIPPET_CHARS) {
            "web search hit snippet exceeds bound"
        }
        require(snippet.none { it.code == 0x00 || it.code == 0x7f }) {
            "web search hit snippet contains forbidden control characters"
        }
        require(score == null || (score.isFinite() && score in 0.0..1.0)) {
            "web search hit score must be within 0..1 when present"
        }
        require(trust == EvidenceContentTrust.UNTRUSTED_CONTENT) {
            "open-web search content is always untrusted"
        }
    }

    override fun toString(): String =
        "WebSearchHit(titleChars=" + title.length +
            ", urlChars=" + url.length +
            ", snippetChars=" + snippet.length +
            ", score=" + score +
            ", trust=" + trust + ")"

    companion object {
        const val MAX_TITLE_CHARS = 512
        const val MAX_SNIPPET_CHARS = 4096
    }
}

data class WebProviderUsage(
    val unitId: String,
    val amount: Long,
) {
    init {
        require(unitId.matches(UNIT_ID)) {
            "web provider usage unit id is invalid"
        }
        require(amount >= 0L) {
            "web provider usage amount must be non-negative"
        }
    }

    companion object {
        private val UNIT_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

data class WebSearchResponse(
    val query: String,
    val hits: List<WebSearchHit>,
    val providerId: String,
    val providerRequestId: String?,
    val providerUsage: WebProviderUsage? = null,
    val acquiredAtEpochMillis: Long,
    val policyVersion: String,
    val adapterVersion: String,
) {
    init {
        require(query.isNotBlank() && query.length <= WebSearchRequest.MAX_QUERY_CHARS) {
            "web search response query is invalid"
        }
        require(hits.size <= WebSearchRequest.MAX_RESULTS) {
            "web search response has too many hits"
        }
        require(providerId.matches(PROVIDER_ID)) {
            "web search provider id is invalid"
        }
        require(
            providerRequestId == null ||
                (
                    providerRequestId.isNotBlank() &&
                        providerRequestId.length <= MAX_PROVIDER_REQUEST_ID_CHARS &&
                        providerRequestId.none { it.code < 0x20 || it.code == 0x7f }
                )
        ) {
            "web search provider request id is invalid"
        }
        require(acquiredAtEpochMillis >= 0L) {
            "web search acquisition time must be non-negative"
        }
        require(policyVersion.isNotBlank() && policyVersion.length <= WebSearchPolicy.MAX_VERSION_CHARS) {
            "web search response policy version is invalid"
        }
        require(adapterVersion.isNotBlank() && adapterVersion.length <= WebSearchPolicy.MAX_VERSION_CHARS) {
            "web search adapter version is invalid"
        }
    }

    override fun toString(): String =
        "WebSearchResponse(hits=" + hits.size +
            ", providerId=" + providerId +
            ", providerRequestId=" +
            (providerRequestId?.let { "[REDACTED]" } ?: "null") +
            ", providerUsage=" + providerUsage +
            ", acquiredAtEpochMillis=" + acquiredAtEpochMillis + ")"

    companion object {
        const val MAX_PROVIDER_REQUEST_ID_CHARS = 256
        private val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

enum class WebSearchFailureKind {
    INVALID_REQUEST,
    POLICY_EGRESS_BLOCKED,
    AUTH_MISSING,
    AUTH_REJECTED,
    DNS_FAILURE,
    CONNECT_FAILURE,
    TLS_FAILURE,
    TIMEOUT,
    RATE_LIMITED,
    QUOTA_EXHAUSTED,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE,
    PROVIDER_FAILURE,
    CANCELLED,
}

sealed interface WebSearchResult {
    data class Success(
        val response: WebSearchResponse,
    ) : WebSearchResult

    data class Failure(
        val kind: WebSearchFailureKind,
        val statusCode: Int? = null,
        val retryAfterMillis: Long? = null,
    ) : WebSearchResult {
        init {
            require(statusCode == null || statusCode in 100..599) {
                "web search failure status code is invalid"
            }
            require(retryAfterMillis == null || retryAfterMillis > 0L) {
                "web search retry-after must be positive when present"
            }
            require(
                statusCode == null ||
                    kind in
                        setOf(
                            WebSearchFailureKind.AUTH_REJECTED,
                            WebSearchFailureKind.RATE_LIMITED,
                            WebSearchFailureKind.QUOTA_EXHAUSTED,
                            WebSearchFailureKind.PROVIDER_FAILURE,
                        )
            ) {
                "web search failure kind may not carry an HTTP status"
            }
            require(
                retryAfterMillis == null ||
                    kind == WebSearchFailureKind.RATE_LIMITED
            ) {
                "only rate-limited web search may carry retry-after"
            }
        }
    }
}

fun interface SafeWebSearcher {
    fun search(
        request: WebSearchRequest,
        policy: WebSearchPolicy,
        cancellationSignal: CancellationSignal,
    ): WebSearchResult
}
