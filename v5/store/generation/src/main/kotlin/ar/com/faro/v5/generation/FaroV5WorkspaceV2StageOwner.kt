package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path

/**
 * Explicit, NOT YET WIRED, fresh V2 stage owner for future recovery/restore.
 *
 * This only prepares and verifies an isolated generation. It does not move the
 * authoritative pointer, import a backup, create a conversation, or register a
 * runtime with CompositionRoot. Material backup/cutover belongs to A1.3c.
 *
 * Crash/failure leaves the unactivated directory in quarantine for explicit
 * lifecycle reconciliation; NEVER auto-deletes an unknown partial database.
 */
class FaroV5WorkspaceV2StageOwner internal constructor(
    storageRoot: Path,
    private val afterMaterialized: (String) -> Unit,
) {
    constructor(storageRoot: Path) : this(storageRoot, {})

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)

    /**
     * A5: create one NEW quarantined five-owner Reality8 generation.
     * No active pointer publication, migration of ACTIVE7, data deletion or
     * implicit recovery of a partially initialized target is permitted.
     *
     * Pointer ABA, even a switch-away-and-back, invalidates this attempt.
     * Failure leaves the candidate staged for explicit owner reconciliation.
     */
    @Synchronized
    fun stageFreshPlanning8AndVerify(
        descriptor: StorageGenerationDescriptor,
    ): StagedStorageGeneration {
        val initialPointer = pointer.read()
        check(initialPointer?.generationId != descriptor.generationId) {
            "Planning8 fresh staging must never target the active generation"
        }
        val candidate = stageFreshAndVerify(descriptor)
        check(pointer.read() == initialPointer) {
            "Active generation changed during fresh five-owner staging"
        }
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(candidate).use { graph ->
            check(graph.workspace != null &&
                graph.reality.maintenance.inspect().schemaVersion == 8 &&
                graph.verifyHealthy()) {
                "Fresh STAGED Reality8 failed exact five-owner validation"
            }
        }
        check(pointer.read() == initialPointer) {
            "Active generation changed during quarantined Reality8 upgrade"
        }
        return candidate
    }

    @Synchronized
    fun stageFreshAndVerify(descriptor: StorageGenerationDescriptor): StagedStorageGeneration {
        check(pointer.read()?.generationId != descriptor.generationId) {
            "active generation cannot be restaged as V2"
        }
        val staged = layout.stageWorkspaceV2(descriptor)
        val directory = staged.directory

        // These constructors are the sole explicit initializers for a NEW,
        // marker-bound V2 generation; every store is closed before verification.
        // A partial stage after crash is NOT implicitly resumed or overwritten.
        RealityStore(directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            afterMaterialized(GenerationStoreFiles.REALITY)
        }
        CommercialEvidenceStore(
            directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()
        ).use { afterMaterialized(GenerationStoreFiles.COMMERCIAL_EVIDENCE) }
        WebEvidenceStore(
            directory.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()
        ).use { afterMaterialized(GenerationStoreFiles.WEB_EVIDENCE) }
        AuxiliaryDeliveryStore(
            directory.resolve(GenerationStoreFiles.AUXILIARY).toString()
        ).use { afterMaterialized(GenerationStoreFiles.AUXILIARY) }
        ConversationWorkspaceStore(
            directory.resolve(GenerationStoreFiles.WORKSPACE).toString()
        ).use { afterMaterialized(GenerationStoreFiles.WORKSPACE) }

        FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
            check(graph.verifyHealthy() && graph.workspace?.snapshot()?.revision == 0L) {
                "fresh V2 generation failed five-store verification"
            }
        }
        check(pointer.read()?.generationId != descriptor.generationId) {
            "V2 target became active unexpectedly during isolated staging"
        }
        return staged
    }
}
