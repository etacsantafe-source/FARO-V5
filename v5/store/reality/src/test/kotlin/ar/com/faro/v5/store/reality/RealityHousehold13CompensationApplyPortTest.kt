package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.household.api.HouseholdReviewGeneration
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** No UI, human approval, Apply binding or stock product authorization in this test. */
class RealityHousehold13CompensationApplyPortTest {
    @TempDir lateinit var root: Path
    private val generation = HouseholdReviewGeneration("active13", 4L)

    private fun file(): Path {
        val file = root.resolve("reality13.sqlite")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO finance_account VALUES ('acct1','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct1','cause1',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "w05-g0", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file)
        val purchase = RealityHousehold12PurchaseRow(
            "p1", "yerba", 10_000, "UNIT", "human", 1L, "MANUAL_EXPLICIT", 1L,
        )
        val receipts = listOf(
            RealityHousehold11ReceiptRow("r01", "i01", "p1", "yerba", 7_000, "UNIT", 0L, 21L, 1L),
            RealityHousehold11ReceiptRow("r02", "i02", "p1", "yerba", 3_000, "UNIT", 0L, 22L, 2L),
        )
        RealityHousehold13CompensationRowsPort.restoreOrVerifyTrustedSealedStageThirteen(
            file, RealityHousehold13CompensationRows(
                RealityHousehold12PurchaseReceiptRows(
                    listOf(purchase), RealityHousehold11ReceiptRows(2L, receipts)),
                emptyList(),
            ),
        )
        return file
    }

    private fun store(
        file: Path,
        gate: MaterialWriteGate = SingleProcessMaterialWriteGate(),
        admission: () -> Boolean = { true },
        writable: Boolean = true,
    ) = RealityStore(
        file.toString(), planningStage8 = true, materialWriteGate = gate,
        householdActive13DevReadOnly = admission,
        householdStockCompensationWritableDev = writable,
    )

    private fun request(
        candidate: RealityHousehold13CompensationCandidate,
        intent: String = "undo-intent",
        revision: Long = candidate.ledgerRevision,
    ) = RealityHousehold13CompensationRequest(
        candidate.generation, candidate.receiptId, candidate.purchaseRef,
        "comp-" + candidate.receiptId, intent,
        candidate.quantityThousandths, candidate.unit, revision, 30L,
    )

    @Test fun successfulUndoPreservesOriginalReceiptsAndReopens() {
        val file = file()
        store(file).use { reality ->
            val rows = reality.household13CompensationForTrustedDev("human", generation)
            val before = requireNotNull(rows.position("r01"))
            assertEquals(10_000L, before.netReceivedThousandths)
            reality.run(TransactionId("g0-tx-1")) { scope ->
                assertTrue(rows.compensate(request(before), scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertNull(rows.position("r01"))
            assertEquals(3_000L, requireNotNull(rows.position("r02")).netReceivedThousandths)
        }
        store(file).use {
            val history = it.snapshotActiveHousehold13RowsForTrustedHost()
            assertEquals(2, history.historical.receipts.receipts.size)
            assertEquals(1, history.compensations.size)
            assertEquals("r01", history.compensations.single().receiptId)
            assertEquals(3L, history.historical.receipts.revision)
        }
    }

    @Test fun wrongPrincipalAndStaleReviewDoNotMutateLedger() {
        val file = file()
        store(file).use { reality ->
            assertNull(reality.household13CompensationForTrustedDev("other", generation).position("r01"))
            val port = reality.household13CompensationForTrustedDev("human", generation)
            val before = requireNotNull(port.position("r01"))
            reality.run(TransactionId("g0-deny")) { scope ->
                assertFalse(port.compensate(request(before, revision = 1L), scope))
                assertFalse(port.compensate(request(before).copy(purchaseRef = "p2"), scope))
                assertFalse(port.compensate(request(before).copy(quantityThousandths = 6000), scope))
                assertFalse(port.compensate(request(before).copy(unit = "GRAM"), scope))
                assertFalse(port.compensate(request(before).copy(generation =
                    HouseholdReviewGeneration("active13", 5L)), scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = reality.snapshotActiveHousehold13RowsForTrustedHost()
            assertEquals(2L, after.historical.receipts.revision)
            assertTrue(after.compensations.isEmpty())
        }
    }

    @Test fun repeatedUndoAndReusedIntentAreDenied() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household13CompensationForTrustedDev("human", generation)
            val first = requireNotNull(port.position("r01"))
            reality.run(TransactionId("g0-first")) { scope ->
                assertTrue(port.compensate(request(first), scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("g0-repeat")) { scope ->
                assertFalse(port.compensate(request(first).copy(intentKey = "different"), scope))
                val other = requireNotNull(port.position("r02"))
                assertFalse(port.compensate(request(other), scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold13RowsForTrustedHost().compensations.size)
        }
    }

    @Test fun rollbackAndExceptionRestoreOriginalLedger() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household13CompensationForTrustedDev("human", generation)
            val original = requireNotNull(port.position("r01"))
            reality.run(TransactionId("g0-rollback")) { scope ->
                assertTrue(port.compensate(request(original), scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(2L, requireNotNull(port.position("r01")).ledgerRevision)
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("g0-failed")) { scope ->
                    assertTrue(port.compensate(request(original), scope))
                    error("synthetic failure before COMMIT")
                }
            }
            val after = reality.snapshotActiveHousehold13RowsForTrustedHost()
            assertEquals(2L, after.historical.receipts.revision)
            assertTrue(after.compensations.isEmpty())
        }
    }

    @Test fun readOnlyModeAndForeignScopeCannotApplyUndo() {
        val file = file()
        store(file, writable = false).use { readOnly ->
            assertThrows(IllegalStateException::class.java) {
                readOnly.household13CompensationForTrustedDev("human", generation)
            }
            assertThrows(IllegalStateException::class.java) {
                readOnly.run(TransactionId("g0-read-only")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
        }
        store(file).use { reality ->
            val port = reality.household13CompensationForTrustedDev("human", generation)
            val candidate = requireNotNull(port.position("r01"))
            val fake = object : RealityTransactionScope {
                override val transactionId = TransactionId("g0-other-store")
            }
            assertThrows(IllegalStateException::class.java) { port.compensate(request(candidate), fake) }
            assertEquals(2L, reality.snapshotActiveHousehold13RowsForTrustedHost().historical.receipts.revision)
        }
    }

    @Test fun revokedFenceAndSuspendedGateRejectFurtherWrites() {
        val file = file()
        val gate = SingleProcessMaterialWriteGate()
        var admitted = true
        store(file, gate, admission = { admitted }).use { reality ->
            val port = reality.household13CompensationForTrustedDev("human", generation)
            assertNotNull(port.position("r01"))
            admitted = false
            assertThrows(IllegalStateException::class.java) { port.position("r01") }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("g0-revoked")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            admitted = true
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { port.position("r01") }
            gate.openMaterialWriteAdmission()
            assertEquals(2L, reality.snapshotActiveHousehold13RowsForTrustedHost().historical.receipts.revision)
        }
    }
}
