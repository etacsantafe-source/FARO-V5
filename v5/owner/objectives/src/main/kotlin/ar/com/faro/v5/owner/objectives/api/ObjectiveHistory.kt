package ar.com.faro.v5.owner.objectives.api

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

data class ObjectiveReading(val value: Long, val unit: ObjectiveMetricUnit) {
    init { require(value >= 0) }
}

data class ObjectiveProgressReport(
    val localDate: LocalDate,
    val explanation: String,
    val reading: ObjectiveReading? = null,
) {
    init { require(explanation.isNotBlank() && explanation.length <= 1200) }
}

sealed interface ObjectiveHistoryChange {
    data class Progress(val report: ObjectiveProgressReport) : ObjectiveHistoryChange

    data class Correction(
        val originalSequence: Int,
        val replacement: ObjectiveProgressReport,
        val explanation: String,
    ) : ObjectiveHistoryChange {
        init {
            require(originalSequence in 1..100_000)
            require(explanation.isNotBlank() && explanation.length <= 400)
        }
    }

    data class Lifecycle(
        val before: ObjectiveStatus,
        val after: ObjectiveStatus,
        val explanation: String,
    ) : ObjectiveHistoryChange {
        init {
            require(after in nextObjectiveStates(before))
            require(explanation.isNotBlank() && explanation.length <= 400)
        }
    }
}

fun nextObjectiveStates(status: ObjectiveStatus): Set<ObjectiveStatus> = when (status) {
    ObjectiveStatus.PLANNED -> setOf(ObjectiveStatus.ACTIVE, ObjectiveStatus.COMPLETED, ObjectiveStatus.ARCHIVED)
    ObjectiveStatus.ACTIVE -> setOf(ObjectiveStatus.PAUSED, ObjectiveStatus.COMPLETED, ObjectiveStatus.ARCHIVED)
    ObjectiveStatus.PAUSED -> setOf(ObjectiveStatus.ACTIVE, ObjectiveStatus.COMPLETED, ObjectiveStatus.ARCHIVED)
    ObjectiveStatus.COMPLETED -> setOf(ObjectiveStatus.ACTIVE, ObjectiveStatus.ARCHIVED)
    ObjectiveStatus.ARCHIVED -> setOf(ObjectiveStatus.ACTIVE)
}

data class ObjectiveHistoryEntry(
    val sequence: Int,
    val recordedAtEpochMillis: Long,
    val change: ObjectiveHistoryChange,
) {
    init { require(sequence in 1..100_000 && recordedAtEpochMillis >= 0) }
}

/** Same canonical event representation in the SQLite extension and versioned backup. */
object ObjectiveHistoryEntryCodec {
    const val MAX_BYTES = 8192

    fun encode(entry: ObjectiveHistoryEntry): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(entry.sequence)
            output.writeLong(entry.recordedAtEpochMillis)
            when (val change = entry.change) {
                is ObjectiveHistoryChange.Progress -> {
                    output.writeByte(1)
                    output.writeReport(change.report)
                }
                is ObjectiveHistoryChange.Correction -> {
                    output.writeByte(2)
                    output.writeInt(change.originalSequence)
                    output.writeReport(change.replacement)
                    output.writeUTF(change.explanation)
                }
                is ObjectiveHistoryChange.Lifecycle -> {
                    output.writeByte(3)
                    output.writeUTF(change.before.name)
                    output.writeUTF(change.after.name)
                    output.writeUTF(change.explanation)
                }
            }
        }
        bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): ObjectiveHistoryEntry {
        require(bytes.size in 1..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val sequence = input.readInt()
            val recordedAt = input.readLong()
            val change = when (input.readUnsignedByte()) {
                1 -> ObjectiveHistoryChange.Progress(input.readReport())
                2 -> ObjectiveHistoryChange.Correction(input.readInt(), input.readReport(), input.readUTF())
                3 -> ObjectiveHistoryChange.Lifecycle(
                    ObjectiveStatus.valueOf(input.readUTF()), ObjectiveStatus.valueOf(input.readUTF()), input.readUTF(),
                )
                else -> error("Invalid objective history event")
            }
            ObjectiveHistoryEntry(sequence, recordedAt, change).also {
                require(encode(it).contentEquals(bytes)) { "Noncanonical objective history entry" }
            }
        }
    }

    private fun DataOutputStream.writeReport(report: ObjectiveProgressReport) {
        writeLong(report.localDate.toEpochDay())
        writeUTF(report.explanation)
        val reading = report.reading
        writeBoolean(reading != null)
        if (reading != null) {
            writeLong(reading.value)
            writeUTF(reading.unit.kind.name)
            writeUTF(reading.unit.label)
        }
    }

    private fun DataInputStream.readReport(): ObjectiveProgressReport {
        val localDate = LocalDate.ofEpochDay(readLong())
        val explanation = readUTF()
        val reading = when (readUnsignedByte()) {
            0 -> null
            1 -> ObjectiveReading(readLong(), ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.valueOf(readUTF()), readUTF()))
            else -> error("Invalid objective reading flag")
        }
        return ObjectiveProgressReport(localDate, explanation, reading)
    }
}

data class ObjectiveConfirmedProgress(val observed: Long, val target: Long) {
    init { require(observed >= 0 && target > 0) }
    val ratio: Double get() = observed.toDouble() / target.toDouble()
}

fun ObjectiveRecord.confirmedProgress(): ObjectiveConfirmedProgress? {
    val target = details.target ?: return null
    val corrections = mutableMapOf<Int, ObjectiveProgressReport>()
    for (event in details.history) {
        val correction = event.change as? ObjectiveHistoryChange.Correction ?: continue
        corrections[correction.originalSequence] = correction.replacement
    }
    for (event in details.history.asReversed()) {
        val progress = event.change as? ObjectiveHistoryChange.Progress ?: continue
        val report = corrections[event.sequence] ?: progress.report
        val reading = report.reading?.takeIf { it.unit == target.unit } ?: continue
        return ObjectiveConfirmedProgress(reading.value, target.value)
    }
    return details.measurements.lastOrNull { it.unit == target.unit }?.let {
        ObjectiveConfirmedProgress(it.value, target.value)
    }
}
