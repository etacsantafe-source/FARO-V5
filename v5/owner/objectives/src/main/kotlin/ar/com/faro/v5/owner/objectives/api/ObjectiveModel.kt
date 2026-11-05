package ar.com.faro.v5.owner.objectives.api

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

data class ObjectiveRecord(
    val id: ObjectiveId,
    val title: ObjectiveTitle,
    val detail: ObjectiveDetail,
    val status: ObjectiveStatus,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(updatedAtEpochMillis >= 0L) { "objective timestamp must be non-negative" }
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
