package ar.com.faro.v5.app

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.generation.*
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.platform.android.backup.AndroidBackupSafAdapter
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.util.function.Supplier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S2-only quarantined Android transport integration. The destination is a
 * private file URI to exercise ContentResolver, NOT an actual SAF provider or
 * user picker. No shipping export gate is bypassed and V2 stays OFF.
 * Full F3 certification still needs a real SAF picker and restore journal.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5F3QuarantinedSafRoundTripInstrumentationTest {
    @Test fun fiveOwnerEncryptedQuarantineRoundTripThroughAndroidResolverKeepsV1() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f3-roundtrip-")
        val password = "synthetic-f3-quarantine-only-2026".toCharArray()
        try {
            val activeV1 = Files.createDirectory(root.resolve("active-v1"))
            Files.write(activeV1.resolve("sentinel"), "V1-protected".toByteArray(Charsets.UTF_8))
            val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
                StorageGenerationDescriptor(
                    StorageGenerationId("f3-quarantine-five"),
                    "schema-five-store-1", 1710000000000L,
                )
            )
            val dir = staged.directory
            RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
            WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
            AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
            ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
            val gate = SingleProcessMaterialWriteGate()
            FaroV5QuarantinedV2GraphAdmission.canonical(gate, Supplier { activeV1 })
                .open(staged).use { graph ->
                    var quiesced = 0
                    val lease = object : MaterialWriteQuiescencePort {
                        override fun <T> withMaterialWritesQuiesced(
                            operationId: MaintenanceOperationId,
                            block: () -> T,
                        ): T {
                            quiesced++
                            return block()
                        }
                    }
                    val encryptedStore = FilePortableBackupArchiveStore(root.resolve("encrypted"))
                    val export = FaroV5FiveStoreExportPreparation(
                        graph, lease, encryptedStore,
                    ) {
                        !FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                            staged.directory.toRealPath() != activeV1.toRealPath() &&
                            graph.verifyHealthy()
                    }
                    val source = export.prepare(
                        MaintenanceOperationId("f3-quarantine-export"),
                        BackupSetId("f3-quarantine-backup"),
                        "5.0.0-test", 1710000000001L, password,
                        FiveStoreBackupExportConsent(true, true),
                        "f3-quarantine-effect",
                    )
                    assertEquals(1, quiesced)
                    assertTrue(encryptedStore.verify(source.artifact))
                    val saf = AndroidBackupSafAdapter(
                        BackupExternalEffectCoordinator(FileBackupExternalAttemptStore(root))
                    )
                    val picker = saf.createExportDocumentIntent(source)
                    assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
                    val destination = root.resolve("selected.farobak2")
                    // A provider may return a nonempty document. Old trailing bytes must
                    // not survive the selected export write.
                    Files.write(destination, ByteArray(source.byteCount.toInt() + 37) {
                        0x5A.toByte()
                    })
                    val uri = Uri.fromFile(destination.toFile())
                    val receipt = saf.writeSelectedExportDocument(
                        context.contentResolver, uri, source, CancellationSignal(),
                    )
                    assertEquals(BackupExternalWriteStatus.SUCCESS, receipt.status)
                    assertEquals("SAF export must truncate stale destination bytes",
                        source.byteCount, Files.size(destination))
                    val importedStore = FilePortableBackupArchiveStore(root.resolve("imported"))
                    val imported = saf.stageSelectedRestoreArchive(
                        context.contentResolver, uri, importedStore,
                        1710000000002L, CancellationSignal(),
                    )
                    assertTrue(importedStore.verify(imported))
                    val encrypted = importedStore.readBytes(imported)
                    try {
                        val result = FaroV5FiveStorePortableBackupCrypto().open(encrypted, password)
                        assertTrue(result is FiveStorePortableBackupOpenResult.Opened)
                        val backup = (result as FiveStorePortableBackupOpenResult.Opened).backupSet
                        assertEquals(graph.workspace!!.backup.snapshot(), backup.workspace)
                        assertEquals(graph.reality.backup.snapshot(), backup.fourStore.reality)
                        assertEquals(graph.commercialEvidence.backup.snapshot(), backup.fourStore.commercialEvidence)
                        assertEquals(graph.webEvidence.backup.snapshot(), backup.fourStore.webEvidence)
                        assertEquals(graph.auxiliary.backup.snapshot(), backup.fourStore.auxiliary)
                    } finally {
                        encrypted.fill(0)
                        importedStore.delete(imported)
                    }
                    // Real Android content:// provider (MediaStore), not a file://
                    // stand-in. SAF picker consent itself remains a separate F3 gate.
                    val providerSource = export.prepare(
                        MaintenanceOperationId("f3-provider-export"),
                        BackupSetId("f3-provider-backup"),
                        "5.0.0-test", 1710000000003L, password,
                        FiveStoreBackupExportConsent(true, true),
                        "f3-provider-effect",
                    )
                    val resolver = context.contentResolver
                    val providerUri = checkNotNull(resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME,
                                "faro-v5-provider-${System.nanoTime()}.farobak2")
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        }
                    )) { "MediaStore provider unavailable for directed Android test" }
                    try {
                        // Providers can return existing bytes; explicit wt must
                        // truncate them and the verified read must use content://.
                        resolver.openOutputStream(providerUri, "wt")!!.use {
                            it.write(ByteArray(providerSource.byteCount.toInt() + 19) { 0x5A.toByte() })
                        }
                        assertEquals(Intent.ACTION_CREATE_DOCUMENT,
                            saf.createExportDocumentIntent(providerSource).action)
                        val providerReceipt = saf.writeSelectedExportDocument(
                            resolver, providerUri, providerSource, CancellationSignal(),
                        )
                        assertEquals(BackupExternalWriteStatus.SUCCESS, providerReceipt.status)
                        val providerImported = FilePortableBackupArchiveStore(
                            root.resolve("provider-imported")
                        )
                        val providerArtifact = saf.stageSelectedRestoreArchive(
                            resolver, providerUri, providerImported,
                            1710000000004L, CancellationSignal(),
                        )
                        try {
                            assertTrue(providerImported.verify(providerArtifact))
                            val providerEncrypted = providerImported.readBytes(providerArtifact)
                            try {
                                val providerOpened =
                                    FaroV5FiveStorePortableBackupCrypto().open(
                                        providerEncrypted, password
                                    )
                                assertTrue(providerOpened is FiveStorePortableBackupOpenResult.Opened)
                            } finally {
                                providerEncrypted.fill(0)
                            }
                        } finally {
                            providerImported.delete(providerArtifact)
                        }
                    } finally {
                        resolver.delete(providerUri, null, null)
                    }
                    assertEquals("V1-protected", String(Files.readAllBytes(activeV1.resolve("sentinel")), Charsets.UTF_8))
                    assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
                }
        } finally {
            password.fill('\u0000')
            root.toFile().deleteRecursively()
        }
    }
}
