package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/**
 * Isolated W03-D1 Reminder DEV Review/Apply host; NOT an Android production ACL.
 * Linked Matter requires a distinct, freshly leased trusted read policy scope.
 * No Notice or OS delivery is performed by this host.
 */
internal class S4W03ReminderReviewHost(
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
    private val bindings = reality.reminderMutationBindingsForTrustedHost(catalog) { id ->
        grants.epochOrNull(matterScope(id), pinned) != null
    }

    private fun matterScope(id: String) = Planning8ObjectScope(
        OperationId("matter.read"), id,
    )

    private fun current(): Boolean = runCatching {
        !closed && ownerReady() && trustedForeground() &&
            pointer.read() == pinned && currentEpoch() == hostEpoch
    }.getOrDefault(false)

    private fun selected(chat: ConversationId): Boolean =
        current() && workspace.snapshot().workspace.activeConversationId == chat

    fun submit(
        chat: ConversationId,
        intent: S4W03ReminderIntent,
        localRequestId: String,
    ): WorkspacePlanReviewWriteResult? = runCatching {
        gate.withMaterialWrite {
            if (!selected(chat) || localRequestId.length !in 1..256 ||
                localRequestId.isBlank()
            ) return@withMaterialWrite null
            val before = workspace.snapshot()
            val timeline = runBlocking { workspace.readTimeline(chat) }
                ?: return@withMaterialWrite null
            if (timeline.revision != before.revision || timeline.authorityToken.isBlank())
                return@withMaterialWrite null
            val contract = catalog.current(intent.operation)
                ?.takeIf(S4W03ReminderOperations::allows)
                ?: return@withMaterialWrite null
            val key = stableKey(chat, intent.operation, localRequestId)
            val prepared = PreparedOperation(
                contract, S4W03ReminderMutationPayload.encode(intent),
                DependencyStampSet.empty(), IdempotencyKey("w03:reminder:" + key),
            )
            val validator = bindings.single { it.operationId == intent.operation }
            if (validator.revalidate(prepared) != OperationRevalidationResult.Valid)
                return@withMaterialWrite null
            val plan = PreparedPlan.of(
                ApplyPlanNode(PlanNodeId("reminder"), prepared, emptySet()),
            )
            val reviewId = "w03:reminder:" + key
            val existing = runBlocking { workspace.reviews(chat) }
                .firstOrNull { it.reviewId == reviewId }
            if (!selected(chat) || workspace.snapshot().revision != before.revision)
                return@withMaterialWrite null
            if (existing != null) return@withMaterialWrite existing.takeIf {
                it.planFingerprint == plan.planFingerprint &&
                    it.state == ConversationPlanReviewState.PENDING
            }?.let { WorkspacePlanReviewWriteResult.Saved(it, true) }
            workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                chat, reviewId, timeline.authorityToken, before.revision, plan.planFingerprint,
                "Recordatorio " + intent.ruleId.value + ": " + intent.operation.value,
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("w03:reminder-review:" + key),
            ))
        }
    }.getOrNull()

    fun executeAfterExplicitConfirmation(
        context: ConversationPlanReviewContext,
    ): ConversationApplyHostOutcome = gate.withMaterialWrite {
        val denied = ConversationApplyHostOutcome.Denied("reminder_review_unavailable")
        if (!selected(context.conversationId)) return@withMaterialWrite denied
        val approved = workspace.load(context) ?: return@withMaterialWrite denied
        if (approved.review.state != ConversationPlanReviewState.APPROVED)
            return@withMaterialWrite denied
        val envelope = approved.envelope()
        val node = reminderNode(context, envelope) ?: return@withMaterialWrite denied
        val intent = S4W03ReminderMutationPayload.decode(node.preparedOperation.payload)
            ?: return@withMaterialWrite denied
        val snapshot = runCatching { reality.planningDueReadOwnersForTrustedHost().reminders.read() }.getOrNull()
            ?: return@withMaterialWrite denied
        if (!intent.validOn(snapshot)) return@withMaterialWrite denied

        val scope = Planning8ObjectScope(intent.operation, intent.ruleId.value)
        val linked = intent.linkedMatterIds(snapshot).map(::matterScope)
        val nonce = grants.grantAfterTrustedPolicyCheck(scope) ?: return@withMaterialWrite denied
        try {
            val linkedNonces = linked.map { related ->
                val found = grants.grantAfterTrustedPolicyCheck(related)
                    ?: return@withMaterialWrite denied
                related to found
            }
            val admitted = grants.withCurrentGrantLease(scope, pinned, nonce) {
                var attempt: ApplyAttemptIdentity? = null
                val linksCurrent = {
                    linkedNonces.all { (related, token) ->
                        grants.epochOrNull(related, pinned) == token
                    }
                }
                val authorize = ApplyTransactionAuthorizationPort { plan, identity, actual, tx ->
                    current() && attempt == identity &&
                        tx.transactionId == identity.transactionId &&
                        plan.planFingerprint == context.planFingerprint &&
                        actual == node && grants.epochOrNull(scope, pinned) == nonce &&
                        linksCurrent()
                }
                val finalGate = ApplyTransactionFinalCommitGate { plan, identity, _, checked, tx ->
                    checked.singleOrNull() == node &&
                        authorize.isAuthorized(plan, identity, node, tx)
                }
                coordinator(authorize, finalGate) { applying, identity ->
                    val start = workspace.load(applying.context())
                    val valid = start != null && start.review == applying &&
                        applying.state == ConversationPlanReviewState.APPLYING &&
                        applying.transactionId == identity.transactionId &&
                        applying.planFingerprint == context.planFingerprint &&
                        applying.conversationId == context.conversationId &&
                        applying.reviewId == context.reviewId &&
                        start.envelope().contentEquals(envelope) &&
                        selected(context.conversationId) &&
                        grants.epochOrNull(scope, pinned) == nonce &&
                        linksCurrent()
                    if (valid) attempt = identity
                    valid
                }.apply(context).toHostOutcome()
            }
            when (admitted) {
                is Planning8GrantLeaseResult.Admitted -> admitted.value
                Planning8GrantLeaseResult.Denied -> denied
            }
        } finally {
            grants.revoke(scope)
            linked.forEach(grants::revoke)
        }
    }

    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        gate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("reminder_receipt_unavailable")
            if (!selected(context.conversationId)) return@withMaterialWrite denied
            val loaded = workspace.load(context) ?: return@withMaterialWrite denied
            if (reminderNode(context, loaded.envelope()) == null) return@withMaterialWrite denied
            coordinator().reconcileCommittedOnly(context).toHostOutcome()
        }

    private fun reminderNode(
        context: ConversationPlanReviewContext,
        envelope: ByteArray,
    ): ApplyPlanNode? = runCatching {
        if (!context.reviewId.startsWith("w03:reminder:")) return@runCatching null
        val decoded = PreparedPlanEnvelopeCodec.decode(envelope)
        if (decoded.planFingerprint != context.planFingerprint) return@runCatching null
        decoded.applyPlan.nodesInTopologicalOrder().singleOrNull()?.takeIf {
            val contract = it.preparedOperation.contract
            it.dependsOn.isEmpty() && S4W03ReminderOperations.allows(contract) &&
                catalog.current(contract.operationId) == contract &&
                S4W03ReminderMutationPayload.decode(it.preparedOperation.payload)
                    ?.operation == contract.operationId
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

    private fun stableKey(chat: ConversationId, operation: OperationId, key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (part in listOf(chat.value, operation.value, key)) {
            val b = part.toByteArray(Charsets.UTF_8)
            md.update(ByteBuffer.allocate(4).putInt(b.size).array())
            md.update(b)
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    override fun close() {
        closed = true
        grants.close()
    }
}
