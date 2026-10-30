package ar.com.faro.v5.platform.web

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.evidence.SafeWebSearcher
import ar.com.faro.v5.evidence.WebSearchFailureKind
import ar.com.faro.v5.evidence.WebSearchPolicy
import ar.com.faro.v5.evidence.WebSearchRequest
import ar.com.faro.v5.evidence.WebSearchResponse
import ar.com.faro.v5.evidence.WebSearchResult
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.IoExecutionPolicy
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class TavilyWebSearcher internal constructor(
    private val callFactory: Call.Factory,
    private val secretReader: SecretReader,
    private val clock: () -> Long,
    private val credentialId: SecretId,
) : SafeWebSearcher {
    constructor(
        secretReader: SecretReader,
        ioPolicy: IoExecutionPolicy,
        credentialId: SecretId = SecretId(DEFAULT_CREDENTIAL_ID),
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        callFactory =
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(ioPolicy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(ioPolicy.totalTimeoutMillis, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        secretReader = secretReader,
        clock = clock,
        credentialId = credentialId,
    )

    override fun search(
        request: WebSearchRequest,
        policy: WebSearchPolicy,
        cancellationSignal: CancellationSignal,
    ): WebSearchResult {
        if (cancellationSignal.isCancelled()) {
            return failure(WebSearchFailureKind.CANCELLED)
        }
        if (!policy.allows(request)) {
            return failure(WebSearchFailureKind.POLICY_EGRESS_BLOCKED)
        }

        val authorization =
            readAuthorizationHeader()
                ?: return failure(WebSearchFailureKind.AUTH_MISSING)

        val codec =
            TavilySearchJsonCodec(
                maxRequestBytes = TavilySearchJsonCodec.DEFAULT_MAX_REQUEST_BYTES,
                maxResponseBytes = policy.maxWireBytes,
            )
        val encoded = codec.encode(request)
        if (encoded === TavilySearchEncodeResult.TooLarge) {
            return failure(WebSearchFailureKind.INVALID_REQUEST)
        }
        val requestBytes = (encoded as TavilySearchEncodeResult.Ready).copyBytes()

        return try {
            if (cancellationSignal.isCancelled()) {
                return failure(WebSearchFailureKind.CANCELLED)
            }
            val httpRequest =
                Request.Builder()
                    .url(SEARCH_URL)
                    .header(AUTHORIZATION_HEADER, authorization)
                    .header(ACCEPT_HEADER, JSON_MEDIA_TYPE_VALUE)
                    .post(requestBytes.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            execute(
                httpRequest = httpRequest,
                logicalRequest = request,
                policy = policy,
                codec = codec,
                cancellationSignal = cancellationSignal,
            )
        } catch (_: IllegalArgumentException) {
            failure(WebSearchFailureKind.INVALID_REQUEST)
        } catch (_: RuntimeException) {
            failure(WebSearchFailureKind.CONNECT_FAILURE)
        } finally {
            requestBytes.fill(0)
        }
    }

    private fun execute(
        httpRequest: Request,
        logicalRequest: WebSearchRequest,
        policy: WebSearchPolicy,
        codec: TavilySearchJsonCodec,
        cancellationSignal: CancellationSignal,
    ): WebSearchResult {
        val call =
            try {
                callFactory.newCall(httpRequest)
            } catch (_: RuntimeException) {
                return failure(WebSearchFailureKind.CONNECT_FAILURE)
            }

        return when (val outcome = await(call, cancellationSignal)) {
            is CallOutcome.Completed ->
                outcome.response.use { response ->
                    if (!response.isSuccessful) {
                        return@use mapHttpFailure(response)
                    }
                    val mediaType =
                        response.header("Content-Type")
                            ?.substringBefore(';')
                            ?.trim()
                            ?.lowercase()
                    if (mediaType != JSON_MEDIA_TYPE_VALUE) {
                        return@use failure(WebSearchFailureKind.INVALID_RESPONSE)
                    }

                    val responseBytes =
                        readBoundedResponse(
                            response = response,
                            maxBytes = policy.maxWireBytes,
                        )
                            ?: return@use failure(WebSearchFailureKind.RESPONSE_TOO_LARGE)
                    try {
                        when (
                            val decoded =
                                codec.decode(
                                    bytes = responseBytes,
                                    expectedRequest = logicalRequest,
                                )
                        ) {
                            is TavilySearchDecodeResult.Success -> {
                                val acquiredAt = clock()
                                if (acquiredAt < 0L) {
                                    return@use failure(WebSearchFailureKind.INVALID_RESPONSE)
                                }

                                val safeHits =
                                    decoded.hits.mapNotNull { hit ->
                                        val safeUrl =
                                            HttpsTargetPolicy.parse(hit.url)
                                                ?: return@mapNotNull null
                                        try {
                                            hit.copy(url = safeUrl.toString())
                                        } catch (_: IllegalArgumentException) {
                                            null
                                        }
                                    }

                                WebSearchResult.Success(
                                    WebSearchResponse(
                                        query = decoded.query,
                                        hits = safeHits,
                                        providerId = PROVIDER_ID,
                                        providerRequestId = decoded.requestId,
                                        providerUsage = decoded.usage,
                                        acquiredAtEpochMillis = acquiredAt,
                                        policyVersion = policy.policyVersion,
                                        adapterVersion = ADAPTER_VERSION,
                                    )
                                )
                            }

                            TavilySearchDecodeResult.Invalid ->
                                failure(WebSearchFailureKind.INVALID_RESPONSE)

                            TavilySearchDecodeResult.TooLarge ->
                                failure(WebSearchFailureKind.RESPONSE_TOO_LARGE)
                        }
                    } finally {
                        responseBytes.fill(0)
                    }
                }

            is CallOutcome.Failed ->
                mapIoFailure(outcome.error, call)

            CallOutcome.Cancelled ->
                failure(WebSearchFailureKind.CANCELLED)
        }
    }

    private fun await(
        call: Call,
        cancellationSignal: CancellationSignal,
    ): CallOutcome {
        val terminal = AtomicReference<CallOutcome?>(null)
        val latch = CountDownLatch(1)

        try {
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (terminal.compareAndSet(null, CallOutcome.Failed(e))) {
                            latch.countDown()
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        if (terminal.compareAndSet(null, CallOutcome.Completed(response))) {
                            latch.countDown()
                        } else {
                            response.close()
                        }
                    }
                }
            )
        } catch (failure: RuntimeException) {
            return CallOutcome.Failed(
                IOException("Tavily search enqueue failed", failure)
            )
        }

        while (true) {
            if (cancellationSignal.isCancelled()) {
                val previous = terminal.getAndSet(CallOutcome.Cancelled)
                call.cancel()
                if (previous is CallOutcome.Completed) {
                    previous.response.close()
                }
                return CallOutcome.Cancelled
            }

            try {
                if (latch.await(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return terminal.get()
                        ?: CallOutcome.Failed(
                            IOException("Tavily search ended without outcome")
                        )
                }
            } catch (_: InterruptedException) {
                val previous = terminal.getAndSet(CallOutcome.Cancelled)
                call.cancel()
                if (previous is CallOutcome.Completed) {
                    previous.response.close()
                }
                Thread.currentThread().interrupt()
                return CallOutcome.Cancelled
            }
        }
    }

    private fun readBoundedResponse(
        response: Response,
        maxBytes: Int,
    ): ByteArray? {
        val body = response.body
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            return null
        }

        val scratch = ByteArray(maxBytes + 1)
        return try {
            body.byteStream().use { input ->
                var total = 0
                while (total < scratch.size) {
                    val read = input.read(scratch, total, scratch.size - total)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    total += read
                }
                if (total > maxBytes) {
                    null
                } else {
                    scratch.copyOf(total)
                }
            }
        } catch (_: IOException) {
            null
        } finally {
            scratch.fill(0)
        }
    }

    private fun mapHttpFailure(response: Response): WebSearchResult.Failure =
        when (response.code) {
            401, 403 ->
                failure(
                    kind = WebSearchFailureKind.AUTH_REJECTED,
                    statusCode = response.code,
                )

            429 ->
                failure(
                    kind = WebSearchFailureKind.RATE_LIMITED,
                    statusCode = response.code,
                    retryAfterMillis = parseRetryAfterMillis(response),
                )

            432, 433 ->
                failure(
                    kind = WebSearchFailureKind.QUOTA_EXHAUSTED,
                    statusCode = response.code,
                )

            else ->
                failure(
                    kind = WebSearchFailureKind.PROVIDER_FAILURE,
                    statusCode = response.code,
                )
        }

    private fun mapIoFailure(
        error: IOException,
        call: Call,
    ): WebSearchResult.Failure {
        if (call.isCanceled()) {
            return failure(WebSearchFailureKind.CANCELLED)
        }
        val kind =
            when (error) {
                is UnknownHostException -> WebSearchFailureKind.DNS_FAILURE
                is SSLException -> WebSearchFailureKind.TLS_FAILURE
                is SocketTimeoutException,
                is InterruptedIOException -> WebSearchFailureKind.TIMEOUT
                is ConnectException,
                is NoRouteToHostException -> WebSearchFailureKind.CONNECT_FAILURE
                else -> WebSearchFailureKind.CONNECT_FAILURE
            }
        return failure(kind)
    }

    private fun readAuthorizationHeader(): String? {
        val value =
            try {
                secretReader.readValueOrNull(credentialId)
            } catch (_: RuntimeException) {
                return null
            } ?: return null

        val bytes = value.copyBytes()
        return try {
            if (bytes.size !in 1..MAX_CREDENTIAL_BYTES) {
                return null
            }
            val key = strictUtf8(bytes) ?: return null
            if (
                key.isBlank() ||
                key.any { character ->
                    character.code !in VISIBLE_ASCII_MIN..VISIBLE_ASCII_MAX
                }
            ) {
                return null
            }
            "$AUTH_SCHEME $key"
        } finally {
            bytes.fill(0)
        }
    }

    private fun strictUtf8(bytes: ByteArray): String? =
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }

    private fun parseRetryAfterMillis(response: Response): Long? {
        val seconds =
            response.header(RETRY_AFTER_HEADER)
                ?.trim()
                ?.toLongOrNull()
                ?: return null
        if (seconds !in 1..MAX_RETRY_AFTER_SECONDS) {
            return null
        }
        return seconds * 1_000L
    }

    private fun failure(
        kind: WebSearchFailureKind,
        statusCode: Int? = null,
        retryAfterMillis: Long? = null,
    ): WebSearchResult.Failure =
        WebSearchResult.Failure(
            kind = kind,
            statusCode = statusCode,
            retryAfterMillis = retryAfterMillis,
        )

    private sealed interface CallOutcome {
        data class Completed(val response: Response) : CallOutcome
        data class Failed(val error: IOException) : CallOutcome
        data object Cancelled : CallOutcome
    }

    companion object {
        const val SEARCH_URL = "https://api.tavily.com/search"
        const val DEFAULT_CREDENTIAL_ID = "web.tavily.api-key"

        private const val PROVIDER_ID = "tavily"
        private const val ADAPTER_VERSION = "tavily-search-basic-v1"
        private const val MAX_CREDENTIAL_BYTES = 4 * 1024
        private const val MAX_RETRY_AFTER_SECONDS = 24L * 60L * 60L
        private const val CANCELLATION_POLL_MILLIS = 25L
        private const val VISIBLE_ASCII_MIN = 0x21
        private const val VISIBLE_ASCII_MAX = 0x7e
        private const val JSON_MEDIA_TYPE_VALUE = "application/json"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val ACCEPT_HEADER = "Accept"
        private const val RETRY_AFTER_HEADER = "Retry-After"
        private const val AUTH_SCHEME = "Bearer"
        private val JSON_MEDIA_TYPE = JSON_MEDIA_TYPE_VALUE.toMediaType()
    }
}
