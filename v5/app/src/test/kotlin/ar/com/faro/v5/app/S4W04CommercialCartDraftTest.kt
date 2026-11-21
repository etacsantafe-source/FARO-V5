package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialCartDraft.Decision
import ar.com.faro.v5.app.S4W04CommercialCartDraft.PackChoice
import ar.com.faro.v5.app.S4W04CommercialCartDraft.Rejection
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4W04CommercialCartDraftTest {
    private val yerba = HouseholdNeed(
        HouseholdNeedId("need-yerba"),
        HouseholdNeedName("Yerba"),
        HouseholdNeedQuantity(1_000, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.NORMAL,
        HouseholdNeedStatus.OPEN,
        3,
    )
    private val rice = yerba.copy(id = HouseholdNeedId("need-rice"), name = HouseholdNeedName("Arroz"))
    private val snapshot = HouseholdNeedsSnapshot(9, listOf(yerba, rice))
    private val fence = CommercialNeedReadFence("household-dev", 4, "epoch-2", "principal-1")
    private val yerbaKey = CommercialProductKey("sku-yerba-500g")
    private val riceKey = CommercialProductKey("sku-arroz")

    private fun choose(
        need: HouseholdNeed = yerba,
        key: CommercialProductKey = yerbaKey,
        count: Int = 2,
        observed: HouseholdNeedsSnapshot = snapshot,
        readFence: CommercialNeedReadFence = fence,
    ): PackChoice {
        val decision = S4W04CommercialNeedProductSelection.select(
            observed, need.id, key, listOf(key), readFence, explicitlyChosen = true,
        ) { true }
        return PackChoice((decision as CommercialNeedProductDecision.Candidate).binding, count)
    }

    private fun build(
        choices: List<PackChoice> = listOf(choose()),
        observed: HouseholdNeedsSnapshot? = snapshot,
        readFence: CommercialNeedReadFence? = fence,
        grant: () -> Boolean = { true },
    ): Decision = S4W04CommercialCartDraft.build(observed, readFence, choices, grant)

    private fun draft(decision: Decision = build()): S4W04CommercialCartDraft =
        (decision as Decision.Draft).cartDraft

    private fun revalidate(
        draft: S4W04CommercialCartDraft,
        observed: HouseholdNeedsSnapshot? = snapshot,
        readFence: CommercialNeedReadFence? = fence,
        grant: () -> Boolean = { true },
    ): Decision = S4W04CommercialCartDraft.revalidate(draft, observed, readFence, grant)

    private fun rejected(decision: Decision, reason: Rejection) =
        assertEquals(Decision.Rejected(reason), decision)

    @Test
    fun requiresAdmissionSnapshotAndFence() {
        rejected(build(grant = { false }), Rejection.NOT_AUTHORIZED)
        rejected(build(observed = null), Rejection.NOT_AUTHORIZED)
        rejected(build(readFence = null), Rejection.NOT_AUTHORIZED)
        rejected(build(observed = snapshot.copy(revision = -1)), Rejection.NOT_AUTHORIZED)
        var checks = 0
        rejected(build(grant = { ++checks <= 2 }), Rejection.NOT_AUTHORIZED)
    }

    @Test
    fun missingDeletedOrCompletedNeedRejectsWholeCart() {
        val choices = listOf(choose(), choose(rice, riceKey))
        for (needs in listOf(
            emptyList(),
            listOf(rice),
            listOf(rice, yerba.copy(status = HouseholdNeedStatus.COMPLETED)),
        )) {
            rejected(build(choices, snapshot.copy(needs = needs)), Rejection.STALE_CANDIDATE)
        }
    }

    @Test
    fun changesToGlobalRevisionOrOriginalNeedInvalidateCart() {
        val cart = draft()
        rejected(revalidate(cart, snapshot.copy(revision = 10)), Rejection.STALE_CANDIDATE)
        for (changed in listOf(
            yerba.copy(name = HouseholdNeedName("Otra yerba")),
            yerba.copy(quantity = HouseholdNeedQuantity(750, HouseholdNeedUnit.GRAM)),
            yerba.copy(quantity = HouseholdNeedQuantity(1_000, HouseholdNeedUnit.PACKAGE)),
            yerba.copy(revision = 4),
            yerba.copy(dueDate = LocalDate.of(2026, 11, 25)),
        )) {
            rejected(revalidate(cart, snapshot.copy(needs = listOf(changed, rice))), Rejection.STALE_CANDIDATE)
        }
    }

    @Test
    fun generationPointerAbaEpochAndPrincipalInvalidateCart() {
        val cart = draft()
        for (changed in listOf(
            fence.copy(generationId = "other-generation"),
            fence.copy(pointerSequence = 6),
            fence.copy(hostEpoch = "epoch-3"),
            fence.copy(principal = "principal-2"),
        )) {
            rejected(build(readFence = changed), Rejection.INCONSISTENT_READ_FENCE)
            rejected(revalidate(cart, readFence = changed), Rejection.INCONSISTENT_READ_FENCE)
        }
    }

    @Test
    fun explicitPacksRemainSeparateFromNeedQuantityWithoutEvidence() {
        val cart = draft(build(choices = listOf(choose(count = 3))))
        val expected = S4W04CommercialCartDraft.Line(
            yerba.id, yerba.revision, yerba.name, 1_000L, HouseholdNeedUnit.GRAM, yerbaKey, 3,
        )
        assertEquals(listOf(expected), cart.lines)
        assertEquals(1, cart.lineCount)
        assertEquals(9L, cart.householdRevision)
        assertEquals(fence, cart.readFence)
        assertSame(cart, draft(revalidate(cart)))
    }

    @Test
    fun packCountRequiresPositiveBoundedExplicitInput() {
        for (invalid in listOf(0, -1, 10_001, Int.MAX_VALUE)) {
            val choices = listOf(choose(count = invalid))
            rejected(build(choices), Rejection.INVALID_PACK_COUNT)
            assertEquals(invalid, choices.single().requestedPackCount)
        }
        for (valid in listOf(1, 10_000)) {
            assertEquals(valid, draft(build(listOf(choose(count = valid)))).lines.single().requestedPackCount)
        }
    }

    @Test
    fun duplicateNeedRejectedEvenWithDifferentProduct() {
        val first = choose()
        val otherProduct = choose(key = CommercialProductKey("yerba-other"))
        rejected(build(listOf(first, first)), Rejection.DUPLICATE_NEED)
        rejected(build(listOf(first, otherProduct)), Rejection.DUPLICATE_NEED)
    }

    @Test
    fun mixedReadAuthoritiesRejectWholeCart() {
        for (otherFence in listOf(
            fence.copy(generationId = "another-generation"),
            fence.copy(hostEpoch = "another-epoch"),
            fence.copy(principal = "another-principal"),
        )) {
            val mixed = listOf(choose(rice, riceKey), choose(readFence = otherFence))
            rejected(build(mixed), Rejection.INCONSISTENT_READ_FENCE)
        }
    }

    @Test
    fun canonicalOrderAndFingerprintAreStableAndBindThePackChoice() {
        val choices = listOf(choose(), choose(rice, riceKey))
        val first = draft(build(choices))
        val reversed = draft(build(choices.reversed()))
        assertEquals(listOf(rice.id, yerba.id), first.lines.map { it.needId })
        assertEquals(first.lines, reversed.lines)
        assertEquals(first.fingerprint, reversed.fingerprint)
        assertTrue(first.fingerprint.matches(Regex("[0-9a-f]{64}")))
        val changed = draft(build(listOf(choose(count = 1), choose(rice, riceKey))))
        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    @Test
    fun acceptsTwoHundredLinesAndRejectsEmptyOrOversizedLists() {
        val needs = (1..201).map { yerba.copy(id = HouseholdNeedId("need-$it")) }
        val observed = snapshot.copy(needs = needs)
        val choices = needs.map { choose(need = it, count = 10_000, observed = observed) }
        rejected(build(emptyList()), Rejection.EMPTY_OR_TOO_MANY_LINES)
        rejected(build(choices, observed), Rejection.EMPTY_OR_TOO_MANY_LINES)
        val cart = draft(build(choices.take(200), observed))
        assertEquals(200, cart.lineCount)
        assertTrue(cart.lines.all { it.requestedPackCount == 10_000 })
    }

    @Test
    fun cartOwnsAnImmutableCopyOfTheOriginalChoices() {
        val needs = snapshot.needs.toMutableList()
        val original = snapshot.copy(needs = needs)
        val choices = mutableListOf(choose(observed = original))
        val cart = draft(build(choices, original))
        choices[0] = choose(count = 5)
        needs.clear()
        assertEquals(2, cart.lines.single().requestedPackCount)
        assertEquals(yerba.name, cart.lines.single().needName)
        assertThrows(UnsupportedOperationException::class.java) {
            (cart.lines as MutableList<S4W04CommercialCartDraft.Line>).clear()
        }
        assertSame(cart, draft(revalidate(cart)))
        rejected(revalidate(cart, original), Rejection.STALE_CANDIDATE)
    }

    @Test
    fun opaqueProductKeyIsDataAndRevocationInvalidatesTheCart() {
        val opaque = CommercialProductKey("https://example.invalid/untrusted")
        val cart = draft(build(listOf(choose(key = opaque))))
        assertEquals(opaque, cart.lines.single().productKey)
        rejected(revalidate(cart, grant = { false }), Rejection.NOT_AUTHORIZED)
        rejected(revalidate(cart, observed = null), Rejection.NOT_AUTHORIZED)
        rejected(revalidate(cart, readFence = null), Rejection.NOT_AUTHORIZED)
    }
}
