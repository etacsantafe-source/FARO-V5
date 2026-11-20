package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HouseholdNeedReviewTest {
    private val generation = HouseholdReviewGeneration("dev-household", 5)
    private val name = HouseholdNeedName("Café")
    private val fields = HouseholdNeedFields(
        name, HouseholdNeedQuantity(1_250, HouseholdNeedUnit.KILOGRAM),
        HouseholdNeedPriority.HIGH, LocalDate.of(2026, 12, 4),
    )
    private val saved = HouseholdNeed(
        HouseholdNeedId("opaque-key"), name, fields.quantity, fields.priority, HouseholdNeedStatus.OPEN, 2, fields.dueDate,
    )
    private val snapshot = HouseholdNeedsSnapshot(3, listOf(saved))

    @Test
    fun showsHumanSummariesForAllFourActions() {
        val revision = snapshot.revision
        val changed = fields.copy(name = HouseholdNeedName("Café en grano"))
        val requests = listOf(
            HouseholdNeedReviewRequest.Add(changed),
            HouseholdNeedReviewRequest.Edit(name, changed),
            HouseholdNeedReviewRequest.Complete(name),
            HouseholdNeedReviewRequest.Remove(name),
        )
        val reviews = requests.map { ready(it) }
        assertEquals(listOf("shopping_need.add", "shopping_need.update", "shopping_need.complete", "shopping_need.remove"),
            reviews.map { it.action.operationId.value })
        assertTrue(reviews.all { it.expectedRevision == revision && "1,25 kg" in it.summary })
        assertTrue(reviews.none { "opaque-key" in it.summary || it.targetId.value in it.summary })
        assertTrue("Después · Café en grano" in reviews[0].summary)
        assertTrue("Antes · Café" in reviews[1].summary && "Después · Café en grano" in reviews[1].summary)
        assertTrue("pendiente" in reviews[2].summary && "completada" in reviews[2].summary)
        assertTrue("Antes · Café" in reviews[3].summary && "Después" !in reviews[3].summary)
        assertEquals(3L, snapshot.revision)
        assertEquals(listOf(saved), snapshot.needs)
        assertTrue(reviews[0].toPersistenceChange() is HouseholdNeedChange.Add)
        assertEquals(HouseholdNeedChange.Edit(saved.id, 2, changed), reviews[1].toPersistenceChange())
        assertEquals(HouseholdNeedChange.Complete(saved.id, 2), reviews[2].toPersistenceChange())
        assertEquals(HouseholdNeedChange.Remove(saved.id, 2), reviews[3].toPersistenceChange())
    }

    @Test
    fun matchesCanonicalUnicodeNamesAndRejectsAmbiguousMatches() {
        val nameWithCombiningAccent = HouseholdNeedName("CAFE\u0301")
        val request = HouseholdNeedReviewRequest.Remove(nameWithCombiningAccent)
        assertEquals(saved.id, ready(request).targetId)
        val ambiguous = snapshot.copy(needs = listOf(
            saved, saved.copy(id = HouseholdNeedId("opaque-other"), name = nameWithCombiningAccent),
        ))
        assertEquals(HouseholdNeedReviewResult.Rejected.AMBIGUOUS, propose(request, ambiguous))
        assertEquals(HouseholdNeedReviewResult.Rejected.MISSING,
            propose(HouseholdNeedReviewRequest.Remove(HouseholdNeedName("Yerba"))))
    }

    @Test
    fun freezesPayloadAndBindsVersionGenerationAndTarget() {
        val request = HouseholdNeedReviewRequest.Edit(name, fields.copy(priority = HouseholdNeedPriority.LOW))
        val review = ready(request)
        val duplicate = ready(request)
        val fingerprint = review.payload.fingerprint
        assertEquals(duplicate.payload, review.payload)
        assertEquals(duplicate.payload.fingerprint, fingerprint)
        review.payload.copyBytes().fill(0)
        assertEquals(duplicate.payload, review.payload)
        assertTrue(review.matchesCurrentSnapshot(generation, snapshot, fingerprint))
        assertFalse(review.matchesCurrentSnapshot(generation.copy(sequence = 6), snapshot, fingerprint))
        assertFalse(review.matchesCurrentSnapshot(generation, snapshot.copy(revision = 4), fingerprint))
        assertFalse(review.matchesCurrentSnapshot(generation, snapshot.copy(needs = listOf(saved.copy(revision = 3))), fingerprint))
        val other = ready(HouseholdNeedReviewRequest.Remove(name))
        assertFalse(review.matchesCurrentSnapshot(generation, snapshot, other.payload.fingerprint))
    }

    @Test
    fun roundTripsEachCanonicalPayloadAndRejectsMalformedEncoding() {
        val reviews = listOf(
            ready(HouseholdNeedReviewRequest.Add(fields)),
            ready(HouseholdNeedReviewRequest.Edit(name, fields.copy(dueDate = null))),
            ready(HouseholdNeedReviewRequest.Complete(name)),
            ready(HouseholdNeedReviewRequest.Remove(name)),
        )
        for (review in reviews) {
            val payload = review.payload
            val decoded = checkNotNull(HouseholdNeedReview.decode(payload))
            assertEquals(review.before, decoded.before)
            assertEquals(review.after, decoded.after)
            assertEquals(review.toPersistenceChange(), decoded.toPersistenceChange())
            assertEquals(payload.fingerprint, decoded.payload.fingerprint)
            assertNull(HouseholdNeedReview.decode(CanonicalPayload.fromEncoded(payload.formatId, payload.copyBytes() + 0)))
            assertNull(HouseholdNeedReview.decode(CanonicalPayload.fromEncoded("wrong.format", payload.copyBytes())))
            assertNull(HouseholdNeedReview.decode(CanonicalPayload.fromEncoded(payload.formatId, byteArrayOf(1))))
        }
    }

    @Test
    fun derivesAddIdentityFromTheLocalRequestAndRejectsReusedIdentity() {
        val request = HouseholdNeedReviewRequest.Add(fields)
        val first = ready(request)
        assertEquals(first.targetId, ready(request).targetId)
        val other = (HouseholdNeedReview.propose(generation, snapshot, request, "other-request")
            as HouseholdNeedReviewResult.Ready).review
        assertNotEquals(first.targetId, other.targetId)
        assertEquals(HouseholdNeedReviewResult.Rejected.INVALID,
            propose(request, snapshot.copy(revision = 4, needs = listOf(checkNotNull(first.after)))))
    }

    @Test
    fun rejectsUnavailableListsInvalidDatesAndAlreadyCompletedNeeds() {
        val request = HouseholdNeedReviewRequest.Complete(name)
        assertEquals(HouseholdNeedReviewResult.Rejected.UNAVAILABLE, propose(request, null))
        assertEquals(HouseholdNeedReviewResult.Rejected.INVALID,
            propose(HouseholdNeedReviewRequest.Add(fields.copy(dueDate = LocalDate.of(10_000, 1, 1)))))
        assertEquals(HouseholdNeedReviewResult.Rejected.ALREADY_COMPLETE,
            propose(request, snapshot.copy(needs = listOf(saved.copy(status = HouseholdNeedStatus.COMPLETED)))))
    }

    private fun propose(request: HouseholdNeedReviewRequest, current: HouseholdNeedsSnapshot? = snapshot) =
        HouseholdNeedReview.propose(generation, current, request, "request-local-1")

    private fun ready(request: HouseholdNeedReviewRequest): HouseholdNeedReview =
        (propose(request) as HouseholdNeedReviewResult.Ready).review
}
