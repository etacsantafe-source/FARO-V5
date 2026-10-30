package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlanFingerprinterTest {
    @Test
    fun sameSemanticPlanHasSameFingerprintRegardlessOfConstructorOrder() {
        val a = node("a", payload = "payload-a")
        val b = node("b", payload = "payload-b")
        val c = node(
            "c",
            payload = "payload-c",
            dependsOn = setOf(PlanNodeId("a"), PlanNodeId("b")),
        )

        val first = PlanFingerprinter.fingerprint(ApplyPlan.of(c, b, a))
        val second = PlanFingerprinter.fingerprint(ApplyPlan.of(a, c, b))

        assertEquals(first, second)
        assertEquals(64, first.value.length)
    }

    @Test
    fun payloadChangeChangesPlanFingerprint() {
        val first = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", payload = "payload-a"))
        )
        val changed = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", payload = "payload-b"))
        )

        assertNotEquals(first, changed)
    }

    @Test
    fun dependencyStampChangeChangesPlanFingerprint() {
        val first = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", stampValue = "revision:1"))
        )
        val changed = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", stampValue = "revision:2"))
        )

        assertNotEquals(first, changed)
    }

    @Test
    fun idempotencyIdentityChangeChangesPlanFingerprint() {
        val first = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", idempotency = "intent-1"))
        )
        val changed = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", idempotency = "intent-2"))
        )

        assertNotEquals(first, changed)
    }

    @Test
    fun contractFingerprintChangeChangesPlanFingerprint() {
        val first = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", contractFingerprint = "contract-v1"))
        )
        val changed = PlanFingerprinter.fingerprint(
            ApplyPlan.of(node("a", contractFingerprint = "contract-v2"))
        )

        assertNotEquals(first, changed)
    }

    @Test
    fun dependencyEdgeChangeChangesPlanFingerprint() {
        val a = node("a")
        val bIndependent = node("b")
        val bDependsOnA = node("b", dependsOn = setOf(PlanNodeId("a")))

        val first = PlanFingerprinter.fingerprint(ApplyPlan.of(a, bIndependent))
        val changed = PlanFingerprinter.fingerprint(ApplyPlan.of(a, bDependsOnA))

        assertNotEquals(first, changed)
    }

    private fun node(
        id: String,
        payload: String = "payload-" + id,
        stampValue: String = "revision:1",
        idempotency: String = "intent-" + id,
        contractFingerprint: String = "contract-" + id,
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
                    contractFingerprint = ContractFingerprint(contractFingerprint),
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
                        observedValue = DependencyStampValue(stampValue),
                    )
                ),
                idempotencyKey = IdempotencyKey(idempotency),
            ),
            dependsOn = dependsOn,
        )
}
