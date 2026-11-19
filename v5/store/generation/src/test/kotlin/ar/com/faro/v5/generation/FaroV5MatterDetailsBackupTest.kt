package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.ByteBuffer
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5MatterDetailsBackupTest {
    private val legacy = MatterRecord(
        MatterId.of("matter"), MatterTitle.of("Reunion"),
        MatterDetail.of("Revisar"), MatterStatus.OPEN, null, 10L,
    )

    @Test fun oldMatterDataKeepsExactLegacyG4WireVersion() {
        val snapshot = snapshot(legacy)
        val encoded = FaroV5G4DomainSemanticCodec.encode(snapshot)
        assertEquals(1, ByteBuffer.wrap(encoded).getInt(4))
        assertEquals(snapshot, FaroV5G4DomainSemanticCodec.decode(encoded))
    }

    @Test fun extendedMatterDataRoundTripsThroughVersionFiveAndRealityRestore() {
        val updated = legacy.copy(
            status = MatterStatus.WAITING,
            dueAtEpochMillis = 250L,
            updatedAtEpochMillis = 20L,
            details = MatterDetails(
                MatterPriority.HIGH, LocalDate.of(2028, 2, 29),
                listOf(MatterHistoryEntry(
                    1, 20L, MatterHistoryKind.STATUS_CHANGED,
                    MatterStatus.WAITING, "Esperar respuesta",
                )),
            ),
        )
        val snapshot = snapshot(updated)
        val encoded = FaroV5G4DomainSemanticCodec.encode(snapshot)
        assertEquals(5, ByteBuffer.wrap(encoded).getInt(4))
        assertEquals(snapshot, FaroV5G4DomainSemanticCodec.decode(encoded))
        RealityStore(":memory:").use { store ->
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(
                FaroV5G4DomainSemanticCodec.decode(encoded),
            ))
            assertEquals(snapshot, store.domainBackupRestore.restoreOrVerify(snapshot))
            assertArrayEquals(encoded, FaroV5G4DomainSemanticCodec.encode(store.domainBackup.snapshot()))
        }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }

    private fun snapshot(record: MatterRecord) = RealityDomainBackupSnapshot(
        7, ObjectiveSnapshot(0, emptyList()), MatterSnapshot(1, listOf(record)),
    )
}
