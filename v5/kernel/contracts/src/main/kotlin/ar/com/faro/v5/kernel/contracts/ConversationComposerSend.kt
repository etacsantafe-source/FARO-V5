package ar.com.faro.v5.kernel.contracts

/**
 * An acknowledged Workspace draft can become a user turn only in the SAME
 * authoritative SQLite transaction that clears the draft and stores its receipt.
 * This operation never invokes a model, creates an ApplyPlan or invents a reply.
 */
data class ConversationDraftSendRequest(
    val candidate: ConversationDraftSendCandidate,
    val messageId: ConversationMessageId,
    val turnId: ConversationTurnId,
    val createdAtEpochMillis: Long,
    val idempotencyKey: IdempotencyKey,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(candidate.text.isNotBlank())
        require(candidate.text.length <= ConversationTimelinePolicy.MAX_MESSAGE_CHARS)
    }
}

/** Only the canonical Workspace store implements the atomic send authority. */
interface ConversationComposerPort : ConversationDraftPort {
    suspend fun sendDraft(request: ConversationDraftSendRequest): ConversationTimelineWriteResult
}
