package ar.com.faro.v5.app

import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemStatus
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshotReadApi
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrenceStatus
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshotReadApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Source identity is part of the opaque key: domain-local IDs can coincide. */
enum class FaroV5PlanningDueDomain { REMINDER_OCCURRENCE, MONTHLY_PLAN_ITEM }

data class FaroV5PlanningDueItem(
    val domain: FaroV5PlanningDueDomain,
    val opaqueId: String,
    val dueOn: LocalDate,
    /** Null for monthly bills: a civil due date must not be invented as midnight. */
    val dueAtEpochMillis: Long?,
    val timeProvenance: String,
) {
    init {
        require(opaqueId.isNotBlank())
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L)
        require(timeProvenance.isNotBlank())
    }
}

/**
 * Bounded two-owner stored-due projection, NOT global attention coverage.
 * Rules without persisted occurrences are intentionally absent: no recurrence
 * expansion/notification scheduling exists in this owner.
 */
data class FaroV5PlanningDuePage(
    val items: List<FaroV5PlanningDueItem>,
    val reminderRevision: Long,
    val monthlyPlanRevision: Long,
    val completeWithinStoredDues: Boolean,
) {
    val canonicalSources: Set<String> get() =
        setOf("reality.reminder_occurrence", "reality.monthly_plan_item")
    val globalAttentionComplete: Boolean get() = false
    val recurrenceExpanded: Boolean get() = false
    val coverage: String get() = "PERSISTED_OCCURRENCES_AND_PLANNED_MONTHLY_ITEMS_ONLY"
}

/**
 * Isolated N031 read projection over the existing typed Reality8 owner SPIs.
 * The future host MUST inject authentic, independently revocable ACL/read
 * admissions and real Reality8 owner ports. These callbacks do NOT mint
 * authorization. This source is not mounted in Android, Home or Chat.
 *
 * A local date and a due instant are distinct facts. Reading two owner
 * snapshots is not a global cross-owner transaction or Home count.
 */
class FaroV5RealityPlanningDueReadSource(
    private val expectedGenerationId: String,
    private val reminders: ReminderSnapshotReadApi,
    private val monthlyPlan: MonthlyPlanSnapshotReadApi,
    private val currentGenerationId: () -> String?,
    private val pointerCurrent: () -> Boolean,
    private val pointerEpoch: () -> String?,
    private val reminderAclEpoch: () -> String?,
    private val monthlyPlanAclEpoch: () -> String?,
    private val reminderReadAdmitted: () -> Boolean,
    private val monthlyPlanReadAdmitted: () -> Boolean,
) {
    init { require(expectedGenerationId.isNotBlank()) }

    private data class Admission(
        val pointer: String,
        val reminder: String,
        val monthly: String,
    )

    private fun admission(): Admission? = try {
        if (currentGenerationId() != expectedGenerationId || !pointerCurrent() ||
            !reminderReadAdmitted() || !monthlyPlanReadAdmitted()
        ) null else {
            val pointer = pointerEpoch()?.takeIf(String::isNotBlank)
            val reminder = reminderAclEpoch()?.takeIf(String::isNotBlank)
            val monthly = monthlyPlanAclEpoch()?.takeIf(String::isNotBlank)
            if (pointer == null || reminder == null || monthly == null ||
                currentGenerationId() != expectedGenerationId || !pointerCurrent() ||
                !reminderReadAdmitted() || !monthlyPlanReadAdmitted() ||
                pointerEpoch() != pointer || reminderAclEpoch() != reminder ||
                monthlyPlanAclEpoch() != monthly
            ) null else Admission(pointer, reminder, monthly)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    fun readAuthorized(
        now: Instant,
        zone: ZoneId,
        limit: Int = 50,
    ): FaroV5PlanningDuePage? {
        require(limit in 1..100)
        val grant = admission() ?: return null
        val reminderSnapshot = try {
            reminders.read()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (admission() != grant) return null
        val monthlySnapshot = try {
            monthlyPlan.read()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (admission() != grant) return null

        val items = try {
            val through = now.atZone(zone).toLocalDate().plusDays(7L)
            val enabled = reminderSnapshot.rules.asSequence()
                .filter { it.enabled }.map { it.id }.toSet()
            val occurrenceDues = reminderSnapshot.occurrences.asSequence()
                .filter { it.ruleId in enabled &&
                    it.status == ReminderOccurrenceStatus.PENDING }
                .mapNotNull { occurrence ->
                    val dueOn = Instant.ofEpochMilli(occurrence.dueAtEpochMillis)
                        .atZone(zone).toLocalDate()
                    if (dueOn.isAfter(through)) null else FaroV5PlanningDueItem(
                        FaroV5PlanningDueDomain.REMINDER_OCCURRENCE,
                        occurrence.id.value,
                        dueOn,
                        occurrence.dueAtEpochMillis,
                        "reality.reminder_occurrence.due_at_epoch_millis",
                    )
                }
            val monthlyDues = monthlySnapshot.items.asSequence()
                .filter { it.status == MonthlyPlanItemStatus.PLANNED &&
                    it.paymentConfirmedAtEpochMillis == null &&
                    !it.dueDate.isAfter(through) }
                .map { item -> FaroV5PlanningDueItem(
                    FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM,
                    item.id.value,
                    item.dueDate,
                    null,
                    "reality.monthly_plan_item.due_date",
                ) }
            (occurrenceDues + monthlyDues)
                .sortedWith(compareBy<FaroV5PlanningDueItem> { it.dueOn }
                    .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.domain.ordinal }
                    .thenBy { it.opaqueId })
                .take(limit + 1).toList()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        return if (admission() == grant) FaroV5PlanningDuePage(
            items = items.take(limit),
            reminderRevision = reminderSnapshot.revision,
            monthlyPlanRevision = monthlySnapshot.revision,
            completeWithinStoredDues = items.size <= limit,
        ) else null
    }
}
