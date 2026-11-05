package ar.com.faro.v5.owner.matters.spi

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.matters.api.MatterRecord
import ar.com.faro.v5.owner.matters.api.MatterSnapshot

/** Owned by Reality; writes require its active transaction scope. */
interface MatterPersistencePort {
    fun read(): MatterSnapshot
    fun upsert(record: MatterRecord, transactionScope: RealityTransactionScope): MatterRecord
}
