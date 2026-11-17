package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * W01-B3B0 real SQLite WAL tests, DEV/STAGED9 only.
 * Synthetic CONFIRMED SQL state does NOT attest human authentication or ACL.
 */
class RealityFinance9OpeningProvenanceReadTest {
    @TempDir lateinit var root: Path

    private fun open(path: Path): SQLiteConnection =
        BundledSQLiteDriver().open(path.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun scalar(db: SQLiteConnection, sql: String): Long =
        db.prepare(sql).use {
            assertTrue(it.step())
            it.getLong(0)
        }

    private fun stageEight(label: String): Path {
        val file = root.resolve(label + ".db")
        RealityStore(file.toString(), planningStage8 = true).use {
            assertEquals(8, it.schemaVersion())
        }
        return file
    }

    private fun upgrade(file: Path) {
        open(file).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "b3b0-" + file.fileName.toString(), "a".repeat(64), 1000L,
            )
        }
    }

    private fun stageNine(label: String): Path =
        stageEight(label).also(::upgrade)

    private fun account(db: SQLiteConnection, id: String, name: String = id) {
        db.execSQL(
            "INSERT INTO finance_account(account_id,display_name,currency_code,nature) " +
                "VALUES('$id','$name','ARS','CASH')"
        )
    }

    private fun opening(db: SQLiteConnection, id: String, minor: Long) {
        db.execSQL(
            "INSERT INTO finance_account_opening(account_id,opening_cause_id,amount_minor) " +
                "VALUES('$id','cause-$id',$minor)"
        )
    }

    private fun confirm(db: SQLiteConnection, id: String, minor: Long) {
        db.execSQL(
            "INSERT INTO finance_opening_confirmation(" +
                "account_id,amount_minor,confirmation_state,confirmed_at_ms,provenance) " +
                "VALUES('$id',$minor,'CONFIRMED',1000,'LOCAL_EXPLICIT_STAGE_FIXTURE')"
        )
    }

    private fun entry(
        db: SQLiteConnection, id: String, account: String,
        minor: Long, kind: String, at: Long,
    ) {
        db.execSQL(
            "INSERT INTO finance_entry(" +
                "entry_id,account_id,amount_minor,kind,currency_code," +
                "concept,category,effective_at_epoch_millis,recorded_at_epoch_millis)" +
                " VALUES('$id','$account',$minor,'$kind','ARS'," +
                "'Movimiento de prueba','Prueba',$at,$at)"
        )
    }

    private fun read(file: Path): RealityFinance9OpeningProvenanceSnapshot? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9OpeningProvenanceReadForDevOnly().read()
        }

    @Test fun o01LegacyPositiveOpeningMigratesUnverifiedAndNeverShowsAmount() {
        val file = stageEight("legacy")
        open(file).use { db ->
            account(db, "cash")
            opening(db, "cash", 500L)
        }
        upgrade(file)
        val row = requireNotNull(read(file)).accounts.single()
        assertEquals(RealityFinance9OpeningProvenanceState.LEGACY_UNVERIFIED, row.state)
        assertNull(row.confirmedOpeningMinor)
        assertNull(row.ledgerBalanceMinor)
        open(file).use { db ->
            assertEquals(0L, scalar(db, "SELECT count(*) FROM finance_apply_grant"))
        }
    }

    @Test fun o02MissingConfirmationRowCannotBecomeZeroBalance() {
        val file = stageNine("missing")
        open(file).use { account(it, "cash") }
        val read = requireNotNull(read(file))
        assertEquals(0L, read.sourceRevision)
        val row = read.accounts.single()
        assertEquals(RealityFinance9OpeningProvenanceState.MISSING_CONFIRMATION, row.state)
        assertNull(row.confirmedOpeningMinor)
        assertNull(row.ledgerBalanceMinor)
    }

    @Test fun o03OnlyExplicitSqlConfirmationOfZeroMayShowZero() {
        val file = stageNine("explicit-zero")
        open(file).use {
            account(it, "cash")
            confirm(it, "cash", 0L)
        }
        val row = requireNotNull(read(file)).accounts.single()
        assertEquals(RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL, row.state)
        assertEquals(0L, row.confirmedOpeningMinor)
        assertEquals(0L, row.ledgerBalanceMinor)
    }

    @Test fun o04PositivePhysicalOpeningMismatchFailsClosed() {
        val file = stageNine("mismatch")
        open(file).use {
            account(it, "cash")
            opening(it, "cash", 300L)
            confirm(it, "cash", 301L)
        }
        assertNull(read(file))
    }

    @Test fun o05PositiveConfirmedWithoutPhysicalOpeningFailsClosed() {
        val file = stageNine("no-physical")
        open(file).use {
            account(it, "cash")
            confirm(it, "cash", 50L)
        }
        assertNull(read(file))
    }

    @Test fun o06IncomeExpenseExactBalanceAndOverflowFailClosed() {
        val file = stageNine("arithmetic")
        open(file).use {
            account(it, "cash")
            opening(it, "cash", 1000L)
            confirm(it, "cash", 1000L)
            entry(it, "income", "cash", 400L, "INCOME", 10L)
            entry(it, "expense", "cash", 150L, "EXPENSE", 20L)
        }
        val row = requireNotNull(read(file)).accounts.single()
        assertEquals(1250L, row.ledgerBalanceMinor)
        open(file).use {
            entry(it, "overflow", "cash", Long.MAX_VALUE, "INCOME", 30L)
        }
        assertNull(read(file))
    }

    @Test fun o07WalReaderSeesStableOldSnapshotAndLaterReadSeesNewRevision() {
        val file = stageNine("wal-snapshot")
        open(file).use {
            account(it, "cash", "Antes")
            confirm(it, "cash", 0L)
        }
        open(file).use { oldReader ->
            open(file).use { writer ->
                oldReader.execSQL("BEGIN DEFERRED TRANSACTION")
                try {
                    assertEquals(0L, scalar(oldReader,
                        "SELECT revision FROM finance_state WHERE singleton_id=1"))
                    assertEquals("Antes", oldReader.prepare(
                        "SELECT display_name FROM finance_account WHERE account_id='cash'"
                    ).use { assertTrue(it.step()); it.getText(0) })
                    writer.execSQL("BEGIN IMMEDIATE TRANSACTION")
                    try {
                        writer.execSQL(
                            "UPDATE finance_account SET display_name='Despues' " +
                                "WHERE account_id='cash'"
                        )
                        writer.execSQL(
                            "UPDATE finance_state SET revision=1 WHERE singleton_id=1"
                        )
                        writer.execSQL("COMMIT TRANSACTION")
                    } catch (error: Throwable) {
                        runCatching { writer.execSQL("ROLLBACK TRANSACTION") }
                        throw error
                    }
                    assertEquals(0L, scalar(oldReader,
                        "SELECT revision FROM finance_state WHERE singleton_id=1"))
                    assertEquals("Antes", oldReader.prepare(
                        "SELECT display_name FROM finance_account WHERE account_id='cash'"
                    ).use { assertTrue(it.step()); it.getText(0) })
                } finally {
                    oldReader.execSQL("ROLLBACK TRANSACTION")
                }
            }
        }
        val latest = requireNotNull(read(file))
        assertEquals(1L, latest.sourceRevision)
        assertEquals("Despues", latest.accounts.single().displayName)
        assertEquals(0L, latest.accounts.single().ledgerBalanceMinor)
    }

    @Test fun o08LegacyFactoriesAndClosedReaderDenyWithoutCreatingSchemaNine() {
        val seven = root.resolve("seven.db")
        RealityStore(seven.toString()).use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.finance9OpeningProvenanceReadForDevOnly()
            }
        }
        val eight = stageEight("eight")
        RealityStore(eight.toString(), planningStage8 = true).use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.finance9OpeningProvenanceReadForDevOnly()
            }
        }
        val stage = stageNine("closed")
        val store = RealityStore(stage.toString(), planningStage8 = true, financeStage9 = true)
        val port = store.finance9OpeningProvenanceReadForDevOnly()
        store.close()
        assertNull(port.read())
        open(seven).use { assertEquals(7L, scalar(it, "PRAGMA user_version")) }
        open(eight).use { assertEquals(8L, scalar(it, "PRAGMA user_version")) }
    }
}
