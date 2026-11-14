package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** One opening owns its lease until all of its SQLite resources really close. */
internal class FaroV5DevOpenAdmission(val remote: Boolean, private val releaseLease: () -> Unit) : AutoCloseable {
    val live = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    fun revoke() { live.set(false) }
    override fun close() {
        revoke()
        if (closed.compareAndSet(false, true)) releaseLease()
    }
}

/** Optional DEV composition over the Session's exact graph; never opens or owns another store. */
internal class FaroV5DevAssistantSessionPorts private constructor(
    val reviewPort: ConversationReviewPort,
    val applyRequestPort: ConversationApplyRequestPort,
    val assistantTurnPort: ConversationAssistantTurnDispatchPort?,
    private val dispatch: FaroV5HostAcceptedTurnDispatchPort,
    private val closed: AtomicBoolean,
    val recoveredInterruptedTurns: Int,
) : AutoCloseable {
    override fun close() { if (closed.compareAndSet(false, true)) dispatch.close() }

    companion object {
        fun open(
            graph: FaroV5GenerationStoreGraph,
            gate: MaterialWriteGate,
            current: Supplier<String>,
            ready: BooleanSupplier,
            trusted: FaroV5TrustedAssistantComponents?,
        ): FaroV5DevAssistantSessionPorts {
            check(ready.getAsBoolean())
            val workspace = checkNotNull(graph.workspace)
            val closed = AtomicBoolean(false)
            val admitted = BooleanSupplier { !closed.get() && ready.getAsBoolean() }
            val profile = trusted?.profile ?: Supplier { FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE.profileId }
            val reasoning = FaroV5ActiveChatReasoningSession(
                graph.generationId, workspace, gate, current, admitted,
                trusted?.gateway ?: ModelGateway { _, _ -> error("offline_no_provider") },
                { message -> trusted?.egressAdmission?.test(message) == true && admitted.getAsBoolean() },
            )
            val recovered = FaroV5InterruptedAssistantTurnRecovery.reconcile(
                graph.generationId, workspace, reasoning, profile, admitted, admitted,
            )
            val review = FaroV5ActiveChatReviewSession(graph.generationId, workspace, gate, current, admitted)
            val bridge = FaroV5ModelReviewBridge(graph.generationId, workspace, graph.reality, gate, current, admitted)
            val apply = FaroV5ActiveReviewedApplySession(graph.generationId, workspace, graph.reality, gate, current, admitted)
            val dispatch = FaroV5HostAcceptedTurnDispatchPort(
                FaroV5HostAssistantTurnDispatcher(reasoning, Executors.newSingleThreadExecutor(), admitted,
                    { conversation, turn -> bridge.propose(conversation, turn); Unit }), profile, admitted,
            )
            val explicitApply = object : ConversationApplyRequestPort {
                override suspend fun request(context: ConversationPlanReviewContext): ConversationApplyHostOutcome {
                    if (!admitted.getAsBoolean()) return ConversationApplyHostOutcome.Denied("integrated_session_closed")
                    return try {
                        val stored = workspace.reviews(context.conversationId).firstOrNull { it.reviewId == context.reviewId }
                        if (stored == null || stored.context() != context)
                            return ConversationApplyHostOutcome.Denied("stale_review_context")
                        val outcome = when (stored.state) {
                            ConversationPlanReviewState.APPROVED -> apply.executeAfterHumanRequest(context)
                            ConversationPlanReviewState.APPLYING,
                            ConversationPlanReviewState.RECOVERING_UNKNOWN -> apply.reconcileCommittedOnly(context)
                            else -> ConversationApplyHostOutcome.Denied("human_approval_unavailable")
                        }
                        if (admitted.getAsBoolean()) outcome else ConversationApplyHostOutcome.RecoveryRequired
                    } catch (_: Exception) { ConversationApplyHostOutcome.RecoveryRequired }
                }
            }
            return FaroV5DevAssistantSessionPorts(review.port, explicitApply,
                if (trusted == null) null else dispatch, dispatch, closed, recovered)
        }
    }
}
