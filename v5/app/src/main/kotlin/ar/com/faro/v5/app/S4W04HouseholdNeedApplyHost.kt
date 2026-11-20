package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.evidence.CommercialEvidenceOperations
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.owner.household.spi.HouseholdNeedConflict
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/** Needs-only host for the isolated Household10 DEV root. */
internal class S4W04HouseholdNeedApplyHost(
    root: Path,
    staged: StagedStorageGeneration,
    private val pinned: ActiveGenerationPointer,
    private val gate: MaterialWriteGate,
    private val catalog: OperationContractLookupPort,
    private val principal: String,
    private val hostEpoch: String,
    private val currentEpoch: () -> String?,
    private val ownerReady: () -> Boolean,
    private val trustedForeground: () -> Boolean,
    private val policy: (String, OperationId, HouseholdNeedId) -> Boolean,
    wrapReviewPort: (TrustedConversationApplyReviewPort) -> TrustedConversationApplyReviewPort = { it },
) : AutoCloseable {
    init {
        require(principal.isNotBlank() && hostEpoch.isNotBlank())
    }

    private val pointer = FileActiveGenerationPointer(root)
    private val generation = HouseholdReviewGeneration(pinned.generationId.value, pinned.sequence)
    private val grantLock = Any()
    private val principalTag = CanonicalHashBuilder.domain("w04.household.principal")
        .string("principal", principal).sha256Hex().take(32)
    private val scopedPrefix = "w04:household:$principalTag:"
    @Volatile private var closed = false
    private val session = FaroV5GenerationStoreGraph.openActiveHousehold10DevForApplyHost(
        root, staged, pinned, gate, CanonicalPreparedPlanEnvelopeVerifier, ::ownerCurrent,
    )
    private val workspace = session.workspace
    val reviewDecisions: ConversationReviewPort get() = workspace
    val chatSelection: ConversationWorkspaceSelectionPort get() = workspace
    private val reality = session.reality
    private val persistence = reality.householdNeedsPersistenceForTrustedDev()
    private val reviewPort = wrapReviewPort(workspace)
    private val bindings: List<HouseholdBinding> =
        HouseholdNeedReviewAction.entries.map(::NeedBinding) + UndoBinding()

    fun snapshot(): HouseholdNeedsSnapshot? = gate.withMaterialWrite {
        if (foregroundCurrent()) persistence.read().takeIf { foregroundCurrent() } else null
    }

    /**
     * C0B: explicitly chosen product while the current Household owner read
     * operation is independently admitted. This returns only a candidate.
     */
    fun selectCommercialProduct(
        needId: HouseholdNeedId?,
        productKey: CommercialProductKey?,
        presentedProducts: List<CommercialProductKey>,
        explicitlyChosen: Boolean,
    ): CommercialNeedProductDecision = synchronized(grantLock) {
        gate.withMaterialWrite {
            S4W04CommercialNeedProductSelection.select(
                snapshot = if (foregroundCurrent()) persistence.read() else null,
                needId = needId,
                productKey = productKey,
                presentedProducts = presentedProducts,
                readFence = commercialReadFence(),
                explicitlyChosen = explicitlyChosen,
                isOwnerAdmitted = { needId != null && householdReadAdmitted(needId) },
            )
        }
    }

    /**
     * C0B: read-only Commercial Evidence read through the existing leased
     * generation graph. Both policy callbacks must approve independently.
     */
    fun commercialPriceHistoryForSelection(
        candidate: CommercialNeedProductCandidate,
        limit: Int,
        commercialAdmission: (String, OperationId, CommercialProductKey) -> Boolean,
    ): CommercialPriceReadDecision = synchronized(grantLock) {
        gate.withMaterialWrite {
            S4W04CommercialPriceReadHost(
                session = session,
                catalog = catalog,
                currentSnapshot = { if (foregroundCurrent()) persistence.read() else null },
                currentFence = ::commercialReadFence,
                householdAdmission = ::householdReadAdmitted,
                commercialAdmission = { productKey ->
                    runCatching {
                        foregroundCurrent() && commercialAdmission(
                            principal, CommercialEvidenceOperations.PRICE_HISTORY_READ, productKey,
                        )
                    }.getOrDefault(false)
                },
            ).read(candidate, limit)
        }
    }

    private fun commercialReadFence(): CommercialNeedReadFence? =
        if (foregroundCurrent()) {
            CommercialNeedReadFence(
                pinned.generationId.value, pinned.sequence, hostEpoch, principal,
            )
        } else null

    private fun householdReadAdmitted(needId: HouseholdNeedId): Boolean {
        if (!foregroundCurrent()) return false
        val operation = OperationId("shopping.snapshot.read")
        val contract = catalog.current(operation) ?: return false
        if (contract.operationId != operation || contract.capabilityId != CapabilityId("N-033") ||
            contract.canonicalExecutor != ExecutorId("household") ||
            contract.executionProfile != ExecutionProfile.READ_ONLY ||
            contract.exposure != ContractExposure.BINDABLE
        ) return false
        return runCatching { policy(principal, operation, needId) }.getOrDefault(false)
    }

    sealed interface Submission {
        data class Saved(val review: ConversationPlanReviewSnapshot) : Submission
        data class Rejected(val message: String) : Submission
    }

    fun submit(chat: ConversationId, request: HouseholdNeedReviewRequest, localRequestId: String): Submission =
        gate.withMaterialWrite {
            if (!selected(chat)) return@withMaterialWrite unavailable()
            val key = requestKey(chat, localRequestId) ?: return@withMaterialWrite unavailable()
            val draft = when (val result = HouseholdNeedReview.propose(generation, persistence.read(), request, key)) {
                is HouseholdNeedReviewResult.Ready -> result.review
                is HouseholdNeedReviewResult.Rejected -> return@withMaterialWrite Submission.Rejected(result.message)
            }
            val contract = householdContract(draft.action) ?: return@withMaterialWrite unavailable()
            saveReview(chat, key, contract, draft.payload, draft.summary)
        }

    fun submitUndo(
        source: ConversationPlanReviewContext,
        localRequestId: String,
    ): Submission = gate.withMaterialWrite {
        val chat = source.conversationId
        if (!selected(chat) || !source.reviewId.startsWith(scopedPrefix)) return@withMaterialWrite unavailable()
        val key = requestKey(chat, localRequestId) ?: return@withMaterialWrite unavailable()
        val committed = committedOperation(source) ?: return@withMaterialWrite unavailable()
        val original = HouseholdNeedReview.decode(committed.node.preparedOperation.payload)
            ?: return@withMaterialWrite unavailable()
        if (!policy(principal, HouseholdNeedUndo.OPERATION, original.targetId)) return@withMaterialWrite unavailable()
        val undo = HouseholdNeedUndo.propose(generation, persistence.read(), source.reviewId, committed.receipt, original)
            ?: return@withMaterialWrite Submission.Rejected("La necesidad cambió desde la operación original.")
        val contract = undoContract() ?: return@withMaterialWrite unavailable()
        saveReview(chat, key, contract, undo.payload, undo.summary)
    }

    data class HistoryEntry(
        val review: ConversationPlanReviewContext,
        val summary: String,
        val receipt: ExecutionReceipt,
        val undoneReceipt: ExecutionReceipt?,
        val canUndo: Boolean,
    )

    fun history(chat: ConversationId): List<HistoryEntry> = gate.withMaterialWrite {
        if (!selected(chat)) return@withMaterialWrite emptyList()
        val current = persistence.read()
        val entries = runBlocking { workspace.reviews(chat) }.mapNotNull { review ->
            val committed = committedOperation(review.context()) ?: return@mapNotNull null
            val draft = HouseholdNeedReview.decode(committed.node.preparedOperation.payload)
            val undo = HouseholdNeedUndo.decode(committed.node.preparedOperation.payload)
            val target = (draft?.targetId ?: undo?.targetId) ?: return@mapNotNull null
            val operation = committed.node.preparedOperation.contract.operationId
            if (!policy(principal, operation, target)) return@mapNotNull null
            val canUndo = draft != null && review.reviewId.startsWith(scopedPrefix) &&
                policy(principal, HouseholdNeedUndo.OPERATION, target) &&
                HouseholdNeedUndo.propose(generation, current, review.reviewId, committed.receipt, draft) != null
            val entry = HistoryEntry(review.context(), draft?.summary ?: checkNotNull(undo).summary,
                committed.receipt, undo?.sourceReceipt, canUndo)
            val revision = (draft?.expectedRevision ?: checkNotNull(undo).expectedRevision) + 1
            revision to entry
        }
        if (selected(chat)) entries.sortedBy { it.first }.map { it.second } else emptyList()
    }

    private fun requestKey(chat: ConversationId, localRequestId: String): String? {
        if (localRequestId.isBlank() || localRequestId.length > 256) return null
        return CanonicalHashBuilder.domain("w04.household.review").string("principal", principal)
            .string("chat", chat.value).string("request", localRequestId).sha256Hex()
    }

    private fun saveReview(
        chat: ConversationId,
        key: String,
        contract: OperationContractDescriptor,
        payload: CanonicalPayload,
        summary: String,
    ): Submission {
        val reviewId = scopedPrefix + key
        val before = workspace.snapshot()
        val timeline = runBlocking { workspace.readTimeline(chat) } ?: return unavailable()
        if (timeline.revision != before.revision || timeline.authorityToken.isBlank()) return unavailable()
        val operation = PreparedOperation(contract, payload, DependencyStampSet.empty(), IdempotencyKey(reviewId))
        val plan = PreparedPlan.of(ApplyPlanNode(PlanNodeId("household-need"), operation, emptySet()))
        val existing = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
        if (!selected(chat) || workspace.snapshot().revision != before.revision) return unavailable()
        if (existing != null) {
            return if (existing.planFingerprint == plan.planFingerprint &&
                existing.state == ConversationPlanReviewState.PENDING) Submission.Saved(existing) else unavailable()
        }
        val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint, summary,
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review:$key"),
        ))
        return if (saved is WorkspacePlanReviewWriteResult.Saved) Submission.Saved(saved.review) else unavailable()
    }

    private fun unavailable() = Submission.Rejected("El Review de necesidades no está disponible.")

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        synchronized(grantLock) {
            gate.withMaterialWrite {
                val denied = ReviewedConversationApplyResult.Denied("household_confirmation_unavailable")
                if (!selected(context.conversationId)) return@withMaterialWrite denied
                val approved = reviewPort.load(context) ?: return@withMaterialWrite denied
                if (approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied
                val envelope = approved.envelope()
                val node = validatedNode(context, envelope) ?: return@withMaterialWrite denied
                val undo = HouseholdNeedUndo.decode(node.preparedOperation.payload)
                val target = undo?.targetId ?: HouseholdNeedReview.decode(node.preparedOperation.payload)?.targetId
                    ?: return@withMaterialWrite denied
                val operation = node.preparedOperation.contract.operationId
                if (!policy(principal, operation, target)) return@withMaterialWrite denied
                if (undo != null && !verifiedUndoSource(context.conversationId, undo)) return@withMaterialWrite denied

                val grant = HouseholdApplyGrant(context, operation, target)
                val authorize = ApplyTransactionAuthorizationPort { plan, identity, actual, tx ->
                    !grant.consumed && grant.identity == identity && tx.transactionId == identity.transactionId &&
                        grant.context.planFingerprint == plan.planFingerprint && actual == node &&
                        foregroundCurrent() && policy(principal, grant.operation, grant.target)
                }
                val finalGate = ApplyTransactionFinalCommitGate { plan, identity, receipt, nodes, tx ->
                    val allowed = nodes.singleOrNull() == node && receipt.transactionId == identity.transactionId &&
                        receipt.planFingerprint == grant.context.planFingerprint && authorize.isAuthorized(plan, identity, node, tx)
                    if (allowed) grant.consumed = true
                    allowed
                }
                coordinator(authorize, finalGate) { applying, identity ->
                    val live = reviewPort.load(applying.context())
                    val admitted = live?.review == applying && live.envelope().contentEquals(envelope) &&
                        applying.state == ConversationPlanReviewState.APPLYING &&
                        applying.transactionId == identity.transactionId && applying.conversationId == context.conversationId &&
                        applying.reviewId == context.reviewId && applying.planFingerprint == context.planFingerprint &&
                        selected(context.conversationId) && policy(principal, grant.operation, grant.target) &&
                        (undo == null || verifiedUndoSource(context.conversationId, undo))
                    if (admitted) grant.identity = identity
                    admitted
                }.apply(context)
            }
        }

    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        gate.withMaterialWrite {
            val denied = ReviewedConversationApplyResult.Denied("household_recovery_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val saved = reviewPort.load(context) ?: return@withMaterialWrite denied
            if (validatedNode(context, saved.envelope()) == null) return@withMaterialWrite denied
            coordinator().reconcileCommittedOnly(context)
        }

    private fun ownerCurrent(): Boolean =
        !closed && ownerReady() && currentEpoch() == hostEpoch && pointer.read() == pinned

    private fun foregroundCurrent(): Boolean = ownerCurrent() && trustedForeground()

    private fun selected(chat: ConversationId): Boolean =
        foregroundCurrent() && workspace.snapshot().workspace.activeConversationId == chat

    private fun householdContract(action: HouseholdNeedReviewAction): OperationContractDescriptor? =
        catalog.current(action.operationId)?.takeIf {
            it.operationId == action.operationId && it.capabilityId == CapabilityId("N-033") &&
                it.canonicalExecutor == ExecutorId("household") && it.executionProfile == ExecutionProfile.REALITY_MUTATION &&
                it.exposure == ContractExposure.BINDABLE
        }

    private fun undoContract(): OperationContractDescriptor? =
        catalog.current(HouseholdNeedUndo.OPERATION)?.takeIf {
            it.operationId == HouseholdNeedUndo.OPERATION && it.capabilityId == CapabilityId("N-016") &&
                it.canonicalExecutor == ExecutorId("undo-coordinator") &&
                it.executionProfile == ExecutionProfile.REALITY_MUTATION && it.exposure == ContractExposure.BINDABLE
        }

    private fun validatedNode(context: ConversationPlanReviewContext, envelope: ByteArray): ApplyPlanNode? =
        householdNode(context, envelope, currentGenerationRequired = true)

    private fun householdNode(
        context: ConversationPlanReviewContext,
        envelope: ByteArray,
        currentGenerationRequired: Boolean,
    ): ApplyPlanNode? {
        val own = context.reviewId.startsWith(scopedPrefix)
        if (!own && !LEGACY_REVIEW.matches(context.reviewId)) return null
        val plan = runCatching { PreparedPlanEnvelopeCodec.decode(envelope) }.getOrNull() ?: return null
        if (plan.planFingerprint != context.planFingerprint) return null
        val node = plan.applyPlan.nodesInTopologicalOrder().singleOrNull() ?: return null
        if (node.dependsOn.isNotEmpty() || node.preparedOperation.dependencyStamps != DependencyStampSet.empty()) return null
        val prepared = node.preparedOperation
        val original = HouseholdNeedReview.decode(prepared.payload)
        if (original != null) {
            return node.takeIf {
                prepared.contract == householdContract(original.action) &&
                    (!currentGenerationRequired || original.generation == generation)
            }
        }
        val undo = HouseholdNeedUndo.decode(prepared.payload) ?: return null
        return node.takeIf {
            own && prepared.contract == undoContract() && (!currentGenerationRequired || undo.generation == generation)
        }
    }

    private data class CommittedOperation(val node: ApplyPlanNode, val receipt: ExecutionReceipt)

    private fun committedOperation(context: ConversationPlanReviewContext): CommittedOperation? {
        val saved = workspace.loadCommitted(context) ?: return null
        val node = householdNode(context, saved.envelope(), currentGenerationRequired = false) ?: return null
        val receipt = saved.review.receipt ?: return null
        if (receipt.planFingerprint != context.planFingerprint || !observesReceipt(receipt)) return null
        return CommittedOperation(node, receipt)
    }

    private fun verifiedUndoSource(chat: ConversationId, undo: HouseholdNeedUndo): Boolean {
        if (!undo.sourceReviewId.startsWith(scopedPrefix)) return false
        val review = runBlocking { workspace.reviews(chat) }.singleOrNull { it.reviewId == undo.sourceReviewId }
            ?: return false
        val committed = committedOperation(review.context()) ?: return false
        return committed.receipt == undo.sourceReceipt &&
            committed.node.preparedOperation.payload == undo.original.payload
    }

    private fun observesReceipt(receipt: ExecutionReceipt): Boolean {
        val attempt = reality.applyAttemptLookup.find(receipt.transactionId) ?: return false
        return attempt.state == ApplyAttemptJournalState.COMMITTED &&
            attempt.planFingerprint == receipt.planFingerprint && reality.executionReceiptLookup.find(receipt.transactionId) == receipt
    }

    private fun coordinator(
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ) = ReviewedConversationApplyCoordinator(
        workspace = reviewPort, decode = PreparedPlanEnvelopeCodec::decode,
        retry = ApplyRetryReceiptGate(reality.applyAttemptIdentities, reality.executionReceiptLookup),
        preflight = ApplyPreflightGate(catalog, reality, bindings),
        bindings = ApplyBindingResolutionGate(bindings),
        executor = ApplyTransactionExecutor(reality, reality, authorization, finalGate),
        attempts = reality.applyAttemptLookup, receipts = reality.executionReceiptLookup, afterBeginApply = afterBegin,
    )

    private interface HouseholdBinding : OperationBindingPort, OperationRevalidationPort

    private inner class NeedBinding(private val action: HouseholdNeedReviewAction) : HouseholdBinding {
        override val operationId = action.operationId
        override val descriptor = OperationBindingDescriptor(operationId, ExecutorId("household"), ExecutionProfile.REALITY_MUTATION)

        override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
            if (revalidated(preparedOperation) != null) OperationRevalidationResult.Valid
            else OperationRevalidationResult.Rejected(OperationRevalidationFailure.PRECONDITION_FAILED, STALE)

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult {
            val draft = revalidated(preparedOperation) ?: return OperationBindingExecutionResult.Rejected(STALE)
            return try {
                persistence.mutate(draft.expectedRevision, draft.toPersistenceChange(), transactionScope)
                OperationBindingExecutionResult.Completed
            } catch (_: HouseholdNeedConflict) {
                OperationBindingExecutionResult.Rejected(STALE)
            }
        }

        private fun revalidated(prepared: PreparedOperation): HouseholdNeedReview? {
            val intent = HouseholdNeedReview.decode(prepared.payload) ?: return null
            if (intent.action != action || prepared.contract != householdContract(action) ||
                prepared.dependencyStamps != DependencyStampSet.empty()) return null
            return intent.takeIf { it.matchesCurrentSnapshot(generation, persistence.read(), prepared.payload.fingerprint) }
        }
    }

    private inner class UndoBinding : HouseholdBinding {
        override val operationId = HouseholdNeedUndo.OPERATION
        override val descriptor = OperationBindingDescriptor(operationId, ExecutorId("undo-coordinator"), ExecutionProfile.REALITY_MUTATION)

        override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
            if (revalidated(preparedOperation) != null) OperationRevalidationResult.Valid
            else OperationRevalidationResult.Rejected(OperationRevalidationFailure.PRECONDITION_FAILED, STALE)

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult {
            val undo = revalidated(preparedOperation) ?: return OperationBindingExecutionResult.Rejected(STALE)
            return try {
                persistence.mutate(undo.expectedRevision, undo.toPersistenceChange(), transactionScope)
                OperationBindingExecutionResult.Completed
            } catch (_: HouseholdNeedConflict) {
                OperationBindingExecutionResult.Rejected(STALE)
            }
        }

        private fun revalidated(prepared: PreparedOperation): HouseholdNeedUndo? {
            if (prepared.contract != undoContract() || prepared.dependencyStamps != DependencyStampSet.empty()) return null
            val undo = HouseholdNeedUndo.decode(prepared.payload) ?: return null
            return undo.takeIf { observesReceipt(it.sourceReceipt) && it.matchesCurrentSnapshot(generation, persistence.read()) }
        }
    }

    private class HouseholdApplyGrant(
        val context: ConversationPlanReviewContext,
        val operation: OperationId,
        val target: HouseholdNeedId,
    ) {
        var identity: ApplyAttemptIdentity? = null
        var consumed = false
    }

    override fun close() = synchronized(grantLock) {
        closed = true
        session.close()
    }

    private companion object {
        const val STALE = "household_review_stale_or_invalid"
        val LEGACY_REVIEW = Regex("w04:household:[0-9a-f]{64}")
    }
}
