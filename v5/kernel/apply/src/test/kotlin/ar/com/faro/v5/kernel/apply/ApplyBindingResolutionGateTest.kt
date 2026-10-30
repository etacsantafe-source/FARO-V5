package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApplyBindingResolutionGateTest {
    @Test
    fun exactResolvedBindingSetIsReturnedInPlanOrder() {
        val plan = plan()
        val a = binding("fixture.a", "finance", ExecutionProfile.REALITY_MUTATION)
        val b = binding("fixture.b", "planning", ExecutionProfile.REALITY_MUTATION)

        val result = ApplyBindingResolutionGate(listOf(b, a)).resolve(plan)
            as ApplyBindingResolution.Resolved

        assertEquals(listOf(PlanNodeId("a"), PlanNodeId("b")), result.bindingsInPlanOrder.map { it.nodeId })
        assertSame(a, result.bindingsInPlanOrder[0].binding)
        assertSame(b, result.bindingsInPlanOrder[1].binding)
    }

    @Test
    fun missingBindingFailsClosed() {
        val result = ApplyBindingResolutionGate(
            listOf(binding("fixture.a", "finance", ExecutionProfile.REALITY_MUTATION))
        ).resolve(plan())

        assertEquals(
            ApplyBindingResolution.Rejected(
                ApplyBindingRejection.BINDING_UNAVAILABLE,
                PlanNodeId("b"),
            ),
            result,
        )
    }

    @Test
    fun executorMismatchFailsClosed() {
        val result = ApplyBindingResolutionGate(
            listOf(
                binding("fixture.a", "wrong", ExecutionProfile.REALITY_MUTATION),
                binding("fixture.b", "planning", ExecutionProfile.REALITY_MUTATION),
            )
        ).resolve(plan())

        assertEquals(
            ApplyBindingResolution.Rejected(
                ApplyBindingRejection.BINDING_EXECUTOR_MISMATCH,
                PlanNodeId("a"),
            ),
            result,
        )
    }

    @Test
    fun profileMismatchFailsClosed() {
        val result = ApplyBindingResolutionGate(
            listOf(
                binding("fixture.a", "finance", ExecutionProfile.READ_ONLY),
                binding("fixture.b", "planning", ExecutionProfile.REALITY_MUTATION),
            )
        ).resolve(plan())

        assertEquals(
            ApplyBindingResolution.Rejected(
                ApplyBindingRejection.BINDING_PROFILE_MISMATCH,
                PlanNodeId("a"),
            ),
            result,
        )
    }

    @Test
    fun duplicateBindingFailsAtConstruction() {
        val a = binding("fixture.a", "finance", ExecutionProfile.REALITY_MUTATION)

        assertThrows<IllegalArgumentException> {
            ApplyBindingResolutionGate(listOf(a, a))
        }
    }

    private fun binding(
        operationId: String,
        executor: String,
        profile: ExecutionProfile,
    ) = object : OperationBindingPort {
        override val descriptor = OperationBindingDescriptor(
            operationId = OperationId(operationId),
            canonicalExecutor = ExecutorId(executor),
            executionProfile = profile,
        )
        override fun execute(preparedOperation: PreparedOperation, transactionScope: RealityTransactionScope): OperationBindingExecutionResult = OperationBindingExecutionResult.Completed
    }

    private fun plan(): PreparedPlanView {
        val a = node(
            nodeId = "a",
            operationId = "fixture.a",
            executor = "finance",
            dependsOn = emptySet(),
        )
        val b = node(
            nodeId = "b",
            operationId = "fixture.b",
            executor = "planning",
            dependsOn = setOf(PlanNodeId("a")),
        )
        val applyPlan = ApplyPlan.of(b, a)
        return object : PreparedPlanView {
            override val applyPlan = applyPlan
            override val planFingerprint = PlanFingerprint("plan-bindings")
        }
    }

    private fun node(
        nodeId: String,
        operationId: String,
        executor: String,
        dependsOn: Set<PlanNodeId>,
    ) = ApplyPlanNode(
        nodeId = PlanNodeId(nodeId),
        preparedOperation = PreparedOperation(
            contract = OperationContractDescriptor(
                operationId = OperationId(operationId),
                capabilityId = CapabilityId("N-025"),
                canonicalExecutor = ExecutorId(executor),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
                contractVersion = ContractVersion("1"),
                contractFingerprint = ContractFingerprint("contract-" + operationId),
                exposure = ContractExposure.BINDABLE,
            ),
            payload = CanonicalPayload.fromEncoded(
                "fixture-v1",
                operationId.toByteArray(),
            ),
            dependencyStamps = DependencyStampSet.of(
                DependencyStamp(
                    authorityBoundary = AuthorityBoundaryId("reality." + executor),
                    dependencyKey = DependencyKey("entity:" + operationId),
                    observedValue = DependencyStampValue("revision:1"),
                )
            ),
            idempotencyKey = IdempotencyKey("intent-" + operationId),
        ),
        dependsOn = dependsOn,
    )
}
