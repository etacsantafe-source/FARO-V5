package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder

/**
 * C1b1: commercial quote terms are observational Evidence, not Reality.
 * Only the opt-in staged Evidence2 owner may persist them. Legacy v1 rows
 * project UNKNOWN without guessing locality, validity or promotion.
 */
enum class CommercialLocalityKind { UNKNOWN, KNOWN }
enum class CommercialPresentationUnit { UNIT, GRAM, MILLILITER }
enum class CommercialPaymentMethod { UNKNOWN, CASH, DEBIT, CREDIT, TRANSFER, OTHER }
enum class CommercialEligibility { UNKNOWN, GENERAL, RESTRICTED }
enum class CommercialPromotionKind { UNKNOWN, NONE, DISCOUNT_PERCENT, FIXED_AMOUNT }

/**
 * DISCOUNT_PERCENT uses basis points (1..10000), not floating point.
 * FIXED_AMOUNT uses amountMinor of the record's ORIGINAL currency.
 * No currency conversion, scraping, URL execution or inference of validity.
 */
data class CommercialEvidenceTypedTerms(
    val localityKind: CommercialLocalityKind = CommercialLocalityKind.UNKNOWN,
    val localityCode: String? = null,
    val issuedAtEpochMillis: Long? = null,
    val validFromEpochMillis: Long? = null,
    val validUntilEpochMillis: Long? = null,
    val presentationQuantityThousandths: Long? = null,
    val presentationUnit: CommercialPresentationUnit? = null,
    val packCount: Int? = null,
    val paymentMethod: CommercialPaymentMethod = CommercialPaymentMethod.UNKNOWN,
    val eligibility: CommercialEligibility = CommercialEligibility.UNKNOWN,
    val conditions: String? = null,
    val promotionKind: CommercialPromotionKind = CommercialPromotionKind.UNKNOWN,
    val promotionValue: Long? = null,
    val promotionSourceRef: String? = null,
) {
    init {
        if (localityKind == CommercialLocalityKind.UNKNOWN) {
            require(localityCode == null) { "UNKNOWN locality cannot have a code" }
        } else {
            require(localityCode != null && localityCode.matches(Regex("[A-Z0-9][A-Z0-9:_-]{0,63}"))) {
                "known locality requires a canonical code"
            }
        }
        listOf(issuedAtEpochMillis, validFromEpochMillis, validUntilEpochMillis)
            .filterNotNull().forEach { require(it >= 0L) { "negative commercial date" } }
        require(validFromEpochMillis == null || validUntilEpochMillis == null ||
            validFromEpochMillis <= validUntilEpochMillis) { "invalid commercial validity interval" }
        require(issuedAtEpochMillis == null || validUntilEpochMillis == null ||
            issuedAtEpochMillis <= validUntilEpochMillis) { "offer expires before issue" }

        if (presentationQuantityThousandths == null) {
            require(presentationUnit == null && packCount == null) {
                "unknown presentation must not contain partial quantity/unit/pack"
            }
        } else {
            require(presentationQuantityThousandths > 0L && presentationUnit != null &&
                packCount != null && packCount > 0) { "incomplete commercial presentation" }
        }
        require(conditions == null || (conditions.isNotBlank() && conditions.length <= 1024)) {
            "invalid commercial conditions"
        }
        require(promotionSourceRef == null ||
            (promotionSourceRef.isNotBlank() && promotionSourceRef.length <= 1024)) {
            "invalid promotional source ref"
        }
        when (promotionKind) {
            CommercialPromotionKind.UNKNOWN, CommercialPromotionKind.NONE ->
                require(promotionValue == null && promotionSourceRef == null) {
                    "unqualified promotion cannot have value or source"
                }
            CommercialPromotionKind.DISCOUNT_PERCENT ->
                require(promotionValue != null && promotionValue in 1L..10_000L &&
                    promotionSourceRef != null) { "percentage promotion needs bps and source" }
            CommercialPromotionKind.FIXED_AMOUNT ->
                require(promotionValue != null && promotionValue > 0L &&
                    promotionSourceRef != null) { "fixed promotion needs amount and source" }
        }
    }

    fun fingerprint(): String =
        CanonicalHashBuilder.domain("commercial-evidence-typed-terms-v2")
            .string("localityKind", localityKind.name)
            .string("localityCode", localityCode ?: "")
            .string("issuedAt", issuedAtEpochMillis?.toString() ?: "")
            .string("validFrom", validFromEpochMillis?.toString() ?: "")
            .string("validUntil", validUntilEpochMillis?.toString() ?: "")
            .string("quantityThousandths", presentationQuantityThousandths?.toString() ?: "")
            .string("presentationUnit", presentationUnit?.name ?: "")
            .string("packCount", packCount?.toString() ?: "")
            .string("paymentMethod", paymentMethod.name)
            .string("eligibility", eligibility.name)
            .string("conditions", conditions ?: "")
            .string("promotionKind", promotionKind.name)
            .string("promotionValue", promotionValue?.toString() ?: "")
            .string("promotionSourceRef", promotionSourceRef ?: "")
            .sha256Hex()

    companion object {
        val UNKNOWN = CommercialEvidenceTypedTerms()
    }
}
