package ar.com.faro.v5.platform.ai

import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class GroqObservedModel(
    val modelId: String,
    val active: Boolean,
    val contextWindowTokens: Int?,
) {
    init {
        require(modelId.isNotBlank() && modelId.length <= MAX_MODEL_ID_CHARS) {
            "Groq observed model id must be non-blank and bounded"
        }
        require(contextWindowTokens == null || contextWindowTokens > 0) {
            "Groq observed context window must be positive when present"
        }
    }

    companion object {
        private const val MAX_MODEL_ID_CHARS = 256
    }
}

sealed interface GroqModelCatalogDecodeResult {
    data class Success(
        val models: List<GroqObservedModel>,
    ) : GroqModelCatalogDecodeResult

    data object Invalid : GroqModelCatalogDecodeResult
    data object TooLarge : GroqModelCatalogDecodeResult
}

/**
 * Bounded parser for Groq's /openai/v1/models response.
 *
 * The provider catalog is observational only. It can report whether a configured model is
 * present/active and its context window, but FARO never infers semantic capabilities from a
 * provider model id.
 */
class GroqModelCatalogCodec(
    private val maxBytes: Int,
) {
    init {
        require(maxBytes in 1..MAX_CATALOG_BYTES) {
            "Groq model catalog bound must be positive and bounded"
        }
    }

    fun decode(bytes: ByteArray): GroqModelCatalogDecodeResult {
        if (bytes.isEmpty()) {
            return GroqModelCatalogDecodeResult.Invalid
        }
        if (bytes.size > maxBytes) {
            return GroqModelCatalogDecodeResult.TooLarge
        }

        val text = strictUtf8(bytes) ?: return GroqModelCatalogDecodeResult.Invalid
        return try {
            val root =
                Json.parseToJsonElement(text) as? JsonObject
                    ?: return GroqModelCatalogDecodeResult.Invalid
            val data =
                root["data"] as? JsonArray
                    ?: return GroqModelCatalogDecodeResult.Invalid
            if (data.size > MAX_MODELS) {
                return GroqModelCatalogDecodeResult.TooLarge
            }

            val models =
                data.map { item ->
                    val model =
                        item as? JsonObject
                            ?: return GroqModelCatalogDecodeResult.Invalid
                    val idPrimitive =
                        model["id"] as? JsonPrimitive
                            ?: return GroqModelCatalogDecodeResult.Invalid
                    val id =
                        idPrimitive.contentOrNull
                            ?.takeIf {
                                it.isNotBlank() &&
                                    it.length <= MAX_MODEL_ID_CHARS
                            }
                            ?: return GroqModelCatalogDecodeResult.Invalid

                    val activePrimitive =
                        model["active"] as? JsonPrimitive
                            ?: return GroqModelCatalogDecodeResult.Invalid
                    val active =
                        activePrimitive.booleanOrNull
                            ?: return GroqModelCatalogDecodeResult.Invalid

                    val contextPrimitive =
                        model["context_window"] as? JsonPrimitive
                    val context =
                        if (contextPrimitive == null) {
                            null
                        } else {
                            contextPrimitive.intOrNull
                                ?.takeIf { it > 0 }
                                ?: return GroqModelCatalogDecodeResult.Invalid
                        }

                    GroqObservedModel(
                        modelId = id,
                        active = active,
                        contextWindowTokens = context,
                    )
                }

            if (models.map { it.modelId }.distinct().size != models.size) {
                return GroqModelCatalogDecodeResult.Invalid
            }

            GroqModelCatalogDecodeResult.Success(models)
        } catch (_: Exception) {
            GroqModelCatalogDecodeResult.Invalid
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

    companion object {
        private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
        private const val MAX_MODELS = 1024
        private const val MAX_MODEL_ID_CHARS = 256
    }
}

data class GroqBindingCapabilityDeclaration(
    val bindingId: ModelBindingId,
    val modelId: String,
    val capabilities: Set<ModelCapability>,
    val configuredLifecycle: ModelLifecycle = ModelLifecycle.ACTIVE,
) {
    init {
        require(modelId.isNotBlank() && modelId.length <= MAX_MODEL_ID_CHARS) {
            "Groq declared model id must be non-blank and bounded"
        }
        require(capabilities.isNotEmpty()) {
            "Groq binding capability declaration must not be empty"
        }
    }

    companion object {
        private const val MAX_MODEL_ID_CHARS = 256
    }
}

object GroqCapabilitySnapshotResolver {
    fun resolve(
        declaration: GroqBindingCapabilityDeclaration,
        observedModels: List<GroqObservedModel>,
    ): ProviderCapabilitySnapshot {
        require(observedModels.map { it.modelId }.distinct().size == observedModels.size) {
            "Groq observed model ids must be unique"
        }

        val observed = observedModels.firstOrNull { it.modelId == declaration.modelId }
        val lifecycle =
            when {
                declaration.configuredLifecycle != ModelLifecycle.ACTIVE ->
                    declaration.configuredLifecycle
                observed?.active == true ->
                    ModelLifecycle.ACTIVE
                else ->
                    ModelLifecycle.UNAVAILABLE
            }

        return ProviderCapabilitySnapshot(
            bindingId = declaration.bindingId,
            lifecycle = lifecycle,
            capabilities = declaration.capabilities.toSet(),
            contextLimitTokens = observed?.contextWindowTokens,
        )
    }
}
