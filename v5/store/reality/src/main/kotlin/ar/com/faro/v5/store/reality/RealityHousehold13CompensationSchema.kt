package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** SQL13 adds compensation history to an inactive, sealed SQL12 file. */
internal object RealityHousehold13CompensationSchema {
    const val VERSION = 13

    private val table = """
        CREATE TABLE household_stock_compensation (
            compensation_id TEXT PRIMARY KEY NOT NULL
                CHECK(length(compensation_id) BETWEEN 1 AND 128 AND compensation_id = trim(compensation_id)
                    AND instr(compensation_id, char(0)) = 0),
            intent_key TEXT NOT NULL UNIQUE
                CHECK(length(intent_key) BETWEEN 1 AND 128 AND intent_key = trim(intent_key)
                    AND instr(intent_key, char(0)) = 0),
            receipt_id TEXT NOT NULL UNIQUE
                CHECK(length(receipt_id) BETWEEN 1 AND 128 AND receipt_id = trim(receipt_id)
                    AND instr(receipt_id, char(0)) = 0),
            purchase_ref TEXT NOT NULL
                CHECK(length(purchase_ref) BETWEEN 1 AND 128 AND purchase_ref = trim(purchase_ref)
                    AND instr(purchase_ref, char(0)) = 0),
            quantity_thousandths INTEGER NOT NULL CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
            unit TEXT NOT NULL CHECK(unit IN
                ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
            principal TEXT NOT NULL
                CHECK(length(principal) BETWEEN 1 AND 128 AND principal = trim(principal)
                    AND instr(principal, char(0)) = 0),
            recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= 0),
            revision INTEGER NOT NULL UNIQUE CHECK(revision > 0)
        ) STRICT
        """.trimIndent()

    private fun linkExists(row: String) = """
        EXISTS (
            SELECT 1
            FROM household_stock_receipt AS r
            JOIN household_confirmed_purchase AS p ON p.purchase_ref = r.purchase_ref
            JOIN household_stock_receipt_state AS s ON s.singleton_id = 1
            WHERE r.receipt_id = $row.receipt_id AND r.purchase_ref = $row.purchase_ref
                AND r.quantity_thousandths = $row.quantity_thousandths AND r.unit = $row.unit
                AND p.stock_item_ref = r.stock_item_ref AND p.unit = r.unit
                AND p.confirmed_by = $row.principal AND p.provenance = 'MANUAL_EXPLICIT'
                AND $row.recorded_at_ms >= r.recorded_at_ms
                AND $row.revision > r.revision AND $row.revision <= s.revision
                AND NOT EXISTS (
                    SELECT 1 FROM household_stock_receipt AS other WHERE other.revision = $row.revision
                )
        )
        """.trimIndent()

    private val insertGuard = """
        CREATE TRIGGER household_stock_compensation_insert_guard
        BEFORE INSERT ON household_stock_compensation
        WHEN NOT ${linkExists("NEW")}
            OR EXISTS (
                SELECT 1 FROM household_stock_compensation AS existing
                WHERE existing.compensation_id = NEW.compensation_id OR existing.intent_key = NEW.intent_key
                    OR existing.receipt_id = NEW.receipt_id OR existing.revision = NEW.revision
            )
        BEGIN
            SELECT RAISE(ABORT, 'invalid stock compensation source');
        END
        """.trimIndent().trimStart()

    private val updateGuard = """
        CREATE TRIGGER household_stock_compensation_update_guard
        BEFORE UPDATE ON household_stock_compensation
        BEGIN
            SELECT RAISE(ABORT, 'stock compensation is append-only');
        END
        """.trimIndent()

    private val deleteGuard = """
        CREATE TRIGGER household_stock_compensation_delete_guard
        BEFORE DELETE ON household_stock_compensation
        BEGIN
            SELECT RAISE(ABORT, 'stock compensation is append-only');
        END
        """.trimIndent()

    private val definitions = mapOf(
        "household_stock_compensation" to table,
        "household_stock_compensation_insert_guard" to insertGuard,
        "household_stock_compensation_update_guard" to updateGuard,
        "household_stock_compensation_delete_guard" to deleteGuard,
    )

    fun installOnStagedTwelveConnection(db: SQLiteConnection, beforeCommit: () -> Unit) {
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val historical = RealityHousehold12PurchaseRowsPort.readCurrentHouseholdRows(db, expectedVersion = 12)
            for (ddl in definitions.values) db.execSQL(ddl)
            db.execSQL("PRAGMA user_version = $VERSION")
            check(validate(db) == historical) { "SQL13 migration modified historical Household rows" }
            beforeCommit()
            db.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            try {
                db.execSQL("ROLLBACK TRANSACTION")
            } catch (rollback: Throwable) {
                failure.addSuppressed(rollback)
            }
            throw failure
        }
    }

    fun validate(db: SQLiteConnection): RealityHousehold12PurchaseReceiptRows {
        val historical = RealityHousehold12PurchaseRowsPort.readCurrentHouseholdRows(db, expectedVersion = VERSION)
        val actual = db.prepare(
            "SELECT name,sql FROM sqlite_schema WHERE tbl_name = 'household_stock_compensation' AND sql IS NOT NULL",
        ).use { rows ->
            buildMap { while (rows.step()) put(rows.getText(0), rows.getText(1)) }
        }
        check(actual == definitions) { "SQL13 stock compensation schema differs" }
        db.prepare(
            "SELECT 1 FROM household_stock_compensation AS c WHERE NOT (" + linkExists("c") + ") LIMIT 1",
        ).use {
            check(!it.step()) { "SQL13 stock compensation source differs" }
        }
        return historical
    }
}
