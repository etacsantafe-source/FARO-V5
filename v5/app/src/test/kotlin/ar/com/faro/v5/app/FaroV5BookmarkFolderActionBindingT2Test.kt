package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import ar.com.faro.v5.surface.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Real SQLite owners; sandbox/foreground facts simulated, not native Android gesture proof. */
class FaroV5BookmarkFolderActionBindingT2Test {
    @TempDir lateinit var root: Path
    private inner class Case(label: String, missingTarget: Boolean = false, seed: Boolean = true) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val pinned = requireNotNull(pointer.compareAndSet(null, StorageGenerationId("gen-" + label)))
        val workspace = ConversationWorkspaceStore(root.resolve(label + "-workspace.db").toString())
        val chat = ConversationId("chat-" + label)
        val message = ConversationMessageId("message-" + label)
        var foreground = true
        var hostReady = true
        var hostEpoch: String? = "host-a"
        var onForeground: () -> Boolean = { foreground }
        val token get() = workspace.bookmarkReadAdmission().authorityToken
        lateinit var sandbox: FaroV5AndroidSandboxPrincipal
        var authority: FaroV5LocalInstallReadAuthority
        val principal: String
        var mutations: FaroV5LocalInstallBookmarkMutationAuthority
        val auxiliary: AuxiliaryDeliveryStore
        init {
            runBlocking {
                workspace.createAndSelect(chat, 1, IdempotencyKey("create-" + label))
                val before = requireNotNull(workspace.readTimeline(chat))
                assertTrue(workspace.writeTimeline(ConversationTimelineWriteRequest(
                    chat, before.authorityToken, before.revision, IdempotencyKey("message-" + label),
                    ConversationTimelineIntent.AcceptUser(ConversationMessage(
                        message, chat, ConversationTurnId("turn-" + label),
                        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                        "private source body " + label, 1,
                    )),
                )) is ConversationTimelineWriteResult.Saved)
            }
            val path = root.resolve(label + "-auxiliary.db").toString()
            // Fixture-only seed writer, closed BEFORE the actual default-deny host owner opens.
            val thisSeed = seed
            val seed = object : BookmarkAuthorityGate {
                override fun isScopeActive(generationId: String, authorityToken: String) = true
                override fun isTargetAvailable(target: BookmarkTarget) = true
                override fun isMutationActive(generationId: String, authorityToken: String) = true
            }
            if (thisSeed) AuxiliaryDeliveryStore(path, bookmarkAuthorityGate = seed).use { setup ->
                runBlocking {
                    assertEquals(BookmarkMutationResult.Saved(1, false), setup.bookmarks.mutate(
                        BookmarkMutationRequest(pinned.generationId.value, token, 0, IdempotencyKey("folder-" + label),
                            BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("folder"), "Carpeta local")))))
                    assertEquals(BookmarkMutationResult.Saved(2, false), setup.bookmarks.mutate(
                        BookmarkMutationRequest(pinned.generationId.value, token, 1, IdempotencyKey("saved-" + label),
                            BookmarkMutation.Add(BookmarkMetadata(BookmarkId("saved"),
                                BookmarkTarget(chat, if (missingTarget) ConversationMessageId("missing") else message,
                                    pinned.generationId.value, token), BookmarkFolderId("folder"), true)))))
                }
            }
            auxiliary = AuxiliaryDeliveryStore(path)
            sandbox = FaroV5AndroidSandboxPrincipal(
                expectedApplicationId = "ar.com.faro.alpha.v2coredev",
                pinned = pinned, activePointer = pointer,
                expectedHostEpoch = "host-a", hostEpochOrNull = { hostEpoch },
                facts = AndroidSandboxFactsReadPort {
                    AndroidSandboxProcessFacts(10200, 10200, true, false, false, "ar.com.faro.alpha.v2coredev")
                },
            )
            authority = FaroV5LocalInstallReadAuthority(
                sandbox, pointer, { hostReady }, { onForeground() },
            )
            principal = requireNotNull(authority.technicalPrincipalOrNull())
            mutations = FaroV5LocalInstallBookmarkMutationAuthority(sandbox, pointer, { hostReady }, { onForeground() })
        }
        fun reopenAuthorities() {
            authority=FaroV5LocalInstallReadAuthority(sandbox,pointer,{ hostReady },{ onForeground() })
            mutations=FaroV5LocalInstallBookmarkMutationAuthority(sandbox,pointer,{ hostReady },{ onForeground() })
        }
        fun grant(domains: Set<FaroV5LocalReadDomain> = setOf(
            FaroV5LocalReadDomain.BOOKMARK_METADATA, FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
        )) = authority.grantAfterTrustedForegroundReadAction(domains)
        fun host() = FaroV5BookmarkFolderActionBinding(pinned, pointer, workspace, auxiliary,
            principal, "host-a", { hostEpoch }, { hostReady }, authority, mutations)
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun removeReopenLookupRestoreSameIdReceiptAndDoubleTap() = runBlocking {
        Case("restore").use { f -> f.grant()
            f.host().use { host ->
                val item=FaroV5BookmarkHostItem(BookmarkId("saved"),f.chat,f.message,BookmarkFolderId("folder"),true)
                val remove=requireNotNull(host.prepare(BookmarkMutation.LifecycleSet(item.id,BookmarkLifecycle.REMOVED),item))
                val before=f.auxiliary.backup.snapshot()
                assertTrue(host.isCurrent(remove)); assertEquals(before,f.auxiliary.backup.snapshot())
                assertEquals("Guardados actualizado",host.confirm(remove))
            }
            f.reopenAuthorities(); f.grant()
            f.host().use { reopened ->
                val restore=requireNotNull(reopened.prepare(BookmarkMutation.LifecycleSet(BookmarkId("saved"),BookmarkLifecycle.ACTIVE)))
                assertEquals("Restaurar referencia quitada",restore.label)
                assertEquals("Guardados actualizado",reopened.confirm(restore))
                val after=f.auxiliary.backup.snapshot()
                assertEquals("Revisá nuevamente la acción",reopened.confirm(restore))
                assertEquals(after,f.auxiliary.backup.snapshot())
                assertNull(reopened.prepare(BookmarkMutation.LifecycleSet(BookmarkId("saved"),BookmarkLifecycle.ACTIVE)))
            }
        }
    }
    @Test fun foldersConfirmRenameAssignRootAndNonemptyDeleteNoCascade() = runBlocking {
        Case("folders").use { f -> f.grant(); f.host().use { host ->
            suspend fun act(mutation:BookmarkMutation,item:FaroV5BookmarkHostItem?=null):String = host.confirm(requireNotNull(host.prepare(mutation,item)))
            val folder=BookmarkFolderId("folder")
            assertEquals("La carpeta no está vacía",act(BookmarkMutation.FolderDelete(folder)))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderRename(folder,"Nuevo nombre")))
            val item=FaroV5BookmarkHostItem(BookmarkId("saved"),f.chat,f.message,folder,true)
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderAssign(item.id,null),item))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderDelete(folder)))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderCreate(BookmarkFolder(folder,"Nueva"))))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderAssign(item.id,folder),item))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FavoriteSet(item.id,false),item))
        } }
    }
    @Test fun noReadGrantNoPreviewCancelAndForgedIdentityNeverWrite() = runBlocking {
        Case("grants").use { f -> f.host().use { host ->
            val mutation=BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("new"),"Nueva"))
            val before=f.auxiliary.backup.snapshot()
            assertNull(host.prepare(mutation)); f.grant(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA)); assertNull(host.prepare(mutation))
            f.grant(); val review=requireNotNull(host.prepare(mutation))
            assertFalse(host.isCurrent(FaroV5BookmarkFolderReview(review.label)))
            host.cancel(review); assertEquals("Revisá nuevamente la acción",host.confirm(review))
            assertEquals(before,f.auxiliary.backup.snapshot())
        } }
    }
    @Test fun staleScopeRequiresNewReviewAndDoesNotRetry() = runBlocking {
        Case("stale").use { f -> f.grant(); f.host().use { host ->
            val review=requireNotNull(host.prepare(BookmarkMutation.FolderRename(BookmarkFolderId("folder"),"New")))
            // Concurrent confirmed action through another binding uses the same owner CAS.
            val concurrent=f.host()
            val other=requireNotNull(concurrent.prepare(BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("other"),"Other"))))
            assertEquals("Guardados actualizado",concurrent.confirm(other))
            val before=f.auxiliary.backup.snapshot()
            assertEquals("Guardados cambió. Revisá nuevamente la acción",host.confirm(review))
            assertEquals(before,f.auxiliary.backup.snapshot())
        } }
    }
    @Test fun pauseRevokeAbaClosedAndMissingSourceInvalidateWithoutWrites() = runBlocking {
        for(mode in 0..4) Case("revoke-$mode").use { f -> f.grant(); f.host().use { host ->
            val review=requireNotNull(host.prepare(BookmarkMutation.FolderRename(BookmarkFolderId("folder"),"New")))
            when(mode) {
                0 -> f.authority.revokeAll()
                1 -> f.foreground=false
                2 -> { val other=StorageGenerationId("other"); f.pointer.compareAndSet(f.pinned.generationId,other); f.pointer.compareAndSet(other,f.pinned.generationId) }
                3 -> host.close()
                4 -> f.hostEpoch="new-epoch"
            }
            val before=f.auxiliary.backup.snapshot()
            assertFalse(host.isCurrent(review)); assertEquals("Revisá nuevamente la acción",host.confirm(review)); assertEquals(before,f.auxiliary.backup.snapshot())
        } }
        Case("missing",missingTarget=true).use { f -> f.grant(); f.host().use { host ->
            val item=FaroV5BookmarkHostItem(BookmarkId("saved"),f.chat,ConversationMessageId("missing"),null,false)
            assertNull(host.prepare(BookmarkMutation.FavoriteSet(item.id,true),item))
        } }
    }
}
