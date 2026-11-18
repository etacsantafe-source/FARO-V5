package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationRevalidationFailure
import ar.com.faro.v5.kernel.contracts.OperationRevalidationPort
import ar.com.faro.v5.kernel.contracts.OperationRevalidationResult
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

fun interface FinanceTransferWritePort {
    fun record(transfer: FinanceTransferRecord, transactionScope: RealityTransactionScope)
}

class FinanceTransferRecordBinding(
    private val finance: FinanceTransferWritePort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        FinanceOperations.TRANSFER_RECORD,
        FinanceOperations.EXECUTOR_ID,
        ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val contract = preparedOperation.contract
        if (contract.operationId != descriptor.operationId ||
            contract.canonicalExecutor != descriptor.canonicalExecutor ||
            contract.executionProfile != descriptor.executionProfile
        ) return OperationBindingExecutionResult.Rejected("finance_transfer_contract_mismatch")

        val transfer = FinanceTransferRecordPayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("finance_transfer_payload_invalid")
        finance.record(transfer, transactionScope)
        return OperationBindingExecutionResult.Completed
    }
}

class FinanceTransferRecordRevalidation(
    private val finance: FinanceSnapshotReadApi,
) : OperationRevalidationPort {
    override val operationId = FinanceOperations.TRANSFER_RECORD

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult {
        val transfer = FinanceTransferRecordPayload.decode(preparedOperation.payload)
        return if (transfer != null && transfer.matchesAccounts(finance.read())) {
            OperationRevalidationResult.Valid
        } else {
            OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED,
                "finance_transfer_invalid_payload_or_accounts",
            )
        }
    }
}
