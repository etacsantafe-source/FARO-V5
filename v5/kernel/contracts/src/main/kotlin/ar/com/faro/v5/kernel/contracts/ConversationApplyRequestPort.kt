package ar.com.faro.v5.kernel.contracts

/**
 * Human-initiated request only. Surface supplies a canonical review context,
 * never an ApplyPlan, receipt, permission or executor. The active Android host
 * re-reads Workspace and fences Reality through its existing MaterialWriteGate.
 * Not exposed until complete V2 F1-F4 certification.
 */
interface ConversationApplyRequestPort {
    suspend fun request(context: ConversationPlanReviewContext): ConversationApplyHostOutcome
}
