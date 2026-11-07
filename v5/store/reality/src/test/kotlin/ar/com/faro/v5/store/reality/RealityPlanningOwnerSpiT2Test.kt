package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N033B2: owner SPI and revision stamps only, no Chat ACL/Review binding. */
class RealityPlanningOwnerSpiT2Test {
    @TempDir lateinit var root: Path

    private fun rule(id: String = "r1", matter: String? = null) = ReminderRule(
        ReminderRuleId.of(id), "Vencimiento", "UTC",
        LocalDate.of(2026, 11, 30), LocalTime.of(9, 0),
        ReminderRecurrence.MONTHLY, true, matter, 100L,
    )
    private fun item(id: String = "b1") = MonthlyPlanItem(
        MonthlyPlanItemId.of(id), "Alquiler", YearMonth.of(2026, 11),
        LocalDate.of(2026, 12, 5), MonthlyPlanItemStatus.PLANNED,
        120_000L, "ARS", null, 100L,
    )
    private fun plan() = object : PreparedPlanView {
        override val applyPlan: ApplyPlan get() = error("no user ApplyPlan in N033B2")
        override val planFingerprint = PlanFingerprint("n033b2-receipt-test")
    }

    @Test fun bothOwnersCommitUnderSingleRealityReceiptAndPersist() {
        val file = root.resolve("both-owners.db").toString()
        val tx = TransactionId("n033b2-atomic")
        RealityStore(file, planningStage8 = true,
            transactionIdFactory = { tx },
            receiptIdFactory = { ReceiptId("n033b2-receipt") }).use { db ->
            val identity = db.applyAttemptIdentities.resolve(plan())
            db.run(tx) { scope ->
                assertEquals(1L, db.reminderPersistence.createRule(rule(), 0L, scope).revision)
                assertEquals(1L, db.monthlyPlanPersistence.createItem(item(), 0L, scope).revision)
                assertEquals("revision:1",
                    db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
                assertEquals("revision:1",
                    db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY)?.value)
                db.commit(scope, identity, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(tx, db.executionReceiptLookup.find(tx)?.transactionId)
            assertEquals(1, db.reminderPersistence.read().rules.size)
            assertEquals(1, db.monthlyPlanPersistence.read().items.size)
        }
        RealityStore(file, planningStage8 = true).use { db ->
            assertEquals(1L, db.reminderPersistence.read().revision)
            assertEquals(1L, db.monthlyPlanPersistence.read().revision)
            assertEquals("revision:1",
                db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
            assertEquals(tx, db.executionReceiptLookup.find(tx)?.transactionId)
        }
    }

    @Test fun wholeOwnerStampsAreIndependent() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            assertEquals("revision:0",
                db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
            assertEquals("revision:0",
                db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY)?.value)
            assertNull(db.current(ReminderOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY))
            assertNull(db.current(MonthlyPlanOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY))
            db.run(TransactionId("isolate-owner")) { scope ->
                db.reminderPersistence.createRule(rule(), 0L, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals("revision:1",
                db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
            assertEquals("revision:0",
                db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY)?.value)
        }
    }

    @Test fun schemaSevenIsUnavailableNotSyntheticEmpty() {
        RealityStore(":memory:").use { db ->
            assertNull(db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY))
            assertNull(db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY))
            assertThrows(IllegalStateException::class.java) { db.reminderPersistence.read() }
            assertThrows(IllegalStateException::class.java) { db.monthlyPlanPersistence.read() }
            db.run(TransactionId("schema7-deny")) { scope ->
                assertThrows(IllegalStateException::class.java) {
                    db.reminderPersistence.createRule(rule(), 0L, scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.monthlyPlanPersistence.createItem(item(), 0L, scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(7, db.schemaVersion())
        }
    }

    @Test fun foreignAndExpiredTransactionLeasesAreRejected() {
        RealityStore(":memory:", planningStage8 = true).use { owner ->
            RealityStore(":memory:", planningStage8 = true).use { foreign ->
                owner.run(TransactionId("owner-outer")) { _ ->
                    foreign.run(TransactionId("foreign-lease")) { scope ->
                        assertThrows(IllegalStateException::class.java) {
                            owner.reminderPersistence.createRule(rule(), 0L, scope)
                        }
                        assertThrows(IllegalStateException::class.java) {
                            owner.monthlyPlanPersistence.createItem(item(), 0L, scope)
                        }
                        AtomicApplyTransactionDecision.Rollback(Unit)
                    }
                    AtomicApplyTransactionDecision.Rollback(Unit)
                }
            }
            lateinit var expired: RealityTransactionScope
            val sameId = TransactionId("same-id-different-scope")
            owner.run(sameId) { lease ->
                expired = lease
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            owner.run(sameId) { _ ->
                assertThrows(IllegalStateException::class.java) {
                    owner.reminderPersistence.createRule(rule(), 0L, expired)
                }
                assertThrows(IllegalStateException::class.java) {
                    owner.monthlyPlanPersistence.createItem(item(), 0L, expired)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(owner.reminderPersistence.read().rules.isEmpty())
            assertTrue(owner.monthlyPlanPersistence.read().items.isEmpty())
        }
    }

    @Test fun staleRevisionAndDuplicateCannotEscapeRollback() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            db.run(TransactionId("stale-revision")) { scope ->
                db.reminderPersistence.createRule(rule(), 0L, scope)
                assertThrows(IllegalStateException::class.java) {
                    db.reminderPersistence.createRule(rule("stale"), 0L, scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.reminderPersistence.createRule(rule(), 1L, scope)
                }
                db.monthlyPlanPersistence.createItem(item(), 0L, scope)
                assertThrows(IllegalStateException::class.java) {
                    db.monthlyPlanPersistence.createItem(item("stale"), 0L, scope)
                }
                assertThrows(IllegalStateException::class.java) {
                    db.monthlyPlanPersistence.createItem(item(), 1L, scope)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(0L, db.reminderPersistence.read().revision)
            assertEquals(0L, db.monthlyPlanPersistence.read().revision)
            assertEquals("revision:0",
                db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
        }
    }

    @Test fun orphanMatterAndRollbackLeaveNoOwnerWrites() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            assertThrows(Exception::class.java) {
                db.run(TransactionId("orphan")) { scope ->
                    db.reminderPersistence.createRule(rule(matter="missing-matter"), 0L, scope)
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            db.run(TransactionId("rollback-monthly")) { scope ->
                db.monthlyPlanPersistence.createItem(item(), 0L, scope)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
            assertTrue(db.monthlyPlanPersistence.read().items.isEmpty())
            assertEquals(0L, db.reminderPersistence.read().revision)
            assertEquals(0L, db.monthlyPlanPersistence.read().revision)
            assertEquals("ok", db.integrityCheck())
        }
    }
}
