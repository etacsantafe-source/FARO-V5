package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkMutationAuthorityTest {
    @TempDir lateinit var root: Path
    private open class ReadOnly : BookmarkAuthorityGate {
        override fun isScopeActive(generationId: String, authorityToken: String) = true
        override fun isTargetAvailable(target: BookmarkTarget) = true
    }
    private class Write : ReadOnly() {
        override fun isMutationActive(generationId: String, authorityToken: String) = true
    }
    private fun store(gate: BookmarkAuthorityGate) = AuxiliaryDeliveryStore(
        root.resolve("aux.db").toString(), bookmarkAuthorityGate = gate)
    private fun request() = BookmarkMutationRequest("gen", "owner", 0, IdempotencyKey("add"),
        BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b"), BookmarkTarget(
            ConversationId("chat"), ConversationMessageId("msg"), "gen", "owner"))))
    private fun <T> sync(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome).getOrThrow()
    }

    @Test fun readGrantDoesNotPermitMutationOrCreateScopeReceipt() {
        store(ReadOnly()).use {
            val before = it.backup.snapshot()
            assertInstanceOf(BookmarkReadResult.Available::class.java,
                sync { it.bookmarks.read(BookmarkReadRequest("gen", "owner")) })
            assertEquals(BookmarkMutationResult.Unavailable, sync { it.bookmarks.mutate(request()) })
            assertEquals(before, it.backup.snapshot())
        }
    }

    @Test fun readGrantCannotReplayReceiptAndRevokedReplayAlsoFailsClosed() {
        store(Write()).use { assertEquals(BookmarkMutationResult.Saved(1, false), sync { it.bookmarks.mutate(request()) }) }
        store(ReadOnly()).use {
            assertEquals(BookmarkMutationResult.Unavailable, sync { it.bookmarks.mutate(request()) })
            assertEquals(1, assertInstanceOf(BookmarkReadResult.Available::class.java,
                sync { it.bookmarks.read(BookmarkReadRequest("gen", "owner")) }).page.bookmarks.size)
        }
        val expiring = object : ReadOnly() {
            var checks = 0
            override fun isMutationActive(generationId: String, authorityToken: String) = ++checks == 1
        }
        store(expiring).use {
            assertEquals(BookmarkMutationResult.Unavailable, sync { it.bookmarks.mutate(request()) })
        }
    }

    @Test fun explicitMutationGrantRetainsNormalTransactionalOutcome() {
        store(Write()).use {
            assertEquals(BookmarkMutationResult.Saved(1, false), sync { it.bookmarks.mutate(request()) })
            assertEquals(1L, it.backup.snapshot().bookmarks!!.scope!!.revision)
        }
    }

    @Test fun mutationRevokedBeforeCommitRollsBackItemScopeAndReceipt() {
        val expiring = object : ReadOnly() {
            var checks = 0
            override fun isMutationActive(generationId: String, authorityToken: String) = ++checks <= 2
        }
        store(expiring).use {
            val before = it.backup.snapshot()
            assertEquals(BookmarkMutationResult.Unavailable, sync { it.bookmarks.mutate(request()) })
            assertEquals(before, it.backup.snapshot())
        }
    }

    @Test fun mutationAuthorityFailureIsUnavailableWithoutAnyWrite() {
        val failed = object : ReadOnly() {
            override fun isMutationActive(generationId: String, authorityToken: String): Boolean = error("owner unavailable")
        }
        store(failed).use {
            val before = it.backup.snapshot()
            assertEquals(BookmarkMutationResult.Unavailable, sync { it.bookmarks.mutate(request()) })
            assertEquals(before, it.backup.snapshot())
        }
    }
}
