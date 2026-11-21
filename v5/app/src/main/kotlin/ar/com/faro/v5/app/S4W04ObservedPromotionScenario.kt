package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/**
 * D2B STAGED DEV: hypothetical promotion math, NOT redemption, current price,
 * a validated immediate discount, cashback, checkout or Finance authority.
 */
internal object S4W04ObservedPromotionScenario {
    data class ExplicitPromotionChoice(
        val needId: HouseholdNeedId,
        val productKey: String,
        val selectedPromotion: CommercialObservedHistoryEntry,
        val cartFingerprint: String,
        val sourceWatermark: String,
        val asOfEpochMillis: Long,
    )

    data class SimulatedLine(
        val needId: HouseholdNeedId,
        val productKey: String,
        val groupKey: S4W04ObservedCartBudget.GroupKey,
        val baseSourceRef: String,
        val promotionSourceRef: String,
        val promotionalObservationRef: String,
        val promotionKind: CommercialPromotionKind,
        val promotionValue: Long,
        val requestedPackCount: Int,
        val originalEstimatedMinor: Long,
        val hypotheticalDiscountMinor: Long,
        val hypotheticalAfterMinor: Long,
    )

    data class GroupSubtotal(
        val key: S4W04ObservedCartBudget.GroupKey,
        val modeledBaseMinor: Long,
        val hypotheticalDiscountMinor: Long,
        val hypotheticalAfterMinor: Long,
    )

    enum class Completeness { PARTIAL, MODELED_ALL_SELECTED }

    data class Scenario(
        val cartFingerprint: String,
        val sourceWatermark: String,
        val asOfEpochMillis: Long,
        val completeness: Completeness,
        val unmodeledNeedIds: List<HouseholdNeedId>,
        val lines: List<SimulatedLine>,
        val groups: List<GroupSubtotal>,
        val notice: String = "WHAT_IF_HISTORICAL_ONLY_NOT_ELIGIBILITY_OR_REDEMPTION",
        val warning: String = "NOT_CURRENT_PRICE_OR_CASHBACK",
    )

    enum class Rejection {
        INVALID_BASE,
        UNKNOWN_OR_DUPLICATE_SELECTION,
        MISMATCHED_FENCE,
        AMBIGUOUS_HISTORY,
        INELIGIBLE_PROMOTION,
        CONTEXT_MISMATCH,
        OVERFLOW,
    }

    sealed interface Decision {
        data class Simulated(val scenario: Scenario) : Decision
        data class Rejected(val reason: Rejection) : Decision
    }

