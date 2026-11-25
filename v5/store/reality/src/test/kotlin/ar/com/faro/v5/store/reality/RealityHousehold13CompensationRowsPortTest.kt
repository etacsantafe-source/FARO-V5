package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold13CompensationRowsPortTest {
    @TempDir lateinit var root: Path

    private val port = RealityHousehold13CompensationRowsPort

    @Test
    fun compensatedReceiptAndReplacementSurviveReopen() {
        val target = stage("roundtrip")
        val complete = snapshot()
        assertEquals(RealityHousehold13RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageThirteen(target, complete))
        val read = port.captureTrustedSealedStageThirteen(target)
        assertEquals(complete, read)
        assertEquals(17_000, read.historical.receipts.receipts.sumOf { it.quantityThousandths })
        assertEquals(7_000, read.compensations.single().quantityThousandths)
        assertEquals("r01", read.compensations.single().receiptId)
        assertEquals(4L, read.historical.receipts.revision)
        open(target).use {
            assertEquals(1L, it.scalar("SELECT count(*) FROM household_need"))
            assertEquals(500L, it.scalar("SELECT amount_minor FROM finance_account_opening"))
        }
    }

    @Test
    fun committedReplayVerifiesWithoutDuplicationAndDivergenceFails() {
        val target = stage("retry")
        val complete = snapshot()
        port.restoreOrVerifyTrustedSealedStageThirteen(target, complete)
        val before = Files.readAllBytes(target)
        assertEquals(RealityHousehold13RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedSealedStageThirteen(target, complete))
        val divergent = complete.copy(compensations = listOf(complete.compensations.single().copy(principal = "another")),
            historical = complete.historical.copy(purchases =
                listOf(complete.historical.purchases.single().copy(confirmedBy = "another"))))
        assertThrows(IllegalStateException::class.java) { port.restoreOrVerifyTrustedSealedStageThirteen(target, divergent) }
        assertArrayEquals(before, Files.readAllBytes(target))
        assertEquals(complete, port.captureTrustedSealedStageThirteen(target))
    }

    @Test
    fun rollbackPreservesOriginallyEmptyPurchasesReceiptsAndCompensation() {
        val target = stage("rollback")
        val before = port.captureTrustedSealedStageThirteen(target)
        assertThrows(InterruptedException::class.java) {
            port.restoreOrVerifyTrustedSealedStageThirteen(target, snapshot()) { throw InterruptedException("synthetic") }
        }
        assertEquals(before, port.captureTrustedSealedStageThirteen(target))
        assertEquals(0L, before.historical.receipts.revision)
        assertEquals(RealityHousehold13RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageThirteen(target, snapshot()))
    }

    @Test
    fun preloadedExactV8LedgerCompletesButDivergentLedgerRefuses() {
        val complete = snapshot()
        val exact = stage("exact-v8")
        val divergent = stage("divergent-v8")
        open(exact).use {
            with(RealityHousehold11ReceiptRowsPort) { it.writeReceiptRows(complete.historical.receipts) }
        }
        open(divergent).use {
            val other = complete.historical.receipts.copy(receipts = complete.historical.receipts.receipts.take(2))
            with(RealityHousehold11ReceiptRowsPort) { it.writeReceiptRows(other) }
        }
        assertEquals(RealityHousehold13RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedSealedStageThirteen(exact, complete))
        val before = port.captureTrustedSealedStageThirteen(divergent)
        assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedSealedStageThirteen(divergent, complete)
        }
        assertEquals(before, port.captureTrustedSealedStageThirteen(divergent))
    }

    @Test
    fun rejectsExcessBeforeUndoAndInvalidLinksOrRevisions() {
        val target = stage("invalid")
        val complete = snapshot()
        val ledger = complete.historical.receipts
        val premature = ledger.receipts.map { if (it.receiptId == "r04") it.copy(revision = 3L) else it }
        val compensation = complete.compensations.single()
        val invalid = listOf(
            complete.copy(historical = complete.historical.copy(receipts = ledger.copy(receipts = premature)),
                compensations = listOf(compensation.copy(revision = 4L))),
            complete.copy(compensations = listOf(compensation.copy(unit = "GRAM"))),
            complete.copy(compensations = listOf(compensation.copy(purchaseRef = "other"))),
            complete.copy(compensations = listOf(compensation.copy(revision = 2L))),
            complete.copy(compensations = listOf(compensation.copy(receiptId = "missing"))),
            complete.copy(compensations = listOf(compensation, compensation)),
        )
        val before = port.captureTrustedSealedStageThirteen(target)
        for (rows in invalid) {
            assertThrows(IllegalArgumentException::class.java) { port.restoreOrVerifyTrustedSealedStageThirteen(target, rows) }
        }
        assertEquals(before, port.captureTrustedSealedStageThirteen(target))
    }

    @Test
    fun hardlinkSidecarAndHistoricalReaderAreRejected() {
        val target = stage("file-fences")
        val alias = root.resolve("other-alias.sqlite")
        Files.createLink(alias, target)
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageThirteen(target) }
        Files.delete(alias)
        val sidecar = target.resolveSibling(target.fileName.toString() + "-wal")
        Files.createFile(sidecar)
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageThirteen(target) }
        Files.delete(sidecar)
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(target)
        }
        val historical = stage("untouched-12", migrateToThirteen = false)
        assertThrows(IllegalStateException::class.java) { port.captureTrustedSealedStageThirteen(historical) }
        assertEquals(emptyList<RealityHousehold12PurchaseRow>(),
            RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(historical).purchases)
    }

    private fun snapshot() = RealityHousehold13CompensationRows(
        RealityHousehold12PurchaseReceiptRows(
            listOf(RealityHousehold12PurchaseRow("p1", "yerba", 10_000, "UNIT", "human", 1, "MANUAL_EXPLICIT", 1)),
            RealityHousehold11ReceiptRows(4, listOf(receipt(1, 7_000), receipt(2, 3_000), receipt(4, 7_000))),
        ),
        listOf(RealityHousehold13CompensationRow("undo-r01", "undo-intent", "r01", "p1", 7_000, "UNIT", "human", 23, 3)),
    )

    private fun receipt(revision: Long, amount: Int) =
        RealityHousehold11ReceiptRow("r0$revision", "i0$revision", "p1", "yerba", amount, "UNIT", 0, 20 + revision, revision)

    private fun stage(name: String, migrateToThirteen: Boolean = true): Path {
        val file = root.resolve("$name.sqlite")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-c2c", "a".repeat(64), 1)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            assertEquals(0L, db.scalar("PRAGMA wal_checkpoint(TRUNCATE)"))
        }
        if (migrateToThirteen) RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file)
        return file
    }

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }
}
