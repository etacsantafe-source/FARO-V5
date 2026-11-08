package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency

/**
 * N033C3: existing D-229 operation identity, not a new catalog entry.
 * This binding is INTERNAL to the isolated Reality8 host factory. A Receipt
 * proves that SOME previous Apply committed, not which object it changed.
 * Future trusted host MUST additionally verify original COMMITTED Workspace
 * plan and a NEW, independent human-approved undo.execute Review.
 */
internal object RealityPlanningUndoOperations {
    val EXECUTE = OperationId("undo.execute")
    val CAPABILITY_ID = CapabilityId("N-016")
    val EXECUTOR_ID = ExecutorId("undo-coordinator")
}

private sealed interface UndoOwnerTarget {
    val ownerRevision: Long

    data class Reminder(
        val original: ReminderRule,
        override val ownerRevision: Long,
    ) : UndoOwnerTarget

    data class Monthly(
        val original: MonthlyPlanItem,
        override val ownerRevision: Long,
    ) : UndoOwnerTarget
}

private fun admittedUndoTarget(
    prepared: PreparedOperation,
    catalog: OperationContractLookupPort,
    reality: RealityStore,
): Pair<UndoExecuteIntent, UndoOwnerTarget>? = runCatching {
    val contract = prepared.contract
    if (contract.operationId != RealityPlanningUndoOperations.EXECUTE ||
        contract.capabilityId != RealityPlanningUndoOperations.CAPABILITY_ID ||
        contract.canonicalExecutor != RealityPlanningUndoOperations.EXECUTOR_ID ||
        contract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
        contract.exposure != ContractExposure.BINDABLE ||
        catalog.current(contract.operationId) != contract
    ) return@runCatching null

    val intent = UndoExecutePayload.decode(prepared.payload)
        ?: return@runCatching null
    val sourceTx = intent.sourceReceipt.transactionId
    val actualReceipt = reality.executionReceiptLookup.find(sourceTx)
    val attempt = reality.applyAttemptLookup.find(sourceTx)
    if (actualReceipt != intent.sourceReceipt ||
        attempt?.state != ApplyAttemptJournalState.COMMITTED ||
        attempt.planFingerprint != intent.sourceReceipt.planFingerprint
    ) return@runCatching null

    val target = when (intent.sourceOperationId) {
        ReminderOperations.CREATE -> {
            val original = ReminderCreatePayload.decode(intent.originalCreatePayload)
                ?: return@runCatching null
            if (original.id.value != intent.targetObjectId ||
                !original.enabled || original.linkedMatterId != null ||
                intent.cancelledAtEpochMillis <= original.updatedAtEpochMillis
            ) return@runCatching null
            val owner = reality.reminderCancellation.read()
            if (owner.rules.singleOrNull { it.id == original.id } != original ||
                owner.occurrences.any { it.ruleId == original.id } ||
                prepared.dependencyStamps.asCanonicalList() !=
                    listOf(ReminderOwnerDependency.stamp(owner))
            ) return@runCatching null
            UndoOwnerTarget.Reminder(original, owner.revision)
        }
        MonthlyPlanOperations.ITEM_ADD -> {
            val original = MonthlyPlanItemAddPayload.decode(intent.originalCreatePayload)
                ?: return@runCatching null
            if (original.id.value != intent.targetObjectId ||
                original.status != MonthlyPlanItemStatus.PLANNED ||
                original.paymentConfirmedAtEpochMillis != null ||
                intent.cancelledAtEpochMillis <= original.updatedAtEpochMillis
            ) return@runCatching null
            val owner = reality.monthlyPlanCancellation.read()
            if (owner.items.singleOrNull { it.id == original.id } != original ||
                prepared.dependencyStamps.asCanonicalList() !=
                    listOf(MonthlyPlanOwnerDependency.stamp(owner))
            ) return@runCatching null
            UndoOwnerTarget.Monthly(original, owner.revision)
        }
        else -> return@runCatching null
    }
    intent to target
}.getOrNull()

/**
 * Executes only while a fresh ApplyTransactionScope is active; the underlying
 * owner CAS updates the row without deleting it and commits with a NEW receipt.
 * Never call from a model, arbitrary UI button, or without host authorization.
 */
internal class RealityPlanningUndoExecuteBinding(
    private val reality: RealityStore,
    private val catalog: OperationContractLookupPort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        RealityPlanningUndoOperations.EXECUTE,
        RealityPlanningUndoOperations.EXECUTOR_ID,
        ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val (intent, target) = admittedUndoTarget(
            preparedOperation, catalog, reality,
        ) ?: return OperationBindingExecutionResult.Rejected(
            "undo_origin_or_owner_unavailable",
        )
        if (intent.sourceReceipt.transactionId == transactionScope.transactionId)
            return OperationBindingExecutionResult.Rejected("undo_requires_new_transaction")
        when (target) {
            is UndoOwnerTarget.Reminder -> reality.reminderCancellation.cancelRule(
                target.original, intent.cancelledAtEpochMillis,
                target.ownerRevision, transactionScope,
            )
            is UndoOwnerTarget.Monthly -> reality.monthlyPlanCancellation.cancelItem(
                target.original, intent.cancelledAtEpochMillis,
                target.ownerRevision, transactionScope,
            )
        }
        return OperationBindingExecutionResult.Completed
    }
}

/** Preflight cannot grant ACL or verify Workspace historical attribution. */
internal class RealityPlanningUndoExecuteRevalidation(
    private val reality: RealityStore,
    private val catalog: OperationContractLookupPort,
) : OperationRevalidationPort {
    override val operationId = RealityPlanningUndoOperations.EXECUTE

    override fun revalidate(
        preparedOperation: PreparedOperation,
    ): OperationRevalidationResult =
        if (admittedUndoTarget(preparedOperation, catalog, reality) == null)
            OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED,
                "undo_origin_or_owner_unavailable",
            )
        else OperationRevalidationResult.Valid
}
