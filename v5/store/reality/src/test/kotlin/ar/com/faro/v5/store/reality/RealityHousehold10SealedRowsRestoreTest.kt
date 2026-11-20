package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold10SealedRowsRestoreTest {
    @TempDir lateinit var root: Path

    private val first = RealityHousehold10Row("a", "Yerba", 500, "GRAM", "HIGH", "OPEN", 4, 20_000)
    private val second = RealityHousehold10Row("b", "Leche", 1_000, "LITER", "NORMAL", "COMPLETED", 7, null)
    private val snapshot = RealityHousehold10Rows(9, listOf(first, second))
    private val fresh = RealityHousehold10Rows(0, emptyList())

    @Test
    fun readsSealedRowsInCanonicalOrderAndRestoresDatesAndRevision() {
        val source = stage("source.db")
        open(source).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=9")
            db.execSQL("INSERT INTO household_need VALUES ('b','Leche',1000,'LITER','NORMAL','COMPLETED',7,NULL)")
            db.execSQL("INSERT INTO household_need VALUES ('a','Yerba',500,'GRAM','HIGH','OPEN',4,20000)")
            seal(db)
        }
        val bytes = Files.readAllBytes(source)
        val captured = read(source)
        assertEquals(snapshot, captured)
        assertArrayEquals(bytes, Files.readAllBytes(source))
        val target = stage("target.db")
        assertEquals(RealityHousehold10RestoreDisposition.RESTORED, restore(target, captured))
        assertEquals(snapshot, read(target))
    }

    @Test
    fun keepsRevisionOnEmptySnapshots() {
        val file = stage()
        val emptyRevised = RealityHousehold10Rows(18, emptyList())
        assertEquals(RealityHousehold10RestoreDisposition.RESTORED, restore(file, emptyRevised))
        assertEquals(emptyRevised, read(file))
        assertThrows(IllegalStateException::class.java) { restore(file, snapshot) }
    }

    @Test
    fun identicalReplayDoesNotWriteAndConflictingReplayStops() {
        val file = stage()
        restore(file, snapshot)
        val bytes = Files.readAllBytes(file)
        assertEquals(RealityHousehold10RestoreDisposition.ALREADY_IDENTICAL, restore(file, snapshot))
        assertArrayEquals(bytes, Files.readAllBytes(file))
        assertThrows(IllegalStateException::class.java) {
            restore(file, snapshot.copy(needs = listOf(first.copy(name = "Mate"), second)))
        }
        assertEquals(snapshot, read(file))
    }

    @Test
    fun rollsBackRowsAndRevisionOnFaultBeforeCommit() {
        val file = stage()
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold10StagedRowsRestorePort.restore(file, snapshot) { error("injected precommit failure") }
        }
        assertEquals(fresh, read(file))
        assertEquals(500L, financeOpening(file))
        assertEquals(RealityHousehold10RestoreDisposition.RESTORED, restore(file, snapshot))
    }

    @Test
    fun refusesWrongVersionLinksSidecarsAndForeignKeyViolations() {
        val nine = stage("nine.db", migrate = false)
        assertThrows(IllegalStateException::class.java) { read(nine) }
        assertThrows(IllegalStateException::class.java) { restore(nine, snapshot) }
        val file = stage()
        val symlink = Files.createSymbolicLink(root.resolve("symlink.db"), file)
        assertThrows(IllegalStateException::class.java) { read(symlink) }
        assertThrows(IllegalStateException::class.java) { restore(symlink, snapshot) }
        val hardlink = Files.createLink(root.resolve("hardlink.db"), file)
        assertThrows(IllegalStateException::class.java) { read(file) }
        assertThrows(IllegalStateException::class.java) { restore(file, snapshot) }
        Files.delete(hardlink)
        val sidecar = Files.createSymbolicLink(root.resolve("stage.db-wal"), root.resolve("missing"))
        assertThrows(IllegalStateException::class.java) { read(file) }
        assertThrows(IllegalStateException::class.java) { restore(file, snapshot) }
        Files.delete(sidecar)
        open(file).use { db ->
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('missing','bad-cause',5)")
            seal(db)
        }
        assertThrows(IllegalStateException::class.java) { read(file) }
        assertThrows(IllegalStateException::class.java) { restore(file, snapshot) }
    }

    @Test
    fun rejectsInvalidNoncanonicalAndOversizeInputWithoutEffects() {
        val file = stage()
        val bytes = Files.readAllBytes(file)
        val invalid = listOf(
            snapshot.copy(revision = -1),
            snapshot.copy(needs = listOf(second, first)),
            snapshot.copy(needs = listOf(first, first)),
            snapshot.copy(needs = listOf(first.copy(dueEpochDay = Long.MAX_VALUE))),
            snapshot.copy(needs = listOf(first.copy(unit = "POUND"))),
            snapshot.copy(needs = listOf(first.copy(name = " Yerba "))),
            snapshot.copy(needs = listOf(first.copy(revision = 10))),
            snapshot.copy(needs = List(8_193) { first }),
            snapshot.copy(needs = List(8_000) {
                first.copy(id = it.toString().padStart(4, '0') + "界".repeat(124), name = "界".repeat(160))
            }),
        )
        for (value in invalid) {
            assertThrows(IllegalArgumentException::class.java) { restore(file, value) }
        }
        assertArrayEquals(bytes, Files.readAllBytes(file))
    }

    @Test
    fun rejectsAStageWithHouseholdTriggersAndCorruptFiles() {
        val file = stage()
        open(file).use { db ->
            db.execSQL("CREATE TRIGGER wrong_owner AFTER INSERT ON household_need BEGIN DELETE FROM finance_account_opening; END")
            seal(db)
        }
        assertThrows(IllegalStateException::class.java) { restore(file, snapshot) }
        assertEquals(500L, financeOpening(file))
        val corrupt = Files.writeString(root.resolve("corrupt.db"), "invalid database")
        assertThrows(androidx.sqlite.SQLiteException::class.java) { read(corrupt) }
    }

    @Test
    fun restoresHouseholdWithoutChangingFinanceOrReceiptTables() {
        val file = stage()
        val sql = listOf(
            "SELECT quote(account_id)||':'||amount_minor FROM finance_account_opening",
            "SELECT group_concat(entry_id) FROM finance_entry",
            "SELECT count(*) FROM apply_attempt",
            "SELECT count(*) FROM execution_receipt",
            "SELECT count(*) FROM reality_outbox",
        )
        fun unrelatedRows() = open(file).use { db ->
            sql.map { query -> db.prepare(query).use { it.step(); if (it.isNull(0)) null else it.getText(0) } }
        }
        val before = unrelatedRows()
        restore(file, snapshot)
        assertEquals(before, unrelatedRows())
        assertEquals(snapshot, read(file))
    }

    private fun stage(name: String = "stage.db", migrate: Boolean = true): Path {
        val file = root.resolve(name)
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct-1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct-1','cause-1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "prepare-nine", "a".repeat(64), 1L)
            if (migrate) RealityHousehold10Schema.installOnStagedNineConnection(db)
            seal(db)
        }
        return file
    }

    private fun seal(db: SQLiteConnection) {
        db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { assertTrue(it.step() && it.getLong(0) == 0L) }
    }

    private fun open(file: Path) = BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun financeOpening(file: Path) = open(file).use { db ->
        db.prepare("SELECT amount_minor FROM finance_account_opening WHERE account_id='acct-1'").use {
            assertTrue(it.step())
            it.getLong(0)
        }
    }

    private fun restore(file: Path, value: RealityHousehold10Rows) =
        RealityHousehold10StagedRowsRestorePort.restoreOrVerifyTrustedStagedTen(file, value)

    private fun read(file: Path) = RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(file)
}
