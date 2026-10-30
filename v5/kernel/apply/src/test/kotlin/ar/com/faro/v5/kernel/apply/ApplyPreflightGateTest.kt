package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyPreflightGateTest {
    @Test
    fun exactAuthorizationContractDependencyAndRevalidationAreAccepted() {
        val descriptor = descriptor()
        val gate = gate(
            descriptor = descriptor,
            revalidators = listOf(validRevalidator(descriptor.operationId)),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Accepted,
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
    }

    @Test
    fun mismatchedAuthorizationFailsBeforeAnyRevalidationLookup() {
        val descriptor = descriptor()
        var contractLookups = 0
        var dependencyLookups = 0
        var semanticRevalidations = 0
        val gate = ApplyPreflightGate(
            contracts = OperationContractLookupPort {
                contractLookups += 1
                descriptor
            },
            dependencyStamps = DependencyStampLookupPort { _, _ ->
                dependencyLookups += 1
                DependencyStampValue("revision:1")
            },
            operationRevalidators = listOf(
                revalidator(descriptor.operationId) {
                    semanticRevalidations += 1
                    OperationRevalidationResult.Valid
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                ApplyPreflightRejection.AUTHORIZATION_MISMATCH,
            ),
            gate.validate(
                plan,
                PlanAuthorizationRef(PlanFingerprint("different-plan")),
            ),
        )
        assertEquals(0, contractLookups)
        assertEquals(0, dependencyLookups)
        assertEquals(0, semanticRevalidations)
    }

    @Test
    fun unknownPreparedContractFailsBeforeDependencyOrSemanticRevalidation() {
        var dependencyLookups = 0
        var semanticRevalidations = 0
        val descriptor = descriptor()
        val gate = ApplyPreflightGate(
            contracts = OperationContractLookupPort { null },
            dependencyStamps = DependencyStampLookupPort { _, _ ->
                dependencyLookups += 1
                DependencyStampValue("revision:1")
            },
            operationRevalidators = listOf(
                revalidator(descriptor.operationId) {
                    semanticRevalidations += 1
                    OperationRevalidationResult.Valid
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.CONTRACT_UNKNOWN,
                nodeId = PlanNodeId("node-a"),
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
        assertEquals(0, dependencyLookups)
        assertEquals(0, semanticRevalidations)
    }

    @Test
    fun changedContractFailsBeforeDependencyOrSemanticRevalidation() {
        var dependencyLookups = 0
        var semanticRevalidations = 0
        val preparedDescriptor = descriptor()
        val currentDescriptor = preparedDescriptor.copy(
            contractFingerprint = ContractFingerprint("contract-new"),
        )
        val gate = ApplyPreflightGate(
            contracts = lookup(currentDescriptor),
            dependencyStamps = DependencyStampLookupPort { _, _ ->
                dependencyLookups += 1
                DependencyStampValue("revision:1")
            },
            operationRevalidators = listOf(
                revalidator(preparedDescriptor.operationId) {
                    semanticRevalidations += 1
                    OperationRevalidationResult.Valid
                }
            ),
        )
        val plan = plan(preparedDescriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.CONTRACT_CHANGED,
                nodeId = PlanNodeId("node-a"),
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
        assertEquals(0, dependencyLookups)
        assertEquals(0, semanticRevalidations)
    }

    @Test
    fun changedDependencyFailsClosedBeforeSemanticRevalidation() {
        var semanticRevalidations = 0
        val descriptor = descriptor()
        val gate = ApplyPreflightGate(
            contracts = lookup(descriptor),
            dependencyStamps = currentDependency("revision:2"),
            operationRevalidators = listOf(
                revalidator(descriptor.operationId) {
                    semanticRevalidations += 1
                    OperationRevalidationResult.Valid
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.STALE_DEPENDENCY,
                nodeId = PlanNodeId("node-a"),
                authorityBoundary = AuthorityBoundaryId("reality.finance"),
                dependencyKey = DependencyKey("entity:fixture"),
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
        assertEquals(0, semanticRevalidations)
    }

    @Test
    fun missingDependencyFailsClosedBeforeSemanticRevalidation() {
        var semanticRevalidations = 0
        val descriptor = descriptor()
        val gate = ApplyPreflightGate(
            contracts = lookup(descriptor),
            dependencyStamps = DependencyStampLookupPort { _, _ -> null },
            operationRevalidators = listOf(
                revalidator(descriptor.operationId) {
                    semanticRevalidations += 1
                    OperationRevalidationResult.Valid
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.STALE_DEPENDENCY,
                nodeId = PlanNodeId("node-a"),
                authorityBoundary = AuthorityBoundaryId("reality.finance"),
                dependencyKey = DependencyKey("entity:fixture"),
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
        assertEquals(0, semanticRevalidations)
    }

    @Test
    fun missingOperationRevalidatorFailsClosed() {
        val descriptor = descriptor()
        val gate = gate(descriptor = descriptor, revalidators = emptyList())
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.REVALIDATION_UNAVAILABLE,
                nodeId = PlanNodeId("node-a"),
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
    }

    @Test
    fun policyRejectionFailsClosedWithTypedCode() {
        val descriptor = descriptor()
        val gate = gate(
            descriptor = descriptor,
            revalidators = listOf(
                revalidator(descriptor.operationId) {
                    OperationRevalidationResult.Rejected(
                        failure = OperationRevalidationFailure.POLICY_REJECTED,
                        code = "approval_window_closed",
                    )
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.POLICY_REJECTED,
                nodeId = PlanNodeId("node-a"),
                code = "approval_window_closed",
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
    }

    @Test
    fun failedPreconditionFailsClosedWithTypedCode() {
        val descriptor = descriptor()
        val gate = gate(
            descriptor = descriptor,
            revalidators = listOf(
                revalidator(descriptor.operationId) {
                    OperationRevalidationResult.Rejected(
                        failure = OperationRevalidationFailure.PRECONDITION_FAILED,
                        code = "account_closed",
                    )
                }
            ),
        )
        val plan = plan(descriptor, PlanFingerprint("plan-current"))

        assertEquals(
            ApplyPreflightResult.Rejected(
                reason = ApplyPreflightRejection.PRECONDITION_FAILED,
                nodeId = PlanNodeId("node-a"),
                code = "account_closed",
            ),
            gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)),
        )
    }

    private fun gate(
        descriptor: OperationContractDescriptor,
        revalidators: List<OperationRevalidationPort>,
    ) = ApplyPreflightGate(
        contracts = lookup(descriptor),
        dependencyStamps = currentDependency("revision:1"),
        operationRevalidators = revalidators,
    )

    private fun lookup(
        descriptor: OperationContractDescriptor,
    ): OperationContractLookupPort =
        OperationContractLookupPort { operationId ->
            descriptor.takeIf { it.operationId == operationId }
        }

    private fun currentDependency(value: String): DependencyStampLookupPort =
        DependencyStampLookupPort { authorityBoundary, dependencyKey ->
            if (
                authorityBoundary == AuthorityBoundaryId("reality.finance") &&
                dependencyKey == DependencyKey("entity:fixture")
            ) {
                DependencyStampValue(value)
            } else {
                null
            }
        }

    private fun validRevalidator(operationId: OperationId): OperationRevalidationPort =
        revalidator(operationId) { OperationRevalidationResult.Valid }

    private fun revalidator(
        id: OperationId,
        body: () -> OperationRevalidationResult,
    ): OperationRevalidationPort =
        object : OperationRevalidationPort {
            override val operationId: OperationId = id

            override fun revalidate(
                preparedOperation: PreparedOperation,
            ): OperationRevalidationResult = body()
        }

    private fun descriptor(): OperationContractDescriptor =
        OperationContractDescriptor(
            operationId = OperationId("fixture.apply"),
            capabilityId = CapabilityId("N-025"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("contract-current"),
            exposure = ContractExposure.BINDABLE,
        )

    private fun plan(
        descriptor: OperationContractDescriptor,
        fingerprint: PlanFingerprint,
    ): PreparedPlanView {
        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("node-a"),
                preparedOperation = PreparedOperation(
                    contract = descriptor,
                    payload = CanonicalPayload.fromEncoded(
                        "fixture-v1",
                        "amount=100".toByteArray(),
                    ),
                    dependencyStamps = DependencyStampSet.of(
                        DependencyStamp(
                            authorityBoundary = AuthorityBoundaryId("reality.finance"),
                            dependencyKey = DependencyKey("entity:fixture"),
                            observedValue = DependencyStampValue("revision:1"),
                        )
                    ),
                    idempotencyKey = IdempotencyKey("intent-a"),
                ),
                dependsOn = emptySet(),
            )
        )
        return object : PreparedPlanView {
            override val applyPlan = applyPlan
            override val planFingerprint = fingerprint
        }
    }
}
