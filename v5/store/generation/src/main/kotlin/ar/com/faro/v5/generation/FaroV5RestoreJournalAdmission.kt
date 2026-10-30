package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.BootstrapJournalPort
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationId

/**
 * Stage-only journal admission for the *existing* one-lease five-store
 * semantic restore. Enforces durable RESTORE/STAGED authority while the same
 * global material-write lease is held. No nested quiescence, no mutation of
 * journal/pointer, no implicit bootstrap, activation or recovery.
 *
 * This is NOT a complete V2 restore coordinator. Journal VALIDATED transition
 * still requires authenticated restored-state proof + production host binding.
 * Until then this object is test/staging-only and V2 remains disabled.
 *
 * Pass this wrapper in place of the quiescence dependency to
 * FaroV5FiveStoreStagedRestore. The underlying delegate MUST be the real
 * shared runtime gate covering all five owners plus generation activation;
 * wrapping a store-local lock does NOT make it safe for production.
 */
class FaroV5RestoreJournalAdmission(
    private val journal: BootstrapJournalPort,
    private val activePointer: ActiveGenerationPointerPort,
    private val expectedTarget: StorageGenerationId,
    private val globalQuiescence: MaterialWriteQuiescencePort,
) : MaterialWriteQuiescencePort {
    override fun <T> withMaterialWritesQuiesced(
        operationId: MaintenanceOperationId,
        block: () -> T,
    ): T = globalQuiescence.withMaterialWritesQuiesced(operationId) {
        val entry = checkNotNull(journal.read(operationId)) {
            "V2 restore requires a durable operation journal"
        }
        check(entry.operationId == operationId &&
            entry.kind == MaintenanceOperationKind.RESTORE &&
            entry.phase == BootstrapPhase.STAGED &&
            !entry.materialWritesAccepted &&
            entry.targetGenerationId == expectedTarget &&
            entry.previousGenerationId != expectedTarget
        ) { "V2 restore journal is not an exact staged restore for the target" }
        check(activePointer.read()?.generationId == entry.previousGenerationId) {
            "V2 restore source active generation changed before staging"
        }
        // A second unfinished journal operation cannot independently mutate
        // authority under this restore's lease.
        check(journal.pending().all { it.operationId == operationId }) {
            "V2 restore blocked by another unresolved bootstrap operation"
        }
        block()
    }
}
