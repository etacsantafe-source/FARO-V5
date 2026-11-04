package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
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

        when (pointer.read()?.generationId) {
            initialId -> {
                // Never adopt partial or orphaned stages on restart.
                check(journal.pending().isEmpty()) {
                    "DEV startup found pending bootstrap; explicit recovery required"
                }
                val entry = BootstrapJournalEntry(
                    operationId, MaintenanceOperationKind.RESTORE,
                    initialId, targetId, BootstrapPhase.PREPARING,
                )
                journal.create(entry)
                val staged = layout.stageWorkspaceV2(
                    StorageGenerationDescriptor(
                        targetId, "v2-core-dev-five-store-schema-1", 1L,
                    ),
                )
                val dir = staged.directory
                RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
                CommercialEvidenceStore(
                    dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                ).use { }
                WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
                AuxiliaryDeliveryStore(
                    dir.resolve(GenerationStoreFiles.AUXILIARY).toString(),
                ).use { }
                ConversationWorkspaceStore(
                    dir.resolve(GenerationStoreFiles.WORKSPACE).toString(),
                ).use { }

                fun advance(from: BootstrapJournalEntry, phase: BootstrapPhase): BootstrapJournalEntry {
                    val next = BootstrapTransitionPolicy.transition(from, phase)
                    check(journal.compareAndSet(from, next)) { "DEV bootstrap CAS transition failed" }
                    return next
                }
                val stagedEntry = advance(entry, BootstrapPhase.STAGED)
                FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                    staged, CanonicalPreparedPlanEnvelopeVerifier, gate,
                ).use { check(it.workspace != null && it.verifyHealthy()) }
                advance(stagedEntry, BootstrapPhase.VALIDATED)
                val result = coordinator.activate(operationId)
                check(result is GenerationActivationResult.Activated) {
                    "DEV five-owner promotion not admitted: $result"
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
        val graph = FaroV5ActiveGenerationGraphOwner.open(normalized, targetId.value, gate, true)
        return try {
            val workspace = checkNotNull(graph.workspace)
            val alive = AtomicBoolean(true)
            val current = Supplier { pointer.read()?.generationId?.value ?: "" }
            val ready = BooleanSupplier {
                alive.get() && pointer.read()?.generationId == targetId &&
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
            Session(
                graph, alive, reads.workspaceReadPort, reads.timelineReadPort,
                selection.port, composer.port,
            )
        } catch (failure: Throwable) {
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
    ) : AutoCloseable {
        override fun close() {
            if (alive.compareAndSet(true, false)) graph.close()
        }
    }
}
