package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.reminders.api.ReminderSnapshot
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshot

/**
 * Canonical Reality schema8 source; NOT a user-facing read/write capability.
 * FBS2v3 transports the same fields via PlanningDomainSectionSnapshot.
 * This source exists only in an explicit, inactive STAGED V2 generation.
 */
data class RealityPlanningBackupSnapshot(
    val schemaVersion: Int,
    val reminders: ReminderSnapshot,
    val monthlyPlan: MonthlyPlanSnapshot,
) {
    init { requireRestorable() }

    val isFresh: Boolean
        get() = reminders.revision == 0L && reminders.rules.isEmpty() &&
            reminders.occurrences.isEmpty() &&
            monthlyPlan.revision == 0L && monthlyPlan.items.isEmpty()

    fun requireRestorable() {
        require(schemaVersion == 8) { "Reality Planning snapshot must declare schema8" }
        require(reminders.rules.size <= MAX_ROWS &&
            reminders.occurrences.size <= MAX_ROWS &&
            monthlyPlan.items.size <= MAX_ROWS
        ) { "Planning exceeds portable row budget" }
        require(reminders.revision >=
            reminders.rules.size.toLong() + reminders.occurrences.size.toLong() &&
            monthlyPlan.revision >= monthlyPlan.items.size.toLong()
        ) { "Planning revision predates restored rows" }
        require(canonical(reminders.rules.map { it.id.value }) &&
            canonical(reminders.occurrences.map { it.id.value }) &&
            canonical(monthlyPlan.items.map { it.id.value })
        ) { "Planning rows not in canonical UTF8 binary order" }
        // Exact PLN1 serialization length, enforced before any SQLite staged restore.
        // No dependency cycle from Reality storage to generation/portable codecs.
        fun field(raw: String): Long {
            val n = raw.toByteArray(Charsets.UTF_8).size
            require(n <= MAX_FIELD_BYTES) { "Planning field exceeds portable byte budget" }
            return 4L + n
        }
        var bytes = 40L // PLN1 magic, version, schema, revisions and three counts.
        for (rule in reminders.rules) {
            bytes += field(rule.id.value) + field(rule.title) + field(rule.zoneId) +
                8L + 8L + field(rule.recurrence.name) + 1L + 1L +
                (rule.linkedMatterId?.let(::field) ?: 0L) + 8L
        }
        for (occ in reminders.occurrences) {
            bytes += field(occ.id.value) + field(occ.ruleId.value) + 8L +
                field(occ.status.name) + 1L +
                (if (occ.acknowledgedAtEpochMillis != null) 8L else 0L) + 8L
        }
        for (item in monthlyPlan.items) {
            bytes += field(item.id.value) + field(item.title) + 4L + 4L + 8L +
                field(item.status.name) + 1L +
                (if (item.expectedAmountMinor == null) 0L else
                    8L + field(checkNotNull(item.currencyCode))) +
                1L + (if (item.paymentConfirmedAtEpochMillis == null) 0L else 8L) + 8L
        }
        require(bytes <= MAX_SECTION_BYTES) { "Planning exceeds portable 4MiB section" }
    }

    companion object {
        const val SCHEMA_VERSION = 8
        const val MAX_ROWS = 20_000
        const val MAX_FIELD_BYTES = 4_096
        const val MAX_SECTION_BYTES = 4 * 1024 * 1024

        /** SQLite COLLATE BINARY and PLN1 compare unsigned UTF8, never UTF16. */
        private fun canonical(ids: List<String>): Boolean = ids.zipWithNext().all { (a, b) ->
            val l = a.toByteArray(Charsets.UTF_8)
            val r = b.toByteArray(Charsets.UTF_8)
            val n = minOf(l.size, r.size)
            var i = 0
            while (i < n && l[i] == r[i]) i++
            if (i == n) l.size < r.size
            else (l[i].toInt() and 255) < (r[i].toInt() and 255)
        }
    }
}

/** Only internal, single-Reality-mutex capture under global five-store quiescence. */
fun interface RealityPlanningBackupReadPort {
    fun snapshot(): RealityPlanningBackupSnapshot
}

/** Stage-only transactional exact replay; NOT bound to the Android host. */
fun interface RealityPlanningBackupRestorePort {
    fun restoreOrVerify(snapshot: RealityPlanningBackupSnapshot): RealityPlanningBackupSnapshot
}
