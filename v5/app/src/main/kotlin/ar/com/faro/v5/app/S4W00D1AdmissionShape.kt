package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*

/**
 * W00-D1: inert shape/freshness check for a possible two-owner Review.
 *
 * A true result is NOT authorization, a lease, an Apply ticket, or permission
 * to commit. The requirements are supplied by the future trusted owner host;
 * this code does not discover authority, authenticate a principal, or fence
 * cross-process revocation between validation and SQL COMMIT.
 *
 * Not wired to any Activity or existing single-owner Planning8 host.
 */
internal data class S4W00D1ScopeRequirement(
    val nodeId: PlanNodeId,
    val operationId: OperationId,
    val authorityId: String,
    val objectId: String,
)

internal data class S4W00D1GrantObservation(
    val scope: S4W00D1ScopeRequirement,
    val planFingerprint: PlanFingerprint,
    val generationId: String,
    val principalId: String,
    val policyEpoch: String,
    val leaseNonce: String,
)

internal object S4W00D1AdmissionShape {
    private fun valid(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value.length <= 160 &&
            value.none { it.code < 32 || it.code == 127 }

    /**
     * Pure structural screening: both owners must have independently observed
     * non-reused leases. Read each grant twice to detect local changes during
     * this inspection; this cannot protect a later COMMIT. All exceptions DENY.
     */
    fun check(
        plan: PreparedPlanView,
        reviewedFingerprint: PlanFingerprint,
        requirements: List<S4W00D1ScopeRequirement>,
        presentedGrants: List<S4W00D1GrantObservation>,
        pinnedGenerationId: String,
        expectedHostEpoch: String,
        expectedPrincipalId: String,
        currentGenerationOrNull: () -> String?,
        currentHostEpochOrNull: () -> String?,
        currentGrantOrNull: (S4W00D1ScopeRequirement) -> S4W00D1GrantObservation?,
    ): Boolean = runCatching {
        if (plan.planFingerprint != reviewedFingerprint ||
            !valid(pinnedGenerationId) || !valid(expectedHostEpoch) ||
            !valid(expectedPrincipalId)
        ) return@runCatching false
        val nodes = plan.applyPlan.nodesInTopologicalOrder()
        if (nodes.size != 2 || requirements.size != 2 || presentedGrants.size != 2)
            return@runCatching false
        if (requirements.map { it.nodeId } != nodes.map { it.nodeId } ||
            requirements.map { it.nodeId }.toSet().size != 2 ||
            requirements.map { it.authorityId }.toSet().size != 2 ||
            requirements.map { it.objectId to it.authorityId }.toSet().size != 2
        ) return@runCatching false
        for (index in 0..1) {
            val required = requirements[index]
            val descriptor = nodes[index].preparedOperation.contract
            if (required.operationId != descriptor.operationId ||
                descriptor.exposure != ContractExposure.BINDABLE ||
                descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
                !valid(required.authorityId) || !valid(required.objectId)
            ) return@runCatching false
        }
        if (currentGenerationOrNull() != pinnedGenerationId ||
            currentHostEpochOrNull() != expectedHostEpoch
        ) return@runCatching false
        if (presentedGrants.map { it.scope } != requirements ||
            presentedGrants.map { it.leaseNonce }.toSet().size != 2
        ) return@runCatching false
        for (grant in presentedGrants) {
            if (grant.planFingerprint != reviewedFingerprint ||
                grant.generationId != pinnedGenerationId ||
                grant.principalId != expectedPrincipalId ||
                !valid(grant.policyEpoch) || !valid(grant.leaseNonce)
            ) return@runCatching false
        }
        // Re-read twice: avoid reporting structural freshness from just one
        // stale cache hit. The future host must still fence through COMMIT.
        for (pass in 0..1) {
            for (index in 0..1) {
                if (currentGrantOrNull(requirements[index]) != presentedGrants[index])
                    return@runCatching false
            }
        }
        currentGenerationOrNull() == pinnedGenerationId &&
            currentHostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)
}
