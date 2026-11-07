package ar.com.faro.v5.owner.monthlyplan.api

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanPersistencePort

/** N033B3: add a PLANNED item only, never confirm a Finance payment. Not mounted in host. */
class MonthlyPlanItemAddBinding(
    private val persistence: MonthlyPlanPersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = MonthlyPlanOperations.ITEM_ADD,
        canonicalExecutor = MonthlyPlanOperations.EXECUTOR_ID,
        executionProfile = ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        if (!monthlyContractMatches(preparedOperation, catalog)) {
            return OperationBindingExecutionResult.Rejected("monthly_add_contract_mismatch")
        }
        val item = MonthlyPlanItemAddPayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected("monthly_add_payload_invalid")
        if (item.status != MonthlyPlanItemStatus.PLANNED ||
            item.paymentConfirmedAtEpochMillis != null
        ) {
            return OperationBindingExecutionResult.Rejected("monthly_add_payment_state_denied")
        }
        val snapshot = runCatching { persistence.read() }.getOrNull()
            ?: return OperationBindingExecutionResult.Rejected("monthly_owner_unavailable")
        val revision = monthlyExpectedRevision(preparedOperation, snapshot)
            ?: return OperationBindingExecutionResult.Rejected("monthly_add_stale_owner")
        if (snapshot.items.any { it.id == item.id }) {
            return OperationBindingExecutionResult.Rejected("monthly_add_duplicate_id")
        }
        persistence.createItem(item, revision, transactionScope)
        return OperationBindingExecutionResult.Completed
    }
}

/** Preflight of the exact prepared intent against real Reality8 owner state. */
class MonthlyPlanItemAddRevalidation(
    private val persistence: MonthlyPlanPersistencePort,
    private val catalog: OperationContractLookupPort,
) : OperationRevalidationPort {
    override val operationId = MonthlyPlanOperations.ITEM_ADD

    override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult {
        if (!monthlyContractMatches(preparedOperation, catalog)) {
            return monthlyRejected("monthly_add_contract_mismatch", policy = true)
        }
        val item = MonthlyPlanItemAddPayload.decode(preparedOperation.payload)
            ?: return monthlyRejected("monthly_add_payload_invalid")
        if (item.status != MonthlyPlanItemStatus.PLANNED ||
            item.paymentConfirmedAtEpochMillis != null
        ) {
            return monthlyRejected("monthly_add_payment_state_denied", policy = true)
        }
        val snapshot = runCatching { persistence.read() }.getOrNull()
            ?: return monthlyRejected("monthly_owner_unavailable", policy = true)
        if (monthlyExpectedRevision(preparedOperation, snapshot) == null) {
            return monthlyRejected("monthly_add_stale_owner")
        }
        if (snapshot.items.any { it.id == item.id }) {
            return monthlyRejected("monthly_add_duplicate_id")
        }
        return OperationRevalidationResult.Valid
    }
}

private fun monthlyContractMatches(
    prepared: PreparedOperation,
    catalog: OperationContractLookupPort,
): Boolean {
    val contract = prepared.contract
    return contract.operationId == MonthlyPlanOperations.ITEM_ADD &&
        contract.capabilityId == MonthlyPlanOperations.CAPABILITY_ID &&
        contract.canonicalExecutor == MonthlyPlanOperations.EXECUTOR_ID &&
        contract.executionProfile == ExecutionProfile.REALITY_MUTATION &&
        contract.exposure == ContractExposure.BINDABLE &&
        catalog.current(MonthlyPlanOperations.ITEM_ADD) == contract
}

private fun monthlyExpectedRevision(
    prepared: PreparedOperation,
    current: MonthlyPlanSnapshot,
): Long? =
    current.revision.takeIf {
        prepared.dependencyStamps.asCanonicalList() ==
            listOf(MonthlyPlanOwnerDependency.stamp(current))
    }

private fun monthlyRejected(
    code: String,
    policy: Boolean = false,
): OperationRevalidationResult.Rejected =
    OperationRevalidationResult.Rejected(
        failure = if (policy) OperationRevalidationFailure.POLICY_REJECTED
            else OperationRevalidationFailure.PRECONDITION_FAILED,
        code = code,
    )
