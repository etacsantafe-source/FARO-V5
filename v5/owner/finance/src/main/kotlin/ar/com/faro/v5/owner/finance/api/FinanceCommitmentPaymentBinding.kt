package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

fun interface FinanceCommitmentPaymentPort {
    fun record(payment: FinanceCommitmentPayment, scope: RealityTransactionScope)
}

class FinanceCommitmentPaymentBinding private constructor(
    operationId: OperationId,
    private val finance: FinanceCommitmentPaymentPort,
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
        ) return OperationBindingExecutionResult.Rejected("finance_commitment_payment_contract_mismatch")
        val payment = FinanceCommitmentPaymentPayload.decode(descriptor.operationId, preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("finance_commitment_payment_payload_invalid")
        finance.record(payment, transactionScope)
        return OperationBindingExecutionResult.Completed
    }

    companion object {
        fun all(finance: FinanceCommitmentPaymentPort): List<FinanceCommitmentPaymentBinding> =
            listOf(FinanceOperations.PLAN_ITEM_EXECUTE, FinanceOperations.DEBT_PAYMENT_RECORD)
                .map { FinanceCommitmentPaymentBinding(it, finance) }
    }
}
