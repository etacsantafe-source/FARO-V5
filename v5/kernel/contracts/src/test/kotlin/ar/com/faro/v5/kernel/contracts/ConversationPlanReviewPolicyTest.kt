package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConversationPlanReviewPolicyTest {
    private fun pending(chat: String = "chat-a", token: String = "gen-a") =
        ConversationPlanReviewSnapshot(
            ConversationId(chat), "review-1", token, 0, PlanFingerprint("plan-a"),
            "Agregar gasto de luz: $100. Revisar antes de aplicar.",
        )

    private fun evaluate(
        snapshot: ConversationPlanReviewSnapshot,
        intent: ConversationPlanReviewIntent,
    ) = ConversationPlanReviewPolicy.evaluate(snapshot, intent)

    private fun accepted(
        snapshot: ConversationPlanReviewSnapshot,
        intent: ConversationPlanReviewIntent,
    ) = (evaluate(snapshot, intent) as ConversationPlanReviewTransition.Accepted).snapshot

    private fun approved(): ConversationPlanReviewSnapshot =
        pending().let { accepted(it, ConversationPlanReviewIntent.Approve(it.context())) }

    private fun applying(): ConversationPlanReviewSnapshot = approved().let {
        accepted(it, ConversationPlanReviewIntent.BeginApply(
            it.context(), ApplyAttemptIdentity(TransactionId("transaction-a"), it.planFingerprint),
        ))
    }

    private fun rejected(reason: ConversationPlanReviewRejection) =
        ConversationPlanReviewTransition.Rejected(reason)

    @Test fun pendingIsNotImplicitAuthorizationOrExecution() {
        val draft = pending()
        assertEquals(ConversationPlanReviewState.PENDING, draft.state)
        assertNull(draft.transactionId)
        assertNull(draft.receipt)
    }

    @Test fun approvalIsSeparateFromApplyAndIncrementsRevision() {
        val reviewed = approved()
        assertEquals(ConversationPlanReviewState.APPROVED, reviewed.state)
        assertEquals(1, reviewed.revision)
        assertNull(reviewed.transactionId)
    }

    @Test fun wrongConversationFailsClosedEvenWithSameFingerprint() {
        val p = pending()
        assertEquals(rejected(ConversationPlanReviewRejection.WRONG_CONVERSATION),
            evaluate(p, ConversationPlanReviewIntent.Approve(p.context().copy(
                conversationId = ConversationId("chat-b")))))
    }

    @Test fun wrongReviewIdentityFailsClosed() {
        val p = pending()
        assertEquals(rejected(ConversationPlanReviewRejection.WRONG_REVIEW),
            evaluate(p, ConversationPlanReviewIntent.Approve(p.context().copy(
                reviewId = "review-other"))))
    }

    @Test fun restoredGenerationCannotReuseOldApproval() {
        val p = pending(token = "restored-generation")
        assertEquals(rejected(ConversationPlanReviewRejection.WRONG_GENERATION),
            evaluate(p, ConversationPlanReviewIntent.Approve(p.context().copy(
                authorityToken = "old-generation"))))
    }

    @Test fun modifiedPlanCannotReuseApprovalOfPreviousPlan() {
        val p = pending()
        assertEquals(rejected(ConversationPlanReviewRejection.WRONG_FINGERPRINT),
            evaluate(p, ConversationPlanReviewIntent.Approve(p.context().copy(
                planFingerprint = PlanFingerprint("plan-edited")))))
    }

    @Test fun staleRevisionCannotAuthorizeSecondAttempt() {
        val approved = approved()
        assertEquals(rejected(ConversationPlanReviewRejection.STALE_REVISION),
            evaluate(approved, ConversationPlanReviewIntent.BeginApply(
                approved.context().copy(expectedRevision = 0),
                ApplyAttemptIdentity(TransactionId("tx"), approved.planFingerprint))))
    }

    @Test fun applyingBeforeHumanApprovalIsForbidden() {
        val p = pending()
        assertEquals(rejected(ConversationPlanReviewRejection.INVALID_STATE),
            evaluate(p, ConversationPlanReviewIntent.BeginApply(
                p.context(), ApplyAttemptIdentity(TransactionId("tx"), p.planFingerprint))))
    }

    @Test fun wrongAttemptFingerprintRejectedAfterApproval() {
        val a = approved()
        assertEquals(rejected(ConversationPlanReviewRejection.IDENTITY_MISMATCH),
            evaluate(a, ConversationPlanReviewIntent.BeginApply(
                a.context(), ApplyAttemptIdentity(TransactionId("tx"), PlanFingerprint("other")))))
    }

    @Test fun receiptMustMatchBothTransactionAndPlan() {
        val a = applying()
        assertEquals(rejected(ConversationPlanReviewRejection.RECEIPT_MISMATCH),
            evaluate(a, ConversationPlanReviewIntent.ObserveCommitted(
                a.context(), ExecutionReceipt(ReceiptId("r"), TransactionId("other"), a.planFingerprint))))
        assertEquals(rejected(ConversationPlanReviewRejection.RECEIPT_MISMATCH),
            evaluate(a, ConversationPlanReviewIntent.ObserveCommitted(
                a.context(), ExecutionReceipt(ReceiptId("r"), a.transactionId!!, PlanFingerprint("other")))))
    }

    @Test fun verifiedReceiptIsTheOnlyPathToCommitted() {
        val a = applying()
        val receipt = ExecutionReceipt(ReceiptId("r"), a.transactionId!!, a.planFingerprint)
        val committed = accepted(a, ConversationPlanReviewIntent.ObserveCommitted(a.context(), receipt))
        assertEquals(ConversationPlanReviewState.COMMITTED, committed.state)
        assertEquals(receipt, committed.receipt)
        assertEquals(3, committed.revision)
    }

    @Test fun uncertainOutcomeNeverTurnsIntoSuccessOrDiscards() {
        val a = applying()
        val recovering = accepted(a, ConversationPlanReviewIntent.MarkRecovering(a.context()))
        assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN, recovering.state)
        assertNull(recovering.receipt)
        assertEquals(rejected(ConversationPlanReviewRejection.INVALID_STATE),
            evaluate(recovering, ConversationPlanReviewIntent.Discard(recovering.context())))
    }

    @Test fun discardPendingCannotLaterBeApproved() {
        val p = pending()
        val discarded = accepted(p, ConversationPlanReviewIntent.Discard(p.context()))
        assertEquals(ConversationPlanReviewState.DISCARDED, discarded.state)
        assertEquals(rejected(ConversationPlanReviewRejection.INVALID_STATE),
            evaluate(discarded, ConversationPlanReviewIntent.Approve(discarded.context())))
    }

    @Test fun approvedCanBeDiscardedButApplyingCannot() {
        val a = approved()
        assertEquals(ConversationPlanReviewState.DISCARDED,
            accepted(a, ConversationPlanReviewIntent.Discard(a.context())).state)
        val applying = applying()
        assertEquals(rejected(ConversationPlanReviewRejection.INVALID_STATE),
            evaluate(applying, ConversationPlanReviewIntent.Discard(applying.context())))
    }

    @Test fun rejectionRequiresCertainNoEffectVerdict() {
        val a = applying()
        val unknown = ExecutionOutcome(
            ExecutionOutcomeState.RECOVERING_UNKNOWN,
            RetryDisposition.REQUIRES_HUMAN_ACTION,
            EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        )
        assertEquals(rejected(ConversationPlanReviewRejection.EFFECT_NOT_REJECTED),
            evaluate(a, ConversationPlanReviewIntent.ObserveRejected(a.context(), unknown)))
        val noEffect = ExecutionOutcome(
            ExecutionOutcomeState.REJECTED, RetryDisposition.NOT_RETRYABLE,
            EffectCertainty.EFFECT_REJECTED,
        )
        assertEquals(ConversationPlanReviewState.REJECTED,
            accepted(a, ConversationPlanReviewIntent.ObserveRejected(a.context(), noEffect)).state)
    }

    @Test fun forgedCommittedProjectionIsRejectedByConstructor() {
        val a = applying()
        assertThrows(IllegalArgumentException::class.java) {
            a.copy(state = ConversationPlanReviewState.COMMITTED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            a.copy(state = ConversationPlanReviewState.COMMITTED,
                receipt = ExecutionReceipt(ReceiptId("x"), TransactionId("other"), a.planFingerprint))
        }
    }

    @Test fun reviewTextAndAuthorityAreValidatedAtIngress() {
        assertThrows(IllegalArgumentException::class.java) { pending().copy(reviewText = "") }
        assertThrows(IllegalArgumentException::class.java) {
            pending().copy(reviewText = "x".repeat(4_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            pending().copy(authorityToken = "gen\ninvalid")
        }
    }
}
