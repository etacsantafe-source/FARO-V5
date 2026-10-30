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

class FinanceAccountCreateBinding(
    private val persistence: FinancePersistencePort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = FinanceOperations.ACCOUNT_CREATE,
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
                "finance_account_contract_mismatch"
            )
        }

        val create = FinanceAccountCreatePayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected(
                "finance_account_payload_invalid"
            )

        persistence.createAccount(
            create = create,
            transactionScope = transactionScope,
        )
        return OperationBindingExecutionResult.Completed
    }
}

class FinanceAccountCreateRevalidation : OperationRevalidationPort {
    override val operationId = FinanceOperations.ACCOUNT_CREATE

    override fun revalidate(
        preparedOperation: PreparedOperation,
    ): OperationRevalidationResult =
        if (FinanceAccountCreatePayload.decode(preparedOperation.payload) != null) {
            OperationRevalidationResult.Valid
        } else {
            OperationRevalidationResult.Rejected(
                failure = OperationRevalidationFailure.PRECONDITION_FAILED,
                code = "finance_account_payload_invalid",
            )
        }
}
