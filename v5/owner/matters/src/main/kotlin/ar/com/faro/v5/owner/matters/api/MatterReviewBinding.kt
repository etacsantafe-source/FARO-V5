package ar.com.faro.v5.owner.matters.api

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort

/** Scope-bound Matter mutation revalidated in the same Reality SQL transaction. */
class MatterReviewBinding(
    override val operationId: OperationId,
    private val persistence: MatterPersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationBindingPort, OperationRevalidationPort {
    init { require(operationId in MatterReviewOperations.supported) }

    override val descriptor = OperationBindingDescriptor(
        operationId, MatterReviewOperations.EXECUTOR, ExecutionProfile.REALITY_MUTATION,
    )

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
        if (replacement(preparedOperation) == null) OperationRevalidationResult.Rejected(
            OperationRevalidationFailure.PRECONDITION_FAILED, "matter_review_stale_or_invalid",
        ) else OperationRevalidationResult.Valid

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        // This read executes after BEGIN IMMEDIATE, not just on the UI preflight.
        val proposed = replacement(preparedOperation)
            ?: return OperationBindingExecutionResult.Rejected("matter_review_stale_or_invalid")
        persistence.upsert(proposed, transactionScope)
        return OperationBindingExecutionResult.Completed
    }

    private fun replacement(prepared: PreparedOperation): MatterRecord? {
        val contract = prepared.contract
        if (contract.operationId != operationId || !MatterReviewOperations.allows(contract) ||
            catalog.current(operationId) != contract ||
            prepared.dependencyStamps != DependencyStampSet.empty()
        ) return null
        val intent = MatterReviewPayload.decode(prepared.payload) ?: return null
        if (intent.operation != operationId) return null
        return intent.applyOn(persistence.read())
    }
}
