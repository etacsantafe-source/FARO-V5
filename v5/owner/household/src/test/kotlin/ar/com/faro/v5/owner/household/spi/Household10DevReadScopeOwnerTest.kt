package ar.com.faro.v5.owner.household.spi

import ar.com.faro.v5.owner.household.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic sources test scope invariants, NOT positive Android owner ACL. */
class Household10DevReadScopeOwnerTest {
    private val id = HouseholdNeedId("need-yerba")
    private fun row(status: HouseholdNeedStatus = HouseholdNeedStatus.OPEN) =
        HouseholdNeed(id, HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(1000L, HouseholdNeedUnit.UNIT),
            HouseholdNeedPriority.NORMAL, status, 2L)
    private val bound = Household10DevReadScopeOwner.Identity(
        "local-dev", "generation-a", 9L, "epoch-a",
    )
    private var identity = bound
    private var ready = true
    private var trusted = true
    private var snapshot = HouseholdNeedsSnapshot(3L, listOf(row()))

    private fun owner() = Household10DevReadScopeOwner(
        bound.principal, bound.generationId, bound.pointerSequence, bound.hostEpoch,
        { identity }, { ready }, { snapshot },
    )

    @Test fun t01OwnerAdmitsOnlyOpenSelectedRealSnapshotWithFreshNonce() {
        val owner = owner()
        val nonce = assertNotNull(owner.issueAfterTrustedOwnerDecision(id) { trusted })
        assertTrue(owner.admitted(id, nonce))
        assertNull(owner.issueAfterTrustedOwnerDecision(id) { true })
        assertTrue(owner.isRevoked())
    }

    @Test fun t02MissingCompletedAndUntrustedNeedNeverIssue() {
        val missing = owner()
        assertNull(missing.issueAfterTrustedOwnerDecision(HouseholdNeedId("missing")) { true })
        assertTrue(missing.isRevoked())
        snapshot = HouseholdNeedsSnapshot(3L, listOf(row(HouseholdNeedStatus.COMPLETED)))
        val completed = owner()
        assertNull(completed.issueAfterTrustedOwnerDecision(id) { true })
        assertTrue(completed.isRevoked())
        snapshot = HouseholdNeedsSnapshot(3L, listOf(row()))
        val untrusted = owner()
        trusted = false
        assertNull(untrusted.issueAfterTrustedOwnerDecision(id) { trusted })
        assertTrue(untrusted.isRevoked())
    }

    @Test fun t03EpochPointerAbaRowChangeAndOwnerRevocationFailClosed() {
        val owner = owner()
        val nonce = assertNotNull(owner.issueAfterTrustedOwnerDecision(id) { true })
        identity = bound.copy(pointerSequence = 10L)
        assertFalse(owner.admitted(id, nonce))
        identity = bound
        assertFalse(owner.admitted(id, nonce))
        val changed = owner()
        val token = assertNotNull(changed.issueAfterTrustedOwnerDecision(id) { trusted })
        snapshot = HouseholdNeedsSnapshot(4L, listOf(row().copy(revision = 3L)))
        assertFalse(changed.admitted(id, token))
        snapshot = HouseholdNeedsSnapshot(3L, listOf(row()))
        assertFalse(changed.admitted(id, token))
        val revoked = owner()
        val third = assertNotNull(revoked.issueAfterTrustedOwnerDecision(id) { trusted })
        ready = false
        assertFalse(revoked.admitted(id, third))
    }
}
