package ar.com.faro.v5.owner.objectives

import ar.com.faro.v5.owner.objectives.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ObjectiveModelTest {
    @Test
    fun normalizesIdentityAndTitleWithoutInventingRecords() {
        assertEquals("obj-1", ObjectiveId.of("  obj-1 ").value)
        assertEquals("Ahorrar", ObjectiveTitle.of(" Ahorrar ").value)
        assertEquals("", ObjectiveDetail.of("  ").value)
        assertThrows(IllegalArgumentException::class.java) { ObjectiveId.of(" ") }
        assertThrows(IllegalArgumentException::class.java) { ObjectiveTitle.of(" ") }
    }

    @Test
    fun rejectsDuplicateIdsAndNegativeRevision() {
        val row = record()
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveSnapshot(-1, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveSnapshot(1, listOf(row, row))
        }
    }

    @Test
    fun rejectsMalformedOrOversizedFieldsAndTime() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveId.of("a".repeat(129))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTitle.of("x".repeat(161))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveDetail.of("x".repeat(4097))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(updatedAt = -1)
        }
    }

    @Test
    fun readApiIsOnlyAContractAndDoesNotManufacturePersistence() {
        val expected = ObjectiveSnapshot(2, listOf(record()))
        var reads = 0
        val reader = ObjectiveSnapshotReadApi {
            reads++
            expected
        }
        assertEquals(expected, reader.read())
        assertEquals(1, reads)
        assertEquals(ObjectiveStatus.ACTIVE, reader.read().objectives.single().status)
    }

    private fun record(updatedAt: Long = 42L) = ObjectiveRecord(
        id = ObjectiveId.of("obj-1"),
        title = ObjectiveTitle.of("Ahorrar"),
        detail = ObjectiveDetail.of("Fondo de emergencia"),
        status = ObjectiveStatus.ACTIVE,
        updatedAtEpochMillis = updatedAt,
    )
}
