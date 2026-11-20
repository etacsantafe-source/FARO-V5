package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04CommercialNeedProductSelectionTest {
    private val yerba = need("need-yerba", "Yerba")
    private val otherYerba = need("need-other", "Yerba")
    private val snapshot = HouseholdNeedsSnapshot(9L, listOf(yerba, otherYerba))
    private val fence = CommercialNeedReadFence("household10-dev", 4L, "epoch-2", "principal-1")
    private val premium = CommercialProductKey("sku-yerba-premium-500g")
    private val regular = CommercialProductKey("sku-yerba-standard-1kg")

    private fun need(id: String, name: String): HouseholdNeed = HouseholdNeed(
        HouseholdNeedId(id), HouseholdNeedName(name),
        HouseholdNeedQuantity(1_000L, HouseholdNeedUnit.PACKAGE),
        HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 3L,
    )

    private fun selected(
        observed: HouseholdNeedsSnapshot? = snapshot,
        id: HouseholdNeedId? = yerba.id,
        chosen: CommercialProductKey? = premium,
        presented: List<CommercialProductKey> = listOf(regular, premium),
        context: CommercialNeedReadFence? = fence,
        explicit: Boolean = true,
        ownerAdmitted: () -> Boolean = { true },
    ): CommercialNeedProductDecision = S4W04CommercialNeedProductSelection.select(
        observed, id, chosen, presented, context, explicit, ownerAdmitted,
    )

    private fun rejected(
        decision: CommercialNeedProductDecision,
        reason: CommercialNeedProductRejection,
    ) = assertEquals(CommercialNeedProductDecision.Rejected(reason), decision)

    @Test
    fun explicitSelectionUsesNeedIdentityNotItsHumanNameOrPack() {
        val result = selected() as CommercialNeedProductDecision.Candidate
        assertEquals(yerba.id, result.binding.need.id)
        assertEquals(premium, result.binding.productKey)
        assertEquals(9L, result.binding.householdRevision)
        assertEquals(fence, result.binding.readFence)
        assertEquals(CommercialNeedProductDecision.Candidate(result.binding),
            S4W04CommercialNeedProductSelection.revalidate(result.binding, snapshot, fence) { true })
    }

    @Test
    fun twoSameNamesRemainDistinctAndNeverAutoMatch() {
        val result = selected(id = otherYerba.id) as CommercialNeedProductDecision.Candidate
        assertEquals(otherYerba.id, result.binding.need.id)
        rejected(selected(explicit = false), CommercialNeedProductRejection.CLARIFICATION_REQUIRED)
        rejected(selected(chosen = null), CommercialNeedProductRejection.CLARIFICATION_REQUIRED)
        rejected(selected(id = null), CommercialNeedProductRejection.CLARIFICATION_REQUIRED)
    }

    @Test
    fun unknownDeletedAndDuplicatedNeedsNeverSelect() {
        rejected(selected(id = HouseholdNeedId("unknown")), CommercialNeedProductRejection.NEED_NOT_FOUND)
        rejected(selected(observed = snapshot.copy(needs = listOf(otherYerba))),
            CommercialNeedProductRejection.NEED_NOT_FOUND)
        rejected(selected(observed = snapshot.copy(needs = listOf(yerba, yerba))),
            CommercialNeedProductRejection.NEED_NOT_FOUND)
    }

    @Test
    fun completedNeedCannotAcquireNewProductCandidate() {
        val complete = yerba.copy(status = HouseholdNeedStatus.COMPLETED, revision = 4L)
        rejected(selected(observed = snapshot.copy(needs = listOf(complete, otherYerba))),
            CommercialNeedProductRejection.NEED_NOT_OPEN)
    }

    @Test
    fun refusesUnknownDuplicateOrUnboundedProductOptions() {
        rejected(selected(chosen = CommercialProductKey("unshown")),
            CommercialNeedProductRejection.PRODUCT_NOT_PRESENTED)
        rejected(selected(presented = listOf(premium, premium)),
            CommercialNeedProductRejection.PRODUCT_NOT_PRESENTED)
        rejected(selected(presented = emptyList()), CommercialNeedProductRejection.PRODUCT_NOT_PRESENTED)
        rejected(selected(presented = (1..201).map { CommercialProductKey("sku-$it") }),
            CommercialNeedProductRejection.PRODUCT_NOT_PRESENTED)
    }

    @Test
    fun staleGlobalRevisionOrNeedEditInvalidatesSelectedBinding() {
        val binding = (selected() as CommercialNeedProductDecision.Candidate).binding
        rejected(S4W04CommercialNeedProductSelection.revalidate(
            binding, snapshot.copy(revision = 10L), fence) { true },
            CommercialNeedProductRejection.STALE_SNAPSHOT)
        val renamed = yerba.copy(name = HouseholdNeedName("Yerba premium"), revision = 4L)
        rejected(S4W04CommercialNeedProductSelection.revalidate(
            binding, snapshot.copy(needs = listOf(renamed, otherYerba)), fence) { true },
            CommercialNeedProductRejection.STALE_SNAPSHOT)
        val unitChange = yerba.copy(quantity = HouseholdNeedQuantity(750L, HouseholdNeedUnit.GRAM))
        rejected(S4W04CommercialNeedProductSelection.revalidate(
            binding, snapshot.copy(needs = listOf(unitChange, otherYerba)), fence) { true },
            CommercialNeedProductRejection.STALE_SNAPSHOT)
    }

    @Test
    fun removalAndCompletionInvalidateAnExistingBinding() {
        val binding = (selected() as CommercialNeedProductDecision.Candidate).binding
        for (needs in listOf(listOf(otherYerba),
            listOf(yerba.copy(status = HouseholdNeedStatus.COMPLETED), otherYerba))) {
            rejected(S4W04CommercialNeedProductSelection.revalidate(
                binding, snapshot.copy(needs = needs), fence) { true },
                CommercialNeedProductRejection.STALE_SNAPSHOT)
        }
    }

    @Test
    fun generationPointerAbaEpochOrPrincipalChangeInvalidatesBinding() {
        val binding = (selected() as CommercialNeedProductDecision.Candidate).binding
        for (changed in listOf(
            fence.copy(generationId = "next-generation"),
            fence.copy(pointerSequence = 6L),
            fence.copy(hostEpoch = "epoch-3"),
            fence.copy(principal = "other-principal"),
        )) {
            rejected(S4W04CommercialNeedProductSelection.revalidate(
                binding, snapshot, changed) { true },
                CommercialNeedProductRejection.STALE_READ_FENCE)
        }
    }

    @Test
    fun revokedAdmissionAndLateRevocationFailClosed() {
        rejected(selected(ownerAdmitted = { false }), CommercialNeedProductRejection.NOT_AUTHORIZED)
        rejected(selected(observed = null), CommercialNeedProductRejection.NOT_AUTHORIZED)
        rejected(selected(context = null), CommercialNeedProductRejection.NOT_AUTHORIZED)
        var admissionCount = 0
        rejected(selected(ownerAdmitted = { ++admissionCount == 1 }),
            CommercialNeedProductRejection.NOT_AUTHORIZED)
        val binding = (selected() as CommercialNeedProductDecision.Candidate).binding
        rejected(S4W04CommercialNeedProductSelection.revalidate(
            binding, snapshot, fence) { false }, CommercialNeedProductRejection.NOT_AUTHORIZED)
        admissionCount = 0
        rejected(S4W04CommercialNeedProductSelection.revalidate(
            binding, snapshot, fence) { ++admissionCount == 1 },
            CommercialNeedProductRejection.NOT_AUTHORIZED)
    }

    @Test
    fun opaqueProductKeyDoesNotExecuteOrBecomeAPriceObservation() {
        val opaque = CommercialProductKey("https://example.invalid/untrusted")
        val result = selected(chosen = opaque, presented = listOf(opaque))
            as CommercialNeedProductDecision.Candidate
        assertEquals(opaque, result.binding.productKey)
        assertEquals(yerba, result.binding.need)
        assertEquals(snapshot, this.snapshot)
        assertEquals(CommercialNeedProductDecision.Candidate(result.binding),
            S4W04CommercialNeedProductSelection.revalidate(result.binding, snapshot, fence) { true })
    }

    @Test
    fun rejectsInvalidProductKeysAndUntrustedFenceInputs() {
        assertThrows(IllegalArgumentException::class.java) { CommercialProductKey(" ") }
        assertThrows(IllegalArgumentException::class.java) { CommercialProductKey(" trailing ") }
        assertThrows(IllegalArgumentException::class.java) { CommercialProductKey("x".repeat(161)) }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialNeedReadFence("", 1L, "epoch", "principal")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialNeedReadFence("generation", 0L, "epoch", "principal")
        }
    }

    @Test
    fun noLocalMutationSideEffectsOrRetainedOptions() {
        val presented = mutableListOf(regular, premium)
        val initialNeed = yerba
        val initialSnapshot = snapshot
        val binding = (selected(presented = presented) as CommercialNeedProductDecision.Candidate).binding
        presented.clear()
        assertTrue(presented.isEmpty())
        assertFalse(initialSnapshot.needs.isEmpty())
        assertEquals(initialNeed, binding.need)
        assertEquals(initialSnapshot.revision, binding.householdRevision)
        assertEquals(CommercialNeedProductDecision.Candidate(binding),
            S4W04CommercialNeedProductSelection.revalidate(binding, initialSnapshot, fence) { true })
    }
}
