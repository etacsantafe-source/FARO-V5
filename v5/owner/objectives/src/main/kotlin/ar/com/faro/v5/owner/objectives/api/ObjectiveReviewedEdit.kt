package ar.com.faro.v5.owner.objectives.api

import ar.com.faro.v5.kernel.contracts.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

/** The existing N-030 operations used by the manual Objectives review. */
object ObjectiveReviewOperations {
    val CREATE = OperationId("objective.create")
    val UPDATE = OperationId("objective.update")
    val EXECUTOR = ExecutorId("planning")
    private val capability = CapabilityId("N-030")
    val editable = setOf(CREATE, UPDATE)

    fun allows(contract: OperationContractDescriptor): Boolean =
        contract.operationId in editable &&
            contract.capabilityId == capability && contract.canonicalExecutor == EXECUTOR &&
            contract.executionProfile == ExecutionProfile.REALITY_MUTATION &&
            contract.exposure == ContractExposure.BINDABLE
}

/** Complete manual form state. Lifecycle and measurements remain in Reality. */
data class ObjectiveReviewedEdit(
    val id: ObjectiveId,
    val title: ObjectiveTitle,
    val detail: ObjectiveDetail,
    val target: ObjectiveMetric? = null,
    val dueDate: LocalDate? = null,
    val priority: ObjectivePriority = ObjectivePriority.NORMAL,
)

data class ObjectiveEditIntent(
    val expectedRevision: Long,
    val editedAtEpochMillis: Long,
    val form: ObjectiveReviewedEdit,
) {
    init {
        require(expectedRevision >= 0 && editedAtEpochMillis >= 0)
    }
}

object ObjectiveEditPayload {
    private const val FORMAT = "objectives.reviewed-edit.v1"
    private const val MAX_BYTES = 16_384

    fun encode(intent: ObjectiveEditIntent): CanonicalPayload {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            val form = intent.form
            output.writeLong(intent.expectedRevision)
            output.writeLong(intent.editedAtEpochMillis)
            output.writeUTF(form.id.value)
            output.writeUTF(form.title.value)
            output.writeUTF(form.detail.value)
            output.writeUTF(form.priority.name)
            output.writeUTF(form.dueDate?.toString().orEmpty())
            val target = form.target
            output.writeByte(if (target == null) 0 else 1)
            if (target != null) {
                output.writeLong(target.value)
                output.writeUTF(target.unit.kind.name)
                output.writeUTF(target.unit.label)
            }
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_BYTES)
        return CanonicalPayload.fromEncoded(FORMAT, bytes)
    }

    fun decode(payload: CanonicalPayload): ObjectiveEditIntent? {
        if (payload.formatId != FORMAT || payload.size !in 1..MAX_BYTES) return null
        val original = payload.copyBytes()
        return runCatching {
            val intent = DataInputStream(ByteArrayInputStream(original)).use { input ->
                val revision = input.readLong()
                val at = input.readLong()
                val id = ObjectiveId.of(input.readUTF())
                val title = ObjectiveTitle.of(input.readUTF())
                val detail = ObjectiveDetail.of(input.readUTF())
                val priority = ObjectivePriority.valueOf(input.readUTF())
                val dueDate = input.readUTF().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                val target = when (input.readUnsignedByte()) {
                    0 -> null
                    1 -> ObjectiveMetric(
                        input.readLong(),
                        ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.valueOf(input.readUTF()), input.readUTF()),
                    )
                    else -> error("Invalid objective target flag")
                }
                ObjectiveEditIntent(revision, at, ObjectiveReviewedEdit(id, title, detail, target, dueDate, priority))
            }
            require(encode(intent).copyBytes().contentEquals(original)) { "Noncanonical objective review" }
            intent
        }.getOrNull()
    }
}
