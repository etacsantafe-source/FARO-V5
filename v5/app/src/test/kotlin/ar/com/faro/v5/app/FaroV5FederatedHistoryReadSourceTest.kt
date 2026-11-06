package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.ReceiptId
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.store.reality.RealityTimedReceipt
import ar.com.faro.v5.store.reality.RealityTimedReceiptCursor
import ar.com.faro.v5.store.reality.RealityTimedReceiptHistoryReadPort
import ar.com.faro.v5.store.reality.RealityTimedReceiptPage
import ar.com.faro.v5.workspace.WorkspaceHistoryCursor
import ar.com.faro.v5.workspace.WorkspaceHistoryEvent
import ar.com.faro.v5.workspace.WorkspaceHistoryEventKind
import ar.com.faro.v5.workspace.WorkspaceHistoryPage
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** N015 app adapter tests. Fake grants do NOT certify host integration. */
class FaroV5FederatedHistoryReadSourceTest {
    private class Fixture {
        var pointerCurrent = true
        var pointer = "ptr-1"
        var generation = "gen-1"
        var realityGrant: String? = "grant-reality-1"
        var workspaceGrant: String? = "grant-workspace-1"
        var realityAdmitted = true
        var workspaceAdmitted = true
        var workspaceRevision = 1L
        var realityReads = 0
        var workspaceReads = 0
        var legacyUndated = false
        var afterRealityRead: () -> Unit = {}
        var afterWorkspaceRead: () -> Unit = {}
        var realityFailure: Exception? = null
        var workspaceFailure: Exception? = null
        val receipts = mutableListOf<RealityTimedReceipt>()
        val events = mutableListOf<WorkspaceHistoryEvent>()

        fun receipt(id: String, at: Long) {
            receipts += RealityTimedReceipt(
                ExecutionReceipt(ReceiptId("rcpt-$id"), TransactionId(id), PlanFingerprint("fp-$id")),
                at,
            )
        }

        fun message(id: String, at: Long) {
            events += WorkspaceHistoryEvent(
                ConversationMessageId(id), ConversationId("chat"),
                WorkspaceHistoryEventKind.USER_ACCEPTED, at,
            )
        }

        fun source(): FaroV5FederatedHistoryReadSource =
            FaroV5FederatedHistoryReadSource(
                expectedGenerationId = "gen-1",
                reality = RealityTimedReceiptHistoryReadPort { query ->
                    realityReads++
                    realityFailure?.let { throw it }
                    val rows = receipts
                        .filter { e ->
                            val cursor = query.before
                            cursor == null ||
                                e.recordedAtEpochMillis < cursor.recordedAtEpochMillis ||
                                (e.recordedAtEpochMillis == cursor.recordedAtEpochMillis &&
                                    e.receipt.transactionId.value > cursor.transactionId)
                        }.sortedWith(compareByDescending<RealityTimedReceipt> {
                            it.recordedAtEpochMillis
                        }.thenBy { it.receipt.transactionId.value })
                    val visible = rows.take(query.limit)
                    val last = visible.lastOrNull()
                    val page = RealityTimedReceiptPage(
                        receipts = visible,
                        nextCursor = if (rows.size > query.limit && last != null)
                            RealityTimedReceiptCursor(last.recordedAtEpochMillis,
                                last.receipt.transactionId.value) else null,
                        legacyUndated = legacyUndated,
                    )
                    afterRealityRead()
                    page
                },
                workspace = FaroV5WorkspaceHistoryReadPort { query ->
                    workspaceReads++
                    workspaceFailure?.let { throw it }
                    val rows = events
                        .filter { e ->
                            val cursor = query.before
                            cursor == null ||
                                e.createdAtEpochMillis < cursor.atEpochMillis ||
                                (e.createdAtEpochMillis == cursor.atEpochMillis &&
                                    e.messageId.value > cursor.messageId.value)
                        }.sortedWith(compareByDescending<WorkspaceHistoryEvent> {
                            it.createdAtEpochMillis
                        }.thenBy { it.messageId.value })
                    val visible = rows.take(query.limit)
                    val last = visible.lastOrNull()
                    val page = WorkspaceHistoryPage(
                        events = visible,
                        nextCursor = if (rows.size > query.limit && last != null)
                            WorkspaceHistoryCursor(last.createdAtEpochMillis,
                                last.messageId, workspaceRevision) else null,
                        ownerRevision = workspaceRevision,
                        complete = rows.size <= query.limit,
                    )
                    afterWorkspaceRead()
                    page
                },
                currentGenerationId = { generation },
                pointerCurrent = { pointerCurrent },
                pointerEpoch = { pointer },
                realityAclEpoch = { realityGrant },
                workspaceAclEpoch = { workspaceGrant },
                realityReadAdmitted = { realityAdmitted },
                workspaceReadAdmitted = { workspaceAdmitted },
            )
    }

