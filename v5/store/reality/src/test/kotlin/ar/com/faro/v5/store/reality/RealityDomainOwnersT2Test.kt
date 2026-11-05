package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealityDomainOwnersT2Test {
    private fun objective(at: Long = 100L, title: String = "Guardar dinero") =
        ObjectiveRecord(
            ObjectiveId.of("objective-1"),
            ObjectiveTitle.of(title),
            ObjectiveDetail.of("Fondo para imprevistos"),
            ObjectiveStatus.ACTIVE,
            at,
        )

    private fun matter(at: Long = 100L, due: Long? = 200L) =
        MatterRecord(
            MatterId.of("matter-1"),
            MatterTitle.of("Pagar factura"),
            MatterDetail.of("Vencimiento mensual"),
            MatterStatus.WAITING,
            due,
            at,
        )

    @Test
    fun freshDatabaseCreatesBothRealOwnersAndKeepsLegacyBackupWireVersion() {
        RealityStore(":memory:").use { store ->
            assertEquals(7, store.schemaVersion())
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertEquals(0L, store.matterPersistence.read().revision)
            assertEquals(emptyList<ObjectiveRecord>(), store.objectivePersistence.read().objectives)
            assertEquals(emptyList<MatterRecord>(), store.matterPersistence.read().matters)
            assertEquals(5, store.backup.snapshot().schemaVersion)
            assertEquals(7, store.domainBackup.snapshot().schemaVersion)
            assertTrue(store.maintenance.inspect().freshForSemanticMigration)
        }
    }

    @Test
    fun committedObjectiveAndMatterSurviveCloseAndReopen() {
        val db = Files.createTempDirectory("g4-durable").resolve("reality.db")
        RealityStore(db.toString()).use { source ->
            source.run(TransactionId("tx-domain-1")) { scope ->
                source.objectivePersistence.upsert(objective(), scope)
                source.matterPersistence.upsert(matter(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1L, source.objectivePersistence.read().revision)
            assertEquals(1L, source.matterPersistence.read().revision)
        }
        RealityStore(db.toString()).use { reopened ->
            assertEquals(listOf(objective()), reopened.objectivePersistence.read().objectives)
            assertEquals(listOf(matter()), reopened.matterPersistence.read().matters)
            assertEquals(1L, reopened.maintenance.inspect().objectiveCount)
            assertEquals(1L, reopened.maintenance.inspect().matterCount)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun replayingIdenticalUpsertDoesNotChangeRevisionButStaleUpdateFails() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("tx-first")) { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                store.matterPersistence.upsert(matter(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            store.run(TransactionId("tx-repeat")) { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                store.matterPersistence.upsert(matter(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals(1L, store.matterPersistence.read().revision)
            assertThrows(IllegalArgumentException::class.java) {
                store.run(TransactionId("tx-stale")) { scope ->
                    store.objectivePersistence.upsert(objective(99L, "Otra cosa"), scope)
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            store.run(TransactionId("tx-next")) { scope ->
                store.objectivePersistence.upsert(objective(101L, "Nuevo objetivo"), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(2L, store.objectivePersistence.read().revision)
        }
    }

    @Test
    fun rollbackRemovesRowsAndRevisionTogether() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("tx-rollback")) { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                store.matterPersistence.upsert(matter(), scope)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertEquals(0L, store.matterPersistence.read().revision)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertEquals(0, store.matterPersistence.read().matters.size)
            assertTrue(store.maintenance.inspect().freshForSemanticMigration)
        }
    }

    @Test
    fun differentStoreScopeCannotWriteAnotherRealityOwner() {
        RealityStore(":memory:").use { first ->
            RealityStore(":memory:").use { second ->
                assertThrows(IllegalStateException::class.java) {
                    first.run(TransactionId("tx-foreign")) { firstScope ->
                        second.objectivePersistence.upsert(objective(), firstScope)
                        AtomicApplyTransactionDecision.Commit(Unit)
                    }
                }
                assertEquals(0, second.objectivePersistence.read().objectives.size)
                assertEquals(0L, second.objectivePersistence.read().revision)
            }
        }
    }

    @Test
    fun realV5DatabaseMigratesAdditivelyToV7AndPreservesProfile() {
        val db = Files.createTempDirectory("g4-migrate").resolve("reality.db")
        RealityStore(db.toString()).use { source ->
            source.run(TransactionId("tx-profile")) { scope ->
                source.updateDeclared(DeclaredDisplayName.of("Perfil anterior"), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        BundledSQLiteDriver().open(db.toString()).use { connection ->
            connection.execSQL("DROP TABLE objective_record")
            connection.execSQL("DROP TABLE objective_state")
            connection.execSQL("DROP TABLE matter_record")
            connection.execSQL("DROP TABLE matter_state")
            connection.execSQL("PRAGMA user_version = 5")
        }
        RealityStore(db.toString()).use { migrated ->
            assertEquals(7, migrated.schemaVersion())
            assertEquals("Perfil anterior", migrated.read().displayName?.value)
            assertEquals(0, migrated.objectivePersistence.read().objectives.size)
            assertEquals(0, migrated.matterPersistence.read().matters.size)
            assertEquals("ok", migrated.integrityCheck())
        }
    }

    @Test
    fun legacyPortableBackupAndMigrationFailClosedWhenDomainDataExists() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("tx-g4-only")) { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(listOf(objective()), store.domainBackup.snapshot().objectives.objectives)
            assertEquals(1L, store.maintenance.inspect().objectiveCount)
            assertTrue(!store.maintenance.inspect().freshForSemanticMigration)
            assertThrows(IllegalStateException::class.java) { store.backup.snapshot() }
            assertThrows(IllegalStateException::class.java) { store.semanticMigration.snapshot() }
        }
    }

    @Test
    fun legacyBackupRestoresIntoFreshV7WithoutInventingNewDomainData() {
        val snapshot = RealityStore(":memory:").use { source -> source.backup.snapshot() }
        RealityStore(":memory:").use { target ->
            assertEquals(snapshot, target.backup.restoreOrVerify(snapshot))
            assertEquals(emptyList<ObjectiveRecord>(), target.objectivePersistence.read().objectives)
            assertEquals(emptyList<MatterRecord>(), target.matterPersistence.read().matters)
            assertEquals(5, target.backup.snapshot().schemaVersion)
            assertEquals(7, target.schemaVersion())
        }
    }

    @Test
    fun realSchemaSixReceiptsMigrateWithoutInventingCommitTimes() {
        val db = Files.createTempDirectory("faro-v5-reality-v6-to-v7").resolve("reality.db")
        val old = RealityStore(db.toString())
        old.use { store ->
            val empty = store.backup.snapshot()
            val legacy = empty.copy(committedReceipts = listOf(
                RealityCommittedReceiptBackup(
                    receiptId = "old-receipt", transactionId = "tx-old",
                    planFingerprint = "plan-old",
                )
            ))
            assertEquals(legacy, store.backup.restoreOrVerify(legacy))
        }
        // Construct an actual v6 durable schema, not merely a downgraded version number.
        BundledSQLiteDriver().open(db.toString()).use { connection ->
            connection.execSQL(
                "ALTER TABLE execution_receipt DROP COLUMN commit_recorded_at_epoch_millis"
            )
            connection.execSQL("PRAGMA user_version = 6")
        }
        RealityStore(db.toString()).use { migrated ->
            assertEquals(7, migrated.schemaVersion())
            assertEquals(listOf("tx-old"), migrated.receiptHistory
                .query(RealityReceiptHistoryQuery()).receipts.map { it.transactionId.value })
            val timed = migrated.timedReceiptHistory.query(RealityTimedReceiptQuery())
            assertTrue(timed.legacyUndated)
            assertTrue(timed.receipts.isEmpty())
            assertEquals(null, migrated.backup.snapshot().committedReceipts
                .single().commitRecordedAtEpochMillis)
            assertEquals(5, migrated.backup.snapshot().schemaVersion)
            assertEquals("ok", migrated.integrityCheck())
        }
    }
}
