package ar.com.faro.v5.app

import ar.com.faro.v5.control.AiControlSnapshot
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.platform.web.ApprovedChatEndpoint
import ar.com.faro.v5.platform.web.OpenAiCompatibleModelBinding
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.function.Supplier

/** Trusted F1 catalog, not a chat-supplied provider URL or credential. */
data class FaroV5TrustedAssistantComponents(
    val gateway: ModelGateway,
    val profile: Supplier<ModelProfileId>,
    val egressAdmission: Predicate<ConversationMessage>,
)

/**
 * Builds the REAL ComposedModelGateway only from an already-consented local
 * AiControlSnapshot and a Keystore-backed least-authority SecretReader.
 *
 * This is NOT a V2 cutover. All decisions are re-read before reasoning and
 * again for each Workspace fragment; revoked consent fails closed. The
 * provider capability entry is a static trusted catalog, NOT live discovery.
 */
class FaroV5TrustedModelGatewayFactory(
    private val readControl: () -> AiControlSnapshot,
    private val secretReader: SecretReader,
) {
    fun create(): FaroV5TrustedAssistantComponents? {
        val initial = currentControl() ?: return null
        if (!initial.remoteReasoningEnabled || initial.activeProfile != DEFAULT_PROFILE)
            return null
        val credentialId = initial.credentialRefFor(BINDING_ID) ?: return null

        val profile = ModelProfile(
            ref = DEFAULT_PROFILE,
            orderedBindings = listOf(ModelProfileBinding(BINDING_ID, DataEgressTarget.REMOTE_MODEL)),
        )
        val promptRef = PromptRef(PromptId("faro-v5-chat"), PromptVersion(2))
        val prompt = PromptArtifact(
            promptRef,
            CanonicalPayload.fromEncoded(
                "text/plain",
                ("Sos FARO, un asistente personal. Respondé de forma clara y breve en español. " +
                    "No afirmes haber aplicado cambios sin un recibo de ejecución verificable. " +
                    "Solo si el usuario solicita cambiar su nombre declarado, respondé exclusivamente " +
                    "con JSON {\"type\":\"profile.update_declared\",\"display_name\":\"NOMBRE\"}. " +
                    "No incluyas otros campos ni afirmes que el cambio fue ejecutado. " +
                    "En otras consultas respondé normalmente.")
                    .toByteArray(Charsets.UTF_8),
            ),
        )
        val cache = FreshProviderCapabilityCache(
            MonotonicClock { System.nanoTime() },
            TimeUnit.HOURS.toNanos(6),
        )
        cache.replace(listOf(ProviderCapabilitySnapshot(
            BINDING_ID, ModelLifecycle.ACTIVE, setOf(ModelCapability.TEXT), 131072,
        )))
        val liveAuthorized = {
            val latest = currentControl()
            latest != null && latest.remoteReasoningEnabled &&
                latest.activeProfile == DEFAULT_PROFILE &&
                latest.credentialRefFor(BINDING_ID) == credentialId
        }
        // Revocation is checked again immediately before credential access,
        // not only while resolving the gateway policy.
        val guardedSecrets = SecretReader { requested ->
            if (requested != credentialId || !liveAuthorized()) null
            else try { secretReader.readValueOrNull(requested) }
            catch (_: Exception) { null }
        }
        val policy = ModelGatewayPolicyResolver { request ->
            if (!liveAuthorized()) null
            else ModelGatewayPolicySnapshot(
                profileRef = DEFAULT_PROFILE,
                promptRef = promptRef,
                contextualFragments = emptyList(),
                contextBudget = ContextBudget(8192, 65536, 2),
                promptEstimatedTokens = 64,
                requestEstimatedTokens = maxOf(1, (request.input.size + 3) / 4),
                promptSensitivity = EgressSensitivity.PUBLIC,
                requestSensitivity = EgressSensitivity.PRIVATE,
                maxOutputTokens = 2048L,
                pricing = emptyList(),
                monetaryBudget = currentControl()?.budget ?: AiMonetaryBudget.Capped("USD", 0),
                retryTimingConfig = AiRetryTimingConfig(500, 3000, 0, 0),
            )
        }
        val binding = OpenAiCompatibleModelBinding(
            BINDING_ID, ApprovedChatEndpoint.GROQ, GROQ_MODEL_ID,
            guardedSecrets, credentialId,
            IoExecutionPolicy(5000, 20000, 25000), 2048,
            authorizationStillValid = liveAuthorized,
        )
        val gateway = ComposedModelGateway(
            policyResolver = policy,
            profileRegistry = ImmutableModelProfileRegistry.of(listOf(profile)),
            promptRegistry = ImmutablePromptRegistry.of(listOf(prompt)),
            contextAssembler = DeterministicContextAssembler(),
            capabilityCache = cache,
            egressPolicy = AllowlistDataEgressPolicy(
                DataEgressPolicyConfig(
                    remoteAllowed = setOf(EgressSensitivity.PUBLIC, EgressSensitivity.PRIVATE),
                    onDeviceAllowed = emptySet(),
                )
            ),
            bindingRegistry = ImmutableModelBindingRegistry(listOf(binding)),
            retryTimingPolicy = BoundedExponentialAiRetryTimingPolicy,
            retryJitterSource = AiRetryJitterSource { 0L },
            retryWaitPort = AiRetryWaitPort { _, _ -> AiRetryWaitResult.CANCELLED },
        )
        return FaroV5TrustedAssistantComponents(
            gateway = gateway,
            profile = Supplier { DEFAULT_PROFILE.profileId },
            egressAdmission = Predicate { message ->
                liveAuthorized() && message.text.isNotBlank() &&
                    ((message.role == ConversationMessageRole.USER &&
                        message.state == ConversationMessageState.ACCEPTED) ||
                        (message.role == ConversationMessageRole.FARO &&
                            message.state == ConversationMessageState.COMPLETED))
            },
        )
    }

    private fun currentControl(): AiControlSnapshot? =
        try { readControl() } catch (_: Exception) { null }

    companion object {
        val DEFAULT_PROFILE = ModelProfileRef(
            ModelProfileId("faro-remote-groq"), ModelProfileVersion(1),
        )
        val BINDING_ID = ModelBindingId("groq-primary")
        const val CONTROL_FILENAME = "faro-v5-ai.preferences_pb"
        // Groq officially replaced free-tier Llama 3.3 70B on 2026-08-16.
        // Production replacement: https://console.groq.com/docs/deprecations
        const val GROQ_MODEL_ID = "openai/gpt-oss-120b"
    }
}
