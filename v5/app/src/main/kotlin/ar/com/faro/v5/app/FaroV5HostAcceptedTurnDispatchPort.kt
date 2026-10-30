package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationAcceptedUserTurn
import ar.com.faro.v5.kernel.contracts.ConversationAssistantTurnDispatchPort
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * F1 host-only binding between an acknowledged Composer send and the
 * generation-owned asynchronous assistant worker. The surface can report
 * IDs but cannot select a model profile, grant egress or supply model text.
 * ReasoningSession revalidates the exact accepted user row and egress policy.
 * This binding is dormant until a separately certified V2 host supplies it.
 */
class FaroV5HostAcceptedTurnDispatchPort(
    private val dispatcher: FaroV5HostAssistantTurnDispatcher,
    private val trustedProfileId: Supplier<ModelProfileId>,
    private val generationAdmitted: BooleanSupplier,
) : ConversationAssistantTurnDispatchPort, AutoCloseable {
    override fun dispatchAcceptedTurn(
        turn: ConversationAcceptedUserTurn,
        onFinished: () -> Unit,
    ): Boolean {
        val admitted = try { generationAdmitted.getAsBoolean() }
            catch (_: Exception) { false }
        if (!admitted) return false
        val profile = try { trustedProfileId.get() }
            catch (_: Exception) { return false }
        val identity = listOf(
            turn.conversationId.value, turn.turnId.value, turn.userMessageId.value,
        ).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val request = FaroV5AssistantTurnRequest(
            turn.conversationId,
            turn.userMessageId,
            turn.turnId,
            ConversationMessageId("faro-" + hash),
            profile,
            turn.acceptedAtEpochMillis,
            IdempotencyKey("f1:start:" + hash),
            IdempotencyKey("f1:resolve:" + hash),
        )
        val future = dispatcher.submit(request)
        val immediate = if (future.isDone) {
            try { future.getNow(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED) }
            catch (_: Exception) { FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED }
        } else null
        if (immediate == FaroV5AssistantTurnOutcome.STALE ||
            immediate == FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED) return false
        future.whenComplete { _, _ ->
            try { onFinished() } catch (_: Exception) { /* UI may be disposed */ }
        }
        return true
    }

    override fun close() = dispatcher.close()
}
