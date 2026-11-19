package ar.com.faro.v5.owner.matters

import ar.com.faro.v5.owner.matters.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

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


    @Test
    fun extendedValuesDefaultOnlyForLegacyAndKeepCivilReviewDate() {
        val legacy = record()
        assertEquals(MatterDetails(), legacy.details)
        val history = MatterHistoryEntry(
            1, 78L, MatterHistoryKind.STATUS_CHANGED, MatterStatus.OPEN, "Retomar asunto",
        )
        val updated = legacy.copy(
            status = MatterStatus.OPEN, updatedAtEpochMillis = 78,
            details = MatterDetails(
                MatterPriority.HIGH, LocalDate.of(2028, 2, 29), listOf(history),
            ),
        )
        assertEquals(LocalDate.of(2028, 2, 29), updated.details.reviewDate)
        assertEquals(MatterPriority.HIGH, updated.details.priority)
        assertEquals(1, updated.details.history.single().sequence)
    }

    @Test
    fun rejectsOutOfOrderHistoryTimeStatusAndMissingLifecycleReason() {
        val event = MatterHistoryEntry(
            1, 80, MatterHistoryKind.EDITED, MatterStatus.WAITING, "Fecha",
        )
        assertThrows(IllegalArgumentException::class.java) {
            MatterDetails(history = listOf(event.copy(sequence = 2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterDetails(history = listOf(event, event.copy(sequence = 2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(updatedAt = 79).copy(details = MatterDetails(history = listOf(event)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(updatedAt = 80).copy(
                status = MatterStatus.OPEN, details = MatterDetails(history = listOf(event)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterHistoryEntry(1, 80, MatterHistoryKind.STATUS_CHANGED, MatterStatus.WAITING, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterHistoryEntry(1, 80, MatterHistoryKind.EDITED, MatterStatus.WAITING, "a\nb")
        }
        val old = MatterDetails(history = listOf(event))
        assertEquals(true, old.copy(
            history = listOf(event, event.copy(sequence = 2, recordedAtEpochMillis = 81)),
        ).retainsHistoryOf(old))
        assertEquals(false, old.copy(history = emptyList()).retainsHistoryOf(old))
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
