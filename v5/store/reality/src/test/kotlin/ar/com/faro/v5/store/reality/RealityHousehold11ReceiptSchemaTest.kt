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

class RealityHousehold11ReceiptSchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun migratesOnlySeparateCopyAndPreservesAllPriorData() {
        val origin = realityTen()
        val copy = Files.copy(origin, root.resolve("isolated-copy.db"))
        val originalBytes = Files.readAllBytes(origin)
        open(copy).use { RealityHousehold11ReceiptSchema.installOnStagedTenConnection(it) }
        assertArrayEquals(originalBytes, Files.readAllBytes(origin))
        open(origin).use { assertTenNoStock(it) }
        open(copy).use { db ->
            assertEquals(11L, db.scalar("PRAGMA user_version"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
            assertEquals(0L, db.scalar("SELECT revision FROM household_stock_receipt_state"))
            assertHealthy(db)
        }
    }

    @Test
    fun rollsBackDdlAndVersionAfterFaultThenRetries() {
        open(realityTen()).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db) {
                    assertEquals(11L, db.scalar("PRAGMA user_version"))
                    assertTrue(db.hasStock())
                    error("fault before COMMIT")
                }
            }
            assertTenNoStock(db)
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            assertEquals(11L, db.scalar("PRAGMA user_version"))
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
        }
    }

    @Test
    fun rejectsRealityNineSource() {
        open(realityNine()).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            }
            assertEquals(9L, db.scalar("PRAGMA user_version"))
            assertFalse(db.hasStock())
        }
    }

    @Test
    fun rejectsIncompleteTenSourceWithoutCreatingStock() {
        open(realityTen()).use { db ->
            db.execSQL("DROP TABLE household_need_state")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            }
            assertEquals(10L, db.scalar("PRAGMA user_version"))
            assertFalse(db.hasStock())
        }
    }

    @Test
    fun rejectsSecondInstallWithoutChangingEleven() {
        open(realityTen()).use { db ->
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            }
            assertEquals(11L, db.scalar("PRAGMA user_version"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_stock_receipt_state"))
        }
    }

    @Test
    fun rejectsForeignKeyEnforcementDisabled() {
        open(realityTen()).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            }
            assertTenNoStock(db)
        }
    }

    @Test
    fun rejectsInheritedForeignKeyViolation() {
        open(realityTen()).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('orphan','cause',5)")
            db.execSQL("PRAGMA foreign_keys = ON")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            }
            assertTenNoStock(db)
        }
    }

    @Test
    fun rejectsInvalidReceiptRowsAndNeverFabricatesExistingReceipts() {
        open(realityTen()).use { db ->
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            val badRows = listOf(
                "('', 'i1', 'p1', 's1', 1, 'UNIT', 0, 2, 1)",
                "('r1', '', 'p1', 's1', 1, 'UNIT', 0, 2, 1)",
                "('r1', 'i1', '', 's1', 1, 'UNIT', 0, 2, 1)",
                "('r1', 'i1', 'p1', '', 1, 'UNIT', 0, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 0, 'UNIT', 0, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 2147483648, 'UNIT', 0, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 1, 'UNKNOWN', 0, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 1, 'UNIT', -719163, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 1, 'UNIT', 2932897, 2, 1)",
                "('r1', 'i1', 'p1', 's1', 1, 'UNIT', 0, -1, 1)",
                "('r1', 'i1', 'p1', 's1', 1, 'UNIT', 0, 2, 0)",
            )
            for (row in badRows) {
                assertThrows(SQLiteException::class.java) {
                    db.execSQL("INSERT INTO household_stock_receipt VALUES $row")
                }
            }
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
        }
    }

    @Test
    fun enforcesUniqueReceiptAndIntentKeys() {
        open(realityTen()).use { db ->
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r1','i1','p1','s1',1000,'UNIT',0,10,1)")
            assertThrows(SQLiteException::class.java) {
                db.execSQL("INSERT INTO household_stock_receipt VALUES ('r1','i2','p1','s1',1000,'UNIT',0,11,2)")
            }
            assertThrows(SQLiteException::class.java) {
                db.execSQL("INSERT INTO household_stock_receipt VALUES ('r2','i1','p1','s1',1000,'UNIT',0,11,2)")
            }
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
            assertHealthy(db)
        }
    }

    @Test
    fun financeNineActiveOpenerRejectsStagedEleven() {
        val stage = realityTen()
        open(stage).use { RealityHousehold11ReceiptSchema.installOnStagedTenConnection(it) }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(
                fileName = stage.toString(),
                materialWriteGate = SingleProcessMaterialWriteGate(),
                planningStage8 = true,
                financeActive9 = true,
            ).use { }
        }
        open(stage).use { assertEquals(11L, it.scalar("PRAGMA user_version")) }
    }

    private fun realityNine(name: String = "source-nine.db"): Path {
        val file = root.resolve(name)
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a1a-source", "a".repeat(64), 1L)
            assertEquals(0L, db.scalar("PRAGMA wal_checkpoint(TRUNCATE)"))
        }
        return file
    }

    private fun realityTen(name: String = "source-ten.db"): Path {
        val file = realityNine(name)
        open(file).use { db ->
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
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

    private fun SQLiteConnection.hasStock() =
        scalar("SELECT count(*) FROM sqlite_schema WHERE type='table' AND name LIKE 'household_stock_%'") > 0L

    private fun assertTenNoStock(db: SQLiteConnection) {
        assertEquals(10L, db.scalar("PRAGMA user_version"))
        assertFalse(db.hasStock())
    }

    private fun assertHealthy(db: SQLiteConnection) {
        db.prepare("PRAGMA integrity_check").use {
            assertTrue(it.step())
            assertEquals("ok", it.getText(0))
        }
        db.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
    }
}
