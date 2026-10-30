package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceIntent
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Only metadata/receipt portability. NOT a complete Chat/Prepared-plan backup. */
class WorkspaceBackupT2Test {
    @TempDir lateinit var directory: Path

    private fun path(name: String) = directory.resolve(name).toString()

    private fun create(
        store: ConversationWorkspaceStore,
        key: String = "create-a",
    ): WorkspaceWriteResult =
        store.create(
            WorkspaceCreateRequest(
                entry = ConversationWorkspaceEntry(
                    ConversationId("conversation-a"),
                    "Primera conversación",
                    100,
                ),
                expectedRevision = 0,
                idempotencyKey = IdempotencyKey(key),
            )
        )

    private fun pin(store: ConversationWorkspaceStore): WorkspaceWriteResult =
        store.transition(
            WorkspaceTransitionRequest(
                intent = ConversationWorkspaceIntent.SetPinned(
                    ConversationId("conversation-a"), true,
                ),
                expectedRevision = 1,
                idempotencyKey = IdempotencyKey("pin-a"),
            )
        )

    @Test fun emptyFreshBackupHasNoPhantomConversationsOrReceipts() {
        ConversationWorkspaceStore(path("empty.db")).use { store ->
            val snapshot = store.backup.snapshot()
            assertEquals(WorkspaceBackupSnapshot.SCHEMA_VERSION, snapshot.schemaVersion)
            assertEquals(0L, snapshot.revision)
            assertTrue(snapshot.conversations.isEmpty())
            assertTrue(snapshot.receipts.isEmpty())
            assertEquals(null, snapshot.activeConversationId)
            snapshot.validateForRestore()
        }
    }

    @Test fun restoresMetadataAndPersistentReceiptsIntoFreshGeneration() {
        val original = ConversationWorkspaceStore(path("source.db")).use { source ->
            assertTrue(create(source) is WorkspaceWriteResult.Committed)
            assertTrue(pin(source) is WorkspaceWriteResult.Committed)
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(path("target.db")).use { target ->
            val result = target.backup.restoreOrVerify(original)
            assertEquals(original, result)
            assertEquals(2L, target.snapshot().revision)
            assertTrue(target.snapshot().workspace.conversations.single().pinned)
            assertEquals(2, target.backup.snapshot().receipts.size)
            val replay = create(target) as WorkspaceWriteResult.Committed
            assertTrue(replay.replayed)
            assertEquals(1L, replay.committedRevision)
            assertEquals(2L, target.snapshot().revision)
        }
        ConversationWorkspaceStore(path("target.db")).use { opened ->
            assertEquals(original, opened.backup.snapshot())
        }
    }

    @Test fun repeatRestoreVerifiesExistingExactPayloadWithoutWrites() {
        val original = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(path("target.db")).use { target ->
            target.backup.restoreOrVerify(original)
            val revision = target.snapshot().revision
            val verified = target.backup.restoreOrVerify(original)
            assertEquals(original, verified)
            assertEquals(revision, target.snapshot().revision)
        }
    }

    @Test fun conflictingTargetRejectsInsteadOfMergingOrReplacingState() {
        val original = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            source.backup.snapshot()
        }
        ConversationWorkspaceStore(path("conflicting.db")).use { target ->
            create(target, key = "another-key")
            val before = target.backup.snapshot()
            assertThrows(IllegalStateException::class.java) {
                target.backup.restoreOrVerify(original)
            }
            assertEquals(before, target.backup.snapshot())
        }
    }

    @Test fun malformedPendingReviewCannotBeRestoredWithoutPreparedAuthority() {
        val original = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            source.backup.snapshot()
        }
        val malformed = original.copy(
            conversations = original.conversations.map { it.copy(hasPendingReview = true) }
        )
        ConversationWorkspaceStore(path("target.db")).use { target ->
            assertThrows(IllegalStateException::class.java) {
                target.backup.restoreOrVerify(malformed)
            }
            assertEquals(0L, target.snapshot().revision)
            assertTrue(target.backup.snapshot().conversations.isEmpty())
        }
    }

    @Test fun faultInjectedRestoreRollsBackMetadataAndReceiptsTogether() {
        val original = ConversationWorkspaceStore(path("source.db")).use { source ->
            create(source)
            pin(source)
            source.backup.snapshot()
        }
        val file = path("fault.db")
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore(file) { point ->
                if (point == WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE) {
                    error("injected crash after metadata before replay receipts")
                }
            }.use { target ->
                target.backup.restoreOrVerify(original)
            }
        }
        ConversationWorkspaceStore(file).use { recovered ->
            assertEquals(0L, recovered.snapshot().revision)
            assertTrue(recovered.backup.snapshot().conversations.isEmpty())
            assertTrue(recovered.backup.snapshot().receipts.isEmpty())
            assertEquals(original, recovered.backup.restoreOrVerify(original))
        }
    }
}
