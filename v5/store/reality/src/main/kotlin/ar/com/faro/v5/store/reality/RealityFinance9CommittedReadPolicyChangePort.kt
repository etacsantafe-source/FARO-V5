package ar.com.faro.v5.store.reality

/**
 * DEV/STAGED9 READ policy result returned only after the owning SQLite COMMIT.
 *
 * changed=false means an existing revoked epoch was observed without a new
 * transition (idempotent revoke); it MUST NOT trigger an Android notice.
 * This object contains no balance, principal, grant or display authorization.
 */
data class Finance9CommittedReadPolicyChange(
    val observedPolicyEpoch: Long,
    val changed: Boolean,
) {
    init { require(observedPolicyEpoch > 0L) }
}

/**
 * B3B1B1B1B1B1C: cooperatively written DEV policy changes only.
 * Not a human-authentication host. Raw SQLite writers do not use this port.
 */
interface RealityFinance9CommittedReadPolicyChangePort {
    fun issueAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyIssueRequest,
    ): Finance9CommittedReadPolicyChange?

    fun revokeAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyRevocationRequest,
    ): Finance9CommittedReadPolicyChange?
}
