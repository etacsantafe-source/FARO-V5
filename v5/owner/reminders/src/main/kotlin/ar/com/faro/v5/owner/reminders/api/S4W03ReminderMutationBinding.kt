package ar.com.faro.v5.owner.reminders.api

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.spi.ReminderMutationPersistencePort
import ar.com.faro.v5.owner.reminders.spi.ReminderPersistencePort

/**
 * W03-D1 official N-032 Review/Apply binding. Cannot grant owner access.
 * The trusted host supplies the independent Matter-link policy callback;
 * default is DENY. Reads owner state again INSIDE the active SQL transaction.
 */
class S4W03ReminderMutationBinding(
    override val operationId: OperationId,
    private val createPort: ReminderPersistencePort,
    private val mutationPort: ReminderMutationPersistencePort,
    private val catalog: OperationContractLookupPort,
    private val linkedMatterGranted: (String) -> Boolean = { false },
) : OperationBindingPort, OperationRevalidationPort {
    init { require(operationId in S4W03ReminderOperations.supported) }

    override val descriptor = OperationBindingDescriptor(
        operationId, ReminderOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
    )

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
        if (admitted(preparedOperation, requireMatterPolicy = false) != null)
            OperationRevalidationResult.Valid
        else OperationRevalidationResult.Rejected(
            OperationRevalidationFailure.PRECONDITION_FAILED,
            "reminder_mutation_stale_or_invalid",
        )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val intent = admitted(preparedOperation, requireMatterPolicy = true)
            ?: return OperationBindingExecutionResult.Rejected(
                "reminder_mutation_stale_or_link_acl_denied",
            )
        when (val change = intent.change) {
            is S4W03ReminderChange.Create ->
                createPort.createRule(change.rule, intent.expectedRevision, transactionScope)
            is S4W03ReminderChange.Update ->
                mutationPort.updateRule(
                    change.original, change.revised, intent.expectedRevision, transactionScope,
                )
            is S4W03ReminderChange.Cancel ->
                mutationPort.cancelReviewedRule(
                    change.original, change.atEpochMillis,
                    intent.expectedRevision, transactionScope,
                )
            is S4W03ReminderChange.Attend ->
                mutationPort.attendOccurrence(
                    change.acknowledged, intent.expectedRevision, transactionScope,
                )
        }
        return OperationBindingExecutionResult.Completed
    }

    private fun admitted(
        prepared: PreparedOperation,
        requireMatterPolicy: Boolean,
    ): S4W03ReminderIntent? = runCatching {
        val contract = prepared.contract
        if (contract.operationId != operationId ||
            !S4W03ReminderOperations.allows(contract) ||
            catalog.current(operationId) != contract ||
            prepared.dependencyStamps != DependencyStampSet.empty()
        ) return@runCatching null
        val intent = S4W03ReminderMutationPayload.decode(prepared.payload)
            ?.takeIf { it.operation == operationId } ?: return@runCatching null
        // This is re-read after BEGIN IMMEDIATE during execute: stale plans fail.
        val snapshot = createPort.read()
        if (!intent.validOn(snapshot)) return@runCatching null
        if (requireMatterPolicy &&
            intent.linkedMatterIds(snapshot).any { !linkedMatterGranted(it) }
        ) return@runCatching null
        intent
    }.getOrNull()
}
