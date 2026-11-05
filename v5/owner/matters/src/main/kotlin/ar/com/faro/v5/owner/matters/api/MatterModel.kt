package ar.com.faro.v5.owner.matters.api

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

data class MatterRecord(
    val id: MatterId,
    val title: MatterTitle,
    val detail: MatterDetail,
    val status: MatterStatus,
    val dueAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L) {
            "matter due date must be non-negative"
        }
        require(updatedAtEpochMillis >= 0L) { "matter update timestamp must be non-negative" }
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
