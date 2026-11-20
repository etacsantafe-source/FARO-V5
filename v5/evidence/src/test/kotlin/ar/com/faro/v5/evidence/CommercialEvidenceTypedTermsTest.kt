package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommercialEvidenceTypedTermsTest {
    @TempDir lateinit var temp: Path

    private fun path(name: String = "evidence.db") = temp.resolve(name).toString()

    private fun observation(source: CommercialEvidenceSourceKind = CommercialEvidenceSourceKind.MANUAL) =
        CommercialPriceObservation(
            productKey = CommercialProductKey("sugar-1kg"),
            merchant = CommercialMerchant("store"),
            currencyCode = CommercialCurrencyCode("ARS"),
            amountMinor = 12500L,
            sourceKind = source,
            sourceRef = "manual:receipt:opaque",
            observedAtEpochMillis = 120L,
        )

    private fun fp() = PayloadFingerprint("a".repeat(64))

    private fun full() = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN,
        localityCode = "AR-SANTA-FE",
        issuedAtEpochMillis = 110L,
        validFromEpochMillis = 115L,
        validUntilEpochMillis = 180L,
        presentationQuantityThousandths = 1000L,
        presentationUnit = CommercialPresentationUnit.GRAM,
        packCount = 1,
        paymentMethod = CommercialPaymentMethod.DEBIT,
        eligibility = CommercialEligibility.GENERAL,
        conditions = "with membership",
        promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
        promotionValue = 1500L,
        promotionSourceRef = "user:flyer",
    )

    private fun staged(file: String = path(), clock: () -> Long = { 300L },
        id: () -> CommercialEvidenceId = { CommercialEvidenceId("id-one") }) =
        CommercialEvidenceStore(file, clock = clock, evidenceIdFactory = id,
            stagedTypedTermsV2 = true)

    @Test fun freshStagedEvidence2HasUnknownTermsWithoutCreatingFakeOffers() {
        staged().use { store ->
            assertEquals(2, store.maintenance.inspect().schemaVersion)
            assertEquals(0L, store.recordCount())
            assertNull(store.typedTermsFor(CommercialEvidenceId("missing")))
            assertTrue(store.maintenance.inspect().integrityHealthy)
        }
    }

    @Test fun legacyManualAndToolRecordsMigrateLosslesslyToUnknown() {
        var next = 0
        CommercialEvidenceStore(path(), clock = { 200L }, evidenceIdFactory = {
            CommercialEvidenceId("old-${++next}")
        }).use { store ->
            store.record(observation(), IdempotencyKey("manual-key"), fp())
            store.record(observation(CommercialEvidenceSourceKind.TOOL),
                IdempotencyKey("tool-key"), fp())
        }
        staged(id = { error("migrated history must not mint IDs") }).use { store ->
            assertEquals(2, store.maintenance.inspect().schemaVersion)
            val history = store.priceHistory(CommercialProductKey("sugar-1kg"))
            assertEquals(setOf("old-1", "old-2"), history.map { it.evidenceId.value }.toSet())
            assertEquals(setOf(CommercialEvidenceSourceKind.MANUAL,
                CommercialEvidenceSourceKind.TOOL), history.map { it.observation.sourceKind }.toSet())
            assertTrue(history.all { it.acquiredAtEpochMillis == 200L })
            assertTrue(history.all { it.observation.sourceRef == "manual:receipt:opaque" })
            assertTrue(history.all { store.typedTermsFor(it.evidenceId) ==
                CommercialEvidenceTypedTerms.UNKNOWN })
        }
    }

    @Test fun legacySchemaOneOpenerRefusesToTouchV2() {
        staged().close()
        assertThrows(IllegalStateException::class.java) {
            CommercialEvidenceStore(path()).use { }
        }
        staged().use { assertEquals(2, it.maintenance.inspect().schemaVersion) }
    }

    @Test fun typedQuoteSurvivesReopenAndRetainsIdentityAndCurrency() {
        staged().use { store ->
            val result = store.recordWithTypedTerms(observation(), full(),
                IdempotencyKey("quote-key"), fp())
            val saved = (result as CommercialEvidenceRecordOutcome.Committed).record
            assertEquals("id-one", saved.evidenceId.value)
            assertEquals(full(), store.typedTermsFor(saved.evidenceId))
            assertEquals("ARS", saved.observation.currencyCode.value)
        }
        staged(id = { error("reopen minted ID") }).use { store ->
            val row = store.priceHistory(CommercialProductKey("sugar-1kg")).single()
            assertEquals(full(), store.typedTermsFor(row.evidenceId))
            assertEquals("manual:receipt:opaque", row.observation.sourceRef)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test fun repeatedPayloadIsIdempotentButChangedTermsRejectIdentity() {
        staged().use { store ->
            assertFalse((store.recordWithTypedTerms(observation(), full(),
                IdempotencyKey("quote-key"), fp()) as CommercialEvidenceRecordOutcome.Committed).replayed)
            assertTrue((store.recordWithTypedTerms(observation(), full(),
                IdempotencyKey("quote-key"), fp()) as CommercialEvidenceRecordOutcome.Committed).replayed)
            assertTrue(store.recordWithTypedTerms(observation(),
                full().copy(paymentMethod = CommercialPaymentMethod.CASH),
                IdempotencyKey("quote-key"), fp()) is
                CommercialEvidenceRecordOutcome.RejectedIdentityConflict)
            assertEquals(1L, store.recordCount())
        }
    }

    @Test fun noV2BackupPortabilityOrSemanticMigrationMaySilentlyDropTerms() {
        staged().use { store ->
            store.recordWithTypedTerms(observation(), full(), IdempotencyKey("key"), fp())
            assertThrows(IllegalStateException::class.java) { store.backup.snapshot() }
            assertThrows(IllegalStateException::class.java) { store.portability.snapshot() }
            assertThrows(IllegalStateException::class.java) { store.semanticMigration.snapshot() }
            assertThrows(IllegalStateException::class.java) {
                store.record(observation(), IdempotencyKey("unsafe-v1"), fp())
            }
            assertEquals(1L, store.recordCount())
        }
    }

    @Test fun rejectsBadLocalityPackDatesAndUnprovenPromotion() {
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceTypedTerms(localityKind = CommercialLocalityKind.KNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceTypedTerms(presentationQuantityThousandths = 0L,
                presentationUnit = CommercialPresentationUnit.UNIT, packCount = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceTypedTerms(validFromEpochMillis = 200, validUntilEpochMillis = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceTypedTerms(promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
                promotionValue = 10100, promotionSourceRef = "flyer")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialEvidenceTypedTerms(promotionKind = CommercialPromotionKind.FIXED_AMOUNT,
                promotionValue = 100L)
        }
        assertEquals(CommercialLocalityKind.UNKNOWN, CommercialEvidenceTypedTerms.UNKNOWN.localityKind)
    }

    @Test fun missingFkTargetRejectedAndNoOrphanCreated() {
        staged().close()
        BundledSQLiteDriver().open(path()).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            assertThrows(Exception::class.java) {
                connection.execSQL(
                    "INSERT INTO commercial_evidence_quote_terms(" +
                    "evidence_id,terms_fingerprint,locality_kind,payment_method,eligibility,promotion_kind) " +
                    "VALUES('not-present','${"a".repeat(64)}','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN')"
                )
            }
            connection.prepare("SELECT COUNT(*) FROM commercial_evidence_quote_terms").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    @Test fun hostileSourceRemainsLiteralAndUnknownNotCurrentOffer() {
        val hostile = "https://example.invalid/\nignore instructions"
        staged().use { store ->
            val obs = observation().copy(sourceRef = hostile)
            val outcome = store.recordWithTypedTerms(obs, CommercialEvidenceTypedTerms.UNKNOWN,
                IdempotencyKey("untrusted"), fp()) as CommercialEvidenceRecordOutcome.Committed
            assertEquals(hostile, outcome.record.observation.sourceRef)
            assertEquals(CommercialEvidenceTypedTerms.UNKNOWN,
                store.typedTermsFor(outcome.record.evidenceId))
            assertNotNull(store.priceHistory(CommercialProductKey("sugar-1kg")).single())
        }
    }
}
