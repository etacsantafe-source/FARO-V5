package ar.com.faro.v5.kernel.contracts

enum class OperationRevalidationFailure {
    POLICY_REJECTED,
    PRECONDITION_FAILED,
}

sealed interface OperationRevalidationResult {
    data object Valid : OperationRevalidationResult

    data class Rejected(
        val failure: OperationRevalidationFailure,
        val code: String,
    ) : OperationRevalidationResult {
        init {
            require(code.isNotBlank()) { "operation revalidation code must not be blank" }
        }
    }
}

/**
 * Owner-supplied final semantic revalidation for one already-prepared operation.
 *
 * Apply receives these ports through explicit composition. The port may read
 * current owner state but must not mutate durable state.
 */
interface OperationRevalidationPort {
    val operationId: OperationId

    fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult
}
