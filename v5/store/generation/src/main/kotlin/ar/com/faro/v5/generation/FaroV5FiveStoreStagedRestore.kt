package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.RestoreArchiveIdentityPort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFbs2BackupSnapshot
import java.nio.file.Path

enum class FiveStoreStagedRestoreBlock {
    TARGET_NOT_QUARANTINED,
    WRITE_PAUSE_UNAVAILABLE,
    INVALID_ARCHIVE,
    SOURCE_TARGET_IDENTITY_CONFLICT,
    STAGED_RESTORE_FAILED,
    ARCHIVE_IDENTITY_CONFLICT,
    /** A valid FBS2 v2 G4 section cannot be dropped by this legacy restore. */
    UNSUPPORTED_G4_ARCHIVE,
}

sealed interface FiveStoreStagedRestoreResult {
    /** Staged data only: NOT a journal validation, active pointer or replay fence. */
    data class StagedVerified(val targetGenerationId: String) : FiveStoreStagedRestoreResult

    data class Blocked(
        val reason: FiveStoreStagedRestoreBlock,
        val archiveFailure: FiveStorePortableBackupOpenFailure? = null,
    ) : FiveStoreStagedRestoreResult
}

enum class FiveStoreStagedRestoreSection {
    REALITY, COMMERCIAL_EVIDENCE, WEB_EVIDENCE, AUXILIARY, WORKSPACE,
}

/**
 * Quarantined FBS2/FAROBAK2 restore ONLY into a pre-existing, initialized and
 * version-marked V2 stage. NEVER opens or changes the active pointer.
 *
 * This helper is deliberately NOT bound to CompositionRoot, export, or recovery.
 * The supplied quiescence MUST cover writes to all five stores and generation
 * activation; that real host binding remains an uncertified S2.A6 blocker.
 * A crash may leave an INCOMPLETE staged target: no automatic activation,
 * deletion, adoption, or fallback to V1 is permitted.
 *
 * A graph with prepared-plan records must have the trusted canonical plan
 * verifier injected through openValidatedV2WithPlanVerifier. Deny-all remains
 * the default; Workspace refuses any unverifiable plan on restore.
 */
