package ar.com.faro.v5.owner.objectives.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object ObjectiveHistoryOperations {
    val LIFECYCLE = OperationId("objective.lifecycle.set")
    val supported = ObjectiveReviewOperations.editable + LIFECYCLE

    fun allows(contract: ar.com.faro.v5.kernel.contracts.OperationContractDescriptor): Boolean =
        ObjectiveReviewOperations.allows(contract) ||
            (contract.operationId == LIFECYCLE &&
                contract.capabilityId == ar.com.faro.v5.kernel.contracts.CapabilityId("N-030") &&
                contract.canonicalExecutor == ObjectiveReviewOperations.EXECUTOR &&
                contract.executionProfile == ar.com.faro.v5.kernel.contracts.ExecutionProfile.REALITY_MUTATION &&
                contract.exposure == ar.com.faro.v5.kernel.contracts.ContractExposure.BINDABLE)
}

data class ObjectiveHistoryIntent(
    val objectiveId: ObjectiveId,
    val expectedRevision: Long,
    val entry: ObjectiveHistoryEntry,
) {
    init { require(expectedRevision >= 0) }

    val operation: OperationId
        get() = if (entry.change is ObjectiveHistoryChange.Lifecycle) ObjectiveHistoryOperations.LIFECYCLE
        else ObjectiveReviewOperations.UPDATE

    fun applyOn(record: ObjectiveRecord): ObjectiveRecord? {
        if (record.id != objectiveId || entry.sequence != record.details.history.size + 1 ||
            entry.recordedAtEpochMillis <= record.updatedAtEpochMillis
        ) return null
        val status = when (val change = entry.change) {
            is ObjectiveHistoryChange.Lifecycle -> {
                if (change.before != record.status) return null
                change.after
            }
            is ObjectiveHistoryChange.Progress -> {
                if (record.status == ObjectiveStatus.ARCHIVED || !accepts(record, change.report)) return null
                record.status
            }
            is ObjectiveHistoryChange.Correction -> {
                val original = record.details.history.getOrNull(change.originalSequence - 1)?.change
                if (original !is ObjectiveHistoryChange.Progress || !accepts(record, change.replacement)) return null
                record.status
            }
        }
        return record.copy(
            status = status, updatedAtEpochMillis = entry.recordedAtEpochMillis,
            details = record.details.copy(history = record.details.history + entry),
        )
    }

    private fun accepts(record: ObjectiveRecord, report: ObjectiveProgressReport): Boolean =
        report.reading == null || report.reading.unit == record.details.target?.unit
}

object ObjectiveHistoryPayload {
    private const val FORMAT = "objectives.history.v1"
    private const val MAX_BYTES = 9_000

    fun encode(intent: ObjectiveHistoryIntent): CanonicalPayload {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(intent.objectiveId.value)
                output.writeLong(intent.expectedRevision)
                output.write(ObjectiveHistoryEntryCodec.encode(intent.entry))
            }
            buffer.toByteArray()
        }
        require(bytes.size <= MAX_BYTES)
        return CanonicalPayload.fromEncoded(FORMAT, bytes)
    }

    fun decode(payload: CanonicalPayload): ObjectiveHistoryIntent? {
        if (payload.formatId != FORMAT || payload.size !in 1..MAX_BYTES) return null
        val bytes = payload.copyBytes()
        return runCatching {
            val result = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                ObjectiveHistoryIntent(
                    ObjectiveId.of(input.readUTF()), input.readLong(),
                    ObjectiveHistoryEntryCodec.decode(input.readBytes()),
                )
            }
            require(encode(result).copyBytes().contentEquals(bytes))
            result
        }.getOrNull()
    }
}
