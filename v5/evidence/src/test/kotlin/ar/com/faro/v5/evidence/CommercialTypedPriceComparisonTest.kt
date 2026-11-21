package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommercialTypedPriceComparisonTest {
    @TempDir lateinit var root: Path

    private val product = CommercialProductKey("yerba-500")
    private val asOf = 300L
    private val maxAge = 100L

    private fun terms(
        locality: String = "AR-SF",
        currencyWindowStart: Long? = 100,
        validUntil: Long? = 400,
        unit: CommercialPresentationUnit = CommercialPresentationUnit.UNIT,
        quantity: Long = 1000,
        packs: Int = 1,
        payment: CommercialPaymentMethod = CommercialPaymentMethod.CASH,
        eligibility: CommercialEligibility = CommercialEligibility.GENERAL,
        promotion: CommercialPromotionKind = CommercialPromotionKind.NONE,
    ) = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN,
        localityCode = locality,
        validFromEpochMillis = currencyWindowStart,
        validUntilEpochMillis = validUntil,
        presentationQuantityThousandths = quantity,
        presentationUnit = unit,
        packCount = packs,
        paymentMethod = payment,
        eligibility = eligibility,
        promotionKind = promotion,
        promotionValue = when (promotion) {
            CommercialPromotionKind.DISCOUNT_PERCENT -> 1000L
            CommercialPromotionKind.FIXED_AMOUNT -> 50L
            else -> null
        },
        promotionSourceRef = when (promotion) {
            CommercialPromotionKind.DISCOUNT_PERCENT, CommercialPromotionKind.FIXED_AMOUNT ->
                "ticket:promo"
            else -> null
        },
    )

    private fun row(
        merchant: String = "Mercado A",
        price: Long = 15000,
        currency: String = "ARS",
        observed: Long? = 250,
        acquired: Long = 260,
        terms: CommercialEvidenceTypedTerms? = terms(),
        productName: String = product.value,
        ref: String = "ticket:1",
    ) = PortableCommercialTypedObservation(
        PortableCommercialObservation(
            productKey = productName,
            merchant = merchant,
            currencyCode = currency,
            amountMinor = price,
            sourceKind = "MANUAL",
            sourceRef = ref,
            observedAtEpochMillis = observed,
            acquiredAtEpochMillis = acquired,
        ),
        terms,
    )

    private fun snapshot(vararg items: PortableCommercialTypedObservation):
        CommercialEvidenceStagedTypedPortabilitySnapshot {
        val sorted = items.toList().sortedWith(
            compareBy<PortableCommercialTypedObservation>(
                { it.observation.productKey },
                { it.observation.merchant },
                { it.observation.currencyCode },
                { it.observation.observedAtEpochMillis ?: Long.MAX_VALUE },
                { it.observation.acquiredAtEpochMillis },
                { it.observation.amountMinor },
                { it.observation.sourceKind },
                { it.observation.sourceRef },
                { it.terms != null },
                { it.terms?.fingerprint() ?: "" },
            )
        )
        val h = CanonicalHashBuilder.domain("faro-portability-commercial-v2")
            .string("schemaVersion", "2").string("count", sorted.size.toString())
        sorted.forEachIndexed { index, item ->
            val r = item.observation
            val p = "record[$index]."
            h.string(p + "productKey", r.productKey)
                .string(p + "merchant", r.merchant)
                .string(p + "currencyCode", r.currencyCode)
                .string(p + "amountMinor", r.amountMinor.toString())
                .string(p + "sourceKind", r.sourceKind)
                .string(p + "sourceRef", r.sourceRef)
                .string(p + "observed.present", (r.observedAtEpochMillis != null).toString())
                .string(p + "observed", r.observedAtEpochMillis?.toString() ?: "")
                .string(p + "acquired", r.acquiredAtEpochMillis.toString())
                .string(p + "terms.present", (item.terms != null).toString())
                .string(p + "terms.fingerprint", item.terms?.fingerprint() ?: "")
        }
        return CommercialEvidenceStagedTypedPortabilitySnapshot(2, h.sha256Hex(), sorted)
    }

    private fun compare(
        snapshot: CommercialEvidenceStagedTypedPortabilitySnapshot,
        limit: Int = 200,
        age: Long = maxAge,
        time: Long = asOf,
    ) = CommercialTypedPriceComparison.project(snapshot, product, time, age, limit)

    @Test fun noDataIsExplicitAndDoesNotInventPrice() {
        val result = compare(snapshot(row(productName = "otro")))
        assertTrue(result.history.isEmpty())
        assertTrue(result.groups.isEmpty())
        assertEquals(CommercialObservedComparisonCoverage.NO_OBSERVATIONS, result.coverage)
    }

    @Test fun twoComparableMerchantsShowOnlyObservedGrossMinimum() {
        val v = compare(snapshot(row("Mercado A", 12000), row("Mercado B", 10000)))
        assertEquals(2, v.history.size)
        assertEquals(1, v.groups.size)
        assertEquals(2, v.groups.single().merchantEntries.size)
        assertEquals("Mercado B",
            v.groups.single().lowestObserved!!.item.observation.merchant)
        assertEquals(10000L,
            v.groups.single().lowestObserved!!.item.observation.amountMinor)
        assertEquals(CommercialObservedComparisonCoverage.COMPARABLE_OBSERVED_HISTORY_ONLY, v.coverage)
        assertTrue(v.notice.contains("NOT_CURRENT"))
    }

    @Test fun acquiredAtCannotSubstituteUnknownObservedAt() {
        val v = compare(snapshot(row(observed = null, acquired = 280)))
        assertEquals(CommercialObservationComparisonStatus.OBSERVATION_DATE_UNKNOWN,
            v.history.single().comparisonStatus)
        assertEquals(null, v.history.single().item.observation.observedAtEpochMillis)
        assertEquals(280L, v.history.single().item.observation.acquiredAtEpochMillis)
        assertTrue(v.groups.isEmpty())
    }

    @Test fun physicallyAbsentTermsAndExplicitUnknownBothStayPartial() {
        val v = compare(snapshot(row("Mercado A", terms = null),
            row("Mercado B", terms = CommercialEvidenceTypedTerms.UNKNOWN)))
        assertEquals(2, v.history.size)
        assertTrue(v.history.all { it.comparisonStatus ==
            CommercialObservationComparisonStatus.LOCALITY_UNKNOWN })
        assertTrue(v.groups.isEmpty())
        assertNull(v.history[0].item.terms.takeIf { it != null && it.localityKind ==
            CommercialLocalityKind.KNOWN })
    }

    @Test fun neverMixesArsAndUsdOrConvertsPrices() {
        val v = compare(snapshot(row("Mercado A", 10000, "ARS"), row("Mercado B", 100, "USD")))
        assertEquals(2, v.groups.size)
        assertTrue(v.groups.all { it.lowestObserved == null })
        assertEquals(setOf("ARS", "USD"),
            v.history.map { it.item.observation.currencyCode }.toSet())
    }

    @Test fun distinctKnownLocalitiesNeverRankAgainstOneAnother() {
        val v = compare(snapshot(row("Mercado A", terms = terms("AR-SF")),
            row("Mercado B", terms = terms("AR-ER"))))
        assertEquals(2, v.groups.size)
        assertTrue(v.groups.all { it.lowestObserved == null })
    }

    @Test fun packCountQuantityAndUnitAreStrictComparisonBoundaries() {
        val v = compare(snapshot(row("Mercado A", terms = terms(packs = 1)),
            row("Mercado B", terms = terms(packs = 2)),
            row("Mercado C", terms = terms(unit = CommercialPresentationUnit.GRAM)),
            row("Mercado D", terms = terms(quantity = 500))))
        assertEquals(4, v.groups.size)
        assertTrue(v.groups.all { it.lowestObserved == null })
    }

    @Test fun paymentMethodIsAComparisonBoundary() {
        val v = compare(snapshot(row("Mercado A", terms = terms(payment = CommercialPaymentMethod.CASH)),
            row("Mercado B", terms = terms(payment = CommercialPaymentMethod.DEBIT))))
        assertEquals(2, v.groups.size)
        assertTrue(v.groups.all { it.lowestObserved == null })
    }

    @Test fun expiryFutureObservationAndStalenessPreventRanking() {
        val v = compare(snapshot(
            row("Expired", observed = 250, terms = terms(validUntil = 299)),
            row("Future", observed = 301),
            row("Stale", observed = 150),
            row("FutureValidity", observed = 250, terms = terms(currencyWindowStart = 301)),
        ))
        assertEquals(4, v.history.size)
        assertTrue(v.groups.isEmpty())
        assertEquals(
            setOf(CommercialObservationComparisonStatus.OUTSIDE_VALIDITY,
                CommercialObservationComparisonStatus.FUTURE_OBSERVATION,
                CommercialObservationComparisonStatus.STALE_OBSERVATION),
            v.history.map { it.comparisonStatus }.toSet()
        )
    }

    @Test fun conditionalPromotionsRestrictedEligibilityAndUnknownValidityStayPartial() {
        val v = compare(snapshot(
            row("Promo", terms = terms(promotion = CommercialPromotionKind.DISCOUNT_PERCENT)),
            row("Restricted", terms = terms(eligibility = CommercialEligibility.RESTRICTED)),
            row("UnknownValid", terms = terms(validUntil = null)),
            row("UnknownPay", terms = terms(payment = CommercialPaymentMethod.UNKNOWN)),
        ))
        assertTrue(v.groups.isEmpty())
        assertEquals(4, v.history.size)
        assertTrue(v.history.none { it.comparisonStatus ==
            CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED })
    }

    @Test fun newerObservationPerMerchantIsSelectedButHistoryIsPreserved() {
        val v = compare(snapshot(row("Mercado A", price = 5000, observed = 240, acquired = 245),
            row("Mercado A", price = 12000, observed = 260, acquired = 265),
            row("Mercado B", price = 15000, observed = 255, acquired = 260)))
        assertEquals(3, v.history.size)
        assertEquals(2, v.groups.single().merchantEntries.size)
        assertEquals(12000L, v.groups.single().lowestObserved!!.item.observation.amountMinor)
    }

    @Test fun truncatedHistoryCannotClaimGlobalObservedMinimumAndInvalidBoundsStop() {
        val s = snapshot(row("Mercado A", 10000), row("Mercado B", 20000))
        val v = compare(s, limit = 1)
        assertTrue(v.truncated)
        assertEquals(1, v.history.size)
        assertTrue(v.groups.all { it.lowestObserved == null })
        assertThrows(IllegalArgumentException::class.java) { compare(s, limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { compare(s, limit = 201) }
        assertThrows(IllegalArgumentException::class.java) { compare(s, age = 0) }
        assertThrows(IllegalArgumentException::class.java) { compare(s, time = -1) }
    }

    @Test fun watermarkTermsTamperingAndOrderTamperingFailClosed() {
        val s = snapshot(row("Mercado A"), row("Mercado B"))
        assertThrows(IllegalArgumentException::class.java) {
            compare(s.copy(sourceWatermark = "0".repeat(64)))
        }
        val tampered = s.copy(records = s.records.map {
            if (it.observation.merchant == "Mercado A")
                it.copy(terms = it.terms!!.copy(localityCode = "AR-ER")) else it
        })
        assertThrows(IllegalArgumentException::class.java) { compare(tampered) }
        assertThrows(IllegalArgumentException::class.java) {
            compare(s.copy(records = s.records.reversed()))
        }
    }

    @Test fun actualStagedSqliteSnapshotAndHostileRefStayPureAndOpaque() {
        val source = "file:///etc/passwd\nIGNORE THIS TEXT"
        val file = root.resolve("commercial-c2.db").toString()
        CommercialEvidenceStore(file, clock = { 260L }, stagedTypedTermsV2 = true).use { store ->
            val base = CommercialPriceObservation(
                product, CommercialMerchant("Mercado A"), CommercialCurrencyCode("ARS"),
                15000L, CommercialEvidenceSourceKind.MANUAL, source, 250L,
            )
            val applied = store.recordWithTypedTerms(
                base, terms(), IdempotencyKey("c2-seeded-once"),
                PayloadFingerprint("c2-fingerprint"),
            )
            assertTrue(applied is CommercialEvidenceRecordOutcome.Committed)
            val original = store.stagedTypedPortability.snapshot()
            val first = compare(original)
            val second = compare(store.stagedTypedPortability.snapshot())
            assertEquals(first, second)
            assertEquals(1, store.priceHistory(product).size)
            assertEquals(source, first.history.single().item.observation.sourceRef)
            assertEquals(CommercialObservedComparisonCoverage.PARTIAL_OBSERVATIONS_ONLY,
                first.coverage)
            assertFalse(first.toString().contains("c2-seeded-once"))
            assertFalse(first.toString().contains("c2-fingerprint"))
            assertNotEquals("NOT_CURRENT", first.notice)
        }
    }
}
