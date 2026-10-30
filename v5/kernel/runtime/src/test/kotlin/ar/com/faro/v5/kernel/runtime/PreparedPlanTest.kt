package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PreparedPlanTest {
    @Test
    fun constructorOrderCannotChangePreparedPlanFingerprint() {
        val a = node("a")
        val b = node("b")
        val c = node(
            "c",
            dependsOn = setOf(PlanNodeId("a"), PlanNodeId("b")),
        )

        val first = PreparedPlan.of(c, b, a)
        val second = PreparedPlan.of(a, c, b)

        assertEquals(first.planFingerprint, second.planFingerprint)
        assertEquals(
            listOf(PlanNodeId("a"), PlanNodeId("b"), PlanNodeId("c")),
            first.applyPlan.topologicalOrder,
        )
    }

    @Test
    fun authorizationReferenceIsBoundToExactPreparedPlanFingerprint() {
        val preparedPlan = PreparedPlan.of(node("a"))

        assertEquals(
            PlanAuthorizationRef(preparedPlan.planFingerprint),
            preparedPlan.authorizationRef,
        )
    }

    @Test
    fun materialPayloadChangeCreatesDifferentPreparedPlanFingerprint() {
        val first = PreparedPlan.of(node("a", payload = "amount=100"))
        val changed = PreparedPlan.of(node("a", payload = "amount=101"))

        assertNotEquals(first.planFingerprint, changed.planFingerprint)
    }

    @Test
    fun invalidDagFailsBeforePreparedPlanExists() {
        assertThrows(IllegalArgumentException::class.java) {
            PreparedPlan.of(
                node("a", dependsOn = setOf(PlanNodeId("b"))),
                node("b", dependsOn = setOf(PlanNodeId("a"))),
            )
        }
    }

    @Test
    fun preparedPlanStringDoesNotExposeCanonicalPayload() {
        val preparedPlan = PreparedPlan.of(
            node("a", payload = "very-secret-payload")
        )

        assertEquals(false, preparedPlan.toString().contains("very-secret-payload"))
    }

    private fun node(
        id: String,
        payload: String = "payload-" + id,
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
                    payload.toByteArray(),
                ),
                dependencyStamps = DependencyStampSet.of(
                    DependencyStamp(
                        authorityBoundary = AuthorityBoundaryId("reality.finance"),
                        dependencyKey = DependencyKey("entity:" + id),
                        observedValue = DependencyStampValue("revision:1"),
                    )
                ),
                idempotencyKey = IdempotencyKey("intent-" + id),
            ),
            dependsOn = dependsOn,
        )
}
