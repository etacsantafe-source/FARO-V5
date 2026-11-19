package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Single-session Objective Review/Apply seam for the isolated DEV host. */
internal class S4W02ObjectiveReviewHost(
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
    private val bindings = ObjectiveReviewOperations.editable.map {
        ObjectiveReviewedEditBinding(it, reality.objectivePersistence, catalog)
    }

    private fun current(): Boolean = runCatching {
        !closed && ownerReady() && trustedForeground() && pointer.read() == pinned && currentEpoch() == hostEpoch
    }.getOrDefault(false)

    private fun selected(chat: ConversationId): Boolean =
        current() && workspace.snapshot().workspace.activeConversationId == chat

    fun submit(
        chat: ConversationId,
        operation: OperationId,
        intent: ObjectiveEditIntent,
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
            val contract = catalog.current(operation)?.takeIf(ObjectiveReviewOperations::allows)
                ?: return@withMaterialWrite null
            val key = stableKey(chat, operation, localRequestId)
            val prepared = PreparedOperation(
                contract, ObjectiveEditPayload.encode(intent), DependencyStampSet.empty(), IdempotencyKey("w02:edit:" + key),
            )
            val validator = bindings.single { it.operationId == operation }
            if (validator.revalidate(prepared) != OperationRevalidationResult.Valid) return@withMaterialWrite null
            val plan = PreparedPlan.of(ApplyPlanNode(PlanNodeId("objective"), prepared, emptySet()))
            val form = intent.form
            val action = if (operation == ObjectiveReviewOperations.CREATE) "Crear" else "Editar"
            val summary = buildString {
                append("$action objetivo «${form.title.value}». Prioridad ${form.priority.name}.")
                form.dueDate?.let { append(" Fecha $it.") }
                form.target?.let {
                    val unit = if (it.unit.kind == ObjectiveMetricUnit.Kind.MONETARY)
                        "unidades menores (${it.unit.label})" else it.unit.label
                    append(" Meta: ${it.value} $unit.")
                }
                if (form.detail.value.isNotEmpty()) {
                    append(" Finalidad: ")
                    append(form.detail.value.take(3000))
                    if (form.detail.value.length > 3000) append("…")
                }
            }
            val reviewId = "w02:objective:" + key
            val existing = runBlocking { workspace.reviews(chat) }.firstOrNull { it.reviewId == reviewId }
            if (!selected(chat) || workspace.snapshot().revision != before.revision) return@withMaterialWrite null
            if (existing != null) {
                return@withMaterialWrite existing.takeIf {
                    it.planFingerprint == plan.planFingerprint && it.state == ConversationPlanReviewState.PENDING
                }?.let { WorkspacePlanReviewWriteResult.Saved(it, true) }
            }
            workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint,
                summary, PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("w02:review:" + key),
            ))
        }
    }.getOrNull()

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        gate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("objective_review_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val approved = workspace.load(context) ?: return@withMaterialWrite denied
            if (approved.review.state != ConversationPlanReviewState.APPROVED) return@withMaterialWrite denied
            val envelope = approved.envelope()
            val node = objectiveNode(context, envelope) ?: return@withMaterialWrite denied
            val intent = ObjectiveEditPayload.decode(node.preparedOperation.payload) ?: return@withMaterialWrite denied
            val scope = Planning8ObjectScope(node.preparedOperation.contract.operationId, intent.form.id.value)
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
            val denied = ConversationApplyHostOutcome.Denied("objective_receipt_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val snapshot = workspace.load(context) ?: return@withMaterialWrite denied
            if (objectiveNode(context, snapshot.envelope()) == null) return@withMaterialWrite denied
            coordinator().reconcileCommittedOnly(context).toHostOutcome()
        }

    private fun objectiveNode(context: ConversationPlanReviewContext, envelope: ByteArray): ApplyPlanNode? =
        runCatching {
            if (!context.reviewId.startsWith("w02:objective:")) return@runCatching null
            val decoded = PreparedPlanEnvelopeCodec.decode(envelope)
            if (decoded.planFingerprint != context.planFingerprint) return@runCatching null
            decoded.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf {
                val contract = it.preparedOperation.contract
                it.dependsOn.isEmpty() && ObjectiveReviewOperations.allows(contract) &&
                    catalog.current(contract.operationId) == contract
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
