package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.BootstrapJournalPort
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import java.util.function.Supplier

/**
 * F2 Android candidate health inspection, never an activation or pointer switch.
 *
 * A validated journal/descriptor alone is insufficient: the isolated five-owner
 * SQLite graph must actually open and pass health checks. The V1 active pointer
 * and exact journal are rechecked after graph disposal. This cannot grant V2
 * material authority, install replay fences, or replace the production V1 host.
 */
internal class FaroV5QuarantinedV2RecoveryHost(
    private val layout: FileStorageGenerationLayout,
    private val journal: BootstrapJournalPort,
    private val pointer: ActiveGenerationPointerPort,
    private val gate: MaterialWriteGate,
) {
    private val probe = FaroV5QuarantinedV2RecoveryProbe(layout, journal, pointer)

    fun inspectHealthy(operationId: MaintenanceOperationId): V2QuarantinedRecoveryEvidence =
        runCatching {
            val candidate = probe.inspect(operationId)
            if (candidate !is V2QuarantinedRecoveryEvidence.Candidate)
                return@runCatching V2QuarantinedRecoveryEvidence.Blocked
            val entry = journal.read(operationId)
                ?: return@runCatching V2QuarantinedRecoveryEvidence.Blocked
            val previous = entry.previousGenerationId
                ?: return@runCatching V2QuarantinedRecoveryEvidence.Blocked
            val before = pointer.read()
                ?: return@runCatching V2QuarantinedRecoveryEvidence.Blocked
            if (before.generationId != previous)
                return@runCatching V2QuarantinedRecoveryEvidence.Blocked

            val staged = layout.requireStaged(entry.targetGenerationId)
            val admission = FaroV5QuarantinedV2GraphAdmission.canonical(
                gate,
                Supplier {
                    val active = pointer.read()?.generationId
                        ?: error("active pointer disappeared during recovery inspection")
                    layout.requireStaged(active).directory
                },
            )
            val healthy = admission.open(staged).use { graph ->
                graph.generationId == candidate.targetGenerationId && graph.verifyHealthy()
            }
            if (healthy && pointer.read() == before &&
                journal.read(operationId) == entry && probe.inspect(operationId) == candidate
            ) candidate else V2QuarantinedRecoveryEvidence.Blocked
        }.getOrDefault(V2QuarantinedRecoveryEvidence.Blocked)
}
