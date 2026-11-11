package ar.com.faro.v5.app

import android.app.Application
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
import ar.com.faro.v5.surface.FaroV5ActivityReadPort
import java.nio.file.Files
import java.util.UUID
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
    companion object {
        // One DEV material-write authority for all Activity sessions in this process.
        private val sharedGate = SingleProcessMaterialWriteGate()
    }
    private val gate = sharedGate
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
        // Recovery is allowed ONLY for a previously foreground-authorized
        // Reality8 journal. Normal Chat open never starts a new stage.
        val planning8 = FaroV5V2Planning8DevHost(normalized, gate)
        planning8.recoverPreviouslyAuthorizedOrFail()
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
            planning8.targetId -> {
                // Exact Reality8 operation was already recovered or verified.
                planning8.requireCompleted(checkNotNull(pointer.read()))
            }
            else -> error("DEV active generation has unexpected identity")
        }

        // Pin FULL pointer on every session: a past generation ID is never an
        // adequate authorization token after ABA or a Reality7→8 cutover.
        val openingPointer = checkNotNull(pointer.read()) { "DEV active pointer missing" }
        val activeId = openingPointer.generationId
        val oldFinalEntry = if (activeId == targetId) {
            checkNotNull(journal.read(operationId)).also {
                check(it.phase == BootstrapPhase.COMPLETED && it.materialWritesAccepted &&
                    replayFence.isInstalled(MaintenanceOperationKind.RESTORE, targetId))
            }
        } else {
            check(activeId == planning8.targetId)
            planning8.requireCompleted(openingPointer)
            null
        }
        val graph = if (activeId == planning8.targetId)
            FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
                normalized, activeId.value, openingPointer.sequence, gate, true,
            )
        else FaroV5ActiveGenerationGraphOwner.open(
            normalized, activeId.value, gate, true,
        )
        val readAccesses = mutableListOf<FaroV5V2CoreDevSearchReadAuthority>()
        var pointerObserver: Runnable? = null
        return try {
            val workspace = checkNotNull(graph.workspace)
            val alive = AtomicBoolean(true)
            val current = Supplier { pointer.read()?.generationId?.value ?: "" }
            val ready = BooleanSupplier {
                val observed = pointer.read()
                alive.get() && observed == openingPointer &&
                    if (activeId == targetId)
                        journal.read(operationId) == oldFinalEntry &&
                            replayFence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)
                    else planning8.isCommittedCurrent(openingPointer)
            }
            val reads = FaroV5ActiveChatReadSession(
                activeId.value, workspace, workspace, current, ready,
            )
            val selection = FaroV5ActiveChatSelectionSession(
                activeId.value, workspace, gate, current, ready,
            )
            val composer = FaroV5ActiveChatComposerSession(
                activeId.value, workspace, gate, current, ready,
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
                activeId.value, workspace,
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
                activeId.value,
                FinanceSnapshotReadApi { graph.reality.financePersistence.read() },
                { pointer.read()?.generationId?.value },
                { financeGrant.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val objectiveSource = FaroV5ObjectivesSearchReadSource(
                activeId.value, graph.reality.objectivePersistence,
                { pointer.read()?.generationId?.value },
                { objectiveGrant.epochOrNull() },
                { ready.getAsBoolean() },
            )
            val matterSource = FaroV5MattersSearchReadSource(
                activeId.value, graph.reality.matterPersistence,
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
                selection.port, composer.port, search, readAccesses.toList(), pointer, observer, openingPointer,
            )
        } catch (failure: Throwable) {
            pointerObserver?.let { pointer.removeChangeListener(it) }
            readAccesses.forEach { it.close() }
            graph.close()
            throw failure
        }
    }

    /**
     * Dedicated native foreground button only. The existing Android Session
     * must have been closed by the caller BEFORE requesting this operation.
     * Normal Chat/Activity/Pendientes never enter this path.
     */
    internal fun upgradePlanning8AfterExplicitForegroundRequest(
        foreground: () -> Boolean,
    ): Session {
        check(foreground()) { "DEV upgrade denied without foreground click" }
        val normalized = root.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized) &&
            Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
        FaroV5V2Planning8DevHost(normalized, gate)
            .promoteAfterExplicitForegroundRequest(foreground)
        return open()
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
        private val openingPointer: ActiveGenerationPointer,
    ) : AutoCloseable {
        @Volatile private var bookmarkFolders: FaroV5BookmarkFolderActionBinding? = null
        @Volatile private var bookmarkFoldersProbe: FaroV5AndroidSandboxPrincipal? = null
        @Volatile private var bookmarkReview: FaroV5BookmarkReviewPreparationBinding? = null
        @Volatile private var bookmarkReviewProbe: FaroV5AndroidSandboxPrincipal? = null
        private var bookmarkAuthority: FaroV5LocalInstallReadAuthority? = null
        private var bookmarkBinding: FaroV5BookmarkReadHostBinding? = null
        private var historyAuthority: FaroV5LocalInstallReadAuthority? = null
        private var historyBinding: FaroV5RealHistoryActivityHostBinding? = null
        private var pendingAuthority: FaroV5LocalInstallReadAuthority? = null
        private var pendingBinding: FaroV5N031ThreeOwnerDueHostBinding? = null

        /**
         * DEV-only entry: the caller MUST be the dedicated native button callback
         * while this exact Activity remains RESUMED. Never called on bootstrap,
         * normal Chat open, a model response or background task.
         */
        @Synchronized
        internal fun openActivityAfterTrustedForegroundRequest(
            application: Application,
            foreground: () -> Boolean,
        ): FaroV5ActivityReadPort? {
            if (!alive.get() || historyBinding != null ||
                application.packageName != "ar.com.faro.alpha.v2coredev" ||
                !runCatching { foreground() }.getOrDefault(false)
            ) return null
            val pinned = runCatching { pointer.read() }.getOrNull() ?: return null
            if (pinned.generationId.value != graph.generationId) return null
            val workspace = graph.workspace ?: return null
            val epoch = UUID.randomUUID().toString()
            val hostReady: () -> Boolean = {
                alive.get() && runCatching {
                    foreground() && pointer.read() == pinned
                }.getOrDefault(false)
            }
            var probe: FaroV5AndroidSandboxPrincipal? = null
            var access: FaroV5LocalInstallReadAuthority? = null
            var binding: FaroV5RealHistoryActivityHostBinding? = null
            var mounted = false
            return try {
                probe = FaroV5AndroidSandboxPrincipal(
                    expectedApplicationId = application.packageName,
                    pinned = pinned,
                    activePointer = pointer,
                    expectedHostEpoch = epoch,
                    hostEpochOrNull = { if (alive.get()) epoch else null },
                    facts = AndroidPlatformSandboxFactsReader(application),
                )
                access = FaroV5LocalInstallReadAuthority(
                    sandbox = probe,
                    activePointer = pointer,
                    hostReady = hostReady,
                    foregroundReady = foreground,
                )
                val principal = access.technicalPrincipalOrNull()
                if (principal == null || !hostReady() ||
                    !access.grantAfterTrustedForegroundReadAction(
                        setOf(FaroV5LocalReadDomain.REALITY_RECEIPT,
                              FaroV5LocalReadDomain.WORKSPACE_MESSAGE)
                    )
                ) null else {
                    binding = FaroV5RealHistoryActivityHostBinding(
                        generationId = graph.generationId,
                        pinned = pinned,
                        activePointer = pointer,
                        reality = graph.reality,
                        workspace = workspace,
                        principalId = principal,
                        expectedHostEpoch = epoch,
                        hostEpochOrNull = { if (alive.get()) epoch else null },
                        hostReady = hostReady,
                        readPolicy = access.historyReadPolicy(),
                        invalidations = access.invalidations,
                    )
                    if (!hostReady()) null else {
                        historyAuthority = access
                        historyBinding = binding
                        mounted = true
                        binding
                    }
                }
            } catch (_: Exception) {
                null
            } finally {
                if (!mounted) {
                    binding?.close()
                    access?.close()
                    probe?.close()
                }
            }
        }


        /** Sole caller is the dedicated native Guardados click while this Activity is RESUMED. */
        @Synchronized
        internal fun openBookmarksAfterTrustedForegroundRequest(
            application: Application,
            foreground: () -> Boolean,
        ): FaroV5BookmarkReadHostBinding? {
            if (!alive.get() || bookmarkBinding != null ||
                application.packageName != "ar.com.faro.alpha.v2coredev" ||
                !runCatching { foreground() && pointer.read() == openingPointer }.getOrDefault(false)
            ) return null
            val workspace = graph.workspace ?: return null
            val epoch = UUID.randomUUID().toString()
            val hostReady: () -> Boolean = {
                alive.get() && runCatching {
                    foreground() && pointer.read() == openingPointer
                }.getOrDefault(false)
            }
            var probe: FaroV5AndroidSandboxPrincipal? = null
            var access: FaroV5LocalInstallReadAuthority? = null
            var binding: FaroV5BookmarkReadHostBinding? = null
            var mounted = false
            return try {
                probe = FaroV5AndroidSandboxPrincipal(
                    expectedApplicationId = application.packageName,
                    pinned = openingPointer, activePointer = pointer,
                    expectedHostEpoch = epoch,
                    hostEpochOrNull = { if (alive.get()) epoch else null },
                    facts = AndroidPlatformSandboxFactsReader(application),
                )
                access = FaroV5LocalInstallReadAuthority(
                    sandbox = probe, activePointer = pointer,
                    hostReady = hostReady, foregroundReady = foreground,
                )
                val principal = access.technicalPrincipalOrNull()
                if (principal == null || !hostReady() ||
                    !access.grantAfterTrustedForegroundReadAction(setOf(
                        FaroV5LocalReadDomain.BOOKMARK_METADATA,
                        FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
                    ))
                ) null else {
                    binding = FaroV5BookmarkReadHostBinding(
                        pinned = openingPointer, activePointer = pointer,
                        workspace = workspace, auxiliary = graph.auxiliary,
                        principalId = principal, expectedHostEpoch = epoch,
                        hostEpochOrNull = { if (alive.get()) epoch else null },
                        hostReady = hostReady, readAuthority = access,
                    )
                    if (!hostReady()) null else {
                        bookmarkAuthority = access
                        bookmarkBinding = binding
                        mounted = true
                        binding
                    }
                }
            } catch (_: Exception) { null }
            finally {
                if (!mounted) {
                    binding?.close()
                    access?.close()
                    probe?.close()
                }
            }
        }

        /** Fresh explicit folder/lifecycle action. Preview receives read grants only. */
        internal fun openBookmarkFolderActionsAfterTrustedForegroundRequest(
            application: Application, foreground: () -> Boolean,
        ): FaroV5BookmarkFolderActionBinding? {
            revokeBookmarkFolderActionsOnPause()
            if (application.packageName != "ar.com.faro.alpha.v2coredev" || !alive.get() || !foreground() ||
                pointer.read() != openingPointer) return null
            val workspace = graph.workspace ?: return null
            val epoch = UUID.randomUUID().toString()
            val ready = { alive.get() && foreground() && pointer.read() == openingPointer }
            val probe = FaroV5AndroidSandboxPrincipal(application.packageName, openingPointer, pointer,
                epoch, { if (alive.get()) epoch else null }, AndroidPlatformSandboxFactsReader(application))
            val reads = FaroV5LocalInstallReadAuthority(probe, pointer, ready, foreground)
            val mutations = FaroV5LocalInstallBookmarkMutationAuthority(probe, pointer, ready, foreground)
            val principal = reads.technicalPrincipalOrNull()
            if (principal == null || mutations.technicalPrincipalOrNull() != principal ||
                !reads.grantAfterTrustedForegroundReadAction(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA,
                    FaroV5LocalReadDomain.WORKSPACE_MESSAGE))) {
                reads.close(); mutations.close(); probe.close(); return null
            }
            val binding = FaroV5BookmarkFolderActionBinding(openingPointer, pointer, workspace,
                graph.auxiliary, principal, epoch, { if (alive.get()) epoch else null }, ready, reads, mutations)
            synchronized(this) {
                if (!ready()) { binding.close(); probe.close(); return null }
                bookmarkFolders = binding; bookmarkFoldersProbe = probe
            }
            return binding
        }

        @Synchronized internal fun revokeBookmarkFolderActionsOnPause() {
            bookmarkFolders?.close(); bookmarkFolders = null
            bookmarkFoldersProbe?.close(); bookmarkFoldersProbe = null
        }

        /** Explicit per-message action only. Constructing this adapter grants nothing. */
        internal fun bookmarkChatActions(application: Application, foreground: () -> Boolean): ar.com.faro.v5.surface.FaroV5BookmarkChatActions =
            object : ar.com.faro.v5.surface.FaroV5BookmarkChatActions {
                override suspend fun prepare(conversationId: ConversationId, messageId: ConversationMessageId,
                    action: ar.com.faro.v5.surface.FaroV5BookmarkChatAction): ar.com.faro.v5.surface.FaroV5BookmarkChatReview? {
                    revokeBookmarkReviewOnPause()
                    if (application.packageName != "ar.com.faro.alpha.v2coredev" || !alive.get() || !foreground() ||
                        pointer.read() != openingPointer) return null
                    val workspace = graph.workspace ?: return null
                    val epoch = UUID.randomUUID().toString()
                    val ready = { alive.get() && foreground() && pointer.read() == openingPointer }
                    val probe = FaroV5AndroidSandboxPrincipal(application.packageName, openingPointer, pointer,
                        epoch, { if (alive.get()) epoch else null }, AndroidPlatformSandboxFactsReader(application))
                    val reads = FaroV5LocalInstallReadAuthority(probe, pointer, ready, foreground)
                    val mutations = FaroV5LocalInstallBookmarkMutationAuthority(probe, pointer, ready, foreground)
                    val principal = reads.technicalPrincipalOrNull()
                    if (principal == null || mutations.technicalPrincipalOrNull() != principal ||
                        !reads.grantAfterTrustedForegroundReadAction(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA,
                            FaroV5LocalReadDomain.WORKSPACE_MESSAGE))) {
                        reads.close(); mutations.close(); probe.close(); return null
                    }
                    val binding = FaroV5BookmarkReviewPreparationBinding(openingPointer, pointer, workspace,
                        graph.auxiliary, principal, epoch, { if (alive.get()) epoch else null }, ready, reads, mutations)
                    synchronized(this@Session) {
                        if (!ready()) { binding.close(); probe.close(); return null }
                        bookmarkReview = binding; bookmarkReviewProbe = probe
                    }
                    return binding.prepare(conversationId, messageId, action)
                }
                override fun isCurrent(review: ar.com.faro.v5.surface.FaroV5BookmarkChatReview) =
                    bookmarkReview?.isCurrent(review) == true
                override suspend fun confirm(review: ar.com.faro.v5.surface.FaroV5BookmarkChatReview) =
                    bookmarkReview?.confirm(review) ?: "Guardados no disponible"
                override fun cancel(review: ar.com.faro.v5.surface.FaroV5BookmarkChatReview) { bookmarkReview?.cancel(review) }
            }

        @Synchronized
        internal fun revokeBookmarkReviewOnPause() {
            bookmarkReview?.close(); bookmarkReview = null
            bookmarkReviewProbe?.close(); bookmarkReviewProbe = null
        }

        @Synchronized
        internal fun isBookmarkReadCurrent(
            binding: FaroV5BookmarkReadHostBinding,
            page: FaroV5BookmarkHostPage,
            foreground: () -> Boolean,
        ): Boolean = alive.get() && bookmarkBinding === binding &&
            runCatching { foreground() && pointer.read() == openingPointer }.getOrDefault(false) &&
            binding.isPageCurrent(page)

        @Synchronized
        internal fun revokeBookmarkReadingOnPause() {
            revokeBookmarkFolderActionsOnPause()
            bookmarkBinding?.close()
            bookmarkBinding = null
            bookmarkAuthority?.close()
            bookmarkAuthority = null
        }

        /** Strictly DEV-only; called solely by the Pendientes native button. */
        @Synchronized
        internal fun openPendingAfterTrustedForegroundRequest(
            application: Application,
            foreground: () -> Boolean,
        ): FaroV5N031ThreeOwnerDueHostBinding? {
            if (!alive.get() || pendingBinding != null ||
                application.packageName != "ar.com.faro.alpha.v2coredev" ||
                !runCatching { foreground() }.getOrDefault(false)
            ) return null
            val pinned = runCatching { pointer.read() }.getOrNull() ?: return null
            if (pinned.generationId.value != graph.generationId) return null
            val epoch = UUID.randomUUID().toString()
            val hostReady: () -> Boolean = {
                alive.get() && runCatching {
                    foreground() && pointer.read() == pinned
                }.getOrDefault(false)
            }
            var probe: FaroV5AndroidSandboxPrincipal? = null
            var access: FaroV5LocalInstallReadAuthority? = null
            var binding: FaroV5N031ThreeOwnerDueHostBinding? = null
            var mounted = false
            return try {
                probe = FaroV5AndroidSandboxPrincipal(
                    expectedApplicationId = application.packageName,
                    pinned = pinned,
                    activePointer = pointer,
                    expectedHostEpoch = epoch,
                    hostEpochOrNull = { if (alive.get()) epoch else null },
                    facts = AndroidPlatformSandboxFactsReader(application),
                )
                access = FaroV5LocalInstallReadAuthority(
                    sandbox = probe,
                    activePointer = pointer,
                    hostReady = hostReady,
                    foregroundReady = foreground,
                )
                val principal = access.technicalPrincipalOrNull()
                if (principal == null || !hostReady() ||
                    !access.grantAfterTrustedForegroundReadAction(
                        setOf(
                            FaroV5LocalReadDomain.MATTER_DUE,
                            FaroV5LocalReadDomain.REMINDER_OCCURRENCE,
                            FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM,
                        )
                    )
                ) null else {
                    // Existing DEV Reality7 fails CLOSED inside the REALITY8 reader.
                    // Never migrate ACTIVE data to render a screen.
                    binding = FaroV5N031ThreeOwnerDueHostBinding(
                        generationId = graph.generationId,
                        pinned = pinned,
                        activePointer = pointer,
                        reality = graph.reality,
                        principalId = principal,
                        expectedHostEpoch = epoch,
                        hostEpochOrNull = { if (alive.get()) epoch else null },
                        hostReady = hostReady,
                        readAuthority = access,
                    )
                    if (!hostReady()) null else {
                        pendingAuthority = access
                        pendingBinding = binding
                        mounted = true
                        binding
                    }
                }
            } catch (_: Exception) {
                null
            } finally {
                if (!mounted) {
                    binding?.close()
                    access?.close()
                    probe?.close()
                }
            }
        }

        /** Final UI publish must revalidate three grants after async SQL. */
        @Synchronized
        internal fun isPendingReadCurrent(
            binding: FaroV5N031ThreeOwnerDueHostBinding,
            foreground: () -> Boolean,
        ): Boolean {
            if (!alive.get() || pendingBinding !== binding ||
                !runCatching { foreground() }.getOrDefault(false)
            ) return false
            val access = pendingAuthority ?: return false
            val principal = access.technicalPrincipalOrNull() ?: return false
            val pinned = runCatching { pointer.read() }.getOrNull() ?: return false
            return listOf(
                FaroV5LocalReadDomain.MATTER_DUE,
                FaroV5LocalReadDomain.REMINDER_OCCURRENCE,
                FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM,
            ).all { access.epochOrNull(principal, it, pinned) != null }
        }

        @Synchronized
        internal fun revokePendingReadingOnPause() {
            pendingBinding?.close()
            pendingBinding = null
            pendingAuthority?.close()
            pendingAuthority = null
        }

        /** Backgrounding revokes all four DEV Search owners, not only the UI.
         * A future onResume cannot restore these grants: an explicit native
         * user action must open a new verified host session.
         */
        @Synchronized
        internal fun revokeSearchReadingOnPause() {
            searchAccesses.forEach { it.close() }
        }

        /** A paused or changed host cannot reuse a previous user request. */
        @Synchronized
        internal fun revokeActivityReadingOnPause() {
            historyBinding?.close()
            historyBinding = null
            historyAuthority?.close()
            historyAuthority = null
        }

        @Synchronized
        override fun close() {
            if (alive.compareAndSet(true, false)) {
                revokeActivityReadingOnPause()
                revokePendingReadingOnPause()
                revokeBookmarkReviewOnPause()
                revokeBookmarkReadingOnPause()
                searchAccesses.forEach { it.close() }
                pointer.removeChangeListener(pointerObserver)
                graph.close()
            }
        }
    }
}
