package ar.com.faro.v5.platform.ai

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

sealed interface GroqJsonEncodeResult {
    class Ready internal constructor(
        bytes: ByteArray,
    ) : GroqJsonEncodeResult {
        private val snapshot = bytes.copyOf()

        val size: Int
            get() = snapshot.size

        fun copyBytes(): ByteArray = snapshot.copyOf()

        override fun toString(): String =
            "GroqJsonEncodeResult.Ready(size=$size, content=[REDACTED])"
    }

    data object TooLarge : GroqJsonEncodeResult
}

sealed interface GroqJsonDecodeResult {
    class Success(
        val responseText: String,
        val inputTokens: Long?,
        val outputTokens: Long?,
    ) : GroqJsonDecodeResult {
        override fun toString(): String =
            "GroqJsonDecodeResult.Success(responseText=[REDACTED], inputTokens=$inputTokens, outputTokens=$outputTokens)"
    }

    data object Invalid : GroqJsonDecodeResult
    data object TooLarge : GroqJsonDecodeResult
}

/**
 * Bounded Groq Chat Completions JSON wire codec.
 *
 * Provider-managed tools, browser/search and citations are explicitly disabled in the
 * generated request. The codec never returns raw parse exception messages.
 */
class GroqChatJsonCodec(
    private val maxRequestBytes: Int,
    private val maxResponseBytes: Int,
) {
    init {
        require(maxRequestBytes in 1..MAX_WIRE_BYTES) {
            "Groq request wire bound must be positive and bounded"
        }
        require(maxResponseBytes in 1..MAX_WIRE_BYTES) {
            "Groq response wire bound must be positive and bounded"
        }
    }

    fun encode(
        request: GroqChatCompletionRequest,
    ): GroqJsonEncodeResult {
        val root =
            buildJsonObject {
                put("model", JsonPrimitive(request.modelId))
                put("stream", JsonPrimitive(false))
                put("tool_choice", JsonPrimitive("none"))
                put("citation_options", JsonPrimitive("disabled"))
                put(
                    "messages",
                    buildJsonArray {
                        request.messages.forEach { message ->
                            add(
                                buildJsonObject {
                                    put(
                                        "role",
                                        JsonPrimitive(
                                            when (message.role) {
                                                GroqChatRole.SYSTEM -> "system"
                                                GroqChatRole.USER -> "user"
                                            }
                                        ),
                                    )
                                    put(
                                        "content",
                                        JsonPrimitive(message.content),
                                    )
                                }
                            )
                        }
                    },
                )
            }

        val encoded = root.toString().toByteArray(StandardCharsets.UTF_8)
        return if (encoded.size <= maxRequestBytes) {
            val ready = GroqJsonEncodeResult.Ready(encoded)
            encoded.fill(0)
            ready
        } else {
            encoded.fill(0)
            GroqJsonEncodeResult.TooLarge
        }
    }

    fun decode(
        bytes: ByteArray,
    ): GroqJsonDecodeResult {
        if (bytes.isEmpty()) {
            return GroqJsonDecodeResult.Invalid
        }
        if (bytes.size > maxResponseBytes) {
            return GroqJsonDecodeResult.TooLarge
        }

        val text = strictUtf8(bytes) ?: return GroqJsonDecodeResult.Invalid
        return try {
            val root = Json.parseToJsonElement(text) as? JsonObject
                ?: return GroqJsonDecodeResult.Invalid
            val choices = root["choices"] as? JsonArray
                ?: return GroqJsonDecodeResult.Invalid
            val first = choices.firstOrNull() as? JsonObject
                ?: return GroqJsonDecodeResult.Invalid
            val message = first["message"] as? JsonObject
                ?: return GroqJsonDecodeResult.Invalid
            val responseText =
                (message["content"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return GroqJsonDecodeResult.Invalid

            val usage = root["usage"] as? JsonObject
            val inputTokens = usage?.nonNegativeLong("prompt_tokens")
                ?: if (usage?.containsKey("prompt_tokens") == true) {
                    return GroqJsonDecodeResult.Invalid
                } else {
                    null
                }
            val outputTokens = usage?.nonNegativeLong("completion_tokens")
                ?: if (usage?.containsKey("completion_tokens") == true) {
                    return GroqJsonDecodeResult.Invalid
                } else {
                    null
                }

            GroqJsonDecodeResult.Success(
                responseText = responseText,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
            )
        } catch (_: Exception) {
            GroqJsonDecodeResult.Invalid
        }
    }

    private fun JsonObject.nonNegativeLong(
        key: String,
    ): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.isString) {
            return null
        }
        return primitive.longOrNull?.takeIf { it >= 0L }
    }

    private fun strictUtf8(
        bytes: ByteArray,
    ): String? =
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
        private const val MAX_WIRE_BYTES = 8 * 1024 * 1024
    }
}
