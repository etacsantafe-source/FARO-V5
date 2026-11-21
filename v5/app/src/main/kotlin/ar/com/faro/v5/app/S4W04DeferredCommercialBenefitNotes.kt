package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/**
 * D2C1 DEV: user-declared commercial caveats, never monetary entitlements.
 * Read-only. No bank, coupon, Finance, checkout, cashback or stock authority.
 */
internal object S4W04DeferredCommercialBenefitNotes {
    enum class BenefitKind {
        CASHBACK_FUTURE,
        INSTALLMENT_CONDITION,
        BANK_ELIGIBILITY,
        OTHER_UNVERIFIED,
    }

    enum class Provenance { USER_DECLARED_UNVERIFIED }
    enum class ReviewStatus { REVIEW_REQUIRED_UNVERIFIED, UNCLASSIFIED_NO_CLAIM }

    data class ExplicitNote(
        val cartFingerprint: String,
        val sourceWatermark: String,
        val asOfEpochMillis: Long,
        val key: GroupKey,
        val kind: BenefitKind,
        val details: String? = null,
        val sourceRef: String? = null,
    )

    data class Notice(
        val kind: BenefitKind,
        val details: String?,
        val sourceRef: String?,
        val provenance: Provenance = Provenance.USER_DECLARED_UNVERIFIED,
    )

    data class GroupDisclosure(
        val key: GroupKey,
        val reviewStatus: ReviewStatus,
        val groupCoverage: S4W04MerchantWhatIfSummary.GroupCoverage,
        val notices: List<Notice>,
    )

    enum class Rejection {
        INVALID_SUMMARY,
        INVALID_NOTE,
        MISMATCHED_FENCE,
        DUPLICATE_KIND_PER_GROUP,
        UNKNOWN_GROUP,
    }

    sealed interface Decision {
        data class Annotated(
            val cartFingerprint: String,
            val sourceWatermark: String,
            val asOfEpochMillis: Long,
            val coverage: S4W04MerchantWhatIfSummary.Coverage,
            val unmodeledNeedIds: List<HouseholdNeedId>,
            val groups: List<GroupDisclosure>,
            val warning: String = "NO_CASHBACK_FINANCE_STOCK_APPLY",
            val notice: String = "USER_DECLARED_UNVERIFIED_COMMERCIAL_BENEFITS_NOT_MONEY",
        ) : Decision

        data class Rejected(val reason: Rejection) : Decision
    }

    private val currencyCode = Regex("[A-Z]{3}")
    private val groupOrder = compareBy<GroupKey>(
        { it.currencyCode }, { it.merchant }, { it.localityCode }, { it.paymentMethod.name },
    )

    fun annotate(
        summary: S4W04MerchantWhatIfSummary.Decision.Summary,
        notes: List<ExplicitNote>,
    ): Decision {
        if (summary.cartFingerprint.isBlank() || summary.sourceWatermark.isBlank() ||
            summary.asOfEpochMillis < 0L ||
            notes.any {
                it.cartFingerprint != summary.cartFingerprint ||
                    it.sourceWatermark != summary.sourceWatermark ||
                    it.asOfEpochMillis != summary.asOfEpochMillis
            }
        ) return Decision.Rejected(Rejection.MISMATCHED_FENCE)

        if (!validSummary(summary)) return Decision.Rejected(Rejection.INVALID_SUMMARY)
        if (notes.size > 200 || notes.any {
                !validKey(it.key) || !validLiteral(it.details) || !validLiteral(it.sourceRef)
            }
        ) return Decision.Rejected(Rejection.INVALID_NOTE)

        val rowKeys = summary.rows.map { it.key }.toSet()
        if (notes.any { it.key !in rowKeys }) return Decision.Rejected(Rejection.UNKNOWN_GROUP)

        if (notes.map { it.key to it.kind }.distinct().size != notes.size) {
            return Decision.Rejected(Rejection.DUPLICATE_KIND_PER_GROUP)
        }

        val notesByKey = notes.groupBy { it.key }
        val disclosures = summary.rows.sortedWith(Comparator { left, right ->
            groupOrder.compare(left.key, right.key)
        })
            .map { row ->
                val notices = notesByKey[row.key].orEmpty().sortedBy { it.kind.name }
                    .map { Notice(it.kind, it.details, it.sourceRef) }
                GroupDisclosure(
                    key = row.key,
                    reviewStatus = if (notices.isEmpty()) ReviewStatus.UNCLASSIFIED_NO_CLAIM
                        else ReviewStatus.REVIEW_REQUIRED_UNVERIFIED,
                    groupCoverage = row.groupCoverage,
                    notices = Collections.unmodifiableList(notices),
                )
            }
        return Decision.Annotated(
            cartFingerprint = summary.cartFingerprint,
            sourceWatermark = summary.sourceWatermark,
            asOfEpochMillis = summary.asOfEpochMillis,
            coverage = summary.coverage,
            unmodeledNeedIds =
                Collections.unmodifiableList(summary.unmodeledNeedIds.toList()),
            groups = Collections.unmodifiableList(disclosures),
        )
    }

