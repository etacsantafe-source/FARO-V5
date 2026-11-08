package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N033C3 internal Undo owner SQL; NO new human-approved host binding. */
class RealityPlanningUndoCancelT2Test {
    @TempDir lateinit var root: Path

    private fun rule(id: String = "undo-rule") = ReminderRule(
        ReminderRuleId.of(id), "Recordar", "UTC",
        LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
        ReminderRecurrence.ONCE, true, null, 100L,
    )
    private fun item(id: String = "undo-item", paid: Boolean = false) = MonthlyPlanItem(
        MonthlyPlanItemId.of(id), "Alquiler", YearMonth.of(2026, 11),
        LocalDate.of(2026, 11, 25),
        if (paid) MonthlyPlanItemStatus.PAID_CONFIRMED else MonthlyPlanItemStatus.PLANNED,
        15000L, "ARS", if (paid) 150L else null, 100L,
    )
    private fun plan(label: String) = object : PreparedPlanView {
        override val applyPlan: ApplyPlan get() = error("no user ApplyPlan in internal SQL test")
        override val planFingerprint = PlanFingerprint("n033c3-undo-" + label)
    }
    private fun createRule(db: RealityStore, original: ReminderRule = rule()) {
        db.run(TransactionId("seed-" + original.id.value)) { scope ->
            db.reminderPersistence.createRule(original, 0L, scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }
    private fun createItem(db: RealityStore, original: MonthlyPlanItem = item()) {
        db.run(TransactionId("seed-" + original.id.value)) { scope ->
            db.monthlyPlanPersistence.createItem(original, 0L, scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    @Test fun reminderUndoHasNewReceiptAndKeepsOriginalReceiptAcrossReopen() {
        val path = root.resolve("reminder-undo.db").toString()
        val txs = AtomicInteger()
        val receipts = AtomicInteger()
        val source = rule()
        lateinit var firstReceipt: ExecutionReceipt
        lateinit var undoReceipt: ExecutionReceipt
        RealityStore(path, planningStage8 = true,
            transactionIdFactory = { TransactionId("tx-n033c3-" + txs.incrementAndGet()) },
            receiptIdFactory = { ReceiptId("receipt-n033c3-" + receipts.incrementAndGet()) },
        ).use { db ->
            val first = db.applyAttemptIdentities.resolve(plan("create"))
            db.run(first.transactionId) { scope ->
                db.reminderPersistence.createRule(source, 0L, scope)
                firstReceipt = db.commit(scope, first, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val second = db.applyAttemptIdentities.resolve(plan("cancel"))
            assertNotEquals(first.transactionId, second.transactionId)
            db.run(second.transactionId) { scope ->
                db.reminderCancellation.cancelRule(source, 200L, 1L, scope)
                undoReceipt = db.commit(scope, second, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val state = db.planningBackup.snapshot()
            assertEquals(2L, state.reminders.revision)
            assertFalse(state.reminders.rules.single().enabled)
            assertEquals(200L, state.reminders.rules.single().updatedAtEpochMillis)
            assertEquals(firstReceipt, db.executionReceiptLookup.find(first.transactionId))
            assertEquals(undoReceipt, db.executionReceiptLookup.find(second.transactionId))
            assertEquals(0L, state.monthlyPlan.revision)
        }
        RealityStore(path, planningStage8 = true).use { db ->
            assertFalse(db.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(firstReceipt, db.executionReceiptLookup.find(firstReceipt.transactionId))
            assertEquals(undoReceipt, db.executionReceiptLookup.find(undoReceipt.transactionId))
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test fun monthlyUndoChangesOnlyPlanItemAndNeverCreatesFinancePayment() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val original = item()
            createItem(db, original)
            db.run(TransactionId("cancel-monthly")) { scope ->
                val after = db.monthlyPlanCancellation.cancelItem(original, 300L, 1L, scope)
                assertEquals(MonthlyPlanItemStatus.CANCELLED, after.items.single().status)
                assertNull(after.items.single().paymentConfirmedAtEpochMillis)
                assertEquals(15000L, after.items.single().expectedAmountMinor)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val all = db.planningBackup.snapshot()
            assertEquals(2L, all.monthlyPlan.revision)
            assertEquals(0L, all.reminders.revision)
        }
    }

    @Test fun staleRevisionAndDoubleCancelFailWithoutSecondMutation() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val original = rule()
            createRule(db, original)
            db.run(TransactionId("cancel-first")) { scope ->
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(original, 200L, 0L, scope)
                }
                db.reminderCancellation.cancelRule(original, 200L, 1L, scope)
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(original, 250L, 1L, scope)
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = db.planningBackup.snapshot()
            assertEquals(2L, after.reminders.revision)
            assertFalse(after.reminders.rules.single().enabled)
            assertEquals(200L, after.reminders.rules.single().updatedAtEpochMillis)
        }
    }

    @Test fun changedRuleOccurrenceAndPaidMonthlyCannotBeCompensated() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val original = rule()
            createRule(db, original)
            db.run(TransactionId("seed-occurrence")) { scope ->
                val occurrence = ReminderOccurrence(
                    ReminderOccurrenceId.of("due1"), original.id, 10000L,
                    ReminderOccurrenceStatus.PENDING, null, 120L,
                )
                db.planningApplyInsert.insert(
                    RealityPlanningStageInsert.Occurrence(occurrence, 1L), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val paid = item(id = "paid", paid = true)
            createItem(db, paid)
            db.run(TransactionId("unsafe-undo")) { scope ->
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(original, 300L, 2L, scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(original.copy(title="Tampered"), 300L, 2L, scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.monthlyPlanCancellation.cancelItem(paid, 300L, 1L, scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(MonthlyPlanItemStatus.PAID_CONFIRMED,
                db.planningBackup.snapshot().monthlyPlan.items.single().status)
        }
    }

    @Test fun expiredScopeAndSchemaSevenDenyCancellation() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val original = rule()
            createRule(db, original)
            lateinit var expired: RealityTransactionScope
            db.run(TransactionId("old-scope")) { scope ->
                expired = scope
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            db.run(TransactionId("new-scope")) { _ ->
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(original, 200L, 1L, expired)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.single().enabled)
        }
        RealityStore(":memory:").use { db ->
            db.run(TransactionId("legacy-seven")) { scope ->
                assertThrows(IllegalStateException::class.java) {
                    db.reminderCancellation.cancelRule(rule(), 200L, 0L, scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(7, db.schemaVersion())
        }
    }

    @Test fun failureAfterNewReceiptBeforeCommitRollsBackOnlyUndoAndRetrySucceeds() {
        val path = root.resolve("rollback-undo.db").toString()
        val source = rule()
        val tx = TransactionId("tx-new-undo")
        val receipt = ReceiptId("receipt-new-undo")
        RealityStore(path, planningStage8 = true,
            transactionIdFactory = { tx }, receiptIdFactory = { receipt },
        ).use { db ->
            createRule(db, source)
            val attempt = db.applyAttemptIdentities.resolve(plan("retry"))
            assertEquals(tx, attempt.transactionId)
            assertThrows(IllegalStateException::class.java) {
                db.run(tx) { scope ->
                    db.reminderCancellation.cancelRule(source, 400L, 1L, scope)
                    db.commit(scope, attempt, emptyList())
                    error("simulated process crash before outer SQLite COMMIT")
                }
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(1L, db.planningBackup.snapshot().reminders.revision)
            assertNull(db.executionReceiptLookup.find(tx))
            assertEquals(ApplyAttemptJournalState.PREPARED, db.applyAttemptLookup.find(tx)?.state)
        }
        RealityStore(path, planningStage8 = true,
            transactionIdFactory = { error("PREPARED identity must survive restart") },
            receiptIdFactory = { receipt },
        ).use { db ->
            val same = db.applyAttemptIdentities.resolve(plan("retry"))
            assertEquals(tx, same.transactionId)
            db.run(tx) { scope ->
                db.reminderCancellation.cancelRule(source, 400L, 1L, scope)
                db.commit(scope, same, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(2L, db.planningBackup.snapshot().reminders.revision)
            assertFalse(db.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(receipt, db.executionReceiptLookup.find(tx)?.receiptId)
        }
    }
}
