package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import ar.com.faro.v5.store.reality.RealityPlanningBackupSnapshot

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
        var observedG4: RealityDomainBackupSnapshot? = null
        var observedPlanning: RealityPlanningBackupSnapshot? = null
        // The coordinator invokes this adapter only INSIDE one global lease.
        // A single Reality mutex acquisition reads legacy data and G4 owners
        // together; the old FBS1 backup port retains its fail-closed G4 gate.
        val pairedReality = object : RealityBackupPort {
            override fun snapshot(): RealityBackupSnapshot {
                check(observedG4 == null) { "FBS2 Reality owner snapshot read twice" }
                val paired = graph.reality.fbs2Backup.snapshot()
                observedG4 = paired.g4Domains
                observedPlanning = paired.planningDomains
                return paired.legacy
            }

            override fun restoreOrVerify(
                snapshot: RealityBackupSnapshot,
            ): RealityBackupSnapshot = error("capture-only Reality adapter cannot restore")
        }
        val capture = SemanticBackupCaptureCoordinator(
            quiescence = quiescence,
            reality = pairedReality,
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
        val g4 = checkNotNull(observedG4) {
            "FBS2 Reality owner was not captured under global quiescence"
        }
        // Reuse unchanged FBS2 v1 when G4 is *genuinely* untouched.
        // Nonzero revisions with zero rows still require v2.
        val hasG4Authority = g4.objectives.objectives.isNotEmpty() ||
            g4.matters.matters.isNotEmpty() ||
            g4.objectives.revision != 0L || g4.matters.revision != 0L
        return FaroV5FiveStoreSemanticSet(
            four, workspaceSnapshot,
            // Schema8 STAGED always needs a complete four-section FBS2v3
            // even when there are zero Planning rows/revisions. Never
            // downgrade to v1/v2 based on an empty-looking new owner.
            g4Domains = if (observedPlanning != null || hasG4Authority) g4 else null,
            planningDomains = observedPlanning?.let {
                PlanningDomainSectionSnapshot(it.schemaVersion,it.reminders,it.monthlyPlan)
            },
        )
    }
}
