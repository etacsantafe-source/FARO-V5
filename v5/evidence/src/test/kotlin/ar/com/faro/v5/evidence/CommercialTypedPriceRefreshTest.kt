package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommercialTypedPriceRefreshTest {
    @TempDir lateinit var root: Path

    private val product = CommercialProductKey("rice-500")

    private inner class Fixture(
        name: String = "commercial-c3.db",
        v2: Boolean = true,
    ) : AutoCloseable {
        val filename = root.resolve(name).toString()
        val store = CommercialEvidenceStore(
            filename, clock = { 1000L }, stagedTypedTermsV2 = v2,
        )
        var householdAllowed = true
        var commercialAllowed = true
        // Explicit test-only admission: a permitted product may still lack history.
        var otherProductGranted = false
        var lateRevocation = false
        var fence = CommercialTypedRefreshFence("gen-1", 1L, "epoch-1", "principal-1")
        val service = CommercialTypedPriceRefreshService(
            store,
            BuiltInContractCatalog.create(),
            "principal-1",
            { fence },
            { key ->
                (key == product || (otherProductGranted && key.value == "other-product")) &&
                    householdAllowed
            },
            { key ->
                (key == product || (otherProductGranted && key.value == "other-product")) &&
                    commercialAllowed &&
                    (!lateRevocation || store.recordCount() == 1L)
            },
        )

        init {
            if (v2) {
                val result = store.recordWithTypedTerms(
                    CommercialPriceObservation(
                        product, CommercialMerchant("Market Old"),
                        CommercialCurrencyCode("ARS"), 13000L,
                        CommercialEvidenceSourceKind.MANUAL, "ticket:old", 700L,
                    ),
                    CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("seed-c3"),
                    PayloadFingerprint("seed-c3-payload"),
                )
                assertTrue(result is CommercialEvidenceRecordOutcome.Committed)
            }
        }

        fun input(
            amount: Long = 16000,
            observed: Long? = 800,
            sourceKind: CommercialEvidenceSourceKind = CommercialEvidenceSourceKind.MANUAL,
            requestId: String = "new-quote-1",
            productKey: CommercialProductKey = product,
            terms: CommercialEvidenceTypedTerms = CommercialEvidenceTypedTerms.UNKNOWN,
        ) = CommercialTypedRefreshInput(
            CommercialPriceObservation(
                productKey, CommercialMerchant("Market New"),
                CommercialCurrencyCode("ARS"), amount, sourceKind,
                "file:///receipt.txt\nquoted text only", observed,
            ),
            terms,
            requestId,
            900L,
        )

        fun prepare(input: CommercialTypedRefreshInput = input()):
            CommercialTypedRefreshPreparation = service.prepare(input)

        fun draft(input: CommercialTypedRefreshInput = input()): PreparedCommercialTypedRefresh =
            assertInstanceOf(
                CommercialTypedRefreshPreparation.Ready::class.java,
                prepare(input),
            ).draft

        fun apply(
            draft: PreparedCommercialTypedRefresh = draft(),
            confirmed: () -> Boolean = { true },
        ): CommercialTypedRefreshResult = service.confirmAndAppend(draft, confirmed)

        override fun close() = store.close()
    }

    @Test fun appendCreatesSecondObservationAndRetainsOriginalAndTerms() =
        Fixture().use { f ->
            val before = f.store.priceHistory(product).single()
            val t = CommercialEvidenceTypedTerms(
                localityKind = CommercialLocalityKind.KNOWN,
                localityCode = "AR-SF",
                validFromEpochMillis = 700,
                validUntilEpochMillis = 2000,
                presentationQuantityThousandths = 1000,
                presentationUnit = CommercialPresentationUnit.UNIT,
                packCount = 1,
                paymentMethod = CommercialPaymentMethod.DEBIT,
                eligibility = CommercialEligibility.GENERAL,
                promotionKind = CommercialPromotionKind.NONE,
            )
            val result = assertInstanceOf(
                CommercialTypedRefreshResult.Appended::class.java,
                f.apply(f.draft(f.input(terms = t))),
            )
            assertFalse(result.replayed)
            assertEquals("APPENDED_NEW_OBSERVATION_NOT_SUPERSESSION", result.status)
            assertEquals(2, f.store.priceHistory(product).size)
            assertTrue(f.store.priceHistory(product).contains(before))
            assertEquals(t, f.store.typedTermsFor(result.record.evidenceId))
        }

    @Test fun preparingRefreshIsStrictlyReadOnly() = Fixture().use { f ->
        val before = f.store.stagedTypedPortability.snapshot()
        assertTrue(f.prepare() is CommercialTypedRefreshPreparation.Ready)
        assertEquals(before, f.store.stagedTypedPortability.snapshot())
        assertEquals(1L, f.store.recordCount())
    }

    @Test fun cancelHumanConfirmationPreventsAppend() = Fixture().use { f ->
        val result = f.apply(f.draft()) { false }
        assertEquals(CommercialTypedRefreshResult.Rejected(
            CommercialTypedRefreshRejection.NOT_CONFIRMED), result)
        assertEquals(1L, f.store.recordCount())
    }

    @Test fun householdAndCommercialGrantsAreIndependent() = Fixture().use { f ->
        f.householdAllowed = false
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.NOT_AUTHORIZED), f.prepare())
        f.householdAllowed = true
        val draft = f.draft()
        f.commercialAllowed = false
        assertEquals(CommercialTypedRefreshResult.Rejected(
            CommercialTypedRefreshRejection.NOT_AUTHORIZED), f.apply(draft))
        assertEquals(1L, f.store.recordCount())
    }

    @Test fun fenceSequenceOrEpochChangeRejectsReviewedIntent() = Fixture().use { f ->
        val draft = f.draft()
        f.fence = f.fence.copy(pointerSequence = 3L, hostEpoch = "epoch-2")
        assertEquals(CommercialTypedRefreshResult.Rejected(
            CommercialTypedRefreshRejection.STALE_FENCE), f.apply(draft))
        assertEquals(1L, f.store.recordCount())
    }

    @Test fun changedSourceWatermarkInvalidatesPreviousReview() = Fixture().use { f ->
        val draft = f.draft()
        f.store.recordWithTypedTerms(
            f.input(requestId = "concurrent").observation,
            CommercialEvidenceTypedTerms.UNKNOWN,
            IdempotencyKey("concurrent-intent"),
            PayloadFingerprint("concurrent-payload"),
        )
        val outcome = f.apply(draft)
        assertEquals(CommercialTypedRefreshResult.Rejected(
            CommercialTypedRefreshRejection.SOURCE_CHANGED), outcome)
        assertEquals(2L, f.store.recordCount())
    }

    @Test fun toolSourceAndMissingProductAreNotManualRefresh() = Fixture().use { f ->
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.INVALID_INPUT),
            f.prepare(f.input(sourceKind = CommercialEvidenceSourceKind.TOOL)))
        val unknown = f.input(productKey = CommercialProductKey("other-product"))
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.NOT_AUTHORIZED), f.prepare(unknown))
        f.otherProductGranted = true
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.NO_EXISTING_HISTORY), f.prepare(unknown))
        assertEquals(1L, f.store.recordCount())
    }

    @Test fun requestIdIsBoundedAndCannotContainControlCharacters() = Fixture().use { f ->
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.INVALID_INPUT),
            f.prepare(f.input(requestId = " bad ")))
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.INVALID_INPUT),
            f.prepare(f.input(requestId = "line\nbreak")))
        assertEquals(CommercialTypedRefreshPreparation.Rejected(
            CommercialTypedRefreshRejection.INVALID_INPUT),
            f.prepare(f.input(requestId = "x".repeat(161))))
    }

    @Test fun staleDraftCannotBeRepeatedToDuplicateTheSameRefresh() = Fixture().use { f ->
        val draft = f.draft()
        assertTrue(f.apply(draft) is CommercialTypedRefreshResult.Appended)
        assertEquals(2L, f.store.recordCount())
        assertEquals(CommercialTypedRefreshResult.Rejected(
            CommercialTypedRefreshRejection.SOURCE_CHANGED), f.apply(draft))
        assertEquals(2L, f.store.recordCount())
    }

    @Test fun nullObservedAtAndExplicitUnknownTermsStayUnknown() = Fixture().use { f ->
        val result = assertInstanceOf(CommercialTypedRefreshResult.Appended::class.java,
            f.apply(f.draft(f.input(observed = null))))
        assertEquals(null, result.record.observation.observedAtEpochMillis)
        assertEquals(1000L, result.record.acquiredAtEpochMillis)
        assertEquals(CommercialEvidenceTypedTerms.UNKNOWN,
            f.store.typedTermsFor(result.record.evidenceId))
        assertTrue(result.record.observation.sourceRef.startsWith("file://"))
    }

    @Test fun postCommitRevocationRequiresRecoveryNotFalseRollback() = Fixture().use { f ->
        f.lateRevocation = true
        val draft = f.draft()
        val result = f.apply(draft)
        assertEquals(CommercialTypedRefreshResult.RecoveryRequired(draft.idempotencyKey), result)
        assertEquals(2L, f.store.recordCount())
    }

    @Test fun persistedRefreshReopensAndLegacySchemaCannotAcceptTypedIntent() {
        val f = Fixture("reopen.db")
        val committed = assertInstanceOf(CommercialTypedRefreshResult.Appended::class.java,
            f.apply())
        val id = committed.record.evidenceId
        f.close()
        CommercialEvidenceStore(
            root.resolve("reopen.db").toString(),
            stagedTypedTermsV2 = true,
        ).use { reopened ->
            assertEquals(2, reopened.priceHistory(product).size)
            assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, reopened.typedTermsFor(id))
        }

        Fixture("legacy.db", v2 = false).use { legacy ->
            assertEquals(CommercialTypedRefreshPreparation.Rejected(
                CommercialTypedRefreshRejection.STORE_UNAVAILABLE), legacy.prepare())
            assertEquals(0L, legacy.store.recordCount())
        }
    }
}
