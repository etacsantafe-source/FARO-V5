package ar.com.faro.v5.kernel.contracts

/** Pure A4 review policy. A persisted owner MUST supply these snapshots and enforce CAS.
 * This type is NOT proof of durable authorization, a new execution engine, or a receipt issuer.
 */
enum class ConversationPlanReviewState {
    PENDING, APPROVED, APPLYING, RECOVERING_UNKNOWN, REJECTED, COMMITTED, DISCARDED
}

data class ConversationPlanReviewContext(
    val conversationId: ConversationId,
    val reviewId: String,
    val authorityToken: String,
    val expectedRevision: Long,
    val planFingerprint: PlanFingerprint,
) {
    init {
        require(reviewId.isNotBlank() && reviewId.length <= 128 && reviewId.none { it.code < 32 || it.code == 127 })
        require(authorityToken.isNotBlank() && authorityToken.length <= 160 &&
            authorityToken.none { it.code < 32 || it.code == 127 })
        require(expectedRevision >= 0)
    }
}

/** Frozen plan identity, presentation and Reality result observed through a canonical owner.
 * Neither a mutable UI flag nor a restored screen bundle can manufacture this state.
 */
data class ConversationPlanReviewSnapshot(
    val conversationId: ConversationId,
    val reviewId: String,
    val authorityToken: String,
    val revision: Long,
    val planFingerprint: PlanFingerprint,
    val reviewText: String,
    val state: ConversationPlanReviewState = ConversationPlanReviewState.PENDING,
    val transactionId: TransactionId? = null,
    val receipt: ExecutionReceipt? = null,
) {
    init {
        ConversationPlanReviewContext(
            conversationId, reviewId, authorityToken, revision, planFingerprint,
        )
        require(reviewText.isNotBlank() && reviewText.length <= 4_000) {
            "review text must be present and bounded"
        }
        require(reviewText.none { it.code < 32 && it != '\n' && it != '\t' }) {
            "review text contains control characters"
        }
        when (state) {
            ConversationPlanReviewState.PENDING,
            ConversationPlanReviewState.APPROVED,
            ConversationPlanReviewState.DISCARDED ->
                require(transactionId == null && receipt == null)
            ConversationPlanReviewState.APPLYING,
            ConversationPlanReviewState.RECOVERING_UNKNOWN,
            ConversationPlanReviewState.REJECTED ->
                require(transactionId != null && receipt == null)
            ConversationPlanReviewState.COMMITTED ->
                require(transactionId != null && receipt != null &&
                    receipt.transactionId == transactionId &&
                    receipt.planFingerprint == planFingerprint) {
                    "committed review requires matching Reality execution receipt"
                }
        }
    }

    fun context() = ConversationPlanReviewContext(
        conversationId, reviewId, authorityToken, revision, planFingerprint,
    )
}

/** Each user intent carries exact observed conversation, generation, revision and plan. */
sealed interface ConversationPlanReviewIntent {
    val context: ConversationPlanReviewContext

    data class Approve(override val context: ConversationPlanReviewContext) :
        ConversationPlanReviewIntent

    /** Only the trusted Apply orchestration may supply the resolved attempt identity. */
    data class BeginApply(
        override val context: ConversationPlanReviewContext,
        val identity: ApplyAttemptIdentity,
    ) : ConversationPlanReviewIntent

    /** The owner must re-read the journal before resolving an unknown effect. */
    data class MarkRecovering(override val context: ConversationPlanReviewContext) :
        ConversationPlanReviewIntent

    /** Receipt MUST come from the canonical Reality read port, never the UI. */
    data class ObserveCommitted(
        override val context: ConversationPlanReviewContext,
        val receipt: ExecutionReceipt,
    ) : ConversationPlanReviewIntent

    /** Only a canonical no-effect rejection may transition to rejected. */
    data class ObserveRejected(
        override val context: ConversationPlanReviewContext,
        val outcome: ExecutionOutcome,
    ) : ConversationPlanReviewIntent

    data class Discard(override val context: ConversationPlanReviewContext) :
        ConversationPlanReviewIntent
}

enum class ConversationPlanReviewRejection {
    WRONG_CONVERSATION, WRONG_REVIEW, WRONG_GENERATION, WRONG_FINGERPRINT,
    STALE_REVISION, INVALID_STATE, IDENTITY_MISMATCH, RECEIPT_MISMATCH,
    EFFECT_NOT_REJECTED
}

