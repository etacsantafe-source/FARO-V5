package ar.com.faro.v5.app

import ar.com.faro.v5.owner.matters.api.MatterStatus
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * Real Reality Matter owner fields only. Priority, snooze, reminders and
 * monthly plans are NOT part of MatterRecord and cannot be inferred.
 */
data class FaroV5MatterDueItem(
    val matterId: String,
    val status: MatterStatus,
    val dueAtEpochMillis: Long,
) {
    init {
        require(matterId.isNotBlank())
        require(dueAtEpochMillis >= 0L)
    }
}

data class FaroV5MatterDuePage(
    val items: List<FaroV5MatterDueItem>,
    val ownerRevision: Long,
    val completeWithinMatterDue: Boolean,
) {
    val canonicalSource: String get() = "reality.matter_record"
    val dueTimeProvenance: String get() = "reality.matter_record.due_at_epoch_millis"
    val coverage: String get() = "MATTERS_DUE_ONLY"
    val globalAttentionComplete: Boolean get() = false
    val hasReminderOwner: Boolean get() = false
    val hasMonthlyPlanOwner: Boolean get() = false
}

/**
 * N031 owner contributor: strictly read-only, non-Home, due-only Matter facts
 * from REAL Reality persistence. Every read needs independent host read admission
 * with a pinned durable pointer epoch. Never turn absent ACL into count=0.
 */
class FaroV5RealityMatterDueReadSource(
    private val expectedGenerationId: String,
    private val matters: MatterPersistencePort,
    private val currentGenerationId: () -> String?,
    private val pointerCurrent: () -> Boolean,
    private val pointerEpoch: () -> String?,
    private val aclEpoch: () -> String?,
    private val readAdmitted: () -> Boolean,
) {
    init { require(expectedGenerationId.isNotBlank()) }

    private data class Admission(val pointer: String, val acl: String)

    private fun admission(): Admission? = try {
        if (currentGenerationId() != expectedGenerationId ||
            !pointerCurrent() || !readAdmitted()) null
        else {
            val pointer = pointerEpoch()?.takeIf { it.isNotBlank() }
            val acl = aclEpoch()?.takeIf { it.isNotBlank() }
            if (pointer == null || acl == null ||
                currentGenerationId() != expectedGenerationId ||
                !pointerCurrent() || !readAdmitted() ||
                pointerEpoch() != pointer || aclEpoch() != acl
            ) null else Admission(pointer, acl)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    fun readAuthorized(
        now: Instant,
        zone: ZoneId,
        limit: Int = 50,
    ): FaroV5MatterDuePage? {
        require(limit in 1..100)
        val grant = admission() ?: return null
        val snapshot = try {
            matters.read()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (admission() != grant) return null

        val items = try {
            val through = now.atZone(zone).toLocalDate().plusDays(7L)
            snapshot.matters.asSequence()
                .filter { m ->
                    m.status == MatterStatus.OPEN ||
                    m.status == MatterStatus.IN_PROGRESS ||
                    m.status == MatterStatus.WAITING
                }
                .mapNotNull { m ->
                    val due = m.dueAtEpochMillis ?: return@mapNotNull null
                    if (Instant.ofEpochMilli(due).atZone(zone).toLocalDate().isAfter(through)) null
                    else FaroV5MatterDueItem(m.id.value, m.status, due)
                }
                .sortedWith(compareBy<FaroV5MatterDueItem> { it.dueAtEpochMillis }
                    .thenBy { it.matterId })
                .take(limit + 1)
                .toList()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        return if (admission() == grant) FaroV5MatterDuePage(
            items = items.take(limit),
            ownerRevision = snapshot.revision,
            completeWithinMatterDue = items.size <= limit,
        ) else null
    }
}
