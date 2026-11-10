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

class FaroV5WorkspaceBookmarkAuthorityGateTest {
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
        fun gate(source: () -> String? = { epoch }) = FaroV5WorkspaceBookmarkAuthorityGate(
            workspace, pinned, pointer, "epoch-a", source, { ready })
        override fun close() = workspace.close()
    }

    @Test fun actualWorkspaceTargetAndOwnerTokenAreRequiredWithoutGrantingWrites() {
        Fixture().use { f ->
            val gate = f.gate()
            val target = f.target()
            assertTrue(gate.isScopeActive("gen-a", f.token))
            assertTrue(gate.isTargetAvailable(target))
            assertFalse(gate.isTargetAvailable(target.copy(conversationId = ConversationId("b"))))
            assertFalse(gate.isTargetAvailable(target.copy(messageId = ConversationMessageId("missing"))))
            assertFalse(gate.isScopeActive("gen-a", "foreign-token"))
            assertFalse(gate.isScopeActive("gen-b", f.token))
            assertFalse(gate.isMutationActive("gen-a", f.token))
            assertEquals(f.pinned, f.pointer.read())
        }
    }

    @Test fun absentRenewedFailedOrUnadmittedReadGrantCannotUseWorkspaceFactsAsAcl() {
        Fixture().use { f ->
            val gate = f.gate()
            f.epoch = null
            assertFalse(gate.isScopeActive("gen-a", f.token))
            f.epoch = "epoch-b"
            assertFalse(gate.isScopeActive("gen-a", f.token))
            f.epoch = "epoch-a"
            f.ready = false
            assertFalse(gate.isScopeActive("gen-a", f.token))
            f.ready = true
            assertFalse(f.gate { error("grant unavailable") }.isScopeActive("gen-a", f.token))
        }
    }

    @Test fun pointerAbaNeverResurrectsSameGenerationReadSession() {
        Fixture().use { f ->
            val gate = f.gate()
            val token = f.token
            assertTrue(gate.isScopeActive("gen-a", token))
            assertNotNull(f.pointer.compareAndSet(f.pinned.generationId, StorageGenerationId("gen-b")))
            assertNotNull(f.pointer.compareAndSet(StorageGenerationId("gen-b"), f.pinned.generationId))
            assertEquals(f.pinned.generationId, f.pointer.read()!!.generationId)
            assertNotEquals(f.pinned.sequence, f.pointer.read()!!.sequence)
            assertFalse(gate.isScopeActive("gen-a", token))
        }
    }

    @Test fun closedCanonicalWorkspaceFailsClosedWithoutThrowingThroughGate() {
        Fixture().use { f ->
            val gate = f.gate()
            val token = f.token
            val target = f.target()
            f.workspace.close()
            assertFalse(gate.isScopeActive("gen-a", token))
            assertFalse(gate.isTargetAvailable(target))
        }
    }

    @Test fun grantRevokedDuringCanonicalLookupDiscardsTargetAvailability() {
        Fixture().use { f ->
            var checks = 0
            val gate = f.gate { if (++checks <= 2) "epoch-a" else null }
            assertFalse(gate.isTargetAvailable(f.target()))
            assertTrue(checks >= 3)
        }
    }

    @Test fun genuineOwnerReadGateCannotMutateAuxiliaryEvenWithMatchingToken() {
        Fixture().use { f ->
            AuxiliaryDeliveryStore(root.resolve("auxiliary.db").toString(), bookmarkAuthorityGate = f.gate()).use { aux ->
                val before = aux.backup.snapshot()
                assertInstanceOf(BookmarkReadResult.Available::class.java,
                    sync { aux.bookmarks.read(BookmarkReadRequest("gen-a", f.token)) })
                val request = BookmarkMutationRequest("gen-a", f.token, 0, IdempotencyKey("folder"),
                    BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"), "No write grant")))
                assertEquals(BookmarkMutationResult.Unavailable, sync { aux.bookmarks.mutate(request) })
                assertEquals(before, aux.backup.snapshot())
            }
        }
    }
}
