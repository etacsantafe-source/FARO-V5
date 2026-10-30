package ar.com.faro.v5.platform.web

import ar.com.faro.v5.evidence.SafeHttpFetcher
import ar.com.faro.v5.evidence.WebFetchFailureKind
import ar.com.faro.v5.evidence.WebFetchMethod
import ar.com.faro.v5.evidence.WebFetchPolicy
import ar.com.faro.v5.evidence.WebFetchRequest
import ar.com.faro.v5.evidence.WebFetchResult
import ar.com.faro.v5.evidence.WebFetchedSource
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.IoExecutionPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpSafeHttpFetcher internal constructor(
    private val callFactory: Call.Factory,
    private val clock: () -> Long,
) : SafeHttpFetcher {
    constructor(
        ioPolicy: IoExecutionPolicy,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        callFactory =
            OkHttpClient.Builder()
                .dns(PolicyEnforcingDns())
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(ioPolicy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(ioPolicy.totalTimeoutMillis, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        clock = clock,
    )

    override fun fetch(
        request: WebFetchRequest,
        policy: WebFetchPolicy,
        cancellationSignal: CancellationSignal,
    ): WebFetchResult {
        if (cancellationSignal.isCancelled()) {
            return failure(WebFetchFailureKind.CANCELLED)
        }

        val initial =
            HttpsTargetPolicy.parse(request.url)
                ?: return failure(WebFetchFailureKind.INVALID_REQUEST)

        var current = initial
        var redirectCount = 0

        while (true) {
            if (cancellationSignal.isCancelled()) {
                return failure(WebFetchFailureKind.CANCELLED)
            }

            val httpRequest =
                buildRequest(
                    target = current,
                    request = request,
                    policy = policy,
                )

            val call =
                try {
                    callFactory.newCall(httpRequest)
                } catch (_: RuntimeException) {
                    return failure(WebFetchFailureKind.CONNECT_FAILURE)
                }

            when (val outcome = await(call, cancellationSignal)) {
                is CallOutcome.Completed -> {
                    val action =
                        outcome.response.use { response ->
                            handleResponse(
                                response = response,
                                call = call,
                                originalRequest = request,
                                current = current,
                                redirectCount = redirectCount,
                                policy = policy,
                                cancellationSignal = cancellationSignal,
                            )
                        }

                    when (action) {
                        is ResponseAction.Complete ->
                            return WebFetchResult.Success(action.source)

                        is ResponseAction.Failed ->
                            return action.failure

                        is ResponseAction.Redirect -> {
                            current = action.target
                            redirectCount += 1
                        }
                    }
                }

                is CallOutcome.Failed ->
                    return mapIoFailure(outcome.error, call)

                CallOutcome.Cancelled ->
                    return failure(WebFetchFailureKind.CANCELLED)
            }
        }
    }

    private fun buildRequest(
        target: HttpUrl,
        request: WebFetchRequest,
        policy: WebFetchPolicy,
    ): Request {
        val builder =
            Request.Builder()
                .url(target)
                .header("Accept", policy.allowedMediaTypes.sorted().joinToString(", "))
                // Explicit header disables OkHttp transparent decompression. FARO applies
                // both wire and decoded bounds itself.
                .header("Accept-Encoding", "gzip")

        return when (request.method) {
            WebFetchMethod.GET -> builder.get().build()
            WebFetchMethod.HEAD -> builder.head().build()
        }
    }

    private fun handleResponse(
        response: Response,
        call: Call,
        originalRequest: WebFetchRequest,
        current: HttpUrl,
        redirectCount: Int,
        policy: WebFetchPolicy,
        cancellationSignal: CancellationSignal,
    ): ResponseAction {
        if (cancellationSignal.isCancelled()) {
            call.cancel()
            return ResponseAction.Failed(
                failure(WebFetchFailureKind.CANCELLED)
            )
        }

        if (response.code in REDIRECT_CODES) {
            if (redirectCount >= policy.maxRedirects) {
                return ResponseAction.Failed(
                    failure(WebFetchFailureKind.TOO_MANY_REDIRECTS)
                )
            }
            val location =
                response.header("Location")
                    ?: return ResponseAction.Failed(
                        failure(WebFetchFailureKind.REDIRECT_BLOCKED)
                    )
            val next =
                HttpsTargetPolicy.resolveRedirect(
                    current = current,
                    location = location,
                    redirectCount = redirectCount,
                    maxRedirects = policy.maxRedirects,
                )
                    ?: return ResponseAction.Failed(
                        failure(WebFetchFailureKind.REDIRECT_BLOCKED)
                    )
            return ResponseAction.Redirect(next)
        }

        if (response.code !in 200..299) {
            return ResponseAction.Failed(
                failure(
                    kind = WebFetchFailureKind.HTTP_STATUS,
                    statusCode = response.code,
                    retryAfterMillis = parseRetryAfterMillis(response),
                )
            )
        }

        val mediaType =
            response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }

        if (
            mediaType != null &&
            mediaType !in policy.allowedMediaTypes
        ) {
            return ResponseAction.Failed(
                failure(WebFetchFailureKind.CONTENT_TYPE_REJECTED)
            )
        }

        if (originalRequest.method == WebFetchMethod.HEAD) {
            val acquiredAt = clock()
            if (acquiredAt < 0L) {
                return ResponseAction.Failed(
                    failure(WebFetchFailureKind.INVALID_RESPONSE)
                )
            }
            return ResponseAction.Complete(
                WebFetchedSource(
                    requestedUrl = originalRequest.url,
                    finalUrl = current.toString(),
                    method = originalRequest.method,
                    statusCode = response.code,
                    mediaType = mediaType,
                    redirectCount = redirectCount,
                    acquiredAtEpochMillis = acquiredAt,
                    contentSha256Hex = null,
                    body = null,
                    policyVersion = policy.policyVersion,
                    fetcherVersion = policy.fetcherVersion,
                )
            )
        }

        if (mediaType == null) {
            return ResponseAction.Failed(
                failure(WebFetchFailureKind.CONTENT_TYPE_REJECTED)
            )
        }

        val encoding =
            response.header("Content-Encoding")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: "identity"

        if (encoding != "identity" && encoding != "gzip") {
            return ResponseAction.Failed(
                failure(WebFetchFailureKind.CONTENT_ENCODING_REJECTED)
            )
        }

        val wire =
            when (
                val read =
                    readBounded(
                        input = response.body.byteStream(),
                        maxBytes = policy.maxWireBytes,
                        call = call,
                        cancellationSignal = cancellationSignal,
                    )
            ) {
                is ReadResult.Bytes -> read.bytes
                ReadResult.Cancelled ->
                    return ResponseAction.Failed(
                        failure(WebFetchFailureKind.CANCELLED)
                    )
                ReadResult.TooLarge ->
                    return ResponseAction.Failed(
                        failure(WebFetchFailureKind.RESPONSE_TOO_LARGE)
                    )
                is ReadResult.Failed ->
                    return ResponseAction.Failed(
                        mapBodyIoFailure(read.error, call)
                    )
            }

        val decoded =
            try {
                when (encoding) {
                    "identity" -> {
                        if (wire.size > policy.maxDecodedBytes) {
                            return ResponseAction.Failed(
                                failure(WebFetchFailureKind.RESPONSE_TOO_LARGE)
                            )
                        }
                        wire.copyOf()
                    }

                    "gzip" ->
                        when (
                            val read =
                                try {
                                    GZIPInputStream(ByteArrayInputStream(wire)).use { gzip ->
                                        readBounded(
                                            input = gzip,
                                            maxBytes = policy.maxDecodedBytes,
                                            call = call,
                                            cancellationSignal = cancellationSignal,
                                        )
                                    }
                                } catch (failure: IOException) {
                                    ReadResult.Failed(failure)
                                }
                        ) {
                            is ReadResult.Bytes -> read.bytes
                            ReadResult.Cancelled ->
                                return ResponseAction.Failed(
                                    failure(WebFetchFailureKind.CANCELLED)
                                )
                            ReadResult.TooLarge ->
                                return ResponseAction.Failed(
                                    failure(WebFetchFailureKind.RESPONSE_TOO_LARGE)
                                )
                            is ReadResult.Failed ->
                                return ResponseAction.Failed(
                                    failure(WebFetchFailureKind.INVALID_RESPONSE)
                                )
                        }

                    else -> error("validated encoding")
                }
            } finally {
                wire.fill(0)
            }

        return try {
            if (cancellationSignal.isCancelled()) {
                return ResponseAction.Failed(
                    failure(WebFetchFailureKind.CANCELLED)
                )
            }

            val acquiredAt = clock()
            if (acquiredAt < 0L) {
                return ResponseAction.Failed(
                    failure(WebFetchFailureKind.INVALID_RESPONSE)
                )
            }

            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(decoded)
                    .joinToString("") { byte -> "%02x".format(byte) }
            val body =
                CanonicalPayload.fromEncoded(
                    formatId = mediaType,
                    bytes = decoded,
                )

            ResponseAction.Complete(
                WebFetchedSource(
                    requestedUrl = originalRequest.url,
                    finalUrl = current.toString(),
                    method = originalRequest.method,
                    statusCode = response.code,
                    mediaType = mediaType,
                    redirectCount = redirectCount,
                    acquiredAtEpochMillis = acquiredAt,
                    contentSha256Hex = digest,
                    body = body,
                    policyVersion = policy.policyVersion,
                    fetcherVersion = policy.fetcherVersion,
                )
            )
        } catch (_: RuntimeException) {
            ResponseAction.Failed(
                failure(WebFetchFailureKind.INVALID_RESPONSE)
            )
        } finally {
            decoded.fill(0)
        }
    }

    private fun readBounded(
        input: InputStream,
        maxBytes: Int,
        call: Call,
        cancellationSignal: CancellationSignal,
    ): ReadResult {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        return try {
            while (true) {
                if (cancellationSignal.isCancelled()) {
                    call.cancel()
                    return ReadResult.Cancelled
                }
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                if (read == 0) {
                    continue
                }
                if (output.size().toLong() + read.toLong() > maxBytes.toLong()) {
                    return ReadResult.TooLarge
                }
                output.write(buffer, 0, read)
            }
            if (cancellationSignal.isCancelled()) {
                call.cancel()
                ReadResult.Cancelled
            } else {
                ReadResult.Bytes(output.toByteArray())
            }
        } catch (failure: IOException) {
            if (call.isCanceled() || cancellationSignal.isCancelled()) {
                ReadResult.Cancelled
            } else {
                ReadResult.Failed(failure)
            }
        } finally {
            buffer.fill(0)
            output.reset()
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
                IOException("safe web enqueue failed", failure)
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
                            IOException("safe web call ended without outcome")
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

    private fun mapIoFailure(
        error: IOException,
        call: Call,
    ): WebFetchResult.Failure {
        if (call.isCanceled()) {
            return failure(WebFetchFailureKind.CANCELLED)
        }
        return mapBodyIoFailure(error, call)
    }

    private fun mapBodyIoFailure(
        error: IOException,
        call: Call,
    ): WebFetchResult.Failure {
        if (call.isCanceled()) {
            return failure(WebFetchFailureKind.CANCELLED)
        }
        if (error.hasCause<BlockedDestinationException>()) {
            return failure(WebFetchFailureKind.DESTINATION_BLOCKED)
        }
        val kind =
            when (error) {
                is UnknownHostException -> WebFetchFailureKind.DNS_FAILURE
                is SSLException -> WebFetchFailureKind.TLS_FAILURE
                is SocketTimeoutException,
                is InterruptedIOException -> WebFetchFailureKind.TIMEOUT
                is ProtocolException -> WebFetchFailureKind.INVALID_RESPONSE
                is ConnectException,
                is NoRouteToHostException -> WebFetchFailureKind.CONNECT_FAILURE
                else -> WebFetchFailureKind.CONNECT_FAILURE
            }
        return failure(kind)
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun parseRetryAfterMillis(
        response: Response,
    ): Long? {
        val seconds =
            response.header("Retry-After")
                ?.trim()
                ?.toLongOrNull()
                ?: return null
        if (seconds !in 1..MAX_RETRY_AFTER_SECONDS) {
            return null
        }
        return seconds * 1_000L
    }

    private fun failure(
        kind: WebFetchFailureKind,
        statusCode: Int? = null,
        retryAfterMillis: Long? = null,
    ): WebFetchResult.Failure =
        WebFetchResult.Failure(
            kind = kind,
            statusCode = statusCode,
            retryAfterMillis = retryAfterMillis,
        )

    private sealed interface ResponseAction {
        data class Complete(
            val source: WebFetchedSource,
        ) : ResponseAction

        data class Redirect(
            val target: HttpUrl,
        ) : ResponseAction

        data class Failed(
            val failure: WebFetchResult.Failure,
        ) : ResponseAction
    }

    private sealed interface ReadResult {
        data class Bytes(
            val bytes: ByteArray,
        ) : ReadResult

        data class Failed(
            val error: IOException,
        ) : ReadResult

        data object TooLarge : ReadResult
        data object Cancelled : ReadResult
    }

    private sealed interface CallOutcome {
        data class Completed(
            val response: Response,
        ) : CallOutcome

        data class Failed(
            val error: IOException,
        ) : CallOutcome

        data object Cancelled : CallOutcome
    }

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val BUFFER_SIZE = 8192
        private const val CANCELLATION_POLL_MILLIS = 25L
        private const val MAX_RETRY_AFTER_SECONDS = 24L * 60L * 60L
    }
}
