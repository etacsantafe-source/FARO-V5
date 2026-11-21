package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder

/** Pure STAGED Evidence2 comparison. Neither a quote/stock guarantee nor an admission grant. */
enum class CommercialObservationComparisonStatus {
    COMPARABLE_BASE_OBSERVED,
    OBSERVATION_DATE_UNKNOWN,
    FUTURE_OBSERVATION,
    STALE_OBSERVATION,
    LOCALITY_UNKNOWN,
    PRESENTATION_UNKNOWN,
    VALIDITY_UNKNOWN,
    OUTSIDE_VALIDITY,
    PAYMENT_UNKNOWN,
    ELIGIBILITY_NOT_GENERAL,
    PROMOTION_NOT_BASE,
}

data class CommercialObservedHistoryEntry(
    val item: PortableCommercialTypedObservation,
    val comparisonStatus: CommercialObservationComparisonStatus,
)

data class CommercialObservedGroupKey(
    val currencyCode: String,
    val localityCode: String,
    val presentationQuantityThousandths: Long,
    val presentationUnit: CommercialPresentationUnit,
    val packCount: Int,
    val paymentMethod: CommercialPaymentMethod,
)

data class CommercialObservedComparisonGroup(
    val key: CommercialObservedGroupKey,
    /** One latest observation per merchant; the original full history stays intact. */
    val merchantEntries: List<CommercialObservedHistoryEntry>,
    /** Gross historical observed minimum, only across two+ distinct merchants. Not a current offer. */
    val lowestObserved: CommercialObservedHistoryEntry?,
) {
    init {
        require(merchantEntries.map { it.item.observation.merchant }.distinct().size ==
            merchantEntries.size)
    }
}

enum class CommercialObservedComparisonCoverage {
    NO_OBSERVATIONS,
    PARTIAL_OBSERVATIONS_ONLY,
    COMPARABLE_OBSERVED_HISTORY_ONLY,
}

data class CommercialTypedPriceComparisonResult(
    val productKey: CommercialProductKey,
    val sourceWatermark: String,
    val asOfEpochMillis: Long,
    val maxObservationAgeMillis: Long,
    val truncated: Boolean,
    val history: List<CommercialObservedHistoryEntry>,
    val groups: List<CommercialObservedComparisonGroup>,
    val coverage: CommercialObservedComparisonCoverage,
    /** Must never be displayed as verified current price, availability or savings. */
    val notice: String = "OBSERVED_HISTORY_ONLY_NOT_CURRENT_QUOTE_OR_STOCK",
)

/**
 * Requires an already-authorized, healthy H1 STAGED snapshot from its owning host.
 * It cannot fetch data or grant access; it verifies canonical order and the whole H1
 * watermark so altered terms cannot silently lead to a price comparison.
 */
object CommercialTypedPriceComparison {
    private const val MAX_AGE_MILLIS = 365L * 24L * 60L * 60L * 1000L

    private val canonicalOrder = compareBy<PortableCommercialTypedObservation>(
        { it.observation.productKey },
        { it.observation.merchant },
        { it.observation.currencyCode },
        { it.observation.observedAtEpochMillis ?: Long.MAX_VALUE },
        { it.observation.acquiredAtEpochMillis },
        { it.observation.amountMinor },
        { it.observation.sourceKind },
        { it.observation.sourceRef },
        { it.terms != null },
        { it.terms?.fingerprint() ?: "" },
    )

    private val historyOrder = compareByDescending<PortableCommercialTypedObservation> {
        it.observation.observedAtEpochMillis ?: Long.MIN_VALUE
    }.thenByDescending { it.observation.acquiredAtEpochMillis }
        .thenBy { it.observation.merchant }
        .thenBy { it.observation.currencyCode }
        .thenBy { it.observation.amountMinor }
        .thenBy { it.observation.sourceRef }
        .thenBy { it.terms?.fingerprint() ?: "" }

