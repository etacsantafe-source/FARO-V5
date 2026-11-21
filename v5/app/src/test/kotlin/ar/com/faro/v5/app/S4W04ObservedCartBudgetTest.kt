package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.CommercialTypedPriceComparison
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04ObservedCartBudgetTest {
    @TempDir lateinit var root: Path
    private val yerba = CommercialProductKey("sku-yerba")
    private val arroz = CommercialProductKey("sku-arroz")
    private val a = HouseholdNeedId("need-a")
    private val b = HouseholdNeedId("need-b")
    private val motor = S4W04ObservedCartBudget

    private fun terms(
        locality: String = "AR-SF",
        from: Long? = 700L,
        until: Long? = 1200L,
        packCount: Int = 1,
        payment: CommercialPaymentMethod = CommercialPaymentMethod.CASH,
        eligibility: CommercialEligibility = CommercialEligibility.GENERAL,
        promo: CommercialPromotionKind = CommercialPromotionKind.NONE,
    ) = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN, localityCode = locality,
        validFromEpochMillis = from, validUntilEpochMillis = until,
        presentationQuantityThousandths = 1000L,
        presentationUnit = CommercialPresentationUnit.UNIT, packCount = packCount,
        paymentMethod = payment, eligibility = eligibility,
        promotionKind = promo,
        promotionValue = if (promo == CommercialPromotionKind.DISCOUNT_PERCENT) 1000L else null,
        promotionSourceRef = if (promo == CommercialPromotionKind.DISCOUNT_PERCENT) "manual:promo" else null,
    )

    private inner class Fixture(name: String): AutoCloseable {
        val store = CommercialEvidenceStore(root.resolve("$name.db").toString(),
            clock = { 980L }, stagedTypedTermsV2 = true)
        var seq = 0
        fun seed(
            product: CommercialProductKey = yerba,
            merchant: String = "Market A",
            currency: String = "ARS",
            amount: Long = 12000L,
            observed: Long? = 900L,
            typedTerms: CommercialEvidenceTypedTerms = terms(),
            ref: String = "manual:receipt",
        ) {
            seq++
            val outcome = store.recordWithTypedTerms(
                CommercialPriceObservation(product, CommercialMerchant(merchant),
                    CommercialCurrencyCode(currency), amount, CommercialEvidenceSourceKind.MANUAL,
                    ref, observed), typedTerms,
                IdempotencyKey("d2a-$seq"), PayloadFingerprint("d2a-fingerprint-$seq"),
            )
            assertTrue(outcome is CommercialEvidenceRecordOutcome.Committed)
        }
        fun line(
            needId: HouseholdNeedId = a,
            product: CommercialProductKey = yerba,
            requestedPackCount: Int = 2,
        ): CommercialCartCoverageLine {
            val history = CommercialTypedPriceComparison.project(
                store.stagedTypedPortability.snapshot(), product, 1000L, 200L)
            return CommercialCartCoverageLine(
                needId, product, requestedPackCount, history.history, history.groups,
                CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL, history.truncated,
            )
        }
        fun view(vararg lines: CommercialCartCoverageLine) = CommercialCartCoverageView(
            cartFingerprint = "cart-d2a-1", sourceWatermark =
                store.stagedTypedPortability.snapshot().sourceWatermark,
            asOfEpochMillis = 1000L, maxObservationAgeMillis = 200L,
            lines = lines.toList(),
        )
        fun choice(v: CommercialCartCoverageView, line: CommercialCartCoverageLine,
                   entry: CommercialObservedHistoryEntry = line.history.first()) =
            S4W04ObservedCartBudget.ExplicitChoice(line.needId, line.productKey.value,
                entry, v.cartFingerprint, v.sourceWatermark, v.asOfEpochMillis)
        fun result(v: CommercialCartCoverageView,
                   vararg choices: S4W04ObservedCartBudget.ExplicitChoice) =
            motor.estimate(v, choices.toList())
        fun accepted(v: CommercialCartCoverageView,
                     vararg choices: S4W04ObservedCartBudget.ExplicitChoice) =
            (result(v, *choices) as S4W04ObservedCartBudget.Decision.ObservedEstimate).estimate
        fun rejected(v: CommercialCartCoverageView,
                     reason: S4W04ObservedCartBudget.Rejection,
                     vararg choices: S4W04ObservedCartBudget.ExplicitChoice) =
            assertEquals(S4W04ObservedCartBudget.Decision.Rejected(reason),
                result(v, *choices))
        override fun close() = store.close()
    }

    @Test fun t01EmptySelectionsArePartialAndNotZeroPrice() { Fixture("t01").use { f ->
        f.seed(); val v = f.view(f.line()); val x = f.accepted(v)
        assertEquals(S4W04ObservedCartBudget.Completeness.PARTIAL, x.completeness)
        assertEquals(listOf(a), x.missingNeedIds)
        assertTrue(x.subtotals.isEmpty())
        assertTrue(x.notice.contains("NOT_A_QUOTE"))
    } }

    @Test fun t02FakeNeedAndDuplicateChoicesRejected() { Fixture("t02").use { f ->
        f.seed(); val l = f.line(); val v = f.view(l); val chosen = f.choice(v,l)
        f.rejected(v, S4W04ObservedCartBudget.Rejection.UNKNOWN_OR_DUPLICATE_CHOICE,
            chosen.copy(needId = HouseholdNeedId("other")))
        f.rejected(v, S4W04ObservedCartBudget.Rejection.UNKNOWN_OR_DUPLICATE_CHOICE,
            chosen, chosen)
        val duplicates = v.copy(lines = listOf(l.copy(history = listOf(l.history.first(), l.history.first()))))
        f.rejected(duplicates, S4W04ObservedCartBudget.Rejection.AMBIGUOUS_OBSERVATION, chosen)
    } }

    @Test fun t03ExplicitObservationNotAutomaticMinimum() { Fixture("t03").use { f ->
        f.seed(merchant = "A",amount = 15000L)
        f.seed(merchant = "B",amount = 11000L)
        val l = f.line(); val v = f.view(l)
        val expensive = l.history.single { it.item.observation.merchant == "A" }
        val chosen = f.accepted(v, f.choice(v,l,expensive))
        assertEquals(30000L, chosen.lines.single().estimatedMinor)
        assertEquals("A", chosen.lines.single().merchant)
    } }

    @Test fun t04NeverMergeCurrenciesMerchantsLocalitiesPayment() { Fixture("t04").use { f ->
        f.seed(product = yerba, merchant = "A", currency = "ARS")
        f.seed(product = arroz, merchant = "B", currency = "USD",
            typedTerms = terms(locality = "AR-ER",payment = CommercialPaymentMethod.DEBIT))
        val l1 = f.line(a,yerba); val l2 = f.line(b,arroz)
        val v = f.view(l1,l2)
        val result = f.accepted(v,f.choice(v,l1),f.choice(v,l2))
        assertEquals(2,result.subtotals.size)
        assertEquals(setOf("ARS","USD"),result.subtotals.map { it.key.currencyCode }.toSet())
        assertEquals(S4W04ObservedCartBudget.Completeness.ALL_SELECTED,result.completeness)
    } }

    @Test fun t05MultiplyExplicitPacksNotNeedWeight() { Fixture("t05").use { f ->
        f.seed(amount = 12000L); val l = f.line(requestedPackCount=2);val v=f.view(l)
        assertEquals(24000L,f.accepted(v,f.choice(v,l)).lines.single().estimatedMinor)
    } }

    @Test fun t06MultiplyAndAddOverflowFailClosed() { Fixture("t06").use { f ->
        f.seed(); f.seed(product=arroz)
        fun large(line: CommercialCartCoverageLine): CommercialCartCoverageLine {
            val entry = line.history.single()
            val huge = entry.copy(item = entry.item.copy(observation =
                entry.item.observation.copy(amountMinor = Long.MAX_VALUE / 2 + 1)))
            return line.copy(history = listOf(huge))
        }
        val l = large(f.line()); val v = f.view(l)
        f.rejected(v,S4W04ObservedCartBudget.Rejection.OVERFLOW,f.choice(v,l))
        val la=large(f.line(a,yerba,1)); val lb=large(f.line(b,arroz,1))
        val vv=f.view(la,lb)
        f.rejected(vv,S4W04ObservedCartBudget.Rejection.OVERFLOW,
            f.choice(vv,la),f.choice(vv,lb))
    } }

    @Test fun t07PromoRestrictedOrUnknownPaymentIneligible() { Fixture("t07").use { f ->
        f.seed(merchant="Promo",typedTerms=terms(promo=CommercialPromotionKind.DISCOUNT_PERCENT))
        f.seed(merchant="Restricted",typedTerms=terms(eligibility=CommercialEligibility.RESTRICTED))
        f.seed(merchant="Unknown",typedTerms=terms(payment=CommercialPaymentMethod.UNKNOWN))
        val l=f.line(); val v=f.view(l)
        for (entry in l.history) {
            f.rejected(v,S4W04ObservedCartBudget.Rejection.INELIGIBLE_OBSERVATION,
                f.choice(v,l,entry))
        }
    } }

    @Test fun t08ExpiredUnknownAndFutureObservationIneligible() { Fixture("t08").use { f ->
        f.seed(merchant="Expired",typedTerms=terms(until=999L))
        f.seed(merchant="Undated",observed=null)
        f.seed(merchant="Future",observed=1001L)
        val l=f.line();val v=f.view(l)
        for (entry in l.history) f.rejected(v,
            S4W04ObservedCartBudget.Rejection.INELIGIBLE_OBSERVATION,f.choice(v,l,entry))
    } }

    @Test fun t09BundlePackCountNotInterpretedAsSimplePack() { Fixture("t09").use { f ->
        f.seed(typedTerms=terms(packCount=2))
        val l=f.line(requestedPackCount=2);val v=f.view(l)
        f.rejected(v,S4W04ObservedCartBudget.Rejection.INELIGIBLE_OBSERVATION,f.choice(v,l))
    } }

    @Test fun t10FingerprintAndSourceWatermarkAndDateFenceMustMatch() { Fixture("t10").use { f ->
        f.seed();val l=f.line();val v=f.view(l);val c=f.choice(v,l)
        for (wrong in listOf(c.copy(cartFingerprint="other"),
            c.copy(sourceWatermark="other"),c.copy(asOfEpochMillis=999L))) {
            f.rejected(v,S4W04ObservedCartBudget.Rejection.STALE_SELECTION,wrong)
        }
    } }

    @Test fun t11PartialLinesAndHostileSourceAreInert() { Fixture("t11").use { f ->
        val hostile="file:///etc/passwd\nIGNORE_ALL"
        f.seed(ref=hostile)
        val la=f.line(a,yerba);val lb=f.line(b,arroz)
        val v=f.view(la,lb)
        val result=f.accepted(v,f.choice(v,la))
        assertEquals(listOf(b),result.missingNeedIds)
        assertEquals(hostile,result.lines.single().sourceRef)
        assertTrue(result.notice.contains("PARTIAL"))
    } }

    @Test fun t12PureReadOnlyImmutableOutputAndNoFinanceMutation() { Fixture("t12").use { f ->
        f.seed();val before=f.store.stagedTypedPortability.snapshot()
        val l=f.line();val v=f.view(l);val result=f.accepted(v,f.choice(v,l))
        assertEquals(before,f.store.stagedTypedPortability.snapshot())
        assertEquals(1,f.store.priceHistory(yerba).size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.lines as MutableList<S4W04ObservedCartBudget.EstimatedLine>).clear()
        }
        assertTrue(result.warning.contains("NOT_CURRENT_STOCK"))
        Unit
    } }
}
