package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5V2CoreDevBookmarksPresentationT2Test {
    @Test fun unavailableIsDifferentFromAuthorizedEmpty() {
        assertEquals(FaroV5V2CoreDevBookmarksPresentation.unavailable,
            FaroV5V2CoreDevBookmarksPresentation.render(null))
        val empty = FaroV5V2CoreDevBookmarksPresentation.render(FaroV5BookmarkHostPage(emptyList(), emptyList(), false))
        assertTrue(empty.contains("No hay referencias"))
        assertNotEquals(FaroV5V2CoreDevBookmarksPresentation.unavailable, empty)
    }

    @Test fun boundedMetadataLabelsNeverRenderSourceIdentifiers() {
        val folder = BookmarkFolder(BookmarkFolderId("folder-opaque"), "Lecturas")
        val page = FaroV5BookmarkHostPage(listOf(FaroV5BookmarkHostItem(
            BookmarkId("bookmark-secret-id"), ConversationId("chat-secret-id"),
            ConversationMessageId("message-secret-id"), folder.id, true,
        )), listOf(folder), true)
        val text = FaroV5V2CoreDevBookmarksPresentation.render(page)
        assertTrue(text.contains("Referencia 1 · Favorito · Lecturas"))
        assertTrue(text.contains("Hay más referencias"))
        for (secret in listOf("bookmark-secret-id", "chat-secret-id", "message-secret-id", "folder-opaque"))
            assertFalse(text.contains(secret))
    }

    @Test fun rootNonFavoriteAndCompletePageDoNotInventFolderOrGlobalCount() {
        val text = FaroV5V2CoreDevBookmarksPresentation.render(FaroV5BookmarkHostPage(listOf(
            FaroV5BookmarkHostItem(BookmarkId("b"), ConversationId("c"),
                ConversationMessageId("m"), null, false),
        ), emptyList(), false))
        assertEquals("Referencia 1", text)
    }
}
