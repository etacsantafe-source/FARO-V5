package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WorkspaceBookmarkReadAdmissionTest {
    @TempDir lateinit var root: Path
    private val chat = ConversationId("a")
    private val message = ConversationMessageId("message")
    private fun store() = ConversationWorkspaceStore(root.resolve("workspace.db").toString())
    private fun seed(store: ConversationWorkspaceStore) {
        store.create(WorkspaceCreateRequest(ConversationWorkspaceEntry(chat, "Chat", 1), 0, IdempotencyKey("create")))
        write(store, "user", ConversationTimelineIntent.AcceptUser(ConversationMessage(message, chat,
            ConversationTurnId("turn"), ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
            "reserved body", 1)))
    }
    private fun write(store: ConversationWorkspaceStore, key: String, intent: ConversationTimelineIntent) {
        val before = store.timelineSnapshot(chat)!!
        assertInstanceOf(ConversationTimelineWriteResult.Saved::class.java,
            store.saveTimeline(ConversationTimelineWriteRequest(chat, before.authorityToken,
                before.revision, IdempotencyKey(key), intent)))
    }

    @Test fun emptyAdmissionAndInvalidTargetPairNeverCreateConversationOrReceipt() {
        store().use {
            val before = it.backup.snapshot()
            val facts = it.bookmarkReadAdmission()
            assertEquals(0L, facts.ownerRevision)
            assertNull(facts.targetAvailable)
            assertFalse(it.bookmarkReadAdmission(chat, message).targetAvailable!!)
            assertThrows(IllegalArgumentException::class.java) { it.bookmarkReadAdmission(chat, null) }
            assertEquals(before, it.backup.snapshot())
        }
    }

    @Test fun exactConversationAndMessageAreRequiredAndArchiveRevokesAdmission() {
        store().use {
            seed(it)
            val facts = it.bookmarkReadAdmission(chat, message)
            assertEquals(true, facts.targetAvailable)
            assertEquals(it.snapshot().revision, facts.ownerRevision)
            val other = ConversationId("b")
            it.create(WorkspaceCreateRequest(ConversationWorkspaceEntry(other, "Other", 1),
                it.snapshot().revision, IdempotencyKey("other")))
            assertEquals(false, it.bookmarkReadAdmission(other, message).targetAvailable)
            assertEquals(false, it.bookmarkReadAdmission(chat, ConversationMessageId("missing")).targetAvailable)
            it.transition(WorkspaceTransitionRequest(ConversationWorkspaceIntent.SetArchived(chat, true),
                it.snapshot().revision, IdempotencyKey("archive")))
            assertEquals(false, it.bookmarkReadAdmission(chat, message).targetAvailable)
        }
    }

    @Test fun processingAndPartialAreNotCompleteAndAdmissionNeverCarriesText() {
        store().use {
            seed(it)
            val reply = ConversationMessageId("reply")
            write(it, "start", ConversationTimelineIntent.StartFaro(ConversationMessage(reply, chat,
                ConversationTurnId("turn"), ConversationMessageRole.FARO, ConversationMessageState.PROCESSING, "", 2)))
            assertEquals(false, it.bookmarkReadAdmission(chat, reply).targetAvailable)
            write(it, "partial", ConversationTimelineIntent.ResolveFaro(reply, ConversationTurnId("turn"),
                ConversationMessageState.PARTIAL, "incomplete body"))
            assertEquals(false, it.bookmarkReadAdmission(chat, reply).targetAvailable)
            write(it, "user2", ConversationTimelineIntent.AcceptUser(ConversationMessage(ConversationMessageId("user2"), chat,
                ConversationTurnId("turn2"), ConversationMessageRole.USER, ConversationMessageState.ACCEPTED, "next body", 3)))
            val completed = ConversationMessageId("reply2")
            write(it, "start2", ConversationTimelineIntent.StartFaro(ConversationMessage(completed, chat,
                ConversationTurnId("turn2"), ConversationMessageRole.FARO, ConversationMessageState.PROCESSING, "", 4)))
            write(it, "done2", ConversationTimelineIntent.ResolveFaro(completed, ConversationTurnId("turn2"),
                ConversationMessageState.COMPLETED, "completed body"))
            val facts = it.bookmarkReadAdmission(chat, completed)
            assertEquals(true, facts.targetAvailable)
            assertFalse(facts.toString().contains("body"))
            assertFalse(facts.toString().contains(facts.authorityToken))
        }
    }

    @Test fun closedOwnerCannotSupplyAdmission() {
        val store = store()
        store.close()
        assertThrows(IllegalStateException::class.java) { store.bookmarkReadAdmission() }
    }
}
