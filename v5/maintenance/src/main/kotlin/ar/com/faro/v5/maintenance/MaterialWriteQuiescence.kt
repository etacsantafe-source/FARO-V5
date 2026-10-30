package ar.com.faro.v5.maintenance

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate

/**
 * Global runtime boundary used by Maintenance before operations that must
 * observe a stable set of authorities or switch the active StorageGeneration.
 *
 * Implementations must prevent new material writes for the full block,
 * wait for an already-started material write to finish, and always release
 * the gate when the block returns or throws.
 */
interface MaterialWriteQuiescencePort {
    fun <T> withMaterialWritesQuiesced(
        operationId: MaintenanceOperationId,
        block: () -> T,
    ): T
}


class MaterialWriteGateQuiescence(
    private val gate: MaterialWriteGate,
) : MaterialWriteQuiescencePort {
    override fun <T> withMaterialWritesQuiesced(
        operationId: MaintenanceOperationId,
        block: () -> T,
    ): T =
        gate.withQuiescedMaterialWrites(block)
}
