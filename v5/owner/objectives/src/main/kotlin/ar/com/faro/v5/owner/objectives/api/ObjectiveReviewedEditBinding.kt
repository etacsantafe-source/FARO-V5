package ar.com.faro.v5.owner.objectives.api

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.objectives.spi.ObjectivePersistencePort

class ObjectiveReviewedEditBinding(
    override val operationId: OperationId,
    private val persistence: ObjectivePersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationBindingPort, OperationRevalidationPort {
    init { require(operationId in ObjectiveHistoryOperations.supported) }

    override val descriptor = OperationBindingDescriptor(
        operationId, ObjectiveReviewOperations.EXECUTOR, ExecutionProfile.REALITY_MUTATION,
    )

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
        if (replacement(preparedOperation) == null) OperationRevalidationResult.Rejected(
            OperationRevalidationFailure.PRECONDITION_FAILED, "objective_review_stale_or_invalid",
        ) else OperationRevalidationResult.Valid

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        // Recheck on the actual transaction, after BEGIN IMMEDIATE.
        val record = replacement(preparedOperation)
            ?: return OperationBindingExecutionResult.Rejected("objective_review_stale_or_invalid")
        persistence.upsert(record, transactionScope)
        return OperationBindingExecutionResult.Completed
    }

    private fun replacement(prepared: PreparedOperation): ObjectiveRecord? {
        val contract = prepared.contract
        if (contract.operationId != operationId || !ObjectiveHistoryOperations.allows(contract) ||
            catalog.current(operationId) != contract || prepared.dependencyStamps != DependencyStampSet.empty()
        ) return null
        val snapshot = persistence.read()
        val history = ObjectiveHistoryPayload.decode(prepared.payload)
        if (history != null) {
            if (history.operation != operationId || snapshot.revision != history.expectedRevision) return null
            return snapshot.objectives.firstOrNull { it.id == history.objectiveId }?.let(history::applyOn)
        }
        if (operationId !in ObjectiveReviewOperations.editable) return null
        val intent = ObjectiveEditPayload.decode(prepared.payload) ?: return null
        if (snapshot.revision != intent.expectedRevision) return null
        val form = intent.form
        val existing = snapshot.objectives.firstOrNull { it.id == form.id }
        if (operationId == ObjectiveReviewOperations.CREATE && existing != null) return null
        if (operationId == ObjectiveReviewOperations.UPDATE &&
            (existing == null || intent.editedAtEpochMillis <= existing.updatedAtEpochMillis)
        ) return null
        return ObjectiveRecord(
            form.id, form.title, form.detail, existing?.status ?: ObjectiveStatus.PLANNED,
            intent.editedAtEpochMillis, (existing?.details ?: ObjectiveDetails()).copy(
                target = form.target, dueDate = form.dueDate, priority = form.priority,
            ),
        )
    }
}
