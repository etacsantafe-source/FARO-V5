package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineIntent
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteRequest
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
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

/** glibc native SQLite gate, never run bundled driver from Android Termux Bionic JVM. */
class ConversationTimelineStoreT2Test {
    @TempDir lateinit var root: Path
    private fun db(name: String = "workspace.db") = root.resolve(name).toString()
    private fun chat(name: String = "a") = ConversationId(name)

    private fun create(store: ConversationWorkspaceStore, name: String = "a", rev: Long = 0L) =
        store.create(WorkspaceCreateRequest(
            ConversationWorkspaceEntry(chat(name), "Chat $name", 100L),
            rev, IdempotencyKey("new-$name"),
        ))

    private fun user(
        name: String = "a", key: String = "u-1", turn: String = "t-1",
        text: String = "Hola", reply: String? = null,
    ) = ConversationMessage(
        ConversationMessageId(key), chat(name), ConversationTurnId(turn),
        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
        text, 100L, reply?.let(::ConversationMessageId),
    )
    private fun assistant(name: String = "a", key: String = "a-1", turn: String = "t-1") =
        ConversationMessage(
            ConversationMessageId(key), chat(name), ConversationTurnId(turn),
            ConversationMessageRole.FARO, ConversationMessageState.PROCESSING, "", 101L,
        )

    private fun send(
        store: ConversationWorkspaceStore,
        name: String = "a",
        key: String,
        intent: ConversationTimelineIntent,
        revision: Long? = null,
        token: String? = null,
    ): ConversationTimelineWriteResult {
        val before = store.timelineSnapshot(chat(name))!!
        return store.saveTimeline(
            ConversationTimelineWriteRequest(
                chat(name), token ?: before.authorityToken, revision ?: before.revision,
                IdempotencyKey(key), intent,
            )
        )
    }

    @Test fun emptyWorkspaceReadDoesNotCreatePhantomConversation() {
        ConversationWorkspaceStore(db()).use { store ->
            assertNull(store.timelineSnapshot(chat()))
            assertEquals(0L, store.snapshot().revision)
        }
    }

    @Test fun userMessagePersistsWithoutInventingAssistantText() {
        val file = db()
        ConversationWorkspaceStore(file).use { store ->
            create(store)
            send(store, key = "write-user", intent = ConversationTimelineIntent.AcceptUser(user()))
        }
        ConversationWorkspaceStore(file).use { reopened ->
            val messages = reopened.timelineSnapshot(chat())!!.messages
            assertEquals(1, messages.size)
            assertEquals("Hola", messages.single().text)
            assertEquals(ConversationMessageState.ACCEPTED, messages.single().state)
        }
    }

