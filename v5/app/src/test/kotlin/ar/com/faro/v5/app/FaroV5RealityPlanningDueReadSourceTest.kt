package ar.com.faro.v5.app

import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItem
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemId
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemStatus
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshot
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshotReadApi
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrence
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrenceId
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrenceStatus
import ar.com.faro.v5.owner.reminders.api.ReminderRecurrence
import ar.com.faro.v5.owner.reminders.api.ReminderRule
import ar.com.faro.v5.owner.reminders.api.ReminderRuleId
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshot
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshotReadApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Typed canonical owner projections only; not a shipping ACL or Android UI test. */
class FaroV5RealityPlanningDueReadSourceTest {
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val now = Instant.parse("2026-11-05T15:00:00Z")

    private fun rule(id: String, enabled: Boolean = true) = ReminderRule(
        id = ReminderRuleId.of(id), title = "Private reminder $id",
        zoneId = zone.id, anchorLocalDate = LocalDate.parse("2026-11-05"),
        anchorLocalTime = LocalTime.NOON, recurrence = ReminderRecurrence.ONCE,
        enabled = enabled, updatedAtEpochMillis = 1L,
    )

    private fun occurrence(
        id: String, rule: String, due: String,
        status: ReminderOccurrenceStatus = ReminderOccurrenceStatus.PENDING,
    ) = ReminderOccurrence(
        id = ReminderOccurrenceId.of(id), ruleId = ReminderRuleId.of(rule),
        dueAtEpochMillis = Instant.parse(due).toEpochMilli(),
        status = status,
        acknowledgedAtEpochMillis =
            if (status == ReminderOccurrenceStatus.ACKNOWLEDGED) 1L else null,
        updatedAtEpochMillis = 1L,
    )

    private fun item(
        id: String, date: String,
        status: MonthlyPlanItemStatus = MonthlyPlanItemStatus.PLANNED,
    ): MonthlyPlanItem {
        val dueDate = LocalDate.parse(date)
        return MonthlyPlanItem(
            id = MonthlyPlanItemId.of(id), title = "Private payment $id",
            period = YearMonth.from(dueDate), dueDate = dueDate, status = status,
            paymentConfirmedAtEpochMillis =
                if (status == MonthlyPlanItemStatus.PAID_CONFIRMED) 1L else null,
            updatedAtEpochMillis = 1L,
        )
    }

    private class Fixture {
        var generation = "gen-8"
        var pointerCurrent = true
        var pointerEpoch = "p-1"
        var reminderAcl: String? = "r-1"
        var monthlyAcl: String? = "m-1"
        var reminderGranted = true
        var monthlyGranted = true
        var reminderReads = 0
        var monthlyReads = 0
        var afterReminderRead: () -> Unit = {}
        var afterMonthlyRead: () -> Unit = {}
        var reminderFailure: Exception? = null
        var monthlyFailure: Exception? = null
        var reminder = ReminderSnapshot(5L, emptyList(), emptyList())
        var monthly = MonthlyPlanSnapshot(6L, emptyList())

        fun source() = FaroV5RealityPlanningDueReadSource(
            expectedGenerationId = "gen-8",
            reminders = ReminderSnapshotReadApi {
                reminderReads++
                reminderFailure?.let { throw it }
                val result = reminder
                afterReminderRead()
                result
            },
            monthlyPlan = MonthlyPlanSnapshotReadApi {
                monthlyReads++
                monthlyFailure?.let { throw it }
                val result = monthly
                afterMonthlyRead()
                result
            },
            currentGenerationId = { generation },
            pointerCurrent = { pointerCurrent },
            pointerEpoch = { pointerEpoch },
            reminderAclEpoch = { reminderAcl },
            monthlyPlanAclEpoch = { monthlyAcl },
            reminderReadAdmitted = { reminderGranted },
            monthlyPlanReadAdmitted = { monthlyGranted },
        )
    }

    @Test
    fun missingEitherOwnerGrantNeverReadsAndEmptyIsNotGlobalZero() {
        val f = Fixture()
        f.reminderAcl = null
        assertNull(f.source().readAuthorized(now, zone))
        f.reminderAcl = "r-1"
        f.monthlyGranted = false
        assertNull(f.source().readAuthorized(now, zone))
        assertEquals(0, f.reminderReads)
        assertEquals(0, f.monthlyReads)
        f.monthlyGranted = true
        val page = requireNotNull(f.source().readAuthorized(now, zone))
        assertTrue(page.items.isEmpty())
        assertTrue(page.completeWithinStoredDues)
        assertFalse(page.globalAttentionComplete)
        assertFalse(page.recurrenceExpanded)
        assertEquals("PERSISTED_OCCURRENCES_AND_PLANNED_MONTHLY_ITEMS_ONLY", page.coverage)
        assertEquals(5L, page.reminderRevision)
        assertEquals(6L, page.monthlyPlanRevision)
    }

