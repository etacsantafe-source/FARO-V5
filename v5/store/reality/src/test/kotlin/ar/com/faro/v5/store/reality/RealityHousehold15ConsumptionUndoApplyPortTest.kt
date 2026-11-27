package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.household.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** SQL15 DEV-only: tests the port, not any shipping Undo host or real stock. */
class RealityHousehold15ConsumptionUndoApplyPortTest {
    @TempDir lateinit var root: Path
    private val generation = HouseholdReviewGeneration("active15", 4L)
    private val originalContext = HouseholdStockReceiveDevContext(
        "human", 7L, generation, isolatedDev = true, foreground = true,
    )
    private val sourceReview = ConversationPlanReviewContext(
        ConversationId("source-chat"), "source-consume-review", "trusted-token", 5L,
        PlanFingerprint("consume-fingerprint"),
    )
    private val sourceReceipt = ExecutionReceipt(
        ReceiptId("apply-receipt-not-consume-ref"), TransactionId("consume-tx"),
        sourceReview.planFingerprint,
    )
    private val contract = OperationContractDescriptor(
        OperationId("undo.execute"), CapabilityId("N-016"), ExecutorId("undo-coordinator"),
        ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
        ContractFingerprint("sql15-undo-fixture"), ContractExposure.BINDABLE,
    )

    private fun original() = HouseholdStockConsumptionReview(
        HouseholdStockConsumptionDevRequest(
            originalContext, "item", HouseholdNeedQuantity(4_000, HouseholdNeedUnit.UNIT),
            "purchase", "consume", 2L,
        ),
    )

