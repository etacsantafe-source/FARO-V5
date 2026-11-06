package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineIntent
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteRequest
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceIntent
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** N015 genuine SQLite Workspace read only. No host grant or federation claimed. */
class WorkspaceHistoryReadT2Test {
    @TempDir lateinit var root: Path

    private fun file() = root.resolve("workspace.db").toString()
    private fun id(chat: String) = ConversationId(chat)

    private fun create(store: ConversationWorkspaceStore, chat: String) {
        val revision = store.snapshot().revision
        val result = store.create(
            WorkspaceCreateRequest(
                ConversationWorkspaceEntry(id(chat), "Chat $chat", 10L),
                revision, IdempotencyKey("new-$chat"),
            )
        )
        assertTrue(result is WorkspaceWriteResult.Committed)
    }

    private fun write(
        store: ConversationWorkspaceStore,
        chat: String,
        key: String,
        messageId: String,
        turnId: String,
        role: ConversationMessageRole,
        state: ConversationMessageState,
        time: Long,
    ) {
        val current = requireNotNull(store.timelineSnapshot(id(chat)))
        val message = ConversationMessage(
            ConversationMessageId(messageId), id(chat), ConversationTurnId(turnId),
            role, state, if (role == ConversationMessageRole.USER) "Solicitud aceptada" else "",
            time,
        )
        val intent = if (role == ConversationMessageRole.USER)
            ConversationTimelineIntent.AcceptUser(message)
        else ConversationTimelineIntent.StartFaro(message)
        val result = store.saveTimeline(
            ConversationTimelineWriteRequest(
                id(chat), current.authorityToken, current.revision,
                IdempotencyKey(key), intent,
            )
        )
        assertTrue(result is ConversationTimelineWriteResult.Saved)
    }

    private fun complete(
        store: ConversationWorkspaceStore,
        chat: String,
        messageId: String,
        turnId: String,
        outcome: ConversationMessageState,
    ) {
        val before = requireNotNull(store.timelineSnapshot(id(chat)))
        val result = store.saveTimeline(
            ConversationTimelineWriteRequest(
                id(chat), before.authorityToken, before.revision,
                IdempotencyKey("resolve-$messageId"),
                ConversationTimelineIntent.ResolveFaro(
                    ConversationMessageId(messageId), ConversationTurnId(turnId),
                    outcome, if (outcome == ConversationMessageState.COMPLETED ||
                        outcome == ConversationMessageState.PARTIAL) "Respuesta" else "",
                ),
            )
        )
        assertTrue(result is ConversationTimelineWriteResult.Saved)
    }

    @Test
    fun emptyStoreIsRealEmptyButNotGlobalHistory() {
        ConversationWorkspaceStore(file()).use { store ->
            val page = store.readHistoryPage()
            assertTrue(page.events.isEmpty())
            assertTrue(page.complete)
            assertNull(page.nextCursor)
            assertEquals("workspace.message", page.canonicalSource)
            assertFalse(page.globallyComplete)
            assertTrue(page.hostAdmissionRequired)
        }
    }

    @Test
    fun ownerPaginatesTimeDescendingAndTiesByCanonicalId() {
        ConversationWorkspaceStore(file()).use { store ->
            create(store, "a")
            create(store, "b")
            write(store, "a", "a-user", "u-a", "t-a", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 100L)
            write(store, "b", "b-user", "u-b", "t-b", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 100L)
            val first = store.readHistoryPage(WorkspaceHistoryQuery(limit = 1))
            assertEquals(listOf("u-a"), first.events.map { it.messageId.value })
            assertFalse(first.complete)
            assertEquals("workspace.message.created_ms", first.events[0].timeProvenance)
            val second = store.readHistoryPage(WorkspaceHistoryQuery(before = first.nextCursor, limit = 1))
            assertEquals(listOf("u-b"), second.events.map { it.messageId.value })
            assertTrue(second.complete)
            assertNull(second.nextCursor)
        }
    }