    @Test
    fun missingGrantOrPointerNeverQueriesAnyOwner() {
        val f = Fixture()
        val source = f.source()
        f.workspaceGrant = null
        assertNull(source.readAuthorized())
        assertEquals(0, f.realityReads)
        assertEquals(0, f.workspaceReads)
        f.workspaceGrant = "grant-workspace-1"
        f.pointerCurrent = false
        assertNull(source.readAuthorized())
        assertEquals(0, f.realityReads)
        f.pointerCurrent = true
        f.generation = "other"
        assertNull(source.readAuthorized())
        assertEquals(0, f.realityReads)
    }

    @Test
    fun deterministicMergeHasNoSkippedRowsAcrossTwoOwnerPages() {
        val f = Fixture()
        f.receipt("r4", 400)
        f.receipt("r2", 200)
        f.message("w5", 500)
        f.message("w3", 300)
        val source = f.source()
        val first = requireNotNull(source.readAuthorized(limit = 2))
        assertEquals(listOf("w5", "r4"), first.items.map { it.opaqueId })
        assertEquals(listOf(FaroV5ActivityDomain.WORKSPACE_MESSAGE,
            FaroV5ActivityDomain.REALITY_RECEIPT), first.items.map { it.domain })
        assertFalse(first.completeWithinTwoSources)
        assertFalse(first.globallyComplete)
        val second = requireNotNull(source.readAuthorized(limit = 2,
            before = requireNotNull(first.nextCursor)))
        assertEquals(listOf("w3", "r2"), second.items.map { it.opaqueId })
        assertTrue(second.completeWithinTwoSources)
        assertNull(second.nextCursor)
        assertEquals("workspace.message.created_ms", first.items.first().timeProvenance)
        assertEquals("reality.receipt.commit_recorded_at_epoch_millis",
            first.items.last().timeProvenance)
    }

    @Test
    fun tiesSortBySourceAndIdAndLegacyCoverageIsNotHidden() {
        val f = Fixture()
        f.receipt("tx-b", 10)
        f.receipt("tx-a", 10)
        f.message("msg-a", 10)
        f.legacyUndated = true
        val source = f.source()
        val first = requireNotNull(source.readAuthorized(limit = 2))
        assertEquals(listOf("tx-a", "tx-b"), first.items.map { it.opaqueId })
        assertTrue(first.legacyUndatedReality)
        val second = requireNotNull(source.readAuthorized(limit = 2,
            before = requireNotNull(first.nextCursor)))
        assertEquals(listOf("msg-a"), second.items.map { it.opaqueId })
        assertTrue(second.legacyUndatedReality)
        assertFalse(second.globallyComplete)
    }

    @Test
    fun pointerAbaOrRealityRevocationDuringReadDeniesWholePage() {
        val f = Fixture()
        f.receipt("tx", 100)
        f.message("msg", 110)
        f.afterRealityRead = {
            // A -> B -> A: same generation, distinct durable pointer sequence.
            f.pointer = "ptr-3"
        }
        assertNull(f.source().readAuthorized())
        assertEquals(0, f.workspaceReads)
        f.pointer = "ptr-1"
        f.afterRealityRead = { f.realityGrant = "grant-reality-2" }
        assertNull(f.source().readAuthorized())
        assertEquals(0, f.workspaceReads)
    }

    @Test
    fun workspaceRevocationOrRevisionChangeDeniesEntireHistory() {
        val f = Fixture()
        f.receipt("tx", 100)
        f.message("m1", 200)
        f.message("m2", 150)
        val source = f.source()
        val first = requireNotNull(source.readAuthorized(limit = 1))
        f.workspaceRevision = 2L
        assertNull(source.readAuthorized(limit = 1,
            before = requireNotNull(first.nextCursor)))
        f.workspaceRevision = 1L
        f.afterWorkspaceRead = { f.workspaceGrant = null }
        assertNull(source.readAuthorized())
    }

    @Test
    fun ownerFailuresAndCancellationFailClosedAndInvalidLimitIsRejected() {
        val f = Fixture()
        f.realityFailure = IllegalStateException("Reality closed")
        assertNull(f.source().readAuthorized())
        f.realityFailure = null
        f.workspaceFailure = IllegalStateException("Workspace closed")
        assertNull(f.source().readAuthorized())
        f.workspaceFailure = CancellationException("cancel")
        assertThrows(CancellationException::class.java) { f.source().readAuthorized() }
        assertThrows(IllegalArgumentException::class.java) { f.source().readAuthorized(limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { f.source().readAuthorized(limit = 101) }
    }
}