    fun project(
        snapshot: CommercialEvidenceStagedTypedPortabilitySnapshot,
        productKey: CommercialProductKey,
        asOfEpochMillis: Long,
        maxObservationAgeMillis: Long,
        limit: Int = 200,
    ): CommercialTypedPriceComparisonResult {
        require(asOfEpochMillis >= 0L)
        require(maxObservationAgeMillis in 1..MAX_AGE_MILLIS)
        require(limit in 1..200)
        verifySnapshot(snapshot)

        val matching = snapshot.records
            .asSequence()
            .filter { it.observation.productKey == productKey.value }
            .sortedWith(historyOrder)
            .toList()
        val truncated = matching.size > limit
        val history = matching.take(limit).map { item ->
            CommercialObservedHistoryEntry(
                item,
                classify(item, asOfEpochMillis, maxObservationAgeMillis),
            )
        }
        val groups = history.asSequence()
            .filter { it.comparisonStatus ==
                CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED }
            .groupBy { entry ->
                val obs = entry.item.observation
                val terms = checkNotNull(entry.item.terms)
                CommercialObservedGroupKey(
                    currencyCode = obs.currencyCode,
                    localityCode = checkNotNull(terms.localityCode),
                    presentationQuantityThousandths =
                        checkNotNull(terms.presentationQuantityThousandths),
                    presentationUnit = checkNotNull(terms.presentationUnit),
                    packCount = checkNotNull(terms.packCount),
                    paymentMethod = terms.paymentMethod,
                )
            }
            .map { (key, matches) ->
                val newestPerMerchant = matches
                    .groupBy { it.item.observation.merchant }
                    .values.map { entries ->
                        entries.maxWith(
                            compareBy<CommercialObservedHistoryEntry>(
                                { it.item.observation.observedAtEpochMillis ?: Long.MIN_VALUE },
                                { it.item.observation.acquiredAtEpochMillis },
                                { it.item.observation.sourceRef },
                                { it.item.observation.amountMinor },
                            )
                        )
                    }.sortedBy { it.item.observation.merchant }
                val minimum =
                    if (!truncated && newestPerMerchant.size >= 2) {
                        newestPerMerchant.minWith(
                            compareBy<CommercialObservedHistoryEntry>(
                                { it.item.observation.amountMinor },
                                { it.item.observation.merchant },
                                { it.item.observation.sourceRef },
                            )
                        )
                    } else null
                CommercialObservedComparisonGroup(
                    key = key,
                    merchantEntries = newestPerMerchant,
                    lowestObserved = minimum,
                )
            }.sortedBy { it.key.toString() }

        return CommercialTypedPriceComparisonResult(
            productKey = productKey,
            sourceWatermark = snapshot.sourceWatermark,
            asOfEpochMillis = asOfEpochMillis,
            maxObservationAgeMillis = maxObservationAgeMillis,
            truncated = truncated,
            history = history,
            groups = groups,
            coverage = when {
                history.isEmpty() -> CommercialObservedComparisonCoverage.NO_OBSERVATIONS
                groups.any { it.lowestObserved != null } ->
                    CommercialObservedComparisonCoverage.COMPARABLE_OBSERVED_HISTORY_ONLY
                else -> CommercialObservedComparisonCoverage.PARTIAL_OBSERVATIONS_ONLY
            },
        )
    }

    private fun classify(
        item: PortableCommercialTypedObservation,
        asOf: Long,
        maxAge: Long,
    ): CommercialObservationComparisonStatus {
        val observed = item.observation.observedAtEpochMillis
            ?: return CommercialObservationComparisonStatus.OBSERVATION_DATE_UNKNOWN
        if (observed > asOf) return CommercialObservationComparisonStatus.FUTURE_OBSERVATION
        if (asOf - observed > maxAge) {
            return CommercialObservationComparisonStatus.STALE_OBSERVATION
        }
        val terms = item.terms
        if (terms == null || terms.localityKind != CommercialLocalityKind.KNOWN ||
            terms.localityCode == null
        ) return CommercialObservationComparisonStatus.LOCALITY_UNKNOWN
        if (terms.presentationQuantityThousandths == null ||
            terms.presentationUnit == null || terms.packCount == null
        ) return CommercialObservationComparisonStatus.PRESENTATION_UNKNOWN
        if (terms.validFromEpochMillis == null || terms.validUntilEpochMillis == null) {
            return CommercialObservationComparisonStatus.VALIDITY_UNKNOWN
        }
        if (asOf < terms.validFromEpochMillis || asOf > terms.validUntilEpochMillis) {
            return CommercialObservationComparisonStatus.OUTSIDE_VALIDITY
        }
        if (terms.paymentMethod == CommercialPaymentMethod.UNKNOWN) {
            return CommercialObservationComparisonStatus.PAYMENT_UNKNOWN
        }
        if (terms.eligibility != CommercialEligibility.GENERAL) {
            return CommercialObservationComparisonStatus.ELIGIBILITY_NOT_GENERAL
        }
        if (terms.promotionKind != CommercialPromotionKind.NONE) {
            return CommercialObservationComparisonStatus.PROMOTION_NOT_BASE
        }
        return CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED
    }

    private fun verifySnapshot(snapshot: CommercialEvidenceStagedTypedPortabilitySnapshot) {
        require(snapshot.schemaVersion == 2)
        require(snapshot.records == snapshot.records.sortedWith(canonicalOrder)) {
            "noncanonical STAGED commercial record order"
        }
        val hash = CanonicalHashBuilder.domain("faro-portability-commercial-v2")
            .string("schemaVersion", "2")
            .string("count", snapshot.records.size.toString())
        snapshot.records.forEachIndexed { index, item ->
            val obs = item.observation
            require(obs.productKey.isNotBlank() && obs.merchant.isNotBlank())
            require(obs.currencyCode.matches(Regex("[A-Z]{3}")))
            require(obs.amountMinor > 0L && obs.acquiredAtEpochMillis >= 0L)
            require(obs.observedAtEpochMillis == null || obs.observedAtEpochMillis >= 0L)
            require(obs.sourceRef.isNotBlank() && obs.sourceRef.length <= 1024)
            val prefix = "record[$index]."
            hash.string(prefix + "productKey", obs.productKey)
                .string(prefix + "merchant", obs.merchant)
                .string(prefix + "currencyCode", obs.currencyCode)
                .string(prefix + "amountMinor", obs.amountMinor.toString())
                .string(prefix + "sourceKind", obs.sourceKind)
                .string(prefix + "sourceRef", obs.sourceRef)
                .string(prefix + "observed.present",
                    (obs.observedAtEpochMillis != null).toString())
                .string(prefix + "observed", obs.observedAtEpochMillis?.toString() ?: "")
                .string(prefix + "acquired", obs.acquiredAtEpochMillis.toString())
                .string(prefix + "terms.present", (item.terms != null).toString())
                .string(prefix + "terms.fingerprint", item.terms?.fingerprint() ?: "")
        }
        require(hash.sha256Hex() == snapshot.sourceWatermark) {
            "STAGED commercial watermark mismatch"
        }
    }
}
