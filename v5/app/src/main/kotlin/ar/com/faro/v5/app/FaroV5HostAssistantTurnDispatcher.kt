package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier

/**
 * F1 host-owned asynchronous boundary for an already accepted Workspace turn.
 *
 * This executor must be dedicated to the current generation and closed on
 * graph disposal. Neither Compose nor a caller may provide model text,
 * provider routing or an egress grant: the trusted ReasoningSession performs
 * those checks again before every model call and durable Workspace mutation.
 *
 * Queue acknowledgement is NOT a durable assistant reply. If the process dies
 * before execution, the accepted user turn remains in Workspace; recovery
 * must not silently replay an uncertain model call. This boundary does not
 * activate V2 or certify end-to-end Android delivery.
 */
class FaroV5HostAssistantTurnDispatcher @JvmOverloads constructor(
    private val session: FaroV5ActiveChatReasoningSession,
    private val dedicatedWorker: ExecutorService,
    private val generationAdmitted: BooleanSupplier,
    private val onDurableReply: (ConversationId, ConversationTurnId) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val inFlight = ConcurrentHashMap<
        Pair<ConversationId, ConversationTurnId>,
        CompletableFuture<FaroV5AssistantTurnOutcome>
    >()

    private fun admitted(): Boolean =
        !closed.get() && try { generationAdmitted.getAsBoolean() }
        catch (_: Exception) { false }

    /**
     * Nonblocking for the UI caller. The same in-flight turn shares one
     * future; Workspace's durable StartFaro idempotency is the final guard.
     */
    fun submit(
        request: FaroV5AssistantTurnRequest,
    ): CompletableFuture<FaroV5AssistantTurnOutcome> {
        if (!admitted())
            return CompletableFuture.completedFuture(FaroV5AssistantTurnOutcome.STALE)
        val key = request.conversationId to request.turnId
        val result = CompletableFuture<FaroV5AssistantTurnOutcome>()
        val existing = inFlight.putIfAbsent(key, result)
        if (existing != null) return existing
        try {
            dedicatedWorker.execute {
                val outcome = try {
                    if (!admitted()) FaroV5AssistantTurnOutcome.STALE
                    else session.run(
                        request,
                        CancellationSignal {
                            !admitted() || Thread.currentThread().isInterrupted
                        },
                    )
                } catch (_: Exception) {
                    FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED
                }
                // Only the trusted host can turn a durable model reply into a
                // bounded, reviewable proposal. Never grant Apply or change
                // the recorded assistant outcome on proposal failure.
                if (outcome == FaroV5AssistantTurnOutcome.COMPLETED ||
                    outcome == FaroV5AssistantTurnOutcome.ALREADY_RECORDED) {
                    try { onDurableReply(request.conversationId, request.turnId) }
                    catch (_: Exception) { /* replayable on a later delivery */ }
                }
                result.complete(outcome)
                inFlight.remove(key, result)
            }
        } catch (_: RuntimeException) {
            inFlight.remove(key, result)
            result.complete(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED)
        }
        return result
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            dedicatedWorker.shutdownNow()
            inFlight.values.forEach {
                it.complete(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED)
            }
            inFlight.clear()
        }
    }
}
