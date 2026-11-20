package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold10MigrationTest {
    @TempDir lateinit var root: Path

    @Test
    fun migratesOnlyTheCopyAndPreservesFinanceOpeningAndProvenance() {
        val origin = financeNine()
        val stage = Files.copy(origin, root.resolve("separate-stage.db"))
        val originBefore = Files.readAllBytes(origin)

        open(stage).use { RealityHousehold10Schema.installOnStagedNineConnection(it) }

        assertArrayEquals(originBefore, Files.readAllBytes(origin))
        open(origin).use { assertUnmigrated(it) }
        open(stage).use { db ->
            assertEquals(10L, db.scalar("PRAGMA user_version"))
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_need"))
            assertEquals(0L, db.scalar("SELECT revision FROM household_need_state WHERE singleton_id = 1"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            db.prepare("SELECT confirmation_state, provenance FROM finance_opening_confirmation").use {
                assertTrue(it.step())
                assertEquals("LEGACY_UNVERIFIED", it.getText(0))
                assertEquals("MIGRATED_FROM_8", it.getText(1))
            }
            db.prepare("PRAGMA integrity_check").use {
                assertTrue(it.step())
                assertEquals("ok", it.getText(0))
            }
        }
    }

    @Test
    fun rollsBackDdlAndVersionAfterFaultAndAllowsRetry() {
        open(financeNine()).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db) {
                    assertEquals(10L, db.scalar("PRAGMA user_version"))
                    assertTrue(db.hasHouseholdTables())
                    error("injected fault before commit")
                }
            }
            assertUnmigrated(db)
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))

            RealityHousehold10Schema.installOnStagedNineConnection(db)
            assertEquals(10L, db.scalar("PRAGMA user_version"))
        }
    }

    @Test
    fun rejectsOlderIncompleteAndAlreadyMigratedLayouts() {
        val older = root.resolve("eight.db")
        RealityStore(older.toString(), planningStage8 = true).close()
        open(older).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db)
            }
            assertEquals(8L, db.scalar("PRAGMA user_version"))
            assertFalse(db.hasHouseholdTables())
        }
        open(financeNine()).use { db ->
            db.execSQL("DROP TABLE finance_schema_migration_audit")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db)
            }
            assertUnmigrated(db)
        }
        open(financeNine("retry-nine.db")).use { db ->
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db)
            }
            assertEquals(10L, db.scalar("PRAGMA user_version"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need_state"))
        }
    }

    @Test
    fun rejectsDisabledAndViolatedForeignKeys() {
        open(financeNine()).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db)
            }
            assertUnmigrated(db)

            db.execSQL("INSERT INTO finance_account_opening VALUES ('missing', 'bad-cause', 5)")
            db.execSQL("PRAGMA foreign_keys = ON")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold10Schema.installOnStagedNineConnection(db)
            }
            assertUnmigrated(db)
        }
    }

    @Test
    fun enforcesNeedRowConstraintsAndUniqueIdentity() {
        open(financeNine()).use { db ->
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1', 'Yerba', 1, 'UNIT', 'LOW', 'OPEN', 0, NULL)")
            db.execSQL(
                "INSERT INTO household_need VALUES " +
                    "('n2', 'Yerba', 2147483647, 'GRAM', 'HIGH', 'COMPLETED', 1, 2932896)",
            )
            val invalidRows = listOf(
                "'n1', 'Yerba', 1, 'UNIT', 'LOW', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 0, 'UNIT', 'LOW', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 2147483648, 'UNIT', 'LOW', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 1.5, 'UNIT', 'LOW', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 1, '', 'LOW', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 1, 'UNIT', 'URGENT', 'OPEN', 0, NULL",
                "'bad', 'Yerba', 1, 'UNIT', 'LOW', 'BOUGHT', 0, NULL",
                "'bad', 'Yerba', 1, 'UNIT', 'LOW', 'OPEN', -1, NULL",
                "'bad', 'Yerba', 1, 'UNIT', 'LOW', 'OPEN', 0, -719163",
                "'bad', 'Yerba', 1, 'UNIT', 'LOW', 'OPEN', 0, 2932897",
            )
            for (row in invalidRows) {
                assertThrows(SQLiteException::class.java) {
                    db.execSQL("INSERT INTO household_need VALUES ($row)")
                }
            }
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_need"))
        }
    }

    @Test
    fun existingActiveFinanceNineOpenerRejectsHouseholdTen() {
        val stage = financeNine()
        open(stage).use { RealityHousehold10Schema.installOnStagedNineConnection(it) }

        assertThrows(IllegalStateException::class.java) {
            RealityStore(
                fileName = stage.toString(),
                materialWriteGate = SingleProcessMaterialWriteGate(),
                planningStage8 = true,
                financeActive9 = true,
            ).use { }
        }
        open(stage).use { assertEquals(10L, it.scalar("PRAGMA user_version")) }
    }

    private fun financeNine(name: String = "finance-nine.db"): Path {
        val file = root.resolve(name)
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct-1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct-1','cause-1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "prepare-nine", "a".repeat(64), 1L)
            assertEquals(0L, db.scalar("PRAGMA wal_checkpoint(TRUNCATE)"))
        }
        return file
    }

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys = ON") }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }

    private fun SQLiteConnection.hasHouseholdTables(): Boolean =
        scalar("SELECT count(*) FROM sqlite_schema WHERE type = 'table' AND name IN ('household_need', 'household_need_state')") > 0

    private fun assertUnmigrated(db: SQLiteConnection) {
        assertEquals(9L, db.scalar("PRAGMA user_version"))
        assertFalse(db.hasHouseholdTables())
    }
}
