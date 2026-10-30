package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStoreStagedRestoreT2Test {
    @TempDir lateinit var root: Path
    private val password = "restoration-secure-password".toCharArray()
    private val operation = MaintenanceOperationId("isolated-restore-test")

    private class Lease(private val reject: Boolean = false) : MaterialWriteQuiescencePort {
        var calls = 0
        var releases = 0

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            calls++
            if (reject) error("quiescence cannot be acquired")
            return try {
                block()
            } finally {
                releases++
            }
        }
    }

    private data class Fresh(
        val staged: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String, base: Path = root): Fresh {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("restore-" + name), "isolated-five-store-restore", 111L,
        )
        val staged = FaroV5WorkspaceV2StageOwner(base).stageFreshAndVerify(descriptor)
        return Fresh(staged, FaroV5GenerationStoreGraph.openValidatedV2(staged))
    }

    private fun archive(source: Fresh): Pair<ByteArray, FaroV5FiveStoreSemanticSet> {
        // A visible nonempty Workspace revision proves that fifth-store data
        // actually changes on successful restore, rather than an empty no-op.
        val workspace = source.graph.workspace!!
        val original = workspace.backup.snapshot()
        workspace.backup.restoreOrVerify(original.copy(revision = 9L))
        val set = FaroV5FiveStoreBackupCapture(source.graph, Lease()).capture(
            operationId = MaintenanceOperationId("capture-source"),
            backupSetId = BackupSetId("restore-source-set"),
            productVersion = "5.0-test",
            createdAtEpochMillis = 222L,
        )
        return FaroV5FiveStorePortableBackupCrypto().seal(set, password) to set
    }

    private fun restore(
        target: Fresh, encrypted: ByteArray, lease: Lease = Lease(),
        after: (FiveStoreStagedRestoreSection) -> Unit = {},
    ): FiveStoreStagedRestoreResult =
        FaroV5FiveStoreStagedRestore(root, target.staged, target.graph, lease, after,
            FileRestoreArchiveIdentity(root))
            .restoreAuthenticated(operation, encrypted, password)

    @Test fun fiveStoreAuthenticatedRestoreIsStagedOnlyAndIdempotent() {
        fresh("source").use { source ->
            val (encrypted, expected) = archive(source)
            fresh("target").use { target ->
                val lease = Lease()
                assertTrue(restore(target, encrypted, lease) is
                    FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(expected.workspace, target.graph.workspace!!.backup.snapshot())
                assertEquals(expected.fourStore.reality, target.graph.reality.backup.snapshot())
                assertEquals(expected.fourStore.commercialEvidence,
                    target.graph.commercialEvidence.backup.snapshot())
                assertEquals(expected.fourStore.webEvidence,
                    target.graph.webEvidence.backup.snapshot())
                assertEquals(expected.fourStore.auxiliary,
                    target.graph.auxiliary.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
                assertTrue(restore(target, encrypted, lease) is
                    FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(2, lease.calls)
                assertEquals(2, lease.releases)
            }
        }
    }

    @Test fun tamperedArchiveCannotChangeEvenTheWorkspaceRevision() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            encrypted[encrypted.lastIndex] =
                (encrypted.last().toInt() xor 1).toByte()
            fresh("target").use { target ->
                val before = target.graph.workspace!!.backup.snapshot()
                val result = restore(target, encrypted)
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                        FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
                    ), result,
                )
                assertEquals(before, target.graph.workspace!!.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun unpausedWritesRefuseRestoreWithoutPartialStaging() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                val lease = Lease(reject = true)
                val before = target.graph.workspace!!.backup.snapshot()
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE,
                    ), restore(target, encrypted, lease),
                )
                assertEquals(1, lease.calls)
                assertEquals(0, lease.releases)
                assertEquals(before, target.graph.workspace!!.backup.snapshot())
            }
        }
    }

    @Test fun sourceGenerationCanNeverBeItsOwnRestoreTarget() {
        fresh("source").use { source ->
            val (encrypted, expected) = archive(source)
            assertEquals(
                FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
                ), restore(source, encrypted),
            )
            assertEquals(expected.workspace, source.graph.workspace!!.backup.snapshot())
        }
    }

    @Test fun crashDuringStagingCannotSwitchActiveGeneration() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                val before = target.graph.workspace!!.backup.snapshot()
                val result = restore(target, encrypted, after = { section ->
                    if (section == FiveStoreStagedRestoreSection.REALITY)
                        error("injected crash before second store")
                })
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                    ), result,
                )
                assertEquals(before, target.graph.workspace!!.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun activeTargetIsRejectedBeforeTheFirstRestoreWrite() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                val pointer = FileActiveGenerationPointer(root)
                val before = target.graph.workspace!!.backup.snapshot()
                assertTrue(pointer.compareAndSet(null, target.staged.descriptor.generationId) != null)
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED,
                    ), restore(target, encrypted),
                )
                assertEquals(before, target.graph.workspace!!.backup.snapshot())
            }
        }
    }

    @Test fun sameGenerationIdFromDifferentDirectoryCannotImpersonateTargetGraph() {
        fresh("source").use { source ->
            val (encrypted, _) = archive(source)
            fresh("target").use { target ->
                // Same generation ID, but the graph belongs to a different
                // storage root. A string-only generation check is insufficient.
                fresh("target", root.resolve("shadow-root")).use { shadow ->
                    val before = shadow.graph.workspace!!.backup.snapshot()
                    val result = FaroV5FiveStoreStagedRestore(
                        root, target.staged, shadow.graph, Lease(),
                        archiveIdentity = FileRestoreArchiveIdentity(root),
                    ).restoreAuthenticated(operation, encrypted, password)
                    assertEquals(
                        FiveStoreStagedRestoreResult.Blocked(
                            FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED,
                        ), result,
                    )
                    assertEquals(before, shadow.graph.workspace!!.backup.snapshot())
                    assertNull(FileActiveGenerationPointer(root).read())
                }
            }
        }
    }
}
