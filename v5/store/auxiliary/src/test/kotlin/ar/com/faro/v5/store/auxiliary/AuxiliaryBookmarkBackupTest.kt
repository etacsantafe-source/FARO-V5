package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryBookmarkBackupTest {
    @TempDir lateinit var root: Path
    private val authority = object : BookmarkAuthorityGate {
        override fun isScopeActive(generationId: String, authorityToken: String) = true
        override fun isTargetAvailable(target: BookmarkTarget) = true
    }
    private fun store(name: String, gate: BookmarkAuthorityGate? = authority) =
        AuxiliaryDeliveryStore(root.resolve(name).toString(), bookmarkAuthorityGate = gate)
    private fun <T> sync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
    private fun request(revision: Long, key: String, mutation: BookmarkMutation) =
        BookmarkMutationRequest("gen-a", "private-token", revision, IdempotencyKey(key), mutation)
    private fun add() = request(1, "add", BookmarkMutation.Add(BookmarkMetadata(
        BookmarkId("b1"), BookmarkTarget(ConversationId("chat"), ConversationMessageId("msg"),
            "gen-a", "private-token"), BookmarkFolderId("f1"), favorite = true)))
    private fun seed(s: AuxiliaryDeliveryStore): AuxiliaryBackupSnapshot {
        sync { s.bookmarks.mutate(request(0, "folder", BookmarkMutation.FolderCreate(
            BookmarkFolder(BookmarkFolderId("f1"), "Ideas")))) }
        sync { s.bookmarks.mutate(add()) }
        sync { s.bookmarks.mutate(request(2, "remove", BookmarkMutation.LifecycleSet(
            BookmarkId("b1"), BookmarkLifecycle.REMOVED))) }
        return s.backup.snapshot()
    }

    @Test fun allFourTablesSurviveRestoreReopenAndExactReceiptReplay() {
        val snapshot = store("source").use(::seed)
        assertEquals(6L, snapshot.bookmarks!!.recordCount)
        assertFalse(snapshot.toString().contains("private-token"))
        store("target").use { assertEquals(snapshot, it.backup.restoreOrVerify(snapshot)) }
        store("target").use {
            assertEquals(snapshot, it.backup.snapshot())
            assertEquals(snapshot, it.backup.restoreOrVerify(snapshot))
            assertEquals(BookmarkMutationResult.Saved(2, true), sync { it.bookmarks.mutate(add()) })
            assertEquals(BookmarkLifecycle.REMOVED, it.backup.snapshot().bookmarks!!.items.single().lifecycle)
        }
    }

    @Test fun restoredIdentityDoesNotGrantDefaultOrChangedGenerationAccess() {
        val snapshot = store("source").use(::seed)
        store("target", null).use {
            it.backup.restoreOrVerify(snapshot)
            assertEquals(BookmarkReadResult.Unavailable, sync { it.bookmarks.read(BookmarkReadRequest("gen-a", "private-token")) })
        }
        store("target").use {
            assertEquals(BookmarkReadResult.Stale, sync { it.bookmarks.read(BookmarkReadRequest("gen-b", "private-token")) })
            assertEquals(BookmarkReadResult.Stale, sync { it.bookmarks.read(BookmarkReadRequest("gen-a", "new-token")) })
        }
    }

    @Test fun occupiedBookmarkDestinationIsNeverTreatedAsFresh() {
        val snapshot = store("source").use(::seed)
        store("target").use {
            sync { it.bookmarks.mutate(request(0, "other", BookmarkMutation.FolderCreate(
                BookmarkFolder(BookmarkFolderId("other"), "Existing")))) }
            val before = it.backup.snapshot()
            assertThrows(IllegalStateException::class.java) { it.backup.restoreOrVerify(snapshot) }
            assertEquals(before, it.backup.snapshot())
        }
    }

    @Test fun failedReceiptInsertRollsBackScopeFoldersAndItems() {
        val snapshot = store("source").use(::seed)
        store("target").use { target ->
            BundledSQLiteDriver().open(root.resolve("target").toString()).use {
                it.execSQL("CREATE TRIGGER fail_receipt BEFORE INSERT ON bookmark_receipt BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
            }
            val before = target.backup.snapshot()
            assertThrows(Exception::class.java) { target.backup.restoreOrVerify(snapshot) }
            assertEquals(before, target.backup.snapshot())
            assertTrue(target.maintenance.inspect().foreignKeysHealthy)
        }
    }

    @Test fun malformedIdentityDuplicatesAndDanglingFolderFailBeforeWriting() {
        val b = store("source").use(::seed).bookmarks!!
        assertThrows(IllegalArgumentException::class.java) { b.copy(items = b.items + b.items) }
        assertThrows(IllegalArgumentException::class.java) { b.copy(folders = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { b.copy(scope = null) }
        assertThrows(IllegalArgumentException::class.java) { b.copy(scope = b.scope!!.copy(generationId = "other")) }
        assertThrows(IllegalArgumentException::class.java) { b.copy(receipts = List(100001) { b.receipts.first() }) }
    }

    @Test fun unicodeIdsUseCanonicalKotlinOrderAcrossSQLiteRestore() {
        val ids = listOf("\uE000", "\uD800\uDC00")
        val snapshot = store("unicode").use { source ->
            ids.forEachIndexed { index, id ->
                sync { source.bookmarks.mutate(request(index.toLong(), id,
                    BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId(id), "Folder")))) }
            }
            source.backup.snapshot()
        }
        assertEquals(ids.sorted(), snapshot.bookmarks!!.folders.map { it.id.value })
        assertEquals(ids.sorted(), snapshot.bookmarks!!.receipts.map { it.key })
        store("unicode-target").use { assertEquals(snapshot, it.backup.restoreOrVerify(snapshot)) }
    }

    @Test fun absentLegacySectionDoesNotBecomeVerifiedEmptyOrOverwrite() {
        val snapshot = store("source").use(::seed).copy(bookmarks = null)
        store("target").use {
            val before = it.backup.snapshot()
            assertThrows(IllegalStateException::class.java) { it.backup.restoreOrVerify(snapshot) }
            assertEquals(before, it.backup.snapshot())
        }
    }
}
