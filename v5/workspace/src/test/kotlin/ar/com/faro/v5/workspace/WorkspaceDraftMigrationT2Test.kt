package ar.com.faro.v5.workspace

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.ConversationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Version one was the staged metadata-only SQLite format, never an active user cutover. */
class WorkspaceDraftMigrationT2Test {
    @TempDir lateinit var directory: Path
    private fun file() = directory.resolve("old-metadata.db").toString()

    private fun createLegacyVersionOne() {
        BundledSQLiteDriver().open(file()).use { db ->
            db.execSQL(
                """CREATE TABLE workspace_conversation (
                   id TEXT NOT NULL PRIMARY KEY,
                   title TEXT NOT NULL,
                   activity_ms INTEGER NOT NULL,
                   pinned INTEGER NOT NULL,
                   archived INTEGER NOT NULL,
                   pending_review INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE workspace_meta (
                   singleton INTEGER NOT NULL PRIMARY KEY,
                   revision INTEGER NOT NULL,
                   active_id TEXT
                )"""
            )
            db.execSQL(
                """CREATE TABLE workspace_receipt (
                   key TEXT NOT NULL PRIMARY KEY,
                   fingerprint TEXT NOT NULL,
                   committed_revision INTEGER NOT NULL,
                   changed INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "INSERT INTO workspace_conversation VALUES ('old', 'Historial previo', 123, 0, 0, 0)"
            )
            db.execSQL("INSERT INTO workspace_meta VALUES (1, 1, 'old')")
            db.execSQL(
                "INSERT INTO workspace_receipt VALUES ('old-key', '" +
                    "a".repeat(64) + "', 1, 1)"
            )
            db.execSQL("PRAGMA user_version = 1")
        }
    }

    @Test fun explicitFreshOwnerMigratesV1MetadataWithoutDroppingRows() {
        createLegacyVersionOne()
        ConversationWorkspaceStore(file()).use { upgraded ->
            val snapshot = upgraded.snapshot()
            assertEquals(1L, snapshot.revision)
            assertEquals(ConversationId("old"), snapshot.workspace.activeConversationId)
            assertEquals("Historial previo", snapshot.workspace.conversations.single().title)
            val draft = upgraded.draftSnapshot(ConversationId("old"))!!
            assertEquals("", draft.text)
            assertEquals(0L, draft.revision)
            assertTrue(draft.authorityToken.isNotBlank())
            assertEquals(1, upgraded.backup.snapshot().receipts.size)
        }
        BundledSQLiteDriver().open(file()).use { db ->
            db.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(4L, it.getLong(0))
            }
        }
    }

    @Test fun sealedGenerationCannotImplicitlyMigrateV1OnExistingOpen() {
        createLegacyVersionOne()
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore.openExisting(file()).close()
        }
        BundledSQLiteDriver().open(file()).use { db ->
            db.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(1L, it.getLong(0))
            }
        }
    }
}
