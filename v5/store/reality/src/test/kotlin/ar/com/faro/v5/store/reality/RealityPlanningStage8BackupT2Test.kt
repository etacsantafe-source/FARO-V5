package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
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

/** Isolated Reality8 native SQLite fixtures. No app/active pointer permissions. */
class RealityPlanningStage8BackupT2Test {
    @TempDir lateinit var root: Path

    private fun sample(matter: String? = null): RealityPlanningBackupSnapshot {
        val rule=ReminderRuleId.of("rule-a")
        return RealityPlanningBackupSnapshot(
            8,
            ReminderSnapshot(2L,
                listOf(ReminderRule(
                    rule, "Pago pendiente", "America/Argentina/Cordoba",
                    LocalDate.of(2026,11,30), LocalTime.of(9,15),
                    ReminderRecurrence.MONTHLY, true, matter, 100L,
                )),
                listOf(ReminderOccurrence(
                    ReminderOccurrenceId.of("occ-a"), rule, 9999L,
                    ReminderOccurrenceStatus.PENDING, null, 101L,
                )),
            ),
            MonthlyPlanSnapshot(1L,listOf(MonthlyPlanItem(
                MonthlyPlanItemId.of("plan-a"), "Alquiler",
                YearMonth.of(2026,11), LocalDate.of(2026,12,5),
                MonthlyPlanItemStatus.PLANNED, 123_000L, "ARS", null, 101L,
            ))),
        )
    }

    @Test fun defaultRealitySevenDoesNotExposePlanningOrMigrateIt() {
        val file=root.resolve("v7.db").toString()
        RealityStore(file).use { db ->
            assertEquals(7,db.schemaVersion())
            assertThrows(IllegalStateException::class.java) { db.planningBackup.snapshot() }
            assertEquals(7,db.domainBackup.snapshot().schemaVersion)
        }
        RealityStore(file).use { db -> assertEquals(7,db.schemaVersion()) }
    }

    @Test fun optInFreshEightHasTwoExplicitDurableOwners() {
        val file=root.resolve("v8-empty.db").toString()
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(8,db.schemaVersion())
            assertTrue(db.planningBackup.snapshot().isFresh)
            assertEquals(7,db.domainBackup.snapshot().schemaVersion)
            assertEquals(8,db.fbs2Backup.snapshot().planningDomains?.schemaVersion)
        }
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(8,db.schemaVersion())
            assertTrue(db.planningBackup.snapshot().isFresh)
        }
        assertThrows(IllegalStateException::class.java) { RealityStore(file).use { } }
    }

    @Test fun stageSevenToEightKeepsG4DataAndRevisions() {
        val file=root.resolve("7-to-8.db").toString()
        val before=RealityStore(file).use { db ->
            db.run(TransactionId("schema-seven-seed")) { scope ->
                db.objectivePersistence.upsert(
                    ObjectiveRecord(ObjectiveId.of("goal-1"),ObjectiveTitle.of("Ahorro"),
                        ObjectiveDetail.of("Reserva"),ObjectiveStatus.ACTIVE,100L),scope,
                )
                db.matterPersistence.upsert(
                    MatterRecord(MatterId.of("matter-1"),MatterTitle.of("Factura"),
                        MatterDetail.of("A pagar"),MatterStatus.WAITING,800L,100L),scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            db.domainBackup.snapshot()
        }
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(8,db.schemaVersion())
            assertEquals(before,db.domainBackup.snapshot())
            assertTrue(db.planningBackup.snapshot().isFresh)
            assertEquals("ok",db.integrityCheck())
        }
    }

    @Test fun oldSixToEightKeepsNullableReceiptMigrationAndRows() {
        val file=root.resolve("6-to-8.db").toString()
        RealityStore(file).use { assertEquals(7,it.schemaVersion()) }
        BundledSQLiteDriver().open(file).use { raw ->
            raw.execSQL("ALTER TABLE execution_receipt DROP COLUMN commit_recorded_at_epoch_millis")
            raw.execSQL("PRAGMA user_version = 6")
        }
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(8,db.schemaVersion())
            assertEquals("ok",db.integrityCheck())
            assertTrue(db.planningBackup.snapshot().isFresh)
            assertEquals(0L,db.maintenance.inspect().executionReceiptCount)
        }
    }

    @Test fun oldFiveToEightAddsDomainsWithoutDroppingFinanceOrApplyTables() {
        val file=root.resolve("5-to-8.db").toString()
        RealityStore(file).use { assertEquals(7,it.schemaVersion()) }
        BundledSQLiteDriver().open(file).use { raw ->
            listOf("objective_record","objective_state","matter_record","matter_state")
                .forEach { raw.execSQL("DROP TABLE $it") }
            raw.execSQL("ALTER TABLE execution_receipt DROP COLUMN commit_recorded_at_epoch_millis")
            raw.execSQL("PRAGMA user_version = 5")
        }
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(8,db.schemaVersion())
            assertEquals(0L,db.objectivePersistence.read().revision)
            assertEquals(0L,db.matterPersistence.read().revision)
            assertTrue(db.planningBackup.snapshot().isFresh)
            assertTrue(db.maintenance.inspect().foreignKeysHealthy)
        }
    }

    @Test fun planningStageRestoreIsDurableAndExactReplayWithoutDuplicateRows() {
        val file=root.resolve("planning-durable.db").toString()
        val backup=sample()
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(backup,db.planningBackupRestore.restoreOrVerify(backup))
            assertEquals(backup,db.planningBackupRestore.restoreOrVerify(backup))
            assertEquals(8,db.fbs2Backup.snapshot().planningDomains?.schemaVersion)
            assertThrows(IllegalStateException::class.java) { db.backup.snapshot() }
        }
        RealityStore(file,planningStage8=true).use { db ->
            assertEquals(backup,db.planningBackup.snapshot())
            assertFalse(db.planningBackup.snapshot().isFresh)
            assertEquals("ok",db.integrityCheck())
            assertEquals(7,db.domainBackup.snapshot().schemaVersion)
        }
    }

    @Test fun foreignInvalidOrNonFreshOwnerCannotOverwriteExistingState() {
        RealityStore(":memory:",planningStage8=true).use { db ->
            val empty=db.planningBackup.snapshot()
            assertThrows(Exception::class.java) {
                db.planningBackupRestore.restoreOrVerify(sample("missing-matter"))
            }
            assertEquals(empty,db.planningBackup.snapshot())
            val valid=sample()
            assertEquals(valid,db.planningBackupRestore.restoreOrVerify(valid))
            assertThrows(IllegalStateException::class.java) {
                db.planningBackupRestore.restoreOrVerify(
                    valid.copy(monthlyPlan=valid.monthlyPlan.copy(revision=2L))
                )
            }
            assertEquals(valid,db.planningBackup.snapshot())
        }
    }
}
