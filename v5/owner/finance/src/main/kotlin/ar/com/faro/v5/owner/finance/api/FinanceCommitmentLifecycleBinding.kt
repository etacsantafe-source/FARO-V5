package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

fun interface FinanceCommitmentLifecyclePort {
    fun change(command: FinanceCommitmentLifecycle, scope: RealityTransactionScope)
}

class FinanceCommitmentLifecycleBinding private constructor(
    operationId: OperationId,
    private val finance: FinanceCommitmentLifecyclePort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId, FinanceOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val contract = preparedOperation.contract
        if (contract.operationId != descriptor.operationId ||
            contract.canonicalExecutor != descriptor.canonicalExecutor ||
            contract.executionProfile != descriptor.executionProfile
        ) return OperationBindingExecutionResult.Rejected("finance_commitment_lifecycle_contract_mismatch")
        val command = FinanceCommitmentLifecyclePayload.decode(descriptor.operationId, preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("finance_commitment_lifecycle_payload_invalid")
        finance.change(command, transactionScope)
        return OperationBindingExecutionResult.Completed
    }

    companion object {
        fun all(finance: FinanceCommitmentLifecyclePort): List<FinanceCommitmentLifecycleBinding> =
            listOf(
                FinanceOperations.SCHEDULE_UPDATE, FinanceOperations.SCHEDULE_SET_ACTIVE,
                FinanceOperations.SCHEDULE_DELETE, FinanceOperations.PLAN_ITEM_CANCEL,
            )
                .map { FinanceCommitmentLifecycleBinding(it, finance) }
    }
}
