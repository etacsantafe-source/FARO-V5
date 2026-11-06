package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.matters.api.MatterDetail
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterRecord
import ar.com.faro.v5.owner.matters.api.MatterSnapshot
import ar.com.faro.v5.owner.matters.api.MatterStatus
import ar.com.faro.v5.owner.matters.api.MatterTitle
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Typed real Matter owner records; no invented Reminder or MonthlyPlan source. */
class FaroV5RealityMatterDueReadSourceTest {
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val now = Instant.parse("2026-11-05T15:00:00Z")

    private fun matter(
        id: String, due: String?, status: MatterStatus = MatterStatus.OPEN,
    ) = MatterRecord(
        id = MatterId.of(id),
        title = MatterTitle.of("Private $id"),
        detail = MatterDetail.of("Sensitive description for $id"),
        status = status,
        dueAtEpochMillis = due?.let { Instant.parse(it).toEpochMilli() },
        updatedAtEpochMillis = 1L,
    )

    private class Fixture {
        var currentGeneration = "gen-1"
        var pointerCurrent = true
        var pointerEpoch = "ptr-1"
        var acl: String? = "acl-1"
        var admitted = true
        var revision = 5L
        var reads = 0
        var records: List<MatterRecord> = emptyList()
        var afterRead: () -> Unit = {}
        var failure: Exception? = null

        val persistence: MatterPersistencePort = object : MatterPersistencePort {
            override fun read(): MatterSnapshot {
                reads++
                failure?.let { throw it }
                val snapshot = MatterSnapshot(revision, records)
                afterRead()
                return snapshot
            }
            override fun upsert(record: MatterRecord, transactionScope: RealityTransactionScope): MatterRecord =
                error("read-only test must never mutate owner")
        }

        fun source() = FaroV5RealityMatterDueReadSource(
            expectedGenerationId = "gen-1",
            matters = persistence,
            currentGenerationId = { currentGeneration },
            pointerCurrent = { pointerCurrent },
            pointerEpoch = { pointerEpoch },
            aclEpoch = { acl },
            readAdmitted = { admitted },
        )
    }

    @Test
    fun missingGrantDoesNotTouchOwnerAndRealZeroIsNotGlobalAttentionZero() {
        val f = Fixture()
        f.acl = null
        assertNull(f.source().readAuthorized(now, zone))
        assertEquals(0, f.reads)
        f.acl = "acl-1"
        val page = requireNotNull(f.source().readAuthorized(now, zone))
        assertTrue(page.items.isEmpty())
        assertTrue(page.completeWithinMatterDue)
        assertFalse(page.globalAttentionComplete)
        assertFalse(page.hasReminderOwner)
        assertFalse(page.hasMonthlyPlanOwner)
        assertEquals("MATTERS_DUE_ONLY", page.coverage)
        assertEquals("reality.matter_record", page.canonicalSource)
    }

    @Test
    fun onlyRealUnresolvedDuesWithinSevenLocalDaysAreIncluded() {
        val f = Fixture()
        f.records = listOf(
            matter("overdue", "2026-11-01T04:00:00Z"),
            matter("in-progress", "2026-11-07T12:00:00Z", MatterStatus.IN_PROGRESS),
            matter("waiting", "2026-11-10T12:00:00Z", MatterStatus.WAITING),
            matter("resolved", "2026-11-07T12:00:00Z", MatterStatus.RESOLVED),
            matter("archived", "2026-11-07T12:00:00Z", MatterStatus.ARCHIVED),
            matter("future", "2026-11-14T12:00:00Z"),
            matter("no-due", null),
        )
        val page = requireNotNull(f.source().readAuthorized(now, zone))
        assertEquals(listOf("overdue", "in-progress", "waiting"), page.items.map { it.matterId })
        assertEquals(listOf(MatterStatus.OPEN, MatterStatus.IN_PROGRESS, MatterStatus.WAITING),
            page.items.map { it.status })
        assertEquals("reality.matter_record.due_at_epoch_millis", page.dueTimeProvenance)
        assertEquals(5L, page.ownerRevision)
        assertEquals(1, f.reads)
    }

    @Test
    fun localCivilDayBoundaryRespectsSuppliedZone() {
        val f = Fixture()
        f.records = listOf(matter("boundary", "2026-11-12T03:00:00Z"))
        val before = Instant.parse("2026-11-05T02:30:00Z")
        val after = Instant.parse("2026-11-05T03:30:00Z")
        assertTrue(requireNotNull(f.source().readAuthorized(before, zone)).items.isEmpty())
        assertEquals(listOf("boundary"),
            requireNotNull(f.source().readAuthorized(after, zone)).items.map { it.matterId })
    }

    @Test
    fun ownerBoundedProjectionMarksTruncatedInsteadOfClaimingCoverage() {
        val f = Fixture()
        f.records = (1..3).map {
            matter("id-$it", "2026-11-07T12:00:00Z")
        }
        val source = f.source()
        val first = requireNotNull(source.readAuthorized(now, zone, limit = 2))
        assertEquals(listOf("id-1", "id-2"), first.items.map { it.matterId })
        assertFalse(first.completeWithinMatterDue)
        assertFalse(first.globalAttentionComplete)
        assertThrows(IllegalArgumentException::class.java) {
            source.readAuthorized(now, zone, limit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.readAuthorized(now, zone, limit = 101)
        }
    }

    @Test
    fun pointerAbaAndAclRevocationDuringOwnerReadDiscardEntireResult() {
        val f = Fixture()
        f.records = listOf(matter("id", "2026-11-07T12:00:00Z"))
        f.afterRead = { f.pointerEpoch = "ptr-3" }
        assertNull(f.source().readAuthorized(now, zone))
        f.pointerEpoch = "ptr-1"
        f.afterRead = { f.acl = "acl-2" }
        assertNull(f.source().readAuthorized(now, zone))
        f.afterRead = {}
        f.admitted = false
        val calls = f.reads
        assertNull(f.source().readAuthorized(now, zone))
        assertEquals(calls, f.reads)
    }

    @Test
    fun closedOwnerFailsClosedAndCancellationPropagates() {
        val f = Fixture()
        f.failure = IllegalStateException("closed owner")
        assertNull(f.source().readAuthorized(now, zone))
        f.failure = CancellationException("cancel")
        assertThrows(CancellationException::class.java) {
            f.source().readAuthorized(now, zone)
        }
        f.failure = null
        f.records = listOf(matter("persisted", "2026-11-06T12:00:00Z"))
        assertEquals(listOf("persisted"),
            requireNotNull(f.source().readAuthorized(now, zone)).items.map { it.matterId })
        assertEquals(5L, f.revision)
    }
}
