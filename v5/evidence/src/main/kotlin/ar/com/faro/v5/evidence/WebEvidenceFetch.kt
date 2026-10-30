package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload

enum class WebFetchMethod {
    GET,
    HEAD,
}

data class WebFetchRequest(
    val url: String,
    val method: WebFetchMethod = WebFetchMethod.GET,
) {
    init {
        require(url.isNotBlank()) {
            "web fetch url must not be blank"
        }
        require(url.length <= MAX_URL_CHARS) {
            "web fetch url exceeds bound"
        }
        require(url.none { it.code < 0x20 || it.code == 0x7f }) {
            "web fetch url must not contain control characters"
        }
    }

    companion object {
        const val MAX_URL_CHARS = 4096
    }
}

class WebFetchPolicy(
    val maxWireBytes: Int,
    val maxDecodedBytes: Int,
    val maxRedirects: Int,
    allowedMediaTypes: Set<String>,
    val policyVersion: String,
    val fetcherVersion: String,
) {
    val allowedMediaTypes: Set<String> =
        allowedMediaTypes
            .map { it.trim().lowercase() }
            .toSet()

    init {
        require(maxWireBytes in 1..MAX_BODY_BYTES) {
            "web fetch wire byte bound must be positive and bounded"
        }
        require(maxDecodedBytes in 1..MAX_BODY_BYTES) {
            "web fetch decoded byte bound must be positive and bounded"
        }
        require(maxRedirects in 0..MAX_REDIRECTS) {
            "web fetch redirect bound is invalid"
        }
        require(this.allowedMediaTypes.isNotEmpty()) {
            "web fetch allowed media types must not be empty"
        }
        require(
            this.allowedMediaTypes.all {
                it.isNotBlank() &&
                    it.length <= MAX_MEDIA_TYPE_CHARS &&
                    MEDIA_TYPE.matches(it)
            }
        ) {
            "web fetch allowed media types must be canonical type/subtype values"
        }
        require(policyVersion.isNotBlank() && policyVersion.length <= MAX_VERSION_CHARS) {
            "web fetch policy version must be non-blank and bounded"
        }
        require(fetcherVersion.isNotBlank() && fetcherVersion.length <= MAX_VERSION_CHARS) {
            "web fetcher version must be non-blank and bounded"
        }
    }

    companion object {
        const val MAX_BODY_BYTES = 16 * 1024 * 1024
        const val MAX_REDIRECTS = 8
        const val MAX_MEDIA_TYPE_CHARS = 127
        const val MAX_VERSION_CHARS = 64
        private val MEDIA_TYPE =
            Regex("[a-z0-9!#$&^_.+\\-]+/[a-z0-9!#$&^_.+\\-]+")
    }
}

enum class EvidenceContentTrust {
    UNTRUSTED_CONTENT,
}

data class WebFetchedSource(
    val requestedUrl: String,
    val finalUrl: String,
    val method: WebFetchMethod,
    val statusCode: Int,
    val mediaType: String?,
    val redirectCount: Int,
    val acquiredAtEpochMillis: Long,
    val contentSha256Hex: String?,
    val body: CanonicalPayload?,
    val policyVersion: String,
    val fetcherVersion: String,
    val trust: EvidenceContentTrust = EvidenceContentTrust.UNTRUSTED_CONTENT,
) {
    init {
        require(requestedUrl.isNotBlank() && requestedUrl.length <= WebFetchRequest.MAX_URL_CHARS) {
            "requested web source url is invalid"
        }
        require(finalUrl.isNotBlank() && finalUrl.length <= WebFetchRequest.MAX_URL_CHARS) {
            "final web source url is invalid"
        }
        require(statusCode in 200..299) {
            "successful web source status must be 2xx"
        }
        require(redirectCount in 0..WebFetchPolicy.MAX_REDIRECTS) {
            "web source redirect count is invalid"
        }
        require(acquiredAtEpochMillis >= 0L) {
            "web source acquisition time must be non-negative"
        }
        require(
            mediaType == null ||
                (
                    mediaType.length <= WebFetchPolicy.MAX_MEDIA_TYPE_CHARS &&
                        mediaType == mediaType.trim().lowercase()
                )
        ) {
            "web source media type must be canonical and bounded"
        }
        require(
            contentSha256Hex == null ||
                SHA256_HEX.matches(contentSha256Hex)
        ) {
            "web source digest must be lowercase sha256 hex"
        }
        require((body == null) == (contentSha256Hex == null)) {
            "web source body and digest must either both exist or both be absent"
        }
        require(method != WebFetchMethod.GET || body != null) {
            "successful GET evidence must retain a bounded body snapshot"
        }
        require(policyVersion.isNotBlank() && policyVersion.length <= WebFetchPolicy.MAX_VERSION_CHARS) {
            "web source policy version must be non-blank and bounded"
        }
        require(fetcherVersion.isNotBlank() && fetcherVersion.length <= WebFetchPolicy.MAX_VERSION_CHARS) {
            "web source fetcher version must be non-blank and bounded"
        }
        require(trust == EvidenceContentTrust.UNTRUSTED_CONTENT) {
            "open-web content is always untrusted"
        }
    }

    override fun toString(): String =
        "WebFetchedSource(statusCode=" + statusCode +
            ", mediaType=" + mediaType +
            ", redirectCount=" + redirectCount +
            ", bodyBytes=" + (body?.size ?: 0) +
            ", trust=" + trust + ")"

    companion object {
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

enum class WebFetchFailureKind {
    INVALID_REQUEST,
    DESTINATION_BLOCKED,
    DNS_FAILURE,
    CONNECT_FAILURE,
    TLS_FAILURE,
    TIMEOUT,
    REDIRECT_BLOCKED,
    TOO_MANY_REDIRECTS,
    RESPONSE_TOO_LARGE,
    CONTENT_TYPE_REJECTED,
    CONTENT_ENCODING_REJECTED,
    HTTP_STATUS,
    INVALID_RESPONSE,
    CANCELLED,
}

sealed interface WebFetchResult {
    data class Success(
        val source: WebFetchedSource,
    ) : WebFetchResult

    data class Failure(
        val kind: WebFetchFailureKind,
        val statusCode: Int? = null,
        val retryAfterMillis: Long? = null,
    ) : WebFetchResult {
        init {
            require(statusCode == null || statusCode in 100..599) {
                "web fetch failure status code is invalid"
            }
            require(retryAfterMillis == null || retryAfterMillis > 0L) {
                "web fetch retry-after must be positive when present"
            }
            require(
                (kind == WebFetchFailureKind.HTTP_STATUS) == (statusCode != null)
            ) {
                "only HTTP_STATUS failures may carry an HTTP status"
            }
        }
    }
}

fun interface SafeHttpFetcher {
    fun fetch(
        request: WebFetchRequest,
        policy: WebFetchPolicy,
        cancellationSignal: CancellationSignal,
    ): WebFetchResult
}
