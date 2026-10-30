package ar.com.faro.v5.platform.ai

import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroqChatJsonCodecTest {
    @Test
    fun `request JSON disables provider managed tools and citations`() {
        val codec = GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 4096)

        val encoded =
            assertInstanceOf(
                GroqJsonEncodeResult.Ready::class.java,
                codec.encode(request()),
            )
        val root =
            Json.parseToJsonElement(
                String(encoded.copyBytes(), StandardCharsets.UTF_8)
            ) as JsonObject

        assertEquals("configured-model", (root["model"] as JsonPrimitive).content)
        assertFalse((root["stream"] as JsonPrimitive).boolean)
        assertEquals("none", (root["tool_choice"] as JsonPrimitive).content)
        assertEquals("disabled", (root["citation_options"] as JsonPrimitive).content)
        assertFalse(root.containsKey("tools"))
        assertFalse(root.containsKey("compound_custom"))
        assertFalse(root.containsKey("search"))
        assertFalse(root.containsKey("browser"))

        val messages = root["messages"] as JsonArray
        assertEquals(3, messages.size)
        assertMessage(messages[0] as JsonObject, "system", "system <frozen>")
        assertMessage(messages[1] as JsonObject, "user", "context \"quoted\"")
        assertMessage(messages[2] as JsonObject, "user", "current\nrequest")
    }

    @Test
    fun `successful response decodes first text choice and usage`() {
        val codec = GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 4096)
        val result =
            codec.decode(
                """
                {
                  "id":"chatcmpl-1",
                  "choices":[
                    {"index":0,"message":{"role":"assistant","content":"respuesta"}}
                  ],
                  "usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19},
                  "unknown_future_field":{"kept":"ignored"}
                }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )

        val success =
            assertInstanceOf(
                GroqJsonDecodeResult.Success::class.java,
                result,
            )
        assertEquals("respuesta", success.responseText)
        assertEquals(12L, success.inputTokens)
        assertEquals(7L, success.outputTokens)
    }

    @Test
    fun `usage may be absent but malformed reported usage fails closed`() {
        val codec = GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 4096)

        val noUsage =
            codec.decode(
                """{"choices":[{"message":{"content":"ok"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        val success =
            assertInstanceOf(
                GroqJsonDecodeResult.Success::class.java,
                noUsage,
            )
        assertEquals(null, success.inputTokens)
        assertEquals(null, success.outputTokens)

        val malformed =
            codec.decode(
                """
                {
                  "choices":[{"message":{"content":"ok"}}],
                  "usage":{"prompt_tokens":"12","completion_tokens":-1}
                }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )
        assertSame(GroqJsonDecodeResult.Invalid, malformed)
    }

    @Test
    fun `blank missing malformed and non UTF8 content fail closed`() {
        val codec = GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 4096)

        listOf(
            ByteArray(0),
            "{}".toByteArray(),
            """{"choices":[]}""".toByteArray(),
            """{"choices":[{"message":{"content":"   "}}]}""".toByteArray(),
            byteArrayOf(0xC3.toByte(), 0x28),
        ).forEach { bytes ->
            assertSame(GroqJsonDecodeResult.Invalid, codec.decode(bytes))
        }
    }

    @Test
    fun `request and response byte bounds are enforced before wire use`() {
        val tinyRequestCodec =
            GroqChatJsonCodec(maxRequestBytes = 10, maxResponseBytes = 4096)
        assertSame(
            GroqJsonEncodeResult.TooLarge,
            tinyRequestCodec.encode(request()),
        )

        val tinyResponseCodec =
            GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 8)
        assertSame(
            GroqJsonDecodeResult.TooLarge,
            tinyResponseCodec.decode(
                """{"choices":[{"message":{"content":"ok"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            ),
        )
    }

    @Test
    fun `wire and protocol toString never expose prompt request context or response content`() {
        val message = GroqChatMessage(GroqChatRole.USER, "VERY_PRIVATE_REQUEST")
        val request =
            GroqChatCompletionRequest(
                modelId = "configured-model",
                messages =
                    listOf(
                        GroqChatMessage(GroqChatRole.SYSTEM, "PRIVATE_PROMPT"),
                        message,
                    ),
            )
        val transportSuccess =
            GroqChatTransportResult.Success(
                responseText = "PRIVATE_MODEL_OUTPUT",
                inputTokens = 1,
                outputTokens = 1,
            )
        val codec = GroqChatJsonCodec(maxRequestBytes = 4096, maxResponseBytes = 4096)
        val encoded =
            assertInstanceOf(
                GroqJsonEncodeResult.Ready::class.java,
                codec.encode(request),
            )
        val decoded =
            assertInstanceOf(
                GroqJsonDecodeResult.Success::class.java,
                codec.decode(
                    """{"choices":[{"message":{"content":"PRIVATE_MODEL_OUTPUT"}}]}"""
                        .toByteArray(StandardCharsets.UTF_8)
                ),
            )

        val rendered =
            listOf(
                message.toString(),
                request.toString(),
                transportSuccess.toString(),
                encoded.toString(),
                decoded.toString(),
            ).joinToString("\n")

        listOf(
            "VERY_PRIVATE_REQUEST",
            "PRIVATE_PROMPT",
            "PRIVATE_MODEL_OUTPUT",
        ).forEach { privateText ->
            assertFalse(rendered.contains(privateText))
        }
        assertTrue(rendered.contains("[REDACTED]"))
    }

    private fun request(): GroqChatCompletionRequest =
        GroqChatCompletionRequest(
            modelId = "configured-model",
            messages =
                listOf(
                    GroqChatMessage(
                        role = GroqChatRole.SYSTEM,
                        content = "system <frozen>",
                    ),
                    GroqChatMessage(
                        role = GroqChatRole.USER,
                        content = "context \"quoted\"",
                    ),
                    GroqChatMessage(
                        role = GroqChatRole.USER,
                        content = "current\nrequest",
                    ),
                ),
        )

    private fun assertMessage(
        value: JsonObject,
        role: String,
        content: String,
    ) {
        assertEquals(role, (value["role"] as JsonPrimitive).content)
        assertEquals(content, (value["content"] as JsonPrimitive).content)
    }
}
