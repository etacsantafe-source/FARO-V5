package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** P3.A15 neutral adapter: no false success or hidden Apply permission. */
class FaroV5ReviewedApplyHostAdapterTest {
    @Test fun deniedRemainsDenied() {
        assertEquals(
            ConversationApplyHostOutcome.Denied("preflight_rejected"),
            ReviewedConversationApplyResult.Denied("preflight_rejected").toHostOutcome(),
        )
    }

    @Test fun unknownEffectRemainsRecoveryRequired() {
        assertEquals(
            ConversationApplyHostOutcome.RecoveryRequired,
            ReviewedConversationApplyResult.RecoveryRequired.toHostOutcome(),
        )
    }

    @Test fun committedPreservesCanonicalReceiptAndReview() {
        val receipt = ExecutionReceipt(
            ReceiptId("receipt-a"), TransactionId("transaction-a"), PlanFingerprint("plan-a"),
        )
        val review = ConversationPlanReviewSnapshot(
            ConversationId("chat-a"), "review-a", "generation-a", 3,
            receipt.planFingerprint, "Operación revisada y confirmada",
            ConversationPlanReviewState.COMMITTED,
            transactionId = receipt.transactionId, receipt = receipt,
        )
        assertEquals(
            ConversationApplyHostOutcome.Committed(receipt, review),
            ReviewedConversationApplyResult.Committed(receipt, review).toHostOutcome(),
        )
    }
}
