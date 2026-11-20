package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Path

enum class RealityHousehold10RestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/**
 * Restores only Household rows. The generation owner must authenticate the snapshot and admit
 * a canonical STAGED10 target under its exclusive, pinned pointer lease before calling.
 */
object RealityHousehold10StagedRowsRestorePort {
    fun restoreOrVerifyTrustedStagedTen(
        file: Path,
        snapshot: RealityHousehold10Rows,
    ): RealityHousehold10RestoreDisposition = restore(file, snapshot) {}

    internal fun restore(
        file: Path,
        snapshot: RealityHousehold10Rows,
        beforeCommit: () -> Unit,
    ): RealityHousehold10RestoreDisposition {
        require(snapshot.needs.size <= 8_192) { "Too many Household10 rows" }
        val expected = snapshot.copy(needs = snapshot.needs.toList())
        Household10RowsSql.validate(expected)
        val physical = Household10RowsSql.checkFile(file)
        val disposition = BundledSQLiteDriver().open(
            physical.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            val outcome = try {
                Household10RowsSql.checkSchema(db)
                Household10RowsSql.checkIntegrity(db)
                val current = Household10RowsSql.readRows(db)
                val result = if (current == expected) {
                    RealityHousehold10RestoreDisposition.ALREADY_IDENTICAL
                } else {
                    check(current.revision == 0L && current.needs.isEmpty()) {
                        "Household10 refuses a nonempty or previously revised stage"
                    }
                    db.writeRows(expected)
                    check(Household10RowsSql.readRows(db) == expected) { "Household10 transaction readback differs" }
                    Household10RowsSql.checkIntegrity(db)
                    beforeCommit()
                    RealityHousehold10RestoreDisposition.RESTORED
                }
                db.execSQL("COMMIT TRANSACTION")
                result
            } catch (failure: Throwable) {
                try {
                    db.execSQL("ROLLBACK TRANSACTION")
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressed(rollbackFailure)
                }
                throw failure
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "Household10 stage WAL checkpoint failed" }
            }
            outcome
        }
        check(RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(physical) == expected) {
            "Household10 staged readback differs after close"
        }
        return disposition
    }

    private fun SQLiteConnection.writeRows(snapshot: RealityHousehold10Rows) {
        prepare("UPDATE household_need_state SET revision = ? WHERE singleton_id = 1").use {
            it.bindLong(1, snapshot.revision)
            it.step()
        }
        prepare("INSERT INTO household_need(${Household10RowsSql.NEED_COLUMNS}) VALUES (?,?,?,?,?,?,?,?)").use { stmt ->
            for (row in snapshot.needs) {
                stmt.bindText(1, row.id)
                stmt.bindText(2, row.name)
                stmt.bindLong(3, row.quantityThousandths.toLong())
                stmt.bindText(4, row.unit)
                stmt.bindText(5, row.priority)
                stmt.bindText(6, row.status)
                stmt.bindLong(7, row.revision)
                if (row.dueEpochDay == null) stmt.bindNull(8) else stmt.bindLong(8, row.dueEpochDay)
                stmt.step()
                stmt.reset()
            }
        }
    }
}
