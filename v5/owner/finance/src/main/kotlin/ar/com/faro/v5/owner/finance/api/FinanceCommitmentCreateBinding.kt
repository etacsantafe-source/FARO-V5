package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

fun interface FinanceCommitmentWritePort {
    fun create(command: FinanceCommitmentCreate, scope: RealityTransactionScope)
}

/** Bind only the three declaration operations already present in the contract catalog. */
class FinanceCommitmentCreateBinding private constructor(
    operationId: OperationId,
    private val finance: FinanceCommitmentWritePort,
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
        ) return OperationBindingExecutionResult.Rejected("finance_commitment_contract_mismatch")

        val command = FinanceCommitmentCreatePayload.decode(descriptor.operationId, preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("finance_commitment_payload_invalid")
        finance.create(command, transactionScope)
        return OperationBindingExecutionResult.Completed
    }

    companion object {
        fun all(finance: FinanceCommitmentWritePort): List<FinanceCommitmentCreateBinding> =
            listOf(FinanceOperations.DEBT_CREATE, FinanceOperations.SCHEDULE_CREATE, FinanceOperations.PLAN_ITEM_CREATE)
                .map { FinanceCommitmentCreateBinding(it, finance) }
    }
}
