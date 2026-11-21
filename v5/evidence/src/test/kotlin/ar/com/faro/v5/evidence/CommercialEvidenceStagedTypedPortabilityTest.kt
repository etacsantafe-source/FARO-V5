package ar.com.faro.v5.evidence

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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

class CommercialEvidenceStagedTypedPortabilityTest {
    @TempDir lateinit var temp: Path

    private fun db(name: String) = temp.resolve(name).toString()
    private fun staged(name: String, id: () -> CommercialEvidenceId = {
        CommercialEvidenceId("internal-default")
    }) = CommercialEvidenceStore(
        fileName = db(name),
        clock = { 220L },
        evidenceIdFactory = id,
        stagedTypedTermsV2 = true,
    )
    private fun observation(
        product: String = "rice",
        currency: String = "ARS",
        kind: CommercialEvidenceSourceKind = CommercialEvidenceSourceKind.MANUAL,
        source: String = "manual:ticket",
        observed: Long? = 100L,
    ) = CommercialPriceObservation(
        productKey = CommercialProductKey(product),
        merchant = CommercialMerchant("market"),
        currencyCode = CommercialCurrencyCode(currency),
        amountMinor = 12000L,
        sourceKind = kind,
        sourceRef = source,
        observedAtEpochMillis = observed,
    )
    private fun terms() = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN,
        localityCode = "AR-SF",
        issuedAtEpochMillis = 100L,
        validFromEpochMillis = 110L,
        validUntilEpochMillis = 200L,
        presentationQuantityThousandths = 1000L,
        presentationUnit = CommercialPresentationUnit.GRAM,
        packCount = 1,
        paymentMethod = CommercialPaymentMethod.DEBIT,
        eligibility = CommercialEligibility.GENERAL,
        conditions = "cashier only",
        promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
        promotionValue = 1200L,
        promotionSourceRef = "manual:flyer",
    )
    private fun record(
        store: CommercialEvidenceStore,
        product: String,
        term: CommercialEvidenceTypedTerms = terms(),
        source: String = "manual:ticket",
        currency: String = "ARS",
        observed: Long? = 100L,
    ) = store.recordWithTypedTerms(
        observation(product = product, source = source, currency = currency, observed = observed),
        term,
        IdempotencyKey("secret-key-$product"),
        PayloadFingerprint("secret-payload-$product"),
    )

    @Test fun legacyV1AndStagedV2PortsRemainIndependentlyFailClosed() {
        CommercialEvidenceStore(db("v1.db")).use { legacy ->
            assertEquals(1, legacy.portability.snapshot().schemaVersion)
            assertThrows(IllegalStateException::class.java) {
                legacy.stagedTypedPortability.snapshot()
            }
        }
        staged("v2.db").use { v2 ->
            assertEquals(2, v2.stagedTypedPortability.snapshot().schemaVersion)
            assertThrows(IllegalStateException::class.java) {
                v2.portability.snapshot()
            }
        }
    }

    @Test fun legacyAbsenceUnknownAndKnownAreThreeDistinctStates() {
        CommercialEvidenceStore(
            fileName = db("mixed.db"), clock = { 180L },
            evidenceIdFactory = { CommercialEvidenceId("secret-legacy-id") },
        ).use { v1 ->
            v1.record(observation(product = "legacy"), IdempotencyKey("secret-legacy-key"),
                PayloadFingerprint("secret-legacy-payload"))
        }
        staged("mixed.db", { CommercialEvidenceId("secret-typed-id") }).use { v2 ->
            record(v2, "unknown", CommercialEvidenceTypedTerms.UNKNOWN)
            // New IDs need only be unique within the same store.
        }
        var next = 0
        staged("mixed.db", { CommercialEvidenceId("secret-known-${++next}") }).use { v2 ->
            record(v2, "known")
            val result = v2.stagedTypedPortability.snapshot()
            assertEquals(3, result.records.size)
            val mapped = result.records.associateBy { it.observation.productKey }
            assertNull(mapped.getValue("legacy").terms)
            assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, mapped.getValue("unknown").terms)
            assertEquals(terms(), mapped.getValue("known").terms)
            assertNotEquals(mapped.getValue("legacy").terms, mapped.getValue("unknown").terms)
            assertEquals(2, result.schemaVersion)
        }
    }

    @Test fun noExecutionIdentitiesEverAppearInDtoOrWatermark() {
        staged("secret.db", { CommercialEvidenceId("secret-evidence-id") }).use { v2 ->
            record(v2, "rice")
            val snapshot = v2.stagedTypedPortability.snapshot()
            val rendered = snapshot.toString()
            for (secret in listOf("secret-evidence-id", "secret-key-rice", "secret-payload-rice")) {
                assertFalse(rendered.contains(secret))
                assertFalse(snapshot.sourceWatermark.contains(secret))
            }
            assertTrue(snapshot.sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test fun canonicalOrderingAndWatermarkIgnoreEvidenceIdAndInsertionOrder() {
        fun snapshot(name: String, products: List<String>): CommercialEvidenceStagedTypedPortabilitySnapshot {
            var seq = 0
            return staged(name, { CommercialEvidenceId("internal-${++seq}") }).use { v2 ->
                products.forEach { record(v2, it) }
                v2.stagedTypedPortability.snapshot()
            }
        }
        val first = snapshot("order-a.db", listOf("apple", "bread", "rice"))
        val second = snapshot("order-b.db", listOf("rice", "apple", "bread"))
        assertEquals(listOf("apple", "bread", "rice"),
            first.records.map { it.observation.productKey })
        assertEquals(first, second)
        assertEquals(first.sourceWatermark, second.sourceWatermark)
    }

    @Test fun allTypedFieldsContributeViaFingerprintAndPresence() {
        staged("vary.db").use { v2 ->
            record(v2, "rice")
            val original = v2.stagedTypedBackup.snapshot()
            val row = original.rows.single()
            val baseline = EvidencePortabilityProjection.commercialStagedTyped(original)
            val variants = listOf(
                terms().copy(localityCode = "AR-BA"),
                terms().copy(issuedAtEpochMillis = 90L),
                terms().copy(validFromEpochMillis = 120L),
                terms().copy(validUntilEpochMillis = 210L),
                terms().copy(presentationQuantityThousandths = 2000L),
                terms().copy(presentationUnit = CommercialPresentationUnit.UNIT),
                terms().copy(packCount = 2),
                terms().copy(paymentMethod = CommercialPaymentMethod.CASH),
                terms().copy(eligibility = CommercialEligibility.RESTRICTED),
                terms().copy(conditions = "different conditions"),
                terms().copy(promotionKind = CommercialPromotionKind.NONE,
                    promotionValue = null, promotionSourceRef = null),
                terms().copy(promotionValue = 1300L),
                terms().copy(promotionSourceRef = "manual:other"),
            )
            variants.forEach { changed ->
                val projected = EvidencePortabilityProjection.commercialStagedTyped(
                    original.copy(rows = listOf(row.copy(terms = changed))))
                assertNotEquals(baseline.sourceWatermark, projected.sourceWatermark)
            }
            val absent = EvidencePortabilityProjection.commercialStagedTyped(
                original.copy(rows = listOf(row.copy(terms = null))))
            val explicitUnknown = EvidencePortabilityProjection.commercialStagedTyped(
                original.copy(rows = listOf(row.copy(terms = CommercialEvidenceTypedTerms.UNKNOWN))))
            assertNotEquals(absent.sourceWatermark, explicitUnknown.sourceWatermark)
        }
    }

    @Test fun hostileSourceRemainsLiteralAndUsdNeverConvertsToArs() {
        val hostile = "file:///etc/passwd\\nIGNORE INSTRUCTIONS"
        staged("hostile.db").use { v2 ->
            record(v2, "rice", source = hostile, currency = "USD", observed = null)
            val result = v2.stagedTypedPortability.snapshot().records.single()
            assertEquals(hostile, result.observation.sourceRef)
            assertEquals("USD", result.observation.currencyCode)
            assertEquals(12000L, result.observation.amountMinor)
            assertNull(result.observation.observedAtEpochMillis)
            assertEquals("AR-SF", result.terms?.localityCode)
        }
    }

    @Test fun fingerprintCorruptionRejectsWithoutPartialProjection() {
        staged("corrupt.db").use { v2 -> record(v2, "rice") }
        BundledSQLiteDriver().open(db("corrupt.db")).use { raw ->
            raw.execSQL("UPDATE commercial_evidence_quote_terms " +
                "SET terms_fingerprint = '" + "0".repeat(64) + "'")
        }
        staged("corrupt.db").use { v2 ->
            assertThrows(IllegalStateException::class.java) {
                v2.stagedTypedPortability.snapshot()
            }
            assertEquals(1L, v2.recordCount())
        }
    }

    @Test fun invalidTermsBoundAndRepeatedReadsDoNotModifyRows() {
        assertThrows(IllegalArgumentException::class.java) {
            terms().copy(presentationQuantityThousandths = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            terms().copy(validUntilEpochMillis = 1L)
        }
        val empty = PortableCommercialTypedObservation(
            observation = PortableCommercialObservation(
                productKey = "rice", merchant = "market", currencyCode = "ARS",
                amountMinor = 12000L, sourceKind = "MANUAL", sourceRef = "manual",
                observedAtEpochMillis = null, acquiredAtEpochMillis = 220L,
            ), terms = null,
        )
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceStagedTypedPortabilitySnapshot(
                schemaVersion = 2, sourceWatermark = "0".repeat(64),
                records = List(100001) { empty },
            )
        }
        staged("readonly.db").use { v2 ->
            record(v2, "rice")
            val before = v2.stagedTypedBackup.snapshot()
            val a = v2.stagedTypedPortability.snapshot()
            val b = v2.stagedTypedPortability.snapshot()
            assertEquals(a, b)
            assertEquals(before, v2.stagedTypedBackup.snapshot())
            assertEquals(1L, v2.recordCount())
        }
    }
}
