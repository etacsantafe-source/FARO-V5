package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId

object FinanceOperations {
    val CAPABILITY_ID = CapabilityId("N-025")
    val EXECUTOR_ID = ExecutorId("finance")
    val BUDGET_READ = OperationId("finance.budget.read")
    val BUDGET_SET = OperationId("finance.budget.set")
    val BUDGET_CLEAR = OperationId("finance.budget.clear")
    val SNAPSHOT_READ = OperationId("finance.snapshot.read")
    val ACCOUNT_CREATE = OperationId("finance.account.create")
    val ENTRY_RECORD = OperationId("finance.entry.record")
    val DEBT_PAYMENT_RECORD = OperationId("finance.debt.payment.record")
    val PLAN_ITEM_EXECUTE = OperationId("finance.plan_item.execute")
    val DEBT_CREATE = OperationId("finance.debt.create")
    val SCHEDULE_READ = OperationId("finance.schedule.read")
    val SCHEDULE_UPDATE = OperationId("finance.schedule.update")
    val SCHEDULE_SET_ACTIVE = OperationId("finance.schedule.set_active")
    val SCHEDULE_DELETE = OperationId("finance.schedule.delete")
    val PLAN_ITEM_CANCEL = OperationId("finance.plan_item.cancel")
    val SCHEDULE_CREATE = OperationId("finance.schedule.create")
    val PLAN_ITEM_CREATE = OperationId("finance.plan_item.create")
    val TRANSFER_RECORD = OperationId("finance.transfer.record")

    val ACTIVE_W01_ADDITIONAL_REVIEW = setOf(
        TRANSFER_RECORD, DEBT_CREATE, SCHEDULE_CREATE, PLAN_ITEM_CREATE,
        DEBT_PAYMENT_RECORD, PLAN_ITEM_EXECUTE, SCHEDULE_UPDATE, SCHEDULE_SET_ACTIVE,
        SCHEDULE_DELETE, PLAN_ITEM_CANCEL, BUDGET_SET, BUDGET_CLEAR,
    )
}
