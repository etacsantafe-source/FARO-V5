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
import ar.com.faro.v5.owner.finance.spi.FinancePersistencePort

class FinanceEntryRecordBinding(
    private val persistence: FinancePersistencePort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = FinanceOperations.ENTRY_RECORD,
        canonicalExecutor = FinanceOperations.EXECUTOR_ID,
        executionProfile = ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val contract = preparedOperation.contract
        if (
            contract.operationId != descriptor.operationId ||
            contract.canonicalExecutor != descriptor.canonicalExecutor ||
            contract.executionProfile != descriptor.executionProfile
        ) {
            return OperationBindingExecutionResult.Rejected(
                "finance_entry_contract_mismatch"
            )
        }

        val record = FinanceEntryRecordPayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected(
                "finance_entry_payload_invalid"
            )

        persistence.recordEntry(
            record = record,
            transactionScope = transactionScope,
        )
        return OperationBindingExecutionResult.Completed
    }
}

class FinanceEntryRecordRevalidation(
    private val persistence: FinancePersistencePort,
) : OperationRevalidationPort {
    override val operationId = FinanceOperations.ENTRY_RECORD

    override fun revalidate(
        preparedOperation: PreparedOperation,
    ): OperationRevalidationResult {
        val record = FinanceEntryRecordPayload.decode(preparedOperation.payload)
            ?: return OperationRevalidationResult.Rejected(
                failure = OperationRevalidationFailure.PRECONDITION_FAILED,
                code = "finance_entry_payload_invalid",
            )

        val accountExists =
            persistence.read().accounts.any { it.accountId == record.accountId }
        return if (accountExists) {
            OperationRevalidationResult.Valid
        } else {
            OperationRevalidationResult.Rejected(
                failure = OperationRevalidationFailure.PRECONDITION_FAILED,
                code = "finance_entry_account_missing",
            )
        }
    }
}
