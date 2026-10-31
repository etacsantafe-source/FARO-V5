package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import java.nio.file.Path

/**
 * F3: create a fresh, five-owner V2 quarantine under the SAME host-wide
 * material-write lease as the RESTORE journal PREPARING -> STAGED transition.
 *
 * No pointer switch, activation, Apply, export or external URI is permitted.
 * A crash or failed stage deliberately leaves PREPARING for explicit recovery:
 * never delete an unknown partial generation or silently reuse an operation.
 * Call only after operation-specific user consent and trusted host admission.
 */
class FaroV5QuarantinedRestorePreparation(
    private val storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
) {
    fun prepare(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
    ): StagedStorageGeneration = quiescence.withMaterialWritesQuiesced(operationId) {
        val journal = FileBootstrapJournal(storageRoot)
        val pointer = FileActiveGenerationPointer(storageRoot)
        val predecessor = checkNotNull(pointer.read()) {
            "quarantined restore requires an existing active generation"
        }
        check(predecessor.generationId != descriptor.generationId) {
            "restore target must differ from active generation"
        }
        check(journal.pending().isEmpty()) {
            "another unresolved maintenance operation owns generation authority"
        }
        check(journal.read(operationId) == null) {
            "restore operation identity cannot be reused for a new preparation"
        }
        val preparing = BootstrapJournalEntry(
            operationId = operationId,
            kind = MaintenanceOperationKind.RESTORE,
            previousGenerationId = predecessor.generationId,
            targetGenerationId = descriptor.generationId,
            phase = BootstrapPhase.PREPARING,
        )
        // Publish durable intent BEFORE touching a new target. No automatic
        // cleanup on failure: PREPARING is a recovery barrier, not a PASS.
        journal.create(preparing)
        val staged = FaroV5WorkspaceV2StageOwner(storageRoot)
            .stageFreshAndVerify(descriptor)
        check(staged.descriptor == descriptor && pointer.read() == predecessor) {
            "restore predecessor or immutable stage changed during preparation"
        }
        val next = BootstrapTransitionPolicy.transition(preparing, BootstrapPhase.STAGED)
        check(journal.compareAndSet(preparing, next)) {
            "restore journal changed before staging was committed"
        }
        check(journal.read(operationId) == next && pointer.read() == predecessor) {
            "restore journal or active predecessor drifted after staging"
        }
        staged
    }
}
