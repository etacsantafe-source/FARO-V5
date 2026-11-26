package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.household.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Only isolated SQL14 DEV, never actual stock or shipping consent. */
class RealityHousehold14ReservationApplyPortTest {
    @TempDir lateinit var root: Path
    private val generation = HouseholdReviewGeneration("active14", 4L)

    private fun file(): Path {
        val file = root.resolve("reservation14.sqlite")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','cause',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "a2b1b0", "a".repeat(64), 1L)
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
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        return file
    }

    private fun store(file: Path, writable: Boolean = true,
        gate: MaterialWriteGate = SingleProcessMaterialWriteGate(),
        admission: () -> Boolean = { true },
    ) = RealityStore(file.toString(), planningStage8 = true,
        materialWriteGate = gate, householdActive14DevReadOnly = admission,
        householdStockReservationWritableDev = writable)

    private fun review(
        action: HouseholdStockReservationAction,
        quantity: Long,
        revision: Long,
        intent: String,
        reference: String? = null,
        principal: String = "human",
    ) = HouseholdStockReservationReview(HouseholdStockReservationDevRequest(
        action, HouseholdStockReceiveDevContext(principal, 7L, generation, true, true),
        "item", HouseholdNeedQuantity(quantity, HouseholdNeedUnit.UNIT),
        intent, revision, reference,
    ))

    @Test fun reserveReleaseAndReopenPreservesPhysicalAndHistory() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household14ReservationForTrustedDev("human", generation)
            val original = requireNotNull(port.position("item", "UNIT"))
            assertEquals(3000L, original.physicalNetThousandths)
            assertEquals(0L, original.reservedThousandths)
            reality.run(TransactionId("reserve-tx")) { scope ->
                assertTrue(port.apply(review(HouseholdStockReservationAction.RESERVE,
                    2000, original.ledgerRevision, "reserve"), 50L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val afterReserve = requireNotNull(port.position("item", "UNIT"))
            assertEquals(2000L, afterReserve.reservedThousandths)
            reality.run(TransactionId("release-tx")) { scope ->
                assertTrue(port.apply(review(HouseholdStockReservationAction.RELEASE,
                    1000, afterReserve.ledgerRevision, "release", "reserve"), 60L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        store(file).use { reality ->
            val p = requireNotNull(reality.household14ReservationForTrustedDev("human", generation)
                .position("item", "UNIT"))
            assertEquals(3000L, p.physicalNetThousandths)
            assertEquals(1000L, p.reservedThousandths)
            assertEquals(3L, p.ledgerRevision)
            val rows = reality.snapshotActiveHousehold14RowsForTrustedHost()
            assertEquals(2, rows.reservations.size)
            assertEquals(1, rows.historical.historical.receipts.receipts.size)
            assertTrue(rows.historical.compensations.isEmpty())
        }
    }

    @Test fun oversubscriptionAndStaleRevisionFailClosed() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household14ReservationForTrustedDev("human", generation)
            reality.run(TransactionId("first")) { scope ->
                assertTrue(port.apply(review(HouseholdStockReservationAction.RESERVE, 2000, 1, "r1"), 50, scope))
                assertFalse(port.apply(review(HouseholdStockReservationAction.RESERVE, 2000, 1, "r2"), 51, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("over")) { scope ->
                assertFalse(port.apply(review(HouseholdStockReservationAction.RESERVE, 2000, 2, "r2"), 51, scope))
                assertFalse(port.apply(review(HouseholdStockReservationAction.RESERVE, 1000, 1, "stale"), 51, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold14RowsForTrustedHost().reservations.size)
        }
    }

    @Test fun duplicateIntentWrongPrincipalAndExcessReleaseFailClosed() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household14ReservationForTrustedDev("human", generation)
            reality.run(TransactionId("r1")) { scope ->
                assertTrue(port.apply(review(HouseholdStockReservationAction.RESERVE, 2000, 1, "r1"), 50, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("rejected")) { scope ->
                assertFalse(port.apply(review(HouseholdStockReservationAction.RESERVE, 1000, 2, "r1"), 51, scope))
                assertFalse(port.apply(review(HouseholdStockReservationAction.RELEASE,
                    3000, 2, "rel-bad", "r1"), 51, scope))
                assertFalse(port.apply(review(HouseholdStockReservationAction.RELEASE,
                    1000, 2, "rel-foreign", "missing"), 51, scope))
                assertFalse(port.apply(review(HouseholdStockReservationAction.RESERVE,
                    1000, 2, "not-owner", principal = "outsider"), 51, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold14RowsForTrustedHost().reservations.size)
        }
    }

    @Test fun rollbackAndForeignScopePreserveOriginalLedger() {
        val file = file()
        store(file).use { reality ->
            val port = reality.household14ReservationForTrustedDev("human", generation)
            val command = review(HouseholdStockReservationAction.RESERVE, 2000, 1, "r1")
            reality.run(TransactionId("rollback")) { scope ->
                assertTrue(port.apply(command, 50, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(reality.snapshotActiveHousehold14RowsForTrustedHost().reservations.isEmpty())
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("crash")) { scope ->
                    assertTrue(port.apply(command, 50, scope))
                    error("failure before COMMIT")
                }
            }
            val foreign = object : RealityTransactionScope {
                override val transactionId = TransactionId("foreign")
            }
            assertThrows(IllegalStateException::class.java) { port.apply(command, 50, foreign) }
            assertTrue(reality.snapshotActiveHousehold14RowsForTrustedHost().reservations.isEmpty())
        }
    }

    @Test fun readonlyRevokeAndLegacyUndoDeniedOnSQL14() {
        val file = file()
        store(file, writable = false).use { readOnly ->
            assertThrows(IllegalStateException::class.java) {
                readOnly.household14ReservationForTrustedDev("human", generation)
            }
            assertThrows(IllegalStateException::class.java) {
                readOnly.run(TransactionId("read-only")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            assertThrows(IllegalStateException::class.java) {
                readOnly.household13CompensationForTrustedDev("human", generation)
            }
        }
        val gate = SingleProcessMaterialWriteGate()
        var allowed = true
        store(file, gate = gate, admission = { allowed }).use { reality ->
            val port = reality.household14ReservationForTrustedDev("human", generation)
            assertNotNull(port.position("item", "UNIT"))
            allowed = false
            assertThrows(IllegalStateException::class.java) { port.position("item", "UNIT") }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("revoked")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            allowed = true
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { port.position("item", "UNIT") }
        }
    }
}
