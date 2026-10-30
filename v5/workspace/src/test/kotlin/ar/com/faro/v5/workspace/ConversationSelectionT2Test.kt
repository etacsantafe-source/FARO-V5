package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C130: real Workspace SQLite identity, receipt, restart and crash fence. */
class ConversationSelectionT2Test {
    @TempDir lateinit var root: Path
    private fun path() = root.resolve("selection.db").toString()
    private fun <T> call(fn: suspend () -> T): T {
        var outcome: Result<T>? = null
        fn.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return requireNotNull(outcome).getOrThrow()
    }

    @Test fun atomicCreateSelectAndReopen() {
        ConversationWorkspaceStore(path()).use { store ->
            assertTrue(call { store.createAndSelect(ConversationId("a"), 100, IdempotencyKey("new-a")) }
                is ConversationSelectionResult.Saved)
            assertEquals(ConversationId("a"), store.snapshot().workspace.activeConversationId)
            assertEquals(1L, store.snapshot().revision)
        }
        ConversationWorkspaceStore(path()).use { restored ->
            assertEquals(ConversationId("a"), restored.snapshot().workspace.activeConversationId)
            assertNotNull(restored.draftSnapshot(ConversationId("a")))
        }
    }

    @Test fun newChatReusesEmptyAndReceiptReplays() {
        ConversationWorkspaceStore(path()).use { store ->
            call { store.createAndSelect(ConversationId("a"), 100, IdempotencyKey("new-a")) }
            call { store.createAndSelect(ConversationId("unused"), 101, IdempotencyKey("reuse")) }
            val replay = call {
                store.createAndSelect(ConversationId("unused"), 101, IdempotencyKey("reuse"))
            } as ConversationSelectionResult.Saved
            assertTrue(replay.replayed)
            assertEquals(1L, store.snapshot().revision)
            assertEquals(1, store.snapshot().workspace.conversations.size)
        }
    }

    @Test fun draftPreventsEmptyReuseAndOldChatCanBeReselected() {
        ConversationWorkspaceStore(path()).use { store ->
            val a = ConversationId("a")
            call { store.createAndSelect(a, 100, IdempotencyKey("new-a")) }
            val draft = store.draftSnapshot(a)!!
            assertTrue(store.saveDraft(ConversationDraftSaveRequest(
                a, draft.authorityToken, draft.revision, "hola", IdempotencyKey("draft")
            )) is ConversationDraftWriteResult.Saved)
            call { store.createAndSelect(ConversationId("b"), 101, IdempotencyKey("new-b")) }
            assertEquals(2, store.snapshot().workspace.conversations.size)
            assertEquals(ConversationId("b"), store.snapshot().workspace.activeConversationId)
            assertTrue(call { store.select(a, IdempotencyKey("select-a")) } is ConversationSelectionResult.Saved)
            assertEquals("hola", store.draftSnapshot(a)!!.text)
        }
    }

    @Test fun archivedOrUnknownIdentityCannotBeSelected() {
        ConversationWorkspaceStore(path()).use { store ->
            val a = ConversationId("a")
            call { store.createAndSelect(a, 100, IdempotencyKey("new-a")) }
            store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(a, true), store.snapshot().revision,
                IdempotencyKey("archive"),
            ))
            assertEquals(ConversationSelectionResult.Rejected,
                call { store.select(a, IdempotencyKey("select-archived")) })
            assertEquals(ConversationSelectionResult.Rejected,
                call { store.select(ConversationId("missing"), IdempotencyKey("missing")) })
        }
    }

    @Test fun crashBetweenWriteAndReceiptRollsBackCreateAndSelection() {
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(path()) { fault ->
                if (fault == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) error("crash")
            }.use { store ->
                call { store.createAndSelect(ConversationId("a"), 100, IdempotencyKey("new-a")) }
            }
        }
        ConversationWorkspaceStore(path()).use { store ->
            assertTrue(store.snapshot().workspace.conversations.isEmpty())
            assertNull(store.snapshot().workspace.activeConversationId)
        }
    }
}
