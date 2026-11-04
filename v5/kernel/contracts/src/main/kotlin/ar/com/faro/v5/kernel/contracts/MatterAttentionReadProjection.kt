package ar.com.faro.v5.kernel.contracts

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * N-031 planning-owner contract foundation; does not create an owner, table or
 * operation. Only a canonical authority may supply MatterAttentionSnapshot.
 * An absent read port/snapshot means UNKNOWN, never an empty list or zero.
 */
enum class MatterAttentionLifecycle { OPEN, RESOLVED, DISMISSED }
enum class MatterAttentionPriority { HIGH, NORMAL, LOW }

data class MatterAttentionRecord(
    val id: String,
    val lifecycle: MatterAttentionLifecycle,
    val priority: MatterAttentionPriority,
    val dueOn: LocalDate?,
    val reviewOn: Instant?,
    val createdAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "matter identity must not be blank" }
    }
}

/**
 * Trusted, generation-scoped read input from a future planning owner.
 * This is not a source of truth and never implies reminders/monthly-plan
 * attention are complete or available.
 */
data class MatterAttentionSnapshot(
    val generationId: String,
    val revision: Long,
    val matters: List<MatterAttentionRecord>,
) {
    init {
        require(generationId.isNotBlank()) { "attention authority generation must be nonblank" }
        require(revision >= 0L) { "attention revision must be nonnegative" }
        require(matters.map { it.id }.toSet().size == matters.size) {
            "attention read has duplicate matter identities"
        }
    }
}

fun interface MatterAttentionReadPort {
    suspend fun readSnapshot(): MatterAttentionSnapshot?
}

/**
 * Pure matter-only contributor to attention.read. Matches the useful V4.4
 * rule: OPEN, not snoozed, and HIGH or due within seven local civil days.
 * The clock and zone come from the owner/host, never the device default here.
 *
 * This MUST NOT be bound to Home's Pendientes count until independent reminder,
 * monthly-plan and owner-ACL coverage has been proven.
 */
object MatterAttentionProjection {
    fun urgentMatters(
        snapshot: MatterAttentionSnapshot,
        now: Instant,
        zone: ZoneId,
    ): List<MatterAttentionRecord> {
        val today = now.atZone(zone).toLocalDate()
        val through = today.plusDays(7L)
        return snapshot.matters.asSequence()
            .filter { it.lifecycle == MatterAttentionLifecycle.OPEN }
            .filter { it.reviewOn?.isAfter(now) != true }
            .filter {
                it.priority == MatterAttentionPriority.HIGH ||
                    (it.dueOn != null && !it.dueOn.isAfter(through))
            }
            .sortedWith(
                compareBy<MatterAttentionRecord> {
                    when {
                        it.dueOn != null && !it.dueOn.isAfter(today) -> 0
                        it.priority == MatterAttentionPriority.HIGH -> 1
                        else -> 2
                    }
                }.thenBy { it.dueOn ?: LocalDate.MAX }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            .toList()
    }
}
