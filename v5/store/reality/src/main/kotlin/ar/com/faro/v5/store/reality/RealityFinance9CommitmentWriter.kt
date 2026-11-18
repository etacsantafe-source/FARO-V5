package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentCreate
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentId
import ar.com.faro.v5.owner.finance.api.FinanceCommitmentPayment
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceScheduleRecurrence
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
    }

    private fun SQLiteStatement.bindCommitmentId(index: Int, id: FinanceCommitmentId?) {
        if (id == null) bindNull(index) else bindText(index, id.value)
    }
}