    @Test
    fun onlyPersistedPendingEnabledAndUnpaidPlannedAreShown() {
        val f = Fixture()
        f.reminder = ReminderSnapshot(9L, listOf(rule("active"), rule("off", false)),
            listOf(
                occurrence("due", "active", "2026-11-06T10:00:00Z"),
                occurrence("off-occ", "off", "2026-11-06T10:00:00Z"),
                occurrence("ack", "active", "2026-11-06T10:00:00Z",
                    ReminderOccurrenceStatus.ACKNOWLEDGED),
                occurrence("cancel", "active", "2026-11-06T10:00:00Z",
                    ReminderOccurrenceStatus.CANCELLED),
                occurrence("future", "active", "2026-11-14T12:00:00Z"),
            ))
        f.monthly = MonthlyPlanSnapshot(8L, listOf(
            item("planned", "2026-11-07"),
            item("paid", "2026-11-07", MonthlyPlanItemStatus.PAID_CONFIRMED),
            item("cancelled", "2026-11-07", MonthlyPlanItemStatus.CANCELLED),
            item("far", "2026-11-13"),
        ))
        val page = requireNotNull(f.source().readAuthorized(now, zone))
        assertEquals(listOf("due", "planned"), page.items.map { it.opaqueId })
        assertEquals(listOf(FaroV5PlanningDueDomain.REMINDER_OCCURRENCE,
            FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM), page.items.map { it.domain })
        assertNotNull(page.items[0].dueAtEpochMillis)
        assertNull(page.items[1].dueAtEpochMillis)
        assertEquals("reality.monthly_plan_item.due_date", page.items[1].timeProvenance)
        assertFalse(page.globalAttentionComplete)
    }

    @Test
    fun localCivilWindowIncludesSevenDaysAndOverdueButDoesNotExpandRules() {
        val f = Fixture()
        f.reminder = ReminderSnapshot(5L, listOf(rule("r"), rule("no-occ")),
            listOf(occurrence("boundary", "r", "2026-11-12T03:00:00Z"),
                occurrence("overdue", "r", "2026-10-28T12:00:00Z")))
        f.monthly = MonthlyPlanSnapshot(6L, listOf(item("bill", "2026-11-12")))
        val before = Instant.parse("2026-11-05T02:30:00Z") // Nov 4 in Cordoba
        val after = Instant.parse("2026-11-05T03:30:00Z") // Nov 5 in Cordoba
        assertEquals(listOf("overdue"),
            requireNotNull(f.source().readAuthorized(before, zone)).items.map { it.opaqueId })
        assertEquals(listOf("overdue", "boundary", "bill"),
            requireNotNull(f.source().readAuthorized(after, zone)).items.map { it.opaqueId })
        assertFalse(requireNotNull(f.source().readAuthorized(after, zone)).recurrenceExpanded)
    }

    @Test
    fun boundedStableOrderingAndTruncationNeverClaimGlobalCoverage() {
        val f = Fixture()
        f.reminder = ReminderSnapshot(5L, listOf(rule("r")), listOf(
            occurrence("b", "r", "2026-11-07T12:00:00Z"),
            occurrence("a", "r", "2026-11-07T12:00:00Z"),
        ))
        f.monthly = MonthlyPlanSnapshot(6L, listOf(item("c", "2026-11-07")))
        val page = requireNotNull(f.source().readAuthorized(now, zone, limit = 2))
        assertEquals(listOf("a", "b"), page.items.map { it.opaqueId })
        assertFalse(page.completeWithinStoredDues)
        assertFalse(page.globalAttentionComplete)
        assertThrows(IllegalArgumentException::class.java) {
            f.source().readAuthorized(now, zone, limit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            f.source().readAuthorized(now, zone, limit = 101)
        }
    }

    @Test
    fun pointerAbaAndIndependentRevocationDiscardWholeRead() {
        val f = Fixture()
        f.afterReminderRead = { f.pointerEpoch = "p-3" }
        assertNull(f.source().readAuthorized(now, zone))
        assertEquals(0, f.monthlyReads)
        f.pointerEpoch = "p-1"
        f.afterReminderRead = { f.reminderAcl = "r-2" }
        assertNull(f.source().readAuthorized(now, zone))
        f.reminderAcl = "r-1"
        f.afterReminderRead = {}
        f.afterMonthlyRead = { f.monthlyAcl = "m-2" }
        assertNull(f.source().readAuthorized(now, zone))
        f.afterMonthlyRead = {}
        f.generation = "gen-other"
        assertNull(f.source().readAuthorized(now, zone))
    }

    @Test
    fun ownerExceptionsDenyAndCancellationPropagates() {
        val f = Fixture()
        f.reminderFailure = IllegalStateException("closed")
        assertNull(f.source().readAuthorized(now, zone))
        f.reminderFailure = null
        f.monthlyFailure = IllegalStateException("closed")
        assertNull(f.source().readAuthorized(now, zone))
        f.monthlyFailure = CancellationException("cancel")
        assertThrows(CancellationException::class.java) {
            f.source().readAuthorized(now, zone)
        }
        f.monthlyFailure = null
        assertNotNull(f.source().readAuthorized(now, zone))
    }
}
