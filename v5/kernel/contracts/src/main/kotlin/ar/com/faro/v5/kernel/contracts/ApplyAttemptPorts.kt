package ar.com.faro.v5.kernel.contracts

data class ApplyAttemptIdentity(
    val transactionId: TransactionId,
    val planFingerprint: PlanFingerprint,
)

/**
 * Resolves the stable identity of an Apply attempt.
 *
 * Implementations must return the same transaction identity when retrying the
 * same durable prepared plan. Global uniqueness and durable persistence belong
 * to the concrete store/journal implementation.
 */
fun interface ApplyAttemptIdentityPort {
    fun resolve(preparedPlan: PreparedPlanView): ApplyAttemptIdentity
}

/**
 * Receipt read boundary used before any owner execution.
 */
fun interface ExecutionReceiptLookupPort {
    fun find(transactionId: TransactionId): ExecutionReceipt?
}
