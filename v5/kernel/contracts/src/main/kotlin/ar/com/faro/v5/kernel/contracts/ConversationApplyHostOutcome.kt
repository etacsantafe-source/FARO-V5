package ar.com.faro.v5.kernel.contracts

/**
 * Host-only result of a reviewed, user-requested Apply.
 *
 * This is a neutral reporting contract, NOT authorization, a receipt issuer,
 * an Apply executor or a Surface/model capability. The trusted Kotlin host
 * adapter maps the canonical Reality outcome after the existing gates.
 */
sealed interface ConversationApplyHostOutcome {
    data class Committed(
        val receipt: ExecutionReceipt,
        val review: ConversationPlanReviewSnapshot,
    ) : ConversationApplyHostOutcome

    data class Denied(val reason: String) : ConversationApplyHostOutcome

    data object RecoveryRequired : ConversationApplyHostOutcome
}
