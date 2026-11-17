package ar.com.faro.v5.store.reality

/**
 * W01-B3B0 DEV/STAGED9 only. A CONFIRMED SQL row is database consistency
 * evidence, NOT proof of authenticated human consent or a Finance ACL grant.
 * A future app-owned adapter must enforce principal, pointer and ACL fences.
 */
enum class RealityFinance9OpeningProvenanceState {
    LEGACY_UNVERIFIED,
    MISSING_CONFIRMATION,
    CONFIRMED_IN_SQL,
}

/** Unknown opening and balance are null, NEVER inferred as zero. */
data class RealityFinance9OpeningProvenanceAccount(
    val accountId: String,
    val displayName: String,
    val currencyCode: String,
    val state: RealityFinance9OpeningProvenanceState,
    val confirmedOpeningMinor: Long? = null,
    val ledgerBalanceMinor: Long? = null,
) {
    init {
        require(accountId.isNotBlank() && accountId.length <= 128)
        require(displayName.isNotBlank() && displayName.length <= 80)
        require(Regex("[A-Z]{3}").matches(currencyCode))
        if (state == RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL) {
            require(confirmedOpeningMinor != null && confirmedOpeningMinor >= 0L)
            require(ledgerBalanceMinor != null)
        } else {
            require(confirmedOpeningMinor == null && ledgerBalanceMinor == null) {
                "Unconfirmed opening cannot supply a ledger balance"
            }
        }
    }
}

/** A coherent, local SQLite read snapshot, not a capability to display funds. */
data class RealityFinance9OpeningProvenanceSnapshot(
    val sourceRevision: Long,
    val accounts: List<RealityFinance9OpeningProvenanceAccount>,
) {
    init {
        require(sourceRevision >= 0L)
        require(accounts.size <= 100)
        require(accounts.map { it.accountId }.distinct().size == accounts.size)
    }
}

/**
 * No biometric, no ACL, no receipt, no Apply. Staged SQL read only.
 * null = denied/closed/contradiction/overflow; it NEVER means empty accounts.
 */
fun interface RealityFinance9OpeningProvenanceReadPort {
    fun read(): RealityFinance9OpeningProvenanceSnapshot?
}
