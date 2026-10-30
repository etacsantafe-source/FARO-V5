package ar.com.faro.v5.kernel.contracts

enum class ApplyAttemptJournalState {
    PREPARED,
    COMMITTED,
}

data class ApplyAttemptSnapshot(
    val transactionId: TransactionId,
    val planFingerprint: PlanFingerprint,
    val state: ApplyAttemptJournalState,
)

fun interface ApplyAttemptLookupPort {
    fun find(transactionId: TransactionId): ApplyAttemptSnapshot?
}
