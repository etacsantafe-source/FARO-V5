package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Local DEV Matter Review/Apply. Production ACL is not enabled. */
internal class S4W03MatterReviewHost(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val reality: RealityStore,
    private val gate: MaterialWriteGate,
    private val catalog: OperationContractLookupPort,
    principal: String,
    private val hostEpoch: String,
    private val currentEpoch: () -> String?,
    private val ownerReady: () -> Boolean,
    private val trustedForeground: () -> Boolean,
    policy: Planning8TrustedObjectPolicyPort,
) : AutoCloseable {
    @Volatile private var closed = false
    private val grants = FaroV5Planning8ObjectGrantOwner(
        principal, pinned, pointer, currentEpoch, hostEpoch, policy,
    )
    private val bindings = MatterReviewOperations.supported.map {
        MatterReviewBinding(it, reality.matterPersistence, catalog)
    }

    private fun current(): Boolean = runCatching {
        !closed && ownerReady() && trustedForeground() &&
            pointer.read() == pinned && currentEpoch() == hostEpoch
    }.getOrDefault(false)

    private fun selected(chat: ConversationId): Boolean =
        current() && workspace.snapshot().workspace.activeConversationId == chat

    fun submit(
        chat: ConversationId,
        intent: MatterReviewIntent,
        localRequestId: String,
    ): WorkspacePlanReviewWriteResult? {
        val description = when (val change = intent.change) {
            is MatterReviewChange.Create -> "Crear asunto: " + change.form.title.value
            is MatterReviewChange.Edit -> "Editar asunto: " + change.form.title.value + "; " + change.reason
            is MatterReviewChange.Transition ->
                "Estado " + change.before.name + " a " + change.after.name + ": " + change.reason
            is MatterReviewChange.Snooze ->
                "Modificar fecha a " + (change.dueAtEpochMillis?.toString() ?: "sin fecha") + ": " + change.reason
        }
        val summary = "Asunto " + intent.id.value + "\n" +
            (intent.undoOfSequence?.let { "Deshacer evento #" + it + "\n" } ?: "") + description
        return submitPrepared(chat, intent.operation, MatterReviewPayload.encode(intent), summary, localRequestId)
    }

    private fun submitPrepared(
        chat: ConversationId,
        operation: OperationId,
        payload: CanonicalPayload,
        summary: String,
        localRequestId: String,
    ): WorkspacePlanReviewWriteResult? = runCatching {
        gate.withMaterialWrite {
            if (!selected(chat) || localRequestId.length !in 1..256 || localRequestId.isBlank()) {
                return@withMaterialWrite null
            }
            val before = workspace.snapshot()
            val timeline = runBlocking { workspace.readTimeline(chat) } ?: return@withMaterialWrite null
            if (timeline.revision != before.revision || timeline.authorityToken.isBlank()) {
                return@withMaterialWrite null
            }
            val contract = catalog.current(operation)?.takeIf(MatterReviewOperations::allows)
                ?: return@withMaterialWrite null
            val key = stableKey(chat, operation, localRequestId)
            val prepared = PreparedOperation(
                contract, payload, DependencyStampSet.empty(), IdempotencyKey("w03:matter:" + key),
            )
            val validator = bindings.single { it.operationId == operation }
            if (validator.revalidate(prepared) != OperationRevalidationResult.Valid) return@withMaterialWrite null
            val plan = PreparedPlan.of(ApplyPlanNode(PlanNodeId("matter"), prepared, emptySet()))
            val reviewId = "w03:matter:" + key
            val existing = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
            if (!selected(chat) || workspace.snapshot().revision != before.revision) return@withMaterialWrite null
            if (existing != null) {
                return@withMaterialWrite existing.takeIf {
                    it.planFingerprint == plan.planFingerprint && it.state == ConversationPlanReviewState.PENDING
                }?.let { WorkspacePlanReviewWriteResult.Saved(it, true) }
            }
            workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint,
                summary, PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("w03:review:" + key),
            ))
        }
    }.getOrNull()

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        gate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("matter_review_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val approved = workspace.load(context) ?: return@withMaterialWrite denied
            if (approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied
            val envelope = approved.envelope()
            val node = matterNode(context, envelope) ?: return@withMaterialWrite denied
            val operation = node.preparedOperation.contract.operationId
            val intent = MatterReviewPayload.decode(node.preparedOperation.payload)
                ?.takeIf { it.operation == operation } ?: return@withMaterialWrite denied
            val scope = Planning8ObjectScope(operation, intent.id.value)
            val nonce = grants.grantAfterTrustedPolicyCheck(scope) ?: return@withMaterialWrite denied
            try {
                val leased = grants.withCurrentGrantLease(scope, pinned, nonce) {
                    var attempt: ApplyAttemptIdentity? = null
                    val authorize = ApplyTransactionAuthorizationPort { plan, identity, actual, tx ->
                        current() && attempt == identity && tx.transactionId == identity.transactionId &&
                            plan.planFingerprint == context.planFingerprint && actual == node &&
                            grants.epochOrNull(scope, pinned) == nonce
                    }
                    val finalGate = ApplyTransactionFinalCommitGate { plan, identity, _, checked, tx ->
                        checked.singleOrNull() == node && authorize.isAuthorized(plan, identity, node, tx)
                    }
                    coordinator(authorize, finalGate) { applying, identity ->
                        val start = workspace.load(applying.context())
                        val admitted = start != null && start.review == applying &&
                            applying.state == ConversationPlanReviewState.APPLYING &&
                            applying.transactionId == identity.transactionId &&
                            applying.planFingerprint == context.planFingerprint &&
                            applying.conversationId == context.conversationId &&
                            applying.reviewId == context.reviewId &&
                            start.envelope().contentEquals(envelope) &&
                            selected(context.conversationId) && grants.epochOrNull(scope, pinned) == nonce
                        if (admitted) attempt = identity
                        admitted
                    }.apply(context).toHostOutcome()
                }
                when (leased) {
                    is Planning8GrantLeaseResult.Admitted -> leased.value
                    Planning8GrantLeaseResult.Denied -> denied
                }
            } finally {
                grants.revoke(scope)
            }
        }

    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        gate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("matter_receipt_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val snapshot = workspace.load(context) ?: return@withMaterialWrite denied
            if (matterNode(context, snapshot.envelope()) == null) return@withMaterialWrite denied
            coordinator().reconcileCommittedOnly(context).toHostOutcome()
        }

    private fun matterNode(context: ConversationPlanReviewContext, envelope: ByteArray): ApplyPlanNode? =
        runCatching {
            if (!context.reviewId.startsWith("w03:matter:")) return@runCatching null
            val decoded = PreparedPlanEnvelopeCodec.decode(envelope)
            if (decoded.planFingerprint != context.planFingerprint) return@runCatching null
            decoded.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf {
                val contract = it.preparedOperation.contract
                it.dependsOn.isEmpty() && MatterReviewOperations.allows(contract) &&
                    catalog.current(contract.operationId) == contract &&
                    MatterReviewPayload.decode(it.preparedOperation.payload)?.operation == contract.operationId
            }
        }.getOrNull()

    private fun coordinator(
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ) = ReviewedConversationApplyCoordinator(
        workspace = workspace,
        decode = PreparedPlanEnvelopeCodec::decode,
        retry = ApplyRetryReceiptGate(reality.applyAttemptIdentities, reality.executionReceiptLookup),
        preflight = ApplyPreflightGate(catalog, reality, bindings),
        bindings = ApplyBindingResolutionGate(bindings),
        executor = ApplyTransactionExecutor(reality, reality, authorization, finalGate),
        attempts = reality.applyAttemptLookup,
        receipts = reality.executionReceiptLookup,
        afterBeginApply = afterBegin,
    )

    private fun stableKey(chat: ConversationId, operation: OperationId, request: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf(chat.value, operation.value, request)) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    override fun close() {
        closed = true
        grants.close()
    }
}
