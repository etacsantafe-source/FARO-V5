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
    val DEBT_PAYMENT_RECORD = OperationId("finance.debt.payment.record")
    val PLAN_ITEM_EXECUTE = OperationId("finance.plan_item.execute")
    val DEBT_CREATE = OperationId("finance.debt.create")
    val SCHEDULE_CREATE = OperationId("finance.schedule.create")
    val PLAN_ITEM_CREATE = OperationId("finance.plan_item.create")
    val TRANSFER_RECORD = OperationId("finance.transfer.record")
}
