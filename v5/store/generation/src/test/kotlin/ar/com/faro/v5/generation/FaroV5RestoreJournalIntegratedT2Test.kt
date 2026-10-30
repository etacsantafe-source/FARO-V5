package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exact integration across C106 stage-only five-store restore and C107 durable
 * journal admission. No real host/V2 activation, no pointer switch.
 */
class FaroV5RestoreJournalIntegratedT2Test {
    @TempDir lateinit var root: Path
    private val op = MaintenanceOperationId("integrated-restore-c108")
    private val password = "integration-portable-backup-password".toCharArray()

    private class Lease : MaterialWriteQuiescencePort {
        var enters = 0
        var exits = 0
        var held = false
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            check(!held) { "nested quiescence" }
            held = true
            enters++
            return try {
                block()
            } finally {
                held = false
                exits++
            }
        }
    }

    private class Fixture(
        val staged: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String) : Fixture {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("c108-" + name), "test-only-journal-integrated", 108L,
        )
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor)
        return Fixture(staged, FaroV5GenerationStoreGraph.openValidatedV2(staged))
    }

    private fun sealedBackup(source: Fixture): ByteArray {
        val workspace = source.graph.workspace!!
        val original = workspace.backup.snapshot()
        workspace.backup.restoreOrVerify(original.copy(revision = 9L))
        val set = FaroV5FiveStoreBackupCapture(source.graph, Lease()).capture(
            operationId = MaintenanceOperationId("source-capture-108"),
            backupSetId = BackupSetId("c108-five-store-set"),
            productVersion = "5.0-test",
            createdAtEpochMillis = 108L,
        )
        return FaroV5FiveStorePortableBackupCrypto().seal(set, password)
    }

    private fun prepareJournal(source: Fixture, target: Fixture, phase: BootstrapPhase =
        BootstrapPhase.STAGED) {
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.compareAndSet(null, source.staged.descriptor.generationId) != null)
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operationId = op,
            kind = MaintenanceOperationKind.RESTORE,
            previousGenerationId = source.staged.descriptor.generationId,
            targetGenerationId = target.staged.descriptor.generationId,
            phase = phase,
        ))
    }

    private fun restore(
        target: Fixture,
        archive: ByteArray,
        lease: Lease,
        after: (FiveStoreStagedRestoreSection) -> Unit = {},
    ): FiveStoreStagedRestoreResult {
        val admission = FaroV5RestoreJournalAdmission(
            FileBootstrapJournal(root), FileActiveGenerationPointer(root),
            target.staged.descriptor.generationId, lease,
        )
        return FaroV5FiveStoreStagedRestore(
            root, target.staged, target.graph, admission, after,
            archiveIdentity = FileRestoreArchiveIdentity(root),
        ).restoreAuthenticated(op, archive, password)
    }

    @Test fun exactJournalAllowsFullFiveStoreStagingWithoutPointerOrJournalSwitch() {
        fresh("source").use { source ->
            val bytes = sealedBackup(source)
            fresh("target").use { target ->
                prepareJournal(source, target)
                val lease = Lease()
                assertTrue(restore(target, bytes, lease) is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(9L, target.graph.workspace!!.backup.snapshot().revision)
                assertEquals(source.graph.reality.backup.snapshot(), target.graph.reality.backup.snapshot())
                assertEquals(source.graph.commercialEvidence.backup.snapshot(),
                    target.graph.commercialEvidence.backup.snapshot())
                assertEquals(source.graph.webEvidence.backup.snapshot(),
                    target.graph.webEvidence.backup.snapshot())
                assertEquals(source.graph.auxiliary.backup.snapshot(),
                    target.graph.auxiliary.backup.snapshot())
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
                assertEquals(BootstrapPhase.STAGED, FileBootstrapJournal(root).read(op)!!.phase)
                assertEquals(1, lease.enters)
                assertEquals(1, lease.exits)
            }
        }
    }

    @Test fun missingJournalBlocksFiveStoreStagingBeforeFirstStoreMutation() {
        fresh("source").use { source ->
            val bytes = sealedBackup(source)
            fresh("target").use { target ->
                FileActiveGenerationPointer(root).compareAndSet(null, source.staged.descriptor.generationId)
                val before = target.graph.workspace!!.backup.snapshot()
                val lease = Lease()
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE,
                ), restore(target, bytes, lease))
                assertEquals(before, target.graph.workspace!!.backup.snapshot())
                assertEquals(1, lease.enters)
                assertEquals(1, lease.exits)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
            }
        }
    }

    @Test fun injectedCrashAndSameOperationRetryStaysQuarantined() {
        fresh("source").use { source ->
            val bytes = sealedBackup(source)
            fresh("target").use { target ->
                prepareJournal(source, target)
                val lease = Lease()
                val failure = restore(target, bytes, lease) { section ->
                    if (section == FiveStoreStagedRestoreSection.REALITY)
                        error("injected first-stage crash")
                }
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                ), failure)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
                assertEquals(BootstrapPhase.STAGED, FileBootstrapJournal(root).read(op)!!.phase)
                // Reopen durable journal and pointer ports inside a new admission wrapper.
                assertTrue(restore(target, bytes, lease) is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(9L, target.graph.workspace!!.backup.snapshot().revision)
                assertEquals(2, lease.enters)
                assertEquals(2, lease.exits)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
            }
        }
    }
}
