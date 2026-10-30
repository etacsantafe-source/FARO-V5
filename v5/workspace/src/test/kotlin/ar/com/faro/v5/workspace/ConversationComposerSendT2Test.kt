package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationDraftSaveRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendCandidate
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Real Workspace SQLite native T2: one authoritative Composer send transaction. */
class ConversationComposerSendT2Test {
    @TempDir lateinit var root: Path
    private val chat = ConversationId("chat-1")
    private fun file() = root.resolve("workspace.db").toString()
    private fun create(store: ConversationWorkspaceStore) {
        assertTrue(store.create(WorkspaceCreateRequest(
            ConversationWorkspaceEntry(chat, "Chat real", 100),
            store.snapshot().revision, IdempotencyKey("new-chat"),
        )) is WorkspaceWriteResult.Committed)
    }
    private fun request(store: ConversationWorkspaceStore, text: String = "Hola FARO"): ConversationDraftSendRequest {
        val original = requireNotNull(store.draftSnapshot(chat))
        val saved = store.saveDraft(ConversationDraftSaveRequest(
            chat, original.authorityToken, original.revision, text, IdempotencyKey("persist-draft"),
        )) as ConversationDraftWriteResult.Saved
        val draft = saved.snapshot
        return ConversationDraftSendRequest(
            ConversationDraftSendCandidate(chat, draft.authorityToken, draft.revision, draft.text),
            ConversationMessageId("message-1"), ConversationTurnId("turn-1"),
            1710000000000, IdempotencyKey("send-draft"),
        )
    }

    @Test fun sendIsDurableAtomicAndIdempotentAcrossReopen() {
        val source = file()
        lateinit var command: ConversationDraftSendRequest
        ConversationWorkspaceStore(source).use { store ->
            create(store)
            command = request(store)
            val sent = store.sendCommittedDraft(command) as ConversationTimelineWriteResult.Saved
            assertFalse(sent.replayed)
            assertEquals(listOf("Hola FARO"), sent.snapshot.messages.map { it.text })
            assertEquals("", store.draftSnapshot(chat)!!.text)
        }
        ConversationWorkspaceStore(source).use { reopened ->
            val retry = reopened.sendCommittedDraft(command) as ConversationTimelineWriteResult.Saved
            assertTrue(retry.replayed)
            assertEquals(1, retry.snapshot.messages.size)
            assertEquals("", reopened.draftSnapshot(chat)!!.text)
        }
    }

    @Test fun crashAfterMessageBeforeReceiptLeavesDraftAndTimelineIntact() {
        val source = file()
        lateinit var command: ConversationDraftSendRequest
        ConversationWorkspaceStore(source).use { store ->
            create(store)
            command = request(store)
        }
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(source) { point ->
                if (point == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) error("crash")
            }.use { store ->
                store.sendCommittedDraft(command)
            }
        }
        ConversationWorkspaceStore(source).use { reopened ->
            assertEquals("Hola FARO", reopened.draftSnapshot(chat)!!.text)
            assertTrue(reopened.timelineSnapshot(chat)!!.messages.isEmpty())
            assertTrue(reopened.sendCommittedDraft(command) is ConversationTimelineWriteResult.Saved)
            assertEquals(1, reopened.timelineSnapshot(chat)!!.messages.size)
        }
    }

    @Test fun changedDraftAndMismatchedIdempotencyCannotSendAgain() {
        ConversationWorkspaceStore(file()).use { store ->
            create(store)
            val req = request(store)
            val stale = req.copy(candidate = req.candidate.copy(text = "not acknowledged"),
                idempotencyKey = IdempotencyKey("different"))
            assertTrue(store.sendCommittedDraft(stale) is ConversationTimelineWriteResult.Stale)
            assertEquals("Hola FARO", store.draftSnapshot(chat)!!.text)
            assertTrue(store.sendCommittedDraft(req) is ConversationTimelineWriteResult.Saved)
            assertEquals(ConversationTimelineWriteResult.IdentityConflict,
                store.sendCommittedDraft(req.copy(messageId = ConversationMessageId("altered"))))
            assertEquals(1, store.timelineSnapshot(chat)!!.messages.size)
        }
    }
}
