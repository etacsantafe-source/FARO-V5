package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationDraftPolicyTest {
    private fun snapshot(
        id: String = "chat-a",
        token: String = "generation-one",
        revision: Long = 0,
        text: String = "",
    ) = ConversationDraftSnapshot(ConversationId(id), token, revision, text)

    private fun state(text: String = "") =
        ConversationDraftEditorState(snapshot(text = text))

    private fun request(editor: ConversationDraftEditorState) =
        ConversationDraftPolicy.saveRequest(editor, IdempotencyKey("update-1"))!!

    @Test fun emptyDraftNeverCreatesASendCandidate() {
        assertNull(ConversationDraftPolicy.sendCandidate(state()))
        assertNull(ConversationDraftPolicy.saveRequest(state(), IdempotencyKey("noop")))
    }

    @Test fun whitespaceIsPreservedButNotSendable() {
        val remote = snapshot(revision = 1, text = "  \n ")
        val editor = ConversationDraftEditorState(remote)
        assertEquals("  \n ", editor.text)
        assertNull(ConversationDraftPolicy.sendCandidate(editor))
    }

    @Test fun editRejectsOverLimitInsteadOfSilentlyTruncating() {
        assertThrows(IllegalArgumentException::class.java) {
            ConversationDraftPolicy.edit(state(), "x".repeat(32_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(text = "x".repeat(32_001))
        }
    }

    @Test fun localChangeProducesCanonicalSaveWithStableConversationIdentity() {
        val editor = ConversationDraftPolicy.edit(state(), "hola")
        val save = request(editor)
        assertEquals(ConversationId("chat-a"), save.conversationId)
        assertEquals("generation-one", save.authorityToken)
        assertEquals(0, save.expectedRevision)
        assertEquals("hola", save.text)
        assertTrue(editor.hasUnsavedChanges)
        assertNull(ConversationDraftPolicy.sendCandidate(editor))
    }

    @Test fun readFromDifferentConversationNeverCrossesTheChatBoundary() {
        val editor = ConversationDraftPolicy.edit(state("anterior"), "editado")
        val result = ConversationDraftPolicy.reconcile(
            editor, snapshot(id = "chat-b", text = "ajeno", revision = 5)
        )
        assertEquals(editor, result)
    }

    @Test fun generationChangeFencesStaleEditorAndSend() {
        val editor = state("importante")
        val result = ConversationDraftPolicy.reconcile(
            editor, snapshot(token = "after-restore", text = "otro")
        )
        assertTrue(result.conflict)
        assertNull(ConversationDraftPolicy.sendCandidate(result))
    }

    @Test fun lateRemoteReadCannotReplaceUnsavedLocalChanges() {
        val editor = ConversationDraftPolicy.edit(state("antes"), "texto mío")
        val result = ConversationDraftPolicy.reconcile(
            editor, snapshot(revision = 7, text = "otro")
        )
        assertEquals("texto mío", result.text)
        assertTrue(result.conflict)
        assertNull(ConversationDraftPolicy.saveRequest(result, IdempotencyKey("nope")))
    }

    @Test fun newerRemoteReadUpdatesAnUntouchedEditor() {
        val result = ConversationDraftPolicy.reconcile(
            state("inicial"), snapshot(revision = 4, text = "restaurado")
        )
        assertEquals("restaurado", result.text)
        assertEquals(4, result.persisted.revision)
        assertFalse(result.hasUnsavedChanges)
    }

    @Test fun acknowledgedSaveStillPreservesTypingAfterRequestWentOut() {
        val initial = ConversationDraftPolicy.edit(state(), "primero")
        val savedRequest = request(initial)
        val laterTyping = ConversationDraftPolicy.edit(initial, "primero más")
        val updated = ConversationDraftPolicy.acknowledge(
            laterTyping, savedRequest, snapshot(revision = 1, text = "primero")
        )
        assertEquals("primero más", updated.text)
        assertEquals(1, updated.persisted.revision)
        assertTrue(updated.hasUnsavedChanges)
        assertNull(ConversationDraftPolicy.sendCandidate(updated))
    }

    @Test fun acceptedMatchingSaveAllowsCandidateWithoutClearingDraft() {
        val initial = ConversationDraftPolicy.edit(state(), "hola FARO")
        val result = ConversationDraftPolicy.acknowledge(
            initial, request(initial), snapshot(revision = 1, text = "hola FARO")
        )
        val candidate = ConversationDraftPolicy.sendCandidate(result)!!
        assertEquals(ConversationId("chat-a"), candidate.conversationId)
        assertEquals("hola FARO", candidate.text)
        assertEquals(1, candidate.draftRevision)
        assertEquals("hola FARO", result.text)
        assertFalse(result.hasUnsavedChanges)
    }

    @Test fun mismatchedAcknowledgementCannotMarkDraftSaved() {
        val initial = ConversationDraftPolicy.edit(state(), "hola")
        val result = ConversationDraftPolicy.acknowledge(
            initial, request(initial), snapshot(revision = 1, text = "distinto")
        )
        assertTrue(result.conflict)
        assertNull(ConversationDraftPolicy.sendCandidate(result))
    }

    @Test fun staleRemoteRevisionDoesNotMoveEditorBackwards() {
        val original = ConversationDraftEditorState(snapshot(revision = 5, text = "actual"))
        assertEquals(original, ConversationDraftPolicy.reconcile(
            original, snapshot(revision = 4, text = "antiguo")
        ))
    }
    @Test fun divergentBytesAtSameRevisionMustFailClosed() {
        val initial = state("primera")
        val result = ConversationDraftPolicy.reconcile(
            initial, snapshot(text = "otra", revision = 0)
        )
        assertTrue(result.conflict)
        assertNull(ConversationDraftPolicy.sendCandidate(result))
    }

    @Test fun oldSaveReceiptNeverClearsAnExistingConflict() {
        val initial = ConversationDraftPolicy.edit(state(), "primer intento")
        val save = request(initial)
        val conflicted = initial.copy(conflict = true)
        val result = ConversationDraftPolicy.acknowledge(
            conflicted, save, snapshot(revision = 1, text = "primer intento")
        )
        assertTrue(result.conflict)
        assertEquals(conflicted, result)
    }

    @Test fun explicitlyReviewedConflictCanKeepLocalTextAtLatestRevision() {
        val initial = ConversationDraftPolicy.edit(state(), "mi texto")
        val conflicted = initial.copy(conflict = true)
        val latest = snapshot(revision = 6, text = "otro borrador")
        val resolved = ConversationDraftPolicy.resolveAfterReview(conflicted, latest, true)
        assertEquals("mi texto", resolved.text)
        assertEquals(6, resolved.persisted.revision)
        assertFalse(resolved.conflict)
        assertEquals(6, request(resolved).expectedRevision)
    }

    @Test fun explicitlyReviewedConflictCanUseRestoredCanonicalText() {
        val original = state("local").copy(conflict = true)
        val restored = snapshot(token = "generation-restored", revision = 2, text = "autoridad")
        val result = ConversationDraftPolicy.resolveAfterReview(original, restored, false)
        assertEquals("autoridad", result.text)
        assertEquals("generation-restored", result.persisted.authorityToken)
        assertFalse(result.hasUnsavedChanges)
        assertFalse(result.conflict)
    }

    @Test fun authorityTokenRejectsControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(token = "unsafe\nlog")
        }
    }

    @Test fun higherRevisionCannotSilentlyClearAnUnresolvedConflict() {
        val conflicted = state("no pisar").copy(conflict = true)
        val incoming = snapshot(revision = 8, text = "remote restored")
        val result = ConversationDraftPolicy.reconcile(conflicted, incoming)
        assertEquals(conflicted, result)
        assertTrue(result.conflict)
        assertNull(ConversationDraftPolicy.sendCandidate(result))
    }

}
