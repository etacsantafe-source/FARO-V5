package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision.RecheckedForDisplay
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Decision
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Rejection
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Warning
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.ScenarioScope
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.BenefitKind
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ExplicitNote
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Provenance
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04CommercialReviewPresentationTest {
    private val terms = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
        validFromEpochMillis = 700L, validUntilEpochMillis = 1200L,
        presentationQuantityThousandths = 1000L, presentationUnit = CommercialPresentationUnit.UNIT,
        packCount = 1, paymentMethod = CommercialPaymentMethod.CASH,
        eligibility = CommercialEligibility.GENERAL, promotionKind = CommercialPromotionKind.NONE,
    )

    private inner class Fixture(
        count: Int = 2,
        val packCount: Int = 2,
        singleGroup: Boolean = false,
        source: (Int) -> String = { "manual:first:$it" },
    ) {
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
                val currency = if (singleGroup || index % 2 == 0) "USD" else "ARS"
                val merchant = if (singleGroup || index % 2 == 0) "Market A" else "Market B"
                val item = PortableCommercialTypedObservation(
                    PortableCommercialObservation(
                        line.productKey.value, merchant, currency, 120L, "MANUAL",
                        source(index), 900L, 980L,
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

        fun project(
            view: CommercialCartCoverageView = this.view,
            choices: List<ExplicitChoice> = this.choices,
            estimate: Estimate = this.estimate,
            preview: Preview = this.preview,
            proof: RecheckedForDisplay = recheck(preview),
        ) = S4W04CommercialReviewPresentation.project(draft, view, estimate, choices, preview, proof)
    }

    private fun budget(view: CommercialCartCoverageView, choices: List<ExplicitChoice>): Estimate =
        (S4W04ObservedCartBudget.estimate(view, choices)
            as S4W04ObservedCartBudget.Decision.ObservedEstimate).estimate

    private fun previewFor(
        estimate: Estimate,
        modeledIds: Set<HouseholdNeedId> = estimate.lines.map { it.needId }.toSet(),
        discount: Long = 0L,
        notes: List<ExplicitNote> = emptyList(),
        adjustRow: (Row) -> Row = { it },
    ): Preview {
        val rows = estimate.subtotals.map { subtotal ->
            val key = subtotal.key
            val lines = estimate.lines.filter {
                it.currencyCode == key.currencyCode && it.merchant == key.merchant &&
                    it.localityCode == key.localityCode && it.paymentMethod == key.paymentMethod
            }
            val modeled = lines.filter { it.needId in modeledIds }
            val base = modeled.sumOf { it.estimatedMinor }
            val reduction = if (modeled.isEmpty()) 0L else discount
            adjustRow(Row(
                key, subtotal.estimatedMinor, base, reduction, base - reduction,
                lines.map { it.needId }, modeled.map { it.needId },
                if (modeled.size == lines.size) GroupCoverage.FULLY_MODELED else GroupCoverage.PARTIAL,
            ))
        }
        val unmodeled = (estimate.missingNeedIds + estimate.lines.map { it.needId }
            .filterNot { it in modeledIds }).sortedBy { it.value }
        val summary = Summary(
            estimate.cartFingerprint, estimate.sourceWatermark, estimate.asOfEpochMillis,
            rows, estimate.missingNeedIds, unmodeled,
            if (unmodeled.isEmpty()) PreviewCoverage.ALL_MODELED else PreviewCoverage.PARTIAL,
        )
        val annotated = S4W04DeferredCommercialBenefitNotes.annotate(summary, notes)
            as S4W04DeferredCommercialBenefitNotes.Decision.Annotated
        return S4W04CommercialPreReviewEnvelope.preview(summary, annotated) as Preview
    }

    private fun recheck(preview: Preview) = RecheckedForDisplay(
        preview.digest, 1020L, preview.sourceWatermark, preview.cartFingerprint,
        preview.rows.sumOf { it.estimate.selectedNeedIds.size },
        if (preview.coverage == PreviewCoverage.PARTIAL) Coverage.PARTIAL else Coverage.OBSERVED_ALL_SELECTED,
    )

    private fun assertFrozen(values: List<*>) {
        assertThrows(UnsupportedOperationException::class.java) { (values as MutableList<*>).clear() }
    }

    @Test fun t01CanonicalReadOnlyPresentationPreservesTraceAndRecheck() {
        val fixture = Fixture()
        val shown = fixture.project() as Decision.Display
        val trace = S4W04ExplicitObservationTrace.trace(
            fixture.draft, fixture.view, fixture.estimate, fixture.choices,
            fixture.preview, recheck(fixture.preview),
        ) as S4W04ExplicitObservationTrace.Decision.TracedForDisplay

        assertEquals(fixture.preview.digest, shown.previewDigest)
        assertEquals(trace.traceDigest, shown.traceDigest)
        assertEquals(fixture.draft.fingerprint, shown.cartFingerprint)
        assertEquals(fixture.view.sourceWatermark, shown.sourceWatermark)
        assertEquals(1000L, shown.asOfEpochMillis)
        assertEquals(1020L, shown.checkedAtEpochMillis)
        assertEquals(Coverage.OBSERVED_ALL_SELECTED, shown.coverage)
        assertEquals(trace.lines.toSet(), shown.groups.flatMap { it.lines }.toSet())
        assertEquals(Warning.entries, shown.warnings)
        assertFrozen(shown.groups)
        assertFrozen(shown.groups.first().lines)
    }

    @Test fun t02RejectSubstitutedObservationAndTamperedFences() {
        val fixture = Fixture()
        val choice = fixture.choices.first()
        val replacement = fixture.view.lines.first().history.last()
        val invalidTrace = Decision.Rejected(Rejection.INVALID_TRACE)

        assertEquals(invalidTrace, fixture.project(
            choices = listOf(choice.copy(selectedObservation = replacement), fixture.choices.last()),
        ))
        assertEquals(invalidTrace, fixture.project(
            choices = listOf(choice.copy(sourceWatermark = "forged"), fixture.choices.last()),
        ))
        assertEquals(invalidTrace, fixture.project(estimate = fixture.estimate.copy(cartFingerprint = "forged")))
        assertEquals(invalidTrace, fixture.project(view = fixture.view.copy(sourceWatermark = "forged")))
        assertEquals(invalidTrace, fixture.project(proof = recheck(fixture.preview).copy(checkedAtEpochMillis = 999L)))
    }

    @Test fun t03RejectSubstitutedGroupsDuplicateNeedsAndForgedSubtotals() {
        val fixture = Fixture(count = 3, singleGroup = true)
        val original = fixture.preview.rows.single()
        val row = original.estimate
        for (altered in listOf(
            row.copy(key = row.key.copy(merchant = "Other market")),
            row.copy(selectedNeedIds = listOf(row.selectedNeedIds.first(), row.selectedNeedIds.first())),
            row.copy(observedBaseSubtotalMinor = row.observedBaseSubtotalMinor + 1L),
        )) {
            val preview = fixture.preview.copy(rows = listOf(original.copy(estimate = altered)))
            assertEquals(Decision.Rejected(Rejection.GROUP_MISMATCH), fixture.project(preview = preview))
        }

        val changedScenario = row.copy(
            modeledHypotheticalDiscountMinor = 1L,
            modeledHypotheticalAfterMinor = row.modeledBaseMinor - 1L,
        )
        val staleSeal = fixture.preview.copy(rows = listOf(original.copy(estimate = changedScenario)))
        assertEquals(Decision.Rejected(Rejection.INCOMPLETE_OR_INCONSISTENT_DISPLAY),
            fixture.project(preview = staleSeal))

        val inconsistentModeledBase = previewFor(
            fixture.estimate, modeledIds = setOf(row.selectedNeedIds.first()),
        ) { it.copy(modeledBaseMinor = 241L, modeledHypotheticalAfterMinor = 241L) }
        assertEquals(Decision.Rejected(Rejection.GROUP_MISMATCH),
            fixture.project(preview = inconsistentModeledBase))
    }

    @Test fun t04KeepDifferentCurrenciesAndMerchantsSeparateInCanonicalOrder() {
        val fixture = Fixture(count = 3)
        val shown = fixture.project() as Decision.Display
        val reorderedView = fixture.view.copy(lines = fixture.view.lines.reversed())
        val reorderedPreview = fixture.preview.copy(rows = fixture.preview.rows.reversed())
        assertEquals(shown, fixture.project(
            view = reorderedView, choices = fixture.choices.reversed(), preview = reorderedPreview,
        ))
        assertEquals(listOf("ARS", "USD"), shown.groups.map { it.key.currencyCode })
        assertEquals(listOf("Market B", "Market A"), shown.groups.map { it.key.merchant })
        assertEquals(listOf(240L, 480L), shown.groups.map { it.observedHistoricalSubtotalMinor })
        assertTrue(shown.groups.all { group -> group.lines.all { it.groupKey == group.key } })
    }

    @Test fun t05SeparateWhatIfDiscountFromFutureCashbackAndBankConditions() {
        val fixture = Fixture()
        val key = fixture.estimate.subtotals.first().key
        val notes = listOf(
            BenefitKind.CASHBACK_FUTURE to "Possible future cashback",
            BenefitKind.BANK_ELIGIBILITY to "Selected bank cards only",
        ).map { (kind, details) ->
            ExplicitNote(fixture.draft.fingerprint, fixture.view.sourceWatermark,
                fixture.view.asOfEpochMillis, key, kind, details)
        }
        val shown = fixture.project(preview = previewFor(fixture.estimate, discount = 30L, notes = notes))
            as Decision.Display
        val group = shown.groups.single { it.key == key }
        assertEquals(240L, group.observedHistoricalSubtotalMinor)
        assertEquals(S4W04CommercialReviewPresentation.ScenarioDisclosure(240L, 30L, 210L, ScenarioScope.ALL_MODELED),
            group.scenario)
        assertEquals(notes.associate { it.kind to it.details },
            group.unverifiedBenefits.associate { it.kind to it.details })
        assertTrue(group.unverifiedBenefits.all { it.provenance == Provenance.USER_DECLARED_UNVERIFIED })
        assertFrozen(group.unverifiedBenefits)
        assertTrue(shown.groups.single { it.key != key }.unverifiedBenefits.isEmpty())
    }

    @Test fun t06KeepPartialCoverageAndAbsentPromotionsExplicit() {
        val fixture = Fixture(count = 3, singleGroup = true)
        val choices = fixture.choices.take(2)
        val estimate = budget(fixture.view, choices)
        val shown = fixture.project(
            choices = choices, estimate = estimate, preview = previewFor(estimate, modeledIds = emptySet()),
        ) as Decision.Display
        assertEquals(Coverage.PARTIAL, shown.coverage)
        assertEquals(estimate.missingNeedIds, shown.missingNeedIds)
        assertEquals(fixture.choices.map { it.needId }.sortedBy { it.value }, shown.unmodeledNeedIds)
        val group = shown.groups.single()
        assertEquals(2, group.selectedNeedCount)
        assertEquals(480L, group.observedHistoricalSubtotalMinor)
        assertNull(group.scenario)
        assertTrue(group.unverifiedBenefits.isEmpty())

        val partial = fixture.project(
            choices = choices, estimate = estimate,
            preview = previewFor(estimate, modeledIds = setOf(choices.first().needId), discount = 30L),
        ) as Decision.Display
        assertEquals(S4W04CommercialReviewPresentation.ScenarioDisclosure(240L, 30L, 210L, ScenarioScope.PARTIAL),
            partial.groups.single().scenario)
    }

    @Test fun t07KeepMarkupAndUrlsLiteralAndRejectMalformedOrControlText() {
        val literal = "https://127.0.0.1:1/<script>?a=b"
        val fixture = Fixture(count = 1, source = { literal })
        val note = ExplicitNote(
            fixture.draft.fingerprint, fixture.view.sourceWatermark, fixture.view.asOfEpochMillis,
            fixture.estimate.subtotals.single().key, BenefitKind.OTHER_UNVERIFIED, "<b>tag</b>", literal,
        )
        val result = fixture.project(preview = previewFor(fixture.estimate, notes = listOf(note))) as Decision.Display
        val group = result.groups.single()
        assertEquals(literal, group.lines.single().sourceRef)
        assertEquals(literal, group.unverifiedBenefits.single().sourceRef)
        assertEquals("<b>tag</b>", group.unverifiedBenefits.single().details)

        val malformed = Fixture(count = 1, source = { "manual:\ud800" })
        assertEquals(Decision.Rejected(Rejection.INVALID_TRACE), malformed.project())
        val control = Fixture(count = 1, source = { "manual:\u202ehidden" })
        assertEquals(Decision.Rejected(Rejection.INCOMPLETE_OR_INCONSISTENT_DISPLAY), control.project())
    }

    @Test fun t08FreezeTwoHundredLinesAcrossReorderAndRejectOverflow() {
        val fixture = Fixture(count = 200)
        val choices = fixture.choices.reversed().toMutableList()
        val lines = fixture.view.lines.reversed().toMutableList()
        val rows = fixture.preview.rows.reversed().toMutableList()
        val original = fixture.project() as Decision.Display
        val reordered = fixture.project(
            view = fixture.view.copy(lines = lines), choices = choices, preview = fixture.preview.copy(rows = rows),
        ) as Decision.Display
        choices.clear()
        lines.clear()
        rows.clear()
        assertEquals(original, reordered)
        assertEquals(200, reordered.groups.sumOf { it.lines.size })
        reordered.groups.forEach { assertFrozen(it.lines) }
        assertFrozen(reordered.missingNeedIds)
        assertFrozen(reordered.unmodeledNeedIds)

        val one = Fixture(count = 1)
        val choice = one.choices.single()
        val item = choice.selectedObservation.item
        val huge = choice.selectedObservation.copy(
            item = item.copy(observation = item.observation.copy(amountMinor = Long.MAX_VALUE)),
        )
        val view = one.view.copy(lines = listOf(one.view.lines.single().copy(history = listOf(huge))))
        assertEquals(Decision.Rejected(Rejection.OVERFLOW),
            one.project(view = view, choices = listOf(choice.copy(selectedObservation = huge))))
    }
}
