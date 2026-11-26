package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Reservation ledger for a sealed, inactive SQL14 generation. */
internal object RealityHousehold14ReservationSchema {
    const val VERSION = 14
    private const val TABLE = "household_stock_reservation_event"

    private fun reference(column: String) =
        "length($column) BETWEEN 1 AND 128 AND length(CAST($column AS BLOB)) <= 384 " +
            "AND instr($column, char(0)) = 0 " +
            "AND $column NOT GLOB ('*[' || char(1) || '-' || char(32) || char(127) || ']*')"

    private val table = """
        CREATE TABLE $TABLE (
            event_id TEXT PRIMARY KEY NOT NULL CHECK(${reference("event_id")}),
            intent_key TEXT NOT NULL UNIQUE CHECK(${reference("intent_key")}),
            event_type TEXT NOT NULL CHECK(event_type IN ('RESERVE', 'RELEASE')),
            reservation_ref TEXT NOT NULL CHECK(${reference("reservation_ref")}),
            stock_item_ref TEXT NOT NULL CHECK(${reference("stock_item_ref")}),
            unit TEXT NOT NULL CHECK(unit IN
                ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
            quantity_thousandths INTEGER NOT NULL CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
            principal TEXT NOT NULL CHECK(${reference("principal")}),
            recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= 0),
            revision INTEGER NOT NULL UNIQUE CHECK(revision > 0),
            CHECK((event_type = 'RESERVE') = (event_id = reservation_ref))
        ) STRICT
    """.trimIndent()

    private val insertGuard = """
        CREATE TRIGGER ${TABLE}_insert_guard
        BEFORE INSERT ON $TABLE
        WHEN EXISTS (
            SELECT 1 FROM $TABLE
            WHERE event_id = NEW.event_id OR intent_key = NEW.intent_key OR revision = NEW.revision
        ) OR (NEW.event_type = 'RELEASE' AND NOT EXISTS (
            SELECT 1 FROM $TABLE AS reserved
            WHERE reserved.event_id = NEW.reservation_ref AND reserved.event_type = 'RESERVE'
                AND reserved.stock_item_ref = NEW.stock_item_ref AND reserved.unit = NEW.unit
                AND reserved.principal = NEW.principal AND reserved.revision < NEW.revision
                AND reserved.recorded_at_ms <= NEW.recorded_at_ms
                AND NEW.quantity_thousandths <= reserved.quantity_thousandths - COALESCE((
                    SELECT SUM(released.quantity_thousandths) FROM $TABLE AS released
                    WHERE released.reservation_ref = reserved.event_id AND released.event_type = 'RELEASE'
                ), 0)
        ))
        BEGIN
            SELECT RAISE(ABORT, 'invalid stock reservation event');
        END
    """.trimIndent()

    private fun appendOnlyGuard(action: String) = """
        CREATE TRIGGER ${TABLE}_${action.lowercase()}_guard
        BEFORE $action ON $TABLE
        BEGIN
            SELECT RAISE(ABORT, 'stock reservation ledger is append-only');
        END
    """.trimIndent()

    private val definitions = mapOf(
        TABLE to table,
        "${TABLE}_insert_guard" to insertGuard,
        "${TABLE}_update_guard" to appendOnlyGuard("UPDATE"),
        "${TABLE}_delete_guard" to appendOnlyGuard("DELETE"),
    )

    fun installOnStagedThirteenConnection(db: SQLiteConnection, beforeCommit: () -> Unit) {
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val history = RealityHousehold13CompensationRowsPort.captureConnection(db)
            for (ddl in definitions.values) db.execSQL(ddl)
            check(RealityHousehold13CompensationRowsPort.captureConnection(db) == history) {
                "SQL14 migration changed SQL13 stock history"
            }
            db.prepare("SELECT 1 FROM $TABLE LIMIT 1").use {
                check(!it.step()) { "SQL14 staged reservation ledger must start empty" }
            }
            db.execSQL("PRAGMA user_version = $VERSION")
            requireDefinitions(db)
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

    fun requireDefinitions(db: SQLiteConnection) {
        db.prepare("PRAGMA user_version").use {
            check(it.step() && it.getLong(0) == VERSION.toLong()) { "Reservation schema requires SQL14" }
        }
        val actual = db.prepare(
            "SELECT name,sql FROM sqlite_schema WHERE tbl_name = '$TABLE' AND sql IS NOT NULL",
        ).use { rows ->
            buildMap { while (rows.step()) put(rows.getText(0), rows.getText(1)) }
        }
        check(actual == definitions) { "SQL14 stock reservation schema differs" }
    }
}
