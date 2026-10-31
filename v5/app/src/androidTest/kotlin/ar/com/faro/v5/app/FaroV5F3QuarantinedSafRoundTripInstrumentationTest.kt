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

    @Test fun restorePreparationCreatesDurableStagedJournalWithOneSharedGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f3-journal-")
        try {
            val layout = FileStorageGenerationLayout(root)
            val source = StorageGenerationDescriptor(
                StorageGenerationId("f3-preparation-active-v1"),
                "v1-protected", 1710000000000L,
            )
            layout.stage(source)
            val pointer = FileActiveGenerationPointer(root)
            assertNotNull(pointer.compareAndSet(null, source.generationId))
            val before = pointer.read()
            val journal = FileBootstrapJournal(root)
            val operation = MaintenanceOperationId("f3-prepare-one")
            val target = StorageGenerationDescriptor(
                StorageGenerationId("f3-preparation-v2-target"),
                "five-store-v2-test", 1710000000001L,
            )
            val coordinator = FaroV5QuarantinedRestorePreparation(
                root, MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
            )
            // A stale predecessor token must be rejected BEFORE creating a
            // journal or touching the fresh target.
            val staleOperation = MaintenanceOperationId("f3-prepare-stale-predecessor")
            var staleRejected = false
            try {
                coordinator.prepare(
                    staleOperation, target,
                    expectedPreviousGenerationId = StorageGenerationId("wrong-v1"),
                )
            } catch (_: IllegalStateException) {
                staleRejected = true
            }
            assertTrue("stale predecessor must fail closed", staleRejected)
            assertNull(journal.read(staleOperation))
            assertEquals(before, pointer.read())
            val staged = coordinator.prepare(
                operation, target, expectedPreviousGenerationId = source.generationId,
            )
            assertEquals(target, staged.descriptor)
            assertEquals(target, layout.requireStaged(target.generationId).descriptor)
            val entry = journal.read(operation)
            assertNotNull(entry)
            assertEquals(MaintenanceOperationKind.RESTORE, entry!!.kind)
            assertEquals(BootstrapPhase.STAGED, entry.phase)
            assertEquals(source.generationId, entry.previousGenerationId)
            assertEquals(target.generationId, entry.targetGenerationId)
            assertFalse(entry.materialWritesAccepted)
            assertEquals(before, pointer.read())
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)

            // A second operation must fail BEFORE creating a target or journal.
            val secondOp = MaintenanceOperationId("f3-prepare-conflict")
            val secondTarget = StorageGenerationDescriptor(
                StorageGenerationId("f3-preparation-blocked"),
                "five-store-v2-test", 1710000000002L,
            )
            var refused = false
            try {
                coordinator.prepare(secondOp, secondTarget)
            } catch (_: IllegalStateException) {
                refused = true
            }
            assertTrue("unresolved RESTORE must block another preparation", refused)
            assertNull(journal.read(secondOp))
            assertEquals(before, pointer.read())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun interruptedRestorePreparationRechecksFiveOwnersAndAdvancesOnlyJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f3-recover-")
        try {
            val layout = FileStorageGenerationLayout(root)
            val previous = StorageGenerationDescriptor(
                StorageGenerationId("f3-recover-v1"), "v1-protected", 1710000000100L,
            )
            layout.stage(previous)
            val pointer = FileActiveGenerationPointer(root)
            assertNotNull(pointer.compareAndSet(null, previous.generationId))
            val before = pointer.read()
            val operation = MaintenanceOperationId("f3-recover-operation")
            val target = StorageGenerationDescriptor(
                StorageGenerationId("f3-recover-target"), "five-store-v2-test",
                1710000000101L,
            )
            val journal = FileBootstrapJournal(root)
            journal.create(BootstrapJournalEntry(
                operationId = operation,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = previous.generationId,
                targetGenerationId = target.generationId,
                phase = BootstrapPhase.PREPARING,
            ))
            // Simulated crash after all five stores are initialized, before
            // PREPARING -> STAGED was durably recorded.
            FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(target)
            assertEquals(BootstrapPhase.PREPARING, journal.read(operation)!!.phase)
            val coordinator = FaroV5QuarantinedRestorePreparation(
                root, MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
            )
            assertEquals(target, coordinator.recoverInterrupted(operation, target).descriptor)
            assertEquals(BootstrapPhase.STAGED, journal.read(operation)!!.phase)
            assertEquals(before, pointer.read())
            // A replay of the same complete STAGED identity is idempotent.
            assertEquals(target, coordinator.recoverInterrupted(operation, target).descriptor)
            assertEquals(BootstrapPhase.STAGED, journal.read(operation)!!.phase)
            assertEquals(before, pointer.read())
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun journaledFiveStoreRestoreValidatesAuthenticatedOwnersWithoutV1Switch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f3-validated-")
        val password = "synthetic-f3-journaled-restore-2026".toCharArray()
        try {
            val layout = FileStorageGenerationLayout(root)
            val previous = StorageGenerationDescriptor(
                StorageGenerationId("f3-validated-active-v1"),
                "v1-protected", 1710000000200L,
            )
            layout.stage(previous)
            val pointer = FileActiveGenerationPointer(root)
            assertNotNull(pointer.compareAndSet(null, previous.generationId))
            val before = pointer.read()
            val sharedLease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
            val source = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
                StorageGenerationDescriptor(
                    StorageGenerationId("f3-validated-backup-source"),
                    "five-store-v2-test", 1710000000201L,
                )
            )
            val encrypted = FaroV5GenerationStoreGraph.openValidatedV2(source).use { graph ->
                val workspace = graph.workspace!!
                val original = workspace.backup.snapshot()
                workspace.backup.restoreOrVerify(original.copy(revision = 9L))
                val set = FaroV5FiveStoreBackupCapture(graph, sharedLease).capture(
                    MaintenanceOperationId("f3-validated-capture"),
                    BackupSetId("f3-validated-five-owner-set"),
                    "5.0-test", 1710000000202L,
                )
                FaroV5FiveStorePortableBackupCrypto().seal(set, password)
            }
            try {
                val operation = MaintenanceOperationId("f3-validated-restore")
                val descriptor = StorageGenerationDescriptor(
                    StorageGenerationId("f3-validated-quarantine"),
                    "five-store-v2-test", 1710000000203L,
                )
                val target = FaroV5QuarantinedRestorePreparation(root, sharedLease)
                    .prepare(operation, descriptor)
                val journal = FileBootstrapJournal(root)
                assertEquals(BootstrapPhase.STAGED, journal.read(operation)!!.phase)
                FaroV5GenerationStoreGraph.openValidatedV2(target).use { graph ->
                    val identity = FileRestoreArchiveIdentity(root)
                    val admission = FaroV5RestoreJournalAdmission(
                        journal, pointer, descriptor.generationId, sharedLease,
                    )
                    val staged = FaroV5FiveStoreStagedRestore(
                        root, target, graph, admission,
                        archiveIdentity = identity,
                    ).restoreAuthenticated(operation, encrypted, password)
                    assertTrue(staged is FiveStoreStagedRestoreResult.StagedVerified)
                    val validated = FaroV5StagedRestoreJournalValidator(
                        root, target, graph, journal, pointer, identity, sharedLease,
                    ).validateAuthenticatedStage(operation, encrypted, password)
                    assertEquals(
                        FiveStoreJournalValidationResult.Validated(
                            descriptor.generationId.value, false,
                        ),
                        validated,
                    )
                    assertEquals(9L, graph.workspace!!.backup.snapshot().revision)
                }
                assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)!!.phase)
                assertFalse(journal.read(operation)!!.materialWritesAccepted)
                assertEquals(before, pointer.read())
                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
            } finally {
                encrypted.fill(0)
            }
        } finally {
            password.fill('\u0000')
            root.toFile().deleteRecursively()
        }
    }
    @Test fun mutableContentProviderCannotSwapPinnedAuthenticatedRestoreArchive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f3-provider-pin-")
        val password = "synthetic-f3-provider-pin-2026".toCharArray()
        try {
            val layout = FileStorageGenerationLayout(root)
            val previous = StorageGenerationDescriptor(
                StorageGenerationId("f3-pin-active-v1"), "v1-protected", 1710000000300L,
            )
            layout.stage(previous)
            val pointer = FileActiveGenerationPointer(root)
            assertNotNull(pointer.compareAndSet(null, previous.generationId))
            val before = pointer.read()
            val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
            val source = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
                StorageGenerationDescriptor(
                    StorageGenerationId("f3-pin-source"), "five-store-v2-test",
                    1710000000301L,
                )
            )
            val (original, replacement) =
                FaroV5GenerationStoreGraph.openValidatedV2(source).use { graph ->
                    val capture = FaroV5FiveStoreBackupCapture(graph, lease)
                    val first = capture.capture(
                        MaintenanceOperationId("f3-pin-capture-one"),
                        BackupSetId("f3-pin-backup-one"), "5.0-test", 1710000000302L,
                    )
                    val workspace = graph.workspace!!
                    val snapshot = workspace.backup.snapshot()
                    workspace.backup.restoreOrVerify(
                        snapshot.copy(revision = snapshot.revision + 9L)
                    )
                    val second = capture.capture(
                        MaintenanceOperationId("f3-pin-capture-two"),
                        BackupSetId("f3-pin-backup-two"), "5.0-test", 1710000000303L,
                    )
                    Pair(
                        FaroV5FiveStorePortableBackupCrypto().seal(first, password),
                        FaroV5FiveStorePortableBackupCrypto().seal(second, password),
                    )
                }
            try {
                assertFalse(original.contentEquals(replacement))
                val resolver = context.contentResolver
                val uri = checkNotNull(resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME,
                            "faro-v5-pin-${System.nanoTime()}.farobak2")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    },
                ))
                try {
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(original) }
                    val saf = AndroidBackupSafAdapter(
                        BackupExternalEffectCoordinator(FileBackupExternalAttemptStore(root))
                    )
                    val previewStore = FilePortableBackupArchiveStore(root.resolve("preview"))
                    val preview = saf.stageSelectedRestoreArchive(
                        resolver, uri, previewStore, 1710000000304L, CancellationSignal(),
                    )
                    try {
                        val previewBytes = previewStore.readBytes(preview)
                        try {
                            assertTrue(
                                FaroV5FiveStorePortableBackupCrypto().open(
                                    previewBytes, password
                                ) is FiveStorePortableBackupOpenResult.Opened
                            )
                            val pinnedStore = FilePortableBackupArchiveStore(
                                root.resolve("backup").resolve("five-store-staged")
                            )
                            val pinned = pinnedStore.stageBytes(previewBytes, 1710000000304L)
                            // The provider changes AFTER authentication, but
                            // the durable restore uses the pinned original.
                            resolver.openOutputStream(uri, "wt")!!.use {
                                it.write(replacement)
                            }
                            val pinnedBytes = pinnedStore.readBytes(pinned)
                            try {
                                assertArrayEquals(original, pinnedBytes)
                                val operation = MaintenanceOperationId("f3-pin-restore")
                                val descriptor = StorageGenerationDescriptor(
                                    StorageGenerationId("f3-pin-target"),
                                    "five-store-v2-test", 1710000000305L,
                                )
                                val staged = FaroV5QuarantinedRestorePreparation(
                                    root, lease,
                                ).prepare(
                                    operation, descriptor,
                                    expectedPreviousGenerationId = previous.generationId,
                                )
                                val journal = FileBootstrapJournal(root)
                                FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
                                    val identity = FileRestoreArchiveIdentity(root)
                                    val admission = FaroV5RestoreJournalAdmission(
                                        journal, pointer, descriptor.generationId, lease,
                                    )
                                    val restored = FaroV5FiveStoreStagedRestore(
                                        root, staged, graph, admission,
                                        archiveIdentity = identity,
                                    ).restoreAuthenticated(operation, pinnedBytes, password)
                                    assertTrue(restored is FiveStoreStagedRestoreResult.StagedVerified)
                                    val validated = FaroV5StagedRestoreJournalValidator(
                                        root, staged, graph, journal, pointer, identity, lease,
                                    ).validateAuthenticatedStage(
                                        operation, pinnedBytes, password,
                                    )
                                    assertEquals(
                                        FiveStoreJournalValidationResult.Validated(
                                            descriptor.generationId.value, false,
                                        ),
                                        validated,
                                    )
                                }
                                assertEquals(before, pointer.read())
                                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
                            } finally {
                                pinnedBytes.fill(0)
                            }
                        } finally {
                            previewBytes.fill(0)
                        }
                    } finally {
                        previewStore.delete(preview)
                    }
                } finally {
                    resolver.delete(uri, null, null)
                }
            } finally {
                original.fill(0)
                replacement.fill(0)
            }
        } finally {
            password.fill('\\u0000')
            root.toFile().deleteRecursively()
        }
    }

}