    private fun fixture(): Path {
        val file = root.resolve("undo15.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO finance_account VALUES ('acct','Efectivo','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account_opening VALUES ('acct','cause',500)")
            RealityFinance9Schema.installOnStagedEightConnection(db, "a2c1e", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            RealityHousehold12PurchaseSchema.installOnStagedElevenConnection(db)
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase','item',7000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
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

    private fun consumeStore(file: Path) = RealityStore(
        file.toString(), planningStage8 = true, materialWriteGate = SingleProcessMaterialWriteGate(),
        householdActive15DevReadOnly = { true }, householdStockConsumptionWritableDev = true,
    )

    private fun undoStore(
        file: Path, write: Boolean = true,
        gate: MaterialWriteGate = SingleProcessMaterialWriteGate(),
        admission: () -> Boolean = { true },
    ) = RealityStore(
        file.toString(), planningStage8 = true, materialWriteGate = gate,
        householdActive15DevReadOnly = admission, householdStockConsumptionUndoWritableDev = write,
    )

    private fun seeded(): Path = fixture().also { file ->
        consumeStore(file).use { reality ->
            val port = reality.household15ConsumptionForTrustedDev("human", generation)
            reality.run(TransactionId("seed-consume")) { scope ->
                assertTrue(port.apply(original(), 50L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = requireNotNull(port.position(original().request.copy(ledgerRevision = 3L)))
            assertEquals(3_000L, after.physicalThousandths)
            assertEquals(2_000L, after.reservedThousandths)
        }
    }

    private fun review(
        port: RealityHousehold15ConsumptionUndoApplyPort,
        undoKey: String = "undo-intent",
        sourceOperation: String = "stock.consume",
        timestamp: Long = 70L,
    ): HouseholdStockConsumptionUndoDevDecision.ReviewOnly {
        val intent = UndoExecuteIntent(
            sourceReview, sourceReceipt, OperationId(sourceOperation),
            "consume", original().payload, timestamp,
        )
        val position = requireNotNull(port.position("consume"))
        val request = HouseholdStockConsumptionUndoDevRequest(
            originalContext.copy(hostEpoch = 9L), intent, undoKey, position.ledgerRevision,
        )
        return HouseholdStockConsumptionUndoDevDecision.ReviewOnly(
            request, contract, HouseholdStockConsumptionUndoCommittedOrigin(
                sourceReview, sourceReceipt, original(),
            ), position,
        )
    }

    @Test fun fullUndoRestoresPhysicalReservedAndDurableReadbackAfterReopen() {
        val file = seeded()
        undoStore(file).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val candidate = review(port)
            assertEquals(4_000L, candidate.position.remainingUndoableThousandths)
            reality.run(TransactionId("first-undo")) { scope ->
                assertTrue(port.apply(candidate, 80L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = requireNotNull(port.position("consume"))
            assertEquals(0L, after.remainingUndoableThousandths)
            assertFalse(after.newUndoIntentAvailable)
            val rows = reality.snapshotActiveHousehold15RowsForTrustedHost()
            assertEquals(listOf(
                RealityHousehold15ConsumptionType.CONSUME,
                RealityHousehold15ConsumptionType.CONSUME_UNDO,
            ), rows.consumptions.map { it.type })
            assertEquals(1, rows.historical.reservations.size)
            assertEquals(1, rows.historical.historical.historical.receipts.receipts.size)
        }
        consumeStore(file).use { reality ->
            val snapshot = requireNotNull(reality.household15ConsumptionForTrustedDev(
                "human", generation,
            ).position(original().request.copy(ledgerRevision = 4L)))
            assertEquals(7_000L, snapshot.physicalThousandths)
            assertEquals(2_000L, snapshot.reservedThousandths)
            assertEquals(7_000L, requireNotNull(snapshot.source).remainingThousandths)
        }
    }

    @Test fun duplicateAndDifferentIntentCannotUndoSameConsumptionTwice() {
        undoStore(seeded()).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val first = review(port)
            reality.run(TransactionId("first")) { scope ->
                assertTrue(port.apply(first, 80L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.run(TransactionId("second")) { scope ->
                assertFalse(port.apply(first, 90L, scope))
                assertFalse(port.apply(first.copy(
                    request = first.request.copy(undoIntentKey = "second-undo"),
                ), 90L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(2, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun staleRevisionAndMismatchedOriginalProvenanceAreDenied() {
        undoStore(seeded()).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val original = review(port)
            val badOrigin = original.copy(origin = original.origin.copy(
                stockReview = HouseholdStockConsumptionReview(
                    original.origin.stockReview.request.copy(sourceRef = "foreign-purchase"),
                ),
            ))
            val badRevision = original.copy(request = original.request.copy(
                expectedLedgerRevision = 1L,
            ))
            val badQuantity = original.copy(position = original.position.copy(
                remainingUndoableThousandths = 1_000L,
            ))
            reality.run(TransactionId("rejected")) { scope ->
                assertFalse(port.apply(badOrigin, 80L, scope))
                assertFalse(port.apply(badRevision, 80L, scope))
                assertFalse(port.apply(badQuantity, 80L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun invalidOperationContextAndTimeDoNotWrite() {
        undoStore(seeded()).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val candidate = review(port)
            reality.run(TransactionId("bad-context")) { scope ->
                assertFalse(port.apply(candidate.copy(request = candidate.request.copy(
                    context = candidate.request.context.copy(hostEpoch = 0L),
                )), 80L, scope))
                assertFalse(port.apply(candidate.copy(request = candidate.request.copy(
                    context = candidate.request.context.copy(principal = "outsider"),
                )), 80L, scope))
                assertFalse(port.apply(candidate.copy(request = candidate.request.copy(
                    intent = candidate.request.intent.copy(sourceOperationId = OperationId("stock.receive")),
                )), 80L, scope))
                assertFalse(port.apply(candidate, 40L, scope))
                assertFalse(port.apply(candidate.copy(request = candidate.request.copy(
                    intent = candidate.request.intent.copy(cancelledAtEpochMillis = 10L),
                )), 80L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun collidingIdsAndInvalidConsumptionRefDoNotWrite() {
        undoStore(seeded()).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val candidate = review(port)
            reality.run(TransactionId("collisions")) { scope ->
                for (key in listOf("consume", "receive-intent", "reserve-intent")) {
                    assertFalse(port.apply(candidate.copy(request =
                        candidate.request.copy(undoIntentKey = key)), 80L, scope))
                }
                assertFalse(port.apply(candidate.copy(request = candidate.request.copy(
                    intent = candidate.request.intent.copy(targetObjectId = "missing"),
                )), 80L, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertNull(port.position("missing"))
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun rollbackAndCrashBeforeCommitProduceNoPartialUndo() {
        undoStore(seeded()).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val candidate = review(port)
            reality.run(TransactionId("rollback")) { scope ->
                assertTrue(port.apply(candidate, 80L, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("crash")) { scope ->
                    assertTrue(port.apply(candidate, 80L, scope))
                    error("simulated failure before COMMIT")
                }
            }
            assertEquals(1, reality.snapshotActiveHousehold15RowsForTrustedHost().consumptions.size)
        }
    }

    @Test fun foreignScopeAndRevokedFenceCannotApply() {
        val file = seeded()
        var selected = true
        val gate = SingleProcessMaterialWriteGate()
        undoStore(file, gate = gate, admission = { selected }).use { reality ->
            val port = reality.household15ConsumptionUndoForTrustedDev("human", generation)
            val candidate = review(port)
            val foreign = object : RealityTransactionScope {
                override val transactionId = TransactionId("foreign")
            }
            assertThrows(IllegalStateException::class.java) {
                port.apply(candidate, 80L, foreign)
            }
            selected = false
            assertThrows(IllegalStateException::class.java) { port.position("consume") }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("revoked")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
            selected = true
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { port.position("consume") }
        }
    }

    @Test fun undoAndConsumptionFlagsCannotInheritEachOthersWriteAuthority() {
        val file = seeded()
        undoStore(file, write = false).use { reality ->
            assertThrows(IllegalStateException::class.java) {
                reality.household15ConsumptionUndoForTrustedDev("human", generation)
            }
            assertThrows(IllegalStateException::class.java) {
                reality.run(TransactionId("readonly")) { AtomicApplyTransactionDecision.Commit(Unit) }
            }
        }
        undoStore(file).use { reality ->
            assertThrows(IllegalStateException::class.java) {
                reality.household15ConsumptionForTrustedDev("human", generation)
            }
        }
        consumeStore(file).use { reality ->
            assertThrows(IllegalStateException::class.java) {
                reality.household15ConsumptionUndoForTrustedDev("human", generation)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(file.toString(), planningStage8 = true,
                materialWriteGate = SingleProcessMaterialWriteGate(),
                householdActive15DevReadOnly = { true },
                householdStockConsumptionWritableDev = true,
                householdStockConsumptionUndoWritableDev = true).close()
        }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(file.toString(), planningStage8 = true,
                householdStockConsumptionUndoWritableDev = true).close()
        }
    }
}
