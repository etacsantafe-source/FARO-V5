package ar.com.faro.v5.kernel.contracts

enum class ScheduledWorkExecutionResult {
    SUCCESS,
    RETRY,
    FAILURE,
}

/**
 * Platform-neutral bridge from a persistent scheduler trigger back into current FARO authority.
 *
 * The caller supplies only opaque scheduling identities. Implementations must re-read current
 * canonical state before doing work; scheduler payload is never domain Truth.
 */
fun interface ScheduledWorkExecutionPort {
    fun execute(
        workKind: String,
        semanticId: String,
    ): ScheduledWorkExecutionResult
}
