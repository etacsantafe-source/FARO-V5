package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApplyPlanTest {
    private fun node(
        id: String,
        dependsOn: Set<PlanNodeId> = emptySet(),
    ): ApplyPlanNode =
        ApplyPlanNode(
            nodeId = PlanNodeId(id),
            preparedOperation = PreparedOperation(
                contract = OperationContractDescriptor(
                    operationId = OperationId("fixture." + id),
                    capabilityId = CapabilityId("N-025"),
                    canonicalExecutor = ExecutorId("finance"),
                    executionProfile = ExecutionProfile.REALITY_MUTATION,
                    contractVersion = ContractVersion("1"),
                    contractFingerprint = ContractFingerprint("contract-" + id),
                    exposure = ContractExposure.BINDABLE,
                ),
                payload = CanonicalPayload.fromEncoded(
                    "fixture-v1",
                    id.toByteArray(),
                ),
                dependencyStamps = DependencyStampSet.empty(),
                idempotencyKey = IdempotencyKey("idem-" + id),
            ),
            dependsOn = dependsOn,
        )

    @Test
    fun applyPlanRejectsEmptyPlan() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplyPlan.of()
        }
    }

    @Test
    fun applyPlanRejectsDuplicateNodeIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplyPlan.of(node("a"), node("a"))
        }
    }

    @Test
    fun applyPlanRejectsMissingDependency() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplyPlan.of(
                node("b", setOf(PlanNodeId("missing"))),
            )
        }
    }

    @Test
    fun applyPlanRejectsSelfDependency() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplyPlan.of(
                node("a", setOf(PlanNodeId("a"))),
            )
        }
    }

    @Test
    fun applyPlanRejectsCycles() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplyPlan.of(
                node("a", setOf(PlanNodeId("b"))),
                node("b", setOf(PlanNodeId("a"))),
            )
        }
    }

    @Test
    fun topologicalOrderIsStableAndIndependentOfInputOrder() {
        val a = node("a")
        val b = node("b")
        val c = node("c", setOf(PlanNodeId("a"), PlanNodeId("b")))
        val d = node("d", setOf(PlanNodeId("b")))

        val first = ApplyPlan.of(d, c, b, a)
        val second = ApplyPlan.of(a, b, c, d)

        val expected = listOf(
            PlanNodeId("a"),
            PlanNodeId("b"),
            PlanNodeId("c"),
            PlanNodeId("d"),
        )

        assertEquals(expected, first.topologicalOrder)
        assertEquals(expected, second.topologicalOrder)
        assertEquals(expected, first.nodesInTopologicalOrder().map { it.nodeId })
    }
}
