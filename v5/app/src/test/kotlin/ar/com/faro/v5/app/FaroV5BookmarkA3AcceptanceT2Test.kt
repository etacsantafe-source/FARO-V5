package ar.com.faro.v5.app

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.*
import ar.com.faro.v5.surface.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Integrated real SQLite hosts; simulated sandbox facts are NOT native gesture/physical proof. */
class FaroV5BookmarkA3AcceptanceT2Test {
    @TempDir lateinit var root: Path
    private inner class Case(val name: String) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve("$name-pointer"))
        val pinned = requireNotNull(pointer.compareAndSet(null, StorageGenerationId("gen-$name")))
        val workspaceFile = root.resolve("$name-workspace.db").toString()
        val auxiliaryFile = root.resolve("$name-auxiliary.db").toString()
        val workspace = ConversationWorkspaceStore(workspaceFile)
        val chat = ConversationId("chat-$name")
        val message = ConversationMessageId("message-$name")
        var foreground = true
        var epoch: String? = "host"
        var afterWrite: () -> Unit = {}
        fun auxiliaryOwner() = AuxiliaryDeliveryStore(auxiliaryFile, materialWriteGate = object : MaterialWriteGate {
            override fun <T> withMaterialWrite(block: () -> T): T { val v=block(); afterWrite(); return v }
            override fun <T> withQuiescedMaterialWrites(block: () -> T): T = block()
        })
        var auxiliary = auxiliaryOwner()
        private fun sandbox() = FaroV5AndroidSandboxPrincipal("ar.com.faro.alpha.v2coredev",pinned,pointer,
            "host",{ epoch },AndroidSandboxFactsReadPort {
                AndroidSandboxProcessFacts(10200,10200,true,false,false,"ar.com.faro.alpha.v2coredev")
            })
        private val process = sandbox()
        val reads = FaroV5LocalInstallReadAuthority(process,pointer,{ foreground },{ foreground })
        val mutations = FaroV5LocalInstallBookmarkMutationAuthority(process,pointer,{ foreground },{ foreground })
        val principal = requireNotNull(reads.technicalPrincipalOrNull())
        val bindings = mutableListOf<AutoCloseable>()
        val token get() = workspace.bookmarkReadAdmission().authorityToken
        init { runBlocking {
            assertTrue(workspace.createAndSelect(chat,1,IdempotencyKey("create-$name")) is ConversationSelectionResult.Saved)
            append(message)
        } }
        suspend fun append(id: ConversationMessageId) {
            val before=requireNotNull(workspace.readTimeline(chat))
            assertTrue(workspace.writeTimeline(ConversationTimelineWriteRequest(chat,before.authorityToken,before.revision,
                IdempotencyKey("write-${id.value}"),ConversationTimelineIntent.AcceptUser(ConversationMessage(id,chat,
                    ConversationTurnId("turn-${id.value}"),ConversationMessageRole.USER,ConversationMessageState.ACCEPTED,
                    "private source ${id.value}",1)))) is ConversationTimelineWriteResult.Saved)
        }
        fun grant() = reads.grantAfterTrustedForegroundReadAction(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA,FaroV5LocalReadDomain.WORKSPACE_MESSAGE))
        fun review() = FaroV5BookmarkReviewPreparationBinding(pinned,pointer,workspace,auxiliary,principal,"host",{ epoch },
            { foreground },reads,mutations).also { bindings+=it }
        fun folders() = FaroV5BookmarkFolderActionBinding(pinned,pointer,workspace,auxiliary,principal,"host",{ epoch },
            { foreground },reads,mutations).also { bindings+=it }
        fun reader() = FaroV5BookmarkReadHostBinding(pinned,pointer,workspace,auxiliary,principal,"host",{ epoch },
            { foreground },reads).also { bindings+=it }
        fun target(id: ConversationMessageId = message) = BookmarkTarget(chat,id,pinned.generationId.value,token)
        fun request(revision: Long, key: String, mutation: BookmarkMutation) =
            BookmarkMutationRequest(pinned.generationId.value,token,revision,IdempotencyKey(key),mutation)
        fun mutator() = FaroV5BookmarkMutationHostBinding(pinned,pointer,workspace,auxiliary,principal,"host",{ epoch },
            { foreground },mutations).also { bindings+=it }
        suspend fun save(): FaroV5BookmarkHostItem {
            grant(); val review=review(); val consent=requireNotNull(review.prepare(chat,message,FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            assertEquals("Guardados actualizado",review.confirm(consent))
            return requireNotNull(reader().readAuthorized()).items.single()
        }
        fun reopen() { auxiliary.close(); auxiliary=auxiliaryOwner() }
        fun sqlWorkspace(sql: String) { BundledSQLiteDriver().open(workspaceFile).use { it.execSQL(sql) } }
        override fun close() {
            bindings.forEach { runCatching { it.close() } }; reads.close(); mutations.close()
            runCatching { auxiliary.close() }; workspace.close()
        }
    }

    @Test fun a3_01And08ReadGrantDoesNotWriteAndPreviewCancelIdentityAreOneShot() = runBlocking {
        Case("consent").use { f ->
            val review=f.review(); val before=f.auxiliary.backup.snapshot()
            assertNull(review.prepare(f.chat,f.message,FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            assertNull(f.reader().readAuthorized()); f.grant()
            val request=f.request(0,"without-consent",BookmarkMutation.Add(BookmarkMetadata(BookmarkId("raw"),f.target())))
            assertEquals(FaroV5BookmarkMutationOutcome.Unavailable,f.mutator().mutateAuthorized(request,f.target()))
            val prepared=requireNotNull(review.prepare(f.chat,f.message,FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            assertFalse(review.isCurrent(FaroV5BookmarkChatReview(f.chat,f.message,prepared.label)))
            assertEquals(before,f.auxiliary.backup.snapshot()); review.cancel(prepared)
            assertEquals("Revisá nuevamente la acción",review.confirm(prepared))
            assertEquals(before,f.auxiliary.backup.snapshot())
            assertTrue(requireNotNull(f.reader().readAuthorized()).items.isEmpty())
            assertNotEquals(FaroV5V2CoreDevBookmarksPresentation.render(null),FaroV5V2CoreDevBookmarksPresentation.render(FaroV5BookmarkHostPage(emptyList(),emptyList(),false)))
        }
    }
    @Test fun a3_02And09ChatReviewReceiptThenFreshOriginalConversationAndExactMessage() = runBlocking {
        Case("roundtrip").use { f ->
            val item=f.save(); val saved=f.auxiliary.backup.snapshot()
            assertEquals(1,saved.bookmarks!!.receipts.size)
            val reader=f.reader(); val page=requireNotNull(reader.readAuthorized())
            f.workspace.createAndSelect(ConversationId("other"),2,IdempotencyKey("other"))
            val source=FaroV5BookmarkSourceNavigationBinding(f.pinned,f.pointer,f.workspace,f.auxiliary,reader,f.reads,
                f.principal,{ f.foreground },f.workspace,f.workspace,ConversationTimelineReadPort { f.workspace.readTimeline(it) })
            source.use {
                val target=requireNotNull(it.open(page,page.items.single()))
                assertEquals(item.conversationId,target.conversationId); assertEquals(item.messageId,target.messageId)
                assertTrue(it.isCurrent(target)); assertNull(it.open(page,page.items.single()))
            }
            assertEquals(saved,f.auxiliary.backup.snapshot())
            assertEquals(f.chat,f.workspace.readSnapshot().activeConversationId)
        }
    }
    @Test fun a3_03ExactReplayConflictAndStaleNeverCreateSecondReceipt() = runBlocking {
        Case("cas").use { f ->
            val target=f.target(); val request=f.request(0,"same-key",BookmarkMutation.Add(BookmarkMetadata(BookmarkId("saved"),target)))
            val host=f.mutator(); assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,target))
            val first=assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java,host.mutateAuthorized(request,target))
            assertFalse(first.replayed); val before=f.auxiliary.backup.snapshot()
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,target))
            assertTrue(assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java,host.mutateAuthorized(request,target)).replayed)
            val conflict=request.copy(mutation=BookmarkMutation.Add(BookmarkMetadata(BookmarkId("other"),target)))
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(conflict,target))
            assertEquals(FaroV5BookmarkMutationOutcome.IdentityConflict,host.mutateAuthorized(conflict,target))
            val stale=f.request(0,"stale",BookmarkMutation.FavoriteSet(BookmarkId("saved"),true))
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(stale,target))
            assertTrue(host.mutateAuthorized(stale,target) is FaroV5BookmarkMutationOutcome.Stale)
            assertEquals(before,f.auxiliary.backup.snapshot())
        }
    }
    @Test fun a3_04And05FoldersLifecycleAndExplicitRecoverySurviveReopenWithoutDeletingSource() = runBlocking {
        Case("lifecycle").use { f ->
            val item=f.save(); val host=f.folders(); val folder=BookmarkFolderId("folder")
            suspend fun act(m: BookmarkMutation, row: FaroV5BookmarkHostItem?=null) = host.confirm(requireNotNull(host.prepare(m,row)))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderCreate(BookmarkFolder(folder,"Ideas"))))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderAssign(item.id,folder),item))
            assertEquals("La carpeta no está vacía",act(BookmarkMutation.FolderDelete(folder)))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderRename(folder,"Nuevas ideas")))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FavoriteSet(item.id,true),item))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderAssign(item.id,null),item))
            assertEquals("Guardados actualizado",act(BookmarkMutation.FolderDelete(folder)))
            assertEquals("Guardados actualizado",act(BookmarkMutation.LifecycleSet(item.id,BookmarkLifecycle.REMOVED),item))
            assertTrue(requireNotNull(f.reader().readAuthorized()).items.isEmpty())
            val retained=f.auxiliary.backup.snapshot(); f.reopen(); f.reads.revokeAll()
            assertNull(f.reader().readRemovedAfterExplicitRequest())
            assertEquals(retained,f.auxiliary.backup.snapshot()); f.grant()
            assertEquals(item.id,requireNotNull(f.reader().readRemovedAfterExplicitRequest()).items.single().id)
            val restore=f.folders(); val consent=requireNotNull(restore.prepare(BookmarkMutation.LifecycleSet(item.id,BookmarkLifecycle.ACTIVE)))
            assertEquals("Guardados actualizado",restore.confirm(consent))
            assertEquals("Revisá nuevamente la acción",restore.confirm(consent))
            assertEquals(item.id,requireNotNull(f.reader().readAuthorized()).items.single().id)
            assertEquals(f.message,requireNotNull(f.workspace.readTimeline(f.chat)).messages.single().id)
        }
    }
    @Test fun a3_06RealHostPaginationAndMetadataProjectionNeverContainSourceBody() = runBlocking {
        Case("page").use { f ->
            f.grant()
            repeat(55) { n ->
                val id=if(n==0) f.message else ConversationMessageId("message-$n")
                if(n!=0) f.append(id)
                val h=f.review(); val r=requireNotNull(h.prepare(f.chat,id,FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
                assertEquals("Guardados actualizado",h.confirm(r))
            }
            val reader=f.reader(); val one=requireNotNull(reader.readAuthorized())
            assertEquals(50,one.items.size); assertTrue(one.hasMore)
            val two=requireNotNull(reader.readAuthorized(one.items.last().id))
            assertEquals(5,two.items.size); assertFalse(two.hasMore); assertFalse(reader.isPageCurrent(one))
            assertEquals(55,(one.items+two.items).map { it.id }.distinct().size)
            assertFalse(FaroV5V2CoreDevBookmarksPresentation.render(one).contains("private source"))
            assertFalse(f.auxiliary.backup.snapshot().toString().contains("private source"))
        }
    }
    @Test fun a3_06Quotas32And1024RejectNextRequestThroughDefaultDenyHost() = runBlocking {
        Case("quota").use { f ->
            f.auxiliary.close()
            // Fixture-only seed, not evidence of ACL: close before default-deny production owner opens.
            val seed=object: BookmarkAuthorityGate {
                override fun isScopeActive(generationId:String,authorityToken:String)=true
                override fun isTargetAvailable(target:BookmarkTarget)=true
                override fun isMutationActive(generationId:String,authorityToken:String)=true
            }
            AuxiliaryDeliveryStore(f.auxiliaryFile,bookmarkAuthorityGate=seed).use { owner ->
                var revision=0L
                repeat(32) { n ->
                    val mutation=BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f-$n"),"Folder $n"))
                    val request=f.request(revision++,"folder-$n",mutation)
                    assertTrue(owner.bookmarks.mutate(request) is BookmarkMutationResult.Saved)
                }
                repeat(1024) { n ->
                    val metadata=BookmarkMetadata(BookmarkId("b-$n"),f.target(ConversationMessageId("seed-$n")))
                    val request=f.request(revision++,"seed-$n",BookmarkMutation.Add(metadata))
                    assertTrue(owner.bookmarks.mutate(request) is BookmarkMutationResult.Saved)
                }
            }
            f.auxiliary=f.auxiliaryOwner(); val before=f.auxiliary.backup.snapshot()
            val host=f.mutator(); val target=f.target(); val revision=requireNotNull(before.bookmarks?.scope).revision
            for(mutation in listOf(BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("overflow"),"Overflow")),
                BookmarkMutation.Add(BookmarkMetadata(BookmarkId("overflow"),target)))) {
                val request=f.request(revision,"overflow-${mutation::class.simpleName}",mutation)
                val consent=if(mutation is BookmarkMutation.Add) target else null
                assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,consent))
                val result=assertInstanceOf(FaroV5BookmarkMutationOutcome.Rejected::class.java,host.mutateAuthorized(request,consent))
                assertEquals(BookmarkRejection.QUOTA_EXCEEDED,result.reason)
            }
            assertEquals(before,f.auxiliary.backup.snapshot())
        }
    }
    @Test fun a3_07RevocationArchiveDeletionAndFullPointerAbaNeverPublish() = runBlocking {
        for(mode in 0..3) Case("revoke-$mode").use { f ->
            f.save(); val reader=f.reader(); val page=requireNotNull(reader.readAuthorized())
            when(mode) {
                0 -> f.reads.revokeAll()
                1 -> f.sqlWorkspace("UPDATE workspace_conversation SET archived=1")
                2 -> f.sqlWorkspace("DELETE FROM workspace_message")
                3 -> { val other=StorageGenerationId("other"); f.pointer.compareAndSet(f.pinned.generationId,other); f.pointer.compareAndSet(other,f.pinned.generationId) }
            }
            FaroV5BookmarkSourceNavigationBinding(f.pinned,f.pointer,f.workspace,f.auxiliary,reader,f.reads,f.principal,
                { f.foreground },f.workspace,f.workspace,ConversationTimelineReadPort { f.workspace.readTimeline(it) }).use {
                assertNull(it.open(page,page.items.single()))
            }
            assertNull(reader.readAuthorized())
        }
    }
    @Test fun a3_10RollbackAndPostCommitUncertaintyReopenWithoutAutomaticRetry() = runBlocking {
        Case("crash").use { f ->
            val target=f.target(); val request=f.request(0,"crash-key",BookmarkMutation.Add(BookmarkMetadata(BookmarkId("saved"),target)))
            val before=f.auxiliary.backup.snapshot()
            BundledSQLiteDriver().open(f.auxiliaryFile).use { it.execSQL("CREATE TRIGGER fail_receipt BEFORE INSERT ON bookmark_receipt BEGIN SELECT RAISE(ABORT,'fixture crash'); END") }
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,target))
            assertFalse(f.mutator().mutateAuthorized(request,target) is FaroV5BookmarkMutationOutcome.Saved)
            assertEquals(before,f.auxiliary.backup.snapshot()); f.reopen()
            assertEquals(before,f.auxiliary.backup.snapshot())
            BundledSQLiteDriver().open(f.auxiliaryFile).use { it.execSQL("DROP TRIGGER fail_receipt") }
            f.afterWrite={ f.mutations.revokeAll() }
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,target))
            assertEquals(FaroV5BookmarkMutationOutcome.UnknownReconcile,f.mutator().mutateAuthorized(request,target))
            val committed=f.auxiliary.backup.snapshot(); assertEquals(1,committed.bookmarks!!.receipts.size)
            f.afterWrite={}; f.reopen(); assertEquals(committed,f.auxiliary.backup.snapshot())
            assertEquals(FaroV5BookmarkMutationOutcome.Unavailable,f.mutator().mutateAuthorized(request,target))
            assertTrue(f.mutations.grantAfterTrustedForegroundMutationAction(request,target))
            assertTrue(assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java,f.mutator().mutateAuthorized(request,target)).replayed)
            assertEquals(committed,f.auxiliary.backup.snapshot())
        }
    }
    @Test fun a3_11RestoredMetadataNeverGrantsReadAcrossReopen() = runBlocking {
        Case("restore").use { f ->
            f.save(); val retained=f.auxiliary.backup.snapshot()
            val destination=root.resolve("restored.db").toString()
            AuxiliaryDeliveryStore(destination).use { owner ->
                assertEquals(retained,owner.backup.restoreOrVerify(retained))
                assertEquals(BookmarkReadResult.Unavailable,owner.bookmarks.read(BookmarkReadRequest(f.pinned.generationId.value,f.token)))
            }
            AuxiliaryDeliveryStore(destination).use { owner -> assertEquals(retained,owner.backup.snapshot()) }
        }
    }
}
