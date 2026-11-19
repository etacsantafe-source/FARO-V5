package ar.com.faro.v5.owner.matters.api

import java.time.LocalDate

/**
 * G4 domain contract only. The N031 attention projection is NOT its owner.
 * A subsequent durable Reality owner must supply snapshot, write receipts,
 * ACL generation/epoch, recovery, and canonical navigation.
 */
@JvmInline
value class MatterId private constructor(val value: String) {
    companion object {
        fun of(raw: String): MatterId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty() && normalized.length <= 128) {
                "matter id must have 1..128 characters"
            }
            return MatterId(normalized)
        }
    }
}

@JvmInline
value class MatterTitle private constructor(val value: String) {
    companion object {
        fun of(raw: String): MatterTitle {
            val normalized = raw.trim()
            require(normalized.isNotEmpty() && normalized.length <= 160) {
                "matter title must have 1..160 characters"
            }
            return MatterTitle(normalized)
        }
    }
}

@JvmInline
value class MatterDetail private constructor(val value: String) {
    companion object {
        fun of(raw: String): MatterDetail {
            val normalized = raw.trim()
            require(normalized.length <= 4096) { "matter detail exceeds 4096 characters" }
            return MatterDetail(normalized)
        }
    }
}

enum class MatterStatus {
    OPEN, IN_PROGRESS, WAITING, RESOLVED, ARCHIVED
}

/** No automatic urgency or notifications derive from these labels. */
enum class MatterPriority { LOW, NORMAL, HIGH }

enum class MatterHistoryKind { CREATED, EDITED, STATUS_CHANGED }

/** An explicit observed change, never a fabricated event for a legacy row. */
data class MatterHistoryEntry(
    val sequence: Int,
    val recordedAtEpochMillis: Long,
    val kind: MatterHistoryKind,
    val resultingStatus: MatterStatus,
    val reason: String,
) {
    init {
        require(sequence in 1..100_000 && recordedAtEpochMillis >= 0L) {
            "invalid matter history event index/time"
        }
        require(reason.length <= 512 && reason == reason.trim() &&
            reason.none(Char::isISOControl)) { "invalid matter history reason" }
        require(kind != MatterHistoryKind.STATUS_CHANGED || reason.isNotEmpty()) {
            "matter status change requires a reason"
        }
    }
}

data class MatterDetails(
    val priority: MatterPriority = MatterPriority.NORMAL,
    val reviewDate: LocalDate? = null,
    val history: List<MatterHistoryEntry> = emptyList(),
) {
    init {
        require(history.size <= 100_000)
        history.forEachIndexed { index, entry ->
            require(entry.sequence == index + 1 &&
                (index == 0 || entry.recordedAtEpochMillis > history[index - 1].recordedAtEpochMillis)
            ) { "matter history must be contiguous and strictly chronological" }
        }
    }

    fun retainsHistoryOf(previous: MatterDetails): Boolean =
        history.size >= previous.history.size &&
            previous.history.indices.all { history[it] == previous.history[it] }
}

data class MatterRecord(
    val id: MatterId,
    val title: MatterTitle,
    val detail: MatterDetail,
    val status: MatterStatus,
    val dueAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
    val details: MatterDetails = MatterDetails(),
) {
    init {
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L) {
            "matter due date must be non-negative"
        }
        require(updatedAtEpochMillis >= 0L) { "matter update timestamp must be non-negative" }
        require((details.history.lastOrNull()?.recordedAtEpochMillis ?: 0L) <= updatedAtEpochMillis) {
            "matter history cannot be newer than its row"
        }
        require(details.history.lastOrNull()?.resultingStatus == null ||
            details.history.last().resultingStatus == status) {
            "matter status and its last history event disagree"
        }
    }
}

data class MatterSnapshot(
    val revision: Long,
    val matters: List<MatterRecord>,
) {
    init {
        require(revision >= 0L) { "matter revision must be non-negative" }
        require(matters.map { it.id }.distinct().size == matters.size) {
            "duplicate matter id"
        }
    }
}

/** Read contract; not an implemented owner or permission grant. */
fun interface MatterSnapshotReadApi {
    fun read(): MatterSnapshot
}
