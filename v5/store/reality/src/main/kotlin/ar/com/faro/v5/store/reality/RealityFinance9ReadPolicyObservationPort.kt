package ar.com.faro.v5.store.reality

/**
 * W01-B3B1A: STAGED9-only, informational Finance9 read policy observation.
 *
 * This is NOT an ACL grant, trusted identity, human presence check, or UI
 * authorization. Any caller can forge a request or time in DEV; a future
 * trusted Android host must authenticate the principal independently.
 */
data class RealityFinance9ReadPolicyObservationRequest(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val expectedPolicyEpoch: Long,
    val observedAtMs: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(generationId.length in 1..128 && generationId == generationId.trim())
        require(generationId.none { it.code < 32 || it.code == 127 })
        require(pointerSequence > 0L)
        require(expectedPolicyEpoch > 0L)
        require(observedAtMs >= 0L)
    }
}

/** Snapshot of an observed SQL policy row, not permission to expose balances. */
data class RealityFinance9ObservedReadPolicy(
    val generationId: String,
    val pointerSequence: Long,
    val policyEpoch: Long,
    val globalPolicyRevision: Long,
) {
    init {
        require(generationId.isNotBlank() && pointerSequence > 0L)
        require(policyEpoch > 0L && globalPolicyRevision >= 0L)
    }
}

fun interface RealityFinance9ReadPolicyObservationPort {
    /** null = absent, expired, revoked, stale epoch, closed or bad SQL. */
    fun observe(
        request: RealityFinance9ReadPolicyObservationRequest,
    ): RealityFinance9ObservedReadPolicy?

    companion object {
        const val OPERATION_ID: String = "finance.snapshot.read"
        const val OBJECT_SCOPE: String = "finance:all"
    }
}
