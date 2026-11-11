package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Surface policy acceptance. Real Compose gestures/scroll remain in the final physical campaign. */
class FaroV5BookmarkA3UiAcceptanceTest {
    @Test fun sourceFocusAndDrawerAgreeOnlyAfterCanonicalSelection() {
        val old=ConversationId("old"); val source=ConversationId("source"); val message=ConversationMessageId("same-id")
        val target=FaroV5BookmarkSourceTarget(source,message)
        val timeline=ConversationTimelineSnapshot(source,"authority",3,listOf(ConversationMessage(message,source,
            ConversationTurnId("turn"),ConversationMessageRole.USER,ConversationMessageState.ACCEPTED,"fixture",1)))
        val entries=listOf(ConversationWorkspaceEntry(old,"Old",1),ConversationWorkspaceEntry(source,"Source",2))
        val before=ConversationWorkspaceProjector.project(ConversationWorkspaceSnapshot(entries,old))
        assertNull(faroV5BookmarkSourceFocus(target,before.activeConversationId,timeline))
        val after=ConversationWorkspaceProjector.project(ConversationWorkspaceSnapshot(entries,source))
        assertEquals(message,faroV5BookmarkSourceFocus(target,after.activeConversationId,timeline))
        assertFalse(faroV5DrawerRootIsSelected(FaroV5ShellDestination.CHAT,FaroV5ShellDestination.CHAT,after.activeConversationId))
        assertEquals(listOf(FaroV5ShellDestination.CHAT),faroV5NextShellStack(listOf(FaroV5ShellDestination.CHAT,FaroV5ShellDestination.GUARDADOS),FaroV5ShellDestination.CHAT))
    }
    @Test fun missingOrDisposedHandoffCannotFocusPreviouslyVisibleMessage() {
        val chat=ConversationId("chat"); val message=ConversationMessageId("message")
        val target=FaroV5BookmarkSourceTarget(chat,message)
        val missing=ConversationTimelineSnapshot(chat,"authority",2,emptyList())
        assertNull(faroV5BookmarkSourceFocus(target,chat,missing))
        assertNull(faroV5BookmarkSourceFocus(null,chat,missing))
        assertEquals(listOf(FaroV5ShellDestination.CHAT),faroV5RestoreShellStack(emptyList()))
    }
}
