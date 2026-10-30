package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationWorkspaceTransitionsTest {
    private fun chat(
        id: String = "chat-1",
        pinned: Boolean = false,
        archived: Boolean = false,
        pending: Boolean = false,
    ) = ConversationWorkspaceEntry(
        id = ConversationId(id),
        title = "Conversación",
        lastActivityEpochMillis = 17,
        pinned = pinned,
        archived = archived,
        hasPendingReview = pending,
    )

    private fun snapshot(
        vararg entries: ConversationWorkspaceEntry,
        active: ConversationId? = null,
    ) = ConversationWorkspaceSnapshot(entries.toList(), active)

    private fun accepted(
        snapshot: ConversationWorkspaceSnapshot,
        intent: ConversationWorkspaceIntent,
    ): ConversationWorkspaceTransition.Accepted =
        ConversationWorkspaceTransitions.evaluate(snapshot, intent)
            as ConversationWorkspaceTransition.Accepted

    @Test fun emptyWorkspaceCannotSelectOrInventAConversation() {
        assertEquals(
            ConversationWorkspaceTransition.Rejected(ConversationWorkspaceRejection.NOT_FOUND),
            ConversationWorkspaceTransitions.evaluate(
                snapshot(),
                ConversationWorkspaceIntent.Activate(ConversationId("missing")),
            ),
        )
    }

    @Test fun activatingExistingChatKeepsIdentityAndDoesNotCreateRows() {
        val original = chat()
        val result = accepted(snapshot(original), ConversationWorkspaceIntent.Activate(original.id))
        assertEquals(original.id, result.snapshot.activeConversationId)
        assertEquals(listOf(original), result.snapshot.conversations)
        assertTrue(result.changed)
    }

    @Test fun selectingSameConversationIsIdempotentAtProjectionLevel() {
        val original = chat()
        val input = snapshot(original, active = original.id)
        val result = accepted(input, ConversationWorkspaceIntent.Activate(original.id))
        assertEquals(input, result.snapshot)
        assertFalse(result.changed)
    }

    @Test fun archivedConversationCannotBeActivated() {
        val original = chat(archived = true)
        assertEquals(
            ConversationWorkspaceTransition.Rejected(ConversationWorkspaceRejection.ARCHIVED),
            ConversationWorkspaceTransitions.evaluate(
                snapshot(original),
                ConversationWorkspaceIntent.Activate(original.id),
            ),
        )
    }

    @Test fun renameTrimsTitleWithoutChangingMessageActivityOrder() {
        val original = chat()
        val result = accepted(
            snapshot(original),
            ConversationWorkspaceIntent.Rename(original.id, "  Nuevo título  "),
        )
        assertEquals("Nuevo título", result.snapshot.conversations.single().title)
        assertEquals(17L, result.snapshot.conversations.single().lastActivityEpochMillis)
        assertEquals("Conversación", original.title)
    }

    @Test fun renameRejectsEmptyOversizedAndControlCharacters() {
        val original = chat()
        for (invalid in listOf("   ", "x".repeat(81), "línea\notra")) {
            assertEquals(
                ConversationWorkspaceTransition.Rejected(ConversationWorkspaceRejection.INVALID_TITLE),
                ConversationWorkspaceTransitions.evaluate(
                    snapshot(original),
                    ConversationWorkspaceIntent.Rename(original.id, invalid),
                ),
            )
        }
    }

    @Test fun pinIsMetadataOnlyAndDoesNotReduceRecentQuota() {
        val rows = (1..9).map { chat("chat-$it") }
        val updated = accepted(
            ConversationWorkspaceSnapshot(rows, null),
            ConversationWorkspaceIntent.SetPinned(ConversationId("chat-9"), true),
        )
        val drawer = ConversationWorkspaceProjector.project(updated.snapshot)
        assertEquals(listOf(ConversationId("chat-9")), drawer.pinned.map { it.id })
        assertEquals(7, drawer.recent.size)
        assertEquals(1, drawer.older.size)
    }

    @Test fun archivingPinnedActiveClearsSelectionButDoesNotSelectReplacement() {
        val original = chat(pinned = true)
        val other = chat("chat-2")
        val result = accepted(
            snapshot(original, other, active = original.id),
            ConversationWorkspaceIntent.SetArchived(original.id, true),
        )
        assertEquals(null, result.snapshot.activeConversationId)
        assertTrue(result.snapshot.conversations.first().archived)
        assertFalse(result.snapshot.conversations.first().pinned)
        assertEquals(2, result.snapshot.conversations.size)
    }

    @Test fun pendingReviewCannotBeSilentlyHiddenByArchive() {
        val original = chat(pending = true)
        assertEquals(
            ConversationWorkspaceTransition.Rejected(ConversationWorkspaceRejection.PENDING_REVIEW),
            ConversationWorkspaceTransitions.evaluate(
                snapshot(original),
                ConversationWorkspaceIntent.SetArchived(original.id, true),
            ),
        )
    }

    @Test fun unarchivingDoesNotAutoSelectOrCreateAnotherConversation() {
        val original = chat(archived = true)
        val result = accepted(
            snapshot(original),
            ConversationWorkspaceIntent.SetArchived(original.id, false),
        )
        assertFalse(result.snapshot.conversations.single().archived)
        assertEquals(null, result.snapshot.activeConversationId)
    }

    @Test fun archivedConversationCannotBePinned() {
        val original = chat(archived = true)
        assertEquals(
            ConversationWorkspaceTransition.Rejected(ConversationWorkspaceRejection.ARCHIVED),
            ConversationWorkspaceTransitions.evaluate(
                snapshot(original),
                ConversationWorkspaceIntent.SetPinned(original.id, true),
            ),
        )
    }

    @Test fun repeatedArchiveDoesNotMutateSnapshot() {
        val original = chat(archived = true)
        val input = snapshot(original)
        val result = accepted(
            input,
            ConversationWorkspaceIntent.SetArchived(original.id, true),
        )
        assertEquals(input, result.snapshot)
        assertFalse(result.changed)
    }
}
