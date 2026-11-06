package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N032 staged-only owner CAS; no Android host, Apply receipt or user operation. */
class RealityPlanningStageMutationT2Test {
    @TempDir lateinit var root: Path

    private fun rule(
        id: String, title: String = "Recordatorio", matter: String? = null,
    ) = ReminderRule(
        ReminderRuleId.of(id), title, "UTC",
        LocalDate.of(2026, 11, 6), LocalTime.of(9, 0),
        ReminderRecurrence.ONCE, true, matter, 123L,
    )

    private fun occurrence(id: String, ruleId: String) = ReminderOccurrence(
        ReminderOccurrenceId.of(id), ReminderRuleId.of(ruleId),
        10_000L, ReminderOccurrenceStatus.PENDING, null, 124L,
    )

    private fun monthly(id: String) = MonthlyPlanItem(
        MonthlyPlanItemId.of(id), "Pago planificado",
        YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30),
        MonthlyPlanItemStatus.PLANNED, 24_000L, "ARS", null, 125L,
    )

    @Test fun threeStagedInsertsPersistAcrossReopenAndPairedFbs2Read() {
        val file = root.resolve("planning-three.db").toString()
        RealityStore(file, planningStage8 = true).use { db ->
            val one = db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), 0))
            assertEquals(1L, one.reminders.revision)
            assertEquals(0L, one.monthlyPlan.revision)
            val two = db.planningStageInsert.insert(RealityPlanningStageInsert.Occurrence(
                occurrence("o1", "r1"), 1))
            assertEquals(2L, two.reminders.revision)
            val three = db.planningStageInsert.insert(RealityPlanningStageInsert.MonthlyItem(
                monthly("m1"), 0))
            assertEquals(2L, three.reminders.revision)
            assertEquals(1L, three.monthlyPlan.revision)
            assertEquals(1, three.reminders.rules.size)
            assertEquals(1, three.reminders.occurrences.size)
            assertEquals(1, three.monthlyPlan.items.size)
            assertEquals(three, db.planningBackup.snapshot())
            assertEquals(three, db.fbs2Backup.snapshot().planningDomains)
            assertEquals("ok", db.integrityCheck())
        }
        RealityStore(file, planningStage8 = true).use { db ->
            val got = db.planningBackup.snapshot()
            assertEquals(2L, got.reminders.revision)
            assertEquals(1L, got.monthlyPlan.revision)
            assertEquals("r1", got.reminders.rules.single().id.value)
            assertEquals("o1", got.reminders.occurrences.single().id.value)
            assertEquals("m1", got.monthlyPlan.items.single().id.value)
        }
    }

    @Test fun staleRevisionAndDuplicateIdsNeverOverwriteExistingRows() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), 0))
            val original = db.planningBackup.snapshot()
            assertThrows(IllegalStateException::class.java) {
                db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r2"), 0))
            }
            assertThrows(IllegalStateException::class.java) {
                db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(
                    rule("r1", title = "No sobrescribir"), 1))
            }
            assertEquals(original, db.planningBackup.snapshot())
            db.planningStageInsert.insert(
                RealityPlanningStageInsert.MonthlyItem(monthly("m1"), 0))
            val beforeDuplicate = db.planningBackup.snapshot()
            assertThrows(IllegalStateException::class.java) {
                db.planningStageInsert.insert(
                    RealityPlanningStageInsert.MonthlyItem(monthly("m1"), 1))
            }
            assertEquals(beforeDuplicate, db.planningBackup.snapshot())
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test fun invalidMatterAndOrphanOccurrenceRollbackCompletely() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val before = db.fbs2Backup.snapshot()
            assertThrows(Exception::class.java) {
                db.planningStageInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1", matter = "missing-matter"), 0))
            }
            assertEquals(before, db.fbs2Backup.snapshot())
            assertThrows(IllegalStateException::class.java) {
                db.planningStageInsert.insert(
                    RealityPlanningStageInsert.Occurrence(occurrence("o1", "r1"), 0))
            }
            assertEquals(before, db.fbs2Backup.snapshot())
            assertTrue(db.maintenance.inspect().foreignKeysHealthy)
            // Rolled-back revision really is still zero: a valid insert can now commit.
            assertEquals(1L, db.planningStageInsert.insert(
                RealityPlanningStageInsert.Rule(rule("r1"), 0)).reminders.revision)
        }
    }

    @Test fun defaultRealitySevenAndNestedApplyCannotCallStagedWriter() {
        RealityStore(":memory:").use { db ->
            assertEquals(7, db.schemaVersion())
            assertThrows(IllegalStateException::class.java) {
                db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), 0))
            }
        }
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val before = db.planningBackup.snapshot()
            db.run(TransactionId("n032-nested-apply")) { _ ->
                assertThrows(IllegalStateException::class.java) {
                    db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), 0))
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(before, db.planningBackup.snapshot())
            assertThrows(IllegalArgumentException::class.java) {
                db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), -1))
            }
            assertThrows(IllegalArgumentException::class.java) {
                db.planningStageInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r1"), Long.MAX_VALUE))
            }
            assertEquals(before, db.planningBackup.snapshot())
        }
    }

    @Test fun approachingFourMiBPortableBudgetRollsBackInsertedRowAndRevision() {
        val file = root.resolve("budget.db").toString()
        val largeTitle = "漢".repeat(160)
        // 40-byte header + 7781 * 539 bytes = just below 4 MiB.
        val rules = (1..7_781).map { n ->
            rule("rule-" + n.toString().padStart(5, '0'), largeTitle)
        }
        val initial = RealityPlanningBackupSnapshot(
            8, ReminderSnapshot(7_781L, rules, emptyList()),
            MonthlyPlanSnapshot(0L, emptyList()),
        )
        assertFalse(initial.isFresh)
        RealityStore(file, planningStage8 = true).use { db ->
            assertEquals(initial, db.planningBackupRestore.restoreOrVerify(initial))
            val original = db.fbs2Backup.snapshot()
            assertThrows(IllegalArgumentException::class.java) {
                db.planningStageInsert.insert(RealityPlanningStageInsert.Rule(
                    rule("rule-99999", largeTitle), 7_781L,
                ))
            }
            assertEquals(original, db.fbs2Backup.snapshot())
            assertEquals("ok", db.integrityCheck())
        }
        RealityStore(file, planningStage8 = true).use { db ->
            assertEquals(7_781L, db.planningBackup.snapshot().reminders.revision)
            assertEquals(7_781, db.planningBackup.snapshot().reminders.rules.size)
        }
    }

    @Test fun twoConnectionsUseRevisionCasInsideBeginImmediate() {
        val file = root.resolve("two-handles.db").toString()
        RealityStore(file, planningStage8 = true).use { first ->
            RealityStore(file, planningStage8 = true).use { second ->
                first.planningStageInsert.insert(RealityPlanningStageInsert.Rule(rule("r1"), 0))
                assertThrows(IllegalStateException::class.java) {
                    second.planningStageInsert.insert(
                        RealityPlanningStageInsert.Rule(rule("r2"), 0))
                }
                val after = second.planningStageInsert.insert(
                    RealityPlanningStageInsert.Rule(rule("r2"), 1))
                assertEquals(2L, after.reminders.revision)
                assertEquals(listOf("r1", "r2"),
                    first.planningBackup.snapshot().reminders.rules.map { it.id.value })
            }
        }
    }
}
