package ar.com.faro.v5.maintenance

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BackupExternalWriteT2Test {
    @Test
    fun `started backup write cannot be entered again and blindly replayed`(
        @TempDir tempDir: Path,
    ) {
        val source = source(tempDir, "backup/no-blind-replay")
        val coordinator =
            BackupExternalEffectCoordinator(
                FileBackupExternalAttemptStore(tempDir)
            )

        coordinator.begin(source)
        coordinator.markWriteStarted(source)

        assertThrows(IllegalStateException::class.java) {
            coordinator.markWriteStarted(source)
        }
    }

    @Test
    fun `interrupted backup write becomes effect unknown on recovery`(
        @TempDir tempDir: Path,
    ) {
        val source = source(tempDir, "backup/interrupted")
        val store = FileBackupExternalAttemptStore(tempDir)
        val coordinator = BackupExternalEffectCoordinator(store)

        coordinator.begin(source)
        coordinator.markWriteStarted(source)

        val receipts =
            BackupExternalEffectCoordinator(
                FileBackupExternalAttemptStore(tempDir)
            ).reconcileInterruptedWrites()

        assertEquals(1, receipts.size)
        assertEquals(BackupExternalWriteStatus.EFFECT_UNKNOWN, receipts.single().status)
        assertEquals("process_interrupted_after_write_start", receipts.single().reason)
        assertTrue(FileBackupExternalAttemptStore(tempDir).pending().isEmpty())
    }

    @Test
    fun `abandoned picker before provider access is cancelled on process restart`(
        @TempDir tempDir: Path,
    ) {
        val source = source(tempDir, "backup/abandoned-picker")
        BackupExternalEffectCoordinator(FileBackupExternalAttemptStore(tempDir)).begin(source)

        val receipts = BackupExternalEffectCoordinator(
            FileBackupExternalAttemptStore(tempDir)
        ).reconcileInterruptedWrites()

        assertEquals(1, receipts.size)
        assertEquals(BackupExternalWriteStatus.CANCELLED, receipts.single().status)
        assertEquals("process_interrupted_before_write_start", receipts.single().reason)
        assertTrue(FileBackupExternalAttemptStore(tempDir).pending().isEmpty())
    }

    @Test
    fun `lost consent before provider write is terminal failure not replay`(
        @TempDir tempDir: Path,
    ) {
        val source = source(tempDir, "backup/consent-revoked")
        val coordinator = BackupExternalEffectCoordinator(
            FileBackupExternalAttemptStore(tempDir)
        )
        coordinator.begin(source)

        val receipt = coordinator.failBeforeWrite(
            source, "export_consent_or_generation_changed"
        )
        assertEquals(BackupExternalWriteStatus.FAILED, receipt.status)
        assertEquals("export_consent_or_generation_changed", receipt.reason)
        assertTrue(coordinator.reconcileInterruptedWrites().isEmpty())
        assertThrows(IllegalStateException::class.java) {
            coordinator.markWriteStarted(source)
        }
    }

    private fun source(
        tempDir: Path,
        effectIdentity: String,
    ): PreparedBackupExternalWriteSource {
        val store = FilePortableBackupArchiveStore(tempDir.resolve("backup-artifacts"))
        val artifact =
            store.stageBytes(
                "encrypted-backup".toByteArray(Charsets.UTF_8),
                stagedAtEpochMillis = 1L,
            )
        return PreparedBackupExternalWriteSource(
            effectIdentity = effectIdentity,
            artifact = artifact,
            store = store,
        )
    }
}
