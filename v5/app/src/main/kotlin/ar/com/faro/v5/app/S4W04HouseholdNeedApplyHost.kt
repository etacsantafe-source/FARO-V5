package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
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
    private val bindings = HouseholdNeedReviewAction.entries.map(::NeedBinding)

    fun snapshot(): HouseholdNeedsSnapshot? = gate.withMaterialWrite {
        if (foregroundCurrent()) persistence.read().takeIf { foregroundCurrent() } else null
    }

    sealed interface Submission {
        data class Saved(val review: ConversationPlanReviewSnapshot) : Submission
        data class Rejected(val message: String) : Submission
    }

    fun submit(chat: ConversationId, request: HouseholdNeedReviewRequest, localRequestId: String): Submission =
        gate.withMaterialWrite {
            val unavailable = Submission.Rejected("El Review de necesidades no está disponible.")
            if (!selected(chat) || localRequestId.isBlank() || localRequestId.length > 256) {
                return@withMaterialWrite unavailable
            }
            val key = CanonicalHashBuilder.domain("w04.household.review")
                .string("principal", principal).string("chat", chat.value).string("request", localRequestId).sha256Hex()
            val reviewId = "w04:household:$key"
            val before = workspace.snapshot()
            val timeline = runBlocking { workspace.readTimeline(chat) } ?: return@withMaterialWrite unavailable
            if (timeline.revision != before.revision || timeline.authorityToken.isBlank()) return@withMaterialWrite unavailable
            val draft = when (val result = HouseholdNeedReview.propose(generation, persistence.read(), request, key)) {
                is HouseholdNeedReviewResult.Ready -> result.review
                is HouseholdNeedReviewResult.Rejected -> return@withMaterialWrite Submission.Rejected(result.message)
            }
            val contract = householdContract(draft.action) ?: return@withMaterialWrite unavailable
            val operation = PreparedOperation(contract, draft.payload, DependencyStampSet.empty(), IdempotencyKey(reviewId))
            val plan = PreparedPlan.of(ApplyPlanNode(PlanNodeId("household-need"), operation, emptySet()))
            val existing = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
            if (!selected(chat) || workspace.snapshot().revision != before.revision) return@withMaterialWrite unavailable
            if (existing != null) {
                return@withMaterialWrite if (existing.planFingerprint == plan.planFingerprint &&
                    existing.state == ConversationPlanReviewState.PENDING) Submission.Saved(existing) else unavailable
            }
            val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint, draft.summary,
                PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review:$key"),
            ))
            if (saved is WorkspacePlanReviewWriteResult.Saved) Submission.Saved(saved.review) else unavailable
        }

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        synchronized(grantLock) {
            gate.withMaterialWrite {
                val denied = ReviewedConversationApplyResult.Denied("household_confirmation_unavailable")
                if (!selected(context.conversationId)) return@withMaterialWrite denied
                val approved = reviewPort.load(context) ?: return@withMaterialWrite denied
                if (approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied
                val envelope = approved.envelope()
                val node = validatedNode(context, envelope) ?: return@withMaterialWrite denied
                val draft = HouseholdNeedReview.decode(node.preparedOperation.payload) ?: return@withMaterialWrite denied
                if (!policy(principal, draft.action.operationId, draft.targetId)) return@withMaterialWrite denied

                val grant = HouseholdApplyGrant(context, draft.action.operationId, draft.targetId)
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
                        selected(context.conversationId) && policy(principal, grant.operation, grant.target)
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

    private fun validatedNode(context: ConversationPlanReviewContext, envelope: ByteArray): ApplyPlanNode? {
        if (!context.reviewId.startsWith("w04:household:")) return null
        val plan = runCatching { PreparedPlanEnvelopeCodec.decode(envelope) }.getOrNull() ?: return null
        if (plan.planFingerprint != context.planFingerprint) return null
        return plan.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf { node ->
            val intent = HouseholdNeedReview.decode(node.preparedOperation.payload)
            intent != null && node.dependsOn.isEmpty() && intent.generation == generation &&
                node.preparedOperation.contract == householdContract(intent.action)
        }
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

    private inner class NeedBinding(private val action: HouseholdNeedReviewAction) : OperationBindingPort, OperationRevalidationPort {
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
    }
}
