package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationDraftSaveRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceIntent
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Native SQLite tests: run only on Linux/glibc, not Termux Bionic JVM. */
class ConversationDraftStoreT2Test {
    @TempDir lateinit var directory: Path
    private fun path(name: String = "workspace.db") = directory.resolve(name).toString()
    private fun id(value: String = "a") = ConversationId(value)
    private fun create(store: ConversationWorkspaceStore, name: String = "a", revision: Long = 0L) =
        store.create(
            WorkspaceCreateRequest(
                ConversationWorkspaceEntry(id(name), name, 100L),
                revision, IdempotencyKey("create-$name"),
            )
        )

    private fun save(
        store: ConversationWorkspaceStore,
        name: String = "a",
        text: String,
        key: String,
        currentRevision: Long? = null,
        token: String? = null,
    ): ConversationDraftWriteResult {
        val original = store.draftSnapshot(id(name))!!
        return store.saveDraft(
            ConversationDraftSaveRequest(
                id(name),
                token ?: original.authorityToken,
                currentRevision ?: original.revision,
                text, IdempotencyKey(key),
            )
        )
    }

    @Test fun readAbsentConversationDoesNotCreateDraftOrPhantomChat() {
        ConversationWorkspaceStore(path()).use { store ->
            assertNull(store.draftSnapshot(id()))
            assertEquals(0L, store.snapshot().revision)
            assertTrue(store.backup.snapshot().drafts.isEmpty())
        }
    }

    @Test fun draftIsDurableAcrossCloseAndReopenInSameGeneration() {
        val file = path()
        lateinit var token: String
        ConversationWorkspaceStore(file).use { store ->
            create(store)
            token = store.draftSnapshot(id())!!.authorityToken
            val saved = save(store, text = "Primera línea\nSegunda línea", key = "save-a")
                as ConversationDraftWriteResult.Saved
            assertEquals(2L, saved.snapshot.revision)
            assertFalse(saved.replayed)
        }
        ConversationWorkspaceStore(file).use { reopened ->
            val draft = reopened.draftSnapshot(id())!!
            assertEquals("Primera línea\nSegunda línea", draft.text)
            assertEquals(token, draft.authorityToken)
            assertEquals(2L, draft.revision)
        }
    }

