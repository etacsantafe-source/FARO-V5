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

/** Reality-only chronologically indexed *known* receipt times; NOT global history.query. */
data class RealityTimedReceiptCursor(
    val recordedAtEpochMillis: Long,
    val transactionId: String,
) {
    init {
        require(recordedAtEpochMillis >= 0L)
        require(transactionId.isNotBlank())
    }
}

data class RealityTimedReceipt(val receipt: ExecutionReceipt, val recordedAtEpochMillis: Long) {
    init { require(recordedAtEpochMillis >= 0L) }
}

data class RealityTimedReceiptQuery(
    val before: RealityTimedReceiptCursor? = null,
    val limit: Int = 50,
) {
    init { require(limit in 1..100) }
}

data class RealityTimedReceiptPage(
    val receipts: List<RealityTimedReceipt>,
    val nextCursor: RealityTimedReceiptCursor?,
    val legacyUndated: Boolean,
) {
    val canonicalSource: String get() = "reality.execution_receipt"
    val globallyFederated: Boolean get() = false
}

fun interface RealityTimedReceiptHistoryReadPort {
    fun query(query: RealityTimedReceiptQuery): RealityTimedReceiptPage
}
