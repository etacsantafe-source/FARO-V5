package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkTrustedMutationHostTest {
    @TempDir lateinit var root: Path
    private fun <T> sync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
    private fun target(chat: String = "chat") = BookmarkTarget(ConversationId(chat), ConversationMessageId("msg"), "gen", "owner")
    private fun request(rev: Long = 0, key: String = "add", action: BookmarkMutation = BookmarkMutation.Add(
        BookmarkMetadata(BookmarkId("b"), target()))) = BookmarkMutationRequest("gen", "owner", rev, IdempotencyKey(key), action)
    private class Gate(val exact: BookmarkMutationRequest, val target: BookmarkTarget?) : RequestScopedBookmarkAuthorityGate {
        @Volatile var live = true
        @Volatile var source = true
        var checks = 0
        var revokeAt = Int.MAX_VALUE
        override fun isScopeActive(generationId: String, authorityToken: String) = live && generationId == "gen" && authorityToken == "owner"
        override fun isTargetAvailable(target: BookmarkTarget) = source && target == this.target
        override fun isMutationAuthorized(request: BookmarkMutationRequest, resolvedTarget: BookmarkTarget?): Boolean {
            if (++checks == revokeAt) live = false
            return live && request == exact && resolvedTarget == target
        }
    }
    private fun store() = AuxiliaryDeliveryStore(root.resolve("aux.db").toString())
    private fun save(store: AuxiliaryDeliveryStore, gate: Gate, request: BookmarkMutationRequest = gate.exact) =
        sync { store.bookmarkMutatorForTrustedHost(gate).mutate(request) }

    @Test fun exactGrantCommitsAndAuthorizedReplaySurvivesReopenButRevokedDoesNot() {
        val command = request()
        val gate = Gate(command, target())
        store().use { s ->
            assertEquals(BookmarkMutationResult.Unavailable, sync { s.bookmarks.mutate(command) })
            assertEquals(BookmarkMutationResult.Saved(1, false), save(s, gate))
            assertFalse(s.bookmarkMutatorForTrustedHost(gate) is BookmarkReadPort)
        }
        store().use { s ->
            assertEquals(BookmarkMutationResult.Saved(1, true), save(s, gate))
            val before = s.backup.snapshot()
            gate.live = false
            assertEquals(BookmarkMutationResult.Unavailable, save(s, gate))
            assertEquals(before, s.backup.snapshot())
        }
    }

    @Test fun addConsentCannotAuthorizeOtherActionsTargetsKeysOrFolderCreation() {
        store().use { s ->
            val gate = Gate(request(), target())
            val before = s.backup.snapshot()
            val attacks = listOf(
                request(action = BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b"), target("foreign")))),
                request(action = BookmarkMutation.FavoriteSet(BookmarkId("other"), true)),
                request(action = BookmarkMutation.LifecycleSet(BookmarkId("other"), BookmarkLifecycle.ACTIVE)),
                request(action = BookmarkMutation.FolderAssign(BookmarkId("other"), null)),
                request(action = BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"), "Folder"))),
                request(key = "another-key"), request(rev = 1))
            attacks.forEach { assertEquals(BookmarkMutationResult.Unavailable, save(s, gate, it)) }
            assertEquals(before, s.backup.snapshot())
        }
    }

    @Test fun persistedSourceIsResolvedForFavoriteRestoreAndAssignAndRevokedSourceCannotWrite() {
        store().use { s ->
            save(s, Gate(request(), target()))
            val cases = listOf(
                BookmarkMutation.FavoriteSet(BookmarkId("b"), true),
                BookmarkMutation.LifecycleSet(BookmarkId("b"), BookmarkLifecycle.ACTIVE),
                BookmarkMutation.FolderAssign(BookmarkId("b"), null))
            cases.forEachIndexed { i, action ->
                val command = request(1, "action-$i", action)
                val before = s.backup.snapshot()
                assertEquals(BookmarkMutationResult.Unavailable, save(s, Gate(command, target("foreign"))))
                val revoked = Gate(command, target()).also { it.source = false }
                assertEquals(BookmarkMutationResult.Unavailable, save(s, revoked))
                assertEquals(before, s.backup.snapshot())
            }
            val command = request(1, "favorite", cases[0])
            assertEquals(BookmarkMutationResult.Saved(2, false), save(s, Gate(command, target())))
        }
    }

    @Test fun revocationAtFinalFenceRollsBackRevisionItemAndReceiptTogether() {
        store().use { s ->
            val before = s.backup.snapshot()
            val gate = Gate(request(), target()).also { it.revokeAt = 4 }
            assertEquals(BookmarkMutationResult.Unavailable, save(s, gate))
            assertEquals(4, gate.checks)
            assertEquals(before, s.backup.snapshot())
        }
    }

    @Test fun exactFolderActionHasNoMessageGrantAndCasAndFingerprintRemainEnforced() {
        store().use { s ->
            val create = request(action = BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"), "Folder")))
            assertEquals(BookmarkMutationResult.Saved(1, false), save(s, Gate(create, null)))
            val altered = create.copy(mutation = BookmarkMutation.FolderRename(BookmarkFolderId("f"), "Other"))
            assertEquals(BookmarkMutationResult.IdentityConflict, save(s, Gate(altered, null)))
            val stale = altered.copy(idempotencyKey = IdempotencyKey("rename"))
            assertEquals(BookmarkMutationResult.Stale(1), save(s, Gate(stale, null)))
            assertEquals(BookmarkMutationResult.Saved(2, false), save(s, Gate(stale.copy(expectedRevision = 1), null)))
        }
    }

    @Test fun parallelInvocationsCannotInheritAnotherRequestsGrantOrBypassCas() {
        store().use { s ->
            val first = request()
            val second = request(key = "second", action = BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b2"), target("other"))))
            val firstGate = Gate(first, target())
            val secondGate = Gate(second, target("other"))
            val barrier = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val one = pool.submit<BookmarkMutationResult> { barrier.await(); save(s, firstGate) }
                val two = pool.submit<BookmarkMutationResult> { barrier.await(); save(s, secondGate) }
                barrier.countDown()
                val results = listOf(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS))
                assertEquals(1, results.count { it is BookmarkMutationResult.Saved })
                assertEquals(1, results.count { it is BookmarkMutationResult.Stale })
                val before = s.backup.snapshot()
                assertEquals(BookmarkMutationResult.Unavailable, save(s, firstGate, second))
                assertEquals(before, s.backup.snapshot())
            } finally { pool.shutdownNow() }
        }
    }
}
