package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.finance.api.FinanceEntryKind
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot

/** Keyset cursor over owner-declared recorded time; NOT an Apply commit time. */
data class RealityFinanceActivityCursor(
    val recordedAtEpochMillis: Long,
    val entryId: String,
) {
    init {
        require(entryId.isNotBlank()) { "finance activity cursor ID must be nonblank" }
    }
}

data class RealityFinanceActivityQuery(
    val accountId: String? = null,
    val kind: FinanceEntryKind? = null,
    val after: RealityFinanceActivityCursor? = null,
    val limit: Int = 50,
) {
    init {
        require(accountId == null || accountId.isNotBlank()) {
            "finance activity account filter must be nonblank"
        }
        require(limit in 1..100) { "finance activity page size must be 1..100" }
    }
}

/**
 * This is the exact Reality finance ledger subset. It is NOT history.query and
 * cannot certify an Apply COMMITTED receipt or a complete Activity timeline.
 * recordedAt is an owner field, never silently relabeled as commit time.
 */
data class RealityFinanceActivityPage(
    val entries: List<FinanceEntrySnapshot>,
    val nextCursor: RealityFinanceActivityCursor?,
    val financeRevision: Long,
) {
    init {
        require(financeRevision >= 0L)
    }
    val canonicalSource: String get() = "reality.finance_entry"
    val timeProvenance: String get() = "finance.recorded_at_epoch_millis"
    val isGlobalHistoryComplete: Boolean get() = false
    val certifiesApplyCommit: Boolean get() = false
}

fun interface RealityFinanceActivityReadPort {
    fun query(query: RealityFinanceActivityQuery): RealityFinanceActivityPage
}
