package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStampLookupPort
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.OperationRevalidationFailure
import ar.com.faro.v5.kernel.contracts.OperationRevalidationPort
import ar.com.faro.v5.kernel.contracts.OperationRevalidationResult
import ar.com.faro.v5.kernel.contracts.PlanAuthorizationRef
import ar.com.faro.v5.kernel.contracts.PlanNodeId
import ar.com.faro.v5.kernel.contracts.PreparedPlanView

enum class ApplyPreflightRejection {
    AUTHORIZATION_MISMATCH,
    CONTRACT_UNKNOWN,
    CONTRACT_CHANGED,
    STALE_DEPENDENCY,
    REVALIDATION_UNAVAILABLE,
    POLICY_REJECTED,
    PRECONDITION_FAILED,
}

sealed interface ApplyPreflightResult {
    data object Accepted : ApplyPreflightResult

    data class Rejected(
        val reason: ApplyPreflightRejection,
        val nodeId: PlanNodeId? = null,
        val authorityBoundary: AuthorityBoundaryId? = null,
        val dependencyKey: DependencyKey? = null,
        val code: String? = null,
    ) : ApplyPreflightResult
}

class ApplyPreflightGate(
    private val contracts: OperationContractLookupPort,
    private val dependencyStamps: DependencyStampLookupPort,
    operationRevalidators: Iterable<OperationRevalidationPort>,
) {
    private val revalidatorsByOperation: Map<OperationId, OperationRevalidationPort> =
        LinkedHashMap<OperationId, OperationRevalidationPort>().apply {
            operationRevalidators.forEach { revalidator ->
                require(putIfAbsent(revalidator.operationId, revalidator) == null) {
                    "Duplicate operation revalidator: ${revalidator.operationId.value}"
                }
            }
        }.toMap()

    fun validate(
        preparedPlan: PreparedPlanView,
        authorization: PlanAuthorizationRef,
    ): ApplyPreflightResult {
        if (authorization.planFingerprint != preparedPlan.planFingerprint) {
            return ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.AUTHORIZATION_MISMATCH,
            )
        }

        preparedPlan.applyPlan.nodesInTopologicalOrder().forEach { node ->
            val preparedOperation = node.preparedOperation
            val preparedContract = preparedOperation.contract
            val currentContract = contracts.current(preparedContract.operationId)
                ?: return ApplyPreflightResult.Rejected(
                    reason = ApplyPreflightRejection.CONTRACT_UNKNOWN,
                    nodeId = node.nodeId,
                )

            if (currentContract != preparedContract) {
                return ApplyPreflightResult.Rejected(
                    reason = ApplyPreflightRejection.CONTRACT_CHANGED,
                    nodeId = node.nodeId,
                )
            }

            preparedOperation.dependencyStamps.asCanonicalList().forEach { preparedStamp ->
                val currentValue = dependencyStamps.current(
                    preparedStamp.authorityBoundary,
                    preparedStamp.dependencyKey,
                )
                if (currentValue != preparedStamp.observedValue) {
                    return ApplyPreflightResult.Rejected(
                        reason = ApplyPreflightRejection.STALE_DEPENDENCY,
                        nodeId = node.nodeId,
                        authorityBoundary = preparedStamp.authorityBoundary,
                        dependencyKey = preparedStamp.dependencyKey,
                    )
                }
            }

            val revalidator = revalidatorsByOperation[preparedContract.operationId]
                ?: return ApplyPreflightResult.Rejected(
                    reason = ApplyPreflightRejection.REVALIDATION_UNAVAILABLE,
                    nodeId = node.nodeId,
                )

            when (val revalidation = revalidator.revalidate(preparedOperation)) {
                OperationRevalidationResult.Valid -> Unit
                is OperationRevalidationResult.Rejected -> {
                    val reason =
                        when (revalidation.failure) {
                            OperationRevalidationFailure.POLICY_REJECTED ->
                                ApplyPreflightRejection.POLICY_REJECTED
                            OperationRevalidationFailure.PRECONDITION_FAILED ->
                                ApplyPreflightRejection.PRECONDITION_FAILED
                        }
                    return ApplyPreflightResult.Rejected(
                        reason = reason,
                        nodeId = node.nodeId,
                        code = revalidation.code,
                    )
                }
            }
        }

        return ApplyPreflightResult.Accepted
    }
}
