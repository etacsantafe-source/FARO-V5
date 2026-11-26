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

class RealityHousehold15ConsumptionSchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun stagedMigrationPreservesSql14ReservationsAndOtherOwners() {
        val source = sealedFourteen()
        val bytes = Files.readAllBytes(source)
        val oldSchema = open(source).use { it.schemas().filterKeys { key -> !key.startsWith("household_stock_consumption_event") } }
        val target = Files.copy(source, root.resolve("stage15.db"))
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(target) {}
        open(target).use { db ->
            RealityHousehold15ConsumptionSchema.requireDefinitions(db)
            val newSchema = db.schemas().filterKeys { !it.startsWith("household_stock_consumption_event") }
            assertEquals(oldSchema, newSchema)
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_consumption_event"))
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_stock_reservation_event"))
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
            assertEquals(7_000L, db.scalar("SELECT quantity_thousandths FROM household_stock_compensation"))
            assertEquals(3_000L, db.scalar("SELECT (SELECT sum(quantity_thousandths) FROM household_stock_receipt) - (SELECT sum(quantity_thousandths) FROM household_stock_compensation)"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
        }
        assertArrayEquals(bytes, Files.readAllBytes(source))
    }

    @Test
    fun revokedLeaseRollsBackVersionAndSchema() {
        val file = sealedFourteen()
        val history = RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(file) {}
        var checks = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {
                check(++checks == 1) { "revoked lease before SQL15 commit" }
            }
        }
        assertEquals("revoked lease before SQL15 commit", failure.message)
        assertEquals(history, RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(file) {})
        open(file).use {
            assertEquals(14L, it.scalar("PRAGMA user_version"))
            assertEquals(0L, it.scalar("SELECT count(*) FROM sqlite_schema WHERE tbl_name='household_stock_consumption_event'"))
        }
    }

