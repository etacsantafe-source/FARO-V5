package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.BenefitKind
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Decision
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ExplicitNote
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Rejection
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.ReviewStatus
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04DeferredCommercialBenefitNotesTest {
    private val model = S4W04DeferredCommercialBenefitNotes
    private val a = HouseholdNeedId("need-a")
    private val b = HouseholdNeedId("need-b")
    private val ars = GroupKey("ARS", "Market A", "AR-SF", CommercialPaymentMethod.CASH)
    private val usd = GroupKey("USD", "Market B", "AR-SF", CommercialPaymentMethod.DEBIT)

    private fun summary(partial: Boolean = false, reversed: Boolean = false) =
        S4W04MerchantWhatIfSummary.Decision.Summary(
            cartFingerprint = "cart-d2c1",
            sourceWatermark = "watermark-d2c1",
            asOfEpochMillis = 1000L,
            rows = (
                listOf(Row(ars, 12000L, 12000L, 1000L, 11000L,
                    listOf(a), listOf(a), GroupCoverage.FULLY_MODELED)) +
                    if (partial) listOf(Row(usd, 4000L, 0L, 0L, 0L,
                        listOf(b), emptyList(), GroupCoverage.PARTIAL)) else emptyList()
                ).let { if (reversed) it.reversed() else it },
            missingNeedIds = emptyList(),
            unmodeledNeedIds = if (partial) listOf(b) else emptyList(),
            coverage = if (partial) Coverage.PARTIAL else Coverage.ALL_MODELED,
        )

    private fun note(
        key: GroupKey = ars,
        kind: BenefitKind = BenefitKind.CASHBACK_FUTURE,
        details: String? = "Revisar términos del banco",
        sourceRef: String? = "manual:unverified",
    ) = ExplicitNote("cart-d2c1", "watermark-d2c1", 1000L, key, kind, details, sourceRef)

    private fun annotated(
        summary: S4W04MerchantWhatIfSummary.Decision.Summary,
        vararg notes: ExplicitNote,
    ): Decision.Annotated =
        model.annotate(summary, notes.toList()) as Decision.Annotated

    private fun reject(
        expected: Rejection,
        summary: S4W04MerchantWhatIfSummary.Decision.Summary,
        vararg notes: ExplicitNote,
    ) = assertEquals(Decision.Rejected(expected), model.annotate(summary, notes.toList()))

    @Test fun t01FenceMismatchAndBlankFenceReject() {
        val s = summary()
        reject(Rejection.MISMATCHED_FENCE, s, note().copy(cartFingerprint = "other"))
        reject(Rejection.MISMATCHED_FENCE, s, note().copy(sourceWatermark = "other"))
        reject(Rejection.MISMATCHED_FENCE, s, note().copy(asOfEpochMillis = 999L))
        reject(Rejection.MISMATCHED_FENCE, s.copy(cartFingerprint = ""), note())
        reject(Rejection.MISMATCHED_FENCE, s.copy(sourceWatermark = ""), note())
        reject(Rejection.MISMATCHED_FENCE, s.copy(asOfEpochMillis = -1L), note())
    }

    @Test fun t02ForgedSummaryDuplicateRowsAndCoverageRejected() {
        val s = summary()
        reject(Rejection.INVALID_SUMMARY, s.copy(rows = s.rows + s.rows.first()), note())
        reject(Rejection.INVALID_SUMMARY, s.copy(rows = s.rows.map {
            it.copy(modeledHypotheticalAfterMinor = it.modeledHypotheticalAfterMinor + 1L)
        }), note())
        reject(Rejection.INVALID_SUMMARY, s.copy(rows = s.rows.map {
            it.copy(observedBaseSubtotalMinor = -1L)
        }), note())
        reject(Rejection.INVALID_SUMMARY, s.copy(coverage = Coverage.PARTIAL), note())
        reject(Rejection.INVALID_SUMMARY, s.copy(rows = s.rows.map {
            it.copy(selectedNeedIds = listOf(a, a))
        }), note())
    }

    @Test fun t03UnknownGroupAndCrossCurrencyMerchantDenied() {
        val s = summary()
        reject(Rejection.UNKNOWN_GROUP, s, note(key = usd))
        reject(Rejection.UNKNOWN_GROUP, s, note(key = ars.copy(currencyCode = "USD")))
        reject(Rejection.UNKNOWN_GROUP, s, note(key = ars.copy(merchant = "Market B")))
        reject(Rejection.UNKNOWN_GROUP, s, note(key = ars.copy(localityCode = "AR-ER")))
        reject(Rejection.UNKNOWN_GROUP, s,
            note(key = ars.copy(paymentMethod = CommercialPaymentMethod.DEBIT)))
    }

    @Test fun t04SameKindDuplicateRejectedDifferentKindsRemainUnverified() {
        val s = summary()
        reject(Rejection.DUPLICATE_KIND_PER_GROUP, s, note(), note(details = "otro texto"))
        val result = annotated(s, note(),
            note(kind = BenefitKind.INSTALLMENT_CONDITION, details = "cuotas dudosas"))
        assertEquals(2, result.groups.single().notices.size)
        assertEquals(ReviewStatus.REVIEW_REQUIRED_UNVERIFIED,
            result.groups.single().reviewStatus)
        assertEquals(setOf(BenefitKind.CASHBACK_FUTURE, BenefitKind.INSTALLMENT_CONDITION),
            result.groups.single().notices.map { it.kind }.toSet())
    }

    @Test fun t05BoundedOpaqueNoteTextNoInterpretation() {
        val s = summary()
        reject(Rejection.INVALID_NOTE, s, note(details = "X".repeat(1025)))
        reject(Rejection.INVALID_NOTE, s, note(sourceRef = "X".repeat(1025)))
        reject(Rejection.INVALID_NOTE, s, note(details = "  "))
        reject(Rejection.INVALID_NOTE, s, note(sourceRef = ""))
        reject(Rejection.INVALID_NOTE, s,
            note(key = ars.copy(currencyCode = "ars")))
        val result = annotated(s, note(kind = BenefitKind.OTHER_UNVERIFIED,
            details = null, sourceRef = null))
        assertEquals(null, result.groups.single().notices.single().details)
    }

    @Test fun t06DeterministicGroupAndNoticeOrderingWithoutFx() {
        val first = annotated(summary(partial = true),
            note(key = usd, kind = BenefitKind.BANK_ELIGIBILITY),
            note(kind = BenefitKind.INSTALLMENT_CONDITION), note())
        val second = annotated(summary(partial = true, reversed = true),
            note(), note(kind = BenefitKind.INSTALLMENT_CONDITION),
            note(key = usd, kind = BenefitKind.BANK_ELIGIBILITY))
        assertEquals(first, second)
        assertEquals(listOf("ARS", "USD"), first.groups.map { it.key.currencyCode })
        assertEquals(listOf(BenefitKind.CASHBACK_FUTURE, BenefitKind.INSTALLMENT_CONDITION),
            first.groups.first().notices.map { it.kind })
    }

    @Test fun t07NoNotesMeansUnclassifiedAndPartialRemainsPartial() {
        val s = summary(partial = true)
        val empty = annotated(s)
        assertEquals(Coverage.PARTIAL, empty.coverage)
        assertEquals(listOf(b), empty.unmodeledNeedIds)
        assertTrue(empty.groups.all { it.reviewStatus == ReviewStatus.UNCLASSIFIED_NO_CLAIM })
        assertTrue(empty.groups.all { it.notices.isEmpty() })
        val withOne = annotated(s, note())
        assertEquals(ReviewStatus.REVIEW_REQUIRED_UNVERIFIED, withOne.groups.first().reviewStatus)
        assertEquals(ReviewStatus.UNCLASSIFIED_NO_CLAIM, withOne.groups.last().reviewStatus)
        assertTrue(withOne.notice.contains("NOT_MONEY"))
        assertTrue(withOne.warning.contains("NO_CASHBACK"))
    }

    @Test fun t08OutputImmutableInputDetachedAndHostileRefsRemainLiteral() {
        val hostile = "file:///etc/passwd\nhttps://example.invalid/dont-fetch"
        val sourceNotes = mutableListOf(note(sourceRef = hostile, details = "<script>test</script>"))
        val s = summary()
        val before = s.copy(rows = s.rows.toList())
        val decision = model.annotate(s, sourceNotes) as Decision.Annotated
        sourceNotes.clear()
        assertEquals(before, s)
        assertEquals(hostile, decision.groups.single().notices.single().sourceRef)
        assertEquals("<script>test</script>", decision.groups.single().notices.single().details)
        assertThrows(UnsupportedOperationException::class.java) {
            (decision.groups as MutableList<S4W04DeferredCommercialBenefitNotes.GroupDisclosure>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (decision.groups.single().notices as MutableList<S4W04DeferredCommercialBenefitNotes.Notice>).clear()
        }
        assertEquals(
            S4W04DeferredCommercialBenefitNotes.Provenance.USER_DECLARED_UNVERIFIED,
            decision.groups.single().notices.single().provenance,
        )
    }
}
