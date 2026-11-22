package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04HistoricalAuthorizedReadCoordinator.Decision
import ar.com.faro.v5.app.S4W04HistoricalAuthorizedReadCoordinator.Rejection
import ar.com.faro.v5.app.S4W04ObservedCartBudget.ExplicitChoice
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

/** Eight new F1 tests only; D0–D4 and E keep their own certifications untouched. */
class S4W04HistoricalAuthorizedReadCoordinatorTest {
    @TempDir lateinit var temp: Path

    private inner class Fixture(private val missingSecond: Boolean = false) : AutoCloseable {
        val product = CommercialProductKey("sku-yerba")
        private val otherProduct = CommercialProductKey("sku-arroz")
        val need = HouseholdNeed(
            HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(1000L, HouseholdNeedUnit.UNIT),
            HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 2L,
        )
        val missingNeed = need.copy(id = HouseholdNeedId("need-arroz"),
            name = HouseholdNeedName("Arroz"))
        val household = HouseholdNeedsSnapshot(
            7L, if (missingSecond) listOf(need, missingNeed) else listOf(need),
        )
        private val fence = CommercialNeedReadFence("gen-f1", 5L, "epoch-f1", "principal-f1")
        var currentFence: CommercialNeedReadFence? = fence
        var householdAllowed = true
        var commercialAllowed = true
        var denyCommercialAfter: Int? = null
        private var commercialChecks = 0
        private var sourceReads = 0
        var makeSourceUnavailableAfter: Int? = null
        var makeFenceStaleAfter: Int? = null
        private var fenceReads = 0
        var clock: () -> Long? = { 1020L }
        private val store = CommercialEvidenceStore(
            temp.resolve("f1-commerce.db").toString(),
            clock = { 980L }, stagedTypedTermsV2 = true,
        )
        val draft: S4W04CommercialCartDraft

        init {
            val choices = listOf(choice(need, product)) +
                if (missingSecond) listOf(choice(missingNeed, otherProduct)) else emptyList()
            draft = (S4W04CommercialCartDraft.build(household, fence, choices) { true }
                as S4W04CommercialCartDraft.Decision.Draft).cartDraft
            record("Mercado A", 15000L, "manual:precio-a", "ARS")
            record("Mercado B", 12000L, "manual:precio-b", "ARS")
        }

        private fun choice(item: HouseholdNeed, key: CommercialProductKey): S4W04CommercialCartDraft.PackChoice {
            val selected = S4W04CommercialNeedProductSelection.select(
                household, item.id, key, listOf(key), fence, explicitlyChosen = true,
            ) { true } as CommercialNeedProductDecision.Candidate
            return S4W04CommercialCartDraft.PackChoice(selected.binding, 2)
        }

        fun record(merchant: String, minor: Long, sourceRef: String, currency: String) {
            val terms = CommercialEvidenceTypedTerms(
                localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
                validFromEpochMillis = 700L, validUntilEpochMillis = 1200L,
                presentationQuantityThousandths = 1000L,
                presentationUnit = CommercialPresentationUnit.UNIT,
                packCount = 1,
                paymentMethod = CommercialPaymentMethod.CASH,
                eligibility = CommercialEligibility.GENERAL,
                promotionKind = CommercialPromotionKind.NONE,
            )
            val result = store.recordWithTypedTerms(
                CommercialPriceObservation(
                    product, CommercialMerchant(merchant),
                    CommercialCurrencyCode(currency), minor,
                    CommercialEvidenceSourceKind.MANUAL, sourceRef, 900L,
                ),
                terms, IdempotencyKey("f1-${sourceRef}"), PayloadFingerprint("f1-${sourceRef}-payload"),
            )
            assertTrue(result is CommercialEvidenceRecordOutcome.Committed)
        }

        private fun evidence(): CommercialEvidenceStagedTypedPortabilitySnapshot? {
            val next = ++sourceReads
            if (makeSourceUnavailableAfter != null && next > makeSourceUnavailableAfter!!) return null
            return store.stagedTypedPortability.snapshot()
        }

        fun grantEvidence(): Boolean {
            val next = ++commercialChecks
            return commercialAllowed && (denyCommercialAfter == null || next <= denyCommercialAfter!!)
        }

        private fun getFence(): CommercialNeedReadFence? {
            val next = ++fenceReads
            return if (makeFenceStaleAfter != null && next > makeFenceStaleAfter!!)
                fence.copy(hostEpoch = "stale-epoch") else currentFence
        }

        private fun d1() = S4W04CommercialCartCoverage(
            currentHouseholdSnapshot = { household },
            currentFence = { fence },
            currentCommercialSnapshot = { store.stagedTypedPortability.snapshot() },
            householdAdmission = { true },
            commercialReadAdmission = { true },
        )

        fun choose(merchant: String = "Mercado A"): ExplicitChoice {
            val view = (d1().project(draft, 1000L, 200L) as CommercialCartCoverageDecision.Observed).view
            val line = view.lines.first()
            val selected = line.history.single { it.item.observation.merchant == merchant }
            return ExplicitChoice(
                line.needId, line.productKey.value, selected,
                view.cartFingerprint, view.sourceWatermark, view.asOfEpochMillis,
            )
        }

        fun host() = S4W04HistoricalAuthorizedReadCoordinator(
            currentHousehold = { household },
            currentFence = ::getFence,
            currentEvidence = ::evidence,
            householdAdmission = { householdAllowed },
            evidenceAdmission = { grantEvidence() },
            readNow = { clock() },
        )

        override fun close() = store.close()
    }

