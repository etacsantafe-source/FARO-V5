package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.IoExecutionPolicy
import ar.com.faro.v5.maintenance.UpdateArtifactDownloadPort
import ar.com.faro.v5.maintenance.UpdateManifestFormat
import ar.com.faro.v5.maintenance.UpdateManifestSourcePort
import ar.com.faro.v5.maintenance.UpdateManifestCandidate
import ar.com.faro.v5.platform.web.PolicyEnforcingDns
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Update-only HTTPS transport.
 *
 * It reuses the certified P2 public-network DNS policy, adds an explicit host allowlist and keeps
 * redirects manual. Initial URLs are durable P5 data and therefore cannot contain credential-like
 * query parameters; redirect URLs may carry short-lived provider signatures but are never persisted.
 */
class FaroV5UpdateHttpTransport(
    private val configuration: FaroV5UpdateConfiguration,
    ioPolicy: IoExecutionPolicy =
        IoExecutionPolicy(
            connectTimeoutMillis = 10_000L,
            readTimeoutMillis = 60_000L,
            totalTimeoutMillis = 120_000L,
        ),
) : UpdateManifestSourcePort, UpdateArtifactDownloadPort {
    private val manifestClient =
        client(
            ioPolicy = ioPolicy,
            totalTimeoutMillis = ioPolicy.totalTimeoutMillis,
        )
    private val artifactClient =
        client(
            ioPolicy = ioPolicy,
            totalTimeoutMillis =
                maxOf(
                    ioPolicy.totalTimeoutMillis,
                    ARTIFACT_TOTAL_TIMEOUT_MILLIS,
                ),
        )

    override fun check(): UpdateManifestCandidate {
        val response =
            openResponse(
                client = manifestClient,
                rawUrl = configuration.manifestUrl,
                rejectCredentialQuery = true,
                acceptedMediaTypes = MANIFEST_MEDIA_TYPES,
                maxBytes = UpdateManifestFormat.MAX_MANIFEST_BYTES.toLong(),
            )
        return response.use {
            val bytes =
                readFullyBounded(
                    response = it,
                    maxBytes = UpdateManifestFormat.MAX_MANIFEST_BYTES,
                )
            try {
                val text = decodeUtf8Strict(bytes)
                UpdateManifestFormat.parse(
                    sourceIdentity = configuration.manifestUrl,
                    propertiesText = text,
                )
            } finally {
                bytes.fill(0)
            }
        }
    }

    override fun open(candidate: UpdateManifestCandidate): InputStream {
        require(candidate.sourceIdentity == configuration.manifestUrl) {
            "update candidate source identity does not match configured manifest"
        }
        val response =
            openResponse(
                client = artifactClient,
                rawUrl = candidate.artifactUri,
                rejectCredentialQuery = true,
                acceptedMediaTypes = APK_MEDIA_TYPES,
                maxBytes = candidate.declaredSizeBytes,
            )
        val input = response.body.byteStream()
        return ResponseBoundInputStream(
            delegate = input,
            response = response,
            maxBytes = candidate.declaredSizeBytes,
        )
    }

    private fun openResponse(
        client: OkHttpClient,
        rawUrl: String,
        rejectCredentialQuery: Boolean,
        acceptedMediaTypes: Set<String>,
        maxBytes: Long,
    ): Response {
        require(maxBytes > 0L) {
            "update response bound must be positive"
        }

        configuration.requireAllowedUrl(rawUrl, rejectCredentialQuery)
        var current =
            rawUrl.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("invalid update URL")
        var redirects = 0

        while (true) {
            validateTarget(current, rejectCredentialQuery && redirects == 0)
            val request =
                Request.Builder()
                    .url(current)
                    .header("Accept", acceptedMediaTypes.sorted().joinToString(", "))
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build()

            val response = client.newCall(request).execute()
            if (response.code in REDIRECT_CODES) {
                if (redirects >= MAX_REDIRECTS) {
                    response.close()
                    throw IOException("update redirect limit exceeded")
                }
                val location =
                    response.header("Location")
                        ?: run {
                            response.close()
                            throw IOException("update redirect missing location")
                        }
                val next =
                    current.resolve(location)
                        ?: run {
                            response.close()
                            throw IOException("invalid update redirect")
                        }
                response.close()
                validateTarget(next, rejectCredentialQuery = false)
                current = next
                redirects += 1
                continue
            }

            if (response.code !in 200..299) {
                val status = response.code
                response.close()
                throw IOException("update HTTP status $status")
            }

            val encoding =
                response.header("Content-Encoding")
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
            if (encoding != null && encoding != "identity") {
                response.close()
                throw IOException("update content encoding is not supported")
            }

            val mediaType =
                response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
            if (mediaType == null || mediaType !in acceptedMediaTypes) {
                response.close()
                throw IOException("update content type is not allowed")
            }

            val declaredLength = response.body.contentLength()
            if (declaredLength > maxBytes) {
                response.close()
                throw IOException("update response exceeds declared bound")
            }

            return response
        }
    }

    private fun validateTarget(
        target: HttpUrl,
        rejectCredentialQuery: Boolean,
    ) {
        configuration.requireAllowedUrl(
            rawUrl = target.toString(),
            rejectCredentialQuery = rejectCredentialQuery,
        )
        require(target.username.isEmpty() && target.password.isEmpty()) {
            "update URL user-info is forbidden"
        }
        require(target.fragment == null) {
            "update URL fragment is forbidden"
        }
    }

    private fun readFullyBounded(
        response: Response,
        maxBytes: Int,
    ): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_BYTES))
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            response.body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    if (output.size().toLong() + read.toLong() > maxBytes.toLong()) {
                        throw IOException("update manifest exceeds byte bound")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
            output.reset()
        }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private class ResponseBoundInputStream(
        delegate: InputStream,
        private val response: Response,
        private val maxBytes: Long,
    ) : FilterInputStream(delegate) {
        private var count = 0L
        private var closed = false

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                account(1)
            }
            return value
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) {
                account(read)
            }
            return read
        }

        private fun account(read: Int) {
            count = Math.addExact(count, read.toLong())
            if (count > maxBytes) {
                runCatching { close() }
                throw IOException("update artifact exceeds declared byte bound")
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            try {
                super.close()
            } finally {
                response.close()
            }
        }
    }

    private fun client(
        ioPolicy: IoExecutionPolicy,
        totalTimeoutMillis: Long,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .dns(PolicyEnforcingDns())
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(ioPolicy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(ioPolicy.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(totalTimeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
        const val MAX_REDIRECTS = 5
        const val ARTIFACT_TOTAL_TIMEOUT_MILLIS = 20L * 60L * 1000L
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val MANIFEST_MEDIA_TYPES =
            setOf(
                "text/plain",
                "application/octet-stream",
                "application/x-java-properties",
            )
        val APK_MEDIA_TYPES =
            setOf(
                "application/vnd.android.package-archive",
                "application/octet-stream",
            )
    }
}
