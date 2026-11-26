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

/**
 * A2B1B1: DEV-only reservation Review→explicit Confirm→AtomicApply.
 * No model grant, UI shipping, stock consumption, money or background operation.
 */
internal class S4W05StockReservationDevApplyHost(
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
    init { require(principal.isNotBlank() && hostEpoch > 0) }
    private val pointer = FileActiveGenerationPointer(root)
    private val generation = HouseholdReviewGeneration(pinned.generationId.value, pinned.sequence)
    private val grantLock = Any()
    private val prefix = "w05:reserve:" + CanonicalHashBuilder.domain("w05.stock.reservation.principal")
        .string("principal", principal).sha256Hex().take(32) + ":"
    @Volatile private var closed = false
    private val session = FaroV5GenerationStoreGraph.openActiveHousehold14DevForReservationHost(
        root, pinned, gate, CanonicalPreparedPlanEnvelopeVerifier, ::ownerCurrent,
    )
    private val workspace = session.workspace
    private val rows = session.reservations(principal, generation)
    private val reviews = wrapReview(workspace)

    private fun context(): HouseholdStockReceiveDevContext? = if (foregroundCurrent()) {
        HouseholdStockReceiveDevContext(principal, hostEpoch, generation, isolatedDev = true, foreground = true)
    } else null

    private val admission = HouseholdStockReservationDevAdmission(
        catalog, ::context,
        HouseholdStockReservationPositionPort { request ->
            rows.position(request.stockItem, request.quantity.unit.name)?.let { p ->
                val ref = request.reservationRef
                HouseholdStockReservationPosition(
                    p.principal, p.generation, p.stockItemRef, request.quantity.unit, p.ledgerRevision,
                    p.physicalNetThousandths, p.reservedThousandths,
                    if (ref != null) p.remainingByReservation[ref]?.let {
                        HouseholdStockReservationReleasePosition(ref, it)
                    } else null,
                )
            }
        },
        HouseholdStockReservationDevConsentPort {
            policy(principal, it.action.operation, it.stockItem)
        },
    )

    val reviewDecisions: ConversationReviewPort get() = workspace
    val chatSelection: ConversationWorkspaceSelectionPort get() = workspace

    /** Principal-fenced position; never a purchase, consent or Apply capability. */
    fun position(stockItemRef: String, unit: HouseholdNeedUnit) = gate.withMaterialWrite {
        if (!foregroundCurrent() || !stockItemRef.any() ||
            !HouseholdStockReservationAction.entries.any { policy(principal, it.operation, stockItemRef) }
        ) null else rows.position(stockItemRef, unit.name).takeIf { foregroundCurrent() }
    }

    /** Creates a passive Workspace review. Each action and request has its own exact identity. */
    fun submit(
        chat: ConversationId,
        localRequestId: String,
        action: HouseholdStockReservationAction,
        stockItemRef: String,
        quantity: HouseholdNeedQuantity,
        reservationRef: String? = null,
    ): ConversationPlanReviewSnapshot? = gate.withMaterialWrite {
        val operation = action.operation
        if (!selected(chat) || !policy(principal, operation, stockItemRef) ||
            localRequestId.isBlank() || localRequestId.length > 256) return@withMaterialWrite null
        val currentContract = contract(action) ?: return@withMaterialWrite null
        val key = CanonicalHashBuilder.domain("w05.stock.reservation.review")
            .string("principal", principal).string("chat", chat.value)
            .string("operation", operation.value).string("request", localRequestId).sha256Hex()
        val reviewId = prefix + key
        val intentId = prefix + CanonicalHashBuilder.domain("w05.stock.reservation.intent")
            .string("principal", principal).string("operation", operation.value)
            .string("request", localRequestId).sha256Hex()
        val before = workspace.snapshot()
        val timeline = runBlocking { workspace.readTimeline(chat) } ?: return@withMaterialWrite null
        if (timeline.revision != before.revision || timeline.authorityToken.isBlank()) return@withMaterialWrite null
        val previous = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
        if (!selected(chat) || workspace.snapshot().revision != before.revision) return@withMaterialWrite null
        if (previous != null && previous.state != ConversationPlanReviewState.PENDING) {
            val priorContext = previous.context()
            val priorEnvelope = if (previous.state == ConversationPlanReviewState.COMMITTED)
                workspace.loadCommitted(priorContext)?.envelope()
            else reviews.load(priorContext)?.envelope()
            val node = validatedNode(priorContext, priorEnvelope ?: return@withMaterialWrite null, false)
                ?: return@withMaterialWrite null
            val req = checkNotNull(HouseholdStockReservationReview.decode(node.preparedOperation.payload)).request
            return@withMaterialWrite previous.takeIf {
                req.action == action && req.stockItem == stockItemRef && req.quantity == quantity &&
                    req.intentKey == intentId && req.reservationRef == reservationRef
            }
        }
        val observed = rows.position(stockItemRef, quantity.unit.name) ?: return@withMaterialWrite null
        val live = context() ?: return@withMaterialWrite null
        val request = HouseholdStockReservationDevRequest(
            action, live, stockItemRef, quantity, intentId, observed.ledgerRevision, reservationRef,
        )
        val admissionReview = admission.review(request) as? HouseholdStockReservationDevDecision.ReviewOnly
            ?: return@withMaterialWrite null
        if (admissionReview.position.revision != observed.ledgerRevision) return@withMaterialWrite null
        val draft = admissionReview.review
        val node = ApplyPlanNode(PlanNodeId("household-stock-" + action.name.lowercase()),
            PreparedOperation(currentContract, draft.payload, DependencyStampSet.empty(), IdempotencyKey(reviewId)),
            emptySet(),
        )
        val plan = PreparedPlan.of(node)
        if (previous != null) return@withMaterialWrite previous.takeIf {
            it.planFingerprint == plan.planFingerprint
        }
        val verb = if (action == HouseholdStockReservationAction.RESERVE) "Reservar " else "Liberar "
        val summary = verb + quantity.thousandths.toBigDecimal().movePointLeft(3)
            .stripTrailingZeros().toPlainString() + " " + quantity.unit.name + " de " + stockItemRef
        val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint, summary,
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review:$key"),
        ))
        (saved as? WorkspacePlanReviewWriteResult.Saved)?.review
    }

    /** No mutation without an APPROVED Workspace review and current explicit grant. */
    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        synchronized(grantLock) { gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val observed = workspace.loadCommitted(context)
            if (observed != null) return@withMaterialWrite committedRetry(context, observed)
            val approved = reviews.load(context) ?: return@withMaterialWrite denied()
            val envelope = approved.envelope()
            val node = validatedNode(context, envelope, true) ?: return@withMaterialWrite denied()
            val request = checkNotNull(HouseholdStockReservationReview.decode(node.preparedOperation.payload)).request
            if (!policy(principal, request.action.operation, request.stockItem) ||
                approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied()
            val confirmedAt = confirmationClock()
            if (confirmedAt < 0L) return@withMaterialWrite denied()
            var admittedIdentity: ApplyAttemptIdentity? = null
            var consumed = false
            val authorization = ApplyTransactionAuthorizationPort { plan, identity, actual, scope ->
                !consumed && admittedIdentity == identity && scope.transactionId == identity.transactionId &&
                    context.planFingerprint == plan.planFingerprint && node == actual &&
                    selected(context.conversationId) &&
                    policy(principal, request.action.operation, request.stockItem)
            }
            val finalGate = ApplyTransactionFinalCommitGate { plan, identity, receipt, nodes, scope ->
                val allowed = nodes.singleOrNull() == node &&
                    receipt.transactionId == identity.transactionId &&
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
                    applying.conversationId == context.conversationId &&
                    applying.planFingerprint == context.planFingerprint &&
                    selected(context.conversationId) &&
                    policy(principal, request.action.operation, request.stockItem)
                if (authorized) admittedIdentity = identity
                authorized
            }.apply(context)
        } }

    private fun committedRetry(
        context: ConversationPlanReviewContext,
        committed: TrustedCommittedConversationPlan,
    ): ReviewedConversationApplyResult {
        val node = validatedNode(context, committed.envelope(), false) ?: return denied()
        val review = HouseholdStockReservationReview.decode(node.preparedOperation.payload) ?: return denied()
        if (!policy(principal, review.request.action.operation, review.request.stockItem)) return denied()
        val receipt = committed.review.receipt ?: return denied()
        val attempt = session.attempts.find(receipt.transactionId) ?: return denied()
        if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
            attempt.planFingerprint != receipt.planFingerprint ||
            session.receipts.find(receipt.transactionId) != receipt ||
            !selected(context.conversationId)) return denied()
        return ReviewedConversationApplyResult.Committed(receipt, committed.review)
    }

    /** Reconciles a durable COMMITTED receipt only, never replays an UNKNOWN mutation. */
    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val saved = reviews.load(context) ?: return@withMaterialWrite denied()
            val node = validatedNode(context, saved.envelope(), false) ?: return@withMaterialWrite denied()
            val request = checkNotNull(HouseholdStockReservationReview.decode(node.preparedOperation.payload)).request
            if (!policy(principal, request.action.operation, request.stockItem)) return@withMaterialWrite denied()
            coordinator(0L).reconcileCommittedOnly(context)
        }

    private fun coordinator(
        confirmedAt: Long,
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ): ReviewedConversationApplyCoordinator {
        val bindings = HouseholdStockReservationAction.entries.map { ReservationBinding(it, confirmedAt) }
        return ReviewedConversationApplyCoordinator(
            workspace = reviews, decode = PreparedPlanEnvelopeCodec::decode,
            retry = ApplyRetryReceiptGate(session.identities, session.receipts),
            preflight = ApplyPreflightGate(catalog, session.dependencies, bindings),
            bindings = ApplyBindingResolutionGate(bindings),
            executor = ApplyTransactionExecutor(session.transactions, session.commits, authorization, finalGate),
            attempts = session.attempts, receipts = session.receipts, afterBeginApply = afterBegin,
        )
    }

    private inner class ReservationBinding(
        private val action: HouseholdStockReservationAction,
        private val confirmedAt: Long,
    ) : OperationBindingPort, OperationRevalidationPort {
        override val operationId = action.operation
        override val descriptor = OperationBindingDescriptor(action.operation, ExecutorId("household"),
            ExecutionProfile.REALITY_MUTATION)

        override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
            if (admissible(preparedOperation) != null) OperationRevalidationResult.Valid
            else OperationRevalidationResult.Rejected(OperationRevalidationFailure.PRECONDITION_FAILED, CONFLICT)

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult {
            val current = admissible(preparedOperation) ?: return OperationBindingExecutionResult.Rejected(CONFLICT)
            return if (rows.apply(current, confirmedAt, transactionScope)) OperationBindingExecutionResult.Completed
            else OperationBindingExecutionResult.Rejected(CONFLICT)
        }

        private fun admissible(prepared: PreparedOperation): HouseholdStockReservationReview? {
            val review = decode(prepared, true) ?: return null
            if (review.request.action != action ||
                admission.review(review.request) !is HouseholdStockReservationDevDecision.ReviewOnly) return null
            return review.takeIf {
                rows.position(it.request.stockItem, it.request.quantity.unit.name)?.ledgerRevision ==
                    it.request.ledgerRevision
            }
        }
    }

    private fun decode(prepared: PreparedOperation, currentEpochRequired: Boolean): HouseholdStockReservationReview? {
        if (prepared.dependencyStamps != DependencyStampSet.empty()) return null
        val review = HouseholdStockReservationReview.decode(prepared.payload) ?: return null
        if (prepared.contract != contract(review.request.action)) return null
        val ctx = review.request.context
        return review.takeIf {
            ctx.activePointer == generation && ctx.principal == principal &&
                (!currentEpochRequired || ctx.hostEpoch == hostEpoch)
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

    private fun contract(action: HouseholdStockReservationAction): OperationContractDescriptor? =
        catalog.current(action.operation)?.takeIf {
            it.operationId == action.operation && it.capabilityId == CapabilityId("N-035") &&
                it.canonicalExecutor == ExecutorId("household") &&
                it.executionProfile == ExecutionProfile.REALITY_MUTATION && it.exposure == ContractExposure.BINDABLE
        }

    private fun denied() = ReviewedConversationApplyResult.Denied("stock_reservation_confirmation_unavailable")

    override fun close() = synchronized(grantLock) {
        closed = true
        session.close()
    }

    private companion object {
        const val CONFLICT = "stock_reservation_conflict_or_stale_review"
    }
}
