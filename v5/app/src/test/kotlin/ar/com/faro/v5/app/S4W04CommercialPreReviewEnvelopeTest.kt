package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Rejection
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Warning
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.BenefitKind
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Decision.Annotated
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ExplicitNote
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Provenance
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ReviewStatus
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04CommercialPreReviewEnvelopeTest {
    private val model = S4W04CommercialPreReviewEnvelope
    private val a = HouseholdNeedId("need-a")
    private val b = HouseholdNeedId("need-b")
    private val ars = GroupKey("ARS", "Market A", "AR-SF", CommercialPaymentMethod.CASH)
    private val usd = GroupKey("USD", "Market B", "AR-SF", CommercialPaymentMethod.DEBIT)
    private val base = Summary(
        "cart-d3a", "watermark-d3a", 1000L,
        listOf(
            Row(ars, 12000L, 12000L, 1000L, 11000L, listOf(a), listOf(a), GroupCoverage.FULLY_MODELED),
            Row(usd, 8000L, 8000L, 500L, 7500L, listOf(b), listOf(b), GroupCoverage.FULLY_MODELED),
        ),
        emptyList(), emptyList(), Coverage.ALL_MODELED,
    )

    private fun note(
        kind: BenefitKind = BenefitKind.CASHBACK_FUTURE,
        details: String? = "Ver términos",
        sourceRef: String? = "manual:unverified",
        summary: Summary = base,
    ) = ExplicitNote(
        summary.cartFingerprint, summary.sourceWatermark, summary.asOfEpochMillis,
        ars, kind, details, sourceRef,
    )

    private fun annotate(summary: Summary, vararg notes: ExplicitNote): Annotated =
        S4W04DeferredCommercialBenefitNotes.annotate(summary, notes.toList()) as Annotated

    private fun preview(summary: Summary, annotated: Annotated): Decision.Preview =
        model.preview(summary, annotated) as Decision.Preview

    private fun reject(reason: Rejection, summary: Summary, annotated: Annotated) {
        assertEquals(Decision.Rejected(reason), model.preview(summary, annotated))
    }

    @Test fun t01MismatchedBlankAndInvalidFencesReject() {
        val notes = annotate(base)
        for (forged in listOf(
            notes.copy(cartFingerprint = "other"),
            notes.copy(sourceWatermark = "other"),
            notes.copy(asOfEpochMillis = Long.MAX_VALUE),
        )) reject(Rejection.MISMATCHED_FENCE, base, forged)

        reject(Rejection.MISMATCHED_FENCE, base.copy(cartFingerprint = ""), notes.copy(cartFingerprint = ""))
        reject(Rejection.MISMATCHED_FENCE, base.copy(sourceWatermark = " "), notes.copy(sourceWatermark = " "))
        reject(Rejection.MISMATCHED_FENCE, base.copy(asOfEpochMillis = -1L), notes.copy(asOfEpochMillis = -1L))
    }

    @Test fun t02ForgedAmountsKeysAndMembershipReject() {
        val notes = annotate(base)
        val row = base.rows[0]
        for (forged in listOf(
            row.copy(key = ars.copy(currencyCode = "ars")),
            row.copy(key = ars.copy(paymentMethod = CommercialPaymentMethod.UNKNOWN)),
            row.copy(observedBaseSubtotalMinor = -1L),
            row.copy(observedBaseSubtotalMinor = row.modeledBaseMinor + 1L),
            row.copy(modeledHypotheticalDiscountMinor = row.modeledBaseMinor + 1L),
            row.copy(modeledHypotheticalAfterMinor = row.modeledHypotheticalAfterMinor + 1L),
            row.copy(selectedNeedIds = listOf(a, a)),
            row.copy(modeledNeedIds = listOf(b)),
            row.copy(modeledNeedIds = emptyList(), groupCoverage = GroupCoverage.PARTIAL),
        )) reject(Rejection.INVALID_SUMMARY, base.copy(rows = listOf(forged, base.rows[1])), notes)
        reject(Rejection.INVALID_SUMMARY, base.copy(rows = base.rows + row), notes)
        reject(Rejection.INVALID_COVERAGE, base.copy(unmodeledNeedIds = listOf(a)), notes)
    }

    @Test fun t03DisclosuresMustMatchAllCommercialGroupsAndCoverage() {
        val notes = annotate(base)
        val group = notes.groups[0]
        val last = notes.groups[1]
        for (groups in listOf(
            listOf(last),
            notes.groups + group,
            listOf(group, group),
            listOf(group.copy(key = ars.copy(currencyCode = "USD")), last),
            listOf(group.copy(key = ars.copy(merchant = "Market Z")), last),
            listOf(group.copy(groupCoverage = GroupCoverage.PARTIAL), last),
        )) reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(groups = groups))
        reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(coverage = Coverage.PARTIAL))
        reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(unmodeledNeedIds = listOf(a)))
    }

    @Test fun t04WarningsStatusAndNoteBoundsPreserveUnverifiedProvenance() {
        val notes = annotate(base, note())
        val group = notes.groups[0]
        val item = group.notices.single()
        fun rejectGroup(forged: S4W04DeferredCommercialBenefitNotes.GroupDisclosure) {
            reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(groups = listOf(forged, notes.groups[1])))
        }

        reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(warning = "CASHBACK_APPROVED"))
        reject(Rejection.INVALID_DISCLOSURES, base, notes.copy(notice = "VERIFIED_CASHBACK"))
        rejectGroup(group.copy(reviewStatus = ReviewStatus.UNCLASSIFIED_NO_CLAIM))
        rejectGroup(group.copy(notices = emptyList()))
        rejectGroup(group.copy(notices = listOf(item, item)))
        rejectGroup(group.copy(notices = listOf(item.copy(sourceRef = "x".repeat(1025)))))
        rejectGroup(group.copy(notices = listOf(item.copy(details = "x".repeat(1025)))))
        rejectGroup(group.copy(notices = listOf(item.copy(details = "\uD800"))))
        assertEquals(Provenance.USER_DECLARED_UNVERIFIED, preview(base, notes).rows.first().disclosure.notices.single().provenance)
    }

    @Test fun t05RowsAndNoticesHaveCanonicalOrderWithoutCombiningCurrencies() {
        val notes = annotate(base, note(), note(BenefitKind.BANK_ELIGIBILITY))
        val original = preview(base, notes)
        val reordered = preview(
            base.copy(rows = base.rows.reversed()),
            notes.copy(groups = notes.groups.reversed().map { it.copy(notices = it.notices.reversed()) }),
        )
        assertEquals(original, reordered)
        assertTrue(original.digest.matches(Regex("[0-9a-f]{64}")))
        assertEquals(listOf("ARS", "USD"), original.rows.map { it.estimate.key.currencyCode })
        assertEquals(listOf(12000L, 8000L), original.rows.map { it.estimate.observedBaseSubtotalMinor })
    }

    @Test fun t06AmountsSourceAndLiteralBoundariesAffectTheDigest() {
        val initial = preview(base, annotate(base, note(details = "a|b", sourceRef = "c")))
        val row = base.rows[0]
        val changed = base.copy(rows = listOf(
            row.copy(
                observedBaseSubtotalMinor = row.observedBaseSubtotalMinor + 1L,
                modeledBaseMinor = row.modeledBaseMinor + 1L,
                modeledHypotheticalAfterMinor = row.modeledHypotheticalAfterMinor + 1L,
            ),
            base.rows[1],
        ))
        val changedSource = base.copy(sourceWatermark = "later")
        val digests = listOf(
            initial.digest,
            preview(changed, annotate(changed, note(details = "a|b", sourceRef = "c"))).digest,
            preview(changedSource, annotate(changedSource, note(details = "a|b", sourceRef = "c", summary = changedSource))).digest,
            preview(base, annotate(base, note(details = "a", sourceRef = "b|c"))).digest,
            preview(base, annotate(base, note(details = null, sourceRef = "c"))).digest,
        )
        assertEquals(digests.size, digests.toSet().size)
    }

    @Test fun t07PartialCoverageAndLineCapRemainReadOnly() {
        val ids = List(200) { HouseholdNeedId("need-%03d".format(it)) }
        val row = Row(ars, 200L, 1L, 0L, 1L, ids, ids.take(1), GroupCoverage.PARTIAL)
        val partial = base.copy(rows = listOf(row), unmodeledNeedIds = ids.drop(1), coverage = Coverage.PARTIAL)
        val notes = annotate(partial)
        val result = preview(partial, notes)
        assertEquals(Coverage.PARTIAL, result.coverage)
        assertEquals(ids.drop(1), result.unmodeledNeedIds)
        assertEquals(ReviewStatus.UNCLASSIFIED_NO_CLAIM, result.rows.single().disclosure.reviewStatus)
        assertTrue(result.rows.single().disclosure.notices.isEmpty())
        assertEquals(Warning.entries, result.warnings)
        assertTrue(Warning.LIVE_REVALIDATION_REQUIRED in preview(base, annotate(base)).warnings)

        val missing = HouseholdNeedId("need-missing")
        reject(
            Rejection.INVALID_COVERAGE,
            partial.copy(missingNeedIds = listOf(missing), unmodeledNeedIds = ids.drop(1) + missing),
            notes,
        )
    }

    @Test fun t08SealDetachesCollectionsAndKeepsHostileSourceAsText() {
        val source = "https://127.0.0.1:1/<script>literal</script>"
        val selected = mutableListOf(a)
        val rows = mutableListOf(base.rows[0].copy(selectedNeedIds = selected), base.rows[1])
        val summary = base.copy(rows = rows)
        val annotated = annotate(summary, note(sourceRef = source))
        val notices = annotated.groups[0].notices.toMutableList()
        val groups = mutableListOf(annotated.groups[0].copy(notices = notices), annotated.groups[1])
        val result = preview(summary, annotated.copy(groups = groups))
        val snapshot = preview(base, annotate(base, note(sourceRef = source)))

        selected.clear()
        rows.clear()
        notices.clear()
        groups.clear()

        assertEquals(snapshot, result)
        assertEquals(source, result.rows.first().disclosure.notices.single().sourceRef)
        val row = result.rows.first()
        for (list in listOf(
            result.rows, result.warnings, result.missingNeedIds, result.unmodeledNeedIds,
            row.estimate.selectedNeedIds, row.estimate.modeledNeedIds, row.disclosure.notices,
        )) {
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (list as MutableList<Any?>).add(null)
            }
        }
    }
}
