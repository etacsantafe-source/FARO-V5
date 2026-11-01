package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*

internal sealed interface V2RuntimeHealth {
    data class Verified(val generationId: String) : V2RuntimeHealth
    data object Blocked : V2RuntimeHealth
}

internal interface V2RuntimeGraph : AutoCloseable {
    val generationId: String
    val hasWorkspace: Boolean
    fun isHealthy(): Boolean
}

internal fun interface V2RuntimeOpener {
    fun open(staged: StagedStorageGeneration): V2RuntimeGraph
}

/** Isolated post-switch V2 health. Does not publish pointers, replay or admit writes. */
internal class FaroV5V2RecoveryRuntimeHealthOwner(
    private val layout: FileStorageGenerationLayout,
    private val journal: BootstrapJournalPort,
    private val pointer: ActiveGenerationPointerPort,
    private val quiescence: MaterialWriteQuiescencePort,
    private val opener: V2RuntimeOpener,
) {
    fun verify(id: MaintenanceOperationId): V2RuntimeHealth =
        runCatching {
            quiescence.withMaterialWritesQuiesced(id) {
                val entry = journal.read(id)
                    ?: return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked
                if (entry.kind != MaintenanceOperationKind.RESTORE ||
                    entry.previousGenerationId == null ||
                    entry.phase !in setOf(BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED) ||
                    entry.materialWritesAccepted ||
                    pointer.read()?.generationId != entry.targetGenerationId ||
                    journal.pending().any { it.operationId != id }
                ) return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked

                val staged = layout.requireStaged(entry.targetGenerationId)
                if (ExistingGenerationFormatReader.requireAndRead(staged) !=
                        GenerationStoreFormat.WORKSPACE_V2 ||
                    pointer.read()?.generationId != entry.targetGenerationId
                ) return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked

                opener.open(staged).use { graph ->
                    if (graph.generationId != entry.targetGenerationId.value ||
                        !graph.hasWorkspace ||
                        !graph.isHealthy() ||
                        pointer.read()?.generationId != entry.targetGenerationId ||
                        journal.read(id) != entry) V2RuntimeHealth.Blocked
                    else V2RuntimeHealth.Verified(entry.targetGenerationId.value)
                }
            }
        }.getOrDefault(V2RuntimeHealth.Blocked)

    companion object {
        fun canonical(
            layout: FileStorageGenerationLayout,
            journal: BootstrapJournalPort,
            pointer: ActiveGenerationPointerPort,
            quiescence: MaterialWriteQuiescencePort,
            gate: MaterialWriteGate,
        ) = FaroV5V2RecoveryRuntimeHealthOwner(layout, journal, pointer, quiescence,
            V2RuntimeOpener { staged ->
                // The pointer already names this generation: inspect live WAL, not a sealed STAGED snapshot.
                val graph = FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                    staged, CanonicalPreparedPlanEnvelopeVerifier, gate)
                object : V2RuntimeGraph {
                    override val generationId get() = graph.generationId
                    override val hasWorkspace get() = graph.workspace != null
                    override fun isHealthy() = graph.verifyHealthy()
                    override fun close() = graph.close()
                }
            })
    }
}
