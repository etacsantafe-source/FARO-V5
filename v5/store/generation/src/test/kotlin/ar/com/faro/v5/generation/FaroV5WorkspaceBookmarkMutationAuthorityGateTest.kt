package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5WorkspaceBookmarkMutationAuthorityGateTest {
    @TempDir lateinit var root: Path
    private fun <T> sync(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome).getOrThrow()
    }
    private inner class Fixture : AutoCloseable {
        val workspace = ConversationWorkspaceStore(root.resolve("workspace.db").toString())
        val pointer = FileActiveGenerationPointer(root.resolve("pointer"))
        val pinned = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("gen-a")))
        var epoch: String? = "epoch-a"
        var ready = true
        init {
            val chat = ConversationId("a")
            sync { workspace.createAndSelect(chat, 1, IdempotencyKey("create-a")) }
            val before = checkNotNull(sync { workspace.readTimeline(chat) })
            assertInstanceOf(ConversationTimelineWriteResult.Saved::class.java,
                sync { workspace.writeTimeline(ConversationTimelineWriteRequest(chat, before.authorityToken,
                    before.revision, IdempotencyKey("user-a"), ConversationTimelineIntent.AcceptUser(
                        ConversationMessage(ConversationMessageId("message-a"), chat, ConversationTurnId("turn-a"),
                            ConversationMessageRole.USER, ConversationMessageState.ACCEPTED, "reserved content", 1)))) })
            sync { workspace.createAndSelect(ConversationId("b"), 2, IdempotencyKey("create-b")) }
        }
        val token get() = workspace.bookmarkReadAdmission().authorityToken
        fun target() = BookmarkTarget(ConversationId("a"), ConversationMessageId("message-a"), pinned.generationId.value, token)
        fun request() = BookmarkMutationRequest("gen-a", token, 0, IdempotencyKey("add"),
            BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b"), target())))
        fun gate(command: BookmarkMutationRequest = request(), source: () -> String? = { epoch }) =
            FaroV5WorkspaceBookmarkMutationAuthorityGate(workspace, pinned, pointer, command,
                "epoch-a", { _, _ -> source() }, { ready })
        override fun close() = workspace.close()
    }

    @Test fun exactRequestAndCanonicalSourceAreRequired() {
        Fixture().use { f ->
            val r = f.request(); val g = f.gate(r)
            assertTrue(g.isMutationAuthorized(r, f.target()))
            assertFalse(g.isMutationAuthorized(r.copy(idempotencyKey = IdempotencyKey("other")), f.target()))
            assertFalse(g.isMutationAuthorized(r, f.target().copy(conversationId = ConversationId("b"))))
            assertFalse(g.isMutationAuthorized(r, null))
            assertFalse(g.isMutationActive("gen-a", f.token))
        }
    }
    @Test fun epochRevocationPointerAbaAndOwnerCloseDeny() {
        Fixture().use { f ->
            val r = f.request(); val g = f.gate(r)
            f.epoch = null
            assertFalse(g.isMutationAuthorized(r, f.target()))
            f.epoch = "renewed"
            assertFalse(g.isMutationAuthorized(r, f.target()))
            f.epoch = "epoch-a"
            assertTrue(g.isMutationAuthorized(r, f.target()))
            f.pointer.compareAndSet(f.pinned.generationId, StorageGenerationId("other"))
            f.pointer.compareAndSet(StorageGenerationId("other"), f.pinned.generationId)
            assertFalse(g.isMutationAuthorized(r, f.target()))
        }
    }
    @Test fun closedWorkspaceFailsClosed() {
        Fixture().use { f ->
            val r = f.request(); val t = f.target(); val g = f.gate(r)
            f.workspace.close()
            assertFalse(g.isMutationAuthorized(r, t))
        }
    }
    @Test fun exactFolderConsentRequiresWorkspaceScopeButNoMessage() {
        Fixture().use { f ->
            val r = f.request().copy(mutation = BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"), "Folder")))
            val g = f.gate(r)
            assertTrue(g.isMutationAuthorized(r, null))
            assertFalse(g.isMutationAuthorized(r, f.target()))
            f.ready = false
            assertFalse(g.isMutationAuthorized(r, null))
        }
    }
    @Test fun revocationDuringSourceLookupFailsClosed() {
        Fixture().use { f ->
            var reads = 0
            val r = f.request()
            val g = f.gate(r) { if (++reads < 2) "epoch-a" else null }
            assertFalse(g.isMutationAuthorized(r, f.target()))
        }
    }
}
