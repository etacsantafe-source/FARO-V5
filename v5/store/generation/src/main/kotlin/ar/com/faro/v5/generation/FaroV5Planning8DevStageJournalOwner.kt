package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Path

/**
 * S3.A5 — explicit DEV-only preparation of a fresh, still-quarantined Reality8.
 *
 * This is the journal handoff to the EXISTING activation coordinator, not an
 * alternative switcher. The host must inject its ONE global material-write
 * quiescence. No Android caller is wired; no pointer switch, replay fence,
 * Apply/Undo, user read grant or silent cleanup can occur here.
 *
 * PREPARING may contain a partially materialized SQLite set after a crash.
 * It is deliberately NOT auto-adopted or restarted. STAGED is recoverable
 * only after schema8 exact preflight and all-five-store verification.
 */
class FaroV5Planning8DevStageJournalOwner internal constructor(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val stageFresh: (StorageGenerationDescriptor) -> StagedStorageGeneration,
    private val afterStaged: () -> Unit,
) {
    constructor(storageRoot: Path, quiescence: MaterialWriteQuiescencePort) :
        this(storageRoot, quiescence,
            { descriptor ->
                FaroV5WorkspaceV2StageOwner(storageRoot)
                    .stageFreshPlanning8AndVerify(descriptor)
            }, {})

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val journal = FileBootstrapJournal(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)

    /**
     * A single, brand-new target and operation only. Journal PREPARING is
     * durable BEFORE any new SQLite is written; pointer is pinned ID+sequence.
     */
    fun prepareFresh(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedActive: ActiveGenerationPointer,
    ): StagedStorageGeneration =
        quiescence.withMaterialWritesQuiesced(operationId) {
            check(pointer.read() == expectedActive) {
                "A5 DEV initial full active pointer is stale"
            }
            check(expectedActive.generationId != descriptor.generationId) {
                "A5 DEV cannot stage on the active generation"
            }
            check(journal.read(operationId) == null && journal.pending().isEmpty()) {
                "A5 DEV cannot overwrite an operation or coexist with pending journal"
            }
            val preparing = BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = expectedActive.generationId,
                targetGenerationId = descriptor.generationId,
                phase = BootstrapPhase.PREPARING,
            )
            journal.create(preparing)
            // On stage failure, PREPARING and any partial files stay quarantined.
            val staged = stageFresh(descriptor)
            check(pointer.read() == expectedActive && journal.read(operationId) == preparing &&
                journal.pending().singleOrNull() == preparing) {
                "A5 DEV stage materialized while authority changed"
            }
            val sealed = verifyExactlyEight(staged, descriptor, expectedActive)
            val stagedEntry = transition(preparing, BootstrapPhase.STAGED)
            // Test-only crash seam: no pointer or journal mutation after STAGED.
            afterStaged()
            val validated = transition(stagedEntry, BootstrapPhase.VALIDATED)
            check(pointer.read() == expectedActive &&
                journal.read(operationId) == validated) {
                "A5 DEV validated journal drifted from previous pointer"
            }
            sealed
        }

    /**
     * Idempotent STAGED -> VALIDATED recovery; never calls the fresh initializer.
     * PREPARING residue, switched pointers, completed/foreign operations and
     * schema7 candidates must be handled by a separately reviewed lifecycle.
     */
    fun recoverValidatedStage(
        operationId: MaintenanceOperationId,
        descriptor: StorageGenerationDescriptor,
        expectedActive: ActiveGenerationPointer,
    ): StagedStorageGeneration =
        quiescence.withMaterialWritesQuiesced(operationId) {
            val entry = checkNotNull(journal.read(operationId)) {
                "A5 DEV recovery requires an existing journal"
            }
            check(entry.operationId == operationId &&
                entry.kind == MaintenanceOperationKind.RESTORE &&
                entry.previousGenerationId == expectedActive.generationId &&
                entry.targetGenerationId == descriptor.generationId &&
                entry.phase in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) &&
                !entry.materialWritesAccepted &&
                journal.pending().singleOrNull() == entry &&
                pointer.read() == expectedActive) {
                "A5 DEV recovery refuses partial, foreign or switched lifecycle"
            }
            val staged = layout.requireStaged(descriptor.generationId)
            val sealed = verifyExactlyEight(staged, descriptor, expectedActive)
            val result = if (entry.phase == BootstrapPhase.STAGED) {
                transition(entry, BootstrapPhase.VALIDATED)
            } else entry
            check(journal.read(operationId) == result &&
                pointer.read() == expectedActive) {
                "A5 DEV recovered journal or pointer changed"
            }
            sealed
        }

    private fun verifyExactlyEight(
        staged: StagedStorageGeneration,
        descriptor: StorageGenerationDescriptor,
        expectedActive: ActiveGenerationPointer,
    ): StagedStorageGeneration {
        val canonical = layout.requireStaged(descriptor.generationId)
        check(staged.descriptor == descriptor && canonical.descriptor == descriptor &&
            staged.directory.toAbsolutePath().normalize() ==
                canonical.directory.toAbsolutePath().normalize() &&
            ExistingGenerationFormatReader.requireAndRead(canonical) ==
                GenerationStoreFormat.WORKSPACE_V2 &&
            pointer.read() == expectedActive) {
            "A5 DEV stage requires a canonical, non-active immutable descriptor"
        }
        // Unlike the original STAGED upgrade, recovery MUST NOT transform
        // schema7 (or an incomplete Reality) into schema8 as a side effect.
        StagedSqlitePreflight.requireAll(canonical.directory, listOf(
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.REALITY, 8,
                listOf("reminder_state", "monthly_plan_state"),
            ),
        ))
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(canonical).use { graph ->
            check(graph.generationId == descriptor.generationId.value &&
                graph.workspace != null &&
                graph.reality.maintenance.inspect().schemaVersion == 8 &&
                graph.verifyHealthy() && pointer.read() == expectedActive) {
                "A5 DEV stage failed five-owner Reality8 health"
            }
        }
        check(pointer.read() == expectedActive) {
            "A5 DEV previous pointer changed during exact health check"
        }
        return canonical
    }

    private fun transition(
        from: BootstrapJournalEntry,
        to: BootstrapPhase,
    ): BootstrapJournalEntry {
        val next = BootstrapTransitionPolicy.transition(from, to)
        check(journal.compareAndSet(from, next)) {
            "A5 DEV journal CAS phase transition failed"
        }
        return next
    }
}
