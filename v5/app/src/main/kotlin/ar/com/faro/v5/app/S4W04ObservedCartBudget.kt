package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/** D2A: historical estimates only. No price, checkout, finance or stock authority. */
internal object S4W04ObservedCartBudget {
    data class ExplicitChoice(
        val needId: HouseholdNeedId,
        val productKey: String,
        val selectedObservation: CommercialObservedHistoryEntry,
        val cartFingerprint: String,
        val sourceWatermark: String,
        val asOfEpochMillis: Long,
    )

    data class GroupKey(
        val currencyCode: String,
        val merchant: String,
        val localityCode: String,
        val paymentMethod: CommercialPaymentMethod,
    )

    data class EstimatedLine(
        val needId: HouseholdNeedId,
        val productKey: String,
        val merchant: String,
        val currencyCode: String,
        val localityCode: String,
        val paymentMethod: CommercialPaymentMethod,
        val observedAtEpochMillis: Long,
        val sourceRef: String,
        val requestedPackCount: Int,
        val observedAmountMinor: Long,
        val estimatedMinor: Long,
    )

    data class GroupSubtotal(val key: GroupKey, val estimatedMinor: Long)

    enum class Completeness { PARTIAL, ALL_SELECTED }

    data class Estimate(
        val cartFingerprint: String,
        val sourceWatermark: String,
        val asOfEpochMillis: Long,
        val completeness: Completeness,
        val missingNeedIds: List<HouseholdNeedId>,
        val lines: List<EstimatedLine>,
        val subtotals: List<GroupSubtotal>,
        val notice: String,
        val warning: String = "HISTORICAL_OBSERVATION_NOT_CURRENT_STOCK_OR_CHECKOUT",
    )

    enum class Rejection {
        INVALID_COVERAGE,
        UNKNOWN_OR_DUPLICATE_CHOICE,
        STALE_SELECTION,
        AMBIGUOUS_OBSERVATION,
        INELIGIBLE_OBSERVATION,
        OVERFLOW,
    }

    sealed interface Decision {
        data class ObservedEstimate(val estimate: Estimate) : Decision
        data class Rejected(val reason: Rejection) : Decision
    }

    fun estimate(
        coverageView: CommercialCartCoverageView,
        explicitChoices: List<ExplicitChoice>,
    ): Decision {
        if (coverageView.cartFingerprint.isBlank() || coverageView.sourceWatermark.isBlank() ||
            coverageView.asOfEpochMillis < 0L ||
            coverageView.maxObservationAgeMillis !in 1L..MAX_OBSERVATION_AGE ||
            coverageView.lines.size !in 1..200 ||
            coverageView.lines.map { it.needId }.toSet().size != coverageView.lines.size ||
            coverageView.lines.any { it.requestedPackCount !in 1..10_000 }
        ) return rejected(Rejection.INVALID_COVERAGE)
        val linesByNeed = coverageView.lines.associateBy { it.needId }
        if (explicitChoices.size > linesByNeed.size ||
            explicitChoices.map { it.needId }.toSet().size != explicitChoices.size ||
            explicitChoices.any { it.needId !in linesByNeed }
        ) return rejected(Rejection.UNKNOWN_OR_DUPLICATE_CHOICE)

        val estimatedLines = mutableListOf<EstimatedLine>()
        val sums = mutableMapOf<GroupKey, Long>()
        for (choice in explicitChoices.sortedBy { it.needId.value }) {
            val line = checkNotNull(linesByNeed[choice.needId])
            if (choice.productKey != line.productKey.value ||
                choice.cartFingerprint != coverageView.cartFingerprint ||
                choice.sourceWatermark != coverageView.sourceWatermark ||
                choice.asOfEpochMillis != coverageView.asOfEpochMillis
            ) return rejected(Rejection.STALE_SELECTION)

            // Structural membership must be unambiguous. Never pick history.lowestObserved.
            if (line.history.count { it == choice.selectedObservation } != 1) {
                return rejected(Rejection.AMBIGUOUS_OBSERVATION)
            }
            val entry = choice.selectedObservation
            val obs = entry.item.observation
            val terms = entry.item.terms ?: return rejected(Rejection.INELIGIBLE_OBSERVATION)
            val observedAt = obs.observedAtEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_OBSERVATION)
            val from = terms.validFromEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_OBSERVATION)
            val until = terms.validUntilEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_OBSERVATION)
            val locality = terms.localityCode
                ?: return rejected(Rejection.INELIGIBLE_OBSERVATION)
            val presentationQuantity = terms.presentationQuantityThousandths

            if (entry.comparisonStatus !=
                    CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED ||
                obs.productKey != choice.productKey || !obs.currencyCode.matches(Regex("[A-Z]{3}")) ||
                obs.merchant.isBlank() || obs.sourceRef.isBlank() || obs.sourceRef.length > 1024 ||
                obs.amountMinor <= 0L || terms.localityKind != CommercialLocalityKind.KNOWN ||
                terms.eligibility != CommercialEligibility.GENERAL ||
                terms.promotionKind != CommercialPromotionKind.NONE ||
                terms.paymentMethod == CommercialPaymentMethod.UNKNOWN ||
                presentationQuantity == null || presentationQuantity <= 0L ||
                terms.presentationUnit == null || terms.packCount != 1 ||
                observedAt < 0L || observedAt > coverageView.asOfEpochMillis ||
                coverageView.asOfEpochMillis - observedAt > coverageView.maxObservationAgeMillis ||
                from > coverageView.asOfEpochMillis || until < coverageView.asOfEpochMillis
            ) return rejected(Rejection.INELIGIBLE_OBSERVATION)

            val amount = try {
                Math.multiplyExact(obs.amountMinor, line.requestedPackCount.toLong())
            } catch (_: ArithmeticException) {
                return rejected(Rejection.OVERFLOW)
            }
            val key = GroupKey(obs.currencyCode, obs.merchant, locality, terms.paymentMethod)
            sums[key] = try {
                Math.addExact(sums[key] ?: 0L, amount)
            } catch (_: ArithmeticException) {
                return rejected(Rejection.OVERFLOW)
            }
            estimatedLines += EstimatedLine(
                line.needId, choice.productKey, obs.merchant, obs.currencyCode, locality,
                terms.paymentMethod, observedAt, obs.sourceRef, line.requestedPackCount,
                obs.amountMinor, amount,
            )
        }
        val missing = coverageView.lines.map { it.needId }
            .filterNot { id -> explicitChoices.any { it.needId == id } }
            .sortedBy { it.value }
        val completeness = if (missing.isEmpty()) Completeness.ALL_SELECTED else Completeness.PARTIAL
        val subtotals = sums.toList()
            .sortedWith(compareBy({ it.first.currencyCode }, { it.first.merchant },
                { it.first.localityCode }, { it.first.paymentMethod.name }))
            .map { GroupSubtotal(it.first, it.second) }
        return Decision.ObservedEstimate(Estimate(
            coverageView.cartFingerprint, coverageView.sourceWatermark,
            coverageView.asOfEpochMillis, completeness,
            Collections.unmodifiableList(missing),
            Collections.unmodifiableList(estimatedLines),
            Collections.unmodifiableList(subtotals),
            if (completeness == Completeness.PARTIAL)
                "OBSERVED_ESTIMATE_PARTIAL_NOT_A_QUOTE"
            else "OBSERVED_ESTIMATE_ONLY_NOT_CURRENT",
        ))
    }

    private const val MAX_OBSERVATION_AGE = 365L * 24L * 60L * 60L * 1000L
    private fun rejected(reason: Rejection) = Decision.Rejected(reason)
}
