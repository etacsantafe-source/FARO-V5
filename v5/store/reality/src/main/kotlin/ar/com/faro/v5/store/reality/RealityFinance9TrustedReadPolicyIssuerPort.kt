package ar.com.faro.v5.store.reality

/**
 * W01-B3B1B1B1A: STAGED9 DEV ONLY.
 *
 * The request is freely constructible: it DOES NOT prove AndroidKeyStore,
 * authenticated presence, real-time host clock, consent or read authority.
 * The actual trusted Android owner and cross-process invalidations remain
 * B3B1B1B1B, NOT IMPLEMENTED here.
 *
 * The operation and object scopes are deliberately NOT caller parameters:
 * only finance.snapshot.read / finance:all can be issued by this port.
 */
data class Finance9TrustedReadPolicyIssueRequest(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(generationId.length in 1..128 &&
            generationId == generationId.trim() &&
            generationId.none { it.code < 32 || it.code == 127 })
        require(pointerSequence > 0L)
        require(issuedAtMs >= 0L && expiresAtMs > issuedAtMs &&
            expiresAtMs - issuedAtMs <= 30_000L) {
            "DEV read policy is short lived and requires trusted-host issuance later"
        }
    }
}

/**
 * SQL observation of a newly issued policy epoch, NEVER a biometric token,
 * a human authorization, an Apply grant, or a displayable Finance snapshot.
 */
data class Finance9ReadPolicyIssueRecord(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val policyEpoch: Long,
    val expiresAtMs: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(generationId.isNotBlank() && pointerSequence > 0L)
        require(policyEpoch > 0L && expiresAtMs > 0L)
    }
}

fun interface RealityFinance9TrustedReadPolicyIssuerPort {
    /** Caller MUST be a separately authenticated future DEV host, not UI. */
    fun issueAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyIssueRequest,
    ): Finance9ReadPolicyIssueRecord?
}
