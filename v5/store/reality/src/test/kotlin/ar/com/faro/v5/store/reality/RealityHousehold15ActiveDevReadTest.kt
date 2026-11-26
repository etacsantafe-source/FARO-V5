package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.TransactionId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C1B: SQL15 ACTIVE DEV read-only must not reuse sealed immutable stage mode. */
class RealityHousehold15ActiveDevReadTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()

    private fun file(to15: Boolean = true): Path {
        val file = root.resolve("reality15.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','opening',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "a2c1b", "a".repeat(64), 100L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase','item',3000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES " +
                "('receipt','receive-intent','purchase','item',3000,'UNIT',0,20,1)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision=1")
            RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db) {}
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step() && it.getLong(0) == 0L)
            }
        }
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        if (to15) RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {}
        return file
    }

    private fun read(file: Path, admission: () -> Boolean = { true }) =
        RealityStore(file.toString(), planningStage8 = true,
            materialWriteGate = gate, householdActive15DevReadOnly = admission)

    @Test
    fun readsSql15HistoryWithoutWritingOrExposingApply() {
        val file = file()
        val original = Files.readAllBytes(file)
        read(file).use { store ->
            val rows = store.snapshotActiveHousehold15RowsForTrustedHost()
            assertEquals(1, rows.historical.historical.historical.receipts.receipts.size)
            assertEquals(1, rows.historical.historical.historical.purchases.size)
            assertTrue(rows.consumptions.isEmpty())
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("denied-sql15")) {
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                store.household14ReservationForTrustedDev(
                    "human", ar.com.faro.v5.owner.household.api.HouseholdReviewGeneration("selected", 1L))
            }
        }
        assertArrayEquals(original, Files.readAllBytes(file))
    }

    @Test
    fun readsCommittedConsumptionExclusivelyInWal() {
        val file = file()
        val original = Files.readAllBytes(file)
        BundledSQLiteDriver().open(file.toString()).use { writer ->
            writer.execSQL("PRAGMA wal_autocheckpoint = 0")
            writer.execSQL("INSERT INTO household_stock_consumption_event VALUES " +
                "('consume','consume-intent','CONSUME','consume','item','UNIT'," +
                "1000,'human','manual-use',NULL,50,51,2)")
            val wal = file.resolveSibling(file.fileName.toString() + "-wal")
            assertTrue(Files.size(wal) > 32L)
            assertArrayEquals(original, Files.readAllBytes(file))
            read(file).use { store ->
                assertEquals("consume",
                    store.snapshotActiveHousehold15RowsForTrustedHost().consumptions.single().eventId)
            }
        }
    }

    @Test
    fun revocationFencesEveryReadAndPreventsReopen() {
        val file = file()
        var granted = true
        read(file) { granted }.use { store ->
            assertTrue(store.snapshotActiveHousehold15RowsForTrustedHost().consumptions.isEmpty())
            granted = false
            assertThrows(IllegalStateException::class.java) {
                store.snapshotActiveHousehold15RowsForTrustedHost()
            }
        }
        assertThrows(IllegalStateException::class.java) { read(file) { false }.close() }
    }

    @Test
    fun wrongSchemaAndStageActiveCombinationFailClosed() {
        val sql14 = file(to15 = false)
        assertThrows(IllegalStateException::class.java) { read(sql14).close() }
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(sql14) {}
        assertThrows(IllegalStateException::class.java) {
            RealityStore(sql14.toString(), planningStage8 = true, materialWriteGate = gate,
                householdStage15ReadOnly = true, householdActive15DevReadOnly = { true }).close()
        }
    }
}
