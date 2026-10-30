package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId

object FinanceOperations {
    val CAPABILITY_ID = CapabilityId("N-025")
    val EXECUTOR_ID = ExecutorId("finance")
    val SNAPSHOT_READ = OperationId("finance.snapshot.read")
    val ACCOUNT_CREATE = OperationId("finance.account.create")
    val ENTRY_RECORD = OperationId("finance.entry.record")
}
