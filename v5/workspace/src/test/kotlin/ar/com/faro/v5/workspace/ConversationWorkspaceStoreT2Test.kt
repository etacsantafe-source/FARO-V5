package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceIntent
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConversationWorkspaceStoreT2Test {
    @TempDir lateinit var directory: Path

    private fun path() = directory.resolve("workspace.db").toString()

    private fun entry(id: String = "conversation-a") = ConversationWorkspaceEntry(
        id = ConversationId(id),
        title = "Primera conversación",
        lastActivityEpochMillis = 100,
    )

    private fun create(
        store: ConversationWorkspaceStore,
        id: String = "conversation-a",
        revision: Long = 0,
        key: String = "create-a",
    ) = store.create(
        WorkspaceCreateRequest(entry(id), revision, IdempotencyKey(key))
    )

    private fun edit(
        store: ConversationWorkspaceStore,
        intent: ConversationWorkspaceIntent,
        revision: Long,
        key: String,
    ) = store.transition(
        WorkspaceTransitionRequest(intent, revision, IdempotencyKey(key))
    )

    @Test fun freshDatabaseHasNoPhantomConversationAndNoSelectedChat() {
        ConversationWorkspaceStore(path()).use { store ->
            val result = store.snapshot()
            assertEquals(0L, result.revision)
            assertTrue(result.workspace.conversations.isEmpty())
            assertEquals(null, result.workspace.activeConversationId)
        }
    }

    @Test fun explicitCreationAndReopenPersistIdentityRevisionAndMetadata() {
        val path = path()
        ConversationWorkspaceStore(path).use { store ->
            val result = create(store) as WorkspaceWriteResult.Committed
            assertEquals(1L, result.committedRevision)
            assertEquals(1, result.snapshot.workspace.conversations.size)
            assertEquals(null, result.snapshot.workspace.activeConversationId)
        }
        ConversationWorkspaceStore(path).use { restored ->
            val snapshot = restored.snapshot()
            assertEquals(1L, snapshot.revision)
            assertEquals(ConversationId("conversation-a"),
                snapshot.workspace.conversations.single().id)
        }
    }

    @Test fun duplicateCreateDoesNotInsertOrAlterExistingConversation() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val result = create(
                store, revision = 1, key = "create-duplicate"
            )
            assertEquals(
                WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.ID_ALREADY_EXISTS),
                result,
            )
            assertEquals(1L, store.snapshot().revision)
            assertEquals(1, store.snapshot().workspace.conversations.size)
        }
    }

    @Test fun identicalRetryReplaysReceiptAfterReopenWithoutNewRevision() {
        val file = path()
        ConversationWorkspaceStore(file).use { store ->
            create(store)
        }
        ConversationWorkspaceStore(file).use { store ->
            val replay = create(store) as WorkspaceWriteResult.Committed
            assertTrue(replay.replayed)
            assertTrue(replay.changed)
            assertEquals(1L, replay.committedRevision)
            assertEquals(1L, replay.snapshot.revision)
        }
    }

    @Test fun idempotencyKeyCollisionRejectsDifferentPayload() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val conflict = create(store, id = "conversation-other", key = "create-a")
            assertEquals(WorkspaceWriteResult.IdentityConflict, conflict)
            assertEquals(1, store.snapshot().workspace.conversations.size)
        }
    }

    @Test fun staleExpectedRevisionIsRejectedWithoutWriteOrReceipt() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val stale = edit(
                store,
                ConversationWorkspaceIntent.SetPinned(ConversationId("conversation-a"), true),
                0,
                "pin-a",
            )
            assertTrue(stale is WorkspaceWriteResult.Stale)
            assertEquals(1L, store.snapshot().revision)
            assertFalse(store.snapshot().workspace.conversations.single().pinned)
        }
    }

    @Test fun pinArchiveAndUnarchiveSurviveReopenAndDoNotSelectPhantomChat() {
        val file = path()
        ConversationWorkspaceStore(file).use { store ->
            create(store)
            edit(store,
                ConversationWorkspaceIntent.Activate(ConversationId("conversation-a")),
                1, "activate-a",
            )
            edit(store,
                ConversationWorkspaceIntent.SetPinned(ConversationId("conversation-a"), true),
                2, "pin-a",
            )
            val archive = edit(store,
                ConversationWorkspaceIntent.SetArchived(ConversationId("conversation-a"), true),
                3, "archive-a",
            ) as WorkspaceWriteResult.Committed
            assertTrue(archive.changed)
            assertEquals(null, archive.snapshot.workspace.activeConversationId)
        }
        ConversationWorkspaceStore(file).use { restored ->
            val before = restored.snapshot()
            assertEquals(4L, before.revision)
            assertTrue(before.workspace.conversations.single().archived)
            assertFalse(before.workspace.conversations.single().pinned)
            assertEquals(null, before.workspace.activeConversationId)
            val unarchived = edit(restored,
                ConversationWorkspaceIntent.SetArchived(ConversationId("conversation-a"), false),
                4, "unarchive-a",
            ) as WorkspaceWriteResult.Committed
            assertEquals(5L, unarchived.snapshot.revision)
            assertEquals(null, unarchived.snapshot.workspace.activeConversationId)
        }
    }

    @Test fun idempotentNoOpRecordsReceiptWithoutIncreasingRevision() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val first = edit(
                store,
                ConversationWorkspaceIntent.SetPinned(ConversationId("conversation-a"), false),
                1, "noop-pin",
            ) as WorkspaceWriteResult.Committed
            assertFalse(first.changed)
            assertEquals(1L, first.committedRevision)
            val replay = edit(
                store,
                ConversationWorkspaceIntent.SetPinned(ConversationId("conversation-a"), false),
                1, "noop-pin",
            ) as WorkspaceWriteResult.Committed
            assertTrue(replay.replayed)
            assertEquals(1L, store.snapshot().revision)
        }
    }

    @Test fun invalidArchivedActivationDoesNotChangeAuthority() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            edit(store,
                ConversationWorkspaceIntent.SetArchived(ConversationId("conversation-a"), true),
                1, "archive-a",
            )
            val rejected = edit(store,
                ConversationWorkspaceIntent.Activate(ConversationId("conversation-a")),
                2, "activate-archived",
            )
            assertEquals(
                WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.ARCHIVED_CONVERSATION),
                rejected,
            )
            assertEquals(2L, store.snapshot().revision)
        }
    }

    @Test fun injectedCrashBeforeCommitRollsBackBothRowAndReceipt() {
        val file = path()
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(file) { point ->
                if (point == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) {
                    error("injected crash before receipt and commit")
                }
            }.use { store ->
                create(store, key = "attempt-1")
            }
        }
        ConversationWorkspaceStore(file).use { store ->
            assertEquals(0L, store.snapshot().revision)
            assertTrue(store.snapshot().workspace.conversations.isEmpty())
            val retried = create(store, key = "attempt-1")
            assertTrue(retried is WorkspaceWriteResult.Committed)
            assertEquals(1L, store.snapshot().revision)
        }
    }

    @Test fun concurrentWritesWithSameExpectedRevisionCommitAtMostOne() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val barrier = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val first = executor.submit(Callable {
                    barrier.await()
                    edit(
                        store,
                        ConversationWorkspaceIntent.SetPinned(ConversationId("conversation-a"), true),
                        1, "concurrent-pin",
                    )
                })
                val second = executor.submit(Callable {
                    barrier.await()
                    edit(
                        store,
                        ConversationWorkspaceIntent.Rename(ConversationId("conversation-a"), "Renombrada"),
                        1, "concurrent-rename",
                    )
                })
                barrier.countDown()
                val results = listOf(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS))
                assertEquals(1, results.count { it is WorkspaceWriteResult.Committed })
                assertEquals(1, results.count { it is WorkspaceWriteResult.Stale })
                assertEquals(2L, store.snapshot().revision)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun replayReportsOriginalCommitButObservesCurrentSnapshot() {
        ConversationWorkspaceStore(path()).use { store ->
            val initial = create(store) as WorkspaceWriteResult.Committed
            assertEquals(1L, initial.committedRevision)
            val renamed = edit(store,
                ConversationWorkspaceIntent.Rename(ConversationId("conversation-a"), "Renombrada"),
                1, "rename-a",
            ) as WorkspaceWriteResult.Committed
            assertEquals(2L, renamed.snapshot.revision)
            val replay = create(store) as WorkspaceWriteResult.Committed
            assertTrue(replay.replayed)
            assertEquals(1L, replay.committedRevision)
            assertEquals(2L, replay.snapshot.revision)
            assertEquals("Renombrada", replay.snapshot.workspace.conversations.single().title)
        }
    }

    @Test fun operationIdentityConflictCannotBeHiddenByStaleRevision() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val renamed = edit(store,
                ConversationWorkspaceIntent.Rename(ConversationId("conversation-a"), "Renombrada"),
                1, "same-key",
            )
            assertTrue(renamed is WorkspaceWriteResult.Committed)
            val collision = edit(store,
                ConversationWorkspaceIntent.Activate(ConversationId("conversation-a")),
                1, "same-key",
            )
            assertEquals(WorkspaceWriteResult.IdentityConflict, collision)
            assertEquals(2L, store.snapshot().revision)
        }
    }

    @Test fun readSnapshotIsDefensivelyImmutable() {
        ConversationWorkspaceStore(path()).use { store ->
            create(store)
            val snapshot = store.snapshot()
            @Suppress("UNCHECKED_CAST")
            val writable = snapshot.workspace.conversations as MutableList<ConversationWorkspaceEntry>
            assertThrows(UnsupportedOperationException::class.java) {
                writable.clear()
            }
            assertEquals(1, store.snapshot().workspace.conversations.size)
        }
    }

    @Test fun invalidIdempotencyKeyDoesNotPersistAnyMutation() {
        ConversationWorkspaceStore(path()).use { store ->
            assertThrows(IllegalArgumentException::class.java) {
                create(store, key = "x".repeat(161))
            }
            assertThrows(IllegalArgumentException::class.java) {
                create(store, key = "key" + 10.toChar() + "invalid")
            }
            assertTrue(store.snapshot().workspace.conversations.isEmpty())
            assertEquals(0L, store.snapshot().revision)
        }
    }

    @Test fun closeRejectsReadsAndWritesWithoutCreatingState() {
        val store = ConversationWorkspaceStore(path())
        store.close()
        assertThrows(IllegalStateException::class.java) {
            store.snapshot()
        }
    }
}
