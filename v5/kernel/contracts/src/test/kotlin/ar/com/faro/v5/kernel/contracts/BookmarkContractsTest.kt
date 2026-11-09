package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookmarkContractsTest {
    private fun target(
        conversation: String = "chat-a",
        generation: String = "generation-a",
        authority: String = "authority-a",
    ) = BookmarkTarget(
        conversationId = ConversationId(conversation),
        messageId = ConversationMessageId("message-1"),
        generationId = generation,
        authorityToken = authority,
    )

    private fun item() =
        BookmarkMetadata(id = BookmarkId("bookmark-1"), target = target())

    @Test
    fun exactlyEightCandidateOperationsWithCorrectProfiles() {
        val names = BookmarkOperationIds.proposedProfiles.keys.map { it.value }
        assertEquals(
            listOf(
                "search.bookmark.read",
                "search.bookmark.add",
                "search.bookmark.lifecycle.set",
                "search.bookmark.favorite.set",
                "search.bookmark.folder.create",
                "search.bookmark.folder.rename",
                "search.bookmark.folder.delete",
                "search.bookmark.folder.assign",
            ), names,
        )
        assertEquals(8, names.distinct().size)
        assertEquals(ExecutionProfile.READ_ONLY, BookmarkOperationIds.proposedProfiles[BookmarkOperationIds.read])
        assertEquals(7, BookmarkOperationIds.proposedProfiles.values.count {
            it == ExecutionProfile.AUXILIARY_MUTATION
        })
    }

    @Test
    fun opaqueIdsRejectBlankOverlongAndControlCharacters() {
        assertEquals("bookmark-1", BookmarkId("bookmark-1").value)
        assertThrows(IllegalArgumentException::class.java) { BookmarkId("  ") }
        assertThrows(IllegalArgumentException::class.java) { BookmarkId("a".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { BookmarkFolderId("bad\nid") }
        assertThrows(IllegalArgumentException::class.java) { BookmarkFolderId("") }
    }

    @Test
    fun targetBindsExactConversationMessageAndGeneration() {
        val valid = target()
        assertEquals("chat-a", valid.conversationId.value)
        assertEquals("message-1", valid.messageId.value)
        assertNotEquals(valid, target(conversation = "chat-b"))
        assertNotEquals(valid, target(generation = "generation-b"))
        assertThrows(IllegalArgumentException::class.java) { target(generation = "") }
        assertThrows(IllegalArgumentException::class.java) { target(authority = "") }
    }

    @Test
    fun bookmarkMetadataDoesNotDuplicateMessageTextOrRevealAuthority() {
        val bookmark = item()
        val fields = BookmarkMetadata::class.java.declaredFields.map { it.name }
        assertFalse("text" in fields)
        assertFalse("messageText" in fields)
        assertEquals(BookmarkLifecycle.ACTIVE, bookmark.lifecycle)
        assertEquals(null, bookmark.folderId)
        assertFalse(bookmark.favorite)
        assertFalse(bookmark.target.toString().contains("authority-a"))
    }

    @Test
    fun flatFoldersHaveBoundedValidatedNamesAndIdentity() {
        val folder = BookmarkFolder(BookmarkFolderId("folder-a"), "Personal")
        assertEquals(1L, folder.revision)
        assertEquals(80, BookmarkFolder(BookmarkFolderId("f"), "x".repeat(80)).name.length)
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkFolder(BookmarkFolderId("f"), "x".repeat(81))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkFolder(BookmarkFolderId("f"), " Personal ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkMutation.FolderRename(BookmarkFolderId("f"), "")
        }
        assertEquals(32, BookmarkPolicy.MAX_FOLDERS)
    }

    @Test
    fun favoriteLifecycleAndFolderMovesAreDistinctAuxiliaryIntents() {
        val intents: List<BookmarkMutation> = listOf(
            BookmarkMutation.Add(item()),
            BookmarkMutation.LifecycleSet(BookmarkId("bookmark-1"), BookmarkLifecycle.REMOVED),
            BookmarkMutation.FavoriteSet(BookmarkId("bookmark-1"), true),
            BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"), "Grupo")),
            BookmarkMutation.FolderRename(BookmarkFolderId("f"), "Otro"),
            BookmarkMutation.FolderDelete(BookmarkFolderId("f")),
            BookmarkMutation.FolderAssign(BookmarkId("bookmark-1"), null),
        )
        assertEquals(7, intents.map { it.javaClass }.distinct().size)
        assertEquals(null, (intents.last() as BookmarkMutation.FolderAssign).folderId)
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkMutation.Add(item().copy(lifecycle = BookmarkLifecycle.REMOVED))
        }
        assertFalse(BookmarkMutation.FolderDelete::class.java.declaredFields.any {
            it.name.contains("cascade", ignoreCase = true)
        })
    }

    @Test
    fun readPagesAreBoundedAndUnavailableIsNotAnEmptyAuthorizedPage() {
        assertEquals(50, BookmarkReadRequest("generation-a", "authority-a").limit)
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkReadRequest("generation-a", "authority-a", limit = 51)
        }
        val page = BookmarkReadPage(listOf(item()), emptyList(), hasMore = false)
        assertEquals(1, page.bookmarks.size)
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkReadPage(listOf(item(), item()), emptyList(), hasMore = false)
        }
        val available: BookmarkReadResult = BookmarkReadResult.Available(
            BookmarkReadPage(emptyList(), emptyList(), hasMore = false)
        )
        assertNotEquals(BookmarkReadResult.Unavailable, available)
        assertEquals(1024, BookmarkPolicy.MAX_BOOKMARKS)
    }

    @Test
    fun writesRequireMatchingGenerationCASAndIdempotency() {
        val request = BookmarkMutationRequest(
            generationId = "generation-a",
            authorityToken = "authority-a",
            expectedRevision = 0,
            idempotencyKey = IdempotencyKey("bookmark-add-1"),
            mutation = BookmarkMutation.Add(item()),
        )
        assertEquals(0L, request.expectedRevision)
        assertTrue(request.mutation is BookmarkMutation.Add)
        assertFalse(request.toString().contains("authority-a"))
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(expectedRevision = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(generationId = "generation-b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(idempotencyKey = IdempotencyKey("x".repeat(129)))
        }
        assertEquals(BookmarkMutationResult.IdentityConflict, BookmarkMutationResult.IdentityConflict)
        assertEquals(BookmarkMutationResult.Stale(2), BookmarkMutationResult.Stale(2))
        assertEquals(BookmarkMutationResult.Unavailable, BookmarkMutationResult.Unavailable)
    }
}
