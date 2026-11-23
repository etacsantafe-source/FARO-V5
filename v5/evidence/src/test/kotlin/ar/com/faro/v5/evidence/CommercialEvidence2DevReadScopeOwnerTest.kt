package ar.com.faro.v5.evidence

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic owner source; real graph binding and Android issuance NOT tested. */
class CommercialEvidence2DevReadScopeOwnerTest {
    private val product = CommercialProductKey("sku-yerba")
    private val bound = CommercialEvidence2DevReadScopeOwner.Identity(
        "local-dev", "generation-a", 9L, "epoch-a",
    )
    private var identity = bound
    private var ready = true
    private var trusted = true
    private val observation = PortableCommercialTypedObservation(
        PortableCommercialObservation(
            "sku-yerba", "Comercio Santa Fe", "ARS", 15000L, "MANUAL",
            "manual:receipt", 1000L, 1100L,
        ),
        CommercialEvidenceTypedTerms(
            localityKind = CommercialLocalityKind.KNOWN,
            localityCode = "AR-SF",
            presentationQuantityThousandths = 1000L,
            presentationUnit = CommercialPresentationUnit.UNIT,
            packCount = 1, paymentMethod = CommercialPaymentMethod.CASH,
            eligibility = CommercialEligibility.GENERAL,
            promotionKind = CommercialPromotionKind.NONE,
        ),
    )
    private var snapshot = CommercialEvidenceStagedTypedPortabilitySnapshot(
        2, "a".repeat(64), listOf(observation),
    )
    private fun owner() = CommercialEvidence2DevReadScopeOwner(
        bound.principal, bound.generationId, bound.pointerSequence, bound.hostEpoch,
        { identity }, { ready }, CommercialEvidenceStagedTypedPortabilityPort { snapshot },
    )

    @Test fun t01TypedProductIssuedAndRecheckedWithIndependentNonce() {
        val lease = owner()
        val nonce = checkNotNull(lease.issueAfterTrustedOwnerDecision(product) { trusted })
        assertTrue(lease.admitted(product, nonce))
        assertFalse(lease.admitted(CommercialProductKey("other"), nonce))
        assertTrue(lease.isRevoked())
    }

    @Test fun t02LegacyNullTermsMissingProductAndUntrustedAdmissionDenied() {
        val old = owner()
        snapshot = snapshot.copy(records = listOf(observation.copy(terms = null)))
        assertNull(old.issueAfterTrustedOwnerDecision(product) { true })
        assertTrue(old.isRevoked())
        snapshot = snapshot.copy(records = listOf(observation))
        val missing = owner()
        assertNull(missing.issueAfterTrustedOwnerDecision(CommercialProductKey("missing")) { true })
        assertTrue(missing.isRevoked())
        trusted = false
        val noHost = owner()
        assertNull(noHost.issueAfterTrustedOwnerDecision(product) { trusted })
        assertTrue(noHost.isRevoked())
    }

    @Test fun t03SourceWatermarkEpochAbaAndOwnerReadyRevokePermanently() {
        val first = owner()
        val nonce = checkNotNull(first.issueAfterTrustedOwnerDecision(product) { true })
        snapshot = snapshot.copy(sourceWatermark = "b".repeat(64))
        assertFalse(first.admitted(product, nonce))
        snapshot = snapshot.copy(sourceWatermark = "a".repeat(64))
        assertFalse(first.admitted(product, nonce))
        val second = owner()
        val nonce2 = checkNotNull(second.issueAfterTrustedOwnerDecision(product) { true })
        assertNotEquals(nonce, nonce2)
        identity = bound.copy(hostEpoch = "epoch-b")
        assertFalse(second.admitted(product, nonce2))
        identity = bound
        assertFalse(second.admitted(product, nonce2))
        val third = owner()
        val nonce3 = checkNotNull(third.issueAfterTrustedOwnerDecision(product) { true })
        ready = false
        assertFalse(third.admitted(product, nonce3))
    }
}
