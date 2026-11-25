package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold12PurchaseSchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun migratesCopyAndPreservesLegacyReceiptWithoutInferringPurchase() {
        val source = realityEleven()
        val bytes = Files.readAllBytes(source)
        val staged = Files.copy(source, root.resolve("staged.db"))
        open(staged).use { db ->
            val receipts = RealityHousehold11ReceiptRowsPort.readRows(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            assertEquals(12L, db.scalar("PRAGMA user_version"))
            assertEquals(receipts, RealityHousehold11ReceiptRowsPort.readRows(db))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_confirmed_purchase"))
        }
        assertArrayEquals(bytes, Files.readAllBytes(source))
    }

    @Test
    fun rollsBackTableAndVersionOnFailure() {
        open(realityEleven()).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db) {
                    assertEquals(12L, db.scalar("PRAGMA user_version"))
                    error("fault before commit")
                }
            }
            assertElevenWithoutPurchase(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            assertEquals(12L, db.scalar("PRAGMA user_version"))
        }
    }

    @Test
    fun rejectsWrongVersionAndSecondInstall() {
        open(realityEleven()).use { db ->
            db.execSQL("PRAGMA user_version = 10")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            }
            assertEquals(10L, db.scalar("PRAGMA user_version"))
            db.execSQL("PRAGMA user_version = 11")
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            }
            assertEquals(12L, db.scalar("PRAGMA user_version"))
        }
    }

    @Test
    fun rejectsBrokenReceiptSchemaAndInheritedForeignKeyViolations() {
        open(realityEleven()).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('orphan','cause',5)")
            db.execSQL("PRAGMA foreign_keys = ON")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            }
            assertElevenWithoutPurchase(db)
            db.execSQL("DELETE FROM finance_account_opening WHERE account_id = 'orphan'")
            db.execSQL("ALTER TABLE household_stock_receipt ADD COLUMN invalid_column TEXT")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            }
            assertElevenWithoutPurchase(db)
        }
    }

    @Test
    fun rejectsInvalidAndDuplicatePurchases() {
        open(realityEleven()).use { db ->
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            val invalid = listOf(
                "'', 'item', 1000, 'UNIT', 'principal', 1, 'MANUAL_EXPLICIT', 1",
                "'p', 'item', 0, 'UNIT', 'principal', 1, 'MANUAL_EXPLICIT', 1",
                "'p', 'item', 2147483648, 'UNIT', 'principal', 1, 'MANUAL_EXPLICIT', 1",
                "'p', 'item', 1000, 'UNKNOWN', 'principal', 1, 'MANUAL_EXPLICIT', 1",
                "'p', 'item', 1000, 'UNIT', 'principal', 1, 'CART', 1",
                "'p', 'item', 1000, 'UNIT', 'principal', 1, 'MANUAL_EXPLICIT', 0",
            )
            for (fields in invalid) {
                assertThrows(SQLiteException::class.java) {
                    db.execSQL("INSERT INTO household_confirmed_purchase VALUES ($fields)")
                }
            }
            db.execSQL(validInsert)
            assertThrows(SQLiteException::class.java) { db.execSQL(validInsert) }
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_confirmed_purchase"))
        }
    }

    @Test
    fun reopensConfirmedPurchaseFixture() {
        val file = realityEleven()
        open(file).use { db ->
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL(validInsert)
        }
        open(file).use { db ->
            assertEquals(12L, db.scalar("PRAGMA user_version"))
            assertEquals(1000L, db.scalar(
                "SELECT quantity_thousandths FROM household_confirmed_purchase " +
                    "WHERE purchase_ref = 'known' AND confirmed_by = 'principal' AND revision = 1",
            ))
        }
    }

    private val validInsert = "INSERT INTO household_confirmed_purchase " +
        "VALUES ('known','item',1000,'UNIT','principal',1,'MANUAL_EXPLICIT',1)"

    private fun realityEleven(): Path {
        val file = root.resolve("reality11.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a1c0b0", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r1','i1','legacy','item',1000,'UNIT',0,10,1)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision = 1")
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

    private fun assertElevenWithoutPurchase(db: SQLiteConnection) {
        assertEquals(11L, db.scalar("PRAGMA user_version"))
        assertEquals(0L, db.scalar(
            "SELECT count(*) FROM sqlite_schema WHERE name = 'household_confirmed_purchase'",
        ))
    }
}