    private fun validKey(key: GroupKey): Boolean =
        key.currencyCode.matches(currencyCode) && key.merchant.isNotBlank() &&
            key.localityCode.isNotBlank() &&
            key.paymentMethod != CommercialPaymentMethod.UNKNOWN

    private fun validLiteral(value: String?): Boolean =
        value == null || (value.isNotBlank() && value.length <= 1024)

    private fun validSummary(
        summary: S4W04MerchantWhatIfSummary.Decision.Summary,
    ): Boolean {
        if (summary.rows.size !in 1..200 ||
            summary.cashbackStatus !=
                S4W04MerchantWhatIfSummary.CashbackStatus.UNVERIFIED_NOT_APPLIED ||
            summary.warning != "NO_FINANCE_OR_CASHBACK_APPLY" ||
            summary.notice != "HISTORICAL_WHAT_IF_NOT_CURRENT_QUOTE_OR_REDEEMED_DISCOUNT"
        ) return false

        val keys = mutableSetOf<GroupKey>()
        val allSelected = mutableSetOf<HouseholdNeedId>()
        val allModeled = mutableSetOf<HouseholdNeedId>()
        for (row in summary.rows) {
            if (!validKey(row.key) || !keys.add(row.key) ||
                row.observedBaseSubtotalMinor < 0L || row.modeledBaseMinor < 0L ||
                row.modeledHypotheticalDiscountMinor < 0L ||
                row.modeledHypotheticalAfterMinor < 0L ||
                row.modeledBaseMinor > row.observedBaseSubtotalMinor ||
                row.modeledHypotheticalDiscountMinor > row.modeledBaseMinor ||
                row.modeledHypotheticalAfterMinor !=
                    row.modeledBaseMinor - row.modeledHypotheticalDiscountMinor ||
                row.selectedNeedIds.isEmpty() ||
                row.selectedNeedIds != row.selectedNeedIds.distinct().sortedBy { it.value } ||
                row.modeledNeedIds != row.modeledNeedIds.distinct().sortedBy { it.value } ||
                row.modeledNeedIds.any { it !in row.selectedNeedIds } ||
                row.groupCoverage !=
                    (if (row.selectedNeedIds.size == row.modeledNeedIds.size)
                        S4W04MerchantWhatIfSummary.GroupCoverage.FULLY_MODELED
                    else S4W04MerchantWhatIfSummary.GroupCoverage.PARTIAL)
            ) return false

            if (!allSelected.addAllChecked(row.selectedNeedIds) ||
                !allModeled.addAllChecked(row.modeledNeedIds)) return false
        }

        val missing = summary.missingNeedIds
        val unmodeled = summary.unmodeledNeedIds
        if (missing.size + allSelected.size > 200 ||
            missing != missing.distinct().sortedBy { it.value } ||
            missing.any { it in allSelected } ||
            unmodeled != unmodeled.distinct().sortedBy { it.value } ||
            unmodeled != (missing + allSelected.filterNot { it in allModeled })
                .sortedBy { it.value } ||
            summary.coverage != (if (unmodeled.isEmpty())
                S4W04MerchantWhatIfSummary.Coverage.ALL_MODELED
            else S4W04MerchantWhatIfSummary.Coverage.PARTIAL)
        ) return false
        return true
    }

    private fun MutableSet<HouseholdNeedId>.addAllChecked(
        items: List<HouseholdNeedId>,
    ): Boolean {
        for (item in items) if (!add(item)) return false
        return true
    }
}
