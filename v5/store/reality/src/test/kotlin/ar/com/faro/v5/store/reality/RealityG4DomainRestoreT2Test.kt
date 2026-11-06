package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealityG4DomainRestoreT2Test {
    private fun objective(id: String) = ObjectiveRecord(
        ObjectiveId.of(id), ObjectiveTitle.of("Objetivo " + id),
        ObjectiveDetail.of("Reserva para necesidades reales"), ObjectiveStatus.ACTIVE, 100L,
    )
    private fun matter(id: String) = MatterRecord(
        MatterId.of(id), MatterTitle.of("Asunto " + id),
        MatterDetail.of("Vencimiento pendiente"), MatterStatus.WAITING, 500L, 100L,
    )
    private fun sourceSnapshot(): RealityDomainBackupSnapshot =
        RealityStore(":memory:").use { source ->
            source.run(TransactionId("tx-g4-backup-seed")) { scope ->
                source.objectivePersistence.upsert(objective("objective-a"), scope)
                source.objectivePersistence.upsert(objective("objective-b"), scope)
                source.matterPersistence.upsert(matter("matter-a"), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            source.domainBackup.snapshot()
        }

    @Test
    fun exactDomainRestoreSurvivesCloseAndReopenWithRevisionsIntact() {
        val snapshot = sourceSnapshot()
        val db = Files.createTempDirectory("faro-g4-domain-restore").resolve("reality.db")
        RealityStore(db.toString()).use { target ->
            assertEquals(snapshot, target.domainBackupRestore.restoreOrVerify(snapshot))
            assertEquals(2L, target.objectivePersistence.read().revision)
            assertEquals(1L, target.matterPersistence.read().revision)
            assertThrows(IllegalStateException::class.java) { target.backup.snapshot() }
            assertEquals("ok", target.integrityCheck())
        }
        RealityStore(db.toString()).use { reopened ->
            assertEquals(snapshot, reopened.domainBackup.snapshot())
            assertEquals(2L, reopened.maintenance.inspect().objectiveCount)
            assertEquals(1L, reopened.maintenance.inspect().matterCount)
        }
    }

    @Test
    fun sameSourceReplayedTwiceDoesNotDuplicateRowsOrRevisions() {
        val source = sourceSnapshot()
        RealityStore(":memory:").use { target ->
            assertEquals(source, target.domainBackupRestore.restoreOrVerify(source))
            assertEquals(source, target.domainBackupRestore.restoreOrVerify(source))
            assertEquals(2L, target.objectivePersistence.read().revision)
            assertEquals(1L, target.matterPersistence.read().revision)
        }
    }

    @Test
    fun nonemptyOrDifferentTargetNeverGetsOverwritten() {
        val source = sourceSnapshot()
        RealityStore(":memory:").use { occupied ->
            occupied.run(TransactionId("tx-g4-occupied")) { scope ->
                occupied.matterPersistence.upsert(matter("matter-other"), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val before = occupied.domainBackup.snapshot()
            assertThrows(IllegalStateException::class.java) {
                occupied.domainBackupRestore.restoreOrVerify(source)
            }
            assertEquals(before, occupied.domainBackup.snapshot())
        }
        RealityStore(":memory:").use { restored ->
            restored.domainBackupRestore.restoreOrVerify(source)
            val changed = source.copy(matters = source.matters.copy(revision = 2L))
            assertThrows(IllegalStateException::class.java) {
                restored.domainBackupRestore.restoreOrVerify(changed)
            }
            assertEquals(source, restored.domainBackup.snapshot())
        }
    }

    @Test
    fun invalidSchemaOrderOrRevisionCannotWriteAnyRows() {
        val source = sourceSnapshot()
        RealityStore(":memory:").use { target ->
            val empty = target.domainBackup.snapshot()
            val invalid = listOf(
                source.copy(schemaVersion = 6),
                source.copy(objectives = source.objectives.copy(
                    objectives = source.objectives.objectives.reversed(),
                )),
                source.copy(objectives = source.objectives.copy(revision = 1L)),
            )
            for (bad in invalid) {
                assertThrows(IllegalStateException::class.java) {
                    target.domainBackupRestore.restoreOrVerify(bad)
                }
                assertEquals(empty, target.domainBackup.snapshot())
            }
            assertTrue(target.maintenance.inspect().freshForSemanticMigration)
        }
    }

    @Test
    fun restoreRejectsNestedRealityTransactionWithoutSideEffects() {
        val snapshot = sourceSnapshot()
        RealityStore(":memory:").use { target ->
            target.run(TransactionId("tx-g4-nested")) { _ ->
                assertThrows(IllegalStateException::class.java) {
                    target.domainBackupRestore.restoreOrVerify(snapshot)
                }
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(0L, target.objectivePersistence.read().revision)
            assertEquals(0L, target.matterPersistence.read().revision)
            assertTrue(target.domainBackup.snapshot().objectives.objectives.isEmpty())
            assertTrue(target.domainBackup.snapshot().matters.matters.isEmpty())
        }
    }
}
