package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.RestoreArchiveIdentityPort
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.matters.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Fbs2G4RestoreFenceTest {
    @TempDir lateinit var root: Path
    private val passphrase = "private-g4-fail-closed-fixture".toCharArray()
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T = block() // Only a JVM fixture; never grants production admission.
    }

    private fun stage(name: String): Pair<ar.com.faro.v5.maintenance.StagedStorageGeneration,
        FaroV5GenerationStoreGraph> {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("g4-fence-" + name), "g4-restore-rejection-test", 250L,
        )
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor)
        return staged to FaroV5GenerationStoreGraph.openValidatedV2(staged)
    }

    @Test
    fun authenticG4V2ArchiveIsBlockedBeforePinOrAnyStoreMutation() {
        val (sourceStage, source) = stage("source")
        source.use {
            // The v1 portion is captured first, then a G4 owner is populated to
            // exercise a genuine, nonempty typed G4 section in an unsupported v2.
            val legacy = FaroV5FiveStoreBackupCapture(source, lease).capture(
                MaintenanceOperationId("g4-fence-source-capture"),
                BackupSetId("g4-fence-source-backup"),
                "5-isolated", 1000L,
            )
            source.reality.run(TransactionId("tx-fence-g4")) { scope ->
                source.reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("matter-fence"), MatterTitle.of("Pago pendiente"),
                        MatterDetail.of("Comprobación de restauración"),
                        MatterStatus.WAITING, 2_000L, 1_000L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val g4 = source.reality.domainBackup.snapshot()
            assertEquals(1, g4.matters.matters.size)
            val archive = FaroV5FiveStorePortableBackupCrypto()
                .seal(legacy.copy(g4Domains = g4), passphrase)
            assertTrue(FaroV5FiveStorePortableBackupCrypto().open(archive, passphrase)
                is FiveStorePortableBackupOpenResult.Opened)

            val (targetStage, target) = stage("target")
            target.use {
                val reality = target.reality.backup.snapshot()
                val domain = target.reality.domainBackup.snapshot()
                val workspace = target.workspace!!.backup.snapshot()
                val commercial = target.commercialEvidence.backup.snapshot()
                val web = target.webEvidence.backup.snapshot()
                val auxiliary = target.auxiliary.backup.snapshot()
                var pins = 0
                val identity = RestoreArchiveIdentityPort { _, _, _ -> pins++ }
                val result = FaroV5FiveStoreStagedRestore(
                    root, targetStage, target, lease,
                    archiveIdentity = identity,
                ).restoreAuthenticated(
                    MaintenanceOperationId("g4-fence-restore"), archive, passphrase,
                )
                assertEquals(
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.UNSUPPORTED_G4_ARCHIVE,
                    ), result,
                )
                assertEquals(0, pins)
                assertEquals(reality, target.reality.backup.snapshot())
                assertEquals(domain, target.reality.domainBackup.snapshot())
                assertEquals(workspace, target.workspace!!.backup.snapshot())
                assertEquals(commercial, target.commercialEvidence.backup.snapshot())
                assertEquals(web, target.webEvidence.backup.snapshot())
                assertEquals(auxiliary, target.auxiliary.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
                assertTrue(target.verifyHealthy())
            }
            assertEquals("g4-fence-source", sourceStage.descriptor.generationId.value)
        }
    }
}
