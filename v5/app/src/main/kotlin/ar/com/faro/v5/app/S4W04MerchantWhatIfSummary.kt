package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04ObservedCartBudget.Estimate
import ar.com.faro.v5.app.S4W04ObservedCartBudget.EstimatedLine
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.app.S4W04ObservedPromotionScenario.GroupSubtotal
import ar.com.faro.v5.app.S4W04ObservedPromotionScenario.Scenario
import ar.com.faro.v5.app.S4W04ObservedPromotionScenario.SimulatedLine
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/** Read-only projection of historical estimates and explicitly modeled offers. */
internal object S4W04MerchantWhatIfSummary {
    enum class Rejection {
        MISMATCHED_FENCE, INVALID_BASE, INVALID_SCENARIO, INVALID_COVERAGE, OVERFLOW,
    }

    enum class GroupCoverage { FULLY_MODELED, PARTIAL }
    enum class Coverage { ALL_MODELED, PARTIAL }
    enum class CashbackStatus { UNVERIFIED_NOT_APPLIED }

    data class Row(
        val key: GroupKey,
        val observedBaseSubtotalMinor: Long,
        val modeledBaseMinor: Long,
        val modeledHypotheticalDiscountMinor: Long,
        val modeledHypotheticalAfterMinor: Long,
        val selectedNeedIds: List<HouseholdNeedId>,
        val modeledNeedIds: List<HouseholdNeedId>,
        val groupCoverage: GroupCoverage,
    )

    sealed interface Decision {
        data class Summary(
            val cartFingerprint: String,
            val sourceWatermark: String,
            val asOfEpochMillis: Long,
            val rows: List<Row>,
            val missingNeedIds: List<HouseholdNeedId>,
            val unmodeledNeedIds: List<HouseholdNeedId>,
            val coverage: Coverage,
        ) : Decision {
            val cashbackStatus = CashbackStatus.UNVERIFIED_NOT_APPLIED
            val notice = "HISTORICAL_WHAT_IF_NOT_CURRENT_QUOTE_OR_REDEEMED_DISCOUNT"
            val warning = "NO_FINANCE_OR_CASHBACK_APPLY"
        }

        data class Rejected(val reason: Rejection) : Decision
    }

    private val currencyCode = Regex("[A-Z]{3}")
    private val groupOrder = compareBy<GroupKey>(
        { it.currencyCode }, { it.merchant }, { it.localityCode }, { it.paymentMethod.name },
    )

    fun summarize(base: Estimate, scenario: Scenario): Decision {
        if (base.cartFingerprint.isBlank() || base.sourceWatermark.isBlank() ||
            base.asOfEpochMillis < 0L || base.cartFingerprint != scenario.cartFingerprint ||
            base.sourceWatermark != scenario.sourceWatermark ||
            base.asOfEpochMillis != scenario.asOfEpochMillis
        ) return Decision.Rejected(Rejection.MISMATCHED_FENCE)

        return try {
            summarizeChecked(base, scenario)
        } catch (_: ArithmeticException) {
            Decision.Rejected(Rejection.OVERFLOW)
        }
    }

