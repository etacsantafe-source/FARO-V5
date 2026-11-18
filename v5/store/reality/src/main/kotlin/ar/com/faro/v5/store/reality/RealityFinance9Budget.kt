package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import ar.com.faro.v5.owner.finance.api.FinanceBudgetChange
import ar.com.faro.v5.owner.finance.api.FinanceBudgetCoverage
import ar.com.faro.v5.owner.finance.api.FinanceBudgetKey
import ar.com.faro.v5.owner.finance.api.FinanceBudgetObservation
import ar.com.faro.v5.owner.finance.api.FinanceBudgetQuery

internal class RealityFinance9Budget(private val connection: SQLiteConnection) {
    fun change(command: FinanceBudgetChange) {
        val previous = financeRevision()
        check(previous == command.expectedFinanceRevision) { "Budget review is stale" }
        val updated = Math.incrementExact(previous)
        val limit = command.limitMinor
        if (limit == null) {
            connection.prepare(
                "DELETE FROM finance_budget_limit WHERE period = ? AND category = ? AND currency_code = ?",
            ).use { st ->
                st.bindKey(command.key)
                st.step()
            }
        } else {
            connection.prepare(
                """
                INSERT INTO finance_budget_limit(
                    period, category, currency_code, limit_minor, revision, updated_at_ms, source
                ) VALUES (?, ?, ?, ?, ?, ?, 'MANUAL')
                ON CONFLICT(period, category, currency_code) DO UPDATE SET
                    limit_minor = excluded.limit_minor, revision = excluded.revision,
                    updated_at_ms = excluded.updated_at_ms, source = excluded.source
                """.trimIndent(),
            ).use { st ->
                st.bindKey(command.key)
                st.bindLong(4, limit)
                st.bindLong(5, updated)
                st.bindLong(6, command.recordedAtEpochMillis)
                st.step()
            }
        }
        requireOneChange()
        connection.prepare(
            "UPDATE finance_state SET revision = ? WHERE singleton_id = 1 AND revision = ?",
        ).use { st ->
            st.bindLong(1, updated)
            st.bindLong(2, previous)
            st.step()
        }
        requireOneChange()
    }

    fun observe(query: FinanceBudgetQuery): FinanceBudgetObservation {
        val revision = financeRevision()
        val (limit, limitRevision) = connection.prepare(
            """
            SELECT limit_minor, revision FROM finance_budget_limit
            WHERE period = ? AND category = ? AND currency_code = ?
            """.trimIndent(),
        ).use { st ->
            st.bindKey(query.key)
            if (st.step()) st.getLong(0) to st.getLong(1) else null to null
        }
        val actual = actualSpent(query)
        val planned = unassignedCommitments(query.key)
        return FinanceBudgetObservation(
            coverage = if (planned > 0) FinanceBudgetCoverage.PARTIAL else FinanceBudgetCoverage.READY,
            sourceRevision = revision,
            revisionOfLimit = limitRevision,
            limitMinor = limit,
            actualSpentMinor = actual,
            uncategorizedCommitmentsMinor = planned,
            estimatedAvailableMinor = if (planned == 0L) limit?.let { Math.subtractExact(it, actual) } else null,
        )
    }

    private fun actualSpent(query: FinanceBudgetQuery): Long {
        val start = query.key.period.atDay(1).atStartOfDay(query.civilZone).toInstant().toEpochMilli()
        val end = query.key.period.plusMonths(1).atDay(1).atStartOfDay(query.civilZone).toInstant().toEpochMilli()
        return connection.prepare(
            """
            SELECT COALESCE(SUM(e.amount_minor), 0) FROM finance_entry e
            WHERE e.kind = 'EXPENSE' AND e.category = ? AND e.currency_code = ?
              AND e.effective_at_epoch_millis >= ? AND e.effective_at_epoch_millis < ?
              AND NOT EXISTS (SELECT 1 FROM finance_transfer t WHERE t.debit_entry_id = e.entry_id)
            """.trimIndent(),
        ).use { st ->
            st.bindText(1, query.key.category.value)
            st.bindText(2, query.key.currency.value)
            st.bindLong(3, start)
            st.bindLong(4, end)
            check(st.step())
            st.getLong(0).also { check(it >= 0) }
        }
    }

    private fun unassignedCommitments(key: FinanceBudgetKey): Long =
        connection.prepare(
            """
            SELECT p.expected_minor, COALESCE(SUM(a.amount_minor), 0),
              COUNT(a.entry_id), COUNT(e.entry_id)
            FROM finance_plan_item p
            LEFT JOIN finance_plan_item_payment a ON p.plan_item_id = a.plan_item_id
            LEFT JOIN finance_entry e ON e.entry_id = a.entry_id AND e.kind = 'EXPENSE'
              AND e.currency_code = p.currency_code AND e.amount_minor = a.amount_minor
            WHERE p.period = ? AND p.currency_code = ? AND p.state = 'PLANNED'
            GROUP BY p.plan_item_id, p.expected_minor
            """.trimIndent(),
        ).use { st ->
            st.bindText(1, key.period.toString())
            st.bindText(2, key.currency.value)
            var total = 0L
            while (st.step()) {
                val expected = st.getLong(0)
                val paid = st.getLong(1)
                check(paid in 0..expected && st.getLong(2) == st.getLong(3)) { "Invalid planned payment history" }
                total = Math.addExact(total, expected - paid)
            }
            total
        }

    private fun financeRevision(): Long =
        connection.prepare("SELECT revision FROM finance_state WHERE singleton_id = 1").use { st ->
            check(st.step())
            st.getLong(0).also { check(it >= 0) }
        }

    private fun requireOneChange() {
        check(connection.prepare("SELECT changes()").use { it.step() && it.getLong(0) == 1L })
    }

    private fun SQLiteStatement.bindKey(key: FinanceBudgetKey) {
        bindText(1, key.period.toString())
        bindText(2, key.category.value)
        bindText(3, key.currency.value)
    }
}
