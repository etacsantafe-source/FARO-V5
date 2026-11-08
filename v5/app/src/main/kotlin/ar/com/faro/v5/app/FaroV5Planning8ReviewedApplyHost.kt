package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.reminders.api.ReminderCreatePayload
import ar.com.faro.v5.owner.reminders.api.ReminderOperations
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemAddPayload
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemStatus
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.store.reality.RealityStore

/**
 * Dormant, single-request Planning8 host integration; NOT in any shipping or
 * v2CoreDev Activity/CompositionRoot, and no user-facing Apply port is exported.
 *
 * A future verified Android onClick must invoke [executeAfterExplicitHumanRequest].
 * The sandbox evidence is TECHNICAL, not an authenticated human account. The
 * caller must pin one active schema8 generation, a single shared maintenance
 * gate and an official catalog; cross-process writers remain uncertified.
 *
 * Lock order: MaterialWriteGate -> owner grant -> Workspace CAS -> Reality8 SQL.
 * Never read Workspace or call owner.policyEpochOrNull inside Reality.run.
 */
/**
 * N033C3: inert provenance result, NEVER an Undo grant or cancellation.
 * A future distinct Review and fresh owner permission are still required.
 */
internal data class Planning8UndoOriginPreview(
    val sourceOperationId: OperationId,
    val objectId: String,
    val originalReceipt: ExecutionReceipt,
    val observedOwnerRevision: Long,
)