    fun simulate(
        coverageView: CommercialCartCoverageView,
        baseEstimate: S4W04ObservedCartBudget.Estimate,
        explicitChoices: List<ExplicitPromotionChoice>,
    ): Decision {
        if (coverageView.cartFingerprint != baseEstimate.cartFingerprint ||
            coverageView.sourceWatermark != baseEstimate.sourceWatermark ||
            coverageView.asOfEpochMillis != baseEstimate.asOfEpochMillis
        ) return rejected(Rejection.MISMATCHED_FENCE)

        // Reconstruct the complete base estimate using exact, unique D1 observations.
        // Call D2A again instead of trusting caller-produced subtotals or notices.
        val coverageByNeed = coverageView.lines.associateBy { it.needId }
        if (coverageView.lines.size !in 1..200 ||
            coverageByNeed.size != coverageView.lines.size ||
            baseEstimate.lines.size > coverageView.lines.size ||
            baseEstimate.lines.map { it.needId }.toSet().size != baseEstimate.lines.size
        ) return rejected(Rejection.INVALID_BASE)

        val baseChoices = mutableListOf<S4W04ObservedCartBudget.ExplicitChoice>()
        val originalByNeed = mutableMapOf<HouseholdNeedId, CommercialObservedHistoryEntry>()
        for (estimatedLine in baseEstimate.lines) {
            val observedLine = coverageByNeed[estimatedLine.needId]
                ?: return rejected(Rejection.INVALID_BASE)
            if (observedLine.productKey.value != estimatedLine.productKey ||
                observedLine.requestedPackCount != estimatedLine.requestedPackCount
            ) return rejected(Rejection.INVALID_BASE)
            val matching = observedLine.history.filter { entry ->
                val obs = entry.item.observation
                val terms = entry.item.terms
                entry.comparisonStatus ==
                    CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED &&
                    obs.productKey == estimatedLine.productKey &&
                    obs.merchant == estimatedLine.merchant &&
                    obs.currencyCode == estimatedLine.currencyCode &&
                    obs.sourceRef == estimatedLine.sourceRef &&
                    obs.observedAtEpochMillis == estimatedLine.observedAtEpochMillis &&
                    obs.amountMinor == estimatedLine.observedAmountMinor &&
                    terms?.localityCode == estimatedLine.localityCode &&
                    terms?.paymentMethod == estimatedLine.paymentMethod
            }
            if (matching.size != 1) return rejected(Rejection.INVALID_BASE)
            val original = matching.single()
            originalByNeed[estimatedLine.needId] = original
            baseChoices += S4W04ObservedCartBudget.ExplicitChoice(
                estimatedLine.needId, estimatedLine.productKey, original,
                coverageView.cartFingerprint, coverageView.sourceWatermark,
                coverageView.asOfEpochMillis,
            )
        }
        val reconstructed = S4W04ObservedCartBudget.estimate(coverageView, baseChoices)
        if (reconstructed !is S4W04ObservedCartBudget.Decision.ObservedEstimate ||
            reconstructed.estimate != baseEstimate
        ) return rejected(Rejection.INVALID_BASE)

        if (explicitChoices.size !in 1..200 ||
            explicitChoices.size > baseEstimate.lines.size ||
            explicitChoices.map { it.needId }.toSet().size != explicitChoices.size
        ) return rejected(Rejection.UNKNOWN_OR_DUPLICATE_SELECTION)

        val baseLines = baseEstimate.lines.associateBy { it.needId }
        if (explicitChoices.any { it.needId !in baseLines }) {
            return rejected(Rejection.UNKNOWN_OR_DUPLICATE_SELECTION)
        }

        val simulations = mutableListOf<SimulatedLine>()
        for (choice in explicitChoices.sortedBy { it.needId.value }) {
            if (choice.cartFingerprint != coverageView.cartFingerprint ||
                choice.sourceWatermark != coverageView.sourceWatermark ||
                choice.asOfEpochMillis != coverageView.asOfEpochMillis
            ) return rejected(Rejection.MISMATCHED_FENCE)
            val base = checkNotNull(baseLines[choice.needId])
            if (choice.productKey != base.productKey) return rejected(Rejection.CONTEXT_MISMATCH)
            val line = checkNotNull(coverageByNeed[choice.needId])
            if (line.truncated) return rejected(Rejection.INELIGIBLE_PROMOTION)
            if (line.history.count { it == choice.selectedPromotion } != 1) {
                return rejected(Rejection.AMBIGUOUS_HISTORY)
            }
            val entry = choice.selectedPromotion
            val observation = entry.item.observation
            val terms = entry.item.terms ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val originalTerms = originalByNeed[choice.needId]?.item?.terms
                ?: return rejected(Rejection.INVALID_BASE)

            // Cross-module nullable properties must be copied to local vals before checks.
            val observedAt = observation.observedAtEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val issuedAt = terms.issuedAtEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val from = terms.validFromEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val until = terms.validUntilEpochMillis
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val quantity = terms.presentationQuantityThousandths
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val promoValue = terms.promotionValue
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            val promoSource = terms.promotionSourceRef
                ?: return rejected(Rejection.INELIGIBLE_PROMOTION)
            if (entry.comparisonStatus !=
                    CommercialObservationComparisonStatus.PROMOTION_NOT_BASE ||
                terms.localityKind != CommercialLocalityKind.KNOWN ||
                terms.localityCode == null ||
                terms.eligibility != CommercialEligibility.GENERAL ||
                terms.conditions != null ||
                terms.packCount != 1 ||
                terms.presentationUnit == null || quantity <= 0L ||
                terms.paymentMethod == CommercialPaymentMethod.UNKNOWN ||
                promoValue <= 0L || promoSource.isBlank() || promoSource.length > 1024 ||
                observation.sourceRef.isBlank() || observation.sourceRef.length > 1024 ||
                observedAt < 0L || observedAt > coverageView.asOfEpochMillis ||
                issuedAt > coverageView.asOfEpochMillis ||
                coverageView.maxObservationAgeMillis < 1L ||
                coverageView.asOfEpochMillis - observedAt >
                    coverageView.maxObservationAgeMillis ||
                from > coverageView.asOfEpochMillis ||
                until < coverageView.asOfEpochMillis ||
                terms.promotionKind !in setOf(
                    CommercialPromotionKind.DISCOUNT_PERCENT,
                    CommercialPromotionKind.FIXED_AMOUNT,
                ) || (terms.promotionKind == CommercialPromotionKind.DISCOUNT_PERCENT &&
                    promoValue > 10_000L)
            ) return rejected(Rejection.INELIGIBLE_PROMOTION)

            if (observation.productKey != base.productKey ||
                observation.merchant != base.merchant ||
                observation.currencyCode != base.currencyCode ||
                observation.amountMinor != base.observedAmountMinor ||
                terms.localityCode != base.localityCode ||
                terms.paymentMethod != base.paymentMethod ||
                terms.presentationQuantityThousandths !=
                    originalTerms.presentationQuantityThousandths ||
                terms.presentationUnit != originalTerms.presentationUnit ||
                originalTerms.packCount != 1
            ) return rejected(Rejection.CONTEXT_MISMATCH)

            val originalMinor = base.estimatedMinor
            if (originalMinor <= 0L) return rejected(Rejection.INVALID_BASE)
            val hypotheticalDiscount = when (terms.promotionKind) {
                CommercialPromotionKind.DISCOUNT_PERCENT -> {
                    val baseBlocks = originalMinor / 10_000L
                    val remainder = originalMinor % 10_000L
                    // Since bps <= 10000, this cannot exceed originalMinor.
                    baseBlocks * promoValue + remainder * promoValue / 10_000L
                }
                CommercialPromotionKind.FIXED_AMOUNT -> minOf(originalMinor, promoValue)
                else -> return rejected(Rejection.INELIGIBLE_PROMOTION)
            }
            if (hypotheticalDiscount < 0L || hypotheticalDiscount > originalMinor) {
                return rejected(Rejection.OVERFLOW)
            }
            val key = S4W04ObservedCartBudget.GroupKey(
                base.currencyCode, base.merchant, base.localityCode, base.paymentMethod,
            )
            simulations += SimulatedLine(
                base.needId, base.productKey, key, base.sourceRef, promoSource,
                observation.sourceRef, terms.promotionKind, promoValue,
                base.requestedPackCount, originalMinor, hypotheticalDiscount,
                originalMinor - hypotheticalDiscount,
            )
        }

        val groups = mutableMapOf<S4W04ObservedCartBudget.GroupKey, GroupSubtotal>()
        for (line in simulations) {
            val existing = groups[line.groupKey]
            val combined = try {
                GroupSubtotal(
                    line.groupKey,
                    Math.addExact(existing?.modeledBaseMinor ?: 0L, line.originalEstimatedMinor),
                    Math.addExact(existing?.hypotheticalDiscountMinor ?: 0L,
                        line.hypotheticalDiscountMinor),
                    Math.addExact(existing?.hypotheticalAfterMinor ?: 0L, line.hypotheticalAfterMinor),
                )
            } catch (_: ArithmeticException) {
                return rejected(Rejection.OVERFLOW)
            }
            groups[line.groupKey] = combined
        }
        val unmodeled = coverageView.lines.map { it.needId }
            .filterNot { id -> simulations.any { it.needId == id } }
            .sortedBy { it.value }
        val completeness = if (unmodeled.isEmpty() &&
            baseEstimate.completeness == S4W04ObservedCartBudget.Completeness.ALL_SELECTED
        ) Completeness.MODELED_ALL_SELECTED else Completeness.PARTIAL
        val sortedGroups = groups.values.sortedWith(compareBy(
            { it.key.currencyCode }, { it.key.merchant },
            { it.key.localityCode }, { it.key.paymentMethod.name },
        ))
        return Decision.Simulated(Scenario(
            coverageView.cartFingerprint, coverageView.sourceWatermark,
            coverageView.asOfEpochMillis, completeness,
            Collections.unmodifiableList(unmodeled),
            Collections.unmodifiableList(simulations),
            Collections.unmodifiableList(sortedGroups),
        ))
    }

    private fun rejected(reason: Rejection) = Decision.Rejected(reason)
}