    @Test fun t01ExplicitMerchantNotCheapestBecomesHistoricalDisplayOnly() = Fixture().use { f ->
        val display = f.host().project(f.draft, listOf(f.choose("Mercado A")), 1000L, 200L)
            as Decision.Historical
        assertEquals(30000L, display.display.groups.single().observedHistoricalSubtotalMinor)
        assertEquals("Mercado A", display.display.groups.single().key.merchant)
        assertEquals("ARS", display.display.groups.single().key.currencyCode)
        assertTrue(display.display.groups.single().scenario == null)
        assertTrue(display.display.groups.single().unverifiedBenefits.isEmpty())
        assertEquals(S4W04CommercialLiveReadRecheck.Coverage.PARTIAL, display.display.coverage)
    }

    @Test fun t02MissingExplicitSelectionMustNotAutoChooseCheapest() = Fixture().use { f ->
        assertEquals(Decision.Rejected(Rejection.NO_EXPLICIT_SELECTION),
            f.host().project(f.draft, emptyList(), 1000L, 200L))
    }

    @Test fun t03RevokedHouseholdFailsClosed() = Fixture().use { f ->
        val c = f.choose()
        f.householdAllowed = false
        assertEquals(Decision.Rejected(Rejection.NOT_AUTHORIZED_OR_STALE),
            f.host().project(f.draft, listOf(c), 1000L, 200L))
    }

    @Test fun t04RevokedEvidenceDuringReadNeverReturnsHistoricalDisplay() = Fixture().use { f ->
        val c = f.choose()
        f.denyCommercialAfter = 3
        assertTrue(f.host().project(f.draft, listOf(c), 1000L, 200L) is Decision.Rejected)
    }

    @Test fun t05UnavailableEvidenceAfterFirstSnapshotFailsClosed() = Fixture().use { f ->
        val c = f.choose()
        f.makeSourceUnavailableAfter = 2
        assertTrue(f.host().project(f.draft, listOf(c), 1000L, 200L) is Decision.Rejected)
    }

    @Test fun t06ChangedReadFenceDuringRecheckFailsClosed() = Fixture().use { f ->
        val c = f.choose()
        f.makeFenceStaleAfter = 2
        assertTrue(f.host().project(f.draft, listOf(c), 1000L, 200L) is Decision.Rejected)
    }

    @Test fun t07MissingOtherProductIsPartialNeverZero() = Fixture(missingSecond = true).use { f ->
        val display = f.host().project(f.draft, listOf(f.choose()), 1000L, 200L)
            as Decision.Historical
        assertEquals(listOf(f.missingNeed.id), display.display.missingNeedIds)
        assertEquals(2, display.display.unmodeledNeedIds.size)
        assertEquals(30000L, display.display.groups.single().observedHistoricalSubtotalMinor)
    }

    @Test fun t08DuplicateOrForgedSelectionRejectedWithoutDisplay() = Fixture().use { f ->
        val c = f.choose()
        assertTrue(f.host().project(f.draft, listOf(c, c), 1000L, 200L) is Decision.Rejected)
        assertTrue(f.host().project(f.draft,
            listOf(c.copy(cartFingerprint = "forged")), 1000L, 200L) is Decision.Rejected)
    }
}
