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

class RealityHousehold14ReservationRowsPortTest {
    @TempDir lateinit var root: Path

    private val port = RealityHousehold14ReservationRowsPort

    @Test
    fun restoresPartialReleaseAndPreservesHistoryAndOtherOwners() {
        val source = sealedThirteen()
        val before = Files.readAllBytes(source)
        val expected = RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(source)
        val target = Files.copy(source, root.resolve("staged14.db"))
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(target) {}
        val (reserve, release) = events()
        val incoming = RealityHousehold14ReservationRows(expected, listOf(reserve, release))

        assertEquals(RealityHousehold14RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageFourteen(target, incoming) {})
        assertEquals(incoming, port.captureTrustedSealedStageFourteen(target) {})
        assertArrayEquals(before, Files.readAllBytes(source))
        open(target).use {
            assertEquals(14L, it.scalar("PRAGMA user_version"))
            assertEquals(500L, it.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(1L, it.scalar("SELECT count(*) FROM household_need"))
            assertEquals(2L, it.scalar("SELECT count(*) FROM household_stock_receipt"))
            assertEquals(7_000L, it.scalar("SELECT quantity_thousandths FROM household_stock_compensation"))
        }
    }

    @Test
    fun retryIsIdempotentAndDivergentRowsOrHistoryAreDenied() {
        val file = stagedFourteen()
        val before = port.captureTrustedSealedStageFourteen(file) {}
        val incoming = before.copy(reservations = events())
        val wrongHistory = before.historical.copy(compensations = emptyList())
        assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming.copy(historical = wrongHistory)) {}
        }
        assertEquals(RealityHousehold14RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming) {})
        assertEquals(RealityHousehold14RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming) {})
        assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, before) {}
        }
        assertEquals(incoming, port.captureTrustedSealedStageFourteen(file) {})
    }

    @Test
    fun leaseRevocationBeforeCommitRollsBackEveryNewEvent() {
        val file = stagedFourteen()
        val before = port.captureTrustedSealedStageFourteen(file) {}
        var checks = 0
        val incoming = before.copy(reservations = events())
        val failure = assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming) {
                check(++checks == 1) { "lease revoked" }
            }
        }
        assertEquals("lease revoked", failure.message)
        assertEquals(before, port.captureTrustedSealedStageFourteen(file) {})
        port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming) {}
        checks = 0
        assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, incoming) { check(++checks == 1) }
        }
        assertEquals(incoming, port.captureTrustedSealedStageFourteen(file) {})
    }

    @Test
    fun rejectsChronologyIncompatibleLedgerAndOversizedHistory() {
        val file = stagedFourteen()
        val before = port.captureTrustedSealedStageFourteen(file) {}
        val (reserve, release) = events()
        for (rows in listOf(
            listOf(reserve.copy(quantityThousandths = 3_001)), listOf(reserve.copy(revision = 3)),
        )) {
            assertThrows(IllegalStateException::class.java) {
                port.restoreOrVerifyTrustedSealedStageFourteen(file, before.copy(reservations = rows)) {}
            }
        }
        assertThrows(SQLiteException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, before.copy(reservations =
                listOf(reserve, release.copy(principal = "other")))) {}
        }
        assertThrows(IllegalArgumentException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFourteen(file, before.copy(reservations = List(8_193) { reserve })) {}
        }
        assertEquals(before, port.captureTrustedSealedStageFourteen(file) {})
    }

    @Test
    fun rejectsSchemaChangesForeignKeysInvalidUtf8AndUnreadableFiles() {
        val file = stagedFourteen()
        open(file).use { db ->
            db.execSQL("CREATE TABLE extra (r TEXT REFERENCES household_stock_reservation_event(event_id))")
        }
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageFourteen(file) {} }
        open(file).use { db ->
            db.execSQL("DROP TABLE extra")
            db.execSQL("""
                INSERT INTO household_stock_reservation_event
                VALUES ('reserve','intent','RESERVE','reserve',CAST(x'FF' AS TEXT),'UNIT',1,'human',100,4)
            """.trimIndent())
        }
        assertThrows(RuntimeException::class.java) { port.captureTrustedSealedStageFourteen(file) {} }
        open(file).use { it.execSQL("DROP TRIGGER household_stock_reservation_event_update_guard") }
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageFourteen(file) {} }
        val corrupt = Files.writeString(root.resolve("corrupt.db"), "not a SQLite file")
        assertThrows(SQLiteException::class.java) { port.captureTrustedSealedStageFourteen(corrupt) {} }
    }

    @Test
    fun refusesWrongVersionAliasesSidecarsAndAbsentExclusiveStageAuthorization() {
        val source = sealedThirteen()
        val target = Files.copy(source, root.resolve("staged14.db"))
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(target) {}
        val original = Files.readAllBytes(source)
        val link = Files.createSymbolicLink(root.resolve("link.db"), target)
        val aliased = root.resolve(".").resolve(target.fileName)
        for (path in listOf(source, link, aliased)) {
            assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageFourteen(path) {} }
        }
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(target)
        }
        assertThrows(IllegalStateException::class.java) {
            port.captureTrustedSealedStageFourteen(target) { error("not an exclusive staged lease") }
        }
        val sidecar = Files.createFile(root.resolve("staged14.db-wal"))
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageFourteen(target) {} }
        Files.delete(sidecar)
        assertArrayEquals(original, Files.readAllBytes(source))
    }

    private fun events(): List<RealityHousehold14ReservationRow> {
        val reserve = RealityHousehold14ReservationRow(
            "reserve", "reserve-intent", RealityHousehold14ReservationType.RESERVE,
            "reserve", "item", "UNIT", 2_000, "human", 50, 4,
        )
        val release = reserve.copy(eventId = "release", intentKey = "release-intent",
            type = RealityHousehold14ReservationType.RELEASE, quantityThousandths = 1_000,
            recordedAtMs = 60, revision = 5)
        return listOf(reserve, release)
    }

    private fun stagedFourteen(): Path = sealedThirteen().also {
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(it) {}
    }

    private fun sealedThirteen(): Path {
        val file = root.resolve("reality13.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','opening',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a2b0b2", "a".repeat(64), 1L)
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
