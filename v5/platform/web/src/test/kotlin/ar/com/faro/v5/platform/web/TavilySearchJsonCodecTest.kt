package ar.com.faro.v5.platform.web

import ar.com.faro.v5.evidence.WebSearchRequest
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TavilySearchJsonCodecTest {
    private val request =
        WebSearchRequest(
            query = "faro architecture",
            maxResults = 2,
            sensitivity = EgressSensitivity.PUBLIC,
        )

    @Test
    fun `encode freezes basic depth and disables costly generated payloads`() {
        val codec =
            TavilySearchJsonCodec(
                maxRequestBytes = 4096,
                maxResponseBytes = 4096,
            )

        val encoded =
            assertInstanceOf(
                TavilySearchEncodeResult.Ready::class.java,
                codec.encode(request),
            )
        val text = encoded.copyBytes().decodeToString()

        assertTrue(text.contains("\"search_depth\":\"basic\""))
        assertTrue(text.contains("\"max_results\":2"))
        assertTrue(text.contains("\"include_answer\":false"))
        assertTrue(text.contains("\"include_raw_content\":false"))
        assertTrue(text.contains("\"include_images\":false"))
        assertTrue(text.contains("\"auto_parameters\":false"))
        assertTrue(text.contains("\"include_usage\":true"))
    }

    @Test
    fun `decode accepts bounded source hits and optional credit usage`() {
        val codec =
            TavilySearchJsonCodec(
                maxRequestBytes = 4096,
                maxResponseBytes = 4096,
            )

        val result =
            codec.decode(
                """
                {
                  "query":"faro architecture",
                  "results":[
                    {
                      "title":"One",
                      "url":"https://example.com/1",
                      "content":"snippet",
                      "score":0.8
                    }
                  ],
                  "request_id":"opaque-1",
                  "usage":{"credits":1}
                }
                """.trimIndent().encodeToByteArray(),
                request,
            )

        val success =
            assertInstanceOf(
                TavilySearchDecodeResult.Success::class.java,
                result,
            )
        assertEquals(1, success.hits.size)
        assertEquals("credit", success.usage?.unitId)
        assertEquals(1L, success.usage?.amount)
    }

    @Test
    fun `query mismatch excess results and malformed usage fail closed`() {
        val codec =
            TavilySearchJsonCodec(
                maxRequestBytes = 4096,
                maxResponseBytes = 8192,
            )

        val wrongQuery =
            codec.decode(
                """{"query":"different","results":[]}""".encodeToByteArray(),
                request,
            )
        assertEquals(TavilySearchDecodeResult.Invalid, wrongQuery)

        val tooMany =
            codec.decode(
                """
                {
                  "query":"faro architecture",
                  "results":[
                    {"title":"1","url":"https://example.com/1","content":"","score":0.9},
                    {"title":"2","url":"https://example.com/2","content":"","score":0.8},
                    {"title":"3","url":"https://example.com/3","content":"","score":0.7}
                  ]
                }
                """.trimIndent().encodeToByteArray(),
                request,
            )
        assertEquals(TavilySearchDecodeResult.Invalid, tooMany)

        val badUsage =
            codec.decode(
                """
                {
                  "query":"faro architecture",
                  "results":[],
                  "usage":{"credits":-1}
                }
                """.trimIndent().encodeToByteArray(),
                request,
            )
        assertEquals(TavilySearchDecodeResult.Invalid, badUsage)
    }

    @Test
    fun `response wire bound is enforced before JSON parsing`() {
        val codec =
            TavilySearchJsonCodec(
                maxRequestBytes = 4096,
                maxResponseBytes = 32,
            )

        assertEquals(
            TavilySearchDecodeResult.TooLarge,
            codec.decode(ByteArray(33) { 'x'.code.toByte() }, request),
        )
    }
}