    private fun summarizeChecked(base: Estimate, scenario: Scenario): Decision {
        val baseByNeed = base.lines.associateBy { it.needId }
        if (base.lines.size !in 1..200 || baseByNeed.size != base.lines.size ||
            base.lines.any { !validBaseLine(it, base.asOfEpochMillis) }
        ) return Decision.Rejected(Rejection.INVALID_BASE)

        val observedGroups = base.lines.groupBy { it.groupKey() }
        val observedTotals = observedGroups.mapValues { (_, lines) ->
            lines.fold(0L) { sum, line -> Math.addExact(sum, line.estimatedMinor) }
        }
        if (base.subtotals.size != observedTotals.size ||
            base.subtotals.associate { it.key to it.estimatedMinor } != observedTotals
        ) return Decision.Rejected(Rejection.INVALID_BASE)

        val simulatedByNeed = scenario.lines.associateBy { it.needId }
        if (scenario.lines.size !in 1..200 || simulatedByNeed.size != scenario.lines.size ||
            scenario.notice != "WHAT_IF_HISTORICAL_ONLY_NOT_ELIGIBILITY_OR_REDEMPTION" ||
            scenario.warning != "NOT_CURRENT_PRICE_OR_CASHBACK" ||
            scenario.lines.any { !validSimulation(it, baseByNeed[it.needId]) }
        ) return Decision.Rejected(Rejection.INVALID_SCENARIO)

        val modeledGroups = scenario.lines.groupBy { it.groupKey }
        val modeledTotals = modeledGroups.mapValues { (key, lines) ->
            lines.fold(GroupSubtotal(key, 0L, 0L, 0L)) { subtotal, line ->
                GroupSubtotal(
                    key,
                    Math.addExact(subtotal.modeledBaseMinor, line.originalEstimatedMinor),
                    Math.addExact(subtotal.hypotheticalDiscountMinor, line.hypotheticalDiscountMinor),
                    Math.addExact(subtotal.hypotheticalAfterMinor, line.hypotheticalAfterMinor),
                )
            }
        }
        if (scenario.groups.size != modeledTotals.size ||
            scenario.groups.associateBy { it.key } != modeledTotals
        ) return Decision.Rejected(Rejection.INVALID_SCENARIO)

        val missing = base.missingNeedIds
        val unmodeled = (missing + baseByNeed.keys.filterNot { it in simulatedByNeed })
            .sortedBy { it.value }
        val baseCompleteness = if (missing.isEmpty()) {
            S4W04ObservedCartBudget.Completeness.ALL_SELECTED
        } else {
            S4W04ObservedCartBudget.Completeness.PARTIAL
        }
        val scenarioCompleteness = if (unmodeled.isEmpty()) {
            S4W04ObservedPromotionScenario.Completeness.MODELED_ALL_SELECTED
        } else {
            S4W04ObservedPromotionScenario.Completeness.PARTIAL
        }
        if (missing.size > 200 - baseByNeed.size ||
            missing != missing.distinct().sortedBy { it.value } ||
            missing.any { it in baseByNeed } || scenario.unmodeledNeedIds != unmodeled ||
            base.completeness != baseCompleteness || scenario.completeness != scenarioCompleteness
        ) return Decision.Rejected(Rejection.INVALID_COVERAGE)

        val rows = observedGroups.keys.sortedWith(groupOrder).map { key ->
            val selected = observedGroups.getValue(key).map { it.needId }.sortedBy { it.value }
            val modeled = modeledGroups[key].orEmpty().map { it.needId }.sortedBy { it.value }
            val subtotal = modeledTotals[key]
            Row(
                key, observedTotals.getValue(key),
                subtotal?.modeledBaseMinor ?: 0L,
                subtotal?.hypotheticalDiscountMinor ?: 0L,
                subtotal?.hypotheticalAfterMinor ?: 0L,
                Collections.unmodifiableList(selected),
                Collections.unmodifiableList(modeled),
                if (selected.size == modeled.size) GroupCoverage.FULLY_MODELED else GroupCoverage.PARTIAL,
            )
        }
        return Decision.Summary(
            base.cartFingerprint, base.sourceWatermark, base.asOfEpochMillis,
            Collections.unmodifiableList(rows),
            Collections.unmodifiableList(missing.toList()),
            Collections.unmodifiableList(unmodeled),
            if (unmodeled.isEmpty()) Coverage.ALL_MODELED else Coverage.PARTIAL,
        )
    }

    private fun EstimatedLine.groupKey() = GroupKey(currencyCode, merchant, localityCode, paymentMethod)

    private fun validBaseLine(line: EstimatedLine, asOf: Long): Boolean =
        line.productKey.isNotBlank() && line.currencyCode.matches(currencyCode) &&
            line.merchant.isNotBlank() && line.localityCode.isNotBlank() &&
            line.paymentMethod != CommercialPaymentMethod.UNKNOWN &&
            validSourceRef(line.sourceRef) && line.observedAtEpochMillis in 0L..asOf &&
            line.requestedPackCount in 1..10_000 && line.observedAmountMinor > 0L &&
            Math.multiplyExact(line.observedAmountMinor, line.requestedPackCount.toLong()) ==
                line.estimatedMinor

    private fun validSourceRef(value: String): Boolean = value.isNotBlank() && value.length <= 1024

    private fun validSimulation(line: SimulatedLine, base: EstimatedLine?): Boolean {
        if (base == null || line.productKey != base.productKey || line.groupKey != base.groupKey() ||
            line.baseSourceRef != base.sourceRef || !validSourceRef(line.promotionSourceRef) ||
            !validSourceRef(line.promotionalObservationRef) ||
            line.requestedPackCount != base.requestedPackCount ||
            line.originalEstimatedMinor != base.estimatedMinor || line.promotionValue <= 0L
        ) return false

        val amount = base.estimatedMinor
        val discount = when (line.promotionKind) {
            CommercialPromotionKind.DISCOUNT_PERCENT -> {
                val bps = line.promotionValue
                if (bps > 10_000L) return false
                // Split before multiplying to keep the basis-point calculation in Long.
                (amount / 10_000L) * bps + (amount % 10_000L) * bps / 10_000L
            }
            CommercialPromotionKind.FIXED_AMOUNT -> minOf(amount, line.promotionValue)
            else -> return false
        }
        return line.hypotheticalDiscountMinor == discount &&
            line.hypotheticalAfterMinor == amount - discount
    }
}
