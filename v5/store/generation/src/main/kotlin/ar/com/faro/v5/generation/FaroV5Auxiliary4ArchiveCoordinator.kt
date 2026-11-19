package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityFinance9SealedSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9SealedTableRows
import ar.com.faro.v5.store.reality.RealityFinance9StagedRowsRestorePort
import java.nio.file.Path

/** Authenticated FBS2v5 export/replay for a sealed, non-active DEV stage. */
internal class FaroV5Auxiliary4ArchiveCoordinator(
    private val storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
) {
    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val archiveIdentity = FileRestoreArchiveIdentity(storageRoot)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()

    // This delegate stays private to the outer quiesced operation.
    private val alreadyQuiesced = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    fun captureAuthenticated(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        source: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
    ): ByteArray = quiescence.withMaterialWritesQuiesced(operationId) {
        val snapshot = capture(
            operationId, backupSetId, source, expectedActive, productVersion, createdAtEpochMillis,
        )
        val archive = crypto.sealWithReceipts(snapshot, passphrase)
        requirePinnedStage(source, expectedActive)
        archive
    }

    fun restoreAuthenticated(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            try {
                requirePinnedStage(target, expectedActive)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(target, allowAuxiliary3 = true)
            } catch (_: Exception) {
                return@withMaterialWritesQuiesced blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
            }

            if (!crypto.admitsArchiveSize(encryptedArchive.size)) {
                return@withMaterialWritesQuiesced blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
            }
            // Use one immutable content snapshot for authentication and pinning.
            val archive = encryptedArchive.copyOf()
            val opened = try {
                crypto.openWithReceipts(archive, passphrase)
            } catch (_: IllegalArgumentException) {
                return@withMaterialWritesQuiesced blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
            }
            val snapshot = when (opened) {
                is FiveStoreAuthenticatedPayload.Opened -> opened.payload
                is FiveStoreAuthenticatedPayload.Rejected ->
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
                    )
            }
            val manifest = snapshot.base.fourStore.manifest
            if (manifest.sourceGenerationId == target.descriptor.generationId) {
                return@withMaterialWritesQuiesced blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
            }
            try {
                requirePinnedStage(target, expectedActive)
                archiveIdentity.pinOrVerify(operationId, target.descriptor.generationId, archive)
            } catch (_: Exception) {
                return@withMaterialWritesQuiesced blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
            }
            try {
                requirePinnedStage(target, expectedActive)
                FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(target, pointer).close()
                requirePinnedStage(target, expectedActive)
                val restored = FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(
                    target, expectedActive,
                ).use { graph ->
                    FaroV5FiveStoreStagedRestore(
                        storageRoot, target, graph, alreadyQuiesced,
                        afterSection = { section ->
                            requirePinnedStage(target, expectedActive)
                            afterStore(section)
                        },
                        archiveIdentity = archiveIdentity,
                    ).restoreAuthenticatedAuxiliary4StageOnly(
                        operationId, archive, passphrase, expectedActive,
                    )
                }
                if (restored !is FiveStoreStagedRestoreResult.StagedVerified) {
                    return@withMaterialWritesQuiesced restored
                }
                requirePinnedStage(target, expectedActive)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(target, allowAuxiliary3 = false)
                val finance = checkNotNull(snapshot.base.finance9)
                RealityFinance9StagedRowsRestorePort.restoreOrVerifyTrustedStagedNine(
                    target.directory.resolve(GenerationStoreFiles.REALITY),
                    RealityFinance9SealedSnapshot(
                        finance.schemaVersion,
                        finance.tables.map { RealityFinance9SealedTableRows(it.table, it.rows) },
                    ),
                )
                requirePinnedStage(target, expectedActive)
                val observed = capture(
                    operationId, manifest.backupSetId, target, expectedActive,
                    manifest.productVersion, manifest.createdAtEpochMillis,
                )
                check(
                    observed.base.fourStore.copy(manifest = manifest) == snapshot.base.fourStore &&
                        observed.base.workspace == snapshot.base.workspace &&
                        observed.base.g4Domains == snapshot.base.g4Domains &&
                        observed.base.planningDomains == snapshot.base.planningDomains &&
                        observed.base.finance9 == snapshot.base.finance9 &&
                        observed.receipts == snapshot.receipts,
                ) { "Restored FBS2v5 snapshot differs from the authenticated archive" }
                archiveIdentity.requireExistingPinnedArchive(operationId, target.descriptor.generationId, archive)
                requirePinnedStage(target, expectedActive)
                restored
            } catch (_: Exception) {
                blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED)
            }
        }
    } catch (_: Exception) {
        blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    }

    private fun capture(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        productVersion: String,
        createdAtEpochMillis: Long,
    ): FaroV5Auxiliary4Fbs2Snapshot {
        requirePinnedStage(stage, expectedActive)
        val (base, auxiliary) = FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(
            stage, expectedActive,
        ).use { graph ->
            FaroV5FiveStoreBackupCapture(graph, alreadyQuiesced).captureReceiptProjection(
                operationId, backupSetId, productVersion, createdAtEpochMillis,
            )
        }
        requirePinnedStage(stage, expectedActive)
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(stage, allowAuxiliary3 = false)
        val finance = RealityFinance9SealedReadPort.readTrustedSealedStage9(
            stage.directory.resolve(GenerationStoreFiles.REALITY),
        )
        requirePinnedStage(stage, expectedActive)
        return FaroV5Auxiliary4Fbs2Snapshot(
            base.copy(finance9 = Finance9SectionSnapshot(
                finance.schemaVersion,
                finance.tables.map { Finance9TableRows(it.table, it.rows) },
            )),
            auxiliary.receipts,
        )
    }

    private fun requirePinnedStage(stage: StagedStorageGeneration, expectedActive: ActiveGenerationPointer) {
        check(pointer.read() == expectedActive && stage.descriptor.generationId != expectedActive.generationId)
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory == stage.directory.toAbsolutePath().normalize())
        check(pointer.read() == expectedActive)
    }

    private fun blocked(reason: FiveStoreStagedRestoreBlock) =
        FiveStoreStagedRestoreResult.Blocked(reason)
}
