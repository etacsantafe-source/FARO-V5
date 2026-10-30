package ar.com.faro.v5.kernel.contracts

/**
 * An already committed user turn, not a prompt or an authorization token.
 * The trusted host must revalidate it against canonical Workspace before
 * any model request. No profile, provider, model text or egress grant is
 * accepted from the presentation layer.
 */
data class ConversationAcceptedUserTurn(
    val conversationId: ConversationId,
    val userMessageId: ConversationMessageId,
    val turnId: ConversationTurnId,
    val acceptedAtEpochMillis: Long,
) {
    init { require(acceptedAtEpochMillis >= 0L) }
}

/**
 * Nonblocking notification of an accepted Workspace receipt. The callback
 * is a presentation refresh hint only, never an Apply/Delivery authority.
 * A null binding means V2 assistant generation is not available.
 */
fun interface ConversationAssistantTurnDispatchPort {
    fun dispatchAcceptedTurn(turn: ConversationAcceptedUserTurn, onFinished: () -> Unit): Boolean
}

/**
 * Presentation-only status of a host-owned assistant attempt. It cannot grant
 * model egress, retry an uncertain call or authorize an Apply operation.
 */
enum class ConversationAssistantTurnCompletion {
    COMPLETED, FAILED, OFFLINE, NOT_AUTHORIZED, INVALID_TURN,
    ALREADY_RECORDED, IN_PROGRESS, STALE, RECOVERY_REQUIRED
}

/** Optional typed callback; legacy refresh-only dispatch remains compatible. */
interface ConversationAssistantTurnOutcomePort : ConversationAssistantTurnDispatchPort {
    fun dispatchAcceptedTurnWithOutcome(
        turn: ConversationAcceptedUserTurn,
        onFinished: (ConversationAssistantTurnCompletion) -> Unit,
    ): Boolean
}
