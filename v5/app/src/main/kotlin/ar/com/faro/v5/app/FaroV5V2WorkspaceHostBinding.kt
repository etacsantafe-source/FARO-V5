package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import java.nio.file.Path
import java.util.function.Supplier

/**
 * Real Workspace read binding for a five-owner V2 host candidate.
 *
 * The stage remains quarantined: no Apply, delivery to OS, active-pointer switch,
 * replay admission or second live authority is exposed by this session.
 * A later, separately certified lifecycle must own promotion to active host.
 */
class FaroV5V2WorkspaceHostBinding private constructor(
    private val stores: FaroV5GenerationStoreGraph,
) : AutoCloseable {
    val generationId: String = stores.generationId
    val workspaceReadPort: ConversationWorkspaceReadPort =
        requireNotNull(stores.workspace) { "V2 host requires the fifth Workspace owner" }

    fun verifyFiveOwnerHealth(): Boolean = stores.verifyHealthy()

    override fun close() = stores.close()

    companion object {
        @JvmStatic
        fun openQuarantined(
            staged: StagedStorageGeneration,
            gate: MaterialWriteGate,
            activeDirectory: Supplier<Path>,
        ): FaroV5V2WorkspaceHostBinding {
            val graph = FaroV5QuarantinedV2GraphAdmission
                .canonical(gate, activeDirectory).open(staged)
            return try {
                FaroV5V2WorkspaceHostBinding(graph)
            } catch (failure: Throwable) {
                graph.close()
                throw failure
            }
        }
    }
}
