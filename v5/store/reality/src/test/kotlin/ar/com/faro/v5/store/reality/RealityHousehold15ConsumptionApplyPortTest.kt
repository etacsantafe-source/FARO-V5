package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.household.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Isolated SQL15 DEV. Never actual stock or a shipping mutation. */
class RealityHousehold15ConsumptionApplyPortTest {
    @TempDir lateinit var root: Path
    private val generation = HouseholdReviewGeneration("active15", 4L)

    private fun fixture(): Path {
        val file = root.resolve("consume15.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','cause',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "a2c1b", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase','item',7000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('unreceived','item',2000,'UNIT','human',1,'MANUAL_EXPLICIT',2)")
            db.execSQL("INSERT INTO household_stock_receipt VALUES " +
                "('receipt','receive-intent','purchase','item',7000,'UNIT',0,20,1)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision=1")
            RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db) {}
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step() && it.getLong(0) == 0L)
            }
        }
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("INSERT INTO household_stock_reservation_event VALUES " +
                "('reserve','reserve-intent','RESERVE','reserve','item','UNIT',2000,'human',30,2)")
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step() && it.getLong(0) == 0L)
            }
        }
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {}
        return file
    }

    private fun store(file: Path, write: Boolean = true,
        gate: MaterialWriteGate = SingleProcessMaterialWriteGate(),
        admission: () -> Boolean = { true },
    ) = RealityStore(file.toString(), planningStage8 = true, materialWriteGate = gate,
        householdActive15DevReadOnly = admission, householdStockConsumptionWritableDev = write)

    private fun request(quantity: Long = 4000, revision: Long = 2,
        intent: String = "consume", source: String = "purchase", principal: String = "human",
    ) = HouseholdStockConsumptionDevRequest(
        HouseholdStockReceiveDevContext(principal, 7L, generation, true, true),
        "item", HouseholdNeedQuantity(quantity, HouseholdNeedUnit.UNIT), source, intent, revision)

    private fun review(quantity: Long = 4000, revision: Long = 2,
        intent: String = "consume", source: String = "purchase", principal: String = "human",
    ) = HouseholdStockConsumptionReview(request(quantity, revision, intent, source, principal))

    @Test fun atomicConsumePreservesPhysicalAndHistoricalReservations() {
        val file = fixture()
        store(file).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            assertEquals(7000L, requireNotNull(port.position(request())).physicalThousandths)
            reality.run(TransactionId("tx")) { scope ->
                assertTrue(port.apply(review(), 50, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = requireNotNull(port.position(request(revision = 3)))
            assertEquals(3000L, after.physicalThousandths)
            assertEquals(2000L, after.reservedThousandths)
            assertEquals(3000L, requireNotNull(after.source).remainingThousandths)
        }
        store(file).use { reality ->
            val rows = reality.snapshotActiveHousehold15RowsForTrustedHost()
            assertEquals(1, rows.consumptions.size)
            assertEquals("purchase", rows.consumptions.single().sourceRef)
            assertEquals(1, rows.historical.reservations.size)
            assertEquals(1, rows.historical.historical.historical.receipts.receipts.size)
        }
    }

    @Test fun oversubscriptionStaleRevisionUnreceivedOriginAndWrongPrincipalDenied() {
        store(fixture()).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("reject")) { scope ->
                assertFalse(port.apply(review(quantity = 6000), 50, scope))
                assertFalse(port.apply(review(revision = 1), 50, scope))
                assertFalse(port.apply(review(source = "unreceived"), 50, scope))
                assertFalse(port.apply(review(source = "unknown"), 50, scope))
                assertFalse(port.apply(review(principal = "outsider"), 50, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertTrue(reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.isEmpty())
        }
    }

    @Test fun duplicateAndCrossLedgerIdCannotProduceSecondEvent() {
        store(fixture()).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("first")) { scope ->
                assertTrue(port.apply(review(quantity = 1000), 50, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("again")) { scope ->
                assertFalse(port.apply(review(1000, 3), 60, scope))
                assertFalse(port.apply(review(1000, 3, "reserve"), 60, scope))
                assertFalse(port.apply(review(1000, 3, "receive-intent"), 60, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun rollbackCrashAndForeignScopeLeaveNoConsumption() {
        store(fixture()).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("rollback")) { scope ->
                assertTrue(port.apply(review(), 50, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("crash")) { scope ->
                    assertTrue(port.apply(review(), 50, scope))
                    error("crash before commit")
                }
            }
            val foreign = object : RealityTransactionScope {
                override val transactionId = TransactionId("foreign")
            }
            assertThrows(IllegalStateException::class.java) { port.apply(review(), 50, foreign) }
            assertTrue(reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.isEmpty())
        }
    }

    @Test fun readOnlyDefaultAndLegacyWriterAreDenied() {
        val file = fixture()
        store(file, write = false).use { reality ->
            assertThrows(IllegalStateException::class.java) {
                reality.household15ConsumptionForTrustedDev("human", generation)
            }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("readonly")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            assertThrows(IllegalStateException::class.java) {
                reality.household14ReservationForTrustedDev("human", generation)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(file.toString(), planningStage8 = true,
                householdStockConsumptionWritableDev = true).close()
        }
    }

    @Test fun revokedFenceAndMaterialGateBlockReadsAndWrites() {
        val file = fixture()
        val gate = SingleProcessMaterialWriteGate()
        var allowed = true
        store(file, gate = gate, admission = { allowed }).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            assertNotNull(port.position(request()))
            allowed = false
            assertThrows(IllegalStateException::class.java) { port.position(request()) }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("revoked")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            allowed = true
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { port.position(request()) }
        }
    }

    @Test fun reservedConsumptionAndNegativeTimestampCannotApply() {
        assertThrows(IllegalArgumentException::class.java) {
            HouseholdStockConsumptionReview(request().copy(reservationRef = "reserve"))
        }
        store(fixture()).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("negative")) { scope ->
                assertFalse(port.apply(review(), -1, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertTrue(reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.isEmpty())
        }
    }

    @Test fun backdatedAndOveravailableFollowupCannotCommit() {
        store(fixture()).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("initial")) { scope ->
                assertTrue(port.apply(review(), 50, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("reject-later")) { scope ->
                assertFalse(port.apply(review(2000, 3, "too-big"), 60, scope))
                assertFalse(port.apply(review(1000, 3, "backdated"), 40, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }
}
