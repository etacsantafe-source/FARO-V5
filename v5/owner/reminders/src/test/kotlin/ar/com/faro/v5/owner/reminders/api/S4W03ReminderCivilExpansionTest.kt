package ar.com.faro.v5.owner.reminders.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W03ReminderCivilExpansionTest {
    private fun rule(
        date: LocalDate,
        recurrence: ReminderRecurrence = ReminderRecurrence.ONCE,
        zone: String = "UTC",
        time: LocalTime = LocalTime.of(9, 0),
        enabled: Boolean = true,
    ) = ReminderRule(
        id = ReminderRuleId.of("civil-test"),
        title = "Recordar",
        zoneId = zone,
        anchorLocalDate = date,
        anchorLocalTime = time,
        recurrence = recurrence,
        enabled = enabled,
        linkedMatterId = null,
        updatedAtEpochMillis = 1L,
    )

    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()
    private fun d(iso: String): LocalDate = LocalDate.parse(iso)
    private fun dates(list: List<ReminderCivilDue>): List<LocalDate> = list.map { it.nominalLocalDate }

    @Test fun onceDailyAndWeeklyUseCivilAnchor() {
        val start = epoch("2026-10-31T00:00:00Z")
        val end = epoch("2026-11-16T00:00:00Z")
        val original = rule(d("2026-11-01"))
        val once = ReminderCivilExpansion.expand(original, start, end)
        val daily = ReminderCivilExpansion.expand(
            original.copy(recurrence = ReminderRecurrence.DAILY), start, end)
        val weekly = ReminderCivilExpansion.expand(
            original.copy(recurrence = ReminderRecurrence.WEEKLY), start, end)
        assertEquals(listOf(d("2026-11-01")), dates(once))
        assertEquals(15, daily.size)
        assertEquals(listOf(d("2026-11-01"), d("2026-11-08"), d("2026-11-15")), dates(weekly))
    }

    @Test fun monthly31ClampsWithoutDriftingForever() {
        val result = ReminderCivilExpansion.expand(
            rule(d("2026-01-31"), ReminderRecurrence.MONTHLY),
            epoch("2026-01-30T00:00:00Z"), epoch("2026-04-02T00:00:00Z"))
        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31")),
            dates(result))
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }

    @Test fun leapYearFebruary29AndNonLeapFebruary28() {
        fun feb(year: Int): LocalDate {
            return dates(ReminderCivilExpansion.expand(
                rule(LocalDate.of(year, 1, 31), ReminderRecurrence.MONTHLY),
                epoch(year.toString() + "-02-01T00:00:00Z"),
                epoch(year.toString() + "-03-01T00:00:00Z"))).single()
        }
        assertEquals(d("2024-02-29"), feb(2024))
        assertEquals(d("2025-02-28"), feb(2025))
    }

    @Test fun springDstGapShiftsNominal0230To0330() {
        val zone = "America/New_York"
        val result = ReminderCivilExpansion.expand(
            rule(d("2026-03-07"), ReminderRecurrence.DAILY, zone, LocalTime.of(2, 30)),
            epoch("2026-03-07T00:00:00Z"), epoch("2026-03-11T00:00:00Z"))
        val jump = result.single { it.nominalLocalDate == d("2026-03-08") }
        assertEquals(LocalTime.of(3, 30),
            Instant.ofEpochMilli(jump.dueAtEpochMillis).atZone(ZoneId.of(zone)).toLocalTime())
        assertEquals(epoch("2026-03-08T07:30:00Z"), jump.dueAtEpochMillis)
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }

    @Test fun fallDstOverlapUsesEarlierOffsetExactlyOnce() {
        val result = ReminderCivilExpansion.expand(
            rule(d("2026-11-01"), zone = "America/New_York", time = LocalTime.of(1, 30)),
            epoch("2026-11-01T00:00:00Z"), epoch("2026-11-02T00:00:00Z"))
        assertEquals(1, result.size)
        assertEquals(epoch("2026-11-01T05:30:00Z"), result.single().dueAtEpochMillis)
        assertEquals(result.single().id, result.single().id)
    }

    @Test fun disabledAndPureReadDoNotMaterializeOrAcknowledge() {
        val r = rule(d("2026-11-01"), ReminderRecurrence.DAILY)
        val snapshot = ReminderSnapshot(0L, listOf(r), emptyList())
        val result = ReminderCivilExpansion.expand(
            r, epoch("2026-11-01T00:00:00Z"), epoch("2026-11-05T00:00:00Z"))
        assertEquals(4, result.size)
        assertTrue(snapshot.occurrences.isEmpty())
        assertEquals(0L, snapshot.revision)
        assertTrue(ReminderCivilExpansion.expand(
            r.copy(enabled = false),
            epoch("2026-11-01T00:00:00Z"), epoch("2026-11-05T00:00:00Z")).isEmpty())
    }

    @Test fun titleUpdateKeepsIdentityButChangingZoneIsDistinct() {
        val original = rule(d("2026-11-01"), zone = "UTC")
        val from = epoch("2026-10-31T00:00:00Z")
        val until = epoch("2026-11-03T00:00:00Z")
        val first = ReminderCivilExpansion.expand(original, from, until).single()
        val renamed = ReminderCivilExpansion.expand(
            original.copy(title = "Nuevo título", updatedAtEpochMillis = 999L),
            from, until).single()
        val rezoned = ReminderCivilExpansion.expand(
            original.copy(zoneId = "Asia/Tokyo"), from, until).single()
        assertEquals(first.id, renamed.id)
        assertEquals(first.dueAtEpochMillis, renamed.dueAtEpochMillis)
        assertNotEquals(first.id, rezoned.id)
        assertNotEquals(first.dueAtEpochMillis, rezoned.dueAtEpochMillis)
    }

    @Test fun paginationIsBoundedStableAndNoDuplicates() {
        val r = rule(d("2026-11-01"), ReminderRecurrence.DAILY)
        val from = epoch("2026-11-01T00:00:00Z")
        val until = epoch("2026-11-13T00:00:00Z")
        val full = ReminderCivilExpansion.expand(r, from, until)
        val a = ReminderCivilExpansion.expand(r, from, until, offset = 0, limit = 5)
        val b = ReminderCivilExpansion.expand(r, from, until, offset = 5, limit = 5)
        val c = ReminderCivilExpansion.expand(r, from, until, offset = 10, limit = 5)
        assertEquals(12, full.size)
        assertEquals(full, a + b + c)
        assertEquals(full.size, full.map { it.id }.toSet().size)
        assertEquals(listOf(d("2026-11-11"), d("2026-11-12")), dates(c))
    }

    @Test fun invalidWindowsOrPagesRejectWithoutProducingWork() {
        val r = rule(d("2026-11-01"))
        val from = epoch("2026-11-01T00:00:00Z")
        val until = epoch("2026-11-02T00:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, -1L, until)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, from)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, from + 371L * 86_400_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, until, offset = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, until, offset = 10_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, until, limit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderCivilExpansion.expand(r, from, until, limit = 129)
        }
    }

    @Test fun historicalAnchorAndUtcExclusiveBoundary() {
        val old = rule(d("1970-01-01"), ReminderRecurrence.DAILY)
        val recent = ReminderCivilExpansion.expand(
            old, epoch("2026-11-01T00:00:00Z"), epoch("2026-11-04T00:00:00Z"))
        assertEquals(
            listOf(d("2026-11-01"), d("2026-11-02"), d("2026-11-03")),
            dates(recent))
        val jp = rule(d("2026-12-01"), zone = "Asia/Tokyo", time = LocalTime.of(0, 15))
        val due = epoch("2026-11-30T15:15:00Z")
        assertEquals(1, ReminderCivilExpansion.expand(jp, due, due + 1L).size)
        assertTrue(ReminderCivilExpansion.expand(jp, due - 1L, due).isEmpty())
        assertTrue(ReminderCivilExpansion.expand(jp, due + 1L, due + 2L).isEmpty())
    }
}
