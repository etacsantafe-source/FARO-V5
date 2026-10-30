package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GroqModelCatalogTest {
    @Test
    fun `catalog parses active state and context window without capability inference`() {
        val codec = GroqModelCatalogCodec(maxBytes = 4096)
        val decoded =
            codec.decode(
                """
                {
                  "object":"list",
                  "data":[
                    {
                      "id":"openai/gpt-oss-120b",
                      "object":"model",
                      "active":true,
                      "context_window":131072
                    }
                  ]
                }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )

        val success =
            assertInstanceOf(
                GroqModelCatalogDecodeResult.Success::class.java,
                decoded,
            )
        assertEquals(
            listOf(
                GroqObservedModel(
                    modelId = "openai/gpt-oss-120b",
                    active = true,
                    contextWindowTokens = 131072,
                )
            ),
            success.models,
        )

        val snapshot =
            GroqCapabilitySnapshotResolver.resolve(
                declaration =
                    GroqBindingCapabilityDeclaration(
                        bindingId = ModelBindingId("groq-text"),
                        modelId = "openai/gpt-oss-120b",
                        capabilities = setOf(ModelCapability.TEXT),
                    ),
                observedModels = success.models,
            )

        assertEquals(ModelLifecycle.ACTIVE, snapshot.lifecycle)
        assertEquals(setOf(ModelCapability.TEXT), snapshot.capabilities)
        assertEquals(131072, snapshot.contextLimitTokens)
    }

    @Test
    fun `missing provider model becomes unavailable rather than substituted`() {
        val snapshot =
            GroqCapabilitySnapshotResolver.resolve(
                declaration =
                    GroqBindingCapabilityDeclaration(
                        bindingId = ModelBindingId("groq-text"),
                        modelId = "configured-model",
                        capabilities = setOf(ModelCapability.TEXT),
                    ),
                observedModels =
                    listOf(
                        GroqObservedModel(
                            modelId = "different-model",
                            active = true,
                            contextWindowTokens = 8192,
                        )
                    ),
            )

        assertEquals(ModelLifecycle.UNAVAILABLE, snapshot.lifecycle)
        assertEquals(setOf(ModelCapability.TEXT), snapshot.capabilities)
        assertEquals(null, snapshot.contextLimitTokens)
    }

    @Test
    fun `provider inactive cannot become active through declaration`() {
        val snapshot =
            GroqCapabilitySnapshotResolver.resolve(
                declaration =
                    GroqBindingCapabilityDeclaration(
                        bindingId = ModelBindingId("groq-text"),
                        modelId = "configured-model",
                        capabilities = setOf(ModelCapability.TEXT),
                    ),
                observedModels =
                    listOf(
                        GroqObservedModel(
                            modelId = "configured-model",
                            active = false,
                            contextWindowTokens = 32768,
                        )
                    ),
            )

        assertEquals(ModelLifecycle.UNAVAILABLE, snapshot.lifecycle)
        assertEquals(32768, snapshot.contextLimitTokens)
    }

    @Test
    fun `configured deprecation wins even if provider still reports active`() {
        val snapshot =
            GroqCapabilitySnapshotResolver.resolve(
                declaration =
                    GroqBindingCapabilityDeclaration(
                        bindingId = ModelBindingId("groq-text"),
                        modelId = "configured-model",
                        capabilities = setOf(ModelCapability.TEXT),
                        configuredLifecycle = ModelLifecycle.DEPRECATED,
                    ),
                observedModels =
                    listOf(
                        GroqObservedModel(
                            modelId = "configured-model",
                            active = true,
                            contextWindowTokens = 32768,
                        )
                    ),
            )

        assertEquals(ModelLifecycle.DEPRECATED, snapshot.lifecycle)
    }

    @Test
    fun `unknown context remains unknown instead of inventing a provider limit`() {
        val decoded =
            GroqModelCatalogCodec(maxBytes = 4096).decode(
                """
                {
                  "data":[
                    {"id":"configured-model","active":true}
                  ]
                }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )

        val success =
            assertInstanceOf(
                GroqModelCatalogDecodeResult.Success::class.java,
                decoded,
            )
        val snapshot =
            GroqCapabilitySnapshotResolver.resolve(
                declaration =
                    GroqBindingCapabilityDeclaration(
                        bindingId = ModelBindingId("groq-text"),
                        modelId = "configured-model",
                        capabilities = setOf(ModelCapability.TEXT),
                    ),
                observedModels = success.models,
            )

        assertEquals(ModelLifecycle.ACTIVE, snapshot.lifecycle)
        assertEquals(null, snapshot.contextLimitTokens)
    }

    @Test
    fun `duplicate malformed invalid UTF8 and oversized catalogs fail closed`() {
        val codec = GroqModelCatalogCodec(maxBytes = 256)

        val duplicate =
            """
            {
              "data":[
                {"id":"m","active":true,"context_window":8},
                {"id":"m","active":true,"context_window":8}
              ]
            }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        assertSame(GroqModelCatalogDecodeResult.Invalid, codec.decode(duplicate))

        val malformed =
            """{"data":[{"id":"m","active":"yes","context_window":8}]}"""
                .toByteArray(StandardCharsets.UTF_8)
        assertSame(GroqModelCatalogDecodeResult.Invalid, codec.decode(malformed))

        val invalidUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        assertSame(GroqModelCatalogDecodeResult.Invalid, codec.decode(invalidUtf8))

        val oversized = ByteArray(257) { 'x'.code.toByte() }
        assertSame(GroqModelCatalogDecodeResult.TooLarge, codec.decode(oversized))
    }
}
