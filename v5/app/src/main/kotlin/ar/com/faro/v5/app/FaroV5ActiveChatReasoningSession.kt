package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/**
 * Trusted, generation-scoped F1 seam: an already accepted Workspace user turn
 * becomes a durable PROCESSING FARO message before the neutral ModelGateway runs.
 *
 * This is deliberately NOT a UI port, V2 activation, provider configuration,
 * external effect or Apply authority. The host must supply explicit egress
 * admission; ModelGateway independently enforces routing, context and budget.
 * Recovery never repeats a model request whose PROCESSING row already exists.
 */
data class FaroV5AssistantTurnRequest(
    val conversationId: ConversationId,
    val userMessageId: ConversationMessageId,
    val turnId: ConversationTurnId,
    val faroMessageId: ConversationMessageId,
    val profileId: ModelProfileId,
    val startedAtEpochMillis: Long,
    val startKey: IdempotencyKey,
    val resolveKey: IdempotencyKey,
) {
    init { require(startedAtEpochMillis >= 0L) }
}

enum class FaroV5AssistantTurnOutcome {
    COMPLETED, FAILED, OFFLINE, NOT_AUTHORIZED, INVALID_TURN,
    ALREADY_RECORDED, IN_PROGRESS, STALE, RECOVERY_REQUIRED
}

