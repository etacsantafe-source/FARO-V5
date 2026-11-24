package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Path

/**
 * W05-A1B3B1: Household10 readback from SEALED STAGED Reality11, no downgrade.
 * The generation owner must pin host, pointer, identity, and exclude ACTIVE.
 */
object RealityHousehold11NeedsRowsPort {
    fun readTrustedSealedStageEleven(file: Path): RealityHousehold10Rows {
        val physical = Household11ReceiptRowsSql.checkFile(file)
        return BundledSQLiteDriver().open(
            physical.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            Household11ReceiptRowsSql.checkSchema(db)
            Household11ReceiptRowsSql.checkIntegrity(db)
            db.prepare("PRAGMA table_list(household_need)").use { stmt ->
                check(stmt.step() && stmt.getText(2) == "table" &&
                    stmt.getLong(3) == 8L && stmt.getLong(5) == 1L && !stmt.step()) {
                    "Reality11 requires exact STRICT Household10 need table"
                }
            }
            db.prepare("PRAGMA table_list(household_need_state)").use { stmt ->
                check(stmt.step() && stmt.getText(2) == "table" &&
                    stmt.getLong(3) == 2L && stmt.getLong(5) == 1L && !stmt.step()) {
                    "Reality11 requires exact STRICT Household10 state table"
                }
            }
            for ((table, expected) in listOf(
                "household_need" to Household10RowsSql.NEED_COLUMNS.split(','),
                "household_need_state" to listOf("singleton_id", "revision"),
            )) {
                val actual = db.prepare("PRAGMA table_info(" + table + ")").use { stmt ->
                    buildList { while (stmt.step()) add(stmt.getText(1)) }
                }
                check(actual == expected) { "Reality11 Household10 columns mismatch: " + table }
            }
            db.prepare(
                "SELECT count(*) FROM sqlite_schema WHERE type='trigger' AND " +
                    "tbl_name COLLATE NOCASE IN ('household_need','household_need_state')",
            ).use {
                check(it.step() && it.getLong(0) == 0L) {
                    "Reality11 Household10 unexpected trigger"
                }
            }
            db.prepare(
                "SELECT count(*) FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                    "WHERE s.type = 'table' AND fk.\"table\" COLLATE NOCASE IN " +
                    "('household_need','household_need_state')",
            ).use {
                check(it.step() && it.getLong(0) == 0L) {
                    "Reality11 Household10 unexpected inbound foreign key"
                }
            }
            Household10RowsSql.readRows(db)
        }
    }
}
