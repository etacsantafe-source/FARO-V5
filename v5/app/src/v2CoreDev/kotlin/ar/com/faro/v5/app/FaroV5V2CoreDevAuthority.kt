package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import kotlinx.coroutines.flow.merge
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.surface.FaroV5SearchReadPort
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * Compiled ONLY in the v2CoreDev Android variant; not present in shipping or ordinary debug.
 * Local-only five-owner activation with a durable bootstrap journal and real active pointer.
 * No model gateway, Apply port, external action or OS-delivery port is constructed.
 */
internal class FaroV5V2CoreDevAuthority(private val root: Path) {
    private val gate = SingleProcessMaterialWriteGate()
    private val initialId = StorageGenerationId("v2-core-dev-legacy-base-1")
    private val targetId = StorageGenerationId("v2-core-dev-workspace-1")
    private val operationId = MaintenanceOperationId("v2-core-dev-first-activation-1")

    fun open(): Session {
        val normalized = root.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized)) { "DEV root must not be a symlink" }
        Files.createDirectories(normalized)
        check(Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
        // The existing first-generation bootstrap establishes a recoverable V1 authority.
        // Never initialize it again after a real pointer was durably published.
        val pointer = FileActiveGenerationPointer(normalized)
        if (pointer.read() == null) {
            ActiveGenerationStorageLocator(normalized).ensureInitialGenerationAndGetDirectory(
                initialId.value, "v2-core-dev-recovery-base-1",
            )
        }
        val layout = FileStorageGenerationLayout(normalized)
        val journal = FileBootstrapJournal(normalized)
        val replayFence = FileGenerationReplayFence(normalized)
        val quiescence = MaterialWriteGateQuiescence(gate)
        val coordinator = ValidatedGenerationActivationCoordinator(
            journal = journal,
            activePointer = pointer,
            generationLayout = layout,
            quiescence = quiescence,
            generationSwitch = GenerationSwitchCoordinator(journal, pointer, layout),
            runtime = GenerationRuntimeActivationPort { _, generationId ->
                FaroV5ActiveGenerationGraphOwner.open(
                    normalized, generationId.value, gate, true,
                ).use { graph -> graph.workspace != null && graph.verifyHealthy() }
            },
            replayFence = replayFence,
            // No delivery projection is made by this development-only authority.
            projections = GenerationProjectionReconciliationPort { _, _ -> Unit },
            preSwitchAdmission = FaroV5PreSwitchHostAdmission(
                layout = layout,
                activePointer = pointer,
                v2HostCertified = true,
                verifyQuarantinedV2 = { staged ->
                    FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                        staged, CanonicalPreparedPlanEnvelopeVerifier, gate,
                    ).use { it.workspace != null && it.verifyHealthy() }
                },
            ),
        )

        val descriptor = StorageGenerationDescriptor(
            targetId, "v2-core-dev-five-store-schema-1", 1L,
        )
        fun advance(from: BootstrapJournalEntry, phase: BootstrapPhase): BootstrapJournalEntry {
            val next = BootstrapTransitionPolicy.transition(from, phase)
            check(journal.compareAndSet(from, next)) { "DEV bootstrap CAS transition failed" }
            return next
        }
        fun initializeFiveStores(dir: Path) {
            RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(
                dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            ).use { }
            WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
            AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
            ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        }

        when (pointer.read()?.generationId) {
            initialId -> {
                // Resume only the exact pending DEV operation. A completed, foreign
                // or already-writing journal cannot be replayed against the V1 pointer.
                val pending = journal.pending()
                check(pending.all { it.operationId == operationId }) {
                    "DEV startup found a foreign pending bootstrap"
                }
                val prior = journal.read(operationId)
                var entry = if (prior == null) {
                    check(pending.isEmpty()) { "DEV startup has an unexpected pending bootstrap" }
                    BootstrapJournalEntry(
                        operationId, MaintenanceOperationKind.RESTORE,
                        initialId, targetId, BootstrapPhase.PREPARING,
                    ).also { journal.create(it) }
                } else {
                    check(pending.size == 1 && pending.single() == prior &&
                        prior.kind == MaintenanceOperationKind.RESTORE &&
                        prior.previousGenerationId == initialId &&
                        prior.targetGenerationId == targetId &&
                        !prior.materialWritesAccepted) {
                        "DEV recovery journal identity, owner or phase mismatch"
                    }
                    prior
                }
                check(pointer.read()?.generationId == initialId) {
                    "DEV V1 pointer changed before bootstrap recovery"
                }
                when (entry.phase) {
                    BootstrapPhase.PREPARING -> {
                        // Existing V2 pre-database metadata can be resumed exactly.
                        // If none exists, stageWorkspaceV2 requires a never-used path.
                        // SQLite residue, symlinks or an orphaned empty directory
                        // fail closed in BOTH paths: never delete or adopt them.
                        val staged = try {
                            layout.resumeWorkspaceV2(descriptor)
                        } catch (_: IllegalStateException) {
                            layout.stageWorkspaceV2(descriptor)
                        }
                        initializeFiveStores(staged.directory)
                        entry = advance(entry, BootstrapPhase.STAGED)
                    }
                    BootstrapPhase.STAGED,
                    BootstrapPhase.VALIDATED -> Unit
                    else -> error("DEV V1 recovery does not admit bootstrap phase ${entry.phase}")
                }
                if (entry.phase == BootstrapPhase.STAGED) {
                    // Never reinitialize a partially written SQLite set.
                    val staged = layout.requireStaged(targetId)
                    FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                        staged, CanonicalPreparedPlanEnvelopeVerifier, gate,
                    ).use { check(it.workspace != null && it.verifyHealthy()) }
                    entry = advance(entry, BootstrapPhase.VALIDATED)
                }
                check(entry.phase == BootstrapPhase.VALIDATED)
                check(pointer.read()?.generationId == initialId) {
                    "DEV V1 pointer changed before validated activation"
                }
                val result = if (prior == null) {
                    coordinator.activate(operationId)
                } else {
                    coordinator.recover(operationId)
                }
                check(result is GenerationActivationResult.Activated) {
                    "DEV five-owner recovery/promotion not admitted: $result"
                }
            }
            targetId -> {
                // Crash recovery is allowed only for the same durable operation.
                check(journal.pending().all { it.operationId == operationId }) {
                    "DEV encountered a foreign maintenance operation"
                }
                val pending = journal.pending()
                if (pending.isNotEmpty()) {
                    check(pending.single().phase in setOf(
                        BootstrapPhase.VALIDATED,
                        BootstrapPhase.SWITCH_PREPARED,
                        BootstrapPhase.SWITCHED,
                        BootstrapPhase.HEALTH_VERIFIED,
                        BootstrapPhase.COMPLETED,
                    )) { "DEV pending journal phase requires manual recovery" }
                    check(coordinator.recover(operationId) is GenerationActivationResult.Activated) {
                        "DEV activation recovery did not converge"
                    }
                }
            }
            else -> error("DEV active generation has unexpected identity")
        }

        val finalEntry = checkNotNull(journal.read(operationId))
        check(finalEntry.phase == BootstrapPhase.COMPLETED &&
            finalEntry.materialWritesAccepted &&
            pointer.read()?.generationId == targetId &&
            replayFence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)) {
            "DEV activation lacks committed replay fence or authority"
        }
        // Pin the DURABLE pointer sequence, not only its reusable generation ID.
        // A->B->A must never revive an already-mounted DEV Search/read session.
        val openingPointer = checkNotNull(pointer.read()) { "DEV active pointer missing" }
        check(openingPointer.generationId == targetId) { "DEV active generation changed" }
        val pinnedPointerSequence = openingPointer.sequence
        val graph = FaroV5ActiveGenerationGraphOwner.open(normalized, targetId.value, gate, true)
        val readAccesses = mutableListOf<FaroV5V2CoreDevSearchReadAuthority>()
        var pointerObserver: Runnable? = null
        return try {
            val workspace = checkNotNull(graph.workspace)
            val alive = AtomicBoolean(true)
            val current = Supplier { pointer.read()?.generationId?.value ?: "" }
            val ready = BooleanSupplier {
                val observed = pointer.read()
                alive.get() && observed != null &&
                    observed.generationId == targetId &&
                    observed.sequence == pinnedPointerSequence &&
                    journal.read(operationId) == finalEntry &&
                    replayFence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)
            }
            val reads = FaroV5ActiveChatReadSession(
                targetId.value, workspace, workspace, current, ready,
            )
            val selection = FaroV5ActiveChatSelectionSession(
                targetId.value, workspace, gate, current, ready,
            )
            val composer = FaroV5ActiveChatComposerSession(
                targetId.value, workspace, gate, current, ready,
            )
            // DEV search authority is scoped to THIS verified host session.
            // Never persist a grant or reuse a Workspace write authority token.
            val access = FaroV5V2CoreDevSearchReadAuthority {
                ready.getAsBoolean()
            }
            readAccesses += access
            access.grantAfterVerifiedOpen()
            val observer = Runnable { readAccesses.forEach { it.invalidate() } }
            pointer.addChangeListener(observer)
            pointerObserver = observer
            val canonical = FaroV5WorkspaceSearchReadSource(
                targetId.value, workspace,
                { pointer.read()?.generationId?.value },
                { access.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val workspaceSearch = FaroV5WorkspaceSearchSurfaceAdapter(
                canonical, selection.port, access.invalidations,
            )
            // Independent DEV-only grants for Reality Finance, Objectives and
            // Matters; none of these epochs reuses the Workspace read grant.
            fun grantRealityRead(): FaroV5V2CoreDevSearchReadAuthority =
                FaroV5V2CoreDevSearchReadAuthority { ready.getAsBoolean() }.also {
                    readAccesses += it
                    it.grantAfterVerifiedOpen()
                }
            val financeGrant = grantRealityRead()
            val objectiveGrant = grantRealityRead()
            val matterGrant = grantRealityRead()
            val financeSource = FaroV5FinanceSearchReadSource(
                targetId.value,
                FinanceSnapshotReadApi { graph.reality.financePersistence.read() },
                { pointer.read()?.generationId?.value },
                { financeGrant.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val objectiveSource = FaroV5ObjectivesSearchReadSource(
                targetId.value, graph.reality.objectivePersistence,
                { pointer.read()?.generationId?.value },
                { objectiveGrant.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val matterSource = FaroV5MattersSearchReadSource(
                targetId.value, graph.reality.matterPersistence,
                { pointer.read()?.generationId?.value },
                { matterGrant.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val search = FaroV5FederatedSearchSurfaceAdapter(
                canonical, workspaceSearch, financeSource, objectiveSource, matterSource,
                merge(
                    workspaceSearch.invalidations,
                    financeGrant.invalidations,
                    objectiveGrant.invalidations,
                    matterGrant.invalidations,
                ),
            )
            Session(
                graph, alive, reads.workspaceReadPort, reads.timelineReadPort,
                selection.port, composer.port, search, readAccesses.toList(), pointer, observer,
            )
        } catch (failure: Throwable) {
            pointerObserver?.let { pointer.removeChangeListener(it) }
            readAccesses.forEach { it.close() }
            graph.close()
            throw failure
        }
    }

    class Session internal constructor(
        private val graph: FaroV5GenerationStoreGraph,
        private val alive: AtomicBoolean,
        val workspaceReadPort: ConversationWorkspaceReadPort,
        val timelineReadPort: ConversationTimelineReadPort,
        val selectionPort: ConversationWorkspaceSelectionPort,
        val composerPort: ConversationComposerPort,
        val searchReadPort: FaroV5SearchReadPort,
        private val searchAccesses: List<FaroV5V2CoreDevSearchReadAuthority>,
        private val pointer: FileActiveGenerationPointer,
        private val pointerObserver: Runnable,
    ) : AutoCloseable {
        override fun close() {
            if (alive.compareAndSet(true, false)) {
                searchAccesses.forEach { it.close() }
                pointer.removeChangeListener(pointerObserver)
                graph.close()
            }
        }
    }
}
