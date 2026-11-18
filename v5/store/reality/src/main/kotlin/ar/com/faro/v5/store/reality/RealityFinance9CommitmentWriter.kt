package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import ar.com.faro.v5.owner.finance.api.FinanceCalendarItem
import ar.com.faro.v5.owner.finance.api.FinanceCalendarSchedule
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentCalendar
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentCalendarQuery
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentLifecycle
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentState
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentCreate
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentId
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentPayment
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceScheduleRecurrence
import java.time.LocalDate
import java.time.YearMonth

/** Uses the single Reality connection inside the caller's active AtomicApply transaction. */
internal class RealityFinance9CommitmentWriter(private val connection: SQLiteConnection) {
    fun create(command: FinanceCommitmentCreate) {
        when (command) {
            is FinanceCommitmentCreate.Debt -> insertDebt(command)
            is FinanceCommitmentCreate.Schedule -> insertSchedule(command)
            is FinanceCommitmentCreate.PlanItem -> insertPlanItem(command)
        }
        check(connection.prepare("SELECT changes()").use { it.step() && it.getLong(0) == 1L })
    }

    fun recordPayment(payment: FinanceCommitmentPayment, recordExpense: () -> FinanceEntrySnapshot) {
        requireMatchingDebt(payment.debtId, payment.currency)
        val expected = connection.prepare(
            """
            SELECT expected_minor, debt_id FROM finance_plan_item
            WHERE plan_item_id = ? AND currency_code = ? AND state = 'PLANNED'
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, payment.planItemId.value)
            statement.bindText(2, payment.currency.value)
            check(statement.step()) { "Commitment payment needs a planned item in the declared currency" }
            val debtId = if (statement.isNull(1)) null else statement.getText(1)
            check(debtId == payment.debtId?.value) { "Commitment payment debt changed" }
            statement.getLong(0)
        }

        val paid = connection.prepare(
            """
            SELECT p.amount_minor, e.kind, e.amount_minor, e.currency_code
            FROM finance_plan_item_payment p
            LEFT JOIN finance_entry e ON e.entry_id = p.entry_id
            WHERE p.plan_item_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, payment.planItemId.value)
            var total = 0L
            while (statement.step()) {
                val amount = statement.getLong(0)
                check(amount > 0L && !statement.isNull(1) && statement.getText(1) == "EXPENSE" &&
                    statement.getLong(2) == amount && statement.getText(3) == payment.currency.value
                ) { "Inconsistent commitment payment history" }
                total = Math.addExact(total, amount)
            }
            total
        }
        check(paid == payment.expectedPreviouslyPaidMinor) { "Commitment payment review is stale" }
        check(paid <= expected && payment.expense.amountMinor <= expected - paid) {
            "Commitment payment exceeds the outstanding amount"
        }

