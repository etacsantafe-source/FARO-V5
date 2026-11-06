package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.reminders.api.ReminderRule
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrence
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItem
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

/**
 * N032: intentionally INTERNAL staging primitive; this is NOT a Chat,
 * Catalog, ApplyPlan, public backup, or Android host operation.
 *
 * One insert at a time with an exact compare-and-set revision of the
 * affected owner, followed by a full PLN1 budget check before SQL COMMIT.
 * Production needs a separate audited Receipt/ACL/AtomicApply adapter.
 */
internal sealed interface RealityPlanningStageInsert {
    val expectedOwnerRevision: Long

    data class Rule(
        val record: ReminderRule,
        override val expectedOwnerRevision: Long,
    ) : RealityPlanningStageInsert

    data class Occurrence(
        val record: ReminderOccurrence,
        override val expectedOwnerRevision: Long,
    ) : RealityPlanningStageInsert

    data class MonthlyItem(
        val record: MonthlyPlanItem,
        override val expectedOwnerRevision: Long,
    ) : RealityPlanningStageInsert
}

/** Only exposed to module-local isolated Reality8 stage code and tests. */
internal fun interface RealityPlanningStageInsertPort {
    fun insert(command: RealityPlanningStageInsert): RealityPlanningBackupSnapshot
}

/** N033A: isolated same-Reality-transaction bridge; never a public capability. */
internal fun interface RealityPlanningApplyInsertPort {
    fun insert(
        command: RealityPlanningStageInsert,
        transactionScope: RealityTransactionScope,
    ): RealityPlanningBackupSnapshot
}
