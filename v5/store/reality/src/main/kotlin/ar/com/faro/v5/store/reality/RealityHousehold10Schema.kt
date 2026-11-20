package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.LocalDate

/** DDL for an isolated stage. Its future caller must prove generation admission. */
internal object RealityHousehold10Schema {
    private const val SOURCE_VERSION = 9
    const val VERSION = 10

    private val requiredSourceTables = listOf(
        "profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
        "finance_state", "finance_account", "finance_account_opening", "finance_entry",
        "objective_state", "objective_record", "matter_state", "matter_record",
        "reminder_state", "reminder_rule", "reminder_occurrence", "monthly_plan_state",
        "monthly_plan_item", "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
        "finance_opening_confirmation", "finance_transfer", "finance_debt", "finance_schedule",
        "finance_plan_item", "finance_plan_item_payment", "finance_budget_limit",
        "finance_schema_migration_audit",
    )

    fun installOnStagedNineConnection(
        connection: SQLiteConnection,
        beforeCommit: () -> Unit = {},
    ) {
        check(connection.readScalar("PRAGMA foreign_keys") == 1L) {
            "Household10 requires foreign-key enforcement before BEGIN"
        }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            check(connection.readScalar("PRAGMA user_version") == SOURCE_VERSION.toLong()) {
                "Household10 requires staged Reality9"
            }
            val tableNames = connection.prepare("SELECT name FROM sqlite_schema WHERE type = 'table'").use {
                buildSet {
                    while (it.step()) add(it.getText(0))
                }
            }
            check(tableNames.containsAll(requiredSourceTables)) {
                "Household10 requires the complete staged Reality9 table profile"
            }
            check(tableNames.none { it.startsWith("household_", ignoreCase = true) }) {
                "Household tables already exist in the staged Reality9"
            }
            connection.requireIntegrity()

            connection.execSQL(
                """
                CREATE TABLE household_need_state (
                    singleton_id INTEGER PRIMARY KEY NOT NULL CHECK(singleton_id = 1),
                    revision INTEGER NOT NULL CHECK(revision >= 0)
                ) STRICT
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE TABLE household_need (
                    need_id TEXT PRIMARY KEY NOT NULL CHECK(length(trim(need_id)) BETWEEN 1 AND 128),
                    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 160),
                    quantity_thousandths INTEGER NOT NULL CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
                    unit TEXT NOT NULL CHECK(unit IN ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
                    priority TEXT NOT NULL CHECK(priority IN ('LOW', 'NORMAL', 'HIGH')),
                    status TEXT NOT NULL CHECK(status IN ('OPEN', 'COMPLETED')),
                    revision INTEGER NOT NULL CHECK(revision >= 0),
                    due_epoch_day INTEGER CHECK(due_epoch_day BETWEEN
                        ${LocalDate.of(1, 1, 1).toEpochDay()} AND ${LocalDate.of(9999, 12, 31).toEpochDay()})
                ) STRICT
                """.trimIndent(),
            )
            connection.execSQL("INSERT INTO household_need_state(singleton_id, revision) VALUES (1, 0)")
            connection.execSQL("PRAGMA user_version = $VERSION")
            connection.requireIntegrity()
            beforeCommit()
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            try {
                connection.execSQL("ROLLBACK TRANSACTION")
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    private fun SQLiteConnection.readScalar(sql: String): Long = prepare(sql).use {
        check(it.step()) { "missing SQLite scalar result" }
        it.getLong(0)
    }

    private fun SQLiteConnection.requireIntegrity() {
        check(prepare("PRAGMA integrity_check").use { it.step() && it.getText(0) == "ok" && !it.step() }) {
            "staged Reality failed SQLite integrity check"
        }
        check(prepare("PRAGMA foreign_key_check").use { !it.step() }) {
            "staged Reality contains foreign-key violations"
        }
    }
}