class FaroV5FiveStoreStagedRestore(
    private val storageRoot: Path,
    private val target: StagedStorageGeneration,
    private val graph: FaroV5GenerationStoreGraph,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterSection: (FiveStoreStagedRestoreSection) -> Unit = {},
    private val archiveIdentity: RestoreArchiveIdentityPort,
) {
    fun restoreAuthenticated(
        operationId: MaintenanceOperationId,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult =
        restoreAuthenticatedImpl(operationId, encryptedArchive, passphrase, allowG4StageOnly = false)

    /**
     * Deliberately module-internal: isolated staged-restore tests only.
     * This is NOT wired to Android host, public restore or external G4 export.
     * The public method above retains the UNSUPPORTED_G4_ARCHIVE fence.
     */
    internal fun restoreAuthenticatedG4StageOnly(
        operationId: MaintenanceOperationId,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult =
        restoreAuthenticatedImpl(operationId, encryptedArchive, passphrase, allowG4StageOnly = true)

    private fun restoreAuthenticatedImpl(
        operationId: MaintenanceOperationId,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
        allowG4StageOnly: Boolean,
    ): FiveStoreStagedRestoreResult {
        return try {
            quiescence.withMaterialWritesQuiesced(operationId) {
                try {
                    requireStagedNonActiveTarget()
                } catch (_: Exception) {
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED,
                    )
                }
                val opened = try {
                    FaroV5FiveStorePortableBackupCrypto().open(encryptedArchive, passphrase)
                } catch (_: Exception) {
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                    )
                }
                if (opened is FiveStorePortableBackupOpenResult.Rejected) {
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                        opened.reason,
                    )
                }
                val backup = (opened as FiveStorePortableBackupOpenResult.Opened).backupSet
                // FBS2 v2 can carry G4 Objective/Matter data. This legacy staged
                // restore only knows the old Reality four-store segment, so accepting
                // the new section would silently lose canonical owner data.
                // Reject BEFORE archive pinning, SQLite writes or any UI state change.
                if (backup.g4Domains != null && !allowG4StageOnly) {
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.UNSUPPORTED_G4_ARCHIVE,
                    )
                }
                if (backup.fourStore.manifest.sourceGenerationId == target.descriptor.generationId) {
                    return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
                    )
                }
                try {
                    // Pin the EXACT authenticated archive under the same held lease
                    // before the first of five store writes. An interrupted retry
                    // cannot substitute another authenticated backup or target.
                    try {
                        archiveIdentity.pinOrVerify(
                            operationId, target.descriptor.generationId, encryptedArchive,
                        )
                    } catch (_: Exception) {
                        return@withMaterialWritesQuiesced FiveStoreStagedRestoreResult.Blocked(
                            FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT,
                        )
                    }
                    // All semantic bytes are authenticated/decoded before the
                    // first store changes. Each store refuses nonfresh targets,
                    // except exact idempotent replays of its prior restore.
                    val expectedG4 = backup.g4Domains
                    if (expectedG4 == null) {
                        graph.reality.backup.restoreOrVerify(backup.fourStore.reality)
                    } else {
                        // Stage-only paired replay. The FBS1 legacy port deliberately
                        // refuses ANY nonempty G4 owner, even for an exact retry.
                        // Preserve that guard: bypass it ONLY when the complete
                        // paired Reality snapshot already equals this archive.
                        val current = graph.reality.fbs2Backup.snapshot()
                        val expected = RealityFbs2BackupSnapshot(
                            legacy = backup.fourStore.reality, g4Domains = expectedG4,
                        )
                        if (current != expected) {
                            val currentG4 = current.g4Domains
                            check(currentG4.objectives.objectives.isEmpty() &&
                                currentG4.matters.matters.isEmpty() &&
                                currentG4.objectives.revision == 0L &&
                                currentG4.matters.revision == 0L
                            ) { "foreign nonfresh G4 owner cannot be replaced" }
                            // A crash between these calls leaves legacy exact and
                            // G4 fresh; retry may safely resume with the same pin.
                            graph.reality.backup.restoreOrVerify(backup.fourStore.reality)
                            graph.reality.domainBackupRestore.restoreOrVerify(expectedG4)
                        }
                        check(graph.reality.fbs2Backup.snapshot() == expected) {
                            "paired G4 Reality restore differs from authenticated archive"
                        }
                    }
                    afterSection(FiveStoreStagedRestoreSection.REALITY)
                    graph.commercialEvidence.backup.restoreOrVerify(backup.fourStore.commercialEvidence)
                    afterSection(FiveStoreStagedRestoreSection.COMMERCIAL_EVIDENCE)
                    graph.webEvidence.backup.restoreOrVerify(backup.fourStore.webEvidence)
                    afterSection(FiveStoreStagedRestoreSection.WEB_EVIDENCE)
                    graph.auxiliary.backup.restoreOrVerify(backup.fourStore.auxiliary)
                    afterSection(FiveStoreStagedRestoreSection.AUXILIARY)
                    checkNotNull(graph.workspace).backup.restoreOrVerify(backup.workspace)
                    afterSection(FiveStoreStagedRestoreSection.WORKSPACE)

                    check(graph.verifyHealthy()) { "staged V2 graph not healthy after restore" }
                    val realityMatches = if (expectedG4 == null) {
                        graph.reality.backup.snapshot() == backup.fourStore.reality
                    } else {
                        graph.reality.fbs2Backup.snapshot() ==
                            RealityFbs2BackupSnapshot(backup.fourStore.reality, expectedG4)
                    }
                    check(realityMatches &&
                        graph.commercialEvidence.backup.snapshot() == backup.fourStore.commercialEvidence &&
                        graph.webEvidence.backup.snapshot() == backup.fourStore.webEvidence &&
                        graph.auxiliary.backup.snapshot() == backup.fourStore.auxiliary &&
                        graph.workspace!!.backup.snapshot() == backup.workspace
                    ) { "five-store restored snapshot differs from authenticated backup" }
                    requireStagedNonActiveTarget()
                    FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value)
                } catch (_: Exception) {
                    FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED,
                    )
                }
            }
        } catch (_: Exception) {
            // No store is deliberately written if admission itself fails.
            // Even after a partial staging fault the active pointer is untouched.
            FiveStoreStagedRestoreResult.Blocked(
                FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE,
            )
        }
    }

    private fun requireStagedNonActiveTarget() {
        val canonical = FileStorageGenerationLayout(storageRoot)
            .requireStaged(target.descriptor.generationId)
        check(canonical.descriptor == target.descriptor &&
            canonical.directory.toAbsolutePath().normalize() ==
                target.directory.toAbsolutePath().normalize()
        ) { "staged V2 descriptor/directory identity mismatch" }
        check(ExistingGenerationFormatReader.requireAndRead(canonical) ==
            GenerationStoreFormat.WORKSPACE_V2
        ) { "target is not a marked V2 generation" }
        check(graph.generationId == target.descriptor.generationId.value &&
            graph.generationDirectory.toAbsolutePath().normalize() ==
                canonical.directory.toAbsolutePath().normalize() &&
            graph.workspace != null
        ) { "graph does not own all five target stores" }
        check(FileActiveGenerationPointer(storageRoot).read()?.generationId !=
            target.descriptor.generationId
        ) { "active generation must never be a restore target" }
        check(graph.verifyHealthy()) { "target staged graph is unhealthy" }
    }
}
