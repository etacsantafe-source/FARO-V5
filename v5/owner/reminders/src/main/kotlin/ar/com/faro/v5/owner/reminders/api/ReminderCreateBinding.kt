package ar.com.faro.v5.owner.reminders.api

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.reminders.spi.ReminderPersistencePort

/**
 * N033B3 official D-229 operation binding. NOT registered in the Android host.
 * No scope here grants authorization: N033C must supply Review, ACL and generation fences.
 */
class ReminderCreateBinding(
    private val persistence: ReminderPersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = ReminderOperations.CREATE,
        canonicalExecutor = ReminderOperations.EXECUTOR_ID,
        executionProfile = ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        if (!reminderContractMatches(preparedOperation, catalog)) {
            return OperationBindingExecutionResult.Rejected("reminder_create_contract_mismatch")
        }
        val rule = ReminderCreatePayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("reminder_create_payload_invalid")
        if (rule.linkedMatterId != null) {
            return OperationBindingExecutionResult.Rejected("reminder_create_matter_acl_unavailable")
        }
        val snapshot = runCatching { persistence.read() }.getOrNull()
            ?: return OperationBindingExecutionResult.Rejected("reminder_owner_unavailable")
        val revision = reminderExpectedRevision(preparedOperation, snapshot)
            ?: return OperationBindingExecutionResult.Rejected("reminder_create_stale_owner")
        if (snapshot.rules.any { it.id == rule.id }) {
            return OperationBindingExecutionResult.Rejected("reminder_create_duplicate_id")
        }
        // N033A validates the real active lease and CAS INSIDE the one Reality transaction.
        persistence.createRule(rule, revision, transactionScope)
        return OperationBindingExecutionResult.Completed
    }
}

/** Owner-side final semantic check; cannot replace active-host ACL or user Review. */
class ReminderCreateRevalidation(
    private val persistence: ReminderPersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationRevalidationPort {
    override val operationId = ReminderOperations.CREATE

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult {
        if (!reminderContractMatches(preparedOperation, catalog)) {
            return reminderRejected("reminder_create_contract_mismatch", policy = true)
        }
        val rule = ReminderCreatePayload.decode(preparedOperation.payload)
            ?: return reminderRejected("reminder_create_payload_invalid")
        if (rule.linkedMatterId != null) {
            return reminderRejected("reminder_create_matter_acl_unavailable", policy = true)
        }
        val snapshot = runCatching { persistence.read() }.getOrNull()
            ?: return reminderRejected("reminder_owner_unavailable", policy = true)
        if (reminderExpectedRevision(preparedOperation, snapshot) == null) {
            return reminderRejected("reminder_create_stale_owner")
        }
        if (snapshot.rules.any { it.id == rule.id }) {
            return reminderRejected("reminder_create_duplicate_id")
        }
        return OperationRevalidationResult.Valid
    }
}

private fun reminderContractMatches(
    prepared: PreparedOperation,
    catalog: OperationContractLookupPort,
): Boolean {
    val contract = prepared.contract
    return contract.operationId == ReminderOperations.CREATE &&
        contract.capabilityId == ReminderOperations.CAPABILITY_ID &&
        contract.canonicalExecutor == ReminderOperations.EXECUTOR_ID &&
        contract.executionProfile == ExecutionProfile.REALITY_MUTATION &&
        contract.exposure == ContractExposure.BINDABLE &&
        catalog.current(ReminderOperations.CREATE) == contract
}

/** Exactly one owner revision dependency. Extra or cross-owner stamps fail closed. */
private fun reminderExpectedRevision(
    prepared: PreparedOperation,
    current: ReminderSnapshot,
): Long? =
    current.revision.takeIf {
        prepared.dependencyStamps.asCanonicalList() ==
            listOf(ReminderOwnerDependency.stamp(current))
    }

private fun reminderRejected(
    code: String,
    policy: Boolean = false,
): OperationRevalidationResult.Rejected =
    OperationRevalidationResult.Rejected(
        failure = if (policy) OperationRevalidationFailure.POLICY_REJECTED
            else OperationRevalidationFailure.PRECONDITION_FAILED,
        code = code,
    )
