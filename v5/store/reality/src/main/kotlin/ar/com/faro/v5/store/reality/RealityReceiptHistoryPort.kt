package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.ExecutionReceipt

/**
 * N-015 Reality receipt authority, NOT the finished history.query federation.
 * The schema has no durable commit timestamp. An ID-sorted receipt list is
 * never an "Actividad reciente" timeline, and cannot be counted as one.
 */
data class RealityReceiptHistoryQuery(
    val afterTransactionId: String? = null,
    val limit: Int = 50,
) {
    init {
        require(afterTransactionId == null || afterTransactionId.isNotBlank()) {
            "history cursor must be nonblank"
        }
        require(limit in 1..100) { "receipt history page size must be 1..100" }
    }
}

/** An opaque, deterministic Reality-only page; no date or domain inference. */
data class RealityReceiptHistoryPage(
    val receipts: List<ExecutionReceipt>,
    val nextCursor: String?,
) {
    val canonicalSource: String get() = "reality.execution_receipt"
    val hasCommitTimestamps: Boolean get() = false
}

fun interface RealityReceiptHistoryReadPort {
    fun query(query: RealityReceiptHistoryQuery): RealityReceiptHistoryPage
}
