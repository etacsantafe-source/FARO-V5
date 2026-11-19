package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.matters.api.MatterSnapshot
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.ByteBuffer
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5ObjectiveDetailsBackupTest {
    private val base = ObjectiveRecord(
        ObjectiveId.of("goal"), ObjectiveTitle.of("Fondo"), ObjectiveDetail.of(""),
        ObjectiveStatus.ACTIVE, 200,
    )

    @Test fun legacyWireRemainsVersionOneWithNoInventedTarget() {
        val snapshot = snapshot(base)
        val bytes = FaroV5G4DomainSemanticCodec.encode(snapshot)
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(snapshot, FaroV5G4DomainSemanticCodec.decode(bytes))
    }

    @Test fun extendedWireMaintainsDatesCurrencyAndHistoryAcrossRealityRestore() {
        val currency = ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.MONETARY, "ARS")
        val details = ObjectiveDetails(
            ObjectiveMetric(50_001, currency), LocalDate.of(2028, 2, 29), ObjectivePriority.LOW,
            listOf(ObjectiveMeasurement(1, 200, currency, 10_050)),
        )
        val snapshot = snapshot(base.copy(details = details))
        val bytes = FaroV5G4DomainSemanticCodec.encode(snapshot)
        assertEquals(2, ByteBuffer.wrap(bytes).getInt(4))
        val decoded = FaroV5G4DomainSemanticCodec.decode(bytes)
        assertEquals(snapshot, decoded)
        RealityStore(":memory:").use { store ->
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(decoded))
            assertArrayEquals(bytes, FaroV5G4DomainSemanticCodec.encode(store.domainBackup.snapshot()))
        }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(bytes.copyOf(bytes.size - 1))
        }
    }

    private fun snapshot(objective: ObjectiveRecord) = RealityDomainBackupSnapshot(
        7, ObjectiveSnapshot(1, listOf(objective)), MatterSnapshot(0, emptyList()),
    )
}