    @Test fun twoConversationsNeverMixDelayedResponses() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            create(store, "b", 1L)
            send(store, key = "user-a", intent = ConversationTimelineIntent.AcceptUser(user()))
            send(store, "b", "user-b", ConversationTimelineIntent.AcceptUser(
                user("b", "u-b", "t-b", "mensaje B")
            ))
            send(store, key = "start-a", intent = ConversationTimelineIntent.StartFaro(assistant()))
            send(store, key = "finish-a", intent = ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("a-1"), ConversationTurnId("t-1"),
                ConversationMessageState.COMPLETED, "Respuesta original A",
            ))
            assertEquals("Respuesta original A", store.timelineSnapshot(chat())!!.messages.last().text)
            assertEquals(listOf("mensaje B"), store.timelineSnapshot(chat("b"))!!.messages.map { it.text })
        }
    }

    @Test fun quoteLinksOnlyEarlierMessageInSameConversation() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            send(store, key = "one", intent = ConversationTimelineIntent.AcceptUser(user()))
            send(store, key = "quoted", intent = ConversationTimelineIntent.AcceptUser(
                user(key = "u-2", turn = "t-2", reply = "u-1")
            ))
            assertEquals(ConversationMessageId("u-1"),
                store.timelineSnapshot(chat())!!.messages.last().replyToMessageId)
        }
    }

    @Test fun staleGlobalRevisionDoesNotWriteOrAppendReceipt() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            val attempt = send(store, key = "first", intent = ConversationTimelineIntent.AcceptUser(user()))
            assertTrue(attempt is ConversationTimelineWriteResult.Saved)
            val old = send(store, key = "stale", revision = 1L,
                intent = ConversationTimelineIntent.AcceptUser(user(key = "u-2", turn = "t-2")))
            assertTrue(old is ConversationTimelineWriteResult.Stale)
            assertEquals(1, store.timelineSnapshot(chat())!!.messages.size)
        }
    }

    @Test fun idempotencySurvivesCloseAndReopenWithoutDuplicatingMessage() {
        val file = db()
        lateinit var request: ConversationTimelineWriteRequest
        ConversationWorkspaceStore(file).use { store ->
            create(store)
            val before = store.timelineSnapshot(chat())!!
            request = ConversationTimelineWriteRequest(
                chat(), before.authorityToken, before.revision,
                IdempotencyKey("stable"), ConversationTimelineIntent.AcceptUser(user()),
            )
            assertFalse((store.saveTimeline(request) as ConversationTimelineWriteResult.Saved).replayed)
        }
        ConversationWorkspaceStore(file).use { store ->
            assertTrue((store.saveTimeline(request) as ConversationTimelineWriteResult.Saved).replayed)
            assertEquals(1, store.timelineSnapshot(chat())!!.messages.size)
            val altered = request.copy(
                intent = ConversationTimelineIntent.AcceptUser(user(text = "different"))
            )
            assertEquals(ConversationTimelineWriteResult.IdentityConflict, store.saveTimeline(altered))
        }
    }

    @Test fun crashBetweenMessageAndReceiptRollsBackBoth() {
        val file = db()
        ConversationWorkspaceStore(file).use { create(it) }
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(file) { point ->
                if (point == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) error("crash")
            }.use { store ->
                send(store, key = "crash-key", intent = ConversationTimelineIntent.AcceptUser(user()))
            }
        }
        ConversationWorkspaceStore(file).use { store ->
            assertTrue(store.timelineSnapshot(chat())!!.messages.isEmpty())
            assertEquals(1L, store.snapshot().revision)
            assertTrue(send(store, key = "crash-key",
                intent = ConversationTimelineIntent.AcceptUser(user())) is ConversationTimelineWriteResult.Saved)
        }
    }

    @Test fun processingIsTypedAndFinalFailureLeavesNoFalseAnswer() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            send(store, key = "user", intent = ConversationTimelineIntent.AcceptUser(user()))
            send(store, key = "start", intent = ConversationTimelineIntent.StartFaro(assistant()))
            val result = send(store, key = "fail", intent = ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("a-1"), ConversationTurnId("t-1"),
                ConversationMessageState.OFFLINE, "",
            )) as ConversationTimelineWriteResult.Saved
            assertEquals(ConversationMessageState.OFFLINE, result.snapshot.messages.last().state)
            assertEquals("", result.snapshot.messages.last().text)
        }
    }

    @Test fun replyCanFinishAfterChatArchivedWithoutSwitchingActiveConversation() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            send(store, key = "user", intent = ConversationTimelineIntent.AcceptUser(user()))
            send(store, key = "start", intent = ConversationTimelineIntent.StartFaro(assistant()))
            val before = store.snapshot().revision
            store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(chat(), true), before,
                IdempotencyKey("archive-chat"),
            ))
            send(store, key = "after-archive", intent = ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("a-1"), ConversationTurnId("t-1"),
                ConversationMessageState.COMPLETED, "Atado al origen",
            ))
            assertEquals("Atado al origen", store.timelineSnapshot(chat())!!.messages.last().text)
            assertNull(store.snapshot().workspace.activeConversationId)
        }
    }

    @Test fun archivedChatRejectsNewUserMessageButRetainsHistory() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(chat(), true), 1,
                IdempotencyKey("archive"),
            ))
            assertEquals(ConversationTimelineWriteResult.Unavailable,
                send(store, key = "blocked", intent = ConversationTimelineIntent.AcceptUser(user())))
            assertTrue(store.timelineSnapshot(chat())!!.messages.isEmpty())
        }
    }

    @Test fun snapshotRestorePreservesSequenceRepliesAndIdentity() {
        val backup = ConversationWorkspaceStore(db("source.db")).use { source ->
            create(source)
            send(source, key = "user", intent = ConversationTimelineIntent.AcceptUser(user()))
            send(source, key = "reply", intent = ConversationTimelineIntent.AcceptUser(
                user(key = "u-2", turn = "t-2", reply = "u-1")
            ))
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(db("restored.db")).use { dest ->
            assertEquals(backup, dest.backup.restoreOrVerify(backup))
            assertEquals(backup, dest.backup.snapshot())
            assertEquals(listOf("u-1","u-2"),
                dest.timelineSnapshot(chat())!!.messages.map { it.id.value })
        }
    }

    @Test fun oldGenerationReceiptCannotReplayAcrossRestoredAuthority() {
        val source = db("source.db")
        lateinit var request: ConversationTimelineWriteRequest
        val backup = ConversationWorkspaceStore(source).use { store ->
            create(store)
            val before = store.timelineSnapshot(chat())!!
            request = ConversationTimelineWriteRequest(
                chat(), before.authorityToken, before.revision,
                IdempotencyKey("write-original"), ConversationTimelineIntent.AcceptUser(user()),
            )
            store.saveTimeline(request)
            store.backup.snapshot()
        }
        ConversationWorkspaceStore(db("restored.db")).use { dest ->
            dest.backup.restoreOrVerify(backup)
            assertNotEquals(request.authorityToken, dest.timelineSnapshot(chat())!!.authorityToken)
            assertEquals(ConversationTimelineWriteResult.Unavailable, dest.saveTimeline(request))
            assertEquals(backup, dest.backup.snapshot())
        }
    }

    @Test fun duplicateMessageIdOrTurnCannotBeInsertedAgain() {
        ConversationWorkspaceStore(db()).use { store ->
            create(store)
            send(store, key = "first", intent = ConversationTimelineIntent.AcceptUser(user()))
            val attempt = send(store, key = "second", intent = ConversationTimelineIntent.AcceptUser(
                user(key = "other", turn = "t-1")
            ))
            assertTrue(attempt is ConversationTimelineWriteResult.Rejected)
            assertEquals(1, store.timelineSnapshot(chat())!!.messages.size)
        }
    }
}
