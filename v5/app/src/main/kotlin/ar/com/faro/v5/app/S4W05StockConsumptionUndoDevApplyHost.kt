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
 * Isolated N-016 stock Undo. Neither a public Android entry point nor a
 * source of permission: the caller must supply independent live human consent.
 * The only writer is the previously certified A2C1E SQL15 Undo port.
 */
internal class S4W05StockConsumptionUndoDevApplyHost(
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

    private val operation = OperationId("undo.execute")
    private val originalOperation = HouseholdStockConsumptionReview.OPERATION
    private val pointer = FileActiveGenerationPointer(root)
    private val generation = HouseholdReviewGeneration(pinned.generationId.value, pinned.sequence)
    private val prefix = "w05:consume-undo:" + CanonicalHashBuilder.domain("w05.stock.consume.undo.principal")
        .string("principal", principal).sha256Hex().take(24) + ":"
    private val grantLock = Any()
    @Volatile private var closed = false
    private val session = FaroV5GenerationStoreGraph.openActiveHousehold15DevForConsumptionUndoHost(
        root, pinned, gate, CanonicalPreparedPlanEnvelopeVerifier, ::ownerCurrent,
    )
    private val workspace = session.workspace
    private val undos = session.undos(principal, generation)
    private val reviews = wrapReview(workspace)

    val reviewDecisions: ConversationReviewPort get() = workspace
    val chatSelection: ConversationWorkspaceSelectionPort get() = workspace

    private fun liveContext(): HouseholdStockReceiveDevContext? =
        if (foregroundCurrent()) HouseholdStockReceiveDevContext(
            principal, hostEpoch, generation, isolatedDev = true, foreground = true,
        ) else null

    private val admission = HouseholdStockConsumptionUndoDevAdmission(
        catalog, ::liveContext,
        TrustedConsumptionUndoCommittedOriginPort { originalCommitted(it) },
        TrustedConsumptionUndoPositionPort { consumptionRef -> undos.position(consumptionRef) },
        HouseholdStockConsumptionUndoConsentPort { request ->
            policy(principal, operation, request.intent.targetObjectId)
        },
    )

    /** Never mutates SQL15; only an origin authenticated COMMITTED can be undone. */
    fun position(consumptionRef: String): HouseholdStockConsumptionUndoCurrentPosition? =
        gate.withMaterialWrite {
            if (!foregroundCurrent() || !policy(principal, operation, consumptionRef))
                return@withMaterialWrite null
            undos.position(consumptionRef).takeIf { foregroundCurrent() }
        }

    /** The original COMMITTED Review is the only source of the N-016 payload. */
    fun submitUndo(
        sourceReview: ConversationPlanReviewContext,
        localRequestId: String,
    ): ConversationPlanReviewSnapshot? = gate.withMaterialWrite {
        val chat = sourceReview.conversationId
        if (!selected(chat) || localRequestId.isBlank() || localRequestId.length > 256)
            return@withMaterialWrite null
        val contract = contract() ?: return@withMaterialWrite null
        val source = committedSource(sourceReview) ?: return@withMaterialWrite null
        val consumptionRef = source.stockReview.request.intentKey
        if (!policy(principal, operation, consumptionRef)) return@withMaterialWrite null
        val hash = CanonicalHashBuilder.domain("w05.stock.consume.undo.review")
            .string("principal", principal).string("chat", chat.value)
            .string("request", localRequestId).sha256Hex()
        val reviewId = prefix + hash
        val intentKey = "w05cu:" + hash
        val before = workspace.snapshot()
        val timeline = runBlocking { workspace.readTimeline(chat) } ?: return@withMaterialWrite null
        if (timeline.revision != before.revision || timeline.authorityToken.isBlank())
            return@withMaterialWrite null
        val previous = runBlocking { workspace.reviews(chat) }
            .firstOrNull { it.reviewId == reviewId }
        if (!selected(chat) || workspace.snapshot().revision != before.revision)
            return@withMaterialWrite null
        if (previous != null) {
            val saved = if (previous.state == ConversationPlanReviewState.COMMITTED)
                workspace.loadCommitted(previous.context())?.envelope()
            else reviews.load(previous.context())?.envelope()
            val node = validatedNode(previous.context(), saved ?: return@withMaterialWrite null)
                ?: return@withMaterialWrite null
            val original = UndoExecutePayload.decode(node.preparedOperation.payload)
                ?: return@withMaterialWrite null
            return@withMaterialWrite previous.takeIf {
                original.sourceReview == sourceReview && original.sourceReceipt == source.sourceReceipt &&
                    original.targetObjectId == consumptionRef
            }
        }
        val at = confirmationClock()
        if (at < 0L) return@withMaterialWrite null
        val intent = runCatching {
            UndoExecuteIntent(
                sourceReview, source.sourceReceipt, originalOperation, consumptionRef,
                source.stockReview.payload, at,
            )
        }.getOrNull() ?: return@withMaterialWrite null
        val position = undos.position(consumptionRef) ?: return@withMaterialWrite null
        val context = liveContext() ?: return@withMaterialWrite null
        val request = HouseholdStockConsumptionUndoDevRequest(context, intent, intentKey, position.ledgerRevision)
        val approved = admission.review(request) as? HouseholdStockConsumptionUndoDevDecision.ReviewOnly
            ?: return@withMaterialWrite null
        if (approved.origin != source) return@withMaterialWrite null
        val node = ApplyPlanNode(
            PlanNodeId("household-consumption-undo"),
            PreparedOperation(
                contract, UndoExecutePayload.payload(intent), DependencyStampSet.empty(),
                IdempotencyKey(reviewId + ":rev:" + position.ledgerRevision),
            ),
            emptySet(),
        )
        val plan = PreparedPlan.of(node)
        val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, reviewId, timeline.authorityToken, before.revision,
            plan.planFingerprint, "Deshacer consumo físico de " + approved.position.stockItem,
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review:" + hash),
        ))
        (saved as? WorkspacePlanReviewWriteResult.Saved)?.review
    }

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        synchronized(grantLock) { gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val committed = workspace.loadCommitted(context)
            if (committed != null) return@withMaterialWrite committedRetry(context, committed)
            val approved = reviews.load(context) ?: return@withMaterialWrite denied()
            val envelope = approved.envelope()
            val node = validatedNode(context, envelope) ?: return@withMaterialWrite denied()
            val intent = UndoExecutePayload.decode(node.preparedOperation.payload)
                ?: return@withMaterialWrite denied()
            if (intent.sourceReview.conversationId != context.conversationId ||
                !policy(principal, operation, intent.targetObjectId) ||
                approved.review.state != ConversationPlanReviewState.APPROVED
            ) return@withMaterialWrite denied()
            val initial = ready(intent, node.preparedOperation.idempotencyKey.value)
                ?: return@withMaterialWrite denied()
            val confirmedAt = confirmationClock()
            if (confirmedAt < intent.cancelledAtEpochMillis) return@withMaterialWrite denied()
            var admittedIdentity: ApplyAttemptIdentity? = null
            var consumed = false
            val binding = UndoBinding(confirmedAt)
            val authorization = ApplyTransactionAuthorizationPort { plan, identity, actual, scope ->
                !consumed && admittedIdentity == identity && scope.transactionId == identity.transactionId &&
                    plan.planFingerprint == context.planFingerprint && node == actual &&
                    foregroundCurrent() && policy(principal, operation, intent.targetObjectId)
            }
            val finalGate = ApplyTransactionFinalCommitGate { plan, identity, receipt, nodes, scope ->
                // No Workspace or independent Reality receipt reads while SQL15 is writing.
                // The source COMMITTED proof was pinned and rechecked by afterBeginApply.
                // SQL15's CAS ensures receipt/balance cannot change between preview and COMMIT.
                val allowed = nodes.singleOrNull() == node && receipt.transactionId == identity.transactionId &&
                    receipt.planFingerprint == context.planFingerprint &&
                    binding.hasPromoted(node.preparedOperation, identity.transactionId) &&
                    authorization.isAuthorized(plan, identity, node, scope)
                if (allowed) consumed = true
                allowed
            }
            coordinator(authorization, finalGate, binding = binding) { applying, identity ->
                // Called AFTER Workspace BeginApply, BEFORE Reality.run; safe to recheck
                // both the sealed Workspace source and durable Reality journal here.
                val fresh = reviews.load(applying.context())
                val rechecked = ready(intent, node.preparedOperation.idempotencyKey.value)
                val allowed = fresh?.review == applying && fresh.envelope().contentEquals(envelope) &&
                    applying.state == ConversationPlanReviewState.APPLYING &&
                    applying.transactionId == identity.transactionId &&
                    applying.conversationId == context.conversationId &&
                    applying.reviewId == context.reviewId &&
                    applying.authorityToken == context.authorityToken &&
                    applying.planFingerprint == context.planFingerprint &&
                    context.expectedRevision < Long.MAX_VALUE &&
                    applying.revision == context.expectedRevision + 1L &&
                    rechecked == initial &&
                    selected(context.conversationId) && policy(principal, operation, intent.targetObjectId)
                if (allowed) {
                    admittedIdentity = identity
                    binding.promote(node.preparedOperation, initial, identity.transactionId)
                }
                allowed
            }.apply(context)
        } }

    /** Never replay UNKNOWN or COMMITTED as a second undos. */
    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ReviewedConversationApplyResult =
        gate.withMaterialWrite {
            if (!selected(context.conversationId)) return@withMaterialWrite denied()
            val saved = reviews.load(context) ?: return@withMaterialWrite denied()
            val node = validatedNode(context, saved.envelope()) ?: return@withMaterialWrite denied()
            val intent = UndoExecutePayload.decode(node.preparedOperation.payload)
                ?: return@withMaterialWrite denied()
            if (intent.sourceReview.conversationId != context.conversationId ||
                originalCommitted(intent) == null || !policy(principal, operation, intent.targetObjectId)
            ) return@withMaterialWrite denied()
            coordinator().reconcileCommittedOnly(context)
        }

    private fun committedRetry(
        context: ConversationPlanReviewContext,
        saved: TrustedCommittedConversationPlan,
    ): ReviewedConversationApplyResult {
        val node = validatedNode(context, saved.envelope()) ?: return denied()
        val intent = UndoExecutePayload.decode(node.preparedOperation.payload) ?: return denied()
        if (intent.sourceReview.conversationId != context.conversationId ||
            originalCommitted(intent) == null ||
            !policy(principal, operation, intent.targetObjectId)
        ) return denied()
        val receipt = saved.review.receipt ?: return denied()
        val attempt = session.attempts.find(receipt.transactionId) ?: return denied()
        if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
            attempt.planFingerprint != receipt.planFingerprint ||
            session.receipts.find(receipt.transactionId) != receipt ||
            !selected(context.conversationId)
        ) return denied()
        return ReviewedConversationApplyResult.Committed(receipt, saved.review)
    }

    private fun coordinator(
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        binding: UndoBinding = UndoBinding(0L),
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ): ReviewedConversationApplyCoordinator {
        val bindings = listOf(binding)
        return ReviewedConversationApplyCoordinator(
            workspace = reviews, decode = PreparedPlanEnvelopeCodec::decode,
            retry = ApplyRetryReceiptGate(session.identities, session.receipts),
            preflight = ApplyPreflightGate(catalog, session.dependencies, bindings),
            bindings = ApplyBindingResolutionGate(bindings),
            executor = ApplyTransactionExecutor(session.transactions, session.commits, authorization, finalGate),
            attempts = session.attempts, receipts = session.receipts, afterBeginApply = afterBegin,
        )
    }

    private inner class UndoBinding(private val confirmedAt: Long) : OperationBindingPort, OperationRevalidationPort {
        private var admittedPrepared: PreparedOperation? = null
        private var admitted: HouseholdStockConsumptionUndoDevDecision.ReviewOnly? = null
        private var admittedTx: TransactionId? = null

        fun promote(
            prepared: PreparedOperation,
            decision: HouseholdStockConsumptionUndoDevDecision.ReviewOnly,
            transactionId: TransactionId,
        ) {
            check(admitted == null && admittedPrepared == null && admittedTx == null)
            admittedPrepared = prepared
            admitted = decision
            admittedTx = transactionId
        }

        fun hasPromoted(prepared: PreparedOperation, transactionId: TransactionId): Boolean =
            admitted != null && admittedPrepared == prepared && admittedTx == transactionId

        override val operationId = operation
        override val descriptor = OperationBindingDescriptor(
            operation, ExecutorId("undo-coordinator"), ExecutionProfile.REALITY_MUTATION,
        )

        override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
            if (ready(preparedOperation) != null) OperationRevalidationResult.Valid
            else OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED, "consumption_undo_origin_or_ledger_stale",
            )

        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult {
            // Reality.run may only use the frozen host-approved evidence, live grant
            // and SQL15 CAS. Never reenter Workspace/loadCommitted/attempts here.
            val accepted = admitted?.takeIf {
                hasPromoted(preparedOperation, transactionScope.transactionId)
            } ?: return OperationBindingExecutionResult.Rejected("consumption_undo_origin_or_ledger_stale")
            val intent = accepted.request.intent
            if (!foregroundCurrent() || !policy(principal, operation, intent.targetObjectId))
                return OperationBindingExecutionResult.Rejected("consumption_undo_consent_revoked")
            // Committed original and N-016 grant were rechecked before Reality.run.
            // Within SQL15 only promoted evidence, consent and SQL15 CAS are read.
            return if (undos.apply(accepted, confirmedAt, transactionScope))
                OperationBindingExecutionResult.Completed
            else OperationBindingExecutionResult.Rejected("consumption_undo_sql15_conflict")
        }
    }

    private fun ready(prepared: PreparedOperation): HouseholdStockConsumptionUndoDevDecision.ReviewOnly? {
        if (prepared.contract != contract() ||
            prepared.dependencyStamps != DependencyStampSet.empty()) return null
        val intent = UndoExecutePayload.decode(prepared.payload) ?: return null
        val reviewId = prepared.idempotencyKey.value
        return ready(intent, reviewId)
    }

    private fun ready(intent: UndoExecuteIntent, idempotency: String): HouseholdStockConsumptionUndoDevDecision.ReviewOnly? {
        val delimiter = ":rev:"
        val split = idempotency.lastIndexOf(delimiter)
        if (split <= 0 || !foregroundCurrent()) return null
        val reviewId = idempotency.substring(0, split)
        val pinnedRevision = idempotency.substring(split + delimiter.length).toLongOrNull()
            ?: return null
        if (!reviewId.startsWith(prefix) || pinnedRevision <= 0L) return null
        val hash = reviewId.removePrefix(prefix)
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val position = undos.position(intent.targetObjectId) ?: return null
        if (position.ledgerRevision != pinnedRevision) return null
        val context = liveContext() ?: return null
        val request = HouseholdStockConsumptionUndoDevRequest(context, intent, "w05cu:" + hash, pinnedRevision)
        return admission.review(request) as? HouseholdStockConsumptionUndoDevDecision.ReviewOnly
    }

    private fun originalCommitted(intent: UndoExecuteIntent): HouseholdStockConsumptionUndoCommittedOrigin? =
        committedSource(intent.sourceReview)?.takeIf {
            it.sourceReceipt == intent.sourceReceipt &&
                it.stockReview.payload == intent.originalCreatePayload &&
                it.stockReview.request.intentKey == intent.targetObjectId &&
                intent.sourceOperationId == originalOperation
        }

    private fun committedSource(context: ConversationPlanReviewContext): HouseholdStockConsumptionUndoCommittedOrigin? {
        if (!context.reviewId.startsWith("w05:consume:")) return null
        val committed = workspace.loadCommitted(context) ?: return null
        val receipt = committed.review.receipt ?: return null
        val attempt = session.attempts.find(receipt.transactionId) ?: return null
        if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
            attempt.planFingerprint != receipt.planFingerprint ||
            receipt.planFingerprint != context.planFingerprint ||
            session.receipts.find(receipt.transactionId) != receipt
        ) return null
        val plan = runCatching { PreparedPlanEnvelopeCodec.decode(committed.envelope()) }
            .getOrNull() ?: return null
        if (plan.planFingerprint != context.planFingerprint) return null
        val node = plan.applyPlan.nodesInTopologicalOrder().singleOrNull() ?: return null
        if (node.dependsOn.isNotEmpty() ||
            node.preparedOperation.dependencyStamps != DependencyStampSet.empty() ||
            node.preparedOperation.idempotencyKey != IdempotencyKey(context.reviewId)
        ) return null
        val original = node.preparedOperation
        val sourceContract = catalog.current(originalOperation) ?: return null
        if (original.contract != sourceContract || sourceContract.capabilityId != CapabilityId("N-035") ||
            sourceContract.canonicalExecutor != ExecutorId("household") ||
            sourceContract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            sourceContract.exposure != ContractExposure.BINDABLE
        ) return null
        val stock = HouseholdStockConsumptionReview.decode(original.payload) ?: return null
        if (stock.request.context.principal != principal ||
            !stock.request.context.isolatedDev || !stock.request.context.foreground ||
            stock.request.reservationRef != null || stock.request.intentKey.isBlank()
        ) return null
        return HouseholdStockConsumptionUndoCommittedOrigin(context, receipt, stock)
    }

    private fun validatedNode(
        context: ConversationPlanReviewContext, envelope: ByteArray,
    ): ApplyPlanNode? {
        if (!context.reviewId.startsWith(prefix)) return null
        val plan = runCatching { PreparedPlanEnvelopeCodec.decode(envelope) }.getOrNull()
            ?: return null
        if (plan.planFingerprint != context.planFingerprint) return null
        return plan.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf {
            it.dependsOn.isEmpty() &&
                it.preparedOperation.idempotencyKey.value.startsWith(context.reviewId + ":rev:") &&
                it.preparedOperation.idempotencyKey.value.removePrefix(context.reviewId + ":rev:")
                    .toLongOrNull()?.let { revision -> revision > 0L } == true &&
                it.preparedOperation.contract == contract() &&
                it.preparedOperation.dependencyStamps == DependencyStampSet.empty() &&
                UndoExecutePayload.decode(it.preparedOperation.payload) != null
        }
    }

    private fun ownerCurrent(): Boolean =
        !closed && currentEpoch() == hostEpoch && ownerReady() && pointer.read() == pinned
    private fun foregroundCurrent(): Boolean = ownerCurrent() && foreground()
    private fun selected(chat: ConversationId): Boolean =
        foregroundCurrent() && workspace.snapshot().workspace.activeConversationId == chat

    private fun contract(): OperationContractDescriptor? = catalog.current(operation)?.takeIf {
        it.operationId == operation && it.capabilityId == CapabilityId("N-016") &&
            it.canonicalExecutor == ExecutorId("undo-coordinator") &&
            it.executionProfile == ExecutionProfile.REALITY_MUTATION &&
            it.exposure == ContractExposure.BINDABLE
    }

    private fun denied() = ReviewedConversationApplyResult.Denied("consumption_undo_confirmation_unavailable")
    override fun close() = synchronized(grantLock) {
        closed = true
        session.close()
    }
}
