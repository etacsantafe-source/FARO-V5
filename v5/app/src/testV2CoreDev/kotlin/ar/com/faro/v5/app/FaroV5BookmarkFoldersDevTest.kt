package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
class FaroV5BookmarkFoldersDevTest {
    @TempDir lateinit var root: Path
    @Test fun openingAndPausingSessionGrantsNoFolderOrRecoveryAction() {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            session.revokeBookmarkFolderActionsOnPause()
            session.revokeBookmarkReadingOnPause()
            session.revokeBookmarkFolderActionsOnPause()
            assertNotNull(session.timelineReadPort)
        }
        sourceRowIsOneShotAndCannotReuseCopiedItem()
        lateRowCallbackCannotSurvivePagePauseOrDialogChange()
        nativeReadingRevokeKeepsCanonicalSessionUsable()
    }
    private fun sourceRowIsOneShotAndCannotReuseCopiedItem() {
        val item=FaroV5BookmarkHostItem(BookmarkId("saved"),ConversationId("chat"),ConversationMessageId("message"),null,false)
        val page=FaroV5BookmarkHostPage(listOf(item),emptyList(),false)
        val click=FaroV5BookmarkSourceClick(page,item) { true }
        assertTrue(click.consume()); assertFalse(click.consume())
        assertFalse(FaroV5BookmarkSourceClick(page,item.copy()) { true }.consume())
    }
    private fun lateRowCallbackCannotSurvivePagePauseOrDialogChange() {
        val item=FaroV5BookmarkHostItem(BookmarkId("saved"),ConversationId("chat"),ConversationMessageId("message"),null,false)
        val page=FaroV5BookmarkHostPage(listOf(item),emptyList(),false)
        var live=true
        val click=FaroV5BookmarkSourceClick(page,item) { live }
        live=false; assertFalse(click.consume())
        live=true; assertFalse(click.consume())
    }
    private fun nativeReadingRevokeKeepsCanonicalSessionUsable() = kotlinx.coroutines.runBlocking {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            session.revokeBookmarkReadingOnPause()
            val id=ConversationId("same-session")
            assertTrue(session.selectionPort.createAndSelect(id,1,IdempotencyKey("same-session")) is ConversationSelectionResult.Saved)
            assertEquals(id,session.workspaceReadPort.readSnapshot().activeConversationId)
            assertNotNull(session.timelineReadPort.readTimeline(id))
        }
    }
}
