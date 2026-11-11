package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkRemovedRestoreLookupTest {
    @TempDir lateinit var root: Path
    private fun <T> run(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
    private class Gate : BookmarkAuthorityGate {
        var live = true
        var calls = 0
        var revokeAt = Int.MAX_VALUE
        var denied: String? = null
        override fun isScopeActive(generationId: String, authorityToken: String) = live && ++calls < revokeAt
        override fun isTargetAvailable(target: BookmarkTarget) = live && target.messageId.value != denied
        override fun isMutationActive(generationId: String, authorityToken: String) = live
    }
    private val request = BookmarkReadRequest("gen", "token")
    private fun seed(store: AuxiliaryDeliveryStore, count: Int) = run {
        var revision = 0L
        for (index in 0 until count) {
            val id = BookmarkId("b%04d".format(index))
            val target = BookmarkTarget(ConversationId("chat"), ConversationMessageId("m$index"), "gen", "token")
            for (action in listOf(BookmarkMutation.Add(BookmarkMetadata(id,target)), BookmarkMutation.LifecycleSet(id,BookmarkLifecycle.REMOVED))) {
                assertTrue(store.bookmarks.mutate(BookmarkMutationRequest("gen","token",revision,IdempotencyKey("k${revision}"),action)) is BookmarkMutationResult.Saved)
                revision++
            }
        }
    }
    @Test fun lookupAfterOwnerReopenIsReadOnlyCanonicalAndActiveBlind() {
        val path = root.resolve("reopen").toString(); val gate = Gate()
        AuxiliaryDeliveryStore(path,bookmarkAuthorityGate=gate).use { seed(it,1) }
        AuxiliaryDeliveryStore(path).use { store ->
            val before = store.backup.snapshot()
            val page = requireNotNull(store.bookmarkRemovedByIdForTrustedHost(gate,request,BookmarkId("b0000")))
            assertEquals(2,page.scopeRevision); assertEquals(1,page.items.size)
            assertEquals(BookmarkId("b0000"), page.items.single().id)
            assertTrue(requireNotNull(store.bookmarkRemovedByIdForTrustedHost(gate,request,BookmarkId("missing"))).items.isEmpty())
            val active = run { store.bookmarkReaderForTrustedHost(gate).read(request) } as BookmarkReadResult.Available
            assertTrue(active.page.bookmarks.isEmpty())
            assertEquals(BookmarkReviewSnapshot(2,null,null),store.bookmarkReviewSnapshotForTrustedHost(gate,
                BookmarkTarget(ConversationId("chat"),ConversationMessageId("m0"),"gen","token")))
            assertEquals(before,store.backup.snapshot())
        }
    }
    @Test fun boundedFiftyPlusOnePaginationAndLookaheadRevocationDenyWholePage() {
        val gate = Gate()
        AuxiliaryDeliveryStore(root.resolve("pages").toString(),bookmarkAuthorityGate=gate).use { store ->
            seed(store,51)
            val page = requireNotNull(store.bookmarkRemovedPageForTrustedHost(gate,request))
            assertEquals(50,page.items.size); assertTrue(page.hasMore); assertEquals(102,page.scopeRevision)
            val next = requireNotNull(store.bookmarkRemovedPageForTrustedHost(gate,request.copy(cursor=page.items.last().id)))
            assertEquals(1,next.items.size); assertFalse(next.hasMore)
            gate.denied="m50" // lookahead is not visible, but must not leak through hasMore
            assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request))
            gate.denied="m0"
            assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request))
        }
    }
    @Test fun wrongGenerationTokenScopeClosedAndMidReadRevokeNeverFabricateAbsence() {
        val gate = Gate(); val store = AuxiliaryDeliveryStore(root.resolve("deny").toString(),bookmarkAuthorityGate=gate)
        seed(store,1)
        assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request.copy(generationId="foreign")))
        assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request.copy(authorityToken="foreign")))
        gate.calls=0; gate.revokeAt=2
        assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request))
        gate.revokeAt=Int.MAX_VALUE; gate.live=false
        assertNull(store.bookmarkRemovedByIdForTrustedHost(gate,request,BookmarkId("missing")))
        gate.live=true; store.close()
        assertNull(store.bookmarkRemovedPageForTrustedHost(gate,request))
    }
    @Test fun absentScopeIsAuthorizedEmptyWithoutCreatingScopeOrReceipt() {
        AuxiliaryDeliveryStore(root.resolve("empty").toString()).use { store ->
            val before=store.backup.snapshot(); val gate=Gate()
            assertEquals(BookmarkRemovedPage(emptyList(),false,0),store.bookmarkRemovedPageForTrustedHost(gate,request))
            assertEquals(BookmarkFolderSnapshot(emptyList(),0),store.bookmarkFoldersForTrustedHost(gate,"gen","token"))
            assertEquals(before,store.backup.snapshot())
        }
    }
}
