package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.matters.api.*
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03MatterDetailsTest {
    @TempDir lateinit var directory: Path

    private val legacy = MatterRecord(
        MatterId.of("matter"), MatterTitle.of("Turno medico"),
        MatterDetail.of("Confirmar cita"), MatterStatus.WAITING, null, 100L,
    )
    private val event = MatterHistoryEntry(
        1, 101L, MatterHistoryKind.EDITED, MatterStatus.WAITING, "Revisar prioridad",
    )
    private val updated = legacy.copy(
        updatedAtEpochMillis = 101L,
        details = MatterDetails(
            MatterPriority.HIGH, LocalDate.of(2028, 2, 29), listOf(event),
        ),
    )

    @Test fun legacyRowsReopenWithoutFabricatedHistoryAndExtendedValuesPersist() {
        val path = directory.resolve("matters.db").toString()
        RealityStore(path).use { store ->
            put(store, legacy)
            assertEquals(MatterSnapshot(1, listOf(legacy)), store.matterPersistence.read())
        }
        RealityStore(path).use { store ->
            assertEquals(MatterDetails(), store.matterPersistence.read().matters.single().details)
            put(store, updated)
            put(store, updated)
        }
        RealityStore(path).use { store ->
            assertEquals(7, store.schemaVersion())
            assertEquals(MatterSnapshot(2, listOf(updated)), store.matterPersistence.read())
        }
    }

    @Test fun rollbackRevertsLazyTablesRowsRevisionAndRestoreIsIdempotent() {
        val path = directory.resolve("rollback.db").toString()
        val snapshot = RealityStore(":memory:").use { source ->
            put(source, updated)
            source.domainBackup.snapshot()
        }
        RealityStore(path).use { store ->
            store.run(TransactionId("rollback-matter")) { scope ->
                store.matterPersistence.upsert(updated, scope)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(MatterSnapshot(0, emptyList()), store.matterPersistence.read())
        }
        BundledSQLiteDriver().open(path).use { connection ->
            connection.prepare("""
                SELECT count(*) FROM sqlite_schema
                WHERE type='table' AND name IN ('matter_details','matter_history')
            """.trimIndent()).use { st ->
                assertTrue(st.step())
                assertEquals(0L, st.getLong(0))
            }
        }
        RealityStore(path).use { store ->
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(snapshot))
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(snapshot))
        }
        RealityStore(path).use { store ->
            assertEquals(snapshot, store.domainBackup.snapshot())
            assertThrows(IllegalStateException::class.java) {
                store.domainBackupRestore.restoreOrVerify(snapshot.copy(
                    matters = MatterSnapshot(1, listOf(legacy)),
                ))
            }
            assertEquals(snapshot, store.domainBackup.snapshot())
        }
    }

    @Test fun staleUpdatesAndHistoryRewritesCannotReplaceStoredEvents() {
        RealityStore(":memory:").use { store ->
            put(store, updated)
            assertThrows(IllegalArgumentException::class.java) {
                put(store, updated.copy(details = updated.details.copy(priority = MatterPriority.LOW)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                put(store, updated.copy(updatedAtEpochMillis = 102L,
                    details = updated.details.copy(history = emptyList()),
                ))
            }
            assertThrows(IllegalArgumentException::class.java) {
                put(store, updated.copy(updatedAtEpochMillis = 102L,
                    details = updated.details.copy(history = listOf(event.copy(reason = "Inventado"))),
                ))
            }
            val nextEvent = MatterHistoryEntry(
                2, 102L, MatterHistoryKind.EDITED, MatterStatus.WAITING, "Verificar cita",
            )
            val next = updated.copy(
                updatedAtEpochMillis = 102L,
                details = updated.details.copy(history = listOf(event, nextEvent)),
            )
            put(store, next)
            assertEquals(MatterSnapshot(2, listOf(next)), store.matterPersistence.read())
        }
    }

    private fun put(store: RealityStore, record: MatterRecord) {
        store.run(TransactionId("matter-test")) { scope ->
            store.matterPersistence.upsert(record, scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }
}
