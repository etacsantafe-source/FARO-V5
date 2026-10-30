package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PreparedPlanEnvelopeCodecTest {
    private fun node(
        id: String,
        payload: ByteArray = "secret ${id}".toByteArray(),
        dependsOn: Set<PlanNodeId> = emptySet(),
        stampValue: String = "revision:1",
    ) = ApplyPlanNode(
        PlanNodeId(id),
        PreparedOperation(
            OperationContractDescriptor(
                OperationId("finance.add"), CapabilityId("N-025"), ExecutorId("finance"),
                ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
                ContractFingerprint("contract-fp-1"), ContractExposure.BINDABLE,
            ),
            CanonicalPayload.fromEncoded("json-v1", payload),
            DependencyStampSet.of(
                DependencyStamp(AuthorityBoundaryId("reality"), DependencyKey("budget"),
                    DependencyStampValue(stampValue))
            ),
            IdempotencyKey("intent-${id}"),
        ),
        dependsOn,
    )

    private fun sample() = PreparedPlan.of(
        node("b", dependsOn = setOf(PlanNodeId("a"))), node("a"),
    )

    @Test fun exactRoundTripIncludesPayloadAndDependencies() {
        val plan = sample()
        val decoded = PreparedPlanEnvelopeCodec.decode(PreparedPlanEnvelopeCodec.encode(plan))
        assertEquals(plan.planFingerprint, decoded.planFingerprint)
        assertEquals(plan.applyPlan.nodesInTopologicalOrder(), decoded.applyPlan.nodesInTopologicalOrder())
        assertArrayEquals(
            plan.applyPlan.node(PlanNodeId("a"))!!.preparedOperation.payload.copyBytes(),
            decoded.applyPlan.node(PlanNodeId("a"))!!.preparedOperation.payload.copyBytes(),
        )
    }

    @Test fun deterministicIndependentOfConstructorOrder() {
        val a = node("a")
        val b = node("b", dependsOn = setOf(PlanNodeId("a")))
        assertArrayEquals(PreparedPlanEnvelopeCodec.encode(PreparedPlan.of(a, b)),
            PreparedPlanEnvelopeCodec.encode(PreparedPlan.of(b, a)))
    }

    @Test fun frozenInputBytesNeverAliasTheEncodedEnvelope() {
        val bytes = PreparedPlanEnvelopeCodec.encode(sample())
        val read = PreparedPlanEnvelopeCodec.decode(bytes)
        bytes.fill(0)
        assertEquals(sample().planFingerprint, read.planFingerprint)
    }

    @Test fun payloadMutationChangesFingerprintEvenAfterReconstruction() {
        val a = PreparedPlanEnvelopeCodec.decode(PreparedPlanEnvelopeCodec.encode(sample()))
        val b = PreparedPlanEnvelopeCodec.decode(PreparedPlanEnvelopeCodec.encode(
            PreparedPlan.of(node("a", "modified".toByteArray()), node("b",
                dependsOn = setOf(PlanNodeId("a"))))))
        assertNotEquals(a.planFingerprint, b.planFingerprint)
    }

    @Test fun dependencyStampMutationChangesFingerprint() {
        val a = PreparedPlanEnvelopeCodec.decode(PreparedPlanEnvelopeCodec.encode(PreparedPlan.of(node("a"))))
        val b = PreparedPlanEnvelopeCodec.decode(PreparedPlanEnvelopeCodec.encode(
            PreparedPlan.of(node("a", stampValue = "revision:2"))))
        assertNotEquals(a.planFingerprint, b.planFingerprint)
    }

    @Test fun bitFlipInPayloadFailsClosed() {
        val data = PreparedPlanEnvelopeCodec.encode(sample())
        // Payload framing has a predictable non-header region; tamper one byte in a known string.
        val mark = "secret a".toByteArray()
        val i = data.indices.firstOrNull { it + mark.size <= data.size &&
            data.copyOfRange(it, it + mark.size).contentEquals(mark) }!!
        data[i] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            PreparedPlanEnvelopeCodec.decode(data)
        }
    }

    @Test fun changedDeclaredFingerprintFailsClosed() {
        val data = PreparedPlanEnvelopeCodec.encode(sample())
        data[12] = if (data[12] == 'a'.code.toByte()) 'b'.code.toByte() else 'a'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) { PreparedPlanEnvelopeCodec.decode(data) }
    }

    @Test fun unsupportedVersionFailsClosed() {
        val bytes = PreparedPlanEnvelopeCodec.encode(sample())
        bytes[7] = 2
        assertThrows(IllegalArgumentException::class.java) { PreparedPlanEnvelopeCodec.decode(bytes) }
    }

    @Test fun truncatedDataFailsClosed() {
        val data = PreparedPlanEnvelopeCodec.encode(sample())
        assertThrows(Exception::class.java) {
            PreparedPlanEnvelopeCodec.decode(data.copyOf(data.size - 1))
        }
    }

    @Test fun trailingDataFailsClosed() {
        val data = PreparedPlanEnvelopeCodec.encode(sample()) + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { PreparedPlanEnvelopeCodec.decode(data) }
    }

    @Test fun excessivePayloadRejectedAtEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            PreparedPlanEnvelopeCodec.encode(
                PreparedPlan.of(node("a", ByteArray(65_537)))
            )
        }
    }

    @Test fun emptyOrOversizedEnvelopeRejectedAtIngress() {
        assertThrows(IllegalArgumentException::class.java) {
            PreparedPlanEnvelopeCodec.decode(byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreparedPlanEnvelopeCodec.decode(ByteArray(PreparedPlanEnvelopeCodec.MAX_ENVELOPE_BYTES + 1))
        }
    }
}
