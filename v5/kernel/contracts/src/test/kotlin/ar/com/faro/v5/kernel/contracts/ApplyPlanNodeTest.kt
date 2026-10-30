package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ApplyPlanNodeTest {
    private fun preparedOperation(): PreparedOperation =
        PreparedOperation(
            contract = OperationContractDescriptor(
                operationId = OperationId("finance.fixture"),
                capabilityId = CapabilityId("N-025"),
                canonicalExecutor = ExecutorId("finance"),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
                contractVersion = ContractVersion("1"),
                contractFingerprint = ContractFingerprint("contract-v1"),
                exposure = ContractExposure.BINDABLE,
            ),
            payload = CanonicalPayload.fromEncoded(
                "fixture-v1",
                "fixture".toByteArray(),
            ),
            dependencyStamps = DependencyStampSet.empty(),
            idempotencyKey = IdempotencyKey("fixture-1"),
        )

    @Test
    fun planNodeKeepsExplicitIdentityOperationAndDependencies() {
        val node = ApplyPlanNode(
            nodeId = PlanNodeId("node-b"),
            preparedOperation = preparedOperation(),
            dependsOn = setOf(PlanNodeId("node-a")),
        )

        assertEquals(PlanNodeId("node-b"), node.nodeId)
        assertEquals(setOf(PlanNodeId("node-a")), node.dependsOn)
    }

    @Test
    fun planNodeDefensivelyCopiesDependencySet() {
        val mutableDependencies = mutableSetOf(PlanNodeId("node-a"))
        val node = ApplyPlanNode(
            nodeId = PlanNodeId("node-b"),
            preparedOperation = preparedOperation(),
            dependsOn = mutableDependencies,
        )

        mutableDependencies += PlanNodeId("node-c")

        assertEquals(setOf(PlanNodeId("node-a")), node.dependsOn)
        assertFalse(PlanNodeId("node-c") in node.dependsOn)
    }
}
