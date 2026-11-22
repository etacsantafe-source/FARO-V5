package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision.RecheckedForDisplay
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.Decision
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.Rejection
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.Warning
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage as PreviewCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.Estimate
import ar.com.faro.v5.app.S4W04ObservedCartBudget.ExplicitChoice
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableCommercialTypedObservation
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04ExplicitObservationTraceTest {
    private val terms = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
        validFromEpochMillis = 700L, validUntilEpochMillis = 1200L,
        presentationQuantityThousandths = 1000L, presentationUnit = CommercialPresentationUnit.UNIT,
        packCount = 1, paymentMethod = CommercialPaymentMethod.CASH,
        eligibility = CommercialEligibility.GENERAL, promotionKind = CommercialPromotionKind.NONE,
    )

    private inner class Fixture(count: Int = 2, val packCount: Int = 2) {
        private val needs = List(count) { index ->
            HouseholdNeed(
                HouseholdNeedId("need-%03d".format(index)), HouseholdNeedName("Product $index"),
                HouseholdNeedQuantity(1000L, HouseholdNeedUnit.UNIT),
                HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 2L,
            )
        }
        val draft: S4W04CommercialCartDraft
        val view: CommercialCartCoverageView
        val choices: List<ExplicitChoice>
        val estimate: Estimate
        val preview: Preview

        init {
            val snapshot = HouseholdNeedsSnapshot(7L, needs)
            val fence = CommercialNeedReadFence("gen-1", 11L, "epoch-1", "principal-1")
            val packs = needs.mapIndexed { index, need ->
                val product = CommercialProductKey("sku-$index")
                val candidate = S4W04CommercialNeedProductSelection.select(
                    snapshot, need.id, product, listOf(product), fence, explicitlyChosen = true,
                ) { true } as CommercialNeedProductDecision.Candidate
                S4W04CommercialCartDraft.PackChoice(candidate.binding, packCount)
            }
            draft = (S4W04CommercialCartDraft.build(snapshot, fence, packs) { true }
                as S4W04CommercialCartDraft.Decision.Draft).cartDraft
            val lines = draft.lines.mapIndexed { index, line ->
                val currency = if (index % 2 == 0) "USD" else "ARS"
                val merchant = if (index % 2 == 0) "Market A" else "Market B"
                val item = PortableCommercialTypedObservation(
                    PortableCommercialObservation(
                        line.productKey.value, merchant, currency, 120L, "MANUAL",
                        "manual:first:$index", 900L, 980L,
                    ),
                    terms,
                )
                CommercialCartCoverageLine(
                    line.needId, line.productKey, line.requestedPackCount,
                    listOf(
                        CommercialObservedHistoryEntry(item, COMPARABLE_BASE_OBSERVED),
                        CommercialObservedHistoryEntry(
                            item.copy(observation = item.observation.copy(
                                amountMinor = 175L, sourceRef = "manual:second:$index",
                                observedAtEpochMillis = 915L,
                            )),
                            COMPARABLE_BASE_OBSERVED,
                        ),
                    ),
                    emptyList(), CommercialCartLineCoverageStatus.COMPARABLE_OBSERVED_ONLY, false,
                )
            }
            view = CommercialCartCoverageView(draft.fingerprint, "history-1", 1000L, 200L, lines)
            choices = lines.map { line ->
                ExplicitChoice(line.needId, line.productKey.value, line.history.first(),
                    view.cartFingerprint, view.sourceWatermark, view.asOfEpochMillis)
            }
            estimate = budget(view, choices)
            preview = previewFor(estimate)
        }

        fun trace(
            view: CommercialCartCoverageView = this.view,
            choices: List<ExplicitChoice> = this.choices,
            estimate: Estimate = this.estimate,
            preview: Preview = this.preview,
            proof: RecheckedForDisplay = recheck(preview),
        ) = S4W04ExplicitObservationTrace.trace(draft, view, estimate, choices, preview, proof)
    }

    private fun budget(view: CommercialCartCoverageView, choices: List<ExplicitChoice>): Estimate =
        (S4W04ObservedCartBudget.estimate(view, choices)
            as S4W04ObservedCartBudget.Decision.ObservedEstimate).estimate

    private fun previewFor(estimate: Estimate, modeled: Boolean = true): Preview {
        val rows = estimate.subtotals.map { subtotal ->
            val key = subtotal.key
            val selected = estimate.lines.filter {
                it.currencyCode == key.currencyCode && it.merchant == key.merchant &&
                    it.localityCode == key.localityCode && it.paymentMethod == key.paymentMethod
            }.map { it.needId }
            val base = if (modeled) subtotal.estimatedMinor else 0L
            Row(key, subtotal.estimatedMinor, base, 0L, base, selected,
                if (modeled) selected else emptyList(),
                if (modeled) GroupCoverage.FULLY_MODELED else GroupCoverage.PARTIAL)
        }
        val unmodeled = if (modeled) estimate.missingNeedIds
            else (estimate.missingNeedIds + estimate.lines.map { it.needId }).sortedBy { it.value }
        val summary = Summary(estimate.cartFingerprint, estimate.sourceWatermark,
            estimate.asOfEpochMillis, rows, estimate.missingNeedIds, unmodeled,
            if (unmodeled.isEmpty()) PreviewCoverage.ALL_MODELED else PreviewCoverage.PARTIAL)
        val notes = S4W04DeferredCommercialBenefitNotes.annotate(summary, emptyList())
            as S4W04DeferredCommercialBenefitNotes.Decision.Annotated
        return S4W04CommercialPreReviewEnvelope.preview(summary, notes) as Preview
    }

    private fun recheck(preview: Preview) = RecheckedForDisplay(
        preview.digest, 1020L, preview.sourceWatermark, preview.cartFingerprint,
        preview.rows.sumOf { it.estimate.selectedNeedIds.size },
        if (preview.coverage == PreviewCoverage.PARTIAL) Coverage.PARTIAL else Coverage.OBSERVED_ALL_SELECTED,
    )

    @Test fun t01RejectMismatchedFencesAndRetrogradeCheck() {
        val f = Fixture()
        val proof = recheck(f.preview)
        for (changed in listOf(
            proof.copy(cartFingerprint = "wrong"),
            proof.copy(sourceWatermark = "wrong"),
            proof.copy(previewDigest = "0".repeat(64)),
        )) assertEquals(Decision.Rejected(Rejection.MISMATCHED_FENCE), f.trace(proof = changed))
        assertEquals(Decision.Rejected(Rejection.MISMATCHED_FENCE),
            f.trace(estimate = f.estimate.copy(asOfEpochMillis = 999L)))
        assertEquals(Decision.Rejected(Rejection.MISMATCHED_FENCE),
            f.trace(view = f.view.copy(cartFingerprint = "wrong")))
        assertEquals(Decision.Rejected(Rejection.INVALID_RECHECK),
            f.trace(proof = proof.copy(checkedAtEpochMillis = 999L)))
        assertEquals(Decision.Rejected(Rejection.INVALID_RECHECK),
            f.trace(proof = proof.copy(selectedNeedCount = 1)))
    }

    @Test fun t02RejectAbsentDuplicateAndSubstitutedHistoricalObservation() {
        val f = Fixture()
        val line = f.view.lines.first()
        val first = f.choices.first()
        val entry = first.selectedObservation
        val sameGroup = line.history.last()
        val forged = entry.copy(item = entry.item.copy(terms = terms.copy(validUntilEpochMillis = 1300L)))
        val changedHistory = f.view.copy(lines = listOf(
            line.copy(history = line.history + entry), f.view.lines.last(),
        ))
        for (replacement in listOf(sameGroup, forged)) {
            assertEquals(Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION),
                f.trace(choices = listOf(first.copy(selectedObservation = replacement), f.choices.last())))
        }
        assertEquals(Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION),
            f.trace(view = changedHistory))
    }

    @Test fun t03RejectForgedEstimateAndCoverageLines() {
        val f = Fixture()
        val line = f.estimate.lines.first()
        val last = f.estimate.lines.last()
        for (lines in listOf(
            listOf(line.copy(productKey = "wrong"), last),
            listOf(line.copy(requestedPackCount = 3), last),
            listOf(line.copy(observedAmountMinor = 123L), last),
            listOf(line.copy(estimatedMinor = 242L), last),
            listOf(line, line),
            listOf(last),
        )) assertEquals(Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION),
            f.trace(estimate = f.estimate.copy(lines = lines)))
        val subtotal = f.estimate.subtotals.first()
        assertEquals(Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION),
            f.trace(estimate = f.estimate.copy(subtotals = listOf(
                subtotal.copy(estimatedMinor = 300L), f.estimate.subtotals.last(),
            ))))
        val coverage = f.view.lines.first()
        assertEquals(Decision.Rejected(Rejection.INVALID_COVERAGE_OR_DRAFT),
            f.trace(view = f.view.copy(lines = listOf(coverage.copy(requestedPackCount = 3), f.view.lines.last()))))
    }

    @Test fun t04RejectInconsistentPreviewGroupsSubtotalsAndMissingNeeds() {
        val f = Fixture()
        val first = f.preview.rows.first()
        val last = f.preview.rows.last()
        val row = first.estimate
        for (changed in listOf(
            row.copy(observedBaseSubtotalMinor = 241L),
            row.copy(selectedNeedIds = last.estimate.selectedNeedIds),
            row.copy(key = row.key.copy(localityCode = "AR-X")),
            row.copy(key = row.key.copy(currencyCode = "EUR")),
            row.copy(key = row.key.copy(paymentMethod = CommercialPaymentMethod.DEBIT)),
        )) assertEquals(Decision.Rejected(Rejection.PREVIEW_INCONSISTENT),
            f.trace(preview = f.preview.copy(rows = listOf(first.copy(estimate = changed), last))))
        assertEquals(Decision.Rejected(Rejection.PREVIEW_INCONSISTENT),
            f.trace(preview = f.preview.copy(missingNeedIds = row.selectedNeedIds)))
    }

    @Test fun t05SameTraceAfterReorderingWithoutCurrencyAggregation() {
        val f = Fixture()
        val first = f.trace() as Decision.TracedForDisplay
        val view = f.view.copy(lines = f.view.lines.reversed().map { it.copy(history = it.history.reversed()) })
        assertEquals(first, f.trace(view = view, choices = f.choices.reversed()))
        assertEquals(listOf("USD", "ARS"), first.lines.map { it.groupKey.currencyCode })
        assertEquals(listOf("Market A", "Market B"), first.lines.map { it.groupKey.merchant })
        assertEquals(listOf(240L, 240L), first.lines.map { it.estimatedMinor })
        assertTrue(first.traceDigest.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun t06BindLiteralSourceAndUnprojectedObservationIdentityIntoTrace() {
        val f = Fixture(count = 1)
        val line = f.view.lines.single()
        val choice = f.choices.single()
        val entry = choice.selectedObservation
        val literal = "https://127.0.0.1:1/<tag>?a=b"
        val item = entry.item
        val sameProjected = listOf(
            item.copy(observation = item.observation.copy(sourceKind = "SCRAPED")),
            item.copy(observation = item.observation.copy(acquiredAtEpochMillis = 981L)),
            item.copy(terms = terms.copy(validUntilEpochMillis = 1300L)),
        ).map { entry.copy(item = it) }
        val differentProjected = entry.copy(item = item.copy(observation =
            item.observation.copy(sourceRef = literal, observedAtEpochMillis = 935L, amountMinor = 180L)))
        val view = f.view.copy(lines = listOf(line.copy(history = line.history + sameProjected + differentProjected)))
        val original = f.trace(view = view) as Decision.TracedForDisplay
        val sameDigestInputs = sameProjected.map { selected ->
            val traced = f.trace(view = view, choices = listOf(choice.copy(selectedObservation = selected)))
                as Decision.TracedForDisplay
            assertEquals(original.lines, traced.lines)
            traced.traceDigest
        }
        assertEquals(4, (sameDigestInputs + original.traceDigest).toSet().size)

        val choices = listOf(choice.copy(selectedObservation = differentProjected))
        val estimate = budget(view, choices)
        val preview = previewFor(estimate)
        val trace = f.trace(view, choices, estimate, preview) as Decision.TracedForDisplay
        assertNotEquals(original.traceDigest, trace.traceDigest)
        assertEquals(literal, trace.lines.single().sourceRef)
        assertEquals(935L, trace.lines.single().observedAtEpochMillis)
        assertEquals(180L, trace.lines.single().observedAmountMinor)
        assertEquals(2, trace.lines.single().requestedPackCount)
        assertEquals(Decision.Rejected(Rejection.MISMATCHED_FENCE),
            f.trace(view, choices, estimate, preview, recheck(f.preview)))
    }

    @Test fun t07PartialUnmodeledDisplayKeepsOnlyHistoricalAmounts() {
        val f = Fixture()
        val choices = f.choices.take(1)
        val estimate = budget(f.view, choices)
        val preview = previewFor(estimate, modeled = false)
        val trace = f.trace(choices = choices, estimate = estimate, preview = preview) as Decision.TracedForDisplay
        assertEquals(Coverage.PARTIAL, trace.coverage)
        assertEquals(240L, trace.lines.single().estimatedMinor)
        assertEquals(Warning.entries, trace.warnings)
        assertEquals(Decision.Rejected(Rejection.INVALID_RECHECK),
            f.trace(choices = choices, estimate = estimate, preview = preview,
                proof = recheck(preview).copy(coverage = Coverage.OBSERVED_ALL_SELECTED)))
    }

    @Test fun t08FreezeOutputAcceptAtMost200AndFailClosedOnOverflow() {
        val f = Fixture(count = 200)
        val choices = f.choices.toMutableList()
        val lines = f.view.lines.toMutableList()
        val result = f.trace(view = f.view.copy(lines = lines), choices = choices) as Decision.TracedForDisplay
        val snapshot = result.lines.toList()
        choices.add(choices.first())
        assertEquals(Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION), f.trace(choices = choices))
        choices.clear()
        lines.clear()
        assertEquals(snapshot, result.lines)
        assertEquals(200, result.lines.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.lines as MutableList<*>).clear()
        }

        val one = Fixture(count = 1)
        val choice = one.choices.single()
        val entry = choice.selectedObservation
        val huge = entry.copy(item = entry.item.copy(observation =
            entry.item.observation.copy(amountMinor = Long.MAX_VALUE)))
        val view = one.view.copy(lines = listOf(one.view.lines.single().copy(history = listOf(huge))))
        assertEquals(Decision.Rejected(Rejection.OVERFLOW),
            one.trace(view = view, choices = listOf(choice.copy(selectedObservation = huge))))
    }
}
