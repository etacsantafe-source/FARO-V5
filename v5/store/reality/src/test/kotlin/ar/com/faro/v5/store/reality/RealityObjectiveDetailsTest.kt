package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.objectives.api.*
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityObjectiveDetailsTest {
    @TempDir lateinit var directory: Path

    private val legacy = ObjectiveRecord(
        ObjectiveId.of("goal"), ObjectiveTitle.of("Leer libros"), ObjectiveDetail.of("Lectura anual"),
        ObjectiveStatus.ACTIVE, 100,
    )
    private val unit = ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.QUANTITY, "libros")
    private val updated = legacy.copy(updatedAtEpochMillis = 101, details = ObjectiveDetails(
        ObjectiveMetric(12, unit), LocalDate.of(2027, 12, 31), ObjectivePriority.HIGH,
        listOf(ObjectiveMeasurement(1, 101, unit, 3)),
    ))

    @Test fun upgradesLegacyRowsAndReopensWithoutChangingTheBaseSchema() {
        val db = directory.resolve("reality.db").toString()
        RealityStore(db).use { store -> put(store, legacy) }
        RealityStore(db).use { store ->
            assertEquals(ObjectiveSnapshot(1, listOf(legacy)), store.objectivePersistence.read())
            put(store, updated)
            put(store, updated)
        }
        RealityStore(db).use { store ->
            assertEquals(7, store.schemaVersion())
            assertEquals(ObjectiveSnapshot(2, listOf(updated)), store.objectivePersistence.read())
            assertThrows(IllegalArgumentException::class.java) {
                put(store, updated.copy(updatedAtEpochMillis = 102, details = ObjectiveDetails()))
            }
            assertEquals(ObjectiveSnapshot(2, listOf(updated)), store.objectivePersistence.read())
        }
    }

    @Test fun rollsBackTheFirstExtensionAndRestoresIdempotently() {
        val db = directory.resolve("rollback.db").toString()
        val snapshot = RealityStore(":memory:").use { source ->
            put(source, updated)
            source.domainBackup.snapshot()
        }
        RealityStore(db).use { store ->
            store.run(TransactionId("trial")) { scope ->
                store.objectivePersistence.upsert(updated, scope)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(ObjectiveSnapshot(0, emptyList()), store.objectivePersistence.read())
        }
        BundledSQLiteDriver().open(db).use { connection ->
            connection.prepare("SELECT count(*) FROM sqlite_schema WHERE name IN ('objective_details', 'objective_measurement')").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
        RealityStore(db).use { store ->
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(snapshot))
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(snapshot))
        }
        RealityStore(db).use { store ->
            assertEquals(snapshot, store.domainBackup.snapshot())
            assertThrows(IllegalStateException::class.java) {
                store.domainBackupRestore.restoreOrVerify(snapshot.copy(
                    objectives = ObjectiveSnapshot(1, listOf(legacy)),
                ))
            }
            assertEquals(snapshot, store.domainBackup.snapshot())
        }
    }

    @Test fun rejectsAnInvalidMetricOrMeasurementSequenceBeforePersistence() {
        assertThrows(IllegalArgumentException::class.java) { ObjectiveMetric(0, unit) }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.MONETARY, "peso")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveDetails(measurements = listOf(ObjectiveMeasurement(2, 101, unit, 3)))
        }
        val qualitative = ObjectiveDetails(dueDate = LocalDate.of(2027, 1, 1))
        assertNull(qualitative.target)
        assertTrue(qualitative.measurements.isEmpty())
    }

    private fun put(store: RealityStore, record: ObjectiveRecord) {
        store.run(TransactionId("objective-test")) { scope ->
            store.objectivePersistence.upsert(record, scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }
}
