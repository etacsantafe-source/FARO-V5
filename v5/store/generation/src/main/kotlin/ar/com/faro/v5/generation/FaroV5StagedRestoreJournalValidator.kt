package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.BootstrapJournalPort
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.ExistingRestoreArchiveIdentityVerificationPort
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import java.nio.file.Path

enum class FiveStoreJournalValidationBlock {
    JOURNAL_OR_POINTER_INVALID,
    EXISTING_ARCHIVE_PIN_INVALID,
    ARCHIVE_AUTHENTICATION_FAILED,
    STAGED_CONTENT_MISMATCH,
    JOURNAL_RACE,
    QUIESCENCE_UNAVAILABLE,
}

sealed interface FiveStoreJournalValidationResult {
    data class Validated(
        val targetGenerationId: String,
        val alreadyValidated: Boolean,
    ) : FiveStoreJournalValidationResult

    data class Blocked(val reason: FiveStoreJournalValidationBlock) :
        FiveStoreJournalValidationResult
}

/**
 * Attests the exact authenticated five-store RESTORE target before STAGED -> VALIDATED.
 * Stage-only: no pointer switch, no readmission, no replay/OS effects and no backup export.
 * Caller MUST inject the host-wide gate and a graph opened with the trusted plan verifier.
 */
class FaroV5StagedRestoreJournalValidator(
    private val storageRoot: Path,
    private val target: StagedStorageGeneration,
    private val graph: FaroV5GenerationStoreGraph,
    private val journal: BootstrapJournalPort,
    private val pointer: ActiveGenerationPointerPort,
    private val existingArchivePin: ExistingRestoreArchiveIdentityVerificationPort,
    private val quiescence: MaterialWriteQuiescencePort,
) {
    fun validateAuthenticatedStage(
        operationId: MaintenanceOperationId,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreJournalValidationResult =
        try {
            quiescence.withMaterialWritesQuiesced(operationId) {
                val current = journal.read(operationId)
                    ?: return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.JOURNAL_OR_POINTER_INVALID
                    )
                val previous = current.previousGenerationId
                if (current.operationId != operationId ||
                    current.kind != MaintenanceOperationKind.RESTORE ||
                    current.phase !in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) ||
                    current.materialWritesAccepted ||
                    current.targetGenerationId != target.descriptor.generationId ||
                    previous == null || previous == target.descriptor.generationId ||
                    pointer.read()?.generationId != previous ||
                    journal.pending().any { it.operationId != operationId }
                ) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.JOURNAL_OR_POINTER_INVALID
                    )
                }

                // A verified stage must be a real marked V2 generation, never the active one.
                val stageIsExact = runCatching {
                    val staged = FileStorageGenerationLayout(storageRoot)
                        .requireStaged(target.descriptor.generationId)
                    staged.descriptor == target.descriptor &&
                        staged.directory.toAbsolutePath().normalize() ==
                            target.directory.toAbsolutePath().normalize() &&
                        ExistingGenerationFormatReader.requireAndRead(staged) ==
                            GenerationStoreFormat.WORKSPACE_V2 &&
                        graph.generationId == target.descriptor.generationId.value &&
                        graph.generationDirectory.toAbsolutePath().normalize() ==
                            staged.directory.toAbsolutePath().normalize() &&
                        graph.workspace != null
                }.getOrDefault(false)
                if (!stageIsExact) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.STAGED_CONTENT_MISMATCH
                    )
                }

                // Validation may read an EXISTING pin but must NEVER create one.
                val pinned = runCatching {
                    existingArchivePin.requireExistingPinnedArchive(
                        operationId, target.descriptor.generationId, encryptedArchive
                    )
                }.isSuccess
                if (!pinned) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.EXISTING_ARCHIVE_PIN_INVALID
                    )
                }

                val opened = runCatching {
                    FaroV5FiveStorePortableBackupCrypto().open(encryptedArchive, passphrase)
                }.getOrNull() as? FiveStorePortableBackupOpenResult.Opened
                    ?: return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.ARCHIVE_AUTHENTICATION_FAILED
                    )
                val semantic = opened.backupSet
                val exact = runCatching {
                    graph.verifyHealthy() &&
                        semantic.fourStore.manifest.sourceGenerationId != target.descriptor.generationId &&
                        graph.reality.backup.snapshot() == semantic.fourStore.reality &&
                        graph.commercialEvidence.backup.snapshot() ==
                            semantic.fourStore.commercialEvidence &&
                        graph.webEvidence.backup.snapshot() == semantic.fourStore.webEvidence &&
                        graph.auxiliary.backup.snapshot() == semantic.fourStore.auxiliary &&
                        graph.workspace!!.backup.snapshot() == semantic.workspace &&
                        pointer.read()?.generationId == previous
                }.getOrDefault(false)
                if (!exact) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.STAGED_CONTENT_MISMATCH
                    )
                }

                if (current.phase == BootstrapPhase.VALIDATED) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Validated(
                        target.descriptor.generationId.value, alreadyValidated = true
                    )
                }
                val next = BootstrapTransitionPolicy.transition(current, BootstrapPhase.VALIDATED)
                if (!journal.compareAndSet(current, next)) {
                    return@withMaterialWritesQuiesced FiveStoreJournalValidationResult.Blocked(
                        FiveStoreJournalValidationBlock.JOURNAL_RACE
                    )
                }
                check(journal.read(operationId) == next &&
                    pointer.read()?.generationId == previous
                ) { "validated stage journal or predecessor pointer drifted" }
                FiveStoreJournalValidationResult.Validated(
                    target.descriptor.generationId.value, alreadyValidated = false
                )
            }
        } catch (_: Exception) {
            FiveStoreJournalValidationResult.Blocked(
                FiveStoreJournalValidationBlock.QUIESCENCE_UNAVAILABLE
            )
        }
}
