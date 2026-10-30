package ar.com.faro.v5.workspace

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Existing generation must NEVER create an absent SQLite file or uninitialized schema. */
class WorkspaceExistingSchemaT2Test {
    @TempDir lateinit var root: Path

    @Test fun reopeningMaterializedWorkspacePreservesAuthorityAndRevision() {
        val file = root.resolve("workspace.db")
        ConversationWorkspaceStore(file.toString()).use {
            assertEquals(0L, it.snapshot().revision)
        }
        val beforeSize = Files.size(file)
        ConversationWorkspaceStore.openExisting(file.toString()).use {
            assertEquals(0L, it.snapshot().revision)
        }
        assertTrue(Files.size(file) > 0L)
        assertTrue(beforeSize > 0L)
    }

    @Test fun reopeningDoesNotCreateMissingDatabase() {
        val file = root.resolve("not-here.db")
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore.openExisting(file.toString())
        }
        assertFalse(Files.exists(file))
    }

    @Test fun reopeningCannotInitializeEmptyDatabaseFile() {
        val file = Files.createFile(root.resolve("empty.db"))
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore.openExisting(file.toString())
        }
        assertEquals(0L, Files.size(file))
    }

    @Test fun reopeningRejectsSymlinkAndLeavesExternalFileUntouched() {
        val outside = root.resolve("outside.db")
        ConversationWorkspaceStore(outside.toString()).close()
        val link = root.resolve("symlink.db")
        Files.createSymbolicLink(link, outside)
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore.openExisting(link.toString())
        }
        assertTrue(Files.isSymbolicLink(link))
        assertTrue(Files.isRegularFile(outside))
    }
}
