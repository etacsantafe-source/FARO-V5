package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.IoExecutionPolicy
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

sealed interface GroqModelCatalogObservationResult {
    data class Success(
        val models: List<GroqObservedModel>,
    ) : GroqModelCatalogObservationResult

    data class HttpFailure(
        val statusCode: Int,
        val retryAfterMillis: Long? = null,
    ) : GroqModelCatalogObservationResult {
        init {
            require(statusCode in 100..599) {
                "HTTP status must be valid"
            }
            require(retryAfterMillis == null || retryAfterMillis > 0L) {
                "retry-after must be positive when present"
            }
        }
    }

    data class TransportFailure(
        val kind: GroqTransportFailureKind,
    ) : GroqModelCatalogObservationResult
}

/**
 * Authenticated, bounded observation of Groq's model catalog.
 *
 * This is provider observation only. It does not persist provider data, mutate FARO
 * configuration, select a replacement model or infer semantic capabilities from model ids.
 */
class GroqModelCatalogObserver internal constructor(
    private val callFactory: Call.Factory,
    private val secretReader: SecretReader,
    private val codec: GroqModelCatalogCodec,
    private val maxResponseBytes: Int,
    private val credentialId: SecretId,
) {
    constructor(
        secretReader: SecretReader,
        ioPolicy: IoExecutionPolicy,
        maxResponseBytes: Int = DEFAULT_MAX_CATALOG_BYTES,
        credentialId: SecretId = SecretId(GroqHttpsTransport.DEFAULT_CREDENTIAL_ID),
    ) : this(
        callFactory =
            OkHttpClient.Builder()
                .connectTimeout(ioPolicy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(ioPolicy.totalTimeoutMillis, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        secretReader = secretReader,
        codec = GroqModelCatalogCodec(maxBytes = maxResponseBytes),
        maxResponseBytes = maxResponseBytes,
        credentialId = credentialId,
    )

    init {
        require(maxResponseBytes in 1..DEFAULT_MAX_CATALOG_BYTES) {
            "Groq model catalog response bound must be positive and bounded"
        }
    }

    fun observe(
        cancellationSignal: CancellationSignal,
    ): GroqModelCatalogObservationResult {
        if (cancellationSignal.isCancelled()) {
            return cancelled()
        }

        val authorization =
            readAuthorizationHeader()
                ?: return GroqModelCatalogObservationResult.TransportFailure(
                    GroqTransportFailureKind.AUTH_MISSING
                )

        val request =
            try {
                Request.Builder()
                    .url(GROQ_MODELS_URL)
                    .header(AUTHORIZATION_HEADER, authorization)
                    .header(ACCEPT_HEADER, JSON_MEDIA_TYPE_VALUE)
                    .get()
                    .build()
            } catch (_: RuntimeException) {
                return GroqModelCatalogObservationResult.TransportFailure(
                    GroqTransportFailureKind.CONNECT
                )
            }

        val call =
            try {
                callFactory.newCall(request)
            } catch (_: RuntimeException) {
                return GroqModelCatalogObservationResult.TransportFailure(
                    GroqTransportFailureKind.CONNECT
                )
            }

        return when (val outcome = await(call, cancellationSignal)) {
            is CallOutcome.Completed ->
                outcome.response.use { response ->
                    if (!response.isSuccessful) {
                        return@use GroqModelCatalogObservationResult.HttpFailure(
                            statusCode = response.code,
                            retryAfterMillis = parseRetryAfterMillis(response),
                        )
                    }

                    val responseBytes =
                        readBoundedResponse(response)
                            ?: return@use GroqModelCatalogObservationResult.TransportFailure(
                                GroqTransportFailureKind.INVALID_RESPONSE
                            )

                    try {
                        when (val decoded = codec.decode(responseBytes)) {
                            is GroqModelCatalogDecodeResult.Success ->
                                GroqModelCatalogObservationResult.Success(
                                    models = decoded.models,
                                )

                            GroqModelCatalogDecodeResult.Invalid,
                            GroqModelCatalogDecodeResult.TooLarge ->
                                GroqModelCatalogObservationResult.TransportFailure(
                                    GroqTransportFailureKind.INVALID_RESPONSE
                                )
                        }
                    } finally {
                        responseBytes.fill(0)
                    }
                }

            is CallOutcome.Failed ->
                mapIOException(outcome.error, call)

            CallOutcome.Cancelled ->
                cancelled()
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
            return CallOutcome.Failed(IOException("Groq model catalog enqueue failed", failure))
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
                            IOException("Groq model catalog call ended without outcome")
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
    ): ByteArray? {
        val body = response.body
        val declaredLength = body.contentLength()
        if (declaredLength > maxResponseBytes) {
            return null
        }

        val scratch = ByteArray(maxResponseBytes + 1)
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

                if (total > maxResponseBytes) {
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

    private fun parseRetryAfterMillis(
        response: Response,
    ): Long? {
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

    private fun mapIOException(
        error: IOException,
        call: Call,
    ): GroqModelCatalogObservationResult {
        if (call.isCanceled()) {
            return cancelled()
        }

        val kind =
            when (error) {
                is UnknownHostException -> GroqTransportFailureKind.DNS
                is SocketTimeoutException,
                is InterruptedIOException -> GroqTransportFailureKind.TIMEOUT
                is ConnectException,
                is NoRouteToHostException -> GroqTransportFailureKind.CONNECT
                else -> GroqTransportFailureKind.CONNECT
            }
        return GroqModelCatalogObservationResult.TransportFailure(kind)
    }

    private fun cancelled(): GroqModelCatalogObservationResult =
        GroqModelCatalogObservationResult.TransportFailure(
            GroqTransportFailureKind.CANCELLED
        )

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
        const val GROQ_MODELS_URL =
            "https://api.groq.com/openai/v1/models"

        private const val DEFAULT_MAX_CATALOG_BYTES = 2 * 1024 * 1024
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
    }
}
