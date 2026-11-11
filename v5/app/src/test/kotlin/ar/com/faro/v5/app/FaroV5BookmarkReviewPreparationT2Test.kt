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
class FaroV5BookmarkReviewPreparationT2Test {
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
        val authority: FaroV5LocalInstallReadAuthority
        val principal: String
        val mutations: FaroV5LocalInstallBookmarkMutationAuthority
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
            val sandbox = FaroV5AndroidSandboxPrincipal(
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
        fun grant(domains: Set<FaroV5LocalReadDomain> = setOf(
            FaroV5LocalReadDomain.BOOKMARK_METADATA, FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
        )) = authority.grantAfterTrustedForegroundReadAction(domains)
        fun host() = FaroV5BookmarkReviewPreparationBinding(pinned, pointer, workspace, auxiliary,
            principal, "host-a", { hostEpoch }, { hostReady }, authority, mutations)
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun previewNeedsBothGrantsAndNeverWrites() = runBlocking {
        Case("preview", seed = false).use { f -> f.host().use { host ->
            assertNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            f.grant(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA))
            assertNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            f.grant()
            val before = f.auxiliary.backup.snapshot()
            val review = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            assertEquals("Guardar mensaje", review.label)
            assertTrue(host.isCurrent(review))
            assertEquals(before, f.auxiliary.backup.snapshot())
            host.cancel(review)
            assertFalse(host.isCurrent(review))
            assertEquals("Revisá nuevamente la acción", host.confirm(review))
            assertEquals(before, f.auxiliary.backup.snapshot())
        } }
    }
    @Test fun freshScopeAddConfirmAndDoubleTapAreOneShot() = runBlocking {
        Case("new", seed = false).use { f -> f.grant(); f.host().use { host ->
            val review = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            assertEquals("Guardados actualizado", host.confirm(review))
            val after = f.auxiliary.backup.snapshot()
            assertEquals("Revisá nuevamente la acción", host.confirm(review))
            assertEquals(after, f.auxiliary.backup.snapshot())
        } }
    }
    @Test fun globalRevisionTwoNotItemRevisionOneAndLifecycleAreCanonical() = runBlocking {
        Case("existing").use { f -> f.grant(); f.host().use { host ->
            val review = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            assertEquals("Quitar favorito", review.label)
            assertEquals("Guardados actualizado", host.confirm(review))
            val remove = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.SAVE_OR_REMOVE))
            assertEquals("Quitar de Guardados", remove.label)
            assertEquals("Guardados actualizado", host.confirm(remove))
        } }
    }
    @Test fun staleScopeNeverRetriesAndRequiresNewReview() = runBlocking {
        Case("stale").use { f -> f.grant(); f.host().use { host ->
            val review = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            val token = f.token
            val target = BookmarkTarget(f.chat, f.message, f.pinned.generationId.value, token)
            val request = BookmarkMutationRequest(target.generationId, token, 2, IdempotencyKey("concurrent"),
                BookmarkMutation.FavoriteSet(BookmarkId("saved"), false))
            f.mutations.grantAfterTrustedForegroundMutationAction(request, target)
            val gate = ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkMutationAuthorityGate(f.workspace, f.pinned, f.pointer,
                request, requireNotNull(f.mutations.epochOrNull(f.principal, request, target)),
                { r,t -> f.mutations.epochOrNull(f.principal,r,t) }, { true })
            assertTrue(f.auxiliary.bookmarkMutatorForTrustedHost(gate).mutate(request) is BookmarkMutationResult.Saved)
            val before = f.auxiliary.backup.snapshot()
            assertEquals("Guardados cambió. Revisá nuevamente la acción", host.confirm(review))
            assertEquals(before, f.auxiliary.backup.snapshot())
            assertEquals("Revisá nuevamente la acción", host.confirm(review))
        } }
    }
    @Test fun revokeBackgroundAndFullPointerAbaInvalidateReview() = runBlocking {
        for (mode in 0..3) Case("revoked-$mode").use { f -> f.grant(); f.host().use { host ->
            val review = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            when(mode) {
                0 -> f.authority.revokeAll()
                1 -> f.foreground = false
                2 -> { val other = StorageGenerationId("other"); f.pointer.compareAndSet(f.pinned.generationId,other); f.pointer.compareAndSet(other,f.pinned.generationId) }
                3 -> host.close()
            }
            val before = f.auxiliary.backup.snapshot()
            assertFalse(host.isCurrent(review))
            assertEquals("Revisá nuevamente la acción", host.confirm(review))
            assertEquals(before, f.auxiliary.backup.snapshot())
        } }
    }
    @Test fun exactSourceAndReviewIdentityRejectOldOrForeignRequests() = runBlocking {
        Case("identity").use { f -> f.grant(); f.host().use { host ->
            assertNull(host.prepare(ConversationId("foreign"), f.message, FaroV5BookmarkChatAction.FAVORITE))
            assertNull(host.prepare(f.chat, ConversationMessageId("missing"), FaroV5BookmarkChatAction.FAVORITE))
            val old = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            val current = requireNotNull(host.prepare(f.chat, f.message, FaroV5BookmarkChatAction.FAVORITE))
            assertFalse(host.isCurrent(old)); assertTrue(host.isCurrent(current))
            val forged = FaroV5BookmarkChatReview(current.conversationId, current.messageId, current.label)
            assertFalse(host.isCurrent(forged))
        } }
    }
}
