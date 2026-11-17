package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFbs2BackupSnapshot
import ar.com.faro.v5.store.reality.RealityPlanningBackupSnapshot
import java.nio.file.Path

/**
 * M0C2B2B: DEV/STAGED-only full Finance9/FBS2v4 semantic capture + replay.
 *
 * All five store owners, G4, Planning8 and Finance9 are captured under ONE
 * injected material-write lease; the Finance9 sealed read occurs before opening
 * the graph. Restore pins authenticated archive identity before any store write,
 * replays the existing five-store base, CLOSES its SQLite graph, then restores
 * Finance9 through the separately certified sealed stage-only port.
 *
 * NEVER activate the generation or grant Finance permissions. The injected
 * quiescence is not proof of cross-process exclusion; M1 remains a hard gate.
 * A failed operation leaves the target quarantined for exact archive replay,
 * never cutover, deletion or automatically selecting a different backup.
 */
class FaroV5Finance9FullArchiveCoordinator internal constructor(
    private val storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterStore: (FiveStoreStagedRestoreSection) -> Unit,
    private val afterFinance: () -> Unit,
) {
    constructor(storageRoot: Path, quiescence: MaterialWriteQuiescencePort) :
        this(storageRoot, quiescence, {}, {})

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val archiveIdentity = FileRestoreArchiveIdentity(storageRoot)

    /**
     * The ONE outer lease is held by this coordinator. These private delegates
     * must never escape or be used outside their containing lease.
     */
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
        requireStageAndPointer(source, expectedActive)

        // Before any graph opens and creates possible WAL sidecars, obtain
        // canonical whitelisted Finance9 data from the truly sealed stage.
        val finance = FaroV5Finance9SealedSnapshotCapture(
            storageRoot, alreadyQuiesced,
        ).capture(operationId, source, expectedActive)
        requireStageAndPointer(source, expectedActive)

        val complete = FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(
            source, expectedActive,
        ).use { graph ->
            FaroV5FiveStoreBackupCapture(graph, alreadyQuiesced).capture(
                operationId, backupSetId, productVersion, createdAtEpochMillis,
            ).copy(finance9 = finance)
        }
        check(complete.fourStore.manifest.sourceGenerationId ==
            source.descriptor.generationId
        ) { "Finance9 full capture source generation mismatch" }
        check(complete.g4Domains != null && complete.planningDomains != null &&
            complete.finance9 == finance
        ) { "Finance9 full capture must carry G4, Planning and eight tables" }
        requireStageAndPointer(source, expectedActive)
        val archive = FaroV5FiveStorePortableBackupCrypto().seal(
            complete, passphrase,
        )
        requireStageAndPointer(source, expectedActive)
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
                requireStageAndPointer(target, expectedActive)
            } catch (_: Exception) {
                return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED,
                )
            }

            val opened = try {
                FaroV5FiveStorePortableBackupCrypto().open(
                    encryptedArchive, passphrase,
                )
            } catch (_: Exception) {
                return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                )
            }
            if (opened is FiveStorePortableBackupOpenResult.Rejected) {
                return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
                )
            }
            val backup = (opened as FiveStorePortableBackupOpenResult.Opened).backupSet
            val finance = backup.finance9
            val g4 = backup.g4Domains
            val planning = backup.planningDomains
            if (finance == null || g4 == null || planning == null) {
                return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.UNSUPPORTED_FINANCE9_ARCHIVE,
                )
            }
            if (backup.fourStore.manifest.sourceGenerationId ==
                target.descriptor.generationId
            ) {
                return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
                )
            }

            try {
                requireStageAndPointer(target, expectedActive)
                // The legacy public restore still refuses v4. Only this internal
                // stage-only path may replay its five base owners and PIN the
                // immutable archive identity before the first material write.
                val base = FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(
                    target, expectedActive,
                ).use { graph ->
                    FaroV5FiveStoreStagedRestore(
                        storageRoot, target, graph, alreadyQuiesced, afterStore,
                        archiveIdentity,
                    ).restoreAuthenticatedFinance9BaseStageOnly(
                        operationId, encryptedArchive, passphrase,
                    )
                }
                if (base !is FiveStoreStagedRestoreResult.StagedVerified) {
                    return@withMaterialWritesQuiesced base
                }
                requireStageAndPointer(target, expectedActive)

                // Reality base accounts and ledger now exist; foreign keys in
                // Finance9 opening, transfers and payments can be checked.
                // The graph is CLOSED before the sealed SQL9 restore starts.
                FaroV5Finance9AuthenticatedSectionRestore(
                    storageRoot, alreadyQuiesced,
                ).restoreFinanceSectionOnly(
                    operationId, target, expectedActive,
                    encryptedArchive, passphrase,
                )
                afterFinance() // injected test fault after Finance9 COMMIT
                requireStageAndPointer(target, expectedActive)

                // Verify finance on a sealed stage before opening SQLite again.
                val observedFinance = FaroV5Finance9SealedSnapshotCapture(
                    storageRoot, alreadyQuiesced,
                ).capture(operationId, target, expectedActive)
                check(observedFinance == finance) {
                    "Finance9 sealed data differs from authenticated archive"
                }
                FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(
                    target, expectedActive,
                ).use { graph ->
                    val expectedReality = RealityFbs2BackupSnapshot(
                        backup.fourStore.reality, g4,
                        RealityPlanningBackupSnapshot(
                            planning.schemaVersion, planning.reminders,
                            planning.monthlyPlan,
                        ),
                    )
                    check(graph.reality.fbs2Backup.snapshot() == expectedReality &&
                        graph.commercialEvidence.backup.snapshot() ==
                            backup.fourStore.commercialEvidence &&
                        graph.webEvidence.backup.snapshot() ==
                            backup.fourStore.webEvidence &&
                        graph.auxiliary.backup.snapshot() ==
                            backup.fourStore.auxiliary &&
                        graph.workspace?.backup?.snapshot() == backup.workspace &&
                        graph.verifyHealthy()
                    ) { "FBS2v4 five-store replay is not exact" }
                }
                archiveIdentity.requireExistingPinnedArchive(
                    operationId, target.descriptor.generationId, encryptedArchive,
                )
                requireStageAndPointer(target, expectedActive)
                FiveStoreStagedRestoreResult.StagedVerified(
                    target.descriptor.generationId.value,
                )
            } catch (_: Exception) {
                // All writes remain STAGED; do not roll forward a pointer, do not
                // erase partial generations or accept an alternate archive.
                FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                )
            }
        }
    } catch (_: Exception) {
        FiveStoreStagedRestoreResult.Blocked(
            FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE,
        )
    }

    private fun requireStageAndPointer(
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ) {
        check(pointer.read() == expectedActive &&
            stage.descriptor.generationId != expectedActive.generationId
        ) { "Finance9 full archive requires exact nonactive pointer" }
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory.toAbsolutePath().normalize() ==
                stage.directory.toAbsolutePath().normalize()
        ) { "Finance9 full archive requires canonical staged descriptor/path" }
        check(ExistingGenerationFormatReader.requireAndRead(canonical) ==
            GenerationStoreFormat.WORKSPACE_V2
        ) { "Finance9 full archive requires sealed five-store V2 marker" }
        check(pointer.read() == expectedActive) {
            "Finance9 full archive pointer changed during admission"
        }
    }
}
