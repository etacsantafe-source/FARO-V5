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

class RealityHousehold12PurchaseRowsPortTest {
    @TempDir lateinit var root: Path

    private val port = RealityHousehold12PurchaseRowsPort
    private val empty = RealityHousehold12PurchaseReceiptRows(emptyList(), RealityHousehold11ReceiptRows(0, emptyList()))

    @Test
    fun restoresAndCapturesOnePurchaseAndTwoPartialReceiptsWithExactReplay() {
        val target = buildStage("restore")
        val source = buildStage("source")
        val snapshot = confirmed()
        assertEquals(RealityHousehold12RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedStagedTwelve(source, snapshot))
        val captured = port.readTrustedSealedStageTwelve(source)
        assertEquals(snapshot, captured)
        assertEquals(RealityHousehold12RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedStagedTwelve(target, captured))
        assertEquals(captured, port.readTrustedSealedStageTwelve(target))
        val bytes = Files.readAllBytes(target)
        assertEquals(RealityHousehold12RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedStagedTwelve(target, captured) { error("replay must make no writes") })
        assertArrayEquals(bytes, Files.readAllBytes(target))
        open(target).use { db ->
            assertEquals(500L, db.scalar("SELECT amount_minor FROM finance_account_opening"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM household_need"))
        }
    }

    @Test
    fun migratedLegacyReceiptsDoNotCreatePurchases() {
        val stage = buildStage("legacy", includeLegacy = true)
        val expected = RealityHousehold12PurchaseReceiptRows(
            emptyList(), RealityHousehold11ReceiptRows(1, listOf(receipt(1, 1_000).copy(purchaseRef = "legacy"))),
        )
        assertEquals(expected, port.readTrustedSealedStageTwelve(stage))
        assertEquals(RealityHousehold12RowsRestoreDisposition.ALREADY_IDENTICAL,
            port.restoreOrVerifyTrustedStagedTwelve(stage, expected))
        val other = buildStage("legacy-replay")
        assertEquals(RealityHousehold12RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedStagedTwelve(other, expected))
        assertEquals(expected, port.readTrustedSealedStageTwelve(other))
    }

    @Test
    fun refusesDivergentReceiptsOrPurchaseWithoutMutation() {
        val target = buildStage("divergent")
        val original = confirmed()
        port.restoreOrVerifyTrustedStagedTwelve(target, original)
        val bytes = Files.readAllBytes(target)
        assertThrows(IllegalStateException::class.java) { port.restoreOrVerifyTrustedStagedTwelve(target, empty) }
        val different = original.copy(purchases = listOf(purchase().copy(confirmedBy = "other-principal")))
        assertThrows(IllegalStateException::class.java) { port.restoreOrVerifyTrustedStagedTwelve(target, different) }
        assertEquals(original, port.readTrustedSealedStageTwelve(target))
        assertArrayEquals(bytes, Files.readAllBytes(target))
    }

    @Test
    fun interruptionBeforeCommitLeavesPurchasesReceiptsAndRevisionEmpty() {
        val target = buildStage("rollback")
        assertThrows(IllegalStateException::class.java) {
            port.restoreOrVerifyTrustedStagedTwelve(target, confirmed()) {
                error("injected interruption after the readback")
            }
        }
        assertEquals(empty, port.readTrustedSealedStageTwelve(target))
        assertEquals(RealityHousehold12RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedStagedTwelve(target, confirmed()))
    }

    @Test
    fun invalidSnapshotsFailBeforeTheyCanChangeTheStage() {
        val target = buildStage("invalid")
        val p = purchase()
        val valid = confirmed()
        val badPurchases = listOf(
            p.copy(quantityThousandths = 1), p.copy(quantityThousandths = -1),
            p.copy(stockItemRef = "another-item"), p.copy(unit = "PACKAGE"),
            p.copy(confirmedAtMs = -1), p.copy(confirmedBy = " principal"), p.copy(provenance = "CART"),
            p.copy(revision = 0), p.copy(purchaseRef = "bad\u0000ref"),
        )
        for (purchases in badPurchases.map { listOf(it) } +
            listOf(listOf(p, p), List(8_193) { p })) {
            assertThrows(IllegalArgumentException::class.java) {
                port.restoreOrVerifyTrustedStagedTwelve(target, valid.copy(purchases = purchases))
            }
        }
        assertEquals(empty, port.readTrustedSealedStageTwelve(target))
    }

    @Test
    fun exactSchemaAndIntegrityRejectWrongVersionSpuriousTriggerAndAlteredDdl() {
        val eleven = buildStage("eleven", asEleven = true)
        assertThrows(IllegalStateException::class.java) { port.readTrustedSealedStageTwelve(eleven) }
        assertEquals(RealityHousehold11ReceiptRows(0, emptyList()),
            RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(eleven))
        val twelve = buildStage("twelve")
        assertThrows(IllegalStateException::class.java) {
            RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(twelve)
        }
        open(twelve).use { db ->
            db.execSQL("CREATE TRIGGER purchase_trigger AFTER INSERT ON household_confirmed_purchase BEGIN SELECT 1; END")
        }
        assertThrows(IllegalStateException::class.java) { port.restoreOrVerifyTrustedStagedTwelve(twelve, confirmed()) }
        open(twelve).use { db ->
            db.execSQL("DROP TRIGGER purchase_trigger")
            db.execSQL("ALTER TABLE household_confirmed_purchase ADD COLUMN unexpected TEXT")
        }
        assertThrows(IllegalStateException::class.java) { port.readTrustedSealedStageTwelve(twelve) }

        val corrupt = buildStage("corrupt")
        open(corrupt).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('orphan','cause',5)")
        }
        assertThrows(IllegalStateException::class.java) { port.readTrustedSealedStageTwelve(corrupt) }
    }

    @Test
    fun rejectsUnsafePathsAndRetainsCanonicalOrderForSupplementaryText() {
        val db = buildStage("sealed")
        val link = Files.createSymbolicLink(root.resolve("linked.db"), db)
        assertThrows(IllegalStateException::class.java) { port.readTrustedSealedStageTwelve(link) }
        val sidecar = Files.createFile(root.resolve("sealed.db-wal"))
        assertThrows(IllegalStateException::class.java) { port.restoreOrVerifyTrustedStagedTwelve(db, confirmed()) }
        Files.delete(sidecar)
        val p = purchase()
        val ordered = empty.copy(purchases = listOf(p.copy(purchaseRef = "🛒"), p.copy(purchaseRef = "\uE000")))
        assertEquals(RealityHousehold12RowsRestoreDisposition.RESTORED,
            port.restoreOrVerifyTrustedStagedTwelve(db, ordered))
        assertEquals(ordered, port.readTrustedSealedStageTwelve(db))
    }

    private fun confirmed() = RealityHousehold12PurchaseReceiptRows(
        listOf(purchase()), RealityHousehold11ReceiptRows(2, listOf(receipt(1, 7_000), receipt(2, 3_000))),
    )

    private fun purchase() = RealityHousehold12PurchaseRow(
        "confirmed", "item", 10_000, "UNIT", "principal", 12, "MANUAL_EXPLICIT", 1,
    )

    private fun receipt(number: Int, quantity: Int) = RealityHousehold11ReceiptRow(
        "r$number", "i$number", "confirmed", "item", quantity, "UNIT", 0, 10, number.toLong(),
    )

    private fun buildStage(name: String, asEleven: Boolean = false, includeLegacy: Boolean = false): Path {
        val file = root.resolve("$name.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-a1c0b2a", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.execSQL("INSERT INTO household_need VALUES ('n1','Yerba',1000,'UNIT','HIGH','OPEN',0,NULL)")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            if (includeLegacy) {
                db.execSQL("INSERT INTO household_stock_receipt VALUES ('r1','i1','legacy','item',1000,'UNIT',0,10,1)")
                db.execSQL("UPDATE household_stock_receipt_state SET revision = 1")
            }
            if (!asEleven) RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
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
}
