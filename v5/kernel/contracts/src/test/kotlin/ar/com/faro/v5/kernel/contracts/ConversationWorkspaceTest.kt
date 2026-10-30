package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationWorkspaceTest {
    private fun chat(
        number: Int,
        activity: Long = number.toLong(),
        pinned: Boolean = false,
        archived: Boolean = false,
    ) = ConversationWorkspaceEntry(
        id = ConversationId("chat-$number"),
        title = "Conversación $number",
        lastActivityEpochMillis = activity,
        pinned = pinned,
        archived = archived,
    )

    @Test
    fun emptySnapshotDoesNotCreateAPhantomConversation() {
        val snapshot = ConversationWorkspaceSnapshot(emptyList(), null)
        val view = ConversationWorkspaceProjector.project(snapshot)

        assertEquals(emptyList<ConversationWorkspaceEntry>(), view.allConversations)
        assertNull(view.activeConversationId)
        assertFalse(view.hasMore)
    }

    @Test
    fun oneConversationCanBeActive() {
        val entry = chat(1)
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(listOf(entry), entry.id)
            )

        assertEquals(listOf(entry), view.recent)
        assertEquals(entry.id, view.activeConversationId)
        assertFalse(view.hasMore)
    }

    @Test
    fun sevenRecentUseActivityOrderAndEighthRemainsAvailableInHistory() {
        val entries = (1..8).map { chat(it) }
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(entries.reversed(), entries.last().id)
            )

        assertEquals((8 downTo 2).map { ConversationId("chat-$it") }, view.recent.map { it.id })
        assertEquals(listOf(ConversationId("chat-1")), view.older.map { it.id })
        assertEquals(8, view.allConversations.size)
        assertTrue(view.hasMore)
    }

    @Test
    fun pinnedConversationsDoNotConsumeRecentQuotaOrAppearTwice() {
        val entries = (1..9).map { chat(it, pinned = it == 9 || it == 8) }
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(entries, ConversationId("chat-7"))
            )

        assertEquals(listOf(ConversationId("chat-9"), ConversationId("chat-8")), view.pinned.map { it.id })
        assertEquals((7 downTo 1).map { ConversationId("chat-$it") }, view.recent.map { it.id })
        assertEquals(9, view.allConversations.map { it.id }.toSet().size)
        assertFalse(view.hasMore)
    }

    @Test
    fun archivedRemainsRecoverableButNeverAppearsInRecentOrPinned() {
        val archived = chat(5, pinned = true, archived = true)
        val visible = chat(2)
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(listOf(archived, visible), archived.id)
            )

        assertEquals(listOf(visible), view.recent)
        assertTrue(view.pinned.isEmpty())
        assertEquals(listOf(archived), view.archived)
        assertNull(view.activeConversationId)
        assertTrue(view.hasMore)
    }

    @Test
    fun staleActiveSelectionFailsClosedWithoutSelectingAnotherConversation() {
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(listOf(chat(1)), ConversationId("deleted"))
            )

        assertNull(view.activeConversationId)
        assertEquals(1, view.allConversations.size)
    }

    @Test
    fun duplicateConversationIdsAreRejectedAtReadBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            ConversationWorkspaceSnapshot(listOf(chat(1), chat(1, activity = 100)), null)
        }
    }

    @Test
    fun equalTimestampsProduceStableIdOrder() {
        val entries = listOf(chat(3, 10), chat(1, 10), chat(2, 10))
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(entries, null)
            )

        assertEquals(
            listOf(ConversationId("chat-1"), ConversationId("chat-2"), ConversationId("chat-3")),
            view.recent.map { it.id },
        )
    }

    @Test
    fun pendingReviewStateIsOnlyProjectedFromSourceMetadata() {
        val entry = chat(1).copy(hasPendingReview = true)
        val view =
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(listOf(entry), entry.id)
            )

        assertTrue(view.recent.single().hasPendingReview)
    }
}
