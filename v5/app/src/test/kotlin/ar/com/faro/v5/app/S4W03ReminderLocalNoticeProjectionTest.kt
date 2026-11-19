package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.owner.reminders.api.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class S4W03ReminderLocalNoticeProjectionTest {
    private fun rule(id: String, linked: String? = null) = ReminderRule(
        ReminderRuleId.of(id), "Visitar médico", "UTC",
        LocalDate.of(2026, 11, 30), LocalTime.of(9, 0),
        ReminderRecurrence.ONCE, true, linked, 100L,
    )
    private val due = LocalDate.of(2026, 11, 30).atTime(9, 0)
        .toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun sourceCandidatesAreReadOnlyDistinctAndCrossOwnerLinksFailClosed() {
        val visible = rule("r1")
        val hidden = rule("r2", "m1")
        val before = ReminderSnapshot(0L, listOf(visible, hidden), emptyList())
        val first = S4W03ReminderLocalNoticeProjection.candidates(
            before, due, due + 1L,
        )
        assertEquals(1, first.size)
        assertEquals(NoticeMateriality.NOTICE, first.single().materiality)
        assertEquals(NoticeSensitivity.NORMAL, first.single().sensitivity)
        assertEquals(first, S4W03ReminderLocalNoticeProjection.candidates(
            before, due, due + 1L,
        ))
        assertEquals(0L, before.revision)
        assertTrue(before.occurrences.isEmpty())
        val withLinkGrant = S4W03ReminderLocalNoticeProjection.candidates(
            before, due, due + 1L, linkedMatterVisible = { it == "m1" },
        )
        assertEquals(2, withLinkGrant.size)
        assertEquals(NoticeSensitivity.SENSITIVE, withLinkGrant.last().sensitivity)
    }

    @Test fun ackCancelledAndDisabledDoNotFabricateLocalNoticeOrMutateMatter() {
        val source = rule("r1")
        val dueId = ReminderCivilExpansion.expand(source, due, due + 1L).single().id
        val ack = ReminderOccurrence(
            dueId, source.id, due, ReminderOccurrenceStatus.ACKNOWLEDGED,
            due + 10L, due + 10L,
        )
        val acknowledged = ReminderSnapshot(1L, listOf(source), listOf(ack))
        assertTrue(S4W03ReminderLocalNoticeProjection.candidates(
            acknowledged, due, due + 1L,
        ).isEmpty())
        assertTrue(S4W03ReminderLocalNoticeProjection.candidates(
            ReminderSnapshot(0L, listOf(source.copy(enabled = false)), emptyList()),
            due, due + 1L,
        ).isEmpty())
        assertEquals(ReminderOccurrenceStatus.ACKNOWLEDGED,
            acknowledged.occurrences.single().status)
        assertThrows(IllegalArgumentException::class.java) {
            S4W03ReminderLocalNoticeProjection.candidates(
                acknowledged, due, due + 1L, maxCandidates = 0,
            )
        }
    }
}
