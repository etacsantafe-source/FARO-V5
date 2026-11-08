package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Path

/**
 * S3.A5: isolated DEV bridge from the A5 PREPARED/VALIDATED journal to the
 * existing canonical activation coordinator, with no second pointer writer.
 *
 * This owner is deliberately NOT mounted by Android/Chat/CompositionRoot.
 * The future DEV host must inject the SAME process-wide gate and quiescence
 * used by its live five-store graphs and CLOSE old sessions before invoking.
 * certifiedHost is merely an explicit internal host admission, never a
 * human ACL, Apply grant or authority to enable shipping V2.
 */
class FaroV5Planning8DevCutoverOwner internal constructor(
    private val storageRoot: Path,
    private val materialWriteGate: MaterialWriteGate,
    private val quiescence: MaterialWriteQuiescencePort,
    private val planVerifier: PreparedPlanEnvelopeVerificationPort,
    private val certifiedHost: Boolean,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val targetRuntimeHealthy: () -> Boolean,
) {
    constructor(
        storageRoot: Path,
        materialWriteGate: MaterialWriteGate,
        quiescence: MaterialWriteQuiescencePort,
        planVerifier: PreparedPlanEnvelopeVerificationPort,
        certifiedHost: Boolean,
    ) : this(storageRoot, materialWriteGate, quiescence, planVerifier,
        certifiedHost, {}, { true })

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val journal = FileBootstrapJournal(storageRoot)
    private val replayFence = FileGenerationReplayFence(storageRoot)

    /** First promotion only; never initiates or resumes a partial fresh stage. */
    fun activateValidated(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedPrevious: ActiveGenerationPointer,
    ): GenerationActivationResult {
        check(certifiedHost) { "A5 DEV cutover requires an explicit certified host" }
        val record = requireOperation(operationId, descriptor, expectedPrevious)
        check(record.phase == BootstrapPhase.VALIDATED &&
            journal.pending().singleOrNull() == record &&
            pointer.read() == expectedPrevious) {
            "A5 DEV promotion requires exact validated journal and old pointer"
        }
        val result = coordinator(operationId, descriptor, expectedPrevious)
            .activate(operationId)
        verifyOutcome(result, operationId, descriptor, expectedPrevious)
        return result
    }

    /** Recovery only of THIS durable operation; never auto-adopt PREPARING. */
    fun recover(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedPrevious: ActiveGenerationPointer,
    ): GenerationActivationResult {
        check(certifiedHost) { "A5 DEV recovery requires an explicit certified host" }
        val record = requireOperation(operationId, descriptor, expectedPrevious)
        val targetPointer = targetPointer(expectedPrevious, descriptor.generationId)
        val rolledBackPointer = rollbackPointer(expectedPrevious)
        val observed = pointer.read()
        val eligible = when (record.phase) {
            BootstrapPhase.VALIDATED -> observed == expectedPrevious
            BootstrapPhase.SWITCH_PREPARED ->
                observed == expectedPrevious || observed == targetPointer
            BootstrapPhase.SWITCHED,
            BootstrapPhase.HEALTH_VERIFIED,
            BootstrapPhase.CLEANUP_PENDING,
            BootstrapPhase.COMPLETED -> observed == targetPointer
            BootstrapPhase.ROLLBACK_PREPARED ->
                observed == targetPointer || observed == rolledBackPointer
            BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            BootstrapPhase.ROLLBACK_VERIFIED -> observed == rolledBackPointer
            else -> false
        }
        check(eligible) { "A5 DEV recovery refuses stale or foreign pointer sequence" }
        check(journal.pending().all { it.operationId == operationId }) {
            "A5 DEV recovery blocked by another pending maintenance operation"
        }
        if (record.phase == BootstrapPhase.COMPLETED) {
            check(record.materialWritesAccepted &&
                replayFence.isInstalled(
                    MaintenanceOperationKind.RESTORE, descriptor.generationId
                )) { "A5 DEV completed recovery lacks durable replay fence" }
        }
        val result = coordinator(operationId, descriptor, expectedPrevious)
            .recover(operationId)
        verifyOutcome(result, operationId, descriptor, expectedPrevious)
        return result
    }

    private fun requireOperation(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedPrevious: ActiveGenerationPointer,
    ): BootstrapJournalEntry {
        val record = checkNotNull(journal.read(operationId)) {
            "A5 DEV cutover journal missing"
        }
        check(record.operationId == operationId &&
            record.kind == MaintenanceOperationKind.RESTORE &&
            record.previousGenerationId == expectedPrevious.generationId &&
            record.targetGenerationId == descriptor.generationId &&
            record.previousGenerationId != record.targetGenerationId) {
            "A5 DEV cutover operation does not match frozen journal identity"
        }
        val staged = layout.requireStaged(descriptor.generationId)
        check(staged.descriptor == descriptor &&
            ExistingGenerationFormatReader.requireAndRead(staged) ==
                GenerationStoreFormat.WORKSPACE_V2) {
            "A5 DEV cutover target descriptor/marker mismatch"
        }
        return record
    }

    private fun targetPointer(
        previous: ActiveGenerationPointer,
        targetId: StorageGenerationId,
    ): ActiveGenerationPointer = ActiveGenerationPointer(
        Math.addExact(previous.sequence, 1L), targetId,
    )

    private fun rollbackPointer(previous: ActiveGenerationPointer): ActiveGenerationPointer =
        ActiveGenerationPointer(Math.addExact(previous.sequence, 2L), previous.generationId)

    private fun coordinator(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedPrevious: ActiveGenerationPointer,
    ): ValidatedGenerationActivationCoordinator {
        val targetId = descriptor.generationId
        val expectedTarget = targetPointer(expectedPrevious, targetId)
        val expectedRollback = rollbackPointer(expectedPrevious)
        return ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = quiescence,
            generationSwitch = GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                if (kind != MaintenanceOperationKind.RESTORE) false
                else runCatching {
                    val active = checkNotNull(pointer.read())
                    when (id) {
                        targetId -> {
                            check(active == expectedTarget && targetRuntimeHealthy())
                            val staged = layout.requireStaged(targetId)
                            FaroV5GenerationStoreGraph.openActivePlanning8ForCertifiedHost(
                                storageRoot, staged, active.sequence, planVerifier,
                                materialWriteGate, v2HostCertified = certifiedHost,
                            ).use { graph ->
                                graph.workspace != null &&
                                    graph.reality.maintenance.inspect().schemaVersion == 8 &&
                                    graph.verifyHealthy() && pointer.read() == expectedTarget
                            }
                        }
                        expectedPrevious.generationId -> {
                            // Rollback can reopen ONLY the already existing DEV
                            // Reality7 predecessor. Never migrate an active Reality8.
                            check(active == expectedPrevious || active == expectedRollback)
                            val previous = layout.requireStaged(id)
                            FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                                previous, planVerifier, materialWriteGate,
                            ).use { graph ->
                                graph.workspace != null &&
                                    graph.reality.maintenance.inspect().schemaVersion == 7 &&
                                    graph.verifyHealthy() && pointer.read() == active
                            }
                        }
                        else -> false
                    }
                }.getOrDefault(false)
            },
            replayFence = replayFence,
            projections = GenerationProjectionReconciliationPort { _, _ ->
                // DEV-only, no OS or historical notification redelivery.
                Unit
            },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                if (!certifiedHost || kind != MaintenanceOperationKind.RESTORE ||
                    id != targetId
                ) false else runCatching {
                    val record = checkNotNull(journal.read(operationId))
                    check(record.operationId == operationId &&
                        record.targetGenerationId == targetId &&
                        record.previousGenerationId == expectedPrevious.generationId)
                    val staged = layout.requireStaged(id)
                    check(staged.descriptor == descriptor &&
                        ExistingGenerationFormatReader.requireAndRead(staged) ==
                            GenerationStoreFormat.WORKSPACE_V2)
                    val active = checkNotNull(pointer.read())
                    if (active == expectedPrevious) {
                        check(record.phase in setOf(
                            BootstrapPhase.VALIDATED, BootstrapPhase.SWITCH_PREPARED,
                        ))
                        // Exact schema8 preflight BEFORE any staged re-open.
                        // A tampered schema7 cannot be upgraded here.
                        StagedSqlitePreflight.requireAll(staged.directory, listOf(
                            StagedSqlitePreflight.Schema(
                                GenerationStoreFiles.REALITY, 8,
                                listOf("reminder_state", "monthly_plan_state"),
                            ),
                        ))
                        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged)
                            .use { graph ->
                                graph.workspace != null &&
                                    graph.reality.maintenance.inspect().schemaVersion == 8 &&
                                    graph.verifyHealthy() &&
                                    pointer.read() == expectedPrevious
                            }
                    } else if (active == expectedTarget) {
                        FaroV5GenerationStoreGraph.openActivePlanning8ForCertifiedHost(
                            storageRoot, staged, active.sequence, planVerifier,
                            materialWriteGate, v2HostCertified = certifiedHost,
                        ).use { graph ->
                            graph.workspace != null &&
                                graph.reality.maintenance.inspect().schemaVersion == 8 &&
                                graph.verifyHealthy() && pointer.read() == expectedTarget
                        }
                    } else false
                }.getOrDefault(false)
            },
        )
    }

    private fun verifyOutcome(
        result: GenerationActivationResult,
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedPrevious: ActiveGenerationPointer,
    ) {
        when (result) {
            is GenerationActivationResult.Activated -> {
                check(result.operationId == operationId &&
                    result.targetGenerationId == descriptor.generationId &&
                    pointer.read() == targetPointer(expectedPrevious, descriptor.generationId) &&
                    journal.read(operationId)?.phase == BootstrapPhase.COMPLETED &&
                    journal.read(operationId)?.materialWritesAccepted == true &&
                    replayFence.isInstalled(
                        MaintenanceOperationKind.RESTORE, descriptor.generationId,
                    )) { "A5 DEV activation returned without completed replay fence" }
            }
            is GenerationActivationResult.RolledBack -> {
                check(pointer.read() == rollbackPointer(expectedPrevious) &&
                    journal.read(operationId)?.phase == BootstrapPhase.ROLLBACK_VERIFIED &&
                    !replayFence.isInstalled(
                        MaintenanceOperationKind.RESTORE, descriptor.generationId,
                    )) { "A5 DEV rollback returned without previous authority" }
            }
            is GenerationActivationResult.Blocked -> Unit
        }
    }
}
