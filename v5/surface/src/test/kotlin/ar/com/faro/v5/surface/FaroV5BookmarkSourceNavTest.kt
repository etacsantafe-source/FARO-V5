package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5BookmarkSourceNavTest {
    private val chat = ConversationId("source")
    private val message = ConversationMessageId("message")
    private val target = FaroV5BookmarkSourceTarget(chat, message)
    private fun timeline(id: ConversationId = chat) = ConversationTimelineSnapshot(id,"authority",1,
        listOf(ConversationMessage(message,id,ConversationTurnId("turn"),ConversationMessageRole.USER,
            ConversationMessageState.ACCEPTED,"source",1)))
    @Test fun exactSourceFocusesOnlyItsCanonicalTimeline() {
        assertEquals(message, faroV5BookmarkSourceFocus(target,chat,timeline()))
    }
    @Test fun sameMessageIdInAnotherConversationNeverFocuses() {
        val other=ConversationId("other")
        assertNull(faroV5BookmarkSourceFocus(target,other,timeline(other)))
        assertNull(faroV5BookmarkSourceFocus(target,chat,timeline(other)))
        assertNull(faroV5BookmarkSourceFocus(target,other,timeline()))
    }
    @Test fun absentSourceOrSelectionNeverFocuses() {
        assertNull(faroV5BookmarkSourceFocus(target,chat,null))
        assertNull(faroV5BookmarkSourceFocus(target,null,timeline()))
        assertNull(faroV5BookmarkSourceFocus(null,chat,timeline()))
        assertNull(faroV5BookmarkSourceFocus(target,chat,timeline().copy(messages=emptyList())))
    }
}
