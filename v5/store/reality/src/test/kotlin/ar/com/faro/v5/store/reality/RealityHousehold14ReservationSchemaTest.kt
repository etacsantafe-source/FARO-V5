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

class RealityHousehold14ReservationSchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun stagedMigrationPreservesStockAndOtherOwners() {
        val source = sealedThirteen()
        val sourceBytes = Files.readAllBytes(source)
        val target = Files.copy(source, root.resolve("stage14.db"))
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(target) {}
        open(target).use { db ->
            RealityHousehold14ReservationSchema.requireDefinitions(db)
            assertEquals(0L, db.scalar("SELECT count(*) FROM household_stock_reservation_event"))
            assertEquals(10_000L, db.scalar("SELECT quantity_thousandths FROM household_confirmed_purchase"))
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_stock_receipt"))
            assertEquals(7_000L, db.scalar("SELECT quantity_thousandths FROM household_stock_compensation"))
            assertEquals(3_000L, db.scalar("""
                SELECT (SELECT sum(quantity_thousandths) FROM household_stock_receipt)
                    - (SELECT sum(quantity_thousandths) FROM household_stock_compensation)
            """.trimIndent()))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
        }
        assertArrayEquals(sourceBytes, Files.readAllBytes(source))
    }

    @Test
    fun revokedLeaseRollsBackVersionAndDdl() {
        val file = sealedThirteen()
        val history = RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(file)
        var checks = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {
                check(++checks == 1) { "revoked lease before commit" }
            }
        }
        assertEquals("revoked lease before commit", failure.message)
        assertEquals(history, RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(file))
        open(file).use {
            assertEquals(13L, it.scalar("PRAGMA user_version"))
            assertEquals(0L, it.scalar("SELECT count(*) FROM sqlite_schema WHERE tbl_name='household_stock_reservation_event'"))
        }
    }

    @Test
    fun refusesWrongVersionCorruptionAndUnsealedOrAliasedPath() {
        val file = sealedThirteen()
        val bytes = Files.readAllBytes(file)
        val alias = Files.createSymbolicLink(root.resolve("alias.db"), file)
        val hardLink = Files.createLink(root.resolve("hardlink.db"), file)
        for (candidate in listOf(alias, file, hardLink)) {
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(candidate) {}
            }
        }
        Files.delete(hardLink)
        val sidecar = Files.createFile(root.resolve("reality13.db-wal"))
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        }
        Files.delete(sidecar)
        assertArrayEquals(bytes, Files.readAllBytes(file))
        val corrupt = Files.writeString(root.resolve("corrupt.db"), "invalid SQLite file")
        assertThrows(SQLiteException::class.java) {
            RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(corrupt) {}
        }
        for (version in listOf(12, 14)) {
            open(file).use { it.execSQL("PRAGMA user_version=$version") }
            assertThrows(IllegalStateException::class.java) {
                RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
            }
            open(file).use { assertEquals(version.toLong(), it.scalar("PRAGMA user_version")) }
        }
    }

    @Test
    fun validatesTypedEventsAndRejectsMutationOrReplacement() {
        val file = migrated()
        open(file).use { db ->
            val malformed = listOf(
                event(id = "''"), event(id = "'bad id'"), event(id = "char(0)"), event(id = "char(9)"),
                event(type = "'UNKNOWN'"), event(unit = "'ML'"), event(quantity = "0"),
                event(quantity = "2147483648"), event(quantity = "0.5"), event(revision = "0"),
            )
            malformed.forEach { sql -> assertThrows(SQLiteException::class.java) { db.execSQL(sql) } }
            db.execSQL(event())
            for (sql in listOf(
                event(),
                event(id = "'other'", key = "'reserve-intent'", revision = "2"),
                event(id = "'other'", key = "'other-intent'"),
                event().replace("INSERT INTO", "INSERT OR REPLACE INTO"),
                "UPDATE household_stock_reservation_event SET quantity_thousandths=2000",
                "DELETE FROM household_stock_reservation_event",
            )) assertThrows(SQLiteException::class.java) { db.execSQL(sql) }
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_stock_reservation_event"))
        }
    }

    @Test
    fun releaseRequiresMatchingOriginAndCannotExceedRemainingQuantity() {
        open(migrated()).use { db ->
            val release = event(id = "'rel1'", key = "'release-intent'", type = "'RELEASE'",
                reference = "'reserve'", quantity = "400", revision = "2")
            assertThrows(SQLiteException::class.java) { db.execSQL(release) }
            db.execSQL(event())
            for (sql in listOf(
                release.replace("'item'", "'other'"), release.replace("'UNIT'", "'GRAM'"),
                release.replace("'human'", "'other-human'"), release.replace(",400,", ",1001,"),
            )) assertThrows(SQLiteException::class.java) { db.execSQL(sql) }
            db.execSQL(release)
            val second = event(id = "'rel2'", key = "'release-intent2'", type = "'RELEASE'",
                reference = "'reserve'", quantity = "600", revision = "3")
            assertThrows(SQLiteException::class.java) { db.execSQL(second.replace(",600,", ",601,")) }
            db.execSQL(second)
            val repeated = event(id = "'rel3'", key = "'release-intent3'", type = "'RELEASE'",
                reference = "'reserve'", quantity = "1", revision = "4")
            assertThrows(SQLiteException::class.java) { db.execSQL(repeated) }
            assertEquals(1000L, db.scalar("SELECT sum(quantity_thousandths) FROM household_stock_reservation_event WHERE event_type='RELEASE'"))
        }
    }

    @Test
    fun busyCheckpointStillLeavesAnAtomicCommittedSchema() {
        val file = sealedThirteen()
        var reader: SQLiteConnection? = null
        var checks = 0
        try {
            val failure = assertThrows(IllegalStateException::class.java) {
                RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {
                    if (++checks == 2) {
                        reader = open(file)
                        checkNotNull(reader).execSQL("BEGIN")
                        assertEquals(13L, checkNotNull(reader).scalar("PRAGMA user_version"))
                    }
                }
            }
            assertEquals("SQL14 committed but staged checkpoint is busy", failure.message)
        } finally {
            reader?.close()
        }
        open(file).use {
            RealityHousehold14ReservationSchema.requireDefinitions(it)
            assertEquals(0L, it.scalar("SELECT count(*) FROM household_stock_reservation_event"))
            assertEquals(2L, it.scalar("SELECT count(*) FROM household_stock_receipt"))
        }
    }

    @Test
    fun reopenedSchemaRejectsAlteredDefinitions() {
        open(migrated()).use {
            RealityHousehold14ReservationSchema.requireDefinitions(it)
            it.execSQL("DROP TRIGGER household_stock_reservation_event_delete_guard")
            assertThrows(IllegalStateException::class.java) { RealityHousehold14ReservationSchema.requireDefinitions(it) }
        }
    }


    private fun event(
        id: String = "'reserve'", key: String = "'reserve-intent'", type: String = "'RESERVE'",
        reference: String = id, unit: String = "'UNIT'", quantity: String = "1000", revision: String = "1",
    ) = "INSERT INTO household_stock_reservation_event VALUES ($id,$key,$type,$reference,'item',$unit,$quantity,'human',50,$revision)"

    private fun migrated(): Path = sealedThirteen().also {
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(it) {}
    }

    private fun sealedThirteen(): Path {
        val file = root.resolve("reality13.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','opening',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a2b0a", "a".repeat(64), 1L)
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
        return file
    }

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }
}
