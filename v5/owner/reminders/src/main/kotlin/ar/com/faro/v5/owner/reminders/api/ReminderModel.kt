package ar.com.faro.v5.owner.reminders.api

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Contracts only; no persistent owner, scheduler, or host authorization. */
@JvmInline value class ReminderRuleId private constructor(val value: String) {
    companion object {
        fun of(raw: String): ReminderRuleId {
            val v = raw.trim()
            require(v.isNotEmpty() && v.length <= 128)
            return ReminderRuleId(v)
        }
    }
}
@JvmInline value class ReminderOccurrenceId private constructor(val value: String) {
    companion object {
        fun of(raw: String): ReminderOccurrenceId {
            val v = raw.trim()
            require(v.isNotEmpty() && v.length <= 128)
            return ReminderOccurrenceId(v)
        }
    }
}

enum class ReminderRecurrence { ONCE, DAILY, WEEKLY, MONTHLY }
enum class ReminderOccurrenceStatus { PENDING, ACKNOWLEDGED, CANCELLED }

/** Civil schedule intention. DST and end-of-month expansion belong to a later audited engine. */
data class ReminderRule(
    val id: ReminderRuleId,
    val title: String,
    val zoneId: String,
    val anchorLocalDate: LocalDate,
    val anchorLocalTime: LocalTime,
    val recurrence: ReminderRecurrence,
    val enabled: Boolean,
    val linkedMatterId: String? = null,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank() && title.length <= 160)
        require(zoneId.isNotBlank() && zoneId.length <= 128)
        ZoneId.of(zoneId)
        require(linkedMatterId == null ||
            (linkedMatterId.isNotBlank() && linkedMatterId == linkedMatterId.trim() &&
                linkedMatterId.length <= 128))
        require(updatedAtEpochMillis >= 0L)
    }
}

/** A concrete due instant, never inferred from a notification-delivery event. */
data class ReminderOccurrence(
    val id: ReminderOccurrenceId,
    val ruleId: ReminderRuleId,
    val dueAtEpochMillis: Long,
    val status: ReminderOccurrenceStatus,
    val acknowledgedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(dueAtEpochMillis >= 0L && updatedAtEpochMillis >= 0L)
        require((status == ReminderOccurrenceStatus.ACKNOWLEDGED) ==
            (acknowledgedAtEpochMillis != null))
        require(acknowledgedAtEpochMillis == null || acknowledgedAtEpochMillis >= 0L)
    }
}

data class ReminderSnapshot(
    val revision: Long,
    val rules: List<ReminderRule>,
    val occurrences: List<ReminderOccurrence>,
) {
    init {
        require(revision >= 0L)
        val ids = rules.map { it.id }
        require(ids.toSet().size == ids.size)
        val occurrenceIds = occurrences.map { it.id }
        require(occurrenceIds.toSet().size == occurrenceIds.size)
        require(occurrences.all { it.ruleId in ids })
    }
}

/** Not mounted: no claim of N031 source availability. */
fun interface ReminderSnapshotReadApi {
    fun read(): ReminderSnapshot
}