class FaroV5ActiveChatReasoningSession(
    private val generationId: String,
    private val workspace: ConversationTimelinePort,
    private val gate: MaterialWriteGate,
    private val activeGenerationId: Supplier<String>,
    private val startupReady: BooleanSupplier,
    private val gateway: ModelGateway,
    private val trustedEgressAdmission: (ConversationMessage) -> Boolean,
) {
    init { require(generationId.isNotBlank()) }

    private fun current(): Boolean =
        startupReady.getAsBoolean() && activeGenerationId.get() == generationId

    private fun read(id: ConversationId): ConversationTimelineSnapshot? =
        runBlocking { workspace.readTimeline(id) }

    private fun alreadyStarted(
        snapshot: ConversationTimelineSnapshot,
        turnId: ConversationTurnId,
    ): FaroV5AssistantTurnOutcome? {
        val reply = snapshot.messages.firstOrNull {
            it.role == ConversationMessageRole.FARO && it.turnId == turnId
        } ?: return null
        return if (reply.state == ConversationMessageState.PROCESSING)
            FaroV5AssistantTurnOutcome.IN_PROGRESS
        else FaroV5AssistantTurnOutcome.ALREADY_RECORDED
    }

    fun run(
        request: FaroV5AssistantTurnRequest,
        cancellation: CancellationSignal,
    ): FaroV5AssistantTurnOutcome {
        if (!current()) return FaroV5AssistantTurnOutcome.STALE
        val initial = try { read(request.conversationId) }
            catch (_: Exception) { return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED }
            ?: return FaroV5AssistantTurnOutcome.INVALID_TURN
        if (!current()) return FaroV5AssistantTurnOutcome.STALE
        alreadyStarted(initial, request.turnId)?.let { return it }
        val user = initial.messages.firstOrNull {
            it.id == request.userMessageId &&
                it.turnId == request.turnId &&
                it.role == ConversationMessageRole.USER &&
                it.state == ConversationMessageState.ACCEPTED
        } ?: return FaroV5AssistantTurnOutcome.INVALID_TURN
        // The UI cannot mint model content or grant egress by passing an input payload.
        if (!trustedEgressAdmission(user)) return FaroV5AssistantTurnOutcome.NOT_AUTHORIZED
        if (cancellation.isCancelled()) return FaroV5AssistantTurnOutcome.STALE

        val start = try {
            gate.withMaterialWrite {
                check(current()) { "generation changed before FARO start" }
                val latest = read(request.conversationId)
                    ?: return@withMaterialWrite FaroV5AssistantTurnOutcome.INVALID_TURN
                alreadyStarted(latest, request.turnId)?.let { return@withMaterialWrite it }
                if (latest.authorityToken != initial.authorityToken ||
                    latest.messages.none { it == user }) {
                    return@withMaterialWrite FaroV5AssistantTurnOutcome.STALE
                }
                val processing = ConversationMessage(
                    request.faroMessageId, request.conversationId, request.turnId,
                    ConversationMessageRole.FARO, ConversationMessageState.PROCESSING,
                    "", request.startedAtEpochMillis,
                )
                val written = runBlocking {
                    workspace.writeTimeline(
                        ConversationTimelineWriteRequest(
                            request.conversationId, latest.authorityToken, latest.revision,
                            request.startKey, ConversationTimelineIntent.StartFaro(processing),
                        )
                    )
                }
                check(current()) { "generation changed during FARO start" }
                when (written) {
                    is ConversationTimelineWriteResult.Saved ->
                        if (written.replayed) FaroV5AssistantTurnOutcome.IN_PROGRESS
                        else null
                    is ConversationTimelineWriteResult.Stale -> FaroV5AssistantTurnOutcome.STALE
                    else -> FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                }
            }
        } catch (_: Exception) {
            return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
        }
        if (start != null) return start
        // Never hold the material-write admission gate during a remote model call.
        if (!current() || cancellation.isCancelled())
            return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED

        val result = try {
            gateway.reason(
                ReasoningRequest(
                    requestId = ModelRequestId(
                        "f1:" + generationId + ":" + request.conversationId.value +
                            ":" + request.turnId.value
                    ),
                    profileId = request.profileId,
                    requirements = ModelRequirements(setOf(ModelCapability.TEXT)),
                    input = CanonicalPayload.fromEncoded(
                        "text/plain", user.text.toByteArray(StandardCharsets.UTF_8)
                    ),
                ),
                cancellation,
            )
        } catch (_: Exception) { null }
        if (!current() || cancellation.isCancelled())
            return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED

        val decoded = if (result is ReasoningResult.Success) decodeText(result.output) else null
        val outcome = when {
            decoded != null -> ConversationMessageState.COMPLETED
            result is ReasoningResult.Failure &&
                result.failure.kind == AiFailureKind.OFFLINE -> ConversationMessageState.OFFLINE
            else -> ConversationMessageState.FAILED
        }
        val answer = decoded ?: ""
        // Workspace revision is global. Rebase only the CAS revision after re-reading
        // the exact same turn/authority; never re-run the model on a conflict.
        repeat(3) {
            val settled = try {
                gate.withMaterialWrite {
                    check(current()) { "generation changed before FARO resolution" }
                    val latest = read(request.conversationId)
                        ?: return@withMaterialWrite FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                    if (latest.authorityToken != initial.authorityToken)
                        return@withMaterialWrite FaroV5AssistantTurnOutcome.STALE
                    val reply = latest.messages.firstOrNull {
                        it.role == ConversationMessageRole.FARO && it.turnId == request.turnId
                    } ?: return@withMaterialWrite FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                    if (reply.id != request.faroMessageId)
                        return@withMaterialWrite FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                    if (reply.state != ConversationMessageState.PROCESSING)
                        return@withMaterialWrite FaroV5AssistantTurnOutcome.ALREADY_RECORDED
                    val write = runBlocking {
                        workspace.writeTimeline(
                            ConversationTimelineWriteRequest(
                                request.conversationId, latest.authorityToken, latest.revision,
                                IdempotencyKey(request.resolveKey.value + ":r" + latest.revision),
                                ConversationTimelineIntent.ResolveFaro(
                                    request.faroMessageId, request.turnId, outcome, answer,
                                ),
                            )
                        )
                    }
                    check(current()) { "generation changed during FARO resolution" }
                    when (write) {
                        is ConversationTimelineWriteResult.Saved -> when (outcome) {
                            ConversationMessageState.COMPLETED -> FaroV5AssistantTurnOutcome.COMPLETED
                            ConversationMessageState.OFFLINE -> FaroV5AssistantTurnOutcome.OFFLINE
                            else -> FaroV5AssistantTurnOutcome.FAILED
                        }
                        is ConversationTimelineWriteResult.Stale -> null
                        else -> FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                    }
                }
            } catch (_: Exception) { return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED }
            if (settled != null) return settled
        }
        return FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
    }

    private fun decodeText(payload: CanonicalPayload): String? {
        if (payload.formatId != "text/plain" ||
            payload.size > ConversationTimelinePolicy.MAX_MESSAGE_CHARS * 4) return null
        val bytes = payload.copyBytes()
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
                .takeIf { it.isNotBlank() && it.length <= ConversationTimelinePolicy.MAX_MESSAGE_CHARS }
        } catch (_: Exception) { null }
        finally { bytes.fill(0) }
    }
}
