package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/** A2C1C: isolated SQL15 DEV-only consume Review -> explicit Confirm -> AtomicApply. */
internal class S4W05StockConsumptionDevApplyHost(
    root: Path,
    private val pinned: ActiveGenerationPointer,
    private val gate: MaterialWriteGate,
    private val catalog: OperationContractLookupPort,
    private val principal: String,
    private val hostEpoch: Long,
    private val currentEpoch: () -> Long?,
    private val ownerReady: () -> Boolean,
    private val foreground: () -> Boolean,
    private val policy: (String, OperationId, String) -> Boolean,
    private val confirmationClock: () -> Long,
    wrapReview: (TrustedConversationApplyReviewPort) -> TrustedConversationApplyReviewPort = { it },
) : AutoCloseable {
    init {
        require(principal.isNotBlank() && hostEpoch > 0)
    }

    private val operation = HouseholdStockConsumptionReview.OPERATION
    private val pointer = FileActiveGenerationPointer(root)
    private val generation = HouseholdReviewGeneration(pinned.generationId.value, pinned.sequence)
    private val grantLock = Any()
    private val prefix = "w05:consume:" + CanonicalHashBuilder.domain("w05.stock.consume.principal")
        .string("principal", principal).sha256Hex().take(32) + ":"
    @Volatile private var closed = false
    private val session = FaroV5GenerationStoreGraph.openActiveHousehold15DevForConsumptionHost(
        root, pinned, gate, CanonicalPreparedPlanEnvelopeVerifier, ::ownerCurrent,
    )
    private val workspace = session.workspace
    private val rows = session.consumptions(principal, generation)
    private val reviews = wrapReview(workspace)

    private fun context(): HouseholdStockReceiveDevContext? = if (foregroundCurrent()) {
        HouseholdStockReceiveDevContext(principal, hostEpoch, generation, isolatedDev = true, foreground = true)
    } else null

    private val admission = HouseholdStockConsumptionDevAdmission(
        catalog, ::context,
        HouseholdStockConsumptionPositionPort { rows.position(it) },
        HouseholdStockConsumptionDevConsentPort { request ->
            policy(principal, operation, request.stockItem)
        },
    )

    val reviewDecisions: ConversationReviewPort get() = workspace
    val chatSelection: ConversationWorkspaceSelectionPort get() = workspace

    /** Inert trusted SQL15 position. This never grants Apply. */
    fun position(stockItemRef: String, quantity: HouseholdNeedQuantity, sourceRef: String):
        HouseholdStockConsumptionPosition? = gate.withMaterialWrite {
        if (!foregroundCurrent() || !policy(principal, operation, stockItemRef)) return@withMaterialWrite null
        val live = context() ?: return@withMaterialWrite null
        val probe = runCatching {
            HouseholdStockConsumptionDevRequest(live, stockItemRef, quantity, sourceRef,
                "w05-consume-position", 0L)
        }.getOrNull() ?: return@withMaterialWrite null
        rows.position(probe).takeIf { foregroundCurrent() }
    }

    /** Only creates a canonical Workspace review; not a stock mutation. */
    fun submit(
        chat: ConversationId,
        localRequestId: String,
        stockItemRef: String,
        quantity: HouseholdNeedQuantity,
        sourceRef: String,
    ): ConversationPlanReviewSnapshot? = gate.withMaterialWrite {
        if (!selected(chat) || !policy(principal, operation, stockItemRef) ||
            localRequestId.isBlank() || localRequestId.length > 256) return@withMaterialWrite null
        val descriptor = contract() ?: return@withMaterialWrite null
        val key = CanonicalHashBuilder.domain("w05.stock.consume.review")
            .string("principal", principal).string("chat", chat.value)
            .string("request", localRequestId).sha256Hex()
        val reviewId = prefix + key
        val intentId = prefix + CanonicalHashBuilder.domain("w05.stock.consume.intent")
            .string("principal", principal).string("request", localRequestId).sha256Hex()
        val before = workspace.snapshot()
        val timeline = runBlocking { workspace.readTimeline(chat) } ?: return@withMaterialWrite null
        if (timeline.revision != before.revision || timeline.authorityToken.isBlank()) return@withMaterialWrite null
        val previous = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
        if (!selected(chat) || workspace.snapshot().revision != before.revision) return@withMaterialWrite null
        if (previous != null && previous.state != ConversationPlanReviewState.PENDING) {
            val prior = previous.context()
            val bytes = if (previous.state == ConversationPlanReviewState.COMMITTED)
                workspace.loadCommitted(prior)?.envelope()
            else reviews.load(prior)?.envelope()
            val node = validatedNode(prior, bytes ?: return@withMaterialWrite null, false)
                ?: return@withMaterialWrite null
            val request = checkNotNull(HouseholdStockConsumptionReview.decode(node.preparedOperation.payload)).request
            return@withMaterialWrite previous.takeIf {
                request.stockItem == stockItemRef && request.quantity == quantity &&
                    request.sourceRef == sourceRef && request.intentKey == intentId
            }
        }
        val live = context() ?: return@withMaterialWrite null
        val probe = HouseholdStockConsumptionDevRequest(
            live, stockItemRef, quantity, sourceRef, intentId, 0L)
        val current = rows.position(probe) ?: return@withMaterialWrite null
        val request = probe.copy(ledgerRevision = current.revision)
        val decision = admission.review(request) as? HouseholdStockConsumptionDevDecision.ReviewOnly
            ?: return@withMaterialWrite null
        if (decision.position != current || decision.contract != descriptor) return@withMaterialWrite null
        val draft = decision.review
        val node = ApplyPlanNode(PlanNodeId("household-stock-consume"),
            PreparedOperation(descriptor, draft.payload, DependencyStampSet.empty(),
                IdempotencyKey(reviewId)), emptySet())
        val plan = PreparedPlan.of(node)
        if (previous != null) return@withMaterialWrite previous.takeIf {
            it.planFingerprint == plan.planFingerprint
        }
        val summary = "Consumir " + quantity.thousandths.toBigDecimal().movePointLeft(3)
            .stripTrailingZeros().toPlainString() + " " + quantity.unit.name +
            " de " + stockItemRef + " desde " + sourceRef
        val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint,
            summary, PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review:$key"),
        ))
        (saved as? WorkspacePlanReviewWriteResult.Saved)?.review
    }

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        synchronized(grantLock) { gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val observed = workspace.loadCommitted(context)
            if (observed != null) return@withMaterialWrite committedRetry(context, observed)
            val approved = reviews.load(context) ?: return@withMaterialWrite denied()
            val envelope = approved.envelope()
            val node = validatedNode(context, envelope, currentEpochRequired = true) ?: return@withMaterialWrite denied()
            val intent = checkNotNull(HouseholdStockConsumptionReview.decode(node.preparedOperation.payload))
            if (!policy(principal, operation, intent.request.stockItem)) return@withMaterialWrite denied()
            if (approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied()
            val confirmedAt = confirmationClock()
            if (confirmedAt < 0L) return@withMaterialWrite denied()
            var admittedIdentity: ApplyAttemptIdentity? = null
            var consumed = false
            val authorization = ApplyTransactionAuthorizationPort { plan, identity, actual, scope ->
                !consumed && admittedIdentity == identity && scope.transactionId == identity.transactionId &&
                    context.planFingerprint == plan.planFingerprint && node == actual && selected(context.conversationId) &&
                    policy(principal, operation, intent.request.stockItem)
            }
            val finalGate = ApplyTransactionFinalCommitGate { plan, identity, receipt, nodes, scope ->
                val allowed = nodes.singleOrNull() == node && receipt.transactionId == identity.transactionId &&
                    receipt.planFingerprint == context.planFingerprint &&
                    authorization.isAuthorized(plan, identity, node, scope)
                if (allowed) consumed = true
                allowed
            }
            coordinator(confirmedAt, authorization, finalGate) { applying, identity ->
                val live = reviews.load(applying.context())
                val authorized = live?.review == applying && live.envelope().contentEquals(envelope) &&
                    applying.state == ConversationPlanReviewState.APPLYING &&
                    applying.transactionId == identity.transactionId && applying.reviewId == context.reviewId &&
                    applying.conversationId == context.conversationId && applying.planFingerprint == context.planFingerprint &&
                    selected(context.conversationId) && policy(principal, operation, intent.request.stockItem)
                if (authorized) admittedIdentity = identity
                authorized
            }.apply(context)
        } }

    private fun committedRetry(
        context: ConversationPlanReviewContext,
        committed: TrustedCommittedConversationPlan,
    ): ReviewedConversationApplyResult {
        val node = validatedNode(context, committed.envelope(), currentEpochRequired = false) ?: return denied()
        val draft = HouseholdStockConsumptionReview.decode(node.preparedOperation.payload) ?: return denied()
        if (!policy(principal, operation, draft.request.stockItem)) return denied()
        val receipt = committed.review.receipt ?: return denied()
        val attempt = session.attempts.find(receipt.transactionId) ?: return denied()
        if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
            attempt.planFingerprint != receipt.planFingerprint ||
            session.receipts.find(receipt.transactionId) != receipt ||
            !selected(context.conversationId)) return denied()
        return ReviewedConversationApplyResult.Committed(receipt, committed.review)
    }

    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val saved = reviews.load(context) ?: return@withMaterialWrite denied()
            val node = validatedNode(context, saved.envelope(), currentEpochRequired = false)
                ?: return@withMaterialWrite denied()
            val intent = checkNotNull(HouseholdStockConsumptionReview.decode(node.preparedOperation.payload))
            if (!policy(principal, operation, intent.request.stockItem)) return@withMaterialWrite denied()
            coordinator(0L).reconcileCommittedOnly(context)
        }

    private fun coordinator(
        confirmedAt: Long,
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ): ReviewedConversationApplyCoordinator {
        val bindings = listOf(ConsumptionBinding(confirmedAt))
        return ReviewedConversationApplyCoordinator(
            workspace = reviews, decode = PreparedPlanEnvelopeCodec::decode,
            retry = ApplyRetryReceiptGate(session.identities, session.receipts),
            preflight = ApplyPreflightGate(catalog, session.dependencies, bindings),
            bindings = ApplyBindingResolutionGate(bindings),
            executor = ApplyTransactionExecutor(session.transactions, session.commits, authorization, finalGate),
            attempts = session.attempts, receipts = session.receipts, afterBeginApply = afterBegin,
        )
    }

    private inner class ConsumptionBinding(private val confirmedAt: Long) : OperationBindingPort, OperationRevalidationPort {
        override val operationId = operation
        override val descriptor = OperationBindingDescriptor(operation, ExecutorId("household"), ExecutionProfile.REALITY_MUTATION)

        override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
            if (admissible(preparedOperation) != null) OperationRevalidationResult.Valid
            else OperationRevalidationResult.Rejected(OperationRevalidationFailure.PRECONDITION_FAILED, CONFLICT)

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult {
            val current = admissible(preparedOperation) ?: return OperationBindingExecutionResult.Rejected(CONFLICT)
            return if (rows.apply(current, confirmedAt, transactionScope))
                OperationBindingExecutionResult.Completed
            else OperationBindingExecutionResult.Rejected(CONFLICT)
        }

        private fun admissible(prepared: PreparedOperation): HouseholdStockConsumptionReview? {
            val draft = decode(prepared, true) ?: return null
            val admissionResult = admission.review(draft.request)
            if (admissionResult !is HouseholdStockConsumptionDevDecision.ReviewOnly) return null
            return draft.takeIf {
                rows.position(it.request)?.revision == it.request.ledgerRevision &&
                    policy(principal, operation, it.request.stockItem)
            }
        }
    }

    private fun decode(prepared: PreparedOperation, currentEpochRequired: Boolean): HouseholdStockConsumptionReview? {
        if (prepared.contract != contract() || prepared.dependencyStamps != DependencyStampSet.empty()) return null
        return HouseholdStockConsumptionReview.decode(prepared.payload)?.takeIf {
            val context = it.request.context
            context.activePointer == generation && context.principal == principal &&
                (!currentEpochRequired || context.hostEpoch == hostEpoch)
        }
    }

    private fun validatedNode(
        context: ConversationPlanReviewContext, envelope: ByteArray, currentEpochRequired: Boolean,
    ): ApplyPlanNode? {
        if (!context.reviewId.startsWith(prefix)) return null
        val plan = runCatching { PreparedPlanEnvelopeCodec.decode(envelope) }.getOrNull() ?: return null
        if (plan.planFingerprint != context.planFingerprint) return null
        return plan.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf {
            it.dependsOn.isEmpty() && it.preparedOperation.idempotencyKey == IdempotencyKey(context.reviewId) &&
                decode(it.preparedOperation, currentEpochRequired) != null
        }
    }

    private fun ownerCurrent(): Boolean =
        !closed && currentEpoch() == hostEpoch && ownerReady() && pointer.read() == pinned

    private fun foregroundCurrent(): Boolean = ownerCurrent() && foreground()
    private fun selected(chat: ConversationId): Boolean =
        foregroundCurrent() && workspace.snapshot().workspace.activeConversationId == chat

    private fun contract(): OperationContractDescriptor? = catalog.current(operation)?.takeIf {
        it.operationId == operation && it.capabilityId == CapabilityId("N-035") &&
            it.canonicalExecutor == ExecutorId("household") && it.executionProfile == ExecutionProfile.REALITY_MUTATION &&
            it.exposure == ContractExposure.BINDABLE
    }

    private fun denied() = ReviewedConversationApplyResult.Denied("stock_consumption_confirmation_unavailable")

    override fun close() = synchronized(grantLock) {
        closed = true
        session.close()
    }

    private companion object {
        const val CONFLICT = "stock_consumption_conflict_or_stale_review"
    }
}
