package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Validates immutable archive identity across restarts and interrupted five-store staging. */
class FaroV5RestoreArchiveIdentityT2Test {
    @TempDir lateinit var root: Path
    private val operation = MaintenanceOperationId("c109-restore-op")
    private val previous = StorageGenerationId("c109-previous")
    private val targetId = StorageGenerationId("c109-target")
    private val password = "c109-backup-passphrase-secure".toCharArray()

    private class Lease : MaterialWriteQuiescencePort {
        var enters = 0
        var exits = 0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T {
            enters++
            return try { block() } finally { exits++ }
        }
    }

    private data class Fixture(
        val staged: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String): Fixture {
        val d = StorageGenerationDescriptor(
            StorageGenerationId("c109-" + name), "c109-v2-fbs2", 109L,
        )
        val target = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        return Fixture(target, FaroV5GenerationStoreGraph.openValidatedV2(target))
    }

    private fun archive(source: Fixture): Pair<ByteArray, ByteArray> {
        val original = source.graph.workspace!!.backup.snapshot()
        source.graph.workspace!!.backup.restoreOrVerify(original.copy(revision = 7L))
        val set = FaroV5FiveStoreBackupCapture(source.graph, Lease()).capture(
            operationId = MaintenanceOperationId("c109-capture"),
            backupSetId = BackupSetId("c109-semantic-set"),
            productVersion = "5.0-test",
            createdAtEpochMillis = 109L,
        )
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        return crypto.seal(set, password) to crypto.seal(set, password)
    }

    private fun setupJournal(source: Fixture, target: Fixture) {
        check(FileActiveGenerationPointer(root).compareAndSet(
            null, source.staged.descriptor.generationId,
        ) != null)
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operationId = operation,
            kind = MaintenanceOperationKind.RESTORE,
            previousGenerationId = source.staged.descriptor.generationId,
            targetGenerationId = target.staged.descriptor.generationId,
            phase = BootstrapPhase.STAGED,
        ))
    }

    private fun restore(
        target: Fixture,
        bytes: ByteArray,
        lease: Lease,
        after: (FiveStoreStagedRestoreSection) -> Unit = {},
    ): FiveStoreStagedRestoreResult {
        val gate = FaroV5RestoreJournalAdmission(
            FileBootstrapJournal(root), FileActiveGenerationPointer(root),
            target.staged.descriptor.generationId, lease,
        )
        return FaroV5FiveStoreStagedRestore(
            storageRoot = root,
            target = target.staged,
            graph = target.graph,
            quiescence = gate,
            afterSection = after,
            archiveIdentity = FileRestoreArchiveIdentity(root),
        ).restoreAuthenticated(operation, bytes, password)
    }

    @Test fun exactArchiveIdentityPersistsThroughFilesystemPortReopen() {
        val port = FileRestoreArchiveIdentity(root)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        port.pinOrVerify(operation, targetId, bytes)
        FileRestoreArchiveIdentity(root).pinOrVerify(operation, targetId, bytes)
        assertThrows(IllegalStateException::class.java) {
            FileRestoreArchiveIdentity(root).pinOrVerify(operation, targetId, byteArrayOf(1, 2, 3, 4, 6))
        }
    }

    @Test fun sameArchiveCannotBeReusedForAnotherTargetUnderSameOperation() {
        FileRestoreArchiveIdentity(root).pinOrVerify(operation, targetId, byteArrayOf(1))
        assertThrows(IllegalStateException::class.java) {
            FileRestoreArchiveIdentity(root).pinOrVerify(
                operation, StorageGenerationId("different-target"), byteArrayOf(1),
            )
        }
        FileRestoreArchiveIdentity(root).pinOrVerify(
            MaintenanceOperationId("different-op"), StorageGenerationId("different-target"),
            byteArrayOf(1),
        )
    }

    @Test fun modifiedDurableIdentityIsNotSilentlyTrustedOnReopen() {
        FileRestoreArchiveIdentity(root).pinOrVerify(operation, targetId, byteArrayOf(1, 2))
        val dir = root.resolve("bootstrap").resolve("restore-archive-identities")
        val record = Files.list(dir).use { it.filter { p -> p.fileName.toString().endsWith(".bin") }
            .findFirst().orElseThrow() }
        val tampered = Files.readAllBytes(record)
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        Files.write(record, tampered)
        assertThrows(IllegalStateException::class.java) {
            FileRestoreArchiveIdentity(root).pinOrVerify(operation, targetId, byteArrayOf(1, 2))
        }
    }

    @Test fun substitutedAuthenticatedArchiveAfterCrashBlockedBeforeRemainingStores() {
        fresh("source").use { source ->
            val (original, alternate) = archive(source)
            assertTrue(!original.contentEquals(alternate))
            fresh("target").use { target ->
                setupJournal(source, target)
                val lease = Lease()
                val crash = restore(target, original, lease) { section ->
                    if (section == FiveStoreStagedRestoreSection.REALITY)
                        error("simulate process death after first store")
                }
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                ), crash)
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT,
                ), restore(target, alternate, lease))
                assertEquals(0L, target.graph.workspace!!.backup.snapshot().revision)
                assertTrue(restore(target, original, lease) is
                    FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(7L, target.graph.workspace!!.backup.snapshot().revision)
                assertEquals(3, lease.enters)
                assertEquals(3, lease.exits)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
                assertEquals(BootstrapPhase.STAGED, FileBootstrapJournal(root).read(operation)!!.phase)
            }
        }
    }
}
