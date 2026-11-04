package ar.com.faro.v5.kernel.contracts

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatterAttentionReadProjectionTest {
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val now = Instant.parse("2026-11-04T12:00:00Z")

    private fun matter(
        id: String,
        priority: MatterAttentionPriority = MatterAttentionPriority.NORMAL,
        lifecycle: MatterAttentionLifecycle = MatterAttentionLifecycle.OPEN,
        due: String? = null,
        reviewOn: Instant? = null,
    ) = MatterAttentionRecord(
        id = id,
        lifecycle = lifecycle,
        priority = priority,
        dueOn = due?.let(LocalDate::parse),
        reviewOn = reviewOn,
        createdAt = now.minusSeconds(100L),
    )

    private fun snap(vararg values: MatterAttentionRecord) =
        MatterAttentionSnapshot("g1", 2L, values.toList())

    @Test
    fun `high and seven-day due matter qualifies but ordinary far-future does not`() {
        val result = MatterAttentionProjection.urgentMatters(
            snap(
                matter("high", priority = MatterAttentionPriority.HIGH),
                matter("week", due = "2026-11-11"),
                matter("future", due = "2026-11-12"),
                matter("late", priority = MatterAttentionPriority.LOW, due = "2026-11-01"),
                matter("no-due"),
            ), now, zone,
        )
        assertEquals(listOf("late", "high", "week"), result.map { it.id })
    }

    @Test
    fun `terminal or snoozed matters never appear before their review time`() {
        val result = MatterAttentionProjection.urgentMatters(
            snap(
                matter("resolved", MatterAttentionPriority.HIGH, MatterAttentionLifecycle.RESOLVED),
                matter("dismissed", MatterAttentionPriority.HIGH, MatterAttentionLifecycle.DISMISSED),
                matter("snoozed", MatterAttentionPriority.HIGH, reviewOn = now.plusSeconds(1)),
                matter("due-for-review", MatterAttentionPriority.HIGH, reviewOn = now),
            ), now, zone,
        )
        assertEquals(listOf("due-for-review"), result.map { it.id })
    }

    @Test
    fun `local civil day boundary uses supplied zone and not UTC default`() {
        val early = Instant.parse("2026-11-05T02:30:00Z")
        val later = Instant.parse("2026-11-05T03:30:00Z")
        val input = snap(matter("due-on-12", due = "2026-11-12"))
        assertTrue(MatterAttentionProjection.urgentMatters(input, early, zone).isEmpty())
        assertEquals(
            listOf("due-on-12"),
            MatterAttentionProjection.urgentMatters(input, later, zone).map { it.id },
        )
    }

    @Test
    fun `owner snapshot rejects duplicate or missing identity and revision`() {
        assertThrows(IllegalArgumentException::class.java) {
            snap(matter("duplicate"), matter("duplicate"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterAttentionSnapshot("", 0L, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterAttentionSnapshot("g1", -1L, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            matter("")
        }
    }
}
