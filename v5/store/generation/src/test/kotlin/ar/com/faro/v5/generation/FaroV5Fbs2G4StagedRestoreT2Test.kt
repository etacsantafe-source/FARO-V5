package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Isolated JVM stage scope only: NO product host grant or G4 user export. */
class FaroV5Fbs2G4StagedRestoreT2Test {
    @TempDir lateinit var root: Path
    private val password = "isolated-g4-stage-fixture".toCharArray()

    private class Lease(val denied: Boolean = false) : MaterialWriteQuiescencePort {
        var calls = 0
        var releases = 0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T {
            calls++
            if (denied) error("five-owner lease denied")
            return try { block() } finally { releases++ }
        }
    }

    private data class Fresh(
        val stage: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String): Fresh {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("g4-staged-" + name), "g4-stage-unit-fixture", 111L,
        )
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor)
        return Fresh(stage, FaroV5GenerationStoreGraph.openValidatedV2(stage))
    }

    private fun archive(source: Fresh): Pair<ByteArray, FaroV5FiveStoreSemanticSet> {
        source.graph.reality.run(TransactionId("g4-source-seed")) { scope ->
            source.graph.reality.objectivePersistence.upsert(
                ObjectiveRecord(
                    ObjectiveId.of("g4-objective"), ObjectiveTitle.of("Reserva"),
                    ObjectiveDetail.of("Ahorro"), ObjectiveStatus.ACTIVE, 100L,
                ), scope,
            )
            source.graph.reality.matterPersistence.upsert(
                MatterRecord(
                    MatterId.of("g4-matter"), MatterTitle.of("Vencimiento"),
                    MatterDetail.of("Revisar pago"), MatterStatus.WAITING, 200L, 100L,
                ), scope,
            )
            AtomicApplyTransactionDecision.Commit(Unit)
        }
        val w = source.graph.workspace!!
        w.backup.restoreOrVerify(w.backup.snapshot().copy(revision = 9L))
        val data = FaroV5FiveStoreBackupCapture(source.graph, Lease()).capture(
            MaintenanceOperationId("capture-g4-unit"), BackupSetId("g4-stage-set"),
            "5.0-stage", 300L,
        )
        assertEquals(1, data.g4Domains!!.objectives.objectives.size)
        assertEquals(1, data.g4Domains!!.matters.matters.size)
        return FaroV5FiveStorePortableBackupCrypto().seal(data, password) to data
    }

    private fun stageRestore(
        target: Fresh,
        encrypted: ByteArray,
        operation: MaintenanceOperationId = MaintenanceOperationId("g4-stage-restore"),
        lease: Lease = Lease(),
        after: (FiveStoreStagedRestoreSection) -> Unit = {},
    ): FiveStoreStagedRestoreResult =
        FaroV5FiveStoreStagedRestore(
            root, target.stage, target.graph, lease, after,
            FileRestoreArchiveIdentity(root),
        ).restoreAuthenticatedG4StageOnly(operation, encrypted, password)

    @Test fun restoresG4AndFiveStoresThenExactReplay() {
        fresh("source").use { source ->
            val (encrypted, backup) = archive(source)
            fresh("target").use { target ->
                // Product-facing API must remain explicitly fenced.
                val public = FaroV5FiveStoreStagedRestore(
                    root, target.stage, target.graph, Lease(),
                    archiveIdentity = FileRestoreArchiveIdentity(root),
                ).restoreAuthenticated(MaintenanceOperationId("g4-public-denied"), encrypted, password)
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.UNSUPPORTED_G4_ARCHIVE,
                    ), public,
                )
                val lease = Lease()
                assertTrue(stageRestore(target, encrypted, lease = lease) is
                    FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(backup.fourStore.reality, target.graph.reality.fbs2Backup.snapshot().legacy)
                assertEquals(backup.g4Domains, target.graph.reality.fbs2Backup.snapshot().g4Domains)
                assertEquals(backup.workspace, target.graph.workspace!!.backup.snapshot())
                assertEquals(backup.fourStore.commercialEvidence,
                    target.graph.commercialEvidence.backup.snapshot())
                assertEquals(backup.fourStore.webEvidence,
                    target.graph.webEvidence.backup.snapshot())
                assertEquals(backup.fourStore.auxiliary,
                    target.graph.auxiliary.backup.snapshot())
                assertTrue(stageRestore(target, encrypted, lease = lease) is
                    FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(2, lease.calls)
                assertEquals(2, lease.releases)
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun exactLegacyRealityPartialCanResumeG4() {
        fresh("source").use { source ->
            val (encrypted, data) = archive(source)
            fresh("target").use { target ->
                target.graph.reality.backup.restoreOrVerify(data.fourStore.reality)
                assertEquals(0L, target.graph.reality.domainBackup.snapshot().matters.revision)
                assertTrue(stageRestore(target, encrypted) is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(data.g4Domains, target.graph.reality.domainBackup.snapshot())
            }
        }
    }

    @Test fun completedG4RealityCanResumeRemainingFourStores() {
        fresh("source").use { source ->
            val (encrypted, data) = archive(source)
            fresh("target").use { target ->
                target.graph.reality.backup.restoreOrVerify(data.fourStore.reality)
                target.graph.reality.domainBackupRestore.restoreOrVerify(data.g4Domains!!)
                assertTrue(stageRestore(target, encrypted) is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(data.workspace, target.graph.workspace!!.backup.snapshot())
                assertEquals(data.g4Domains, target.graph.reality.domainBackup.snapshot())
            }
        }
    }

    @Test fun foreignNonfreshG4CannotBeReplaced() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                val existing = target.graph.reality.domainBackup.snapshot()
                val foreign = existing.copy(
                    objectives = existing.objectives.copy(revision = 1L),
                )
                target.graph.reality.domainBackupRestore.restoreOrVerify(foreign)
                val beforeLegacy = target.graph.reality.fbs2Backup.snapshot().legacy
                val beforeWorkspace = target.graph.workspace!!.backup.snapshot()
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                    ), stageRestore(target, encrypted),
                )
                assertEquals(foreign, target.graph.reality.domainBackup.snapshot())
                assertEquals(beforeLegacy, target.graph.reality.fbs2Backup.snapshot().legacy)
                assertEquals(beforeWorkspace, target.graph.workspace!!.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun crashAfterPairedRealityRestoresByExactReplay() {
        fresh("source").use { source ->
            val (encrypted, data) = archive(source)
            fresh("target").use { target ->
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                    ), stageRestore(target, encrypted, after = { section ->
                        if (section == FiveStoreStagedRestoreSection.REALITY)
                            error("test-only crash before Evidence")
                    }),
                )
                assertEquals(data.g4Domains, target.graph.reality.domainBackup.snapshot())
                assertTrue(stageRestore(target, encrypted) is FiveStoreStagedRestoreResult.StagedVerified)
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun pinnedOperationRejectsSubstitutedAuthenticatedArchive() {
        fresh("source").use { source ->
            val (encrypted, data) = archive(source)
            fresh("target").use { target ->
                assertTrue(stageRestore(target, encrypted) is FiveStoreStagedRestoreResult.StagedVerified)
                val substitute = FaroV5FiveStorePortableBackupCrypto().seal(data, password)
                assertFalse(substitute.contentEquals(encrypted))
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT,
                    ), stageRestore(target, substitute),
                )
                assertEquals(data.g4Domains, target.graph.reality.domainBackup.snapshot())
            }
        }
    }

    @Test fun corruptArchiveDeniedLeaseAndSelfTargetAllFailClosed() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                val damaged = encrypted.copyOf()
                damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                        FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
                    ), stageRestore(target, damaged,
                        operation = MaintenanceOperationId("g4-bad-archive")),
                )
                val denied = Lease(denied = true)
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE,
                    ), stageRestore(target, encrypted,
                        operation = MaintenanceOperationId("g4-denied"), lease = denied),
                )
                assertEquals(0, denied.releases)
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
                    ), stageRestore(source, encrypted,
                        operation = MaintenanceOperationId("g4-self")),
                )
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }
}
