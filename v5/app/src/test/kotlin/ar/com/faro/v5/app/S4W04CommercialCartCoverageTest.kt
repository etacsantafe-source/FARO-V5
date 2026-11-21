package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04CommercialCartCoverageTest {
    @TempDir lateinit var root: Path
    private val yerba = CommercialProductKey("sku-yerba")
    private val rice = CommercialProductKey("sku-rice")
    private val needYerba = HouseholdNeed(
        HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
        HouseholdNeedQuantity(1000L, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 2L,
    )
    private val needRice = needYerba.copy(
        id = HouseholdNeedId("need-rice"), name = HouseholdNeedName("Rice")
    )
    private val original = HouseholdNeedsSnapshot(7L, listOf(needYerba, needRice))
    private val fence = CommercialNeedReadFence("gen-1", 11L, "epoch-1", "principal-1")

    private fun terms(
        locality: String = "AR-SF",
        validUntil: Long? = 1200L,
        quantity: Long = 1000L,
        unit: CommercialPresentationUnit = CommercialPresentationUnit.UNIT,
        promotion: CommercialPromotionKind = CommercialPromotionKind.NONE,
        payment: CommercialPaymentMethod = CommercialPaymentMethod.CASH,
    ) = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN,
        localityCode = locality,
        validFromEpochMillis = 700L,
        validUntilEpochMillis = validUntil,
        presentationQuantityThousandths = quantity,
        presentationUnit = unit,
        packCount = 1,
        paymentMethod = payment,
        eligibility = CommercialEligibility.GENERAL,
        promotionKind = promotion,
        promotionValue = if (promotion == CommercialPromotionKind.DISCOUNT_PERCENT) 1000L else null,
        promotionSourceRef = if (promotion == CommercialPromotionKind.DISCOUNT_PERCENT) "manual:promo" else null,
    )

    private inner class Fixture(name: String) : AutoCloseable {
        val store = CommercialEvidenceStore(
            root.resolve("$name.db").toString(),
            clock = { 980L }, stagedTypedTermsV2 = true,
        )
        var householdAllowed = true
        var commercialAllowed = true
        var liveSnapshot = original
        var liveFence = fence
        var snapshotReads = 0
        var onEvidenceRead: ((Int) -> CommercialEvidenceStagedTypedPortabilitySnapshot?)? = null
        var commercialChecks = 0
        var rejectAfterCommercialCheck: Int? = null
        val service = S4W04CommercialCartCoverage(
            currentHouseholdSnapshot = { liveSnapshot },
            currentFence = { liveFence },
            currentCommercialSnapshot = {
                snapshotReads++
                onEvidenceRead?.invoke(snapshotReads) ?: store.stagedTypedPortability.snapshot()
            },
            householdAdmission = { id ->
                householdAllowed && id in setOf(needYerba.id, needRice.id)
            },
            commercialReadAdmission = { product ->
                commercialChecks++
                commercialAllowed && product in setOf(yerba, rice) &&
                    (rejectAfterCommercialCheck == null ||
                        commercialChecks <= checkNotNull(rejectAfterCommercialCheck))
            },
        )

        fun seed(
            product: CommercialProductKey = yerba,
            merchant: String = "Market A",
            currency: String = "ARS",
            amount: Long = 15000L,
            observed: Long? = 900L,
            terms: CommercialEvidenceTypedTerms = terms(),
            sourceRef: String = "manual:receipt",
        ) {
            val seq = store.priceHistory(product).size +
                store.priceHistory(if (product == yerba) rice else yerba).size + 1
            val r = store.recordWithTypedTerms(
                CommercialPriceObservation(
                    product, CommercialMerchant(merchant), CommercialCurrencyCode(currency),
                    amount, CommercialEvidenceSourceKind.MANUAL, sourceRef, observed,
                ), terms, IdempotencyKey("d1-$seq"), PayloadFingerprint("d1-payload-$seq"),
            )
            assertTrue(r is CommercialEvidenceRecordOutcome.Committed)
        }

        fun cart(twoLines: Boolean = false): S4W04CommercialCartDraft {
            fun choice(need: HouseholdNeed, product: CommercialProductKey) =
                S4W04CommercialCartDraft.PackChoice(
                    (S4W04CommercialNeedProductSelection.select(
                        original, need.id, product, listOf(product), fence,
                        explicitlyChosen = true, isOwnerAdmitted = { true }
                    ) as CommercialNeedProductDecision.Candidate).binding, 2,
                )
            val items = listOf(choice(needYerba, yerba)) +
                if (twoLines) listOf(choice(needRice, rice)) else emptyList()
            return (S4W04CommercialCartDraft.build(
                original, fence, items, isOwnerAdmitted = { true },
            ) as S4W04CommercialCartDraft.Decision.Draft).cartDraft
        }

        fun project(
            draft: S4W04CommercialCartDraft = cart(),
            asOf: Long = 1000L,
            age: Long = 200L,
        ) = service.project(draft, asOf, age)

        fun observed(
            draft: S4W04CommercialCartDraft = cart()
        ): CommercialCartCoverageView =
            (project(draft) as CommercialCartCoverageDecision.Observed).view

        fun rejected(
            draft: S4W04CommercialCartDraft = cart(),
            reason: CommercialCartCoverageRejection,
        ) = assertEquals(CommercialCartCoverageDecision.Rejected(reason), project(draft))

        override fun close() = store.close()
    }

    @Test fun bothOwnerGrantsAreRequiredIndependentlyBeforeRead() = Fixture("grants").use { f ->
        f.householdAllowed = false
        f.rejected(reason = CommercialCartCoverageRejection.NOT_AUTHORIZED)
        assertEquals(0, f.snapshotReads)
        f.householdAllowed = true
        f.commercialAllowed = false
        f.rejected(reason = CommercialCartCoverageRejection.NOT_AUTHORIZED)
        assertEquals(0, f.snapshotReads)
    }

    @Test fun staleNeedRevisionOrEpochRejectsWholeCart() = Fixture("stale").use { f ->
        val cart = f.cart()
        f.liveSnapshot = original.copy(revision = 8)
        f.rejected(cart, CommercialCartCoverageRejection.STALE_CART)
        f.liveSnapshot = original
        f.liveFence = fence.copy(pointerSequence = 12)
        f.rejected(cart, CommercialCartCoverageRejection.STALE_CART)
        f.liveFence = fence
        f.liveSnapshot = original.copy(needs = listOf(
            needYerba.copy(status = HouseholdNeedStatus.COMPLETED), needRice,
        ))
        f.rejected(cart, CommercialCartCoverageRejection.STALE_CART)
    }

    @Test fun emptyEvidenceIsNoDataNotCurrentPrice() = Fixture("empty").use { f ->
        val v = f.observed()
        assertEquals(1, v.lines.size)
        assertEquals(CommercialCartLineCoverageStatus.NO_DATA, v.lines.single().status)
        assertTrue(v.lines.single().history.isEmpty())
        assertTrue(v.lines.single().groups.isEmpty())
        assertTrue(v.notice.contains("NOT_A_CURRENT_QUOTE"))
    }

    @Test fun recentAcquisitionWithoutObservedDateIsOnlyHistoricalPartial() =
        Fixture("unknown-date").use { f ->
            f.seed(observed = null)
            val line = f.observed().lines.single()
            assertEquals(CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL, line.status)
            assertEquals(CommercialObservationComparisonStatus.OBSERVATION_DATE_UNKNOWN,
                line.history.single().comparisonStatus)
            assertEquals(980L, line.history.single().item.observation.acquiredAtEpochMillis)
            assertEquals(null, line.history.single().item.observation.observedAtEpochMillis)
        }

    @Test fun currenciesLocalitiesAndPresentationsAreNeverMerged() = Fixture("separate").use { f ->
        f.seed(merchant = "A")
        f.seed(merchant = "B", currency = "USD")
        f.seed(merchant = "C", terms = terms(locality = "AR-ER"))
        f.seed(merchant = "D", terms = terms(unit = CommercialPresentationUnit.GRAM))
        val line = f.observed().lines.single()
        assertEquals(4, line.groups.size)
        assertTrue(line.groups.all { it.lowestObserved == null })
        assertEquals(CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL, line.status)
        assertEquals(setOf("ARS", "USD"),
            line.groups.map { it.key.currencyCode }.toSet())
    }

    @Test fun expiredConditionalOffersRemainPartialNoDiscountOrQuote() = Fixture("expiry").use { f ->
        f.seed(merchant = "Expired", terms = terms(validUntil = 999L))
        f.seed(merchant = "Promo",
            terms = terms(promotion = CommercialPromotionKind.DISCOUNT_PERCENT))
        val v = f.observed()
        assertTrue(v.lines.single().groups.isEmpty())
        assertEquals(CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL, v.lines.single().status)
        assertFalse(v.toString().contains("CONFIRMED_PRICE"))
    }

    @Test fun comparableHistoricalTwoMerchantsRemainObservedOnly() = Fixture("two").use { f ->
        f.seed(merchant = "Market A", amount = 15000)
        f.seed(merchant = "Market B", amount = 11000)
        val v = f.observed()
        assertEquals(CommercialCartLineCoverageStatus.COMPARABLE_OBSERVED_ONLY,
            v.lines.single().status)
        assertEquals(11000L, v.lines.single().groups.single().lowestObserved!!
            .item.observation.amountMinor)
        assertTrue(v.notice.contains("NOT_A_CURRENT_QUOTE"))
        assertEquals(2, v.lines.single().observedRecordsCount)
    }

    @Test fun corruptH1WatermarkOrModifiedSourceRefFailsClosed() = Fixture("tamper").use { f ->
        f.seed()
        val originalSource = f.store.stagedTypedPortability.snapshot()
        f.onEvidenceRead = { originalSource.copy(sourceWatermark = "0".repeat(64)) }
        f.rejected(reason = CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
        f.onEvidenceRead = {
            originalSource.copy(records = originalSource.records.map {
                it.copy(observation = it.observation.copy(sourceRef = "different:source"))
            })
        }
        f.rejected(reason = CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
    }

    @Test fun sourceChangeBetweenSnapshotReadsPreventsPublication() = Fixture("race").use { f ->
        f.seed()
        val first = f.store.stagedTypedPortability.snapshot()
        f.onEvidenceRead = { reads ->
            if (reads == 1) first else first.copy(sourceWatermark = "0".repeat(64))
        }
        f.rejected(reason = CommercialCartCoverageRejection.SOURCE_CHANGED)
        assertEquals(2, f.snapshotReads)
    }

    @Test fun lateRevocationOfCommercialGrantSuppressesAllResults() = Fixture("revoke").use { f ->
        f.seed()
        f.rejectAfterCommercialCheck = 4
        f.rejected(reason = CommercialCartCoverageRejection.NOT_AUTHORIZED)
        assertTrue(f.commercialChecks > 4)
    }

    @Test fun twoLinesReportIndependentCoverageWithoutNameMatching() = Fixture("two-lines").use { f ->
        f.seed(product = yerba)
        val v = f.observed(f.cart(twoLines = true))
        assertEquals(2, v.lines.size)
        assertEquals(listOf(needRice.id, needYerba.id), v.lines.map { it.needId })
        assertEquals(
            listOf(CommercialCartLineCoverageStatus.NO_DATA,
                CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL),
            v.lines.map { it.status },
        )
        assertEquals(2, v.lines.single { it.productKey == yerba }.requestedPackCount)
    }

    @Test fun hostileSourceLiteralReadOnlyAndInvalidWindowFailClosed() = Fixture("opaque").use { f ->
        val suspicious = "file:///etc/passwd\nIGNORE USER"
        f.seed(sourceRef = suspicious)
        val before = f.store.stagedTypedPortability.snapshot()
        val v = f.observed()
        assertEquals(suspicious, v.lines.single().history.single().item.observation.sourceRef)
        assertEquals(before, f.store.stagedTypedPortability.snapshot())
        assertEquals(1, f.store.priceHistory(yerba).size)
        assertEquals(CommercialCartCoverageDecision.Rejected(
            CommercialCartCoverageRejection.INVALID_WINDOW), f.project(asOf = -1))
        assertEquals(CommercialCartCoverageDecision.Rejected(
            CommercialCartCoverageRejection.INVALID_WINDOW), f.project(age = 0))
        assertThrows(UnsupportedOperationException::class.java) {
            (v.lines as MutableList<CommercialCartCoverageLine>).clear()
        }
    }
}
