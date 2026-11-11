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
class FaroV5BookmarkRemovedRestoreHostT2Test {
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
        fun remove() = runBlocking {
            val target=BookmarkTarget(chat,message,pinned.generationId.value,token)
            val request=BookmarkMutationRequest(target.generationId,token,2,IdempotencyKey("remove"),BookmarkMutation.LifecycleSet(BookmarkId("saved"),BookmarkLifecycle.REMOVED))
            mutations.grantAfterTrustedForegroundMutationAction(request,target)
            val gate=ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkMutationAuthorityGate(workspace,pinned,pointer,request,
                requireNotNull(mutations.epochOrNull(principal,request,target)),{ r,t -> mutations.epochOrNull(principal,r,t) },{ true })
            assertTrue(auxiliary.bookmarkMutatorForTrustedHost(gate).mutate(request) is BookmarkMutationResult.Saved)
            mutations.revokeAll()
        }
        fun grant(domains: Set<FaroV5LocalReadDomain> = setOf(
            FaroV5LocalReadDomain.BOOKMARK_METADATA, FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
        )) = authority.grantAfterTrustedForegroundReadAction(domains)
        fun host() = FaroV5BookmarkReadHostBinding(pinned, pointer, workspace, auxiliary,
            principal, "host-a", { hostEpoch }, { hostReady }, authority)
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun recoveryNeedsDualGrantsAndExactLookupIsReadOnlyAfterReopen() = runBlocking {
        Case("recovery").use { f -> f.remove()
            f.host().use { host ->
                assertNull(host.readRemovedAfterExplicitRequest())
                f.grant(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA)); assertNull(host.readRemovedAfterExplicitRequest())
                f.grant()
                val before=f.auxiliary.backup.snapshot()
                assertTrue(requireNotNull(host.readAuthorized()).items.isEmpty())
                val page=requireNotNull(host.readRemovedAfterExplicitRequest())
                assertEquals(BookmarkId("saved"),page.items.single().id); assertTrue(host.isRecoveryCurrent(page))
                assertEquals(3,page.scopeRevision)
                assertEquals(before,f.auxiliary.backup.snapshot())
            }
            f.host().use { reopened ->
                val exact=requireNotNull(reopened.lookupRemovedAfterExplicitRequest(BookmarkId("saved")))
                assertEquals(1,exact.items.size)
                val missing=requireNotNull(reopened.lookupRemovedAfterExplicitRequest(BookmarkId("absent")))
                assertTrue(missing.items.isEmpty()); assertFalse(reopened.isRecoveryCurrent(exact))
            }
        }
    }
    @Test fun recoveryLeaseIsIdentityBoundAndRevokedOnPauseAbaEpochAndClose() = runBlocking {
        for(mode in 0..4) Case("lease-$mode").use { f -> f.remove(); f.grant(); f.host().use { host ->
            val page=requireNotNull(host.readRemovedAfterExplicitRequest())
            assertFalse(host.isRecoveryCurrent(page.copy()))
            when(mode) {
                0 -> f.authority.revokeAll()
                1 -> f.foreground=false
                2 -> { val other=StorageGenerationId("other"); f.pointer.compareAndSet(f.pinned.generationId,other); f.pointer.compareAndSet(other,f.pinned.generationId) }
                3 -> f.hostEpoch="other"
                4 -> host.close()
            }
            assertFalse(host.isRecoveryCurrent(page)); assertNull(host.readRemovedAfterExplicitRequest())
        } }
    }
    @Test fun sourceArchiveDeniesRecoveryAndInvalidatesLease() = runBlocking {
        Case("archive").use { f -> f.remove(); f.grant(); f.host().use { host ->
            val page=requireNotNull(host.readRemovedAfterExplicitRequest())
            // Test-only adapter to the real internal Workspace transition (no new app dependency).
            val request=ar.com.faro.v5.workspace.WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(f.chat,true),f.workspace.snapshot().revision,IdempotencyKey("archive"))
            val method=f.workspace.javaClass.methods.single { it.name.startsWith("transition") &&
                it.parameterTypes.contentEquals(arrayOf(ar.com.faro.v5.workspace.WorkspaceTransitionRequest::class.java)) }
            assertTrue(method.invoke(f.workspace,request) is ar.com.faro.v5.workspace.WorkspaceWriteResult.Committed)
            assertFalse(host.isRecoveryCurrent(page)); assertNull(host.readRemovedAfterExplicitRequest())
        } }
    }
}
