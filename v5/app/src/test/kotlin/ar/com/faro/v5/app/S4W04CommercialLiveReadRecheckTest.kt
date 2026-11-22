package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Rejection
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Warning
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage as PreviewCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04CommercialLiveReadRecheckTest {
    @TempDir lateinit var root: Path

    private val product = CommercialProductKey("sku-yerba")
    private val otherProduct = CommercialProductKey("sku-rice")
    private val need = HouseholdNeed(
        HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
        HouseholdNeedQuantity(1000L, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 2L,
    )
    private val otherNeed = need.copy(id = HouseholdNeedId("need-rice"), name = HouseholdNeedName("Rice"))
    private val fence = CommercialNeedReadFence("gen-1", 11L, "epoch-1", "principal-1")
    private val key = GroupKey("ARS", "Market A", "AR-SF", CommercialPaymentMethod.CASH)

    private inner class Fixture(name: String, partial: Boolean = false) : AutoCloseable {
        private val store = CommercialEvidenceStore(
            root.resolve("$name.db").toString(), clock = { 980L }, stagedTypedTermsV2 = true,
        )
        private val original = HouseholdNeedsSnapshot(7L, if (partial) listOf(need, otherNeed) else listOf(need))
        var household: HouseholdNeedsSnapshot? = original
        var activeFence: CommercialNeedReadFence? = fence
        var admitsHousehold = true
        var admitsCommercial = true
        var evidenceReads = 0
        var source: (Int) -> CommercialEvidenceStagedTypedPortabilitySnapshot? = { snapshot() }
        var clock: () -> Long? = { 1020L }
        private var sequence = 0
        private val d1 = S4W04CommercialCartCoverage(
            currentHouseholdSnapshot = { household },
            currentFence = { activeFence },
            currentCommercialSnapshot = { source(++evidenceReads) },
            householdAdmission = { admitsHousehold },
            commercialReadAdmission = { admitsCommercial },
        )
        private val d3b = S4W04CommercialLiveReadRecheck(d1) { clock() }
        val draft: S4W04CommercialCartDraft

        init {
            val choices = listOf(choice(need, product)) +
                if (partial) listOf(choice(otherNeed, otherProduct)) else emptyList()
            draft = (S4W04CommercialCartDraft.build(original, fence, choices) { true }
                as S4W04CommercialCartDraft.Decision.Draft).cartDraft
            seed("Market A")
            seed("Market B")
        }

        private fun choice(item: HouseholdNeed, sku: CommercialProductKey): S4W04CommercialCartDraft.PackChoice {
            val selection = S4W04CommercialNeedProductSelection.select(
                original, item.id, sku, listOf(sku), fence, explicitlyChosen = true,
            ) { true } as CommercialNeedProductDecision.Candidate
            return S4W04CommercialCartDraft.PackChoice(selection.binding, 2)
        }

        fun seed(merchant: String, observed: Long = 900L, until: Long = 1200L) {
            val id = ++sequence
            val terms = CommercialEvidenceTypedTerms(
                localityKind = CommercialLocalityKind.KNOWN,
                localityCode = "AR-SF",
                validFromEpochMillis = 700L,
                validUntilEpochMillis = until,
                presentationQuantityThousandths = 1000L,
                presentationUnit = CommercialPresentationUnit.UNIT,
                packCount = 1,
                paymentMethod = CommercialPaymentMethod.CASH,
                eligibility = CommercialEligibility.GENERAL,
                promotionKind = CommercialPromotionKind.NONE,
            )
            val result = store.recordWithTypedTerms(
                CommercialPriceObservation(
                    product, CommercialMerchant(merchant), CommercialCurrencyCode("ARS"),
                    15000L, CommercialEvidenceSourceKind.MANUAL, "manual:receipt:$id", observed,
                ), terms, IdempotencyKey("d3b-$id"), PayloadFingerprint("d3b-payload-$id"),
            )
            assertTrue(result is CommercialEvidenceRecordOutcome.Committed)
        }

        fun snapshot() = store.stagedTypedPortability.snapshot()

        fun baseline() = (d1.project(draft, 1000L, 200L) as CommercialCartCoverageDecision.Observed).view

        fun preview(baseline: CommercialCartCoverageView): S4W04CommercialPreReviewEnvelope.Decision.Preview {
            val missing = draft.lines.map { it.needId }.filterNot { it == need.id }
            val summary = Summary(
                draft.fingerprint, baseline.sourceWatermark, baseline.asOfEpochMillis,
                listOf(Row(key, 30000L, 30000L, 0L, 30000L,
                    listOf(need.id), listOf(need.id), GroupCoverage.FULLY_MODELED)),
                missing, missing, if (missing.isEmpty()) PreviewCoverage.ALL_MODELED else PreviewCoverage.PARTIAL,
            )
            val annotated = S4W04DeferredCommercialBenefitNotes.annotate(summary, emptyList())
                as S4W04DeferredCommercialBenefitNotes.Decision.Annotated
            return S4W04CommercialPreReviewEnvelope.preview(summary, annotated)
                as S4W04CommercialPreReviewEnvelope.Decision.Preview
        }

        fun check(baseline: CommercialCartCoverageView, preview: S4W04CommercialPreReviewEnvelope.Decision.Preview) =
            d3b.recheck(draft, baseline, preview)

        fun reject(
            reason: Rejection,
            baseline: CommercialCartCoverageView,
            preview: S4W04CommercialPreReviewEnvelope.Decision.Preview = preview(baseline),
        ) = assertEquals(Decision.Rejected(reason), check(baseline, preview))

        override fun close() = store.close()
    }

    @Test fun t01RejectMismatchedIdentityAndMalformedBaseline() = Fixture("identity").use { f ->
        val baseline = f.baseline()
        val preview = f.preview(baseline)
        val line = baseline.lines.single()
        for (mismatch in listOf(
            preview.copy(cartFingerprint = "different"),
            preview.copy(sourceWatermark = "different"),
            preview.copy(asOfEpochMillis = 999L),
        )) f.reject(Rejection.MISMATCHED_FENCE, baseline, mismatch)
        f.reject(Rejection.INVALID_PREVIEW_OR_BASELINE, baseline, preview.copy(digest = "x".repeat(64)))
        for (malformed in listOf(
            baseline.copy(lines = emptyList()),
            baseline.copy(lines = listOf(line, line)),
            baseline.copy(lines = listOf(line.copy(productKey = otherProduct))),
            baseline.copy(lines = listOf(line.copy(requestedPackCount = 3))),
            baseline.copy(maxObservationAgeMillis = 0L),
            baseline.copy(notice = "CURRENT_QUOTE"),
        )) f.reject(Rejection.INVALID_PREVIEW_OR_BASELINE, malformed, preview)
        assertEquals(2, f.evidenceReads)
    }

    @Test fun t02RevalidateHouseholdRevisionFenceAndBothAdmissions() = Fixture("admission").use { f ->
        val baseline = f.baseline()
        val household = checkNotNull(f.household)
        f.household = household.copy(revision = household.revision + 1)
        f.reject(Rejection.DENIED_OR_STALE, baseline)
        f.household = household
        for (changed in listOf(
            fence.copy(generationId = "gen-2"), fence.copy(hostEpoch = "epoch-2"),
            fence.copy(pointerSequence = 12L), fence.copy(principal = "principal-2"),
        )) {
            f.activeFence = changed
            f.reject(Rejection.DENIED_OR_STALE, baseline)
        }
        f.activeFence = fence
        f.admitsHousehold = false
        f.reject(Rejection.DENIED_OR_STALE, baseline)
        f.admitsHousehold = true
        f.admitsCommercial = false
        f.reject(Rejection.DENIED_OR_STALE, baseline)
        assertEquals(2, f.evidenceReads)
    }

    @Test fun t03CompareEntireViewAndDetectChangesInsideD1() = Fixture("source-change").use { f ->
        val baseline = f.baseline()
        val line = baseline.lines.single()
        val entry = line.history.first()
        val changed = entry.copy(item = entry.item.copy(
            observation = entry.item.observation.copy(amountMinor = 10000L),
        ))
        for (forged in listOf(
            line.copy(history = listOf(changed) + line.history.drop(1)),
            line.copy(groups = emptyList()),
            line.copy(status = CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL),
            line.copy(truncated = true),
        )) f.reject(Rejection.SOURCE_CHANGED, baseline.copy(lines = listOf(forged)))

        val source = f.snapshot()
        f.seed("Market C")
        val after = f.snapshot()
        val nextRead = f.evidenceReads + 1
        f.source = { read -> if (read == nextRead) source else after }
        f.reject(Rejection.SOURCE_CHANGED, baseline)
    }

    @Test fun t04MissingUnverifiedSourceAndCallbackExceptionsFailClosed() = Fixture("missing-source").use { f ->
        val baseline = f.baseline()
        val active = f.snapshot()
        f.source = { null }
        f.reject(Rejection.SOURCE_UNVERIFIED, baseline)
        f.source = { error("STAGED source unavailable") }
        f.reject(Rejection.SOURCE_UNVERIFIED, baseline)
        f.source = { active.copy(records = emptyList()) }
        f.reject(Rejection.SOURCE_UNVERIFIED, baseline)
        f.source = { active }
        f.activeFence = null
        f.reject(Rejection.DENIED_OR_STALE, baseline)
    }

    @Test fun t05RejectMissingInvalidReversedAndExpiredClock() = Fixture("clock").use { f ->
        val baseline = f.baseline()
        val year = 365L * 24 * 60 * 60 * 1000
        for (value in listOf(null, -1L, 999L, 1001L + year)) {
            f.clock = { value }
            f.reject(Rejection.CLOCK_INVALID, baseline)
        }
        f.clock = { error("clock unavailable") }
        f.reject(Rejection.CLOCK_INVALID, baseline)
        for (after in listOf(1019L, 61021L, Long.MAX_VALUE)) {
            val readings = listOf(1020L, after).iterator()
            f.clock = { readings.next() }
            f.reject(Rejection.CLOCK_INVALID, baseline)
        }
    }

    @Test fun t06RejectAgedExpiredAndTruncatedSelectedHistory() {
        Fixture("age").use { f ->
            val baseline = f.baseline()
            f.clock = { 1101L }
            f.reject(Rejection.STALE_OBSERVATION_FOR_PREVIEW, baseline)
        }
        Fixture("selected-expired").use { f ->
            f.seed("Market A", observed = 950L, until = 1019L)
            val baseline = f.baseline()
            // Market A still has a different comparable record, which cannot replace the selection.
            f.reject(Rejection.STALE_OBSERVATION_FOR_PREVIEW, baseline)
        }
        Fixture("truncated").use { f ->
            repeat(199) { f.seed("Market C") }
            val baseline = f.baseline()
            assertTrue(baseline.lines.single().truncated)
            f.reject(Rejection.STALE_OBSERVATION_FOR_PREVIEW, baseline)
        }
    }

    @Test fun t07StableReadsReturnOnlyDisplayResultWithExplicitCoverage() {
        for (partial in listOf(false, true)) Fixture("display-$partial", partial).use { f ->
            val baseline = f.baseline()
            val preview = f.preview(baseline)
            assertEquals(
                Decision.RecheckedForDisplay(
                    preview.digest, 1020L, baseline.sourceWatermark, f.draft.fingerprint, 1,
                    if (partial) Coverage.PARTIAL else Coverage.OBSERVED_ALL_SELECTED,
                ),
                f.check(baseline, preview),
            )
            val result = f.check(baseline, preview) as Decision.RecheckedForDisplay
            assertEquals(Warning.entries, result.warnings)
            assertEquals(14, f.evidenceReads)
        }
    }

    @Test fun t08DetectMidRecheckChangesAndFinalAdmissionRevocation() = Fixture("interrupted").use { f ->
        val baseline = f.baseline()
        val original = f.snapshot()
        f.seed("Market C")
        val changed = f.snapshot()
        for (projectionStart in listOf(3, 5)) {
            f.evidenceReads = 0
            f.source = { read -> if (read in projectionStart..projectionStart + 1) changed else original }
            f.reject(Rejection.SOURCE_CHANGED, baseline)
        }

        f.evidenceReads = 0
        f.source = { read ->
            if (read == 6) f.admitsCommercial = false
            original
        }
        f.reject(Rejection.DENIED_OR_STALE, baseline)
    }
}
