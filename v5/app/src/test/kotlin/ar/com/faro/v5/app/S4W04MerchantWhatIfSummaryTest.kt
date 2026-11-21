package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.CashbackStatus
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Rejection
import ar.com.faro.v5.app.S4W04ObservedCartBudget.Estimate
import ar.com.faro.v5.app.S4W04ObservedCartBudget.EstimatedLine
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.app.S4W04ObservedPromotionScenario.Scenario
import ar.com.faro.v5.app.S4W04ObservedPromotionScenario.SimulatedLine
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPaymentMethod.CASH
import ar.com.faro.v5.evidence.CommercialPaymentMethod.DEBIT
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.CommercialPromotionKind.DISCOUNT_PERCENT
import ar.com.faro.v5.evidence.CommercialPromotionKind.FIXED_AMOUNT
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class S4W04MerchantWhatIfSummaryTest {
    private val first = line("a", amount = 12_000L, packs = 2)

    @Test
    fun rejectsMismatchedOrInvalidFences() {
        val base = estimate(listOf(first))
        val offer = scenario(base, listOf(promotion(first)))
        for (changed in listOf(
            offer.copy(cartFingerprint = "different"),
            offer.copy(sourceWatermark = "different"),
            offer.copy(asOfEpochMillis = 101),
        )) {
            rejects(Rejection.MISMATCHED_FENCE, base, changed)
        }
        rejects(Rejection.MISMATCHED_FENCE, base.copy(cartFingerprint = ""), offer.copy(cartFingerprint = ""))
        rejects(Rejection.MISMATCHED_FENCE, base.copy(sourceWatermark = " "), offer.copy(sourceWatermark = " "))
        rejects(Rejection.MISMATCHED_FENCE, base.copy(asOfEpochMillis = -1), offer.copy(asOfEpochMillis = -1))
    }

    @Test
    fun rejectsForgedBaseAmountsSubtotalsAndDuplicateNeeds() {
        val base = estimate(listOf(first))
        val offer = scenario(base, listOf(promotion(first)))
        val subtotal = base.subtotals.single()
        for (changed in listOf(
            base.copy(lines = listOf(first.copy(estimatedMinor = 24_001L))),
            base.copy(lines = listOf(first.copy(requestedPackCount = 0))),
            base.copy(lines = listOf(first, first)),
            base.copy(subtotals = listOf(subtotal.copy(estimatedMinor = -1L))),
            base.copy(subtotals = listOf(subtotal, subtotal)),
        )) {
            rejects(Rejection.INVALID_BASE, changed, offer)
        }
    }

    @Test
    fun rejectsForgedScenarioContextAmountsAndGroups() {
        val base = estimate(listOf(first))
        val promo = promotion(first)
        val offer = scenario(base, listOf(promo))
        val key = promo.groupKey
        for (changed in listOf(
            promo.copy(needId = HouseholdNeedId("unknown")),
            promo.copy(productKey = "other"),
            promo.copy(groupKey = key.copy(currencyCode = "USD")),
            promo.copy(groupKey = key.copy(merchant = "other")),
            promo.copy(groupKey = key.copy(localityCode = "OTHER")),
            promo.copy(groupKey = key.copy(paymentMethod = DEBIT)),
            promo.copy(baseSourceRef = "other"),
            promo.copy(requestedPackCount = 1),
            promo.copy(originalEstimatedMinor = 12_000L),
            promo.copy(hypotheticalDiscountMinor = 4_800L, hypotheticalAfterMinor = 19_200L),
            promo.copy(hypotheticalAfterMinor = 21_601L),
        )) {
            rejects(Rejection.INVALID_SCENARIO, base, scenario(base, listOf(changed)))
        }
        rejects(Rejection.INVALID_SCENARIO, base, offer.copy(lines = listOf(promo, promo)))
        val group = offer.groups.single()
        rejects(Rejection.INVALID_SCENARIO, base, offer.copy(groups = listOf(group, group)))
        rejects(Rejection.INVALID_SCENARIO, base, offer.copy(groups = listOf(group.copy(modeledBaseMinor = 0L))))
        rejects(Rejection.INVALID_SCENARIO, base, offer.copy(notice = "CURRENT_QUOTE"))
    }

    @Test
    fun ordersSeparateContextsWithoutCombiningCurrenciesOrRankingPrices() {
        val second = line("b", amount = 400L)
        val lines = listOf(
            line("f", currency = "USD", amount = 1L),
            line("e", merchant = "Beta", amount = 2L),
            line("d", locality = "ST", amount = 3L),
            line("c", payment = DEBIT, amount = 4L),
            second,
            first,
        )
        val base = estimate(lines)
        val offer = scenario(base, lines.map { promotion(it) })
        val result = summarize(base, offer)
        assertEquals(listOf(24_400L, 4L, 3L, 2L, 1L), result.rows.map { it.observedBaseSubtotalMinor })
        assertEquals(listOf(first.needId, second.needId), result.rows.first().selectedNeedIds)
        assertEquals(Coverage.ALL_MODELED, result.coverage)
        assertEquals(List(5) { GroupCoverage.FULLY_MODELED }, result.rows.map { it.groupCoverage })
        assertEquals(result, summarize(
            base.copy(lines = base.lines.reversed(), subtotals = base.subtotals.reversed()),
            offer.copy(lines = offer.lines.reversed(), groups = offer.groups.reversed()),
        ))
    }

    @Test
    fun preservesMissingAndUnmodeledNeedsWithoutAssigningThemPromotions() {
        val missing = HouseholdNeedId("missing")
        val second = line("b")
        val third = line("c", merchant = "Beta")
        val base = estimate(listOf(third, second, first), listOf(missing))
        val offer = scenario(base, listOf(promotion(first)))
        val result = summarize(base, offer)
        assertEquals(Coverage.PARTIAL, result.coverage)
        assertEquals(listOf(missing), result.missingNeedIds)
        assertEquals(listOf(second.needId, third.needId, missing), result.unmodeledNeedIds)
        val (partial, withoutPromotion) = result.rows
        assertEquals(GroupCoverage.PARTIAL, partial.groupCoverage)
        assertEquals(24_001L, partial.observedBaseSubtotalMinor)
        assertEquals(24_000L, partial.modeledBaseMinor)
        assertEquals(listOf(first.needId), partial.modeledNeedIds)
        assertEquals(GroupCoverage.PARTIAL, withoutPromotion.groupCoverage)
        assertEquals(listOf(third.needId), withoutPromotion.selectedNeedIds)
        assertEquals(emptyList<HouseholdNeedId>(), withoutPromotion.modeledNeedIds)
        rejects(Rejection.INVALID_COVERAGE, base, offer.copy(unmodeledNeedIds = listOf(missing, missing)))
        rejects(Rejection.INVALID_COVERAGE, base, offer.copy(unmodeledNeedIds = offer.unmodeledNeedIds.reversed()))
        rejects(Rejection.INVALID_COVERAGE, base.copy(missingNeedIds = listOf(first.needId)), offer)
        rejects(Rejection.INVALID_COVERAGE, base.copy(completeness = S4W04ObservedCartBudget.Completeness.ALL_SELECTED), offer)
    }

    @Test
    fun separatesPercentageRoundedDownAndFixedDiscountAppliedOnce() {
        val fixed = line("b", amount = 500L, packs = 2)
        val capped = line("c", amount = 7L, packs = 2)
        val rounded = line("d", amount = 19L)
        val base = estimate(listOf(first, fixed, capped, rounded))
        val choices = listOf(
            promotion(first),
            promotion(fixed, kind = FIXED_AMOUNT, value = 400L, discount = 400L),
            promotion(capped, kind = FIXED_AMOUNT, value = 20L, discount = 14L),
            promotion(rounded, discount = 1L),
        )
        val offer = scenario(base, choices)
        val row = summarize(base, offer).rows.single()
        val firstOnly = estimate(listOf(first))
        val percentRow = summarize(firstOnly, scenario(firstOnly, listOf(choices.first()))).rows.single()
        assertEquals(2_400L, percentRow.modeledHypotheticalDiscountMinor)
        assertEquals(21_600L, percentRow.modeledHypotheticalAfterMinor)
        assertEquals(25_033L, row.modeledBaseMinor)
        assertEquals(2_815L, row.modeledHypotheticalDiscountMinor)
        assertEquals(22_218L, row.modeledHypotheticalAfterMinor)
        val multiplied = choices[1].copy(hypotheticalDiscountMinor = 800L, hypotheticalAfterMinor = 200L)
        rejects(Rejection.INVALID_SCENARIO, base, scenario(base, choices.toMutableList().apply { this[1] = multiplied }))
    }

    @Test
    fun rejectsOverflowBeforePublishingAnyRow() {
        val huge = line("max", amount = Long.MAX_VALUE)
        val extra = line("extra")
        val tooLarge = estimate(listOf(huge)).copy(lines = listOf(huge, extra))
        val offer = scenario(tooLarge, listOf(promotion(huge, kind = FIXED_AMOUNT, value = 1L, discount = 1L)))
        rejects(Rejection.OVERFLOW, tooLarge, offer)
        rejects(Rejection.OVERFLOW, tooLarge.copy(lines = listOf(huge.copy(requestedPackCount = 2))), offer)
    }

    @Test
    fun handlesOpaqueReferencesAndPublishesImmutableListsAndNonMonetaryCashbackStatus() {
        val opaque = first.copy(sourceRef = "https://evidence.invalid/item?q=one%2Ftwo")
        val missing = mutableListOf(HouseholdNeedId("missing"))
        val base = estimate(listOf(opaque), missing)
        val choice = promotion(opaque).copy(
            promotionSourceRef = "https://offer.invalid/redeem",
            promotionalObservationRef = "opaque:source?key=data",
        )
        val offer = scenario(base, listOf(choice))
        val result = summarize(base, offer)
        val row = result.rows.single()
        missing.clear()
        assertEquals(listOf(HouseholdNeedId("missing")), result.missingNeedIds)
        assertEquals(CashbackStatus.UNVERIFIED_NOT_APPLIED, result.cashbackStatus)
        assertEquals("HISTORICAL_WHAT_IF_NOT_CURRENT_QUOTE_OR_REDEEMED_DISCOUNT", result.notice)
        assertEquals("NO_FINANCE_OR_CASHBACK_APPLY", result.warning)
        assertThrows(UnsupportedOperationException::class.java) { (result.rows as MutableList<S4W04MerchantWhatIfSummary.Row>).clear() }
        for (ids in listOf(result.missingNeedIds, result.unmodeledNeedIds, row.selectedNeedIds, row.modeledNeedIds)) {
            assertThrows(UnsupportedOperationException::class.java) { (ids as MutableList<HouseholdNeedId>).clear() }
        }
        assertEquals(listOf(choice), offer.lines)
        assertEquals(listOf(opaque), base.lines)
    }

    private fun summarize(base: Estimate, offer: Scenario): Decision.Summary =
        S4W04MerchantWhatIfSummary.summarize(base, offer) as Decision.Summary

    private fun rejects(reason: Rejection, base: Estimate, offer: Scenario) {
        assertEquals(Decision.Rejected(reason), S4W04MerchantWhatIfSummary.summarize(base, offer))
    }

    private fun line(
        id: String,
        currency: String = "ARS",
        merchant: String = "Alfa",
        locality: String = "SF",
        payment: CommercialPaymentMethod = CASH,
        amount: Long = 1L,
        packs: Int = 1,
    ) = EstimatedLine(
        HouseholdNeedId(id), "product-$id", merchant, currency, locality, payment,
        90L, "source:$id", packs, amount, amount * packs,
    )

    private fun EstimatedLine.key() = GroupKey(currencyCode, merchant, localityCode, paymentMethod)

    private fun estimate(lines: List<EstimatedLine>, missing: List<HouseholdNeedId> = emptyList()): Estimate {
        val subtotals = lines.groupBy { it.key() }.map { (key, group) ->
            S4W04ObservedCartBudget.GroupSubtotal(key, group.sumOf { it.estimatedMinor })
        }
        return Estimate(
            "cart", "watermark", 100L,
            if (missing.isEmpty()) S4W04ObservedCartBudget.Completeness.ALL_SELECTED
            else S4W04ObservedCartBudget.Completeness.PARTIAL,
            missing, lines, subtotals, "OBSERVED_ESTIMATE_ONLY_NOT_CURRENT",
        )
    }

    private fun promotion(
        line: EstimatedLine,
        kind: CommercialPromotionKind = DISCOUNT_PERCENT,
        value: Long = 1_000L,
        discount: Long = line.estimatedMinor / 10L,
    ) = SimulatedLine(
        line.needId, line.productKey, line.key(), line.sourceRef, "promo-source", "promo-observation",
        kind, value, line.requestedPackCount, line.estimatedMinor, discount, line.estimatedMinor - discount,
    )

    private fun scenario(base: Estimate, lines: List<SimulatedLine>): Scenario {
        val modeledIds = lines.map { it.needId }.toSet()
        val unmodeled = (base.missingNeedIds + base.lines.map { it.needId }.filterNot { it in modeledIds })
            .sortedBy { it.value }
        val subtotals = lines.groupBy { it.groupKey }.map { (key, group) ->
            S4W04ObservedPromotionScenario.GroupSubtotal(
                key, group.sumOf { it.originalEstimatedMinor }, group.sumOf { it.hypotheticalDiscountMinor },
                group.sumOf { it.hypotheticalAfterMinor },
            )
        }
        return Scenario(
            base.cartFingerprint, base.sourceWatermark, base.asOfEpochMillis,
            if (unmodeled.isEmpty()) S4W04ObservedPromotionScenario.Completeness.MODELED_ALL_SELECTED
            else S4W04ObservedPromotionScenario.Completeness.PARTIAL,
            unmodeled, lines, subtotals,
        )
    }
}
