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

class RealityHousehold13CompensationSchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun stagedMigrationPreservesLegacyOwnersAndIsolatesSource() {
        val source = realityTwelve()
        val bytes = Files.readAllBytes(source)
        val historical = RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(source)
        val target = Files.copy(source, root.resolve("sql13.sqlite"))
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(target)
        open(target).use { db ->
            assertEquals(13L, db.scalar("PRAGMA user_version"))
            assertEquals(historical, RealityHousehold13CompensationSchema.validate(db))
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_compensation"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
        }
        assertArrayEquals(bytes, Files.readAllBytes(source))
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(target)
        }
    }

    @Test
    fun beforeCommitFailureRollsBackVersionTableAndGuards() {
        val file = realityTwelve()
        open(file).use { db ->
            val original = RealityHousehold12PurchaseRowsPort.readCurrentHouseholdRows(db, 12)
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db) {
                    assertEquals(13L, db.scalar("PRAGMA user_version"))
                    error("injected precommit interruption")
                }
            }
            assertEquals(12L, db.scalar("PRAGMA user_version"))
            assertEquals(0L, db.scalar("SELECT count(*) FROM sqlite_schema WHERE tbl_name='household_stock_compensation'"))
            assertEquals(original, RealityHousehold12PurchaseRowsPort.readCurrentHouseholdRows(db, 12))
        }
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file)
        open(file).use { db -> assertEquals(13L, db.scalar("PRAGMA user_version")) }
    }

    @Test
    fun wrongVersionAndDivergentCanonicalSchemasAreDenied() {
        open(realityTwelve()).use { db ->
            db.execSQL("PRAGMA user_version=11")
            assertThrows(IllegalStateException::class.java) { install(db) }
            assertEquals(11L, db.scalar("PRAGMA user_version"))
            db.execSQL("PRAGMA user_version=12")
            db.execSQL("CREATE TABLE household_stock_compensation(fake TEXT)")
            assertThrows(SQLiteException::class.java) { install(db) }
            assertEquals(12L, db.scalar("PRAGMA user_version"))
            db.execSQL("DROP TABLE household_stock_compensation")
            install(db)
            assertThrows(IllegalStateException::class.java) { install(db) }
            db.execSQL("DROP TRIGGER household_stock_compensation_delete_guard")
            assertThrows(IllegalStateException::class.java) { RealityHousehold13CompensationSchema.validate(db) }
        }
    }

    @Test
    fun compensationRequiresConfirmedOriginalPrincipalQuantityUnitAndRevision() {
        open(realityTwelve()).use { db ->
            install(db)
            db.execSQL("UPDATE household_stock_receipt_state SET revision=4")
            val invalidRows = listOf(
                "'c1','u1','missing','p1',7000,'UNIT','human',40,4",
                "'c1','u1','legacy-receipt','legacy',1000,'UNIT','human',40,4",
                "'c1','u1','r7','p1',7000,'UNIT','another',40,4",
                "'c1','u1','r7','p1',6999,'UNIT','human',40,4",
                "'c1','u1','r7','p1',7000,'GRAM','human',40,4",
                "'c1','u1','r7','p1',7000,'UNIT','human',1,4",
                "'c1','u1','r7','p1',7000,'UNIT','human',40,3",
                "'c1','u1','r7','p1',7000,'UNIT','human',40,5",
            )
            for (row in invalidRows) {
                assertThrows(SQLiteException::class.java) { db.execSQL("INSERT INTO household_stock_compensation VALUES ($row)") }
            }
            db.execSQL("INSERT INTO household_stock_compensation VALUES ($valid)")
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_stock_compensation"))
        }
    }

    @Test
    fun eventRejectsDuplicatedUndoConflictingIntentReplacementUpdateAndDelete() {
        val file = realityTwelve()
        open(file).use { db ->
            install(db)
            db.execSQL("UPDATE household_stock_receipt_state SET revision=5")
            db.execSQL("INSERT INTO household_stock_compensation VALUES ($valid)")
            for (sql in listOf(
                "INSERT INTO household_stock_compensation VALUES ('c2','u2','r7','p1',7000,'UNIT','human',45,5)",
                "INSERT INTO household_stock_compensation VALUES ('c2','u1','r3','p1',3000,'UNIT','human',45,5)",
                "INSERT OR REPLACE INTO household_stock_compensation VALUES ('c1','u2','r3','p1',3000,'UNIT','human',45,5)",
                "UPDATE household_stock_compensation SET principal='another'",
                "DELETE FROM household_stock_compensation",
            )) {
                assertThrows(SQLiteException::class.java) { db.execSQL(sql) }
            }
        }
        open(file).use { db ->
            val history = RealityHousehold13CompensationSchema.validate(db)
            assertEquals(3, history.receipts.receipts.size)
            assertEquals(5L, history.receipts.revision)
            assertEquals(7_000L, db.scalar("SELECT quantity_thousandths FROM household_stock_compensation"))
        }
    }

    @Test
    fun verificationDetectsCorruptedOriginalLink() {
        open(realityTwelve()).use { db ->
            install(db)
            db.execSQL("UPDATE household_stock_receipt_state SET revision=4")
            db.execSQL("INSERT INTO household_stock_compensation VALUES ($valid)")
            db.execSQL("DELETE FROM household_stock_receipt WHERE receipt_id='r7'")
            assertThrows(IllegalStateException::class.java) { RealityHousehold13CompensationSchema.validate(db) }
        }
    }

    private fun install(db: SQLiteConnection) = RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db) {}

    private fun realityTwelve(): Path {
        val file = root.resolve("reality12.sqlite")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a1c2a", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES ('p1','item',10000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('legacy-receipt','legacy-intent','legacy','item',1000,'UNIT',0,10,1)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r7','i7','p1','item',7000,'UNIT',0,20,2)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r3','i3','p1','item',3000,'UNIT',0,30,3)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision=3")
            assertEquals(0L, db.scalar("PRAGMA wal_checkpoint(TRUNCATE)"))
        }
        return file
    }

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }

    private companion object {
        const val valid = "'c1','u1','r7','p1',7000,'UNIT','human',40,4"
    }
}
