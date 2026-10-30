package ar.com.faro.v5.maintenance

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PortableBackupArtifactT2Test {
    @Test
    fun `staging preserves bytes digest and supports verified streaming copy`(
        @TempDir tempDir: Path,
    ) {
        val bytes = "encrypted-backup-payload".toByteArray()
        val store = FilePortableBackupArchiveStore(tempDir)

        val staged = store.stageBytes(bytes, stagedAtEpochMillis = 100L)

        assertTrue(store.verify(staged))
        assertEquals(staged.handle.value, staged.sha256Hex)
        assertEquals(bytes.size.toLong(), staged.byteCount)
        assertArrayEquals(bytes, store.readBytes(staged))

        val output = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), store.copyTo(staged, output))
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun `tampered staged archive fails verification`(
        @TempDir tempDir: Path,
    ) {
        val store = FilePortableBackupArchiveStore(tempDir)
        val staged =
            store.stageBytes(
                "archive-original".toByteArray(),
                stagedAtEpochMillis = 100L,
            )
        val file =
            Files.list(tempDir).use { stream ->
                stream
                    .filter { it.fileName.toString().endsWith(".farobak") }
                    .findFirst()
                    .orElseThrow()
            }
        Files.write(file, "archive-tampered".toByteArray())

        assertFalse(store.verify(staged))
    }

    @Test
    fun `reopen removes interrupted staging temp without touching published archive`(
        @TempDir tempDir: Path,
    ) {
        val first = FilePortableBackupArchiveStore(tempDir)
        val staged =
            first.stageBytes(
                "published".toByteArray(),
                stagedAtEpochMillis = 100L,
            )
        val interrupted = tempDir.resolve("incoming.tmp-deadprocess")
        Files.write(interrupted, "partial".toByteArray())

        val reopened = FilePortableBackupArchiveStore(tempDir)

        assertFalse(Files.exists(interrupted))
        assertTrue(reopened.verify(staged))
    }

    @Test
    fun `backup external write interruption becomes unknown after reopen`(
        @TempDir tempDir: Path,
    ) {
        val archiveStore =
            FilePortableBackupArchiveStore(tempDir.resolve("artifacts"))
        val artifact =
            archiveStore.stageBytes(
                "encrypted".toByteArray(),
                stagedAtEpochMillis = 100L,
            )
        val source =
            PreparedBackupExternalWriteSource(
                effectIdentity = "backup/export/process-death",
                artifact = artifact,
                store = archiveStore,
            )
        val attemptRoot = tempDir.resolve("attempts")
        val coordinator =
            BackupExternalEffectCoordinator(
                FileBackupExternalAttemptStore(attemptRoot)
            )

        coordinator.begin(source)
        coordinator.markWriteStarted(source)

        val reopened =
            BackupExternalEffectCoordinator(
                FileBackupExternalAttemptStore(attemptRoot)
            )
        val receipts = reopened.reconcileInterruptedWrites()

        assertEquals(1, receipts.size)
        assertEquals(
            BackupExternalWriteStatus.EFFECT_UNKNOWN,
            receipts.single().status,
        )
        assertEquals(
            "process_interrupted_after_write_start",
            receipts.single().reason,
        )
    }

    @Test
    fun `backup export picker cancel is durable no-write outcome`(
        @TempDir tempDir: Path,
    ) {
        val archiveStore =
            FilePortableBackupArchiveStore(tempDir.resolve("artifacts"))
        val artifact =
            archiveStore.stageBytes(
                "encrypted".toByteArray(),
                stagedAtEpochMillis = 100L,
            )
        val source =
            PreparedBackupExternalWriteSource(
                effectIdentity = "backup/export/cancel",
                artifact = artifact,
                store = archiveStore,
            )
        val attempts = FileBackupExternalAttemptStore(tempDir.resolve("attempts"))
        val coordinator = BackupExternalEffectCoordinator(attempts)

        coordinator.begin(source)
        val receipt = coordinator.pickerCancelled(source)

        assertEquals(BackupExternalWriteStatus.CANCELLED, receipt.status)
        assertEquals("picker_cancelled", receipt.reason)
        assertEquals(
            BackupExternalAttemptPhase.TERMINAL,
            attempts.read(source.effectIdentity)!!.phase,
        )
    }
}
