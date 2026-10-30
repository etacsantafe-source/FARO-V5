package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FilePortableBackupArchiveStore
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

/** Linux/glibc bundled-SQLite T2. No live Android activation or pointer switch. */
class FaroV5StagedRestoreJournalValidationT2Test {
    @TempDir lateinit var root: Path
    private val op = MaintenanceOperationId("c119-validate-stage")
    private val password = "c119-native-restore-passphrase".toCharArray()

    private class Lease : MaterialWriteQuiescencePort {
        var entries = 0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T {
            entries++
            return block()
        }
    }

    private class Fixture(
        val staged: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String): Fixture {
        val d = StorageGenerationDescriptor(
            StorageGenerationId("c119-" + name), "five-store-v2-schema", 119L
        )
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(d)
        return Fixture(staged, FaroV5GenerationStoreGraph.openValidatedV2(staged))
    }

    private fun archive(source: Fixture): ByteArray {
        val original = source.graph.workspace!!.backup.snapshot()
        source.graph.workspace!!.backup.restoreOrVerify(original.copy(revision = 9L))
        val set = FaroV5FiveStoreBackupCapture(source.graph, Lease()).capture(
            operationId = MaintenanceOperationId("c119-backup-capture"),
            backupSetId = BackupSetId("c119-five-store-backup"),
            productVersion = "5.0-test",
            createdAtEpochMillis = 119L
        )
        return FaroV5FiveStorePortableBackupCrypto().seal(set, password)
    }

    private fun prepare(source: Fixture, target: Fixture) {
        check(FileActiveGenerationPointer(root).compareAndSet(
            null, source.staged.descriptor.generationId
        ) != null)
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operationId = op,
            kind = MaintenanceOperationKind.RESTORE,
            previousGenerationId = source.staged.descriptor.generationId,
            targetGenerationId = target.staged.descriptor.generationId,
            phase = BootstrapPhase.STAGED
        ))
    }

    private fun validator(target: Fixture, lease: Lease = Lease()) =
        FaroV5StagedRestoreJournalValidator(
            root, target.staged, target.graph,
            FileBootstrapJournal(root), FileActiveGenerationPointer(root),
            FileRestoreArchiveIdentity(root), lease
        )

    @Test fun authenticatedExactFiveStoreStageAdvancesOnlyJournalAndIsIdempotent() {
        fresh("normal-source").use { source ->
            val encrypted = archive(source)
            fresh("normal-target").use { target ->
                prepare(source, target)
                val admission = FaroV5RestoreJournalAdmission(
                    FileBootstrapJournal(root), FileActiveGenerationPointer(root),
                    target.staged.descriptor.generationId, Lease()
                )
                val staged = FaroV5FiveStoreStagedRestore(
                    root, target.staged, target.graph, admission,
                    archiveIdentity = FileRestoreArchiveIdentity(root)
                ).restoreAuthenticated(op, encrypted, password)
                assertTrue(staged is FiveStoreStagedRestoreResult.StagedVerified)
                val verification = validator(target)
                assertEquals(
                    FiveStoreJournalValidationResult.Validated(
                        target.staged.descriptor.generationId.value, false
                    ),
                    verification.validateAuthenticatedStage(op, encrypted, password)
                )
                assertEquals(BootstrapPhase.VALIDATED, FileBootstrapJournal(root).read(op)!!.phase)
                assertEquals(false, FileBootstrapJournal(root).read(op)!!.materialWritesAccepted)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
                assertEquals(
                    FiveStoreJournalValidationResult.Validated(
                        target.staged.descriptor.generationId.value, true
                    ),
                    verification.validateAuthenticatedStage(op, encrypted, password)
                )
            }
        }
    }

    @Test fun missingPinnedArchiveRefusesJournalValidationWithoutPublishingOrSwitch() {
        fresh("no-pin-source").use { source ->
            val encrypted = archive(source)
            fresh("no-pin-target").use { target ->
                prepare(source, target)
                assertEquals(
                    FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.EXISTING_ARCHIVE_PIN_INVALID
                    ), validator(target).validateAuthenticatedStage(op, encrypted, password)
                )
                assertEquals(BootstrapPhase.STAGED, FileBootstrapJournal(root).read(op)!!.phase)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
            }
        }
    }

    @Test fun incompleteFiveStoreSnapshotsBlockValidatedEvenWithMatchingArchivePin() {
        fresh("partial-source").use { source ->
            val encrypted = archive(source)
            fresh("partial-target").use { target ->
                prepare(source, target)
                FileRestoreArchiveIdentity(root).pinOrVerify(
                    op, target.staged.descriptor.generationId, encrypted
                )
                assertEquals(
                    FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.STAGED_CONTENT_MISMATCH
                    ), validator(target).validateAuthenticatedStage(op, encrypted, password)
                )
                assertEquals(BootstrapPhase.STAGED, FileBootstrapJournal(root).read(op)!!.phase)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
            }
        }
    }
    @Test fun consentedPrivateFaroBak2ExportRestoresAndValidatesOnlyQuarantinedStage() {
        fresh("consented-source").use { source ->
            val before = source.graph.workspace!!.backup.snapshot()
            source.graph.workspace!!.backup.restoreOrVerify(before.copy(revision = 11L))
            val privateStore = FilePortableBackupArchiveStore(root.resolve("private-consented-stage"))
            val captureLease = Lease()
            val prepared = FaroV5FiveStoreExportPreparation(
                source.graph, captureLease, privateStore,
            ) { true }.prepare(
                operationId = MaintenanceOperationId("c137-consented-capture"),
                backupSetId = BackupSetId("c137-consented-backup"),
                productVersion = "5.0-consented",
                createdAtEpochMillis = 137L,
                passphrase = password,
                consent = FiveStoreBackupExportConsent(true, true),
                effectIdentity = "c137-export-effect",
            )
            assertEquals(1, captureLease.entries)
            assertTrue(privateStore.verify(prepared.artifact))
            val encrypted = privateStore.readBytes(prepared.artifact)
            fresh("consented-target").use { target ->
                prepare(source, target)
                val admission = FaroV5RestoreJournalAdmission(
                    FileBootstrapJournal(root), FileActiveGenerationPointer(root),
                    target.staged.descriptor.generationId, Lease()
                )
                val staged = FaroV5FiveStoreStagedRestore(
                    root, target.staged, target.graph, admission,
                    archiveIdentity = FileRestoreArchiveIdentity(root)
                ).restoreAuthenticated(op, encrypted, password)
                assertTrue(staged is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(
                    FiveStoreJournalValidationResult.Validated(
                        target.staged.descriptor.generationId.value, false
                    ),
                    validator(target).validateAuthenticatedStage(op, encrypted, password)
                )
                assertEquals(source.graph.workspace!!.backup.snapshot(),
                    target.graph.workspace!!.backup.snapshot())
                assertEquals(BootstrapPhase.VALIDATED, FileBootstrapJournal(root).read(op)!!.phase)
                assertEquals(false, FileBootstrapJournal(root).read(op)!!.materialWritesAccepted)
                assertEquals(source.staged.descriptor.generationId,
                    FileActiveGenerationPointer(root).read()!!.generationId)
                assertEquals(
                    FiveStoreJournalValidationResult.Validated(
                        target.staged.descriptor.generationId.value, true
                    ),
                    validator(target).validateAuthenticatedStage(op, encrypted, password)
                )
            }
        }
    }

}
