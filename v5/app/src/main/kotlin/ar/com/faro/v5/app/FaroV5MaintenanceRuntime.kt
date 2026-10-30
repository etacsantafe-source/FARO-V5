package ar.com.faro.v5.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import ar.com.faro.v5.generation.FaroV5FiveStoreStagedRestore
import ar.com.faro.v5.generation.FaroV5RestoreJournalAdmission
import ar.com.faro.v5.generation.FaroV5StagedRestoreJournalValidator
import ar.com.faro.v5.generation.FiveStoreJournalValidationResult
import ar.com.faro.v5.generation.FiveStoreStagedRestoreResult
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.StagedPortableBackupArchive
import ar.com.faro.v5.generation.FaroV5FiveStoreExportPreparation
import ar.com.faro.v5.generation.FiveStoreBackupExportConsent
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FilePortableBackupArchiveStore
import ar.com.faro.v5.maintenance.PreparedBackupExternalWriteSource
import ar.com.faro.v5.maintenance.BackupExternalEffectCoordinator
import ar.com.faro.v5.maintenance.BackupExternalEffectReceipt
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBackupExternalAttemptStore
import ar.com.faro.v5.maintenance.EmptyGenerationResetTargetPort
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FilePortabilityExternalAttemptStore
import ar.com.faro.v5.maintenance.FileResetAttemptStore
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationProjectionReconciliationPort
import ar.com.faro.v5.maintenance.GenerationRuntimeActivationPort
import ar.com.faro.v5.maintenance.GenerationSwitchCoordinator
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.PortabilityExternalEffectCoordinator
import ar.com.faro.v5.maintenance.ResetExecutionRequest
import ar.com.faro.v5.maintenance.ResetExecutionResult
import ar.com.faro.v5.maintenance.ResetLifecycleCoordinator
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.maintenance.ValidatedGenerationActivationCoordinator
import ar.com.faro.v5.platform.android.backup.AndroidBackupSafAdapter
import ar.com.faro.v5.platform.android.reset.AndroidResetOsReconciliation
import ar.com.faro.v5.platform.android.reset.AndroidResetVaultReconciliation
import java.io.File

/**
 * A process-local, one-shot SAF export capability. It is never serialized or
 * replayed after Activity/process death; durable external effects reconcile separately.
 */
internal data class FiveStoreSafExportSession(
    val source: PreparedBackupExternalWriteSource,
    val activeGenerationId: String,
)

/**
 * Terminal app-side composition for P4 generation activation recovery.
 *
 * Maintenance owns lifecycle semantics and durable journals. The app contributes only runtime
 * reopen/health and OS projection hooks because it is the terminal module that can see Android
 * adapters and the concrete CompositionRoot together.
 */
