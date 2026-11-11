package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkReviewRevisionTest {
    private fun <T> runBlocking(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
    @TempDir lateinit var root: Path
    private val target = BookmarkTarget(ConversationId("chat"), ConversationMessageId("message"), "gen", "token")
    private class Gate : BookmarkAuthorityGate {
        var live = true
        var available = true
        var reads = 0
        var revokeAt = Int.MAX_VALUE
        override fun isScopeActive(generationId: String, authorityToken: String): Boolean = ++reads < revokeAt && live
        override fun isTargetAvailable(target: BookmarkTarget) = available && live
        override fun isMutationActive(generationId: String, authorityToken: String) = live
    }
    @Test fun missingScopeIsAuthorizedZeroWithoutAnyWrite() {
        AuxiliaryDeliveryStore(root.resolve("new").toString()).use { store ->
            val gate = Gate(); val before = store.backup.snapshot()
            assertEquals(BookmarkReviewSnapshot(0,null,null), store.bookmarkReviewSnapshotForTrustedHost(gate,target))
            assertEquals(before, store.backup.snapshot())
            gate.live = false
            assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target))
        }
    }
    @Test fun canonicalScopeTwoDiffersFromItemOneAndRemovedIsHidden() = runBlocking {
        val gate = Gate()
        AuxiliaryDeliveryStore(root.resolve("existing").toString(), bookmarkAuthorityGate = gate).use { store ->
            suspend fun mutate(rev: Long, key: String, mutation: BookmarkMutation) = store.bookmarks.mutate(
                BookmarkMutationRequest("gen","token",rev,IdempotencyKey(key),mutation))
            mutate(0,"folder",BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"),"Folder")))
            mutate(1,"add",BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b"),target)))
            assertEquals(BookmarkReviewSnapshot(2,BookmarkId("b"),false),store.bookmarkReviewSnapshotForTrustedHost(gate,target))
            assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target.copy(authorityToken="foreign")))
            assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target.copy(generationId="foreign")))
            assertEquals(BookmarkReviewSnapshot(2,null,null),store.bookmarkReviewSnapshotForTrustedHost(gate,target.copy(conversationId=ConversationId("other"))))
            mutate(2,"remove",BookmarkMutation.LifecycleSet(BookmarkId("b"),BookmarkLifecycle.REMOVED))
            assertEquals(BookmarkReviewSnapshot(3,null,null),store.bookmarkReviewSnapshotForTrustedHost(gate,target))
        }
    }
    @Test fun revokeDuringReadAndUnavailableTargetAndClosedOwnerDeny() {
        val store = AuxiliaryDeliveryStore(root.resolve("deny").toString()); val gate = Gate()
        val before = store.backup.snapshot()
        gate.revokeAt = 2
        assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target))
        assertEquals(before,store.backup.snapshot())
        gate.revokeAt = Int.MAX_VALUE; gate.available = false
        assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target))
        gate.available = true; store.close()
        assertNull(store.bookmarkReviewSnapshotForTrustedHost(gate,target))
    }
}