    @Test
    fun refusesVersionsCorruptionAndUnsealedOrAliasedPaths() {
        val file = sealedFourteen()
        val bytes = Files.readAllBytes(file)
        val alias = Files.createSymbolicLink(root.resolve("alias15.db"), file)
        val hardLink = Files.createLink(root.resolve("hardlink15.db"), file)
        for (candidate in listOf(alias, file, hardLink)) {
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(candidate) {}
            }
        }
        Files.delete(hardLink)
        val sidecar = Files.createFile(root.resolve("reality14.db-wal"))
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {}
        }
        Files.delete(sidecar)
        assertArrayEquals(bytes, Files.readAllBytes(file))
        val corrupt = Files.writeString(root.resolve("corrupt15.db"), "invalid SQLite file")
        assertThrows(SQLiteException::class.java) {
            RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(corrupt) {}
        }
        for (version in listOf(13, 15, 16)) {
            open(file).use { it.execSQL("PRAGMA user_version=$version") }
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {}
            }
        }
    }

    @Test
    fun constraintsRejectMalformedEventsDuplicatesAndMutations() {
        open(migrated()).use { db ->
            for (bad in listOf(
                event(id = "''"), event(id = "'bad id'"), event(id = "char(0)"), event(id = "char(9)"),
                event(type = "'UNKNOWN'"), event(unit = "'PINT'"), event(quantity = "0"),
                event(quantity = "2147483648"), event(quantity = "0.5"), event(revision = "0"),
                event(source = "''"), event(reservation = "'bad id'"),
                event(occurred = "60", recorded = "50"),
            )) assertThrows(SQLiteException::class.java) { db.execSQL(bad) }
            db.execSQL(event())
            for (bad in listOf(
                event(), event(id = "'other'", key = "'consume-intent'", revision = "7"),
                event(id = "'other'", key = "'other-intent'"),
                event().replace("INSERT INTO", "INSERT OR REPLACE INTO"),
                "UPDATE household_stock_consumption_event SET quantity_thousandths=2000",
                "DELETE FROM household_stock_consumption_event",
            )) assertThrows(SQLiteException::class.java) { db.execSQL(bad) }
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_stock_consumption_event"))
        }
    }

    @Test
    fun undoRequiresMatchingOriginAndCannotOvercompensate() {
        open(migrated()).use { db ->
            val undo = event(id = "'undo'", key = "'undo-intent'", type = "'CONSUME_UNDO'",
                reference = "'consume'", quantity = "400", revision = "7")
            assertThrows(SQLiteException::class.java) { db.execSQL(undo) }
            db.execSQL(event())
            for (bad in listOf(
                event(id = "'bad1'", key = "'bad1-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", stock = "'other'", quantity = "400", revision = "7"),
                event(id = "'bad2'", key = "'bad2-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", unit = "'GRAM'", quantity = "400", revision = "7"),
                event(id = "'bad3'", key = "'bad3-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", principal = "'other'", quantity = "400", revision = "7"),
                event(id = "'bad4'", key = "'bad4-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", source = "'wrong'", quantity = "400", revision = "7"),
                event(id = "'bad5'", key = "'bad5-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", reservation = "'other'", quantity = "400", revision = "7"),
                event(id = "'bad6'", key = "'bad6-intent'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", quantity = "1001", revision = "7"),
            )) assertThrows(SQLiteException::class.java) { db.execSQL(bad) }
            db.execSQL(undo)
            val excess = event(id = "'undo2'", key = "'undo-intent2'", type = "'CONSUME_UNDO'",
                reference = "'consume'", quantity = "601", revision = "8")
            assertThrows(SQLiteException::class.java) { db.execSQL(excess) }
            db.execSQL(excess.replace(",601,", ",600,"))
            assertThrows(SQLiteException::class.java) {
                db.execSQL(event(id = "'undo3'", key = "'undo-intent3'", type = "'CONSUME_UNDO'",
                    reference = "'consume'", quantity = "1", revision = "9"))
            }
            assertEquals(1000L, db.scalar("SELECT sum(quantity_thousandths) FROM household_stock_consumption_event WHERE event_type='CONSUME_UNDO'"))
        }
    }

    @Test
    fun busyCheckpointPreservesCommittedSchemaForRecovery() {
        val file = sealedFourteen()
        var reader: SQLiteConnection? = null
        var checks = 0
        try {
            val error = assertThrows(IllegalStateException::class.java) {
                RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {
                    if (++checks == 2) {
                        reader = open(file)
                        checkNotNull(reader).execSQL("BEGIN")
                        assertEquals(14L, checkNotNull(reader).scalar("PRAGMA user_version"))
                    }
                }
            }
            assertEquals("SQL15 committed but staged checkpoint is busy", error.message)
        } finally {
            reader?.close()
        }
        open(file).use {
            RealityHousehold15ConsumptionSchema.requireDefinitions(it)
            assertEquals(0L, it.scalar("SELECT count(*) FROM household_stock_consumption_event"))
            assertEquals(2L, it.scalar("SELECT count(*) FROM household_stock_reservation_event"))
        }
    }

    @Test
    fun detectsAlteredSchemaOnReopenAndSupportsEmptyStageReadback() {
        val file = migrated()
        RealityHousehold15StagedFilePort.migrateOrVerifyTrustedStagedFifteen(file) {}
        open(file).use {
            RealityHousehold15ConsumptionSchema.requireDefinitions(it)
            it.execSQL("DROP TRIGGER household_stock_consumption_event_delete_guard")
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold15ConsumptionSchema.requireDefinitions(it)
            }
        }
    }

    private fun event(
        id: String = "'consume'", key: String = "'consume-intent'", type: String = "'CONSUME'",
        reference: String = id, stock: String = "'item'", unit: String = "'UNIT'",
        quantity: String = "1000", principal: String = "'human'", source: String = "'manual-act'",
        reservation: String = "NULL", occurred: String = "50", recorded: String = "60",
        revision: String = "6",
    ) = "INSERT INTO household_stock_consumption_event VALUES ($id,$key,$type,$reference,$stock," +
        "$unit,$quantity,$principal,$source,$reservation,$occurred,$recorded,$revision)"

    private fun migrated() = sealedFourteen().also {
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(it) {}
    }

    private fun sealedFourteen(): Path {
        val file = root.resolve("reality14.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','opening',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a2c0a", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('need','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES ('purchase','item',10000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r7','i7','purchase','item',7000,'UNIT',0,20,1)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES ('r3','i3','purchase','item',3000,'UNIT',0,30,2)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision=2")
            RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db) {}
            db.execSQL("UPDATE household_stock_receipt_state SET revision=3")
            db.execSQL("INSERT INTO household_stock_compensation VALUES ('undo','undo-intent','r7','purchase',7000,'UNIT','human',40,3)")
            assertEquals(0L, db.scalar("PRAGMA wal_checkpoint(TRUNCATE)"))
        }
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        open(file).use { db ->
            db.execSQL("INSERT INTO household_stock_reservation_event VALUES ('reserve','reserve-intent','RESERVE','reserve','item','UNIT',1000,'human',50,4)")
            db.execSQL("INSERT INTO household_stock_reservation_event VALUES ('release','release-intent','RELEASE','reserve','item','UNIT',200,'human',60,5)")
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

    private fun SQLiteConnection.schemas(): Map<String, String> =
        prepare("SELECT name,sql FROM sqlite_schema WHERE sql IS NOT NULL ORDER BY name").use { stmt ->
            buildMap { while (stmt.step()) put(stmt.getText(0), stmt.getText(1)) }
        }
}
