package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** SQL15 is an inactive, sealed STAGED generation only. No stock.consume executor is enabled here. */
internal object RealityHousehold15ConsumptionSchema {
    const val VERSION = 15
    private const val TABLE = "household_stock_consumption_event"

    private fun reference(column: String) =
        "length($column) BETWEEN 1 AND 128 AND length(CAST($column AS BLOB)) <= 384 " +
            "AND instr($column, char(0)) = 0 " +
            "AND $column NOT GLOB ('*[' || char(1) || '-' || char(32) || char(127) || ']*')"

    private val table = """
        CREATE TABLE $TABLE (
            event_id TEXT PRIMARY KEY NOT NULL CHECK(${reference("event_id")}),
            intent_key TEXT NOT NULL UNIQUE CHECK(${reference("intent_key")}),
            event_type TEXT NOT NULL CHECK(event_type IN ('CONSUME', 'CONSUME_UNDO')),
            consumption_ref TEXT NOT NULL CHECK(${reference("consumption_ref")}),
            stock_item_ref TEXT NOT NULL CHECK(${reference("stock_item_ref")}),
            unit TEXT NOT NULL CHECK(unit IN
                ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
            quantity_thousandths INTEGER NOT NULL CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
            principal TEXT NOT NULL CHECK(${reference("principal")}),
            source_ref TEXT NOT NULL CHECK(${reference("source_ref")}),
            reservation_ref TEXT CHECK(reservation_ref IS NULL OR (${reference("reservation_ref")})),
            occurred_at_ms INTEGER NOT NULL CHECK(occurred_at_ms >= 0),
            recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= occurred_at_ms),
            revision INTEGER NOT NULL UNIQUE CHECK(revision > 0),
            CHECK((event_type = 'CONSUME') = (event_id = consumption_ref))
        ) STRICT
    """.trimIndent()

    private val insertGuard = """
        CREATE TRIGGER ${TABLE}_insert_guard
        BEFORE INSERT ON $TABLE
        WHEN EXISTS (
            SELECT 1 FROM $TABLE
            WHERE event_id = NEW.event_id OR intent_key = NEW.intent_key OR revision = NEW.revision
        ) OR (NEW.event_type = 'CONSUME_UNDO' AND NOT EXISTS (
            SELECT 1 FROM $TABLE AS consumed
            WHERE consumed.event_id = NEW.consumption_ref AND consumed.event_type = 'CONSUME'
                AND consumed.stock_item_ref = NEW.stock_item_ref AND consumed.unit = NEW.unit
                AND consumed.principal = NEW.principal AND consumed.source_ref = NEW.source_ref
                AND consumed.reservation_ref IS NEW.reservation_ref
                AND consumed.revision < NEW.revision AND consumed.recorded_at_ms <= NEW.recorded_at_ms
                AND NEW.quantity_thousandths <= consumed.quantity_thousandths - COALESCE((
                    SELECT SUM(undone.quantity_thousandths) FROM $TABLE AS undone
                    WHERE undone.consumption_ref = consumed.event_id AND undone.event_type = 'CONSUME_UNDO'
                ), 0)
        ))
        BEGIN
            SELECT RAISE(ABORT, 'invalid stock consumption event');
        END
    """.trimIndent()

    private fun appendOnlyGuard(action: String) = """
        CREATE TRIGGER ${TABLE}_${action.lowercase()}_guard
        BEFORE $action ON $TABLE
        BEGIN
            SELECT RAISE(ABORT, 'stock consumption ledger is append-only');
        END
    """.trimIndent()

    private val definitions = mapOf(
        TABLE to table,
        "${TABLE}_insert_guard" to insertGuard,
        "${TABLE}_update_guard" to appendOnlyGuard("UPDATE"),
        "${TABLE}_delete_guard" to appendOnlyGuard("DELETE"),
    )

    fun installOnStagedFourteenConnection(db: SQLiteConnection, beforeCommit: () -> Unit) {
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            // Reads and authenticates all SQL14 purchase/receipt/undo/reservation chronology.
            val history = RealityHousehold14ReservationRowsPort.captureConnection(db)
            for (ddl in definitions.values) db.execSQL(ddl)
            check(RealityHousehold14ReservationRowsPort.captureConnection(db) == history) {
                "SQL15 migration altered SQL14 historical rows"
            }
            db.prepare("SELECT 1 FROM $TABLE LIMIT 1").use {
                check(!it.step()) { "SQL15 staged consumption ledger must start empty" }
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
            check(it.step() && it.getLong(0) == VERSION.toLong()) { "Consumption schema requires SQL15" }
        }
        val actual = db.prepare(
            "SELECT name,sql FROM sqlite_schema WHERE tbl_name = '$TABLE' AND sql IS NOT NULL",
        ).use { rows ->
            buildMap { while (rows.step()) put(rows.getText(0), rows.getText(1)) }
        }
        check(actual == definitions) { "SQL15 stock consumption schema differs" }
    }
}
