package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Lossless, bounded, versioned serialization of the *entire* frozen ApplyPlan.
 * Never serialize only a fingerprint or a human-readable proposal.
 *
 * Decoding reconstructs PreparedPlan through its canonical factory and verifies
 * its fingerprint. This is not approval, data egress authorization or Apply.
 * Persistence, encryption and generation fencing belong to Workspace/Maintenance.
 */
object PreparedPlanEnvelopeCodec {
    private const val MAGIC = 0x46505250 // FPRP
    private const val VERSION = 1
    const val MAX_ENVELOPE_BYTES = 524_288
    private const val MAX_FIELD_BYTES = 2_048
    private const val MAX_PAYLOAD_BYTES = 65_536
    private const val MAX_NODES = 64
    private const val MAX_STAMPS_PER_NODE = 128

    fun encode(plan: PreparedPlan): ByteArray {
        val nodes = plan.applyPlan.nodesInTopologicalOrder()
        require(nodes.size in 1..MAX_NODES) { "prepared plan node count is outside envelope bounds" }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeInt(MAGIC)
            stream.writeInt(VERSION)
            stream.field(plan.planFingerprint.value)
            stream.writeInt(nodes.size)
            nodes.forEach { node ->
                val prepared = node.preparedOperation
                val contract = prepared.contract
                stream.field(node.nodeId.value)
                stream.field(contract.operationId.value)
                stream.field(contract.capabilityId.value)
                stream.field(contract.canonicalExecutor.value)
                stream.field(contract.executionProfile.name)
                stream.field(contract.contractVersion.value)
                stream.field(contract.contractFingerprint.value)
                stream.field(contract.exposure.name)
                stream.field(prepared.payload.formatId)
                val payloadBytes = prepared.payload.copyBytes()
                require(payloadBytes.size <= MAX_PAYLOAD_BYTES) {
                    "prepared plan payload exceeds per-node envelope bound"
                }
                stream.writeInt(payloadBytes.size)
                stream.write(payloadBytes)
                stream.field(prepared.idempotencyKey.value)

                val stamps = prepared.dependencyStamps.asCanonicalList()
                require(stamps.size <= MAX_STAMPS_PER_NODE)
                stream.writeInt(stamps.size)
                stamps.forEach { stamp ->
                    stream.field(stamp.authorityBoundary.value)
                    stream.field(stamp.dependencyKey.value)
                    stream.field(stamp.observedValue.value)
                }
                val dependencies = node.dependsOn.sortedBy { it.value }
                stream.writeInt(dependencies.size)
                dependencies.forEach { stream.field(it.value) }
            }
        }
        val bytes = out.toByteArray()
        require(bytes.size <= MAX_ENVELOPE_BYTES) { "prepared plan envelope exceeds total bound" }
        return bytes
    }

    fun decode(encoded: ByteArray): PreparedPlan {
        require(encoded.size in 12..MAX_ENVELOPE_BYTES) { "prepared plan envelope size invalid" }
        val input = DataInputStream(ByteArrayInputStream(encoded))
        require(input.readInt() == MAGIC) { "unknown prepared plan envelope magic" }
        require(input.readInt() == VERSION) { "unsupported prepared plan envelope version" }
        val declaredFingerprint = input.field()
        require(declaredFingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "prepared plan fingerprint format invalid"
        }
        val count = input.readInt()
        require(count in 1..MAX_NODES) { "prepared plan envelope node count invalid" }
        val nodes = ArrayList<ApplyPlanNode>(count)
        repeat(count) {
            val nodeId = PlanNodeId(input.field())
            val contract = OperationContractDescriptor(
                operationId = OperationId(input.field()),
                capabilityId = CapabilityId(input.field()),
                canonicalExecutor = ExecutorId(input.field()),
                executionProfile = ExecutionProfile.valueOf(input.field()),
                contractVersion = ContractVersion(input.field()),
                contractFingerprint = ContractFingerprint(input.field()),
                exposure = ContractExposure.valueOf(input.field()),
            )
            val formatId = input.field()
            val payloadSize = input.readInt()
            require(payloadSize in 0..MAX_PAYLOAD_BYTES) { "prepared plan payload size invalid" }
            val payloadBytes = ByteArray(payloadSize)
            input.readFully(payloadBytes)
            val idempotency = IdempotencyKey(input.field())
            val stampCount = input.readInt()
            require(stampCount in 0..MAX_STAMPS_PER_NODE) { "prepared plan stamps size invalid" }
            val stamps = ArrayList<DependencyStamp>(stampCount)
            repeat(stampCount) {
                stamps += DependencyStamp(
                    AuthorityBoundaryId(input.field()),
                    DependencyKey(input.field()),
                    DependencyStampValue(input.field()),
                )
            }
            val depCount = input.readInt()
            require(depCount in 0..MAX_NODES) { "prepared plan dependencies size invalid" }
            val deps = LinkedHashSet<PlanNodeId>()
            repeat(depCount) {
                require(deps.add(PlanNodeId(input.field()))) {
                    "duplicate dependency in plan envelope"
                }
            }
            nodes += ApplyPlanNode(
                nodeId = nodeId,
                preparedOperation = PreparedOperation(
                    contract = contract,
                    payload = CanonicalPayload.fromEncoded(formatId, payloadBytes),
                    dependencyStamps = DependencyStampSet.of(*stamps.toTypedArray()),
                    idempotencyKey = idempotency,
                ),
                dependsOn = deps,
            )
        }
        require(input.available() == 0) { "trailing prepared plan envelope data" }
        val restored = PreparedPlan.of(*nodes.toTypedArray())
        require(restored.planFingerprint.value == declaredFingerprint) {
            "prepared plan fingerprint mismatch after restore"
        }
        // Enforces one canonical node/stamp/dependency ordering and framing.
        require(encode(restored).contentEquals(encoded)) {
            "non-canonical prepared plan envelope"
        }
        return restored
    }

    private fun DataOutputStream.field(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_FIELD_BYTES &&
            value.none { it.code < 32 || it.code == 127 }) {
            "invalid prepared plan envelope text field"
        }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.field(): String {
        val size = readInt()
        require(size in 1..MAX_FIELD_BYTES) { "prepared plan text field size invalid" }
        val bytes = ByteArray(size)
        readFully(bytes)
        val text = StandardCharsets.UTF_8.newDecoder().decode(
            java.nio.ByteBuffer.wrap(bytes)
        ).toString()
        require(text.none { it.code < 32 || it.code == 127 }) {
            "prepared plan text field contains control characters"
        }
        return text
    }
}
