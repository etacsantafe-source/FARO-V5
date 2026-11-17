package ar.com.faro.v5.store.reality

/**
 * S4 W01 B3B1B1B1B1A: fixed-scope DEV/STAGED9 READ policy revocation request.
 * This is NOT a biometric proof, display lease, Apply authorization or
 * authorization to show Finance balances. A DEV caller can forge this request.
 */
data class Finance9TrustedReadPolicyRevocationRequest(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(generationId.length in 1..128 &&
            generationId == generationId.trim() &&
            generationId.none { it.code < 32 || it.code == 127 })
        require(pointerSequence > 0L)
    }
}

/**
 * Returned after the ACL's SQLite COMMIT, or null when absent/denied/failed.
 * The same epoch is returned for an idempotent repeat: the record CANNOT be
 * used to infer that a NEW transition occurred or to publish an Android event.
 */
data class Finance9ObservedRevokedReadPolicyRecord(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val observedRevokedPolicyEpoch: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(generationId.isNotBlank() && pointerSequence > 0L)
        require(observedRevokedPolicyEpoch > 0L)
    }
}

fun interface RealityFinance9TrustedReadPolicyRevocationPort {
    /** No operation/object scope parameters; always finance.snapshot.read/finance:all. */
    fun revokeAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyRevocationRequest,
    ): Finance9ObservedRevokedReadPolicyRecord?
}
