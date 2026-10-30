package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.owner.finance.spi.FinancePersistencePort

class FinanceSnapshotReader(
    private val persistence: FinancePersistencePort,
) : FinanceSnapshotReadApi {
    override fun read(): FinanceSnapshot = persistence.read()
}
