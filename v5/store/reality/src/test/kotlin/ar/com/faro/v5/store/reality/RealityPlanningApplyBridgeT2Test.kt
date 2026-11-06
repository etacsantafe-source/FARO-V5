package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshot
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N033A: Reality8 same-transaction storage only. NOT a user/Chat Apply binding. */
class RealityPlanningApplyBridgeT2Test {
    @TempDir lateinit var root: Path

    private fun rule(id: String, title: String = "Recordatorio", matter: String? = null) =
        ReminderRule(
            ReminderRuleId.of(id), title, "UTC",
            LocalDate.of(2026, 11, 6), LocalTime.of(9, 0),
            ReminderRecurrence.ONCE, true, matter, 123L,
        )

    private fun plan(id: String) = object : PreparedPlanView {
        // This test only exercises the durable Reality attempt identity/storage seam.
        // It deliberately does not fabricate an executable user ApplyPlan.
        override val applyPlan: ApplyPlan get() = error("N033A must not run user ApplyPlan")
        override val planFingerprint: PlanFingerprint = PlanFingerprint("n033a-plan-" + id)
    }

    @Test fun planningAndReceiptCommitAtomicallyAcrossReopen() {
        val file = root.resolve("atomic-success.db").toString()
        val tx = TransactionId("tx-n033a-atomic")
        RealityStore(file, planningStage8 = true,
            transactionIdFactory = { tx },
            receiptIdFactory = { ReceiptId("receipt-n033a-atomic") }).use { db ->
            val identity = db.applyAttemptIdentities.resolve(plan("atomic"))
            assertEquals(tx, identity.transactionId)
            db.run(tx) { scope ->
                val snapshot = db.planningApplyInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1"), 0), scope)
                assertEquals(1L, snapshot.reminders.revision)
                val receipt = db.commit(scope, identity, emptyList())
                assertEquals(tx, receipt.transactionId)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1L, db.planningBackup.snapshot().reminders.revision)
            assertEquals(1, db.planningBackup.snapshot().reminders.rules.size)
            assertEquals(ApplyAttemptJournalState.COMMITTED,
                db.applyAttemptLookup.find(tx)!!.state)
            assertEquals(tx, db.executionReceiptLookup.find(tx)!!.transactionId)
        }
        RealityStore(file, planningStage8 = true).use { reopened ->
            assertEquals("r1", reopened.planningBackup.snapshot().reminders.rules.single().id.value)
            assertEquals(1L, reopened.planningBackup.snapshot().reminders.revision)
            assertEquals(tx, reopened.executionReceiptLookup.find(tx)!!.transactionId)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test fun crashAfterReceiptButBeforeSqliteCommitRollsBackBothAndCanRetry() {
        val file = root.resolve("before-commit.db").toString()
        val tx = TransactionId("tx-n033a-crash")
        RealityStore(file, planningStage8 = true,
            transactionIdFactory = { tx },
            receiptIdFactory = { ReceiptId("receipt-n033a-crash") }).use { db ->
            val identity = db.applyAttemptIdentities.resolve(plan("crash"))
            assertThrows(IllegalStateException::class.java) {
                db.run(tx) { scope ->
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("r1"), 0), scope)
                    db.commit(scope, identity, emptyList())
                    error("simulated crash before outer SQLite COMMIT")
                }
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.isEmpty())
            assertEquals(0L, db.planningBackup.snapshot().reminders.revision)
            assertNull(db.executionReceiptLookup.find(tx))
            assertEquals(ApplyAttemptJournalState.PREPARED,
                db.applyAttemptLookup.find(tx)!!.state)
        }
        RealityStore(file, planningStage8 = true,
            transactionIdFactory = { error("must reuse PREPARED attempt") },
            receiptIdFactory = { ReceiptId("receipt-n033a-crash") }).use { reopened ->
            val same = reopened.applyAttemptIdentities.resolve(plan("crash"))
            assertEquals(tx, same.transactionId)
            reopened.run(tx) { scope ->
                reopened.planningApplyInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1"), 0), scope)
                reopened.commit(scope, same, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reopened.planningBackup.snapshot().reminders.rules.size)
            assertEquals(tx, reopened.executionReceiptLookup.find(tx)!!.transactionId)
        }
    }

    @Test fun expiredScopeEvenWithSameTransactionIdCannotBeReused() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            lateinit var expired: RealityTransactionScope
            val tx = TransactionId("n033a-scope-aba")
            db.run(tx) { scope ->
                expired = scope
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            db.run(tx) { fresh ->
                assertThrows(IllegalStateException::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("r1"), 0), expired)
                }
                db.planningApplyInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1"), 0), fresh)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, db.planningBackup.snapshot().reminders.rules.size)
        }
    }

    @Test fun foreignScopeAndSchemaSevenCannotWritePlanning() {
        RealityStore(":memory:", planningStage8 = true).use { first ->
            RealityStore(":memory:", planningStage8 = true).use { other ->
                first.run(TransactionId("first-scope")) { _ ->
                    other.run(TransactionId("other-scope")) { foreign ->
                        assertThrows(IllegalStateException::class.java) {
                            first.planningApplyInsert.insert(
                                RealityPlanningStageInsert.Rule(rule("r1"), 0), foreign)
                        }
                        AtomicApplyTransactionDecision.Rollback(Unit)
                    }
                    AtomicApplyTransactionDecision.Rollback(Unit)
                }
                assertTrue(first.planningBackup.snapshot().reminders.rules.isEmpty())
            }
        }
        RealityStore(":memory:").use { legacy ->
            legacy.run(TransactionId("schema7-denied")) { scope ->
                assertThrows(IllegalStateException::class.java) {
                    legacy.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("r1"), 0), scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(7, legacy.schemaVersion())
        }
    }

    @Test fun orphanDuplicateAndStaleRevisionsCannotEscapeRollback() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            db.run(TransactionId("n033a-constraints")) { scope ->
                val missing = ReminderOccurrence(
                    ReminderOccurrenceId.of("o1"), ReminderRuleId.of("no-rule"),
                    10_000L, ReminderOccurrenceStatus.PENDING, null, 124L)
                assertThrows(IllegalStateException::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Occurrence(missing, 0), scope)
                }
                db.planningApplyInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1"), 0), scope)
                assertThrows(IllegalStateException::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("stale"), 0), scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("r1"), 1), scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.isEmpty())
            assertEquals(0L, db.planningBackup.snapshot().reminders.revision)
            db.run(TransactionId("n033a-foreign-key")) { scope ->
                assertThrows(Exception::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("bad", matter = "missing"), 0), scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.planningBackup.snapshot().reminders.rules.isEmpty())
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test fun portableFourMiBBudgetRefusesApplyAndPreservesRevision() {
        val file = root.resolve("budget-apply.db").toString()
        val title = "漢".repeat(160)
        val rules = (1..7_781).map { n ->
            rule("rule-" + n.toString().padStart(5, '0'), title)
        }
        val initial = RealityPlanningBackupSnapshot(
            8, ReminderSnapshot(7_781L, rules, emptyList()),
            MonthlyPlanSnapshot(0L, emptyList()))
        RealityStore(file, planningStage8 = true).use { db ->
            db.planningBackupRestore.restoreOrVerify(initial)
            db.run(TransactionId("n033a-budget")) { scope ->
                assertThrows(IllegalArgumentException::class.java) {
                    db.planningApplyInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("rule-99999", title), 7_781L),
                        scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(initial, db.planningBackup.snapshot())
            assertEquals("ok", db.integrityCheck())
        }
    }
}
