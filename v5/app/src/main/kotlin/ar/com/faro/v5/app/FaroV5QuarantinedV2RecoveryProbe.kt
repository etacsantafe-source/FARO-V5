package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.BootstrapJournalPort
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import java.nio.file.Files
import java.nio.file.LinkOption

/** Evidence only. A Candidate is never permission to switch a pointer or admit writes. */
internal sealed interface V2QuarantinedRecoveryEvidence {
    data class Candidate(val targetGenerationId: String) : V2QuarantinedRecoveryEvidence
    data object Blocked : V2QuarantinedRecoveryEvidence
}

/**
 * Fail-closed, read-only Android-side preflight for an already VALIDATED,
 * marked five-store RESTORE. An eventual active host needs a separate,
 * independently certified runtime/replay/physical activation decision.
 *
 * This does not open SQLite, write journal/pointer, replay historical effects,
 * or replace the production four-store CompositionRoot.
 */
internal class FaroV5QuarantinedV2RecoveryProbe(
    private val layout: FileStorageGenerationLayout,
    private val journal: BootstrapJournalPort,
    private val pointer: ActiveGenerationPointerPort,
) {
    fun inspect(operationId: MaintenanceOperationId): V2QuarantinedRecoveryEvidence =
        runCatching {
            val entry = journal.read(operationId)
                ?: return V2QuarantinedRecoveryEvidence.Blocked
            val previous = entry.previousGenerationId
                ?: return V2QuarantinedRecoveryEvidence.Blocked
            if (entry.operationId != operationId ||
                entry.kind != MaintenanceOperationKind.RESTORE ||
                entry.phase != BootstrapPhase.VALIDATED ||
                entry.materialWritesAccepted ||
                previous == entry.targetGenerationId ||
                pointer.read()?.generationId != previous ||
                journal.pending().any { it.operationId != operationId }
            ) {
                return V2QuarantinedRecoveryEvidence.Blocked
            }

            val stage = layout.requireStaged(entry.targetGenerationId)
            // Re-resolve via the exact immutable descriptor and reject any
            // V1 candidate, orphan Workspace, missing DB or corrupted V2 marker.
            val target = stage.directory.toAbsolutePath().normalize()
            val previousDirectory = layout.requireStaged(previous)
                .directory.toAbsolutePath().normalize()
            if (target == previousDirectory ||
                Files.isSymbolicLink(target) ||
                !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ||
                ExistingGenerationFormatReader.requireAndRead(stage) !=
                    GenerationStoreFormat.WORKSPACE_V2 ||
                pointer.read()?.generationId != previous
            ) {
                return V2QuarantinedRecoveryEvidence.Blocked
            }
            V2QuarantinedRecoveryEvidence.Candidate(entry.targetGenerationId.value)
        }.getOrDefault(V2QuarantinedRecoveryEvidence.Blocked)
}
