package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Decision.Annotated
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.GroupDisclosure
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Provenance
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ReviewStatus
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.CashbackStatus
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/** Seals the contents of a historical review. Live source validation belongs to the host. */
internal object S4W04CommercialPreReviewEnvelope {
    enum class Rejection { MISMATCHED_FENCE, INVALID_SUMMARY, INVALID_DISCLOSURES, INVALID_COVERAGE }
    enum class Warning {
        READ_ONLY_PREVIEW_NOT_APPLY,
        HISTORICAL_AND_USER_DECLARED_UNVERIFIED,
        LIVE_REVALIDATION_REQUIRED,
    }

    data class ReviewRow(val estimate: Row, val disclosure: GroupDisclosure)

    sealed interface Decision {
        @ConsistentCopyVisibility
        data class Preview internal constructor(
            val cartFingerprint: String,
            val sourceWatermark: String,
            val asOfEpochMillis: Long,
            val digest: String,
            val coverage: Coverage,
            val missingNeedIds: List<HouseholdNeedId>,
            val unmodeledNeedIds: List<HouseholdNeedId>,
            val rows: List<ReviewRow>,
        ) : Decision {
            val groupCount: Int get() = rows.size
            val warnings: List<Warning> get() = previewWarnings
        }

        data class Rejected(val reason: Rejection) : Decision
    }

    private const val MAX_NEEDS = 200
    private const val MAX_NOTICES = 200
    private val previewWarnings = Collections.unmodifiableList(Warning.entries)
    private val currencyCode = Regex("[A-Z]{3}")
    private val rowOrder = compareBy<Row>(
        { it.key.currencyCode }, { it.key.merchant }, { it.key.localityCode },
        { it.key.paymentMethod.name },
    )

    fun preview(summary: Summary, annotated: Annotated): Decision {
        if (!validIdentity(summary.cartFingerprint) || !validIdentity(summary.sourceWatermark) ||
            summary.asOfEpochMillis < 0L ||
            summary.cartFingerprint != annotated.cartFingerprint ||
            summary.sourceWatermark != annotated.sourceWatermark ||
            summary.asOfEpochMillis != annotated.asOfEpochMillis
        ) return Decision.Rejected(Rejection.MISMATCHED_FENCE)

        if (summary.rows.size !in 1..MAX_NEEDS ||
            summary.rows.any { it.selectedNeedIds.size !in 1..MAX_NEEDS ||
                it.modeledNeedIds.size > it.selectedNeedIds.size } ||
            summary.cashbackStatus != CashbackStatus.UNVERIFIED_NOT_APPLIED ||
            summary.notice != "HISTORICAL_WHAT_IF_NOT_CURRENT_QUOTE_OR_REDEEMED_DISCOUNT" ||
            summary.warning != "NO_FINANCE_OR_CASHBACK_APPLY"
        ) return Decision.Rejected(Rejection.INVALID_SUMMARY)

        val rows = summary.rows.map { row ->
            row.copy(
                selectedNeedIds = frozen(row.selectedNeedIds),
                modeledNeedIds = frozen(row.modeledNeedIds),
            )
        }.sortedWith(rowOrder)
        val selected = rows.flatMap { it.selectedNeedIds }
        if (rows.map { it.key }.toSet().size != rows.size ||
            selected.toSet().size != selected.size || rows.any { !validRow(it) }
        ) return Decision.Rejected(Rejection.INVALID_SUMMARY)

        if (summary.missingNeedIds.size > MAX_NEEDS || summary.unmodeledNeedIds.size > MAX_NEEDS) {
            return Decision.Rejected(Rejection.INVALID_COVERAGE)
        }
        val missing = frozen(summary.missingNeedIds)
        val modeled = rows.flatMap { it.modeledNeedIds }.toSet()
        val selectedSet = selected.toSet()
        val expectedUnmodeled = (missing + selected.filterNot { it in modeled }).sortedBy { it.value }
        val expectedCoverage = if (expectedUnmodeled.isEmpty()) Coverage.ALL_MODELED else Coverage.PARTIAL
        val unmodeled = frozen(summary.unmodeledNeedIds)
        if (selected.size + missing.size > MAX_NEEDS || !canonicalIds(missing) ||
            missing.any { it in selectedSet } || unmodeled != expectedUnmodeled ||
            summary.coverage != expectedCoverage
        ) return Decision.Rejected(Rejection.INVALID_COVERAGE)

        if (annotated.groups.size != rows.size || annotated.coverage != summary.coverage ||
            annotated.unmodeledNeedIds != unmodeled ||
            annotated.warning != "NO_CASHBACK_FINANCE_STOCK_APPLY" ||
            annotated.notice != "USER_DECLARED_UNVERIFIED_COMMERCIAL_BENEFITS_NOT_MONEY"
        ) return Decision.Rejected(Rejection.INVALID_DISCLOSURES)

        val disclosures = annotated.groups.associateBy { it.key }
        val reviewRows = ArrayList<ReviewRow>(rows.size)
        var noticeCount = 0
        for (row in rows) {
            val disclosure = disclosures[row.key]
                ?: return Decision.Rejected(Rejection.INVALID_DISCLOSURES)
            if (disclosure.notices.size > MAX_NOTICES - noticeCount) {
                return Decision.Rejected(Rejection.INVALID_DISCLOSURES)
            }
            val notices = frozen(disclosure.notices.sortedBy { it.kind.name })
            val expectedStatus = if (notices.isEmpty()) ReviewStatus.UNCLASSIFIED_NO_CLAIM
                else ReviewStatus.REVIEW_REQUIRED_UNVERIFIED
            if (disclosure.groupCoverage != row.groupCoverage ||
                disclosure.reviewStatus != expectedStatus ||
                notices.map { it.kind }.toSet().size != notices.size ||
                notices.any { it.provenance != Provenance.USER_DECLARED_UNVERIFIED ||
                    !validLiteral(it.details) || !validLiteral(it.sourceRef) }
            ) return Decision.Rejected(Rejection.INVALID_DISCLOSURES)
            noticeCount += notices.size
            reviewRows.add(ReviewRow(row, disclosure.copy(notices = notices)))
        }

        val digest = CanonicalHashBuilder.domain("w04.d3a.pre-review.v1")
            .string("cartFingerprint", summary.cartFingerprint)
            .string("sourceWatermark", summary.sourceWatermark)
            .string("asOfEpochMillis", summary.asOfEpochMillis.toString())
            .string("coverage", summary.coverage.name)
            .ids("missing", missing)
            .ids("unmodeled", unmodeled)
            .string("rowCount", reviewRows.size.toString())
        reviewRows.forEachIndexed { index, row -> digest.reviewRow("row[$index]", row) }
        return Decision.Preview(
            summary.cartFingerprint, summary.sourceWatermark, summary.asOfEpochMillis,
            digest.sha256Hex(), summary.coverage, missing, unmodeled,
            Collections.unmodifiableList(reviewRows),
        )
    }