class FaroV5MaintenanceRuntime(
    context: Context,
    private val root: FaroV5CompositionRoot,
) {
    private val storageRoot =
        File(context.applicationContext.noBackupFilesDir, "faro-v5-storage").toPath()
    private val journal = FileBootstrapJournal(storageRoot)
    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val replayFence = FileGenerationReplayFence(storageRoot)
    private val quiescence = MaterialWriteGateQuiescence(root.materialWriteGate)
    private val backupExternalEffects =
        BackupExternalEffectCoordinator(
            FileBackupExternalAttemptStore(storageRoot)
        )
    private val backupSafAdapter by lazy { AndroidBackupSafAdapter(backupExternalEffects) }
    private val fiveStorePrivateArchiveStore by lazy {
        FilePortableBackupArchiveStore(storageRoot.resolve("backup").resolve("five-store-staged"))
    }
    private val portabilityExternalEffects =
        PortabilityExternalEffectCoordinator(
            FilePortabilityExternalAttemptStore(storageRoot)
        )

    private val runtimeActivation =
        GenerationRuntimeActivationPort { _, generationId ->
            root.activateAndVerifyGeneration(generationId.value)
        }

    private val projectionReconciliation =
        GenerationProjectionReconciliationPort { _, _ ->
            root.reprojectStartup()
            check(
                root.startupReprojectionState ==
                    FaroV5CompositionRoot.StartupReprojectionState.PASS
            ) {
                "generation projection reconciliation degraded"
            }
        }

    private val activation =
        ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = quiescence,
            generationSwitch =
                GenerationSwitchCoordinator(
                    journal = journal,
                    activePointer = pointer,
                    generationLayout = layout,
                ),
            runtime = runtimeActivation,
            replayFence = replayFence,
            projections = projectionReconciliation,
            // One shared certificate gates both pre-pointer admission and the
            // active graph opener. Shipping remains V2 OFF until F1-F4/T4.
            preSwitchAdmission = FaroV5PreSwitchHostAdmission(
                layout = layout,
                activePointer = pointer,
                v2HostCertified = FaroV5HostCertification.V2_ACTIVE_ENABLED,
                verifyQuarantinedV2 = { staged ->
                    root.openQuarantinedV2WithHostGate(staged).use { it.verifyHealthy() }
                },
            ),
        )

    private val resetLifecycle =
        ResetLifecycleCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = quiescence,
            targetGeneration = EmptyGenerationResetTargetPort(),
            runtime = runtimeActivation,
            attempts = FileResetAttemptStore(storageRoot),
            vault = AndroidResetVaultReconciliation(context.applicationContext),
            os = AndroidResetOsReconciliation(context.applicationContext),
            generationSwitch =
                GenerationSwitchCoordinator(
                    journal = journal,
                    activePointer = pointer,
                    generationLayout = layout,
                ),
        )

    // Read-only evidence port for a quarantined V2 restore; never an activation
    // permit. The live pre-switch admission remains V1-only.
    private val v2RecoveryProbe by lazy {
        FaroV5QuarantinedV2RecoveryProbe(layout, journal, pointer)
    }

    internal fun inspectQuarantinedV2Restore(
        operationId: String,
    ): V2QuarantinedRecoveryEvidence =
        v2RecoveryProbe.inspect(MaintenanceOperationId(operationId))

    // Internal-only health inspection of an already-switched V2 generation.
    // Not used by V1 startup, active pointer switching, replay or readmission.
    private val isolatedV2RuntimeHealth by lazy {
        FaroV5V2RecoveryRuntimeHealthOwner.canonical(
            layout, journal, pointer, quiescence, root.materialWriteGate
        )
    }

    internal fun inspectPostSwitchV2RuntimeHealth(
        operationId: String,
    ): V2RuntimeHealth =
        isolatedV2RuntimeHealth.verify(
            ar.com.faro.v5.maintenance.MaintenanceOperationId(operationId)
        )

    /**
     * Dormant F3 Android host boundary. Explicit consent is required before
     * encrypted private staging; the existing SAF adapter owns external export
     * and verified outcome. V1 remains unchanged while V2 is uncertified.
     */
    internal fun prepareConsentedFiveStoreExport(
        operationId: String,
        backupSetId: String,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
        userConfirmed: Boolean,
        externalShareAcknowledged: Boolean,
        effectIdentity: String,
    ): PreparedBackupExternalWriteSource {
        check(FaroV5HostCertification.V2_ACTIVE_ENABLED) {
            "five-store export is unavailable before complete V2 certification"
        }
        check(root.startupReprojectionState ==
            FaroV5CompositionRoot.StartupReprojectionState.PASS) {
            "startup recovery must complete before five-store export"
        }
        val active = checkNotNull(pointer.read()?.generationId?.value) {
            "no active generation for five-store export"
        }
        val admitted = {
            FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                pointer.read()?.generationId?.value == active &&
                root.startupReprojectionState ==
                    FaroV5CompositionRoot.StartupReprojectionState.PASS
        }
        check(admitted())
        return FaroV5ActiveGenerationGraphOwner.open(
            storageRoot, active, root.materialWriteGate,
            FaroV5HostCertification.V2_ACTIVE_ENABLED,
        ).use { graph ->
            FaroV5FiveStoreExportPreparation(
                graph = graph,
                quiescence = quiescence,
                archiveStore = fiveStorePrivateArchiveStore,
                activeAdmission = admitted,
            ).prepare(
                operationId = MaintenanceOperationId(operationId),
                backupSetId = BackupSetId(backupSetId),
                productVersion = productVersion,
                createdAtEpochMillis = createdAtEpochMillis,
                passphrase = passphrase,
                consent = FiveStoreBackupExportConsent(
                    confirmedByUser = userConfirmed,
                    acknowledgedExternalShare = externalShareAcknowledged,
                ),
                effectIdentity = effectIdentity,
            )
        }
    }


    /**
     * F3 Android SAF boundary. Only a certified active V2 generation can
     * prepare an export. A process-local session binds the staged encrypted
     * artifact to the exact generation; no URI or passphrase is persisted.
     * V2 remains OFF until the original F1-F4/T4 certification.
     */
    internal fun prepareConsentedFiveStoreSafExport(
        operationId: String,
        backupSetId: String,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
        userConfirmed: Boolean,
        externalShareAcknowledged: Boolean,
        effectIdentity: String,
    ): FiveStoreSafExportSession {
        val generationId = checkNotNull(pointer.read()?.generationId?.value) {
            "five-store export requires an active generation"
        }
        val source = prepareConsentedFiveStoreExport(
            operationId, backupSetId, productVersion, createdAtEpochMillis,
            passphrase, userConfirmed, externalShareAcknowledged, effectIdentity,
        )
        check(pointer.read()?.generationId?.value == generationId) {
            "generation changed while preparing five-store export"
        }
        return FiveStoreSafExportSession(source, generationId)
    }

    internal fun createConsentedFiveStoreExportPicker(
        session: FiveStoreSafExportSession,
        userConfirmed: Boolean,
        externalShareAcknowledged: Boolean,
    ): Intent {
        check(userConfirmed && externalShareAcknowledged) {
            "external backup export requires explicit user consent"
        }
        check(exportSessionAdmitted(session)) {
            "five-store export session no longer has active authority"
        }
        return backupSafAdapter.createExportDocumentIntent(session.source)
    }

    /**
     * A cancelled picker has no external effect. Once a provider URI is
     * returned, recheck consent and generation BEFORE opening it. A failed
     * admission records FAILED, never a false successful external receipt.
     * The existing adapter verifies bytes/hash and preserves EFFECT_UNKNOWN.
     */
    internal fun completeConsentedFiveStoreExportPicker(
        session: FiveStoreSafExportSession,
        resolver: ContentResolver,
        destination: Uri?,
        cancellationSignal: CancellationSignal,
        userConfirmed: Boolean,
        externalShareAcknowledged: Boolean,
    ): BackupExternalEffectReceipt {
        if (destination == null) {
            return backupSafAdapter.exportPickerCancelled(session.source)
        }
        if (!userConfirmed || !externalShareAcknowledged || !exportSessionAdmitted(session)) {
            return backupSafAdapter.exportDeniedBeforeWrite(
                session.source, "export_consent_or_generation_changed"
            )
        }
        return backupSafAdapter.writeSelectedExportDocument(
            resolver, destination, session.source, cancellationSignal,
        )
    }

    private fun exportSessionAdmitted(session: FiveStoreSafExportSession): Boolean =
        FaroV5HostCertification.V2_ACTIVE_ENABLED &&
            root.startupReprojectionState ==
                FaroV5CompositionRoot.StartupReprojectionState.PASS &&
            pointer.read()?.generationId?.value == session.activeGenerationId &&
            runCatching { fiveStorePrivateArchiveStore.verify(session.source.artifact) }
                .getOrDefault(false)

    /**
     * RESTORE ingress stays quarantined: no pointer switch, second authority,
     * automatic Apply or OS effects. Picker URI is one-shot, never journaled.
     */
    internal fun createConsentedFiveStoreRestorePicker(
        operationId: String,
        targetGenerationId: String,
        userConfirmed: Boolean,
    ): Intent {
        check(userConfirmed) { "restore picker requires explicit user confirmation" }
        requireRestorePickerAdmission(operationId, targetGenerationId)
        return backupSafAdapter.createRestoreDocumentIntent()
    }

    internal fun restoreConsentedFiveStorePickedDocument(
        operationId: String,
        targetGenerationId: String,
        resolver: ContentResolver,
        selected: Uri?,
        stagedAtEpochMillis: Long,
        cancellationSignal: CancellationSignal,
        passphrase: CharArray,
        userConfirmed: Boolean,
    ): FiveStoreJournalValidationResult? {
        if (selected == null) return null // picker cancelled: no staging or activation
        check(userConfirmed) { "restore ingress requires explicit user confirmation" }
        requireRestorePickerAdmission(operationId, targetGenerationId)
        val artifact = backupSafAdapter.stageSelectedRestoreArchive(
            resolver, selected, fiveStorePrivateArchiveStore,
            stagedAtEpochMillis, cancellationSignal,
        )
        // Keep a verified encrypted artifact on failure: a journal pin may
        // already exist and recovery must never lose its authenticated input.
        return restoreConsentedFiveStoreStage(
            operationId, targetGenerationId, artifact, passphrase, userConfirmed,
        )
    }

    private fun requireRestorePickerAdmission(
        operationId: String,
        targetGenerationId: String,
    ) {
        check(root.startupReprojectionState ==
            FaroV5CompositionRoot.StartupReprojectionState.PASS) {
            "startup recovery must complete before restore ingress"
        }
        val op = MaintenanceOperationId(operationId)
        val target = StorageGenerationId(targetGenerationId)
        val entry = checkNotNull(journal.read(op)) {
            "restore ingress requires an existing journal"
        }
        check(entry.kind == MaintenanceOperationKind.RESTORE &&
            entry.targetGenerationId == target &&
            !entry.materialWritesAccepted &&
            entry.phase in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) &&
            pointer.read()?.generationId == entry.previousGenerationId) {
            "restore ingress journal or predecessor authority is invalid"
        }
    }

    /**
     * F3 quarantined restore host: accepts only a verified private encrypted
     * artifact, explicit user confirmation and an existing RESTORE journal.
     * The same host-wide gate protects five stores and STAGED->VALIDATED.
     * NEVER switches the active pointer, admits V2 writes or performs OS effects.
     */
    internal fun restoreConsentedFiveStoreStage(
        operationId: String,
        targetGenerationId: String,
        artifact: StagedPortableBackupArchive,
        passphrase: CharArray,
        userConfirmed: Boolean,
    ): FiveStoreJournalValidationResult {
        check(userConfirmed) { "five-store restore requires explicit user confirmation" }
        check(root.startupReprojectionState ==
            FaroV5CompositionRoot.StartupReprojectionState.PASS) {
            "startup recovery must complete before quarantined restore"
        }
        val op = MaintenanceOperationId(operationId)
        val targetId = StorageGenerationId(targetGenerationId)
        val journalEntry = checkNotNull(journal.read(op)) {
            "quarantined restore requires an existing journal"
        }
        check(journalEntry.kind == MaintenanceOperationKind.RESTORE &&
            journalEntry.targetGenerationId == targetId &&
            !journalEntry.materialWritesAccepted &&
            journalEntry.phase in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) &&
            pointer.read()?.generationId == journalEntry.previousGenerationId) {
            "quarantined restore journal or predecessor authority is invalid"
        }

        val privateStore = FilePortableBackupArchiveStore(
            storageRoot.resolve("backup").resolve("five-store-staged")
        )
        check(privateStore.verify(artifact)) {
            "encrypted restore artifact failed private integrity verification"
        }
        val encrypted = privateStore.readBytes(artifact)
        try {
            val target = layout.requireStaged(targetId)
            return root.openQuarantinedV2WithHostGate(target).use { graph ->
                val archiveIdentity = FileRestoreArchiveIdentity(storageRoot)
                if (journalEntry.phase == BootstrapPhase.STAGED) {
                    val admission = FaroV5RestoreJournalAdmission(
                        journal, pointer, targetId, quiescence,
                    )
                    val staged = FaroV5FiveStoreStagedRestore(
                        storageRoot, target, graph, admission,
                        archiveIdentity = archiveIdentity,
                    ).restoreAuthenticated(op, encrypted, passphrase)
                    check(staged is FiveStoreStagedRestoreResult.StagedVerified) {
                        "authenticated five-store restore did not complete staging"
                    }
                }
                FaroV5StagedRestoreJournalValidator(
                    storageRoot, target, graph, journal, pointer,
                    archiveIdentity, quiescence,
                ).validateAuthenticatedStage(op, encrypted, passphrase)
            }
        } finally {
            encrypted.fill(0)
        }
    }

    fun pendingBootstrapKinds(): Set<MaintenanceOperationKind> =
        journal.pending().map { it.kind }.toSet()

    fun reconcileInterruptedExternalEffects() {
        backupExternalEffects.reconcileInterruptedWrites()
        portabilityExternalEffects.reconcileInterruptedWrites()
    }

    fun recoverPendingResetsOrThrow() {
        val results = resetLifecycle.recoverPending()
        val incomplete =
            results.filterNot { result ->
                result is ResetExecutionResult.Completed
            }
        check(incomplete.isEmpty()) {
            "pending reset recovery did not converge: " +
                incomplete.joinToString { it::class.java.simpleName }
        }
    }

    fun recoverPendingActivations(): List<GenerationActivationResult> =
        activation.recoverPending()

    fun recoverPendingActivationsOrThrow() {
        val results = recoverPendingActivations()
        val blocked =
            results.filterIsInstance<GenerationActivationResult.Blocked>()
        check(blocked.isEmpty()) {
            "pending generation activation recovery blocked: " +
                blocked.joinToString { it.reason.name }
        }
    }

    fun executeReset(
        request: ResetExecutionRequest,
    ): ResetExecutionResult =
        resetLifecycle.execute(request)

    fun activateValidated(
        operationId: String,
    ): GenerationActivationResult =
        activation.activate(
            ar.com.faro.v5.maintenance.MaintenanceOperationId(operationId)
        )

    fun isReplayFenceInstalled(
        kind: MaintenanceOperationKind,
        generationId: String,
    ): Boolean =
        replayFence.isInstalled(kind, StorageGenerationId(generationId))
}