internal class FaroV5Planning8ReviewedApplyHost(
    private val generationId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val workspace: TrustedConversationApplyReviewPort,
    private val reality: RealityStore,
    private val materialGate: MaterialWriteGate,
    private val sandbox: FaroV5AndroidSandboxPrincipal,
    private val catalog: OperationContractLookupPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
) : AutoCloseable {
    @Volatile private var closed = false

    init {
        require(generationId.isNotBlank() && pinned.generationId.value == generationId)
        require(expectedHostEpoch.isNotBlank())
        check(activePointer.read() == pinned) { "Planning8 host generation not pinned" }
    }

    // Factories fail closed before granting anything if Reality is not schema8.
    private val operationBindings = reality.planningCreateBindingsForTrustedHost(catalog)
    private val operationRevalidators = reality.planningCreateRevalidatorsForTrustedHost(catalog)

    private fun hostCurrent(): Boolean = runCatching {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private val canonical = FaroV5Planning8ApprovedObjectGate(
        generationId, activePointer, ::hostCurrent, workspace, catalog,
    )
    private val policy = FaroV5Planning8LocalUserRequestPolicy(
        canonical, sandbox, activePointer, ::hostCurrent,
    )
    private val grants = FaroV5Planning8ObjectGrantOwner(
        principalId = requireNotNull(policy.principalIdOrNull()) {
            "Planning8 verified local sandbox unavailable"
        },
        pinned = pinned, activePointer = activePointer,
        hostLifecycleEpochOrNull = hostEpochOrNull, expectedHostEpoch = expectedHostEpoch,
        policy = policy,
    )
    private val approvedWithGrant = FaroV5Planning8ApprovedObjectGate(
        generationId, activePointer, ::hostCurrent, workspace, catalog, grants,
    )
    private val bridge = FaroV5Planning8ReviewGrantLeaseBridge(
        approvedWithGrant, grants, activePointer, ::hostCurrent,
    )

    /** Pure scope test for the in-SQL authorization hook (NO Workspace access). */
    private fun nodeScope(node: ApplyPlanNode): Planning8ObjectScope? = runCatching {
        val op = node.preparedOperation
        when (op.contract.operationId) {
            ReminderOperations.CREATE -> ReminderCreatePayload.decode(op.payload)
                ?.takeIf { it.linkedMatterId == null }
                ?.let { Planning8ObjectScope(ReminderOperations.CREATE, it.id.value) }
            MonthlyPlanOperations.ITEM_ADD -> MonthlyPlanItemAddPayload.decode(op.payload)
                ?.takeIf {
                    it.status == MonthlyPlanItemStatus.PLANNED &&
                        it.paymentConfirmedAtEpochMillis == null
                }?.let { Planning8ObjectScope(MonthlyPlanOperations.ITEM_ADD, it.id.value) }
            else -> null
        }
    }.getOrNull()

    /**
     * This is deliberately NOT a ConversationApplyRequestPort. Only a future
     * trusted onClick adapter may call it; a model or Search result cannot.
     * One fresh host instance is required per separate human Apply request.
     */
    fun executeAfterExplicitHumanRequest(
        context: ConversationPlanReviewContext,
    ): ConversationApplyHostOutcome = materialGate.withMaterialWrite {
        if (!hostCurrent()) {
            return@withMaterialWrite ConversationApplyHostOutcome.Denied("planning_host_unavailable")
        }
        val ticket = policy.issueAfterTrustedHumanApplyRequest(context)
            ?: return@withMaterialWrite ConversationApplyHostOutcome.Denied(
                "planning_user_request_unavailable")
        val nonce = grants.grantAfterTrustedPolicyCheck(ticket.scope)
            ?: return@withMaterialWrite ConversationApplyHostOutcome.Denied(
                "planning_object_grant_unavailable")

        val admitted = bridge.withReviewedGrantLease(
            context, ticket.scope, nonce,
        ) {
            // Captured entirely within the SAME owner monitor. Promotion is
            // done after Workspace BeginApply but BEFORE Reality.run starts.
            var admittedIdentity: ApplyAttemptIdentity? = null
            val authorization = ApplyTransactionAuthorizationPort {
                    preparedPlan, identity, node, transactionScope ->
                runCatching {
                    admittedIdentity == identity &&
                        identity.planFingerprint == preparedPlan.planFingerprint &&
                        transactionScope.transactionId == identity.transactionId &&
                        preparedPlan.applyPlan.nodesInTopologicalOrder()
                            .singleOrNull()?.nodeId == node.nodeId &&
                        node.dependsOn.isEmpty() &&
                        nodeScope(node) == ticket.scope &&
                        hostCurrent()
                }.getOrDefault(false)
            }
            val coordinator = ReviewedConversationApplyCoordinator(
                workspace = workspace,
                decode = PreparedPlanEnvelopeCodec::decode,
                retry = ApplyRetryReceiptGate(
                    reality.applyAttemptIdentities, reality.executionReceiptLookup,
                ),
                preflight = ApplyPreflightGate(
                    contracts = catalog, dependencyStamps = reality,
                    operationRevalidators = operationRevalidators,
                ),
                bindings = ApplyBindingResolutionGate(operationBindings),
                executor = ApplyTransactionExecutor(reality, reality, authorization),
                attempts = reality.applyAttemptLookup,
                receipts = reality.executionReceiptLookup,
                afterBeginApply = { applying, identity ->
                    // Workspace reads here are safe: Reality mutex is NOT held.
                    val promoted = policy.promoteAfterCanonicalBeginApply(
                        applying.context(), identity,
                    )
                    val stillAuthorized = promoted && hostCurrent() &&
                        grants.epochOrNull(ticket.scope, pinned) == nonce
                    if (stillAuthorized) admittedIdentity = identity
                    stillAuthorized
                },
            )
            coordinator.apply(context).toHostOutcome()
        }
        when (admitted) {
            is Planning8GrantLeaseResult.Admitted -> admitted.value
            Planning8GrantLeaseResult.Denied ->
                ConversationApplyHostOutcome.Denied("planning_scope_or_review_unavailable")
        }
    }

    /**
     * Read-only N033C3 provenance inspection. The receipt alone has no object
     * identity; prove the exact original immutable COMMITTED Review, the
     * Reality journal/receipt, and the still-unchanged owner object.
     *
     * This preview does NOT authorize Undo, schedule a cancellation, create
     * PREPARED, or expose a Surface/Chat execution port.
     */
    internal fun inspectCommittedUndoOriginForTrustedHost(
        context: ConversationPlanReviewContext,
    ): Planning8UndoOriginPreview? = runCatching {
        if (!hostCurrent()) return@runCatching null
        val loaded = workspace.load(context) ?: return@runCatching null
        val review = loaded.review
        if (review.context() != context ||
            review.state != ConversationPlanReviewState.COMMITTED
        ) return@runCatching null
        val transactionId = review.transactionId ?: return@runCatching null
        val receipt = review.receipt ?: return@runCatching null
        if (receipt.transactionId != transactionId ||
            receipt.planFingerprint != review.planFingerprint
        ) return@runCatching null
        val attempt = reality.applyAttemptLookup.find(transactionId)
            ?: return@runCatching null
        if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
            attempt.transactionId != transactionId ||
            attempt.planFingerprint != review.planFingerprint ||
            reality.executionReceiptLookup.find(transactionId) != receipt
        ) return@runCatching null

        val plan = PreparedPlanEnvelopeCodec.decode(loaded.envelope())
        if (plan.planFingerprint != review.planFingerprint) return@runCatching null
        val node = plan.applyPlan.nodesInTopologicalOrder().singleOrNull()
            ?: return@runCatching null
        if (node.dependsOn.isNotEmpty()) return@runCatching null
        val operation = node.preparedOperation
        val descriptor = operation.contract
        if (catalog.current(descriptor.operationId) != descriptor ||
            descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            descriptor.exposure != ContractExposure.BINDABLE
        ) return@runCatching null

        val snapshot = reality.planningBackup.snapshot()
        val preview = when (descriptor.operationId) {
            ReminderOperations.CREATE -> {
                if (descriptor.capabilityId != ReminderOperations.CAPABILITY_ID ||
                    descriptor.canonicalExecutor != ReminderOperations.EXECUTOR_ID
                ) return@runCatching null
                val original = ReminderCreatePayload.decode(operation.payload)
                    ?: return@runCatching null
                val actual = snapshot.reminders.rules.singleOrNull { it.id == original.id }
                if (!original.enabled || original.linkedMatterId != null ||
                    actual != original
                ) return@runCatching null
                Planning8UndoOriginPreview(
                    descriptor.operationId, original.id.value, receipt,
                    snapshot.reminders.revision,
                )
            }
            MonthlyPlanOperations.ITEM_ADD -> {
                if (descriptor.capabilityId != MonthlyPlanOperations.CAPABILITY_ID ||
                    descriptor.canonicalExecutor != MonthlyPlanOperations.EXECUTOR_ID
                ) return@runCatching null
                val original = MonthlyPlanItemAddPayload.decode(operation.payload)
                    ?: return@runCatching null
                val actual = snapshot.monthlyPlan.items.singleOrNull { it.id == original.id }
                if (original.status != MonthlyPlanItemStatus.PLANNED ||
                    original.paymentConfirmedAtEpochMillis != null ||
                    actual != original
                ) return@runCatching null
                Planning8UndoOriginPreview(
                    descriptor.operationId, original.id.value, receipt,
                    snapshot.monthlyPlan.revision,
                )
            }
            else -> return@runCatching null
        }
        // No owner lock crosses Workspace access; a changed object/generation
        // rejects this preview. A future executable Undo must recheck at SQL.
        if (!hostCurrent() || reality.planningBackup.snapshot() != snapshot)
            null else preview
    }.getOrNull()

    /** Close waits for any admitted transaction to finish before policy loss. */
    override fun close() {
        grants.close()
        closed = true
        policy.close()
        sandbox.close()
    }
}
