package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import java.security.MessageDigest

/** F1: Workspace human approval -> authoritative Reality transaction -> Workspace receipt.
 * This coordinator is constructed ONLY by the active-generation host under
 * one MaterialWriteGate. Surface cannot access TrustedConversationApplyReviewPort.
 * Cross-store commits are not atomic: the Reality receipt is re-observed after
 * a crash, and uncertain effects are never blindly replayed.
 */
sealed interface ReviewedConversationApplyResult {
    data class Committed(val receipt: ExecutionReceipt, val review: ConversationPlanReviewSnapshot) :
        ReviewedConversationApplyResult
    data class Denied(val reason: String) : ReviewedConversationApplyResult
    data object RecoveryRequired : ReviewedConversationApplyResult
}

class ReviewedConversationApplyCoordinator(
    private val workspace: TrustedConversationApplyReviewPort,
    private val decode: (ByteArray) -> PreparedPlanView,
    private val retry: ApplyRetryReceiptGate,
    private val preflight: ApplyPreflightGate,
    private val bindings: ApplyBindingResolutionGate,
    private val executor: ApplyTransactionExecutor,
    private val attempts: ApplyAttemptLookupPort,
    private val receipts: ExecutionReceiptLookupPort,
) {
    fun apply(context: ConversationPlanReviewContext): ReviewedConversationApplyResult {
        val loaded = workspace.load(context)
            ?: return ReviewedConversationApplyResult.Denied("approved_context_unavailable")
        val review = loaded.review
        val plan = runCatching { decode(loaded.envelope()) }.getOrElse {
            return ReviewedConversationApplyResult.Denied("verified_plan_decode_failed")
        }
        if (plan.planFingerprint != review.planFingerprint)
            return ReviewedConversationApplyResult.Denied("plan_fingerprint_mismatch")
        // The approval can only be read from canonical Workspace, never supplied
        // by an LLM or by the mutable UI.
        if (review.state !in setOf(ConversationPlanReviewState.APPROVED,
                ConversationPlanReviewState.APPLYING,
                ConversationPlanReviewState.RECOVERING_UNKNOWN))
            return ReviewedConversationApplyResult.Denied("human_approval_unavailable")

        return when (val observed = retry.resolve(plan)) {
            is ApplyRetryResolution.Rejected ->
                ReviewedConversationApplyResult.RecoveryRequired

            is ApplyRetryResolution.AlreadyCommitted -> {
                val identity = ApplyAttemptIdentity(
                    observed.receipt.transactionId, observed.receipt.planFingerprint,
                )
                reconcileCommitted(review, identity, observed.receipt)
            }

            is ApplyRetryResolution.Proceed -> {
                // An already-started attempt with an unknown outcome must be
                // recovered by an independent Reality examination, not re-run.
                if (review.state != ConversationPlanReviewState.APPROVED)
                    return ReviewedConversationApplyResult.RecoveryRequired
                if (preflight.validate(plan, PlanAuthorizationRef(plan.planFingerprint))
                    !is ApplyPreflightResult.Accepted)
                    return ReviewedConversationApplyResult.Denied("preflight_rejected")
                val resolved = bindings.resolve(plan)
                if (resolved !is ApplyBindingResolution.Resolved)
                    return ReviewedConversationApplyResult.Denied("binding_unavailable")

                val start = workspace.advance(
                    ConversationPlanReviewIntent.BeginApply(review.context(), observed.identity),
                    key("begin", review, observed.identity.transactionId),
                    attempts, receipts,
                )
                if (start !is ConversationReviewDecision.Saved ||
                    start.review.state != ConversationPlanReviewState.APPLYING)
                    return ReviewedConversationApplyResult.Denied("start_not_committed")
                val execution = executor.execute(plan, observed.identity, resolved)
                when (execution) {
                    is ApplyTransactionExecution.Completed ->
                        reconcileCommitted(start.review, observed.identity, execution.receipt)
                    is ApplyTransactionExecution.RecoveryRequired -> {
                        val receipt = receipts.find(observed.identity.transactionId)
                        if (receipt != null)
                            reconcileCommitted(start.review, observed.identity, receipt)
                        else markRecovering(start.review, observed.identity)
                    }
                    is ApplyTransactionExecution.Rejected ->
                        // Never lie to the UI about an absent effect: a Reality
                        // no-effect journal reconciliation is still required.
                        markRecovering(start.review, observed.identity)
                }
            }
        }
    }

    private fun reconcileCommitted(
        current: ConversationPlanReviewSnapshot,
        identity: ApplyAttemptIdentity,
        receipt: ExecutionReceipt,
    ): ReviewedConversationApplyResult {
        if (receipt.transactionId != identity.transactionId ||
            receipt.planFingerprint != current.planFingerprint ||
            receipts.find(identity.transactionId) != receipt)
            return ReviewedConversationApplyResult.RecoveryRequired

        val applying = if (current.state == ConversationPlanReviewState.APPROVED) {
            val result = workspace.advance(
                ConversationPlanReviewIntent.BeginApply(current.context(), identity),
                key("begin", current, identity.transactionId), attempts, receipts,
            )
            if (result !is ConversationReviewDecision.Saved)
                return ReviewedConversationApplyResult.RecoveryRequired
            result.review
        } else current

        val committed = workspace.advance(
            ConversationPlanReviewIntent.ObserveCommitted(applying.context(), receipt),
            key("committed", applying, identity.transactionId), attempts, receipts,
        )
        return if (committed is ConversationReviewDecision.Saved &&
            committed.review.state == ConversationPlanReviewState.COMMITTED &&
            committed.review.receipt == receipt)
            ReviewedConversationApplyResult.Committed(receipt, committed.review)
        else ReviewedConversationApplyResult.RecoveryRequired
    }

    private fun markRecovering(
        current: ConversationPlanReviewSnapshot,
        identity: ApplyAttemptIdentity,
    ): ReviewedConversationApplyResult {
        // Safe even if a crash occurred before recording the state; when the
        // receipt becomes visible it will be reconciled on restart.
        workspace.advance(
            ConversationPlanReviewIntent.MarkRecovering(current.context()),
            key("recover", current, identity.transactionId), attempts, receipts,
        )
        return ReviewedConversationApplyResult.RecoveryRequired
    }

    private fun key(
        action: String,
        review: ConversationPlanReviewSnapshot,
        transactionId: TransactionId,
    ): IdempotencyKey {
        val source = listOf(
            action, review.conversationId.value, review.reviewId,
            review.planFingerprint.value, transactionId.value,
        ).joinToString("|").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source).joinToString("") { "%02x".format(it) }
        return IdempotencyKey("review.$action.$digest")
    }
}
