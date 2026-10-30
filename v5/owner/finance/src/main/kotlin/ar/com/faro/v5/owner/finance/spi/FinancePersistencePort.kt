package ar.com.faro.v5.owner.finance.spi

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecord
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot

interface FinancePersistencePort {
    fun read(): FinanceSnapshot

    fun createAccount(
        create: FinanceAccountCreate,
        transactionScope: RealityTransactionScope,
    ): FinanceAccountSnapshot

    fun recordEntry(
        record: FinanceEntryRecord,
        transactionScope: RealityTransactionScope,
    ): FinanceEntrySnapshot
}
