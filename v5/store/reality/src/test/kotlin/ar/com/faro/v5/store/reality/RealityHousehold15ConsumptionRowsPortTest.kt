package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold15ConsumptionRowsPortTest {
    @TempDir lateinit var root: Path

    private val port = RealityHousehold15ConsumptionRowsPort

    @Test
    fun capturesEmptySql15PreservingSql14HistoryAndOtherOwners() {
        val file = stagedFifteen()
        val rows = port.captureTrustedSealedStageFifteen(file) {}
        assertEquals(emptyList<RealityHousehold15ConsumptionRow>(), rows.consumptions)
        assertEquals(2, rows.historical.reservations.size)
        assertEquals(2, rows.historical.historical.historical.receipts.receipts.size)
        open(file).use { db ->
            assertEquals(15L, db.scalar("PRAGMA user_version"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_stock_reservation_event"))
        }
    }

    @Test
    fun consumptionAndUndoAreRestoredIdempotentlyWithSealedReadback() {
        val file = stagedFifteen()
        val original = port.captureTrustedSealedStageFifteen(file) {}
        val expected = original.copy(consumptions = validEvents())
        assertEquals(RealityHousehold15RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageFifteen(file, expected) {})
        assertEquals(expected, port.captureTrustedSealedStageFifteen(file) {})
        assertEquals(RealityHousehold15RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedSealedStageFifteen(file, expected) {})
        open(file).use { db ->
            assertEquals(2L, db.scalar("SELECT count(*) FROM household_stock_consumption_event"))
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(3_000L, db.scalar("SELECT (SELECT sum(quantity_thousandths) FROM household_stock_receipt) - (SELECT sum(quantity_thousandths) FROM household_stock_compensation)"))
        }
    }

    @Test
    fun refusesOverconsumptionInvalidUndoReservationAndIdentity() {
        val file = stagedFifteen()
        val original = port.captureTrustedSealedStageFifteen(file) {}
        val (consume, undo) = validEvents()
        val invalid = listOf(
            listOf(consume.copy(quantityThousandths = 2_201)),
            listOf(consume.copy(reservationRef = "reserve")),
            listOf(consume.copy(unit = "GRAM")),
            listOf(consume.copy(eventId = "r7", consumptionRef = "r7")),
            listOf(consume.copy(intentKey = "reserve-intent")),
            listOf(consume.copy(revision = 4)),
            listOf(consume.copy(sourceRef = "bad ref")),
            listOf(consume.copy(quantityThousandths = 0)),
            listOf(undo), listOf(consume, undo.copy(consumptionRef = "bad")),
            listOf(consume, undo.copy(sourceRef = "different")),
            listOf(consume, undo.copy(quantityThousandths = 1_001)),
            listOf(consume, undo.copy(occurredAtMs = 0)),
            listOf(consume, undo.copy(revision = 6)),
            listOf(undo, consume),
        )
        for (events in invalid) {
            assertThrows(RuntimeException::class.java) {
                port.restoreOrVerifyTrustedSealedStageFifteen(file, original.copy(consumptions = events)) {}
            }
        }
        assertEquals(original, port.captureTrustedSealedStageFifteen(file) {})
    }

    @Test
    fun divergentHistoryAndNonEmptyLedgerCannotBeOverwritten() {
        val file = stagedFifteen()
        val original = port.captureTrustedSealedStageFifteen(file) {}
        val expected = original.copy(consumptions = validEvents())
        val alternate = original.copy(consumptions = validEvents().take(1))
        val wrongHistory = original.copy(historical = original.historical.copy(reservations = emptyList()))
        assertThrows(RuntimeException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFifteen(file, wrongHistory) {}
        }
        port.restoreOrVerifyTrustedSealedStageFifteen(file, expected) {}
        for (different in listOf(original, alternate)) {
            assertThrows(RuntimeException::class.java) {
                port.restoreOrVerifyTrustedSealedStageFifteen(file, different) {}
            }
        }
        assertEquals(expected, port.captureTrustedSealedStageFifteen(file) {})
    }

    @Test
    fun revokedStageLeaseBeforeCommitRollsBackAllRows() {
        val file = stagedFifteen()
        val original = port.captureTrustedSealedStageFifteen(file) {}
        val incoming = original.copy(consumptions = validEvents())
        var checks = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageFifteen(file, incoming) {
                check(++checks == 1) { "stage lease revoked" }
            }
        }
        assertEquals("stage lease revoked", failure.message)
        assertEquals(original, port.captureTrustedSealedStageFifteen(file) {})
        assertEquals(RealityHousehold15RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageFifteen(file, incoming) {})
    }

    @Test
    fun rejectsAlteredDefinitionsWrongVersionUnsealedAliasAndMissingLease() {
        val source = sealedFourteen()
        assertThrows(RuntimeException::class.java) {
            port.captureTrustedSealedStageFifteen(source) {}
        }
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(source) {}
        val symlink = Files.createSymbolicLink(root.resolve("alias15.db"), source)
        assertThrows(RuntimeException::class.java) {
            port.captureTrustedSealedStageFifteen(symlink) {}
        }
        assertThrows(IllegalStateException::class.java) {
            port.captureTrustedSealedStageFifteen(source) { error("no lease") }
        }
        open(source).use { it.execSQL("DROP TRIGGER household_stock_consumption_event_delete_guard") }
        assertThrows(IllegalStateException::class.java) {
            port.captureTrustedSealedStageFifteen(source) {}
        }
    }

    @Test
    fun committedButCheckpointBusyCanBeRecoveredWithoutDuplicateRows() {
        val file = stagedFifteen()
        val expected = port.captureTrustedSealedStageFifteen(file) {}
            .copy(consumptions = validEvents())
        var heldReader: SQLiteConnection? = null
        var checks = 0
        try {
            val failure = assertThrows(IllegalStateException::class.java) {
                port.restoreOrVerifyTrustedSealedStageFifteen(file, expected) {
                    if (++checks == 2) {
                        heldReader = open(file)
                        checkNotNull(heldReader).execSQL("BEGIN")
                        assertEquals(15L, checkNotNull(heldReader).scalar("PRAGMA user_version"))
                    }
                }
            }
            assertEquals("SQL15 committed but staged checkpoint is busy; recover with readback", failure.message)
        } finally {
            heldReader?.close()
        }
        assertEquals(expected, port.captureTrustedSealedStageFifteen(file) {})
        assertEquals(RealityHousehold15RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedSealedStageFifteen(file, expected) {})
    }

    private fun validEvents(): List<RealityHousehold15ConsumptionRow> {
        val consumed = RealityHousehold15ConsumptionRow(
            "consume", "consume-intent", RealityHousehold15ConsumptionType.CONSUME,
            "consume", "item", "UNIT", 1_000, "human", "explicit-use", null, 70, 70, 6,
        )
        val undo = consumed.copy(
            eventId = "consume-undo", intentKey = "consume-undo-intent",
            type = RealityHousehold15ConsumptionType.CONSUME_UNDO,
            occurredAtMs = 80, recordedAtMs = 80, revision = 7,
        )
        return listOf(consumed, undo)
    }

    private fun stagedFifteen(): Path = sealedFourteen().also {
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(it) {}
    }

    private fun sealedFourteen(): Path {
        val file = root.resolve("reality14.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','opening',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a2c0b2", "a".repeat(64), 1L)
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

    private fun open(path: Path): SQLiteConnection =
        BundledSQLiteDriver().open(path.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }
}
