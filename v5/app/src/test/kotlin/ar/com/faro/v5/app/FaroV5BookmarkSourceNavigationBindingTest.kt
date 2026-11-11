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
class FaroV5BookmarkSourceNavigationBindingTest {
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
        val reader by lazy { FaroV5BookmarkReadHostBinding(pinned, pointer, workspace, auxiliary,
            principal, "host-a", { hostEpoch }, { foreground && hostReady }, authority) }
        var selected: ConversationWorkspaceSelectionPort = workspace
        var snapshots: ConversationWorkspaceReadPort = ConversationWorkspaceReadPort { workspace.snapshot().workspace }
        var timelines: ConversationTimelineReadPort = ConversationTimelineReadPort { workspace.readTimeline(it) }
        fun host() = FaroV5BookmarkSourceNavigationBinding(pinned, pointer, workspace, auxiliary,
            reader, authority, principal, { foreground && hostReady && hostEpoch == "host-a" },
            selected, snapshots, timelines)
        suspend fun page(): FaroV5BookmarkHostPage { grant(); return requireNotNull(reader.readAuthorized()) }
        suspend fun remove() {
            val target = BookmarkTarget(chat, message, pinned.generationId.value, token)
            val request = BookmarkMutationRequest(target.generationId, token, 2, IdempotencyKey("remove"),
                BookmarkMutation.LifecycleSet(BookmarkId("saved"), BookmarkLifecycle.REMOVED))
            assertTrue(mutations.grantAfterTrustedForegroundMutationAction(request, target))
            val gate = ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkMutationAuthorityGate(workspace, pinned, pointer,
                request, requireNotNull(mutations.epochOrNull(principal, request, target)),
                { r,t -> mutations.epochOrNull(principal,r,t) }, { true })
            assertTrue(auxiliary.bookmarkMutatorForTrustedHost(gate).mutate(request) is BookmarkMutationResult.Saved)
        }
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun selectsFreshCanonicalSourceAndDoesNotMutateAuxiliary() = runBlocking {
        Case("exact").use { f ->
            val other = ConversationId("other")
            f.workspace.createAndSelect(other, 2, IdempotencyKey("other"))
            val page = f.page(); val before = f.auxiliary.backup.snapshot()
            f.host().use { host ->
                val target = requireNotNull(host.open(page, page.items.single()))
                assertEquals(f.chat, target.conversationId); assertEquals(f.message, target.messageId)
                assertEquals(f.chat, f.workspace.snapshot().workspace.activeConversationId)
                assertTrue(host.isCurrent(target)); assertNull(host.open(page, page.items.single()))
                f.authority.revokeAll(); assertFalse(host.isCurrent(target))
            }
            assertEquals(before, f.auxiliary.backup.snapshot())
        }
    }
    @Test fun forgedPageAndCopiedRowAreNotLeased() = runBlocking {
        Case("forged").use { f -> val page=f.page()
            f.host().use { assertNull(it.open(page.copy(),page.items.single())) }
            f.host().use { assertNull(it.open(page,page.items.single().copy())) }
        }
    }
    @Test fun replacedPageInvalidatesOriginalCallback() = runBlocking {
        Case("page").use { f -> val page=f.page(); f.reader.readAuthorized()
            f.host().use { assertNull(it.open(page,page.items.single())) }
        }
    }
    @Test fun removedAfterPageReadNeverSelects() = runBlocking {
        Case("removed").use { f -> val page=f.page(); f.remove()
            f.host().use { assertNull(it.open(page,page.items.single())) }
        }
    }
    @Test fun revokedBackgroundEpochClosedAndFullPointerAbaDeny() = runBlocking {
        for(mode in 0..4) Case("fence-$mode").use { f -> val page=f.page(); f.host().use { host ->
            when(mode) {
                0 -> f.authority.revokeAll()
                1 -> f.foreground=false
                2 -> f.hostEpoch="host-b"
                3 -> host.close()
                4 -> { val next=StorageGenerationId("other"); f.pointer.compareAndSet(f.pinned.generationId,next); f.pointer.compareAndSet(next,f.pinned.generationId) }
            }
            assertNull(host.open(page,page.items.single()))
        } }
    }
    @Test fun savedSelectionWithWrongFreshWorkspaceDoesNotOpenPreviousChat() = runBlocking {
        Case("workspace").use { f -> val page=f.page()
            f.snapshots=ConversationWorkspaceReadPort { f.workspace.snapshot().workspace.copy(activeConversationId=ConversationId("wrong")) }
            f.host().use { assertNull(it.open(page,page.items.single())) }
        }
    }
    @Test fun wrongConversationSameMessageIdAndMissingTimelineAreDenied() = runBlocking {
        for(mode in 0..2) Case("timeline-$mode").use { f -> val page=f.page()
            f.timelines=ConversationTimelineReadPort {
                val timeline=requireNotNull(f.workspace.readTimeline(it))
                when(mode) {
                    0 -> { val wrong=ConversationId("wrong"); timeline.copy(conversationId=wrong,messages=timeline.messages.map { it.copy(conversationId=wrong) }) }
                    1 -> timeline.copy(messages=emptyList())
                    else -> null
                }
            }
            f.host().use { assertNull(it.open(page,page.items.single())) }
        }
    }
    @Test fun selectionFailuresAndExceptionsNeverRetry() = runBlocking {
        for(mode in 0..2) Case("select-$mode").use { f -> val page=f.page(); var calls=0
            f.selected=object: ConversationWorkspaceSelectionPort {
                override suspend fun createAndSelect(id: ConversationId, atMillis: Long, key: IdempotencyKey): ConversationSelectionResult = error("unexpected")
                override suspend fun select(id: ConversationId, key: IdempotencyKey): ConversationSelectionResult {
                    calls++
                    return when(mode) {
                        0 -> ConversationSelectionResult.Stale(f.workspace.snapshot().workspace)
                        1 -> ConversationSelectionResult.Rejected
                        else -> error("post commit uncertainty")
                    }
                }
            }
            f.host().use { assertNull(it.open(page,page.items.single())); assertNull(it.open(page,page.items.single())) }
            assertEquals(1,calls)
        }
    }
    @Test fun revokeDuringTimelineAndLateSelectionChangeInvalidatePublication() = runBlocking {
        for(mode in 0..1) Case("late-$mode").use { f -> val page=f.page()
            f.timelines=ConversationTimelineReadPort {
                val value=f.workspace.readTimeline(it)
                if(mode==0) f.authority.revokeAll() else f.workspace.createAndSelect(ConversationId("other"),2,IdempotencyKey("late"))
                value
            }
            f.host().use { assertNull(it.open(page,page.items.single())) }
        }
    }
}
