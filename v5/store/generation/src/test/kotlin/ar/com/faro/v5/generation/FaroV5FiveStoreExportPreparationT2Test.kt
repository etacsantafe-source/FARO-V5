package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FilePortableBackupArchiveStore
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStoreExportPreparationT2Test {
    @TempDir lateinit var temp: Path
    private val passphrase = "correct horse battery staple".toCharArray()

    private class Lease : MaterialWriteQuiescencePort {
        var calls = 0
        var released = 0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            calls++
            return try { block() } finally { released++ }
        }
    }

    private fun openFive(name: String): FaroV5GenerationStoreGraph {
        val staged = FileStorageGenerationLayout(temp.resolve(name)).stageWorkspaceV2(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("generation-$name"),
                schemaSetFingerprint = "five-store-export-integration",
                createdAtEpochMillis = 1234L,
            )
        )
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        return FaroV5GenerationStoreGraph.openValidatedV2(staged)
    }

    private fun prepare(
        coordinator: FaroV5FiveStoreExportPreparation,
        consent: FiveStoreBackupExportConsent,
    ) = coordinator.prepare(
        operationId = MaintenanceOperationId("five-store-export-operation"),
        backupSetId = BackupSetId("five-store-export-backup"),
        productVersion = "5.0-export",
        createdAtEpochMillis = 54321L,
        passphrase = passphrase,
        consent = consent,
        effectIdentity = "five-store-export-effect",
    )

    @Test fun noConsentOrUncertifiedAuthorityNeverCapturesOrStages() {
        openFive("denied").use { graph ->
            val lease = Lease()
            val store = FilePortableBackupArchiveStore(temp.resolve("denied-stage"))
            val consent = FiveStoreBackupExportConsent(true, true)
            val allowed = FaroV5FiveStoreExportPreparation(graph, lease, store) { true }
            assertThrows(IllegalStateException::class.java) {
                prepare(allowed, FiveStoreBackupExportConsent(true, false))
            }
            val inactive = FaroV5FiveStoreExportPreparation(graph, lease, store) { false }
            assertThrows(IllegalStateException::class.java) {
                prepare(inactive, consent)
            }
            assertEquals(0, lease.calls)
            assertEquals(0, lease.released)
            assertEquals(0L, Files.list(temp.resolve("denied-stage")).use { it.count() })
        }
    }

    @Test fun explicitConsentCapturesFiveRealStoresAndStagesOnlyAuthenticatedArchive() {
        openFive("approved").use { graph ->
            val lease = Lease()
            val store = FilePortableBackupArchiveStore(temp.resolve("approved-stage"))
            val coordinator = FaroV5FiveStoreExportPreparation(graph, lease, store) { true }
            val source = prepare(coordinator, FiveStoreBackupExportConsent(true, true))
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertTrue(store.verify(source.artifact))
            assertEquals("application/octet-stream", source.mimeType)
            val output = ByteArrayOutputStream()
            assertEquals(source.byteCount, source.copyTo(output))
            val encrypted = output.toByteArray()
            assertEquals("FAROBAK2", String(encrypted.copyOfRange(0, 8), StandardCharsets.US_ASCII))
            val opened = assertInstanceOf(
                FiveStorePortableBackupOpenResult.Opened::class.java,
                FaroV5FiveStorePortableBackupCrypto().open(encrypted, passphrase),
            )
            assertEquals(graph.workspace!!.backup.snapshot(), opened.backupSet.workspace)
            assertEquals(graph.reality.backup.snapshot(), opened.backupSet.fourStore.reality)
            assertEquals(graph.commercialEvidence.backup.snapshot(),
                opened.backupSet.fourStore.commercialEvidence)
            assertEquals(graph.webEvidence.backup.snapshot(), opened.backupSet.fourStore.webEvidence)
            assertEquals(graph.auxiliary.backup.snapshot(), opened.backupSet.fourStore.auxiliary)
        }
    }

    @Test fun authorityDriftAfterCaptureNeverStagesAnExportableArtifact() {
        openFive("drift").use { graph ->
            val lease = Lease()
            val store = FilePortableBackupArchiveStore(temp.resolve("drift-stage"))
            var checks = 0
            val coordinator = FaroV5FiveStoreExportPreparation(graph, lease, store) {
                checks++
                checks == 1
            }
            assertThrows(IllegalStateException::class.java) {
                prepare(coordinator, FiveStoreBackupExportConsent(true, true))
            }
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertEquals(0L, Files.list(temp.resolve("drift-stage")).use { it.count() })
        }
    }
}
