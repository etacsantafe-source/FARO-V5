package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.owner.profile.spi.ProfilePersistencePort
import ar.com.faro.v5.workspace.*
import java.security.MessageDigest
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * F1 trusted, generation-fenced proposal bridge. Model output is untrusted
 * evidence, NEVER a PreparedPlan, an approval, or an Apply authority.
 * Only a narrowly allowlisted operation is prepared from CURRENT Reality
 * dependencies and committed to the actual Workspace review journal.
 *
 * Shipping V2 stays OFF; no UI caller receives this bridge.
 */
class FaroV5ModelReviewBridge(
    private val generationId: String,
    private val workspace: ConversationWorkspaceStore,
    private val profile: ProfilePersistencePort,
    private val gate: MaterialWriteGate,
    private val activeGeneration: Supplier<String>,
    private val recoveryReady: BooleanSupplier,
) {
    init { require(generationId.isNotBlank()) }

    private fun current(): Boolean =
        recoveryReady.getAsBoolean() && activeGeneration.get() == generationId

    /**
     * Returns null for ordinary text, malformed JSON, unsupported operations,
     * stale generations or an untrusted/unfinished turn. No model output can
     * create a review without a matching durable accepted USER and COMPLETED
     * FARO message. Duplicate delivery reuses Workspace's idempotency journal.
     */
    fun propose(conversationId: ConversationId, turnId: ConversationTurnId): WorkspacePlanReviewWriteResult? {
        if (!current()) return null
        return try {
            gate.withMaterialWrite {
                if (!current()) return@withMaterialWrite null
                val timeline = runBlocking { workspace.readTimeline(conversationId) }
                    ?: return@withMaterialWrite null
                val user = timeline.messages.firstOrNull {
                    it.turnId == turnId && it.role == ConversationMessageRole.USER &&
                        it.state == ConversationMessageState.ACCEPTED
                } ?: return@withMaterialWrite null
                val reply = timeline.messages.firstOrNull {
                    it.turnId == turnId && it.role == ConversationMessageRole.FARO &&
                        it.state == ConversationMessageState.COMPLETED
                } ?: return@withMaterialWrite null
                if (user.text.isBlank()) return@withMaterialWrite null
                val displayName = parseProfileProposal(reply.text) ?: return@withMaterialWrite null
                if (!current()) return@withMaterialWrite null

                // No operation descriptor, dependency stamp, fingerprint or
                // idempotency identity may be supplied by the model.
                val identity = stableIdentity(conversationId.value + ":" + turnId.value + ":" + reply.id.value)
                // A repeated completion (including after restart) must never
                // generate a second review with a newer Reality stamp.
                val existing = runBlocking { workspace.reviews(conversationId) }
                    .firstOrNull { it.reviewId == "f1:model:" + identity }
                if (existing != null)
                    return@withMaterialWrite WorkspacePlanReviewWriteResult.Saved(existing, true)
                val descriptor = OperationContractDescriptor(
                    ProfileOperations.UPDATE_DECLARED, ProfileOperations.CAPABILITY_ID,
                    ProfileOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
                    ContractVersion("1"), ContractFingerprint("profile-update-declared-v1"),
                    ContractExposure.BINDABLE,
                )
                val operation = PreparedOperation(
                    descriptor,
                    ProfileDeclaredUpdatePayload.payload(ProfileDeclaredUpdate(displayName)),
                    DependencyStampSet.of(profile.read().dependencyStamp()),
                    IdempotencyKey("f1:op:" + identity),
                )
                val prepared = PreparedPlan.of(
                    ApplyPlanNode(PlanNodeId("profile"), operation, emptySet())
                )
                val envelope = PreparedPlanEnvelopeCodec.encode(prepared)
                if (!current()) return@withMaterialWrite null
                workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                    conversationId = conversationId,
                    reviewId = "f1:model:" + identity,
                    authorityToken = timeline.authorityToken,
                    expectedWorkspaceRevision = timeline.revision,
                    planFingerprint = prepared.planFingerprint,
                    reviewText = "Cambiar nombre declarado a «" + displayName.value + "»",
                    planEnvelope = envelope,
                    idempotencyKey = IdempotencyKey("f1:review:" + identity),
                ))
            }
        } catch (_: Exception) {
            // Fail closed: never create a review from a partial or stale plan.
            null
        }
    }

    private fun parseProfileProposal(text: String): DeclaredDisplayName? {
        if (text.length !in 2..4096) return null
        return try {
            val obj = Json.parseToJsonElement(text) as? JsonObject ?: return null
            if (obj.keys != setOf("type", "display_name")) return null
            val type = obj["type"] as? JsonPrimitive ?: return null
            if (type.contentOrNull != "profile.update_declared") return null
            val value = obj["display_name"] as? JsonPrimitive ?: return null
            if (!value.isString) return null
            val raw = value.contentOrNull ?: return null
            if (raw.any { Character.isISOControl(it) }) return null
            DeclaredDisplayName.of(raw)
        } catch (_: Exception) { null }
    }

    private fun stableIdentity(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
