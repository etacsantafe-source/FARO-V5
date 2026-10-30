package ar.com.faro.v5.platform.web

import ar.com.faro.v5.evidence.WebProviderUsage
import ar.com.faro.v5.evidence.WebSearchHit
import ar.com.faro.v5.evidence.WebSearchRequest
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal sealed interface TavilySearchEncodeResult {
    class Ready internal constructor(
        bytes: ByteArray,
    ) : TavilySearchEncodeResult {
        private val snapshot = bytes.copyOf()

        val size: Int
            get() = snapshot.size

        fun copyBytes(): ByteArray = snapshot.copyOf()

        override fun toString(): String =
            "TavilySearchEncodeResult.Ready(size=$size, content=[REDACTED])"
    }

    data object TooLarge : TavilySearchEncodeResult
}

internal sealed interface TavilySearchDecodeResult {
    data class Success(
        val query: String,
        val hits: List<WebSearchHit>,
        val requestId: String?,
        val usage: WebProviderUsage?,
    ) : TavilySearchDecodeResult

    data object Invalid : TavilySearchDecodeResult
    data object TooLarge : TavilySearchDecodeResult
}

internal class TavilySearchJsonCodec(
    private val maxRequestBytes: Int,
    private val maxResponseBytes: Int,
) {
    init {
        require(maxRequestBytes in 1..MAX_WIRE_BYTES) {
            "Tavily request bound must be positive and bounded"
        }
        require(maxResponseBytes in 1..MAX_WIRE_BYTES) {
            "Tavily response bound must be positive and bounded"
        }
    }

    fun encode(
        request: WebSearchRequest,
    ): TavilySearchEncodeResult {
        val root =
            buildJsonObject {
                put("query", JsonPrimitive(request.query))
                put("max_results", JsonPrimitive(request.maxResults))
                put("search_depth", JsonPrimitive("basic"))
                put("topic", JsonPrimitive("general"))
                put("include_answer", JsonPrimitive(false))
                put("include_raw_content", JsonPrimitive(false))
                put("include_images", JsonPrimitive(false))
                put("include_image_descriptions", JsonPrimitive(false))
                put("include_favicon", JsonPrimitive(false))
                put("auto_parameters", JsonPrimitive(false))
                put("include_usage", JsonPrimitive(true))
            }

        val encoded = root.toString().toByteArray(StandardCharsets.UTF_8)
        return if (encoded.size <= maxRequestBytes) {
            val ready = TavilySearchEncodeResult.Ready(encoded)
            encoded.fill(0)
            ready
        } else {
            encoded.fill(0)
            TavilySearchEncodeResult.TooLarge
        }
    }

    fun decode(
        bytes: ByteArray,
        expectedRequest: WebSearchRequest,
    ): TavilySearchDecodeResult {
        if (bytes.isEmpty()) {
            return TavilySearchDecodeResult.Invalid
        }
        if (bytes.size > maxResponseBytes) {
            return TavilySearchDecodeResult.TooLarge
        }

        val text = strictUtf8(bytes) ?: return TavilySearchDecodeResult.Invalid
        return try {
            val root =
                Json.parseToJsonElement(text) as? JsonObject
                    ?: return TavilySearchDecodeResult.Invalid

            val query =
                root.string("query")
                    ?.takeIf { it == expectedRequest.query }
                    ?: return TavilySearchDecodeResult.Invalid

            val results =
                root["results"] as? JsonArray
                    ?: return TavilySearchDecodeResult.Invalid
            if (results.size > expectedRequest.maxResults) {
                return TavilySearchDecodeResult.Invalid
            }

            val hits =
                results.map { element ->
                    val item =
                        element as? JsonObject
                            ?: return TavilySearchDecodeResult.Invalid
                    val title =
                        item.string("title")
                            ?: return TavilySearchDecodeResult.Invalid
                    val url =
                        item.string("url")
                            ?: return TavilySearchDecodeResult.Invalid
                    val snippet =
                        item.string("content")
                            ?: return TavilySearchDecodeResult.Invalid
                    val score =
                        item.optionalScore("score")
                            ?: if (item.containsKey("score")) {
                                return TavilySearchDecodeResult.Invalid
                            } else {
                                null
                            }

                    try {
                        WebSearchHit(
                            title = title,
                            url = url,
                            snippet = snippet,
                            score = score,
                        )
                    } catch (_: IllegalArgumentException) {
                        return TavilySearchDecodeResult.Invalid
                    }
                }

            val requestId =
                root.string("request_id")
                    ?.takeIf {
                        it.isNotBlank() &&
                            it.length <= MAX_PROVIDER_REQUEST_ID_CHARS &&
                            it.none { char -> char.code < 0x20 || char.code == 0x7f }
                    }
                    ?: if (root.containsKey("request_id") && root["request_id"] !is kotlinx.serialization.json.JsonNull) {
                        return TavilySearchDecodeResult.Invalid
                    } else {
                        null
                    }

            val usage =
                root["usage"]?.let { usageElement ->
                    val usageObject =
                        usageElement as? JsonObject
                            ?: return TavilySearchDecodeResult.Invalid
                    val credits =
                        usageObject.nonNegativeLong("credits")
                            ?: return TavilySearchDecodeResult.Invalid
                    WebProviderUsage(
                        unitId = "credit",
                        amount = credits,
                    )
                }

            TavilySearchDecodeResult.Success(
                query = query,
                hits = hits,
                requestId = requestId,
                usage = usage,
            )
        } catch (_: Exception) {
            TavilySearchDecodeResult.Invalid
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull

    private fun JsonObject.optionalScore(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.isString) {
            return null
        }
        return primitive.doubleOrNull
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
    }

    private fun JsonObject.nonNegativeLong(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.isString) {
            return null
        }
        return primitive.longOrNull?.takeIf { it >= 0L }
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

    companion object {
        const val DEFAULT_MAX_REQUEST_BYTES = 64 * 1024
        private const val MAX_WIRE_BYTES = 4 * 1024 * 1024
        private const val MAX_PROVIDER_REQUEST_ID_CHARS = 256
    }
}
