package ar.com.faro.v5.owner.reminders.api

import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderModelTest {
    private val rid = ReminderRuleId.of("r1")
    private fun rule(zone: String = "America/Argentina/Cordoba") = ReminderRule(
        rid, "Vencimiento", zone, LocalDate.of(2026,11,30),
        LocalTime.of(9,0), ReminderRecurrence.MONTHLY, true, null, 100L)

    @Test fun explicitCivilTimeDoesNotGenerateOccurrences() {
        val s = ReminderSnapshot(0L, listOf(rule()), emptyList())
        assertEquals(ReminderRecurrence.MONTHLY, s.rules.single().recurrence)
        assertTrue(s.occurrences.isEmpty())
    }
    @Test fun invalidIdsAndOversizedTitleFail() {
        assertThrows(IllegalArgumentException::class.java) { ReminderRuleId.of(" ") }
        assertThrows(IllegalArgumentException::class.java) { ReminderOccurrenceId.of("") }
        assertThrows(IllegalArgumentException::class.java) { rule().copy(title="x".repeat(161)) }
    }
    @Test fun invalidZoneFailsWithoutFallback() {
        assertThrows(Exception::class.java) { rule("Mars/Phobos") }
        assertThrows(IllegalArgumentException::class.java) { rule("") }
    }
    @Test fun orphanAndDuplicateRulesFail() {
        val orphan = ReminderOccurrence(ReminderOccurrenceId.of("o1"),
            ReminderRuleId.of("orphan"), 1L, ReminderOccurrenceStatus.PENDING, null, 1L)
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSnapshot(1L, listOf(rule()), listOf(orphan))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSnapshot(1L, listOf(rule(),rule()), emptyList())
        }
    }
    @Test fun acknowledgementExplicitAndOccurrenceIdsUnique() {
        val p=ReminderOccurrence(ReminderOccurrenceId.of("o1"),rid,1L,
            ReminderOccurrenceStatus.PENDING,null,1L)
        assertThrows(IllegalArgumentException::class.java) {
            p.copy(status=ReminderOccurrenceStatus.ACKNOWLEDGED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSnapshot(1L,listOf(rule()),listOf(p,p))
        }
        assertEquals(2L,p.copy(status=ReminderOccurrenceStatus.ACKNOWLEDGED,
            acknowledgedAtEpochMillis=2L).acknowledgedAtEpochMillis)
    }
    @Test fun negativesAndNoncanonicalLinkFail() {
        assertThrows(IllegalArgumentException::class.java) {
            rule().copy(updatedAtEpochMillis=-1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            rule().copy(linkedMatterId=" m1 ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSnapshot(-1L,emptyList(),emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderOccurrence(ReminderOccurrenceId.of("o"),rid,-1L,
                ReminderOccurrenceStatus.CANCELLED,null,1L)
        }
    }
}
