package ar.com.faro.v5.store.reality

/**
 * W01-B3B1B0 DEV/STAGED9 ONLY. Policy and opening source observed in one
 * SQLite WAL read transaction. NOT an authenticated principal or a product
 * Finance read grant. A caller may forge the staging request.
 */
data class RealityFinance9ObservedSourceRead(
    val observedPolicy: RealityFinance9ObservedReadPolicy,
    val openingSource: RealityFinance9OpeningProvenanceSnapshot,
) {
    init {
        require(observedPolicy.policyEpoch > 0L)
        require(openingSource.sourceRevision >= 0L)
    }
}

fun interface RealityFinance9ObservedSourceReadPort {
    /** null = no exact policy or inconsistent provenance/transaction/owner. */
    fun observe(
        request: RealityFinance9ReadPolicyObservationRequest,
    ): RealityFinance9ObservedSourceRead?
}