        val expense = recordExpense()
        check(expense.currency == payment.currency) { "Commitment payment account currency differs" }
        connection.prepare(
            "INSERT INTO finance_plan_item_payment(plan_item_id, entry_id, amount_minor) VALUES (?, ?, ?)",
        ).use { statement ->
            statement.bindText(1, payment.planItemId.value)
            statement.bindText(2, expense.entryId.value)
            statement.bindLong(3, expense.amountMinor)
            statement.step()
        }
        check(connection.prepare("SELECT changes()").use { it.step() && it.getLong(0) == 1L })
    }

    fun change(command: FinanceCommitmentLifecycle) {
        when (command) {
            is FinanceCommitmentLifecycle.Edit -> editSchedule(command)
            is FinanceCommitmentLifecycle.SetActive -> setScheduleActive(command)
            is FinanceCommitmentLifecycle.Retire -> retireSchedule(command)
            is FinanceCommitmentLifecycle.Cancel -> cancelPlanItem(command)
        }
        check(connection.prepare("SELECT changes()").use { it.step() && it.getLong(0) == 1L }) {
            "Calendar review is stale or the object has dependent history"
        }
    }

    private fun editSchedule(command: FinanceCommitmentLifecycle.Edit) {
        val replacement = command.replacement
        requireMatchingDebt(replacement.debtId, replacement.currency)
        connection.prepare(
            """
            UPDATE finance_schedule SET debt_id = ?, installment_minor = ?, start_period = ?,
                total_count = ?, recurrence = ?, updated_at_ms = ?, version = ?
            WHERE schedule_id = ? AND version = ? AND currency_code = ? AND updated_at_ms <= ?
            """.trimIndent(),
        ).use { st ->
            st.bindCommitmentId(1, replacement.debtId)
            st.bindLong(2, replacement.installmentMinor)
            st.bindText(3, replacement.startPeriod.toString())
            val total = replacement.totalCount
            if (total == null) st.bindNull(4) else st.bindLong(4, total.toLong())
            st.bindText(5, replacement.recurrence.name)
            st.bindLong(6, command.recordedAtEpochMillis)
            st.bindLong(7, nextVersion(command.expectedVersion))
            st.bindText(8, command.id.value)
            st.bindLong(9, command.expectedVersion)
            st.bindText(10, replacement.currency.value)
            st.bindLong(11, command.recordedAtEpochMillis)
            st.step()
        }
    }

    private fun setScheduleActive(command: FinanceCommitmentLifecycle.SetActive) {
        connection.prepare(
            """
            UPDATE finance_schedule SET active = ?, updated_at_ms = ?, version = ?
            WHERE schedule_id = ? AND version = ? AND updated_at_ms <= ? AND active <> ?
            """.trimIndent(),
        ).use { st ->
            val active = if (command.active) 1L else 0L
            st.bindLong(1, active)
            st.bindLong(2, command.recordedAtEpochMillis)
            st.bindLong(3, nextVersion(command.expectedVersion))
            st.bindText(4, command.id.value)
            st.bindLong(5, command.expectedVersion)
            st.bindLong(6, command.recordedAtEpochMillis)
            st.bindLong(7, active)
            st.step()
        }
    }

    private fun retireSchedule(command: FinanceCommitmentLifecycle.Retire) {
        connection.prepare(
            """
            UPDATE finance_schedule SET active = 0, version = ?, updated_at_ms = ?
            WHERE schedule_id = ? AND version = ? AND updated_at_ms <= ?
              AND NOT EXISTS (SELECT 1 FROM finance_plan_item WHERE schedule_id = ?)
            """.trimIndent(),
        ).use { st ->
            st.bindLong(1, RETIRED_VERSION)
            st.bindLong(2, command.recordedAtEpochMillis)
            st.bindText(3, command.id.value)
            st.bindLong(4, command.expectedVersion)
            st.bindLong(5, command.recordedAtEpochMillis)
            st.bindText(6, command.id.value)
            st.step()
        }
    }

    private fun cancelPlanItem(command: FinanceCommitmentLifecycle.Cancel) {
        connection.prepare(
            """
            UPDATE finance_plan_item SET state = 'CANCELLED', updated_at_ms = ?
            WHERE plan_item_id = ? AND state = 'PLANNED' AND updated_at_ms <= ?
              AND NOT EXISTS (SELECT 1 FROM finance_plan_item_payment WHERE plan_item_id = ?)
            """.trimIndent(),
        ).use { st ->
            st.bindLong(1, command.recordedAtEpochMillis)
            st.bindText(2, command.id.value)
            st.bindLong(3, command.recordedAtEpochMillis)
            st.bindText(4, command.id.value)
            st.step()
        }
    }

    fun readCalendar(query: FinanceCommitmentCalendarQuery): FinanceCommitmentCalendar =
        FinanceCommitmentCalendar(readDebts(query.currency), readSchedules(query.currency), readItems(query))

    private fun readDebts(currency: FinanceCurrencyCode): List<FinanceCommitmentCreate.Debt> =
        connection.prepare(
            """
            SELECT debt_id, title, creditor, principal_minor, created_at_ms FROM finance_debt
            WHERE currency_code = ? ORDER BY debt_id LIMIT 2001
            """.trimIndent(),
        ).use { st ->
            st.bindText(1, currency.value)
            buildList {
                while (st.step()) {
                    check(size < MAX_CALENDAR_ROWS)
                    add(FinanceCommitmentCreate.Debt(
                        FinanceCommitmentId(st.getText(0)), st.getText(1), st.getText(2), currency,
                        st.getLong(3), st.getLong(4),
                    ))
                }
            }
        }

    private fun readSchedules(currency: FinanceCurrencyCode): List<FinanceCalendarSchedule> =
        connection.prepare(
            """
            SELECT schedule_id, debt_id, installment_minor, start_period, recurrence,
                total_count, updated_at_ms, version, active
            FROM finance_schedule WHERE currency_code = ? AND version < ?
            ORDER BY schedule_id LIMIT 2001
            """.trimIndent(),
        ).use { st ->
            st.bindText(1, currency.value)
            st.bindLong(2, RETIRED_VERSION)
            buildList {
                while (st.step()) {
                    check(size < MAX_CALENDAR_ROWS)
                    add(FinanceCalendarSchedule(
                        FinanceCommitmentCreate.Schedule(
                            FinanceCommitmentId(st.getText(0)), st.commitmentIdOrNull(1), currency, st.getLong(2),
                            YearMonth.parse(st.getText(3)), FinanceScheduleRecurrence.valueOf(st.getText(4)),
                            if (st.isNull(5)) null else Math.toIntExact(st.getLong(5)), st.getLong(6),
                        ),
                        st.getLong(7), st.getLong(8) == 1L,
                    ))
                }
            }
        }

    private fun readItems(query: FinanceCommitmentCalendarQuery): List<FinanceCalendarItem> =
        connection.prepare(
            """
            SELECT p.plan_item_id, p.schedule_id, p.schedule_version, p.debt_id, p.due_local_date,
                p.expected_minor, p.state, COALESCE(SUM(a.amount_minor), 0), COUNT(a.entry_id), COUNT(e.entry_id)
            FROM finance_plan_item p
            LEFT JOIN finance_plan_item_payment a ON p.plan_item_id = a.plan_item_id
            LEFT JOIN finance_entry e ON e.entry_id = a.entry_id AND e.kind = 'EXPENSE'
                AND e.currency_code = p.currency_code AND e.amount_minor = a.amount_minor
            WHERE p.currency_code = ? AND p.period BETWEEN ? AND ?
            GROUP BY p.plan_item_id ORDER BY p.due_local_date, p.plan_item_id LIMIT 2001
            """.trimIndent(),
        ).use { st ->
            st.bindText(1, query.currency.value)
            st.bindText(2, query.from.toString())
            st.bindText(3, query.through.toString())
            buildList {
                while (st.step()) {
                    check(size < MAX_CALENDAR_ROWS)
                    val expected = st.getLong(5)
                    val paid = st.getLong(7)
                    check(paid in 0..expected && st.getLong(8) == st.getLong(9))
                    val cancelled = st.getText(6) == "CANCELLED"
                    check(!cancelled || paid == 0L)
                    val dueDate = LocalDate.parse(st.getText(4))
                    val remaining = if (cancelled) 0L else expected - paid
                    val due = remaining > 0 && !dueDate.isAfter(query.asOf)
                    val state = when {
                        cancelled -> FinanceCommitmentState.CANCELLED
                        remaining == 0L -> FinanceCommitmentState.PAID_CONFIRMED
                        paid > 0L -> FinanceCommitmentState.PARTIAL
                        due -> FinanceCommitmentState.DUE
                        else -> FinanceCommitmentState.PLANNED
                    }
                    val scheduled = st.commitmentIdOrNull(1)?.let {
                        FinanceCommitmentCreate.ScheduleReference(it, st.getLong(2))
                    }
                    add(FinanceCalendarItem(
                        FinanceCommitmentId(st.getText(0)), scheduled, st.commitmentIdOrNull(3), dueDate,
                        expected, paid, remaining, state, due,
                    ))
                }
            }
        }

    private fun nextVersion(current: Long): Long = Math.incrementExact(current).also { check(it < RETIRED_VERSION) }

    private fun insertDebt(debt: FinanceCommitmentCreate.Debt) {
        connection.prepare(
            """
            INSERT INTO finance_debt(
                debt_id, title, creditor, currency_code, principal_minor, revision, created_at_ms, updated_at_ms
            ) VALUES (?, ?, ?, ?, ?, 0, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, debt.id.value)
            statement.bindText(2, debt.title)
            statement.bindText(3, debt.creditor)
            statement.bindText(4, debt.currency.value)
            statement.bindLong(5, debt.principalMinor)
            statement.bindLong(6, debt.recordedAtEpochMillis)
            statement.bindLong(7, debt.recordedAtEpochMillis)
            statement.step()
        }
    }

    private fun insertSchedule(schedule: FinanceCommitmentCreate.Schedule) {
        requireMatchingDebt(schedule.debtId, schedule.currency)
        connection.prepare(
            """
            INSERT INTO finance_schedule(
                schedule_id, debt_id, currency_code, installment_minor, start_period, total_count,
                recurrence, version, active, updated_at_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, schedule.id.value)
            statement.bindCommitmentId(2, schedule.debtId)
            statement.bindText(3, schedule.currency.value)
            statement.bindLong(4, schedule.installmentMinor)
            statement.bindText(5, schedule.startPeriod.toString())
            val total = schedule.totalCount
            if (total == null) statement.bindNull(6) else statement.bindLong(6, total.toLong())
            statement.bindText(7, schedule.recurrence.name)
            statement.bindLong(8, schedule.recordedAtEpochMillis)
            statement.step()
        }
    }

    private fun insertPlanItem(item: FinanceCommitmentCreate.PlanItem) {
        requireMatchingDebt(item.debtId, item.currency)
        item.schedule?.let { requireMatchingSchedule(item, it) }
        connection.prepare(
            """
            INSERT INTO finance_plan_item(
                plan_item_id, schedule_id, schedule_version, debt_id, period, ordinal,
                due_local_date, currency_code, expected_minor, state, updated_at_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, item.id.value)
            statement.bindCommitmentId(2, item.schedule?.id)
            val schedule = item.schedule
            if (schedule == null) statement.bindNull(3) else statement.bindLong(3, schedule.version)
            statement.bindCommitmentId(4, item.debtId)
            statement.bindText(5, item.period.toString())
            statement.bindLong(6, item.ordinal.toLong())
            statement.bindText(7, item.dueDate.toString())
            statement.bindText(8, item.currency.value)
            statement.bindLong(9, item.expectedMinor)
            statement.bindLong(10, item.recordedAtEpochMillis)
            statement.step()
        }
    }

    private fun requireMatchingDebt(id: FinanceCommitmentId?, currency: FinanceCurrencyCode) {
        if (id == null) return
        connection.prepare("SELECT 1 FROM finance_debt WHERE debt_id = ? AND currency_code = ?").use { statement ->
            statement.bindText(1, id.value)
            statement.bindText(2, currency.value)
            check(statement.step()) { "Finance commitment debt is missing or has a different currency" }
        }
    }

    private fun requireMatchingSchedule(
        item: FinanceCommitmentCreate.PlanItem,
        reference: FinanceCommitmentCreate.ScheduleReference,
    ) {
        connection.prepare(
            """
            SELECT debt_id, installment_minor, start_period, total_count, recurrence FROM finance_schedule
            WHERE schedule_id = ? AND version = ? AND currency_code = ? AND active = 1
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, reference.id.value)
            statement.bindLong(2, reference.version)
            statement.bindText(3, item.currency.value)
            check(statement.step()) { "Finance commitment schedule does not match the active version and currency" }

            val debtId = if (statement.isNull(0)) null else statement.getText(0)
            check(debtId == item.debtId?.value && statement.getLong(1) == item.expectedMinor) {
                "Finance installment debt or amount differs from the schedule"
            }
            val ordinal = item.ordinal.toLong()
            val recurrence = FinanceScheduleRecurrence.valueOf(statement.getText(4))
            check((statement.isNull(3) || ordinal <= statement.getLong(3)) &&
                (recurrence == FinanceScheduleRecurrence.MONTHLY || ordinal == 1L)
            ) { "Finance installment ordinal is outside the schedule" }
            val expectedPeriod = YearMonth.parse(statement.getText(2)).plusMonths(ordinal - 1)
            check(item.period == expectedPeriod) { "Finance installment period differs from the schedule" }
        }
        connection.prepare(
            """
            SELECT 1 FROM finance_plan_item
            WHERE schedule_id = ? AND period = ? AND state = 'PLANNED' LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, reference.id.value)
            statement.bindText(2, item.period.toString())
            check(!statement.step()) { "Finance schedule already has an item for this period" }
        }
    }

    private fun SQLiteStatement.bindCommitmentId(index: Int, id: FinanceCommitmentId?) {
        if (id == null) bindNull(index) else bindText(index, id.value)
    }

    private fun SQLiteStatement.commitmentIdOrNull(index: Int): FinanceCommitmentId? =
        if (isNull(index)) null else FinanceCommitmentId(getText(index))

    private companion object {
        const val RETIRED_VERSION = Long.MAX_VALUE
        const val MAX_CALENDAR_ROWS = 2000
    }
}