    private fun validKey(key: GroupKey): Boolean =
        key.currencyCode.matches(currencyCode) && validIdentity(key.merchant) &&
            validIdentity(key.localityCode) && key.paymentMethod != CommercialPaymentMethod.UNKNOWN

    private fun validRow(row: Row): Boolean {
        val modeledIds = row.modeledNeedIds
        val selectedIds = row.selectedNeedIds
        val fullyModeled = modeledIds.size == selectedIds.size
        val expectedCoverage = if (fullyModeled) GroupCoverage.FULLY_MODELED else GroupCoverage.PARTIAL
        return validKey(row.key) && canonicalIds(selectedIds) && canonicalIds(modeledIds) &&
            selectedIds.containsAll(modeledIds) && row.groupCoverage == expectedCoverage &&
            row.observedBaseSubtotalMinor >= 0L && row.modeledBaseMinor in 0L..row.observedBaseSubtotalMinor &&
            (modeledIds.isNotEmpty() || row.modeledBaseMinor == 0L) &&
            (!fullyModeled || row.modeledBaseMinor == row.observedBaseSubtotalMinor) &&
            row.modeledHypotheticalDiscountMinor in 0L..row.modeledBaseMinor &&
            row.modeledHypotheticalAfterMinor == row.modeledBaseMinor - row.modeledHypotheticalDiscountMinor
    }

    private fun canonicalIds(ids: List<HouseholdNeedId>): Boolean =
        ids.all { validIdentity(it.value) } && (1 until ids.size).all { index ->
            ids[index - 1].value < ids[index].value
        }

    // Reject malformed Unicode before the UTF-8 framing can replace it with another character.
    private fun validIdentity(value: String): Boolean =
        value.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(value)

    private fun validLiteral(value: String?): Boolean =
        value == null || (value.length <= 1024 && validIdentity(value))

    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

    private fun CanonicalHashBuilder.ids(
        field: String,
        values: List<HouseholdNeedId>,
    ): CanonicalHashBuilder {
        string("$field.count", values.size.toString())
        values.forEachIndexed { index, id -> string("$field[$index]", id.value) }
        return this
    }

    private fun CanonicalHashBuilder.reviewRow(field: String, review: ReviewRow) {
        val row = review.estimate
        val key = row.key
        string("$field.currency", key.currencyCode)
        string("$field.merchant", key.merchant)
        string("$field.locality", key.localityCode)
        string("$field.payment", key.paymentMethod.name)
        string("$field.observedBase", row.observedBaseSubtotalMinor.toString())
        string("$field.modeledBase", row.modeledBaseMinor.toString())
        string("$field.hypotheticalDiscount", row.modeledHypotheticalDiscountMinor.toString())
        string("$field.hypotheticalAfter", row.modeledHypotheticalAfterMinor.toString())
        ids("$field.selected", row.selectedNeedIds)
        ids("$field.modeled", row.modeledNeedIds)
        string("$field.coverage", row.groupCoverage.name)
        string("$field.reviewStatus", review.disclosure.reviewStatus.name)
        val notices = review.disclosure.notices
        string("$field.noticeCount", notices.size.toString())
        notices.forEachIndexed { index, notice ->
            val label = "$field.notice[$index]"
            string("$label.kind", notice.kind.name)
            string("$label.provenance", notice.provenance.name)
            nullableText("$label.details", notice.details)
            nullableText("$label.sourceRef", notice.sourceRef)
        }
    }

    private fun CanonicalHashBuilder.nullableText(field: String, value: String?) {
        string("$field.present", (value != null).toString())
        if (value != null) string(field, value)
    }
}
