package ar.com.faro.v5.owner.objectives.api

import java.time.LocalDate

/**
 * G4 domain contract only. No persistent owner, host ACL, or Search registration
 * is created by these types. A later Reality-backed owner must provide storage,
 * recovery, revision, read-admission, and authenticated navigation.
 */
@JvmInline
value class ObjectiveId private constructor(val value: String) {
    companion object {
        fun of(raw: String): ObjectiveId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty() && normalized.length <= 128) {
                "objective id must have 1..128 characters"
            }
            return ObjectiveId(normalized)
        }
    }
}

@JvmInline
value class ObjectiveTitle private constructor(val value: String) {
    companion object {
        fun of(raw: String): ObjectiveTitle {
            val normalized = raw.trim()
            require(normalized.isNotEmpty() && normalized.length <= 160) {
                "objective title must have 1..160 characters"
            }
            return ObjectiveTitle(normalized)
        }
    }
}

@JvmInline
value class ObjectiveDetail private constructor(val value: String) {
    companion object {
        fun of(raw: String): ObjectiveDetail {
            val normalized = raw.trim()
            require(normalized.length <= 4096) { "objective detail exceeds 4096 characters" }
            return ObjectiveDetail(normalized)
        }
    }
}

enum class ObjectiveStatus {
    PLANNED, ACTIVE, PAUSED, COMPLETED, ARCHIVED
}

enum class ObjectivePriority { LOW, NORMAL, HIGH }

data class ObjectiveMetricUnit(val kind: Kind, val label: String) {
    enum class Kind { QUANTITY, MONETARY }

    init {
        require(label.length in 1..40 && label == label.trim() && label.none(Char::isISOControl)) {
            "Invalid objective metric unit"
        }
        if (kind == Kind.MONETARY) {
            require(label.length == 3 && label.all { it in 'A'..'Z' }) {
                "A monetary objective requires an explicit currency code"
            }
        }
    }
}

data class ObjectiveMetric(val value: Long, val unit: ObjectiveMetricUnit) {
    init {
        require(value > 0) { "Objective metric target must be positive" }
    }
}

data class ObjectiveMeasurement(
    val sequence: Int,
    val recordedAtEpochMillis: Long,
    val unit: ObjectiveMetricUnit,
    val value: Long,
) {
    init {
        require(sequence in 1..100_000 && recordedAtEpochMillis >= 0 && value >= 0) {
            "Invalid objective measurement"
        }
    }
}

data class ObjectiveDetails(
    val target: ObjectiveMetric? = null,
    val dueDate: LocalDate? = null,
    val priority: ObjectivePriority = ObjectivePriority.NORMAL,
    val measurements: List<ObjectiveMeasurement> = emptyList(),
    val history: List<ObjectiveHistoryEntry> = emptyList(),
) {
    init {
        require(measurements.size <= 100_000 && measurements.indices.all { measurements[it].sequence == it + 1 }) {
            "Objective measurements must form a contiguous sequence"
        }
        require(history.size <= 100_000)
        var lifecycle: ObjectiveStatus? = null
        history.forEachIndexed { index, entry ->
            require(entry.sequence == index + 1 && (index == 0 ||
                entry.recordedAtEpochMillis > history[index - 1].recordedAtEpochMillis)
            ) { "Invalid objective history order" }
            when (val event = entry.change) {
                is ObjectiveHistoryChange.Progress -> Unit
                is ObjectiveHistoryChange.Correction -> require(
                    event.originalSequence < entry.sequence &&
                        history[event.originalSequence - 1].change is ObjectiveHistoryChange.Progress
                ) { "A correction must refer to a prior progress entry" }
                is ObjectiveHistoryChange.Lifecycle -> {
                    require(lifecycle == null || event.before == lifecycle) { "Invalid objective state history" }
                    lifecycle = event.after
                }
            }
        }
    }

    fun retainsHistoryOf(previous: ObjectiveDetails): Boolean =
        measurements.size >= previous.measurements.size &&
            previous.measurements.indices.all { previous.measurements[it] == measurements[it] } &&
            history.size >= previous.history.size &&
            previous.history.indices.all { previous.history[it] == history[it] }
}

data class ObjectiveRecord(
    val id: ObjectiveId,
    val title: ObjectiveTitle,
    val detail: ObjectiveDetail,
    val status: ObjectiveStatus,
    val updatedAtEpochMillis: Long,
    val details: ObjectiveDetails = ObjectiveDetails(),
) {
    init {
        require(updatedAtEpochMillis >= 0L) { "objective timestamp must be non-negative" }
        require((details.history.lastOrNull()?.recordedAtEpochMillis ?: 0L) <= updatedAtEpochMillis)
        val lifecycle = details.history.asReversed().firstNotNullOfOrNull {
            it.change as? ObjectiveHistoryChange.Lifecycle
        }
        require(lifecycle == null || lifecycle.after == status) { "Objective state differs from history" }
    }
}

data class ObjectiveSnapshot(
    val revision: Long,
    val objectives: List<ObjectiveRecord>,
) {
    init {
        require(revision >= 0L) { "objective revision must be non-negative" }
        require(objectives.map { it.id }.distinct().size == objectives.size) {
            "duplicate objective id"
        }
    }
}

/** Read contract; not an implemented owner or permission grant. */
fun interface ObjectiveSnapshotReadApi {
    fun read(): ObjectiveSnapshot
}
