package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * F1 certification-only host for a fully initialized, NON-ACTIVE five-store V2
 * generation. This is not the shipping CompositionRoot or an activation path.
 *
 * The SAME staged Workspace backs selection, Composer, timeline, human Review
 * and asynchronous reasoning. The model/profile/egress inputs must originate
 * from the trusted host; neither UI nor a message may supply them.
 *
 * No active pointer mutation, Reality Apply, OS delivery or V2 cutover is
 * exposed. Every port checks that the stage has NOT become active, including
 * after the host was opened. Close on test/host disposal to stop model work.
 */
internal class FaroV5QuarantinedV2ChatHostBinding private constructor(
    private val graph: FaroV5GenerationStoreGraph,
    private val stageDirectory: Path,
    private val activeDirectory: Supplier<Path>,
    private val closed: AtomicBoolean,
    val workspaceReadPort: ConversationWorkspaceReadPort,
    val timelineReadPort: ConversationTimelineReadPort,
    val selectionPort: ConversationWorkspaceSelectionPort,
    val composerPort: ConversationComposerPort,
    val reviewPort: ConversationReviewPort,
    val assistantTurnPort: ConversationAssistantTurnOutcomePort,
    private val dispatch: FaroV5HostAcceptedTurnDispatchPort,
) : AutoCloseable {
    val generationId: String get() = graph.generationId

    fun verifyQuarantined(): Boolean = !closed.get() && try {
        activeDirectory.get().toRealPath() != stageDirectory.toRealPath() &&
            graph.verifyHealthy()
    } catch (_: Exception) { false }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            dispatch.close()
            graph.close()
        }
    }

    companion object {
        /**
         * Host-owned, certification-only admission. Never call from shipping UI.
         * The caller must provide a trusted policy-bound gateway and an explicit
         * egress predicate; a synthetic local gateway is suitable for tests.
         */
        fun openForCertification(
            staged: StagedStorageGeneration,
            gate: MaterialWriteGate,
            activeDirectory: Supplier<Path>,
            gateway: ModelGateway,
            profile: Supplier<ModelProfileId>,
            egress: (ConversationMessage) -> Boolean,
        ): FaroV5QuarantinedV2ChatHostBinding {
            val graph = FaroV5QuarantinedV2GraphAdmission
                .canonical(gate, activeDirectory).open(staged)
            var dispatch: FaroV5HostAcceptedTurnDispatchPort? = null
            try {
                val workspace = requireNotNull(graph.workspace) {
                    "F1 V2 candidate must own Workspace"
                }
                check(graph.verifyHealthy()) { "quarantined five-store graph unhealthy" }
                val closed = AtomicBoolean(false)
                val stagePath = staged.directory.toRealPath()
                val generation = graph.generationId
                val admitted = BooleanSupplier {
                    !closed.get() && try {
                        activeDirectory.get().toRealPath() != stagePath
                    } catch (_: Exception) { false }
                }
                val pinned = Supplier { generation }
                val read = FaroV5ActiveChatReadSession(
                    generation, workspace, workspace, pinned, admitted,
                )
                val selection = FaroV5ActiveChatSelectionSession(
                    generation, workspace, gate, pinned, admitted,
                )
                val composer = FaroV5ActiveChatComposerSession(
                    generation, workspace, gate, pinned, admitted,
                )
                val review = FaroV5ActiveChatReviewSession(
                    generation, workspace, gate, pinned, admitted,
                )
                val reasoning = FaroV5ActiveChatReasoningSession(
                    generation, workspace, gate, pinned, admitted, gateway, egress,
                )
                dispatch = FaroV5HostAcceptedTurnDispatchPort(
                    FaroV5HostAssistantTurnDispatcher(
                        reasoning, Executors.newSingleThreadExecutor(), admitted,
                    ),
                    profile, admitted,
                )
                return FaroV5QuarantinedV2ChatHostBinding(
                    graph, stagePath, activeDirectory, closed,
                    read.workspaceReadPort, read.timelineReadPort,
                    selection.port, composer.port, review.port, dispatch, dispatch,
                )
            } catch (failure: Throwable) {
                runCatching { dispatch?.close() }
                graph.close()
                throw failure
            }
        }
    }
}
