package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.StorageGenerationId

/**
 * Stage-only semantic capture of all five stores from ONE validated V2 graph.
 *
 * The injected quiescence MUST block ALL five owners including Workspace. No
 * Android host currently binds this contract, so this is not proof that live
 * writes are paused. Do not export, encrypt as a user-facing backup, restore
 * or activate V2 until the global owner/writer integration is certified.
 */
class FaroV5FiveStoreBackupCapture(
    private val graph: FaroV5GenerationStoreGraph,
    private val quiescence: MaterialWriteQuiescencePort,
) {
    fun capture(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtEpochMillis: Long,
    ): FaroV5FiveStoreSemanticSet {
        // No fallback to four-store V1 capture if Workspace is absent.
        val workspace = checkNotNull(graph.workspace) {
            "complete five-store capture requires the canonical Workspace owner"
        }
        val capture = SemanticBackupCaptureCoordinator(
            quiescence = quiescence,
            reality = graph.reality.backup,
            commercialEvidence = graph.commercialEvidence.backup,
            webEvidence = graph.webEvidence.backup,
            auxiliary = graph.auxiliary.backup,
        )
        val (four, workspaceSnapshot) = capture.captureWithAdditionalSnapshot(
            operationId = operationId,
            backupSetId = backupSetId,
            sourceGenerationId = StorageGenerationId(graph.generationId),
            productVersion = productVersion,
            createdAtEpochMillis = createdAtEpochMillis,
        ) {
            workspace.backup.snapshot().also { it.validateForRestore() }
        }
        return FaroV5FiveStoreSemanticSet(four, workspaceSnapshot)
    }
}
