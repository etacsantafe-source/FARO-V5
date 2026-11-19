package ar.com.faro.v5.owner.reminders.api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Read-only civil due projection. These are NOT persistent ReminderOccurrence receipts,
 * OS alarms, owner state transitions, or acknowledgement events.
 */
data class ReminderCivilDue(
    val id: ReminderOccurrenceId,
    val ruleId: ReminderRuleId,
    val nominalLocalDate: LocalDate,
    val nominalLocalTime: LocalTime,
    val zoneId: String,
    val dueAtEpochMillis: Long,
)

/**
 * Local civil recurrence: nominal time and date are advanced BEFORE resolving the zone.
 *
 * A DST gap shifts forward by the length of the gap; an overlap uses the earlier
 * offset once. Monthly 29/30/31 clamps for short months but returns to its
 * original anchor day in subsequent months. No database, clock, scheduler, or
 * owner port is read or written. Future timezone-rule changes may change future
 * instants, never historical stored occurrences.
 */
object ReminderCivilExpansion {
    private const val MAX_WINDOW_MILLIS = 370L * 86_400_000L
    private const val MAX_PAGE_SIZE = 128
    private const val MAX_OFFSET = 10_000
    private const val HEX = "0123456789abcdef"

    fun expand(
        rule: ReminderRule,
        fromInclusiveEpochMillis: Long,
        untilExclusiveEpochMillis: Long,
        offset: Int = 0,
        limit: Int = 100,
    ): List<ReminderCivilDue> {
        require(fromInclusiveEpochMillis >= 0L)
        require(untilExclusiveEpochMillis > fromInclusiveEpochMillis)
        require(untilExclusiveEpochMillis - fromInclusiveEpochMillis <= MAX_WINDOW_MILLIS)
        require(offset in 0..MAX_OFFSET)
        require(limit in 1..MAX_PAGE_SIZE)
        if (!rule.enabled) return emptyList()

        val zone = ZoneId.of(rule.zoneId)
        // Two days of civil margin includes extreme +/-14h offsets and skipped
        // calendar dates; actual instants are filtered to exact [from, until).
        val start = Instant.ofEpochMilli(fromInclusiveEpochMillis)
            .atZone(zone).toLocalDate().minusDays(2)
        val end = Instant.ofEpochMilli(untilExclusiveEpochMillis - 1L)
            .atZone(zone).toLocalDate().plusDays(2)
        val anchor = rule.anchorLocalDate
        if (anchor.isAfter(end)) return emptyList()

        val page = ArrayList<ReminderCivilDue>(minOf(limit, 32))
        var discarded = 0

        fun append(nominalDate: LocalDate) {
            val dueAt = LocalDateTime.of(nominalDate, rule.anchorLocalTime)
                .atZone(zone).toInstant().toEpochMilli()
            if (dueAt < fromInclusiveEpochMillis || dueAt >= untilExclusiveEpochMillis) return
            if (discarded < offset) {
                discarded++
                return
            }
            if (page.size < limit) {
                page.add(
                    ReminderCivilDue(
                        id = stableId(rule, nominalDate),
                        ruleId = rule.id,
                        nominalLocalDate = nominalDate,
                        nominalLocalTime = rule.anchorLocalTime,
                        zoneId = rule.zoneId,
                        dueAtEpochMillis = dueAt,
                    )
                )
            }
        }

        when (rule.recurrence) {
            ReminderRecurrence.ONCE -> {
                if (!anchor.isBefore(start) && !anchor.isAfter(end)) append(anchor)
            }
            ReminderRecurrence.DAILY -> {
                val first = maxOf(0L, ChronoUnit.DAYS.between(anchor, start))
                val last = ChronoUnit.DAYS.between(anchor, end)
                for (index in first..last) append(anchor.plusDays(index))
            }
            ReminderRecurrence.WEEKLY -> {
                val first = maxOf(0L, Math.floorDiv(ChronoUnit.DAYS.between(anchor, start), 7L))
                val last = Math.floorDiv(ChronoUnit.DAYS.between(anchor, end), 7L)
                for (index in first..last) append(anchor.plusWeeks(index))
            }
            ReminderRecurrence.MONTHLY -> {
                val firstMonth = YearMonth.from(anchor)
                val first = maxOf(0L, ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(start)))
                val last = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(end))
                for (index in first..last) {
                    val month = firstMonth.plusMonths(index)
                    append(month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth())))
                }
            }
        }
        return page
    }

    private fun stableId(rule: ReminderRule, nominalDate: LocalDate): ReminderOccurrenceId {
        // Length-prefixed fields avoid ambiguity even if an ID contains delimiters.
        val fields = listOf(
            "REMINDER_CIVIL_DUE_V1", rule.id.value, nominalDate.toString(),
            rule.anchorLocalTime.toString(), rule.zoneId,
        )
        val payload = fields.joinToString(separator = "") { it.length.toString() + ":" + it }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        val hex = buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 15])
            }
        }
        return ReminderOccurrenceId.of("rcl1-" + hex)
    }
}
