package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationTimelinePort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * Generation-scoped Chat reads from the SAME Workspace authority as Delivery.
 * Stale UI callbacks fail closed on pointer switch or startup recovery.
 * This never exposes writes, plan approvals, model execution or OS side effects.
 */
class FaroV5ActiveChatReadSession(
    private val generationId: String,
    private val workspace: ConversationWorkspaceReadPort,
    private val timeline: ConversationTimelinePort,
    private val activeGenerationId: Supplier<String>,
    private val startupReady: BooleanSupplier,
) {
    init { require(generationId.isNotBlank()) }

    private fun requireCurrent() {
        check(startupReady.getAsBoolean() && activeGenerationId.get() == generationId) {
            "Chat read session is stale or maintenance recovery is incomplete"
        }
    }

    @JvmField
    val workspaceReadPort = ConversationWorkspaceReadPort {
        requireCurrent()
        val result = workspace.readSnapshot()
        requireCurrent()
        result
    }

    @JvmField
    val timelineReadPort = ConversationTimelineReadPort { id: ConversationId ->
        requireCurrent()
        val result = timeline.readTimeline(id)
        requireCurrent()
        result
    }
}
