package ar.com.faro.v5.kernel.contracts

/**
 * Human-visible Workspace plan review port. No model, Reality or Apply effect:
 * only PENDING → APPROVED or PENDING/APPROVED → DISCARDED can be requested
 * through this boundary. Plan creation and execution remain separate.
 */
interface ConversationReviewPort {
    suspend fun reviews(conversationId: ConversationId): List<ConversationPlanReviewSnapshot>
    suspend fun decide(
        intent: ConversationPlanReviewIntent,
        idempotencyKey: IdempotencyKey,
    ): ConversationReviewDecision
}

sealed interface ConversationReviewDecision {
    data class Saved(val review: ConversationPlanReviewSnapshot, val replayed: Boolean) :
        ConversationReviewDecision
    data object Rejected : ConversationReviewDecision
    data object StaleOrUnavailable : ConversationReviewDecision
}

/**
 * INTERNAL host capability: never provide to Surface or model providers.
 * A loaded envelope is merely verified immutable data, not execution approval.
 * The exact human APPROVED state is always rechecked by canonical Workspace.
 */
class TrustedApprovedConversationPlan(
    val review: ConversationPlanReviewSnapshot,
    encoded: ByteArray,
) {
    private val bytes = encoded.copyOf()
    fun envelope(): ByteArray = bytes.copyOf()
}

/**
 * Host-only bridge to recover a human-approved plan and reconcile it with the
 * canonical Reality journal/receipt. UI retains only ConversationReviewPort.
 */
interface TrustedConversationApplyReviewPort {
    fun load(context: ConversationPlanReviewContext): TrustedApprovedConversationPlan?
    fun advance(
        intent: ConversationPlanReviewIntent,
        key: IdempotencyKey,
        attempts: ApplyAttemptLookupPort,
        receipts: ExecutionReceiptLookupPort,
    ): ConversationReviewDecision
}