sealed interface ConversationPlanReviewTransition {
    data class Accepted(val snapshot: ConversationPlanReviewSnapshot) : ConversationPlanReviewTransition
    data class Rejected(val reason: ConversationPlanReviewRejection) : ConversationPlanReviewTransition
}

/**
 * Non-authoritative deterministic gate used by a single durable Workspace owner.
 * Reality ApplyPreflightGate, ApplyRetryReceiptGate, and receipt verification
 * remain separate mandatory execution gates; this policy cannot replace them.
 */
object ConversationPlanReviewPolicy {
    fun evaluate(
        current: ConversationPlanReviewSnapshot,
        intent: ConversationPlanReviewIntent,
    ): ConversationPlanReviewTransition {
        val context = intent.context
        fun reject(reason: ConversationPlanReviewRejection) =
            ConversationPlanReviewTransition.Rejected(reason)
        if (context.conversationId != current.conversationId)
            return reject(ConversationPlanReviewRejection.WRONG_CONVERSATION)
        if (context.reviewId != current.reviewId)
            return reject(ConversationPlanReviewRejection.WRONG_REVIEW)
        if (context.authorityToken != current.authorityToken)
            return reject(ConversationPlanReviewRejection.WRONG_GENERATION)
        if (context.planFingerprint != current.planFingerprint)
            return reject(ConversationPlanReviewRejection.WRONG_FINGERPRINT)
        if (context.expectedRevision != current.revision)
            return reject(ConversationPlanReviewRejection.STALE_REVISION)

        val next: ConversationPlanReviewSnapshot = when (intent) {
            is ConversationPlanReviewIntent.Approve -> {
                if (current.state != ConversationPlanReviewState.PENDING)
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                current.copy(state = ConversationPlanReviewState.APPROVED)
            }
            is ConversationPlanReviewIntent.BeginApply -> {
                if (current.state != ConversationPlanReviewState.APPROVED)
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                if (intent.identity.planFingerprint != current.planFingerprint)
                    return reject(ConversationPlanReviewRejection.IDENTITY_MISMATCH)
                current.copy(
                    state = ConversationPlanReviewState.APPLYING,
                    transactionId = intent.identity.transactionId,
                )
            }
            is ConversationPlanReviewIntent.MarkRecovering -> {
                if (current.state != ConversationPlanReviewState.APPLYING)
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                current.copy(state = ConversationPlanReviewState.RECOVERING_UNKNOWN)
            }
            is ConversationPlanReviewIntent.ObserveCommitted -> {
                if (current.state !in setOf(
                        ConversationPlanReviewState.APPLYING,
                        ConversationPlanReviewState.RECOVERING_UNKNOWN,
                    ))
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                if (intent.receipt.transactionId != current.transactionId ||
                    intent.receipt.planFingerprint != current.planFingerprint)
                    return reject(ConversationPlanReviewRejection.RECEIPT_MISMATCH)
                current.copy(
                    state = ConversationPlanReviewState.COMMITTED,
                    receipt = intent.receipt,
                )
            }
            is ConversationPlanReviewIntent.ObserveRejected -> {
                if (current.state !in setOf(
                        ConversationPlanReviewState.APPLYING,
                        ConversationPlanReviewState.RECOVERING_UNKNOWN,
                    ))
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                if (intent.outcome.state != ExecutionOutcomeState.REJECTED ||
                    intent.outcome.effectCertainty != EffectCertainty.EFFECT_REJECTED)
                    return reject(ConversationPlanReviewRejection.EFFECT_NOT_REJECTED)
                current.copy(state = ConversationPlanReviewState.REJECTED)
            }
            is ConversationPlanReviewIntent.Discard -> {
                if (current.state !in setOf(
                        ConversationPlanReviewState.PENDING,
                        ConversationPlanReviewState.APPROVED,
                    ))
                    return reject(ConversationPlanReviewRejection.INVALID_STATE)
                current.copy(state = ConversationPlanReviewState.DISCARDED)
            }
        }
        return ConversationPlanReviewTransition.Accepted(next.copy(revision = current.revision + 1))
    }
}
