package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.LocalDate

/**
 * W05-A1A: DDL on an already isolated STAGED Reality10 connection ONLY.
 * Does not select, activate, open, back up or write to a live generation.
 */
internal object RealityHousehold11ReceiptSchema {
    private const val SOURCE_VERSION = 10
    const val VERSION = 11

    private val requiredSourceTables = listOf(
        "profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
        "finance_state", "finance_account", "finance_account_opening", "finance_entry",
        "objective_state", "objective_record", "matter_state", "matter_record",
        "reminder_state", "reminder_rule", "reminder_occurrence", "monthly_plan_state",
        "monthly_plan_item", "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
        "finance_opening_confirmation", "finance_transfer", "finance_debt", "finance_schedule",
        "finance_plan_item", "finance_plan_item_payment", "finance_budget_limit",
        "finance_schema_migration_audit", "household_need_state", "household_need",
    )

    fun installOnStagedTenConnection(
        connection: SQLiteConnection,
        beforeCommit: () -> Unit = {},
    ) {
        check(connection.scalar("PRAGMA foreign_keys") == 1L) {
            "Household11 requires foreign-key enforcement before BEGIN"
        }
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            check(connection.scalar("PRAGMA user_version") == SOURCE_VERSION.toLong()) {
                "Household11 receipts require an isolated staged Reality10"
            }
            val tables = connection.prepare("SELECT name FROM sqlite_schema WHERE type = 'table'").use {
                buildSet {
                    while (it.step()) add(it.getText(0))
                }
            }
            check(tables.containsAll(requiredSourceTables)) {
                "Household11 requires complete Reality10 source tables"
            }
            check(tables.none { it.startsWith("household_stock_", ignoreCase = true) }) {
                "Household stock objects already exist"
            }
            connection.requireIntegrity()

            connection.execSQL(
                """
                CREATE TABLE household_stock_receipt_state (
                    singleton_id INTEGER PRIMARY KEY NOT NULL CHECK(singleton_id = 1),
                    revision INTEGER NOT NULL CHECK(revision >= 0)
                ) STRICT
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE TABLE household_stock_receipt (
                    receipt_id TEXT PRIMARY KEY NOT NULL
                        CHECK(length(trim(receipt_id)) BETWEEN 1 AND 128),
                    intent_key TEXT NOT NULL UNIQUE
                        CHECK(length(trim(intent_key)) BETWEEN 1 AND 128),
                    purchase_ref TEXT NOT NULL
                        CHECK(length(trim(purchase_ref)) BETWEEN 1 AND 128),
                    stock_item_ref TEXT NOT NULL
                        CHECK(length(trim(stock_item_ref)) BETWEEN 1 AND 128),
                    quantity_thousandths INTEGER NOT NULL
                        CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
                    unit TEXT NOT NULL CHECK(unit IN
                        ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
                    received_epoch_day INTEGER NOT NULL CHECK(received_epoch_day BETWEEN
                        ${LocalDate.of(1, 1, 1).toEpochDay()} AND
                        ${LocalDate.of(9999, 12, 31).toEpochDay()}),
                    recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= 0),
                    revision INTEGER NOT NULL CHECK(revision > 0)
                ) STRICT
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO household_stock_receipt_state(singleton_id, revision) VALUES (1, 0)",
            )
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

    private fun SQLiteConnection.scalar(query: String): Long = prepare(query).use {
        check(it.step()) { "missing SQLite scalar" }
        it.getLong(0)
    }

    private fun SQLiteConnection.requireIntegrity() {
        check(prepare("PRAGMA integrity_check").use {
            it.step() && it.getText(0) == "ok" && !it.step()
        }) { "staged Reality failed SQLite integrity check" }
        check(prepare("PRAGMA foreign_key_check").use { !it.step() }) {
            "staged Reality contains foreign-key violations"
        }
    }
}
