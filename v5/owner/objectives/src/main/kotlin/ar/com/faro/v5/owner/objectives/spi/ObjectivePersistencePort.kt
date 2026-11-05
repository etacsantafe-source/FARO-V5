package ar.com.faro.v5.owner.objectives.spi

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.objectives.api.ObjectiveRecord
import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot

/** Owned by Reality; writes require its active transaction scope. */
interface ObjectivePersistencePort {
    fun read(): ObjectiveSnapshot
    fun upsert(record: ObjectiveRecord, transactionScope: RealityTransactionScope): ObjectiveRecord
}
