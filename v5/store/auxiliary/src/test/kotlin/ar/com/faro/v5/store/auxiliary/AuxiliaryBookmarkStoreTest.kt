package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.BookmarkFolder
import ar.com.faro.v5.kernel.contracts.BookmarkFolderId
import ar.com.faro.v5.kernel.contracts.BookmarkId
import ar.com.faro.v5.kernel.contracts.BookmarkMetadata
import ar.com.faro.v5.kernel.contracts.BookmarkMutation
import ar.com.faro.v5.kernel.contracts.BookmarkMutationRequest
import ar.com.faro.v5.kernel.contracts.BookmarkMutationResult
import ar.com.faro.v5.kernel.contracts.BookmarkReadRequest
import ar.com.faro.v5.kernel.contracts.BookmarkReadResult
import ar.com.faro.v5.kernel.contracts.BookmarkRejection
import ar.com.faro.v5.kernel.contracts.BookmarkTarget
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuxiliaryBookmarkStoreTest {
    private class FakeAuthority : BookmarkAuthorityGate {
        var active = true
        var targetAvailable = true
        override fun isScopeActive(generationId: String, authorityToken: String): Boolean =
            active && generationId == "gen-a" && authorityToken == "owner-a"
        override fun isTargetAvailable(target: BookmarkTarget): Boolean =
            targetAvailable && target.generationId == "gen-a" &&
                target.authorityToken == "owner-a"
    }

    private fun item(id: String = "b1",chat: String = "chat-1", folder: String? = null) =
        BookmarkMetadata(
            id = BookmarkId(id),
            target = BookmarkTarget(ConversationId(chat),ConversationMessageId("msg-1"),"gen-a","owner-a"),
            folderId = folder?.let(::BookmarkFolderId),
        )

    private fun request(rev: Long,key: String,mutation: BookmarkMutation) =
        BookmarkMutationRequest("gen-a","owner-a",rev,IdempotencyKey(key),mutation)

    private fun <T> invoke(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }

    private fun save(store: AuxiliaryDeliveryStore,request: BookmarkMutationRequest) =
        invoke { store.bookmarks.mutate(request) }

    private fun page(store: AuxiliaryDeliveryStore,gen: String = "gen-a",token: String = "owner-a") =
        invoke { store.bookmarks.read(BookmarkReadRequest(gen,token)) }

    @Test
    fun defaultWithoutWorkspaceAuthorityIsUnavailable() = db { file ->
        AuxiliaryDeliveryStore(file).use { store ->
            assertEquals(BookmarkReadResult.Unavailable, page(store))
            assertEquals(
                BookmarkMutationResult.Unavailable,
                save(store,request(0,"k1",BookmarkMutation.Add(item())))
            )
        }
    }

    @Test
    fun authorizedMetadataSurvivesFileReopenWithoutMessageText() = db { file ->
        val auth = FakeAuthority()
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate = auth).use { store ->
            assertEquals(
                BookmarkMutationResult.Saved(1,false),
                save(store,request(0,"k1",BookmarkMutation.Add(item())))
            )
        }
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate = auth).use { store ->
            val read = assertInstanceOf(BookmarkReadResult.Available::class.java,page(store))
            assertEquals("b1",read.page.bookmarks.single().id.value)
            assertEquals("chat-1",read.page.bookmarks.single().target.conversationId.value)
            assertEquals("msg-1",read.page.bookmarks.single().target.messageId.value)
        }
    }

    @Test
    fun exactReceiptReplayIsIdempotentAcrossReopen() = db { file ->
        val auth = FakeAuthority()
        val cmd = request(0,"k1",BookmarkMutation.Add(item()))
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=auth).use { store ->
            assertEquals(BookmarkMutationResult.Saved(1,false),save(store,cmd))
        }
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=auth).use { store ->
            assertEquals(BookmarkMutationResult.Saved(1,true),save(store,cmd))
            assertEquals(1,assertInstanceOf(BookmarkReadResult.Available::class.java,page(store)).page.bookmarks.size)
        }
    }

    @Test
    fun sameReceiptKeyWithDifferentMutationIsIdentityConflict() = db { file ->
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=FakeAuthority()).use { store ->
            save(store,request(0,"reused",BookmarkMutation.Add(item())))
            assertEquals(
                BookmarkMutationResult.IdentityConflict,
                save(store,request(1,"reused",BookmarkMutation.FavoriteSet(BookmarkId("b1"),true)))
            )
        }
    }

    @Test
    fun staleCASDoesNotChangeSavedFavorite() = db { file ->
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=FakeAuthority()).use { store ->
            save(store,request(0,"add",BookmarkMutation.Add(item())))
            assertEquals(
                BookmarkMutationResult.Stale(1),
                save(store,request(0,"fav-stale",BookmarkMutation.FavoriteSet(BookmarkId("b1"),true)))
            )
            assertEquals(
                BookmarkMutationResult.Saved(2,false),
                save(store,request(1,"fav",BookmarkMutation.FavoriteSet(BookmarkId("b1"),true)))
            )
            val read=assertInstanceOf(BookmarkReadResult.Available::class.java,page(store))
            assertTrue(read.page.bookmarks.single().favorite)
        }
    }

    @Test
    fun differentGenerationOrRevocationNeverReadsForeignMetadata() = db { file ->
        val auth=FakeAuthority()
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=auth).use { store ->
            save(store,request(0,"add",BookmarkMutation.Add(item())))
            assertEquals(BookmarkReadResult.Unavailable,page(store,"gen-b","owner-b"))
            auth.active=false
            assertEquals(BookmarkReadResult.Unavailable,page(store))
            assertEquals(
                BookmarkMutationResult.Unavailable,
                save(store,request(1,"fav",BookmarkMutation.FavoriteSet(BookmarkId("b1"),true)))
            )
        }
    }

    @Test
    fun folderCannotBeDeletedWhileNonEmptyAndCanBeUnassigned() = db { file ->
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=FakeAuthority()).use { store ->
            assertEquals(BookmarkMutationResult.Saved(1,false),save(
                store,request(0,"folder",BookmarkMutation.FolderCreate(
                    BookmarkFolder(BookmarkFolderId("folder-1"),"Personal")
                ))
            ))
            save(store,request(1,"add",BookmarkMutation.Add(item(folder="folder-1"))))
            assertEquals(
                BookmarkMutationResult.Rejected(BookmarkRejection.FOLDER_NOT_EMPTY),
                save(store,request(2,"delete-early",BookmarkMutation.FolderDelete(BookmarkFolderId("folder-1"))))
            )
            assertEquals(BookmarkMutationResult.Saved(3,false),save(
                store,request(2,"unassign",BookmarkMutation.FolderAssign(BookmarkId("b1"),null))
            ))
            assertEquals(BookmarkMutationResult.Saved(4,false),save(
                store,request(3,"delete",BookmarkMutation.FolderDelete(BookmarkFolderId("folder-1")))
            ))
        }
    }

    @Test
    fun sqliteErrorOnReceiptRollsBackMetadataAndScopeRevision() = db { file ->
        AuxiliaryDeliveryStore(file,bookmarkAuthorityGate=FakeAuthority()).use { store ->
            BundledSQLiteDriver().open(file).use { sql ->
                sql.execSQL("""
                    CREATE TRIGGER bookmark_fault BEFORE INSERT ON bookmark_receipt
                    BEGIN SELECT RAISE(ABORT,'injected receipt failure'); END
                """.trimIndent())
            }
            assertThrows(Exception::class.java) {
                save(store,request(0,"failed",BookmarkMutation.Add(item())))
            }
            BundledSQLiteDriver().open(file).use { sql ->
                sql.prepare("SELECT COUNT(*) FROM bookmark_item").use { st ->
                    assertTrue(st.step())
                    assertEquals(0,st.getLong(0).toInt())
                }
                sql.execSQL("DROP TRIGGER bookmark_fault")
            }
            assertEquals(
                BookmarkMutationResult.Saved(1,false),
                save(store,request(0,"retry",BookmarkMutation.Add(item())))
            )
        }
    }

    private fun db(block:(String)->Unit) {
        val dir=Files.createTempDirectory("faro-a3-bookmark-cas")
        try { block(dir.resolve("auxiliary.db").toString()) }
        finally { dir.toFile().deleteRecursively() }
    }
}
