package ar.com.faro.v5.owner.matters

import ar.com.faro.v5.owner.matters.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MatterModelTest {
    @Test
    fun normalizesIdentityAndTitleWithoutInventingMatters() {
        assertEquals("asunto-1", MatterId.of(" asunto-1 ").value)
        assertEquals("Pagar factura", MatterTitle.of(" Pagar factura ").value)
        assertEquals("", MatterDetail.of(" ").value)
        assertThrows(IllegalArgumentException::class.java) { MatterId.of(" ") }
        assertThrows(IllegalArgumentException::class.java) { MatterTitle.of(" ") }
    }

    @Test
    fun rejectsDuplicateMattersAndNegativeRevision() {
        val row = record()
        assertThrows(IllegalArgumentException::class.java) {
            MatterSnapshot(-1, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterSnapshot(2, listOf(row, row))
        }
    }

    @Test
    fun rejectsInvalidDueDatesAndTimestampsAndOversizedText() {
        assertThrows(IllegalArgumentException::class.java) {
            MatterId.of("m".repeat(129))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterTitle.of("x".repeat(161))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterDetail.of("y".repeat(4097))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(dueAt = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(updatedAt = -1)
        }
    }

    @Test
    fun snapshotReadInterfaceIsNotAnOwner() {
        val expected = MatterSnapshot(3, listOf(record()))
        var reads = 0
        val reader = MatterSnapshotReadApi {
            reads++
            expected
        }
        assertEquals(expected, reader.read())
        assertEquals(1, reads)
        assertEquals(MatterStatus.WAITING, reader.read().matters.single().status)
    }

    private fun record(dueAt: Long? = null, updatedAt: Long = 77L) = MatterRecord(
        id = MatterId.of("asunto-1"),
        title = MatterTitle.of("Pagar factura"),
        detail = MatterDetail.of("Esperar vencimiento"),
        status = MatterStatus.WAITING,
        dueAtEpochMillis = dueAt,
        updatedAtEpochMillis = updatedAt,
    )
}