    @Test
    fun filtersByOriginTypeAndInclusiveDatesWithoutReadingBodies() {
        ConversationWorkspaceStore(file()).use { store ->
            create(store, "a")
            create(store, "b")
            write(store, "a", "a-user", "u-a", "t-a", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 100L)
            write(store, "a", "a-faro", "f-a", "t-a", ConversationMessageRole.FARO,
                ConversationMessageState.PROCESSING, 110L)
            complete(store, "a", "f-a", "t-a", ConversationMessageState.COMPLETED)
            write(store, "b", "b-user", "u-b", "t-b", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 200L)
            val result = store.readHistoryPage(WorkspaceHistoryQuery(
                conversationId = id("a"),
                kind = WorkspaceHistoryEventKind.FARO_COMPLETED,
                fromInclusiveMillis = 110L,
                toInclusiveMillis = 110L,
            ))
            assertEquals(listOf("f-a"), result.events.map { it.messageId.value })
            assertEquals(WorkspaceHistoryEventKind.FARO_COMPLETED, result.events.single().kind)
            assertEquals(110L, result.events.single().createdAtEpochMillis)
            assertTrue(result.complete)
            assertTrue(store.readHistoryPage(WorkspaceHistoryQuery(
                kind = WorkspaceHistoryEventKind.USER_ACCEPTED,
                fromInclusiveMillis = 201L,
            )).events.isEmpty())
        }
    }

    @Test
    fun processingPartialAndOfflineResponsesNeverImpersonateCompletedActivity() {
        ConversationWorkspaceStore(file()).use { store ->
            create(store, "a")
            for (n in 1..4) {
                val turn = "t-$n"
                write(store, "a", "user-$n", "u-$n", turn, ConversationMessageRole.USER,
                    ConversationMessageState.ACCEPTED, n.toLong())
                write(store, "a", "faro-$n", "f-$n", turn, ConversationMessageRole.FARO,
                    ConversationMessageState.PROCESSING, (n + 100).toLong())
            }
            complete(store, "a", "f-1", "t-1", ConversationMessageState.COMPLETED)
            complete(store, "a", "f-3", "t-3", ConversationMessageState.PARTIAL)
            complete(store, "a", "f-4", "t-4", ConversationMessageState.OFFLINE)
            val page = store.readHistoryPage()
            assertEquals(5, page.events.size)
            assertEquals(listOf("f-1"), page.events.filter {
                it.kind == WorkspaceHistoryEventKind.FARO_COMPLETED
            }.map { it.messageId.value })
            assertTrue(page.events.none { it.messageId.value in setOf("f-2", "f-3", "f-4") })
        }
    }

    @Test
    fun cursorFailsClosedAfterRevisionChangeAndArgumentsAreBounded() {
        ConversationWorkspaceStore(file()).use { store ->
            create(store, "a")
            write(store, "a", "u1", "u-1", "t-1", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 100L)
            write(store, "a", "u2", "u-2", "t-2", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 99L)
            val page = store.readHistoryPage(WorkspaceHistoryQuery(limit = 1))
            val cursor = requireNotNull(page.nextCursor)
            write(store, "a", "u3", "u-3", "t-3", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 101L)
            assertThrows(IllegalStateException::class.java) {
                store.readHistoryPage(WorkspaceHistoryQuery(before = cursor))
            }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceHistoryQuery(limit = 0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceHistoryQuery(limit = 101)
            }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceHistoryQuery(fromInclusiveMillis = 20, toInclusiveMillis = 19)
            }
        }
    }

    @Test
    fun archivedMessagesSurviveReopenAndHistoryReadDoesNotWrite() {
        val filename = file()
        ConversationWorkspaceStore(filename).use { store ->
            create(store, "a")
            write(store, "a", "u1", "u-1", "t-1", ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED, 123L)
            val revision = store.snapshot().revision
            store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(id("a"), true),
                revision, IdempotencyKey("archive-a"),
            ))
            val before = store.snapshot().revision
            val page = store.readHistoryPage()
            assertEquals(listOf("u-1"), page.events.map { it.messageId.value })
            assertEquals(before, store.snapshot().revision)
        }
        ConversationWorkspaceStore(filename).use { reopened ->
            assertEquals(listOf("u-1"), reopened.readHistoryPage().events.map { it.messageId.value })
            assertEquals(123L, reopened.readHistoryPage().events.single().createdAtEpochMillis)
        }
    }
}
