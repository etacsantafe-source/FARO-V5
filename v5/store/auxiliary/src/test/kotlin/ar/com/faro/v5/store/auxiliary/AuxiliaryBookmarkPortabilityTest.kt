package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkPortabilityTest {
    @TempDir lateinit var root: Path
    private class Gate : BookmarkAuthorityGate {
        override fun isMutationActive(generationId: String, authorityToken: String) = active
        var active = true
        var revokeOnTarget = false
        override fun isScopeActive(generationId: String, authorityToken: String) = active
        override fun isTargetAvailable(target: BookmarkTarget): Boolean {
            if (revokeOnTarget) active = false
            return true
        }
    }
    private fun <T> sync(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome).getOrThrow()
    }
    private fun request(revision: Long, mutation: BookmarkMutation) = BookmarkMutationRequest(
        "gen-a", "private-token", revision, IdempotencyKey("private-receipt-$revision"), mutation)
    private fun seed(store: AuxiliaryDeliveryStore) {
        sync { store.bookmarks.mutate(request(0, BookmarkMutation.FolderCreate(
            BookmarkFolder(BookmarkFolderId("f1"), "Ideas")))) }
        for ((i, id) in listOf("visible", "removed").withIndex()) {
            sync { store.bookmarks.mutate(request((i + 1).toLong(), BookmarkMutation.Add(
                BookmarkMetadata(BookmarkId(id), BookmarkTarget(ConversationId("chat"),
                    ConversationMessageId(id), "gen-a", "private-token"), BookmarkFolderId("f1"))))) }
        }
        sync { store.bookmarks.mutate(request(3, BookmarkMutation.LifecycleSet(BookmarkId("removed"), BookmarkLifecycle.REMOVED))) }
    }
    private fun store(gate: Gate? = Gate()) = AuxiliaryDeliveryStore(
        root.resolve("aux.db").toString(), bookmarkAuthorityGate = gate)

    @Test fun onlyActiveAuthorizedMetadataIsExportableWithoutAuthorityOrReceipts() {
        store().use {
            seed(it)
            val snapshot = it.bookmarkPortability("gen-a", "private-token").snapshot()
            assertEquals(listOf("visible"), snapshot.records.map { b -> b.id })
            assertEquals("Ideas", snapshot.folders.single().name)
            assertEquals("chat", snapshot.records.single().conversationId)
            val text = snapshot.toString()
            for (excluded in listOf("private-token", "private-receipt", "removed", "authorityDigest", "requestHash")) {
                assertFalse(text.contains(excluded), excluded)
            }
            assertTrue(snapshot.sourceWatermark.matches(Regex("[0-9a-f]{64}")))
            assertEquals(0, it.portability.snapshot().notices.size)
        }
    }

    @Test fun missingDefaultAuthorityAndWrongScopeCannotReturnAnEmptySuccess() {
        store(null).use {
            assertThrows(IllegalStateException::class.java) { it.bookmarkPortability("gen-a", "private-token").snapshot() }
        }
        store().use {
            seed(it)
            assertThrows(IllegalStateException::class.java) { it.bookmarkPortability("gen-b", "private-token").snapshot() }
            assertThrows(IllegalStateException::class.java) { it.bookmarkPortability("gen-a", "new-token").snapshot() }
        }
    }

    @Test fun revocationDuringTargetValidationRejectsWholeProjection() {
        val gate = Gate()
        store(gate).use {
            seed(it)
            gate.revokeOnTarget = true
            assertThrows(IllegalStateException::class.java) { it.bookmarkPortability("gen-a", "private-token").snapshot() }
        }
    }

    @Test fun folderRenameAndFavoriteChangeInvalidateExportWatermark() {
        store().use {
            seed(it)
            val port = it.bookmarkPortability("gen-a", "private-token")
            val initial = port.snapshot()
            sync { it.bookmarks.mutate(request(4, BookmarkMutation.FolderRename(BookmarkFolderId("f1"), "Changed"))) }
            val renamed = port.snapshot()
            assertNotEquals(initial.sourceWatermark, renamed.sourceWatermark)
            sync { it.bookmarks.mutate(request(5, BookmarkMutation.FavoriteSet(BookmarkId("visible"), true))) }
            val favorite = port.snapshot()
            assertNotEquals(renamed.sourceWatermark, favorite.sourceWatermark)
            assertTrue(favorite.records.single().favorite)
        }
    }
}