    @Test fun draftsOfTwoConversationsRemainSeparate() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store, "a")
            create(store, "b", 1)
            save(store, "a", "ALFA", "edit-a")
            save(store, "b", "BETA", "edit-b")
            assertEquals("ALFA", store.draftSnapshot(id("a"))!!.text)
            assertEquals("BETA", store.draftSnapshot(id("b"))!!.text)
            assertEquals(2, store.backup.snapshot().drafts.size)
        }
    }

    @Test fun sameKeyReplayedAcrossReopenAndCollisionRejected() {
        val file = path()
        lateinit var request: ConversationDraftSaveRequest
        ConversationWorkspaceStore(file).use { store ->
            create(store)
            val initial = store.draftSnapshot(id())!!
            request = ConversationDraftSaveRequest(id(), initial.authorityToken, 0L, "hola",
                IdempotencyKey("idempotent-draft"))
            assertTrue(store.saveDraft(request) is ConversationDraftWriteResult.Saved)
        }
        ConversationWorkspaceStore(file).use { store ->
            val replay = store.saveDraft(request) as ConversationDraftWriteResult.Saved
            assertTrue(replay.replayed)
            assertEquals("hola", replay.snapshot.text)
            assertEquals(2L, store.snapshot().revision)
            val mismatch = store.saveDraft(request.copy(text = "otro"))
            assertEquals(ConversationDraftWriteResult.IdentityConflict, mismatch)
        }
    }

    @Test fun staleRevisionFailsWithoutChangingDraftOrGlobalRevision() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            save(store, text = "nuevo", key = "first")
            val stale = save(store, text = "perdido", key = "second", currentRevision = 0)
            assertTrue(stale is ConversationDraftWriteResult.Stale)
            assertEquals("nuevo", store.draftSnapshot(id())!!.text)
            assertEquals(2L, store.snapshot().revision)
        }
    }

    @Test fun crashBeforeReceiptRollsBackDraftAndRetryIsValid() {
        val file = path()
        ConversationWorkspaceStore(file).use { create(it) }
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(file) { point ->
                if (point == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) error("inject")
            }.use { save(it, text = "no commit", key = "repeat") }
        }
        ConversationWorkspaceStore(file).use { restored ->
            assertEquals("", restored.draftSnapshot(id())!!.text)
            assertEquals(1L, restored.snapshot().revision)
            val saved = save(restored, text = "no commit", key = "repeat")
            assertTrue(saved is ConversationDraftWriteResult.Saved)
            assertEquals(2L, restored.snapshot().revision)
        }
    }

    @Test fun equalTextNoOpRecordsReceiptWithoutIncreasingRevision() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val original = store.draftSnapshot(id())!!
            val request = ConversationDraftSaveRequest(id(), original.authorityToken, 0, "",
                IdempotencyKey("empty-noop"))
            val done = store.saveDraft(request) as ConversationDraftWriteResult.Saved
            assertEquals(0L, done.snapshot.revision)
            assertEquals(1L, store.snapshot().revision)
            assertTrue((store.saveDraft(request) as ConversationDraftWriteResult.Saved).replayed)
        }
    }

    @Test fun archivedDraftRemainsReadableButCannotChange() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            save(store, text = "recordar", key = "first")
            store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(id(), true), 2L, IdempotencyKey("archive")
            ))
            assertEquals("recordar", store.draftSnapshot(id())!!.text)
            assertEquals(ConversationDraftWriteResult.Unavailable,
                save(store, text = "no", key = "archived"))
        }
    }

    @Test fun oldAuthorityTokenCannotWriteToRestoredGeneration() {
        val first = path("first.db")
        val second = path("restored.db")
        lateinit var oldToken: String
        val backup = ConversationWorkspaceStore(first).use { original ->
            create(original)
            oldToken = original.draftSnapshot(id())!!.authorityToken
            save(original, text = "conservar", key = "save")
            original.backup.snapshot()
        }
        ConversationWorkspaceStore(second).use { restored ->
            restored.backup.restoreOrVerify(backup)
            val draft = restored.draftSnapshot(id())!!
            assertEquals("conservar", draft.text)
            assertNotEquals(oldToken, draft.authorityToken)
            assertEquals(ConversationDraftWriteResult.Unavailable,
                save(restored, text = "prohibido", key = "old-token", token = oldToken))
        }
    }

    @Test fun backupCarriesDraftBytesRevisionsAndReceiptsExactly() {
        val backup = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            save(source, text = "texto con ñ 😃", key = "texto")
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(path("destination.db")).use { target ->
            val result = target.backup.restoreOrVerify(backup)
            assertEquals(backup, result)
            assertEquals(backup, target.backup.snapshot())
            assertEquals("texto con ñ 😃", target.draftSnapshot(id())!!.text)
        }
    }

    @Test fun orphanDraftInBackupRejectedWithoutMutatingTarget() {
        val backup = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            save(source, text = "hola", key = "first")
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(path("target.db")).use { target ->
            val malformed = backup.copy(conversations = emptyList())
            assertThrows(IllegalStateException::class.java) {
                target.backup.restoreOrVerify(malformed)
            }
            assertTrue(target.backup.snapshot().conversations.isEmpty())
        }
    }

    @Test fun oversizedDraftRejectedBeforeSQLiteWrite() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            assertThrows(IllegalArgumentException::class.java) {
                save(store, text = "a".repeat(32_001), key = "invalid")
            }
            assertEquals(1L, store.snapshot().revision)
        }
    }

    @Test fun distinctConversationIdentityCannotWriteAnotherDraft() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val req = ConversationDraftSaveRequest(
                id("other"), store.draftSnapshot(id())!!.authorityToken, 0, "wrong",
                IdempotencyKey("wrong-chat")
            )
            assertEquals(ConversationDraftWriteResult.NotFound, store.saveDraft(req))
            assertEquals("", store.draftSnapshot(id())!!.text)
        }
    }
    @Test fun restoredReceiptWithOriginalTokenNeverReplaysAsSuccess() {
        val source = path("receipt-source.db")
        lateinit var initialRequest: ConversationDraftSaveRequest
        val backup = ConversationWorkspaceStore(source).use { store ->
            create(store)
            val draft = store.draftSnapshot(id())!!
            initialRequest = ConversationDraftSaveRequest(
                id(), draft.authorityToken, draft.revision,
                "write once", IdempotencyKey("persisted-receipt"),
            )
            assertTrue(store.saveDraft(initialRequest) is ConversationDraftWriteResult.Saved)
            store.backup.snapshot()
        }
        ConversationWorkspaceStore(path("receipt-restored.db")).use { restored ->
            restored.backup.restoreOrVerify(backup)
            assertEquals("write once", restored.draftSnapshot(id())!!.text)
            val result = restored.saveDraft(initialRequest)
            assertEquals(ConversationDraftWriteResult.Unavailable, result)
            assertEquals(backup, restored.backup.snapshot())
        }
    }

    @Test fun aggregateCapRejectsNextDraftWithoutChangingAnyReceipts() {
        ConversationWorkspaceStore(path()).use { store ->
            // The 2,000,000 ceiling is over *all* drafts, not one chat.
            // Exercise the actual Workspace owner instead of only a pure mock.
            repeat(62) { index ->
                val chat = "chat-" + index
                create(store, chat, store.snapshot().revision)
                assertTrue(save(store, chat, "x".repeat(32_000),
                    "fill-" + index) is ConversationDraftWriteResult.Saved)
            }
            create(store, "last", store.snapshot().revision)
            val accepted = save(store, "last", "y".repeat(16_000), "exact-boundary")
            assertTrue(accepted is ConversationDraftWriteResult.Saved)
            val before = store.backup.snapshot()
            assertEquals(2_000_000L, before.drafts.sumOf { it.text.length.toLong() })
            val revision = store.snapshot().revision

            val refused = save(store, "last", "y".repeat(16_001), "over-boundary")
            assertEquals(ConversationDraftWriteResult.CapacityExceeded, refused)
            assertEquals(revision, store.snapshot().revision)
            assertEquals(before, store.backup.snapshot())
            assertEquals("y".repeat(16_000), store.draftSnapshot(id("last"))!!.text)

            // Failed request inserted no receipt; accepted idempotency is retained.
            val replay = save(store, "last", "y".repeat(16_000),
                "exact-boundary", currentRevision = 0L)
            assertTrue(replay is ConversationDraftWriteResult.Saved)
            assertTrue((replay as ConversationDraftWriteResult.Saved).replayed)
        }
    }

    @Test fun conservativeUtf8CapacityNeverTruncatesExistingUnicodeDraft() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val content = "😃".repeat(15_000)
            assertTrue(save(store, text = content, key = "unicode") is ConversationDraftWriteResult.Saved)
            assertEquals(content, store.backup.snapshot().drafts.single().text)
        }
    }

}
