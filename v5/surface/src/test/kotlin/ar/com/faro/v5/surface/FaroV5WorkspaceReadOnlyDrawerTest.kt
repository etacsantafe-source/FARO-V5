package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceProjector
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5WorkspaceReadOnlyDrawerTest {
    private fun entry(id: String, activity: Long, pinned: Boolean = false, archived: Boolean = false) =
        ConversationWorkspaceEntry(
            id = ConversationId(id), title = "Chat $id",
            lastActivityEpochMillis = activity, pinned = pinned, archived = archived,
        )

    @Test
    fun unboundReadPortNeverInventsHistoryOrSelection() {
        assertTrue(faroV5AdditionalWorkspaceEntries(null, expanded = true).isEmpty())
        assertTrue(faroV5DrawerRootIsSelected(
            FaroV5ShellDestination.CHAT, FaroV5ShellDestination.CHAT, null
        ))
    }

    @Test
    fun activeChatNeverHighlightsChatRootAtTheSameTime() {
        val selected = ConversationId("existing")
        assertFalse(faroV5DrawerRootIsSelected(
            FaroV5ShellDestination.CHAT, FaroV5ShellDestination.CHAT, selected
        ))
        assertFalse(faroV5DrawerRootIsSelected(
            FaroV5ShellDestination.CHAT, FaroV5ShellDestination.FINANZAS, selected
        ))
        assertTrue(faroV5DrawerRootIsSelected(
            FaroV5ShellDestination.FINANZAS, FaroV5ShellDestination.FINANZAS, selected
        ))
    }

    @Test
    fun recentsKeepSevenWhilePinnedDoNotConsumeQuota() {
        val all = (1..9).map { entry("c$it", it.toLong()) } +
            entry("pinned", 99, pinned = true)
        val projection = ConversationWorkspaceProjector.project(
            ConversationWorkspaceSnapshot(all, ConversationId("c9"))
        )
        assertEquals(1, projection.pinned.size)
        assertEquals(7, projection.recent.size)
        assertEquals(2, projection.older.size)
        assertTrue(faroV5AdditionalWorkspaceEntries(projection, expanded = false).isEmpty())
        assertEquals(2, faroV5AdditionalWorkspaceEntries(projection, expanded = true).size)
        assertTrue(projection.recent.none { it.pinned || it.archived })
    }

    @Test
    fun archivedAreHiddenFromRecentsButAvailableOnExpansion() {
        val projection = ConversationWorkspaceProjector.project(
            ConversationWorkspaceSnapshot(
                listOf(entry("recent", 3), entry("archived", 8, archived = true)),
                ConversationId("recent")
            )
        )
        assertEquals(listOf("recent"), projection.recent.map { it.id.value })
        assertEquals(listOf("archived"),
            faroV5AdditionalWorkspaceEntries(projection, expanded = true)
                .map { it.id.value })
        assertTrue(projection.hasMore)
    }
    @Test
    fun validEmptyWorkspaceIsDistinctFromUnboundLoadingAndFailure() {
        val empty = ConversationWorkspaceProjector.project(
            ConversationWorkspaceSnapshot(emptyList(), null)
        )
        val states: List<FaroV5WorkspaceReadState> = listOf(
            FaroV5WorkspaceReadState.Unbound,
            FaroV5WorkspaceReadState.Loading,
            FaroV5WorkspaceReadState.Failed,
        )
        assertTrue(states.none { it is FaroV5WorkspaceReadState.Ready })
        assertEquals(empty, FaroV5WorkspaceReadState.Ready(empty).projection)
        assertTrue(empty.allConversations.isEmpty())
    }

}
