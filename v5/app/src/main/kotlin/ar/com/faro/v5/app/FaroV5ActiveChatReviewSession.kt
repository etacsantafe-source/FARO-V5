package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationReviewDecision
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/** Same active Workspace/authority, no Apply capability exported to UI. */
class FaroV5ActiveChatReviewSession(
    private val generationId: String,
    private val workspace: ConversationReviewPort,
    private val gate: MaterialWriteGate,
    private val pointer: Supplier<String>,
    private val ready: BooleanSupplier,
) {
    private fun requireCurrent() {
        check(ready.getAsBoolean() && pointer.get() == generationId) {
            "Review is stale or recovery is incomplete"
        }
    }

    @JvmField val port: ConversationReviewPort = object : ConversationReviewPort {
        override suspend fun reviews(conversationId: ConversationId): List<ConversationPlanReviewSnapshot> {
            requireCurrent()
            val result = workspace.reviews(conversationId)
            requireCurrent()
            return result
        }
        override suspend fun decide(
            intent: ConversationPlanReviewIntent,
            idempotencyKey: IdempotencyKey,
        ): ConversationReviewDecision {
            check(intent is ConversationPlanReviewIntent.Approve ||
                intent is ConversationPlanReviewIntent.Discard) { "UI cannot execute Apply" }
            return gate.withMaterialWrite {
                requireCurrent()
                val result = runBlocking { workspace.decide(intent, idempotencyKey) }
                requireCurrent()
                result
            }
        }
    }
}
