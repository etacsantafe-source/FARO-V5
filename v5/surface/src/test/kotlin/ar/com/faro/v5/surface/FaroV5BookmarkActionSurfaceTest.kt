package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class FaroV5BookmarkActionSurfaceTest {
    @Test fun equalVisibleFieldsNeverForgeConsentIdentity() {
        val a = FaroV5BookmarkChatReview(ConversationId("chat"),ConversationMessageId("message"),"Guardar mensaje")
        val b = FaroV5BookmarkChatReview(a.conversationId,a.messageId,a.label)
        assertNotEquals(a,b)
        assertEquals(2,FaroV5BookmarkChatAction.entries.size)
    }
}
