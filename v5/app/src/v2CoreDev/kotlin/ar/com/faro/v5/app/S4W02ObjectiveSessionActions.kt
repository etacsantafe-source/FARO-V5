package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal const val W02_OBJECTIVES_DEV_ID = "ar.com.faro.alpha.w02objectivesdev"

/** Borrowed owner actions on the same active graph as the selected DEV Chat. */
internal class S4W02ObjectiveSessionActions(
    pinned: ActiveGenerationPointer,
    pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val reality: RealityStore,
    materialGate: MaterialWriteGate,
    private val probe: FaroV5AndroidSandboxPrincipal,
    private val ownerReady: () -> Boolean,
    private val foreground: () -> Boolean,
    epoch: String,
    principal: String,
) : ConversationReviewPort, ConversationApplyRequestPort, AutoCloseable {
    private val closed = AtomicBoolean()
    private val referenceGrants = ConcurrentHashMap<ObjectiveLink, String>()
    private val referenceAuthority = ObjectiveReferenceAuthority(
        epochOrNull = { link -> if (admitted()) referenceGrants[link] else null },
        readLabel = { link, epochValue ->
            if (!admitted() || referenceGrants[link] != epochValue) null
            else when (link.owner) {
                ObjectiveLinkOwner.FINANCE -> reality.financePersistence.read().accounts
                    .firstOrNull { it.accountId.value == link.id }?.displayName?.value
                ObjectiveLinkOwner.MATTER -> reality.matterPersistence.read().matters
                    .firstOrNull { it.id.value == link.id }?.title?.value
                ObjectiveLinkOwner.OBJECTIVE -> null
            }
        },
    )
    private val host = S4W02ObjectiveReviewHost(
        pinned, pointer, workspace, reality, materialGate, BuiltInContractCatalog.create(),
        principal, epoch, { if (ownerCurrent()) epoch else null }, ::ownerCurrent, foreground,
        Planning8TrustedObjectPolicyPort { requester, scope, generation ->
            if (requester == principal && generation == pinned && scope.operationId in ObjectiveHistoryOperations.supported &&
                admitted()
            ) epoch else null
        },
        referenceAuthority,
    )

    private fun ownerCurrent(): Boolean =
        !closed.get() && ownerReady() && probe.currentEvidenceOrNull() != null

    private fun admitted(): Boolean = ownerCurrent() && foreground()

    fun snapshot(): ObjectiveSnapshot? = runCatching {
        if (!admitted()) null else reality.objectivePersistence.read().takeIf { admitted() }
    }.getOrNull()

    fun observeReference(link: ObjectiveLink): ObjectiveLinkView = referenceAuthority.observe(link)

    fun authorizeReference(link: ObjectiveLink): Boolean {
        if (link.owner == ObjectiveLinkOwner.OBJECTIVE || !admitted()) return false
        val epoch = UUID.randomUUID().toString()
        referenceGrants[link] = epoch
        if (referenceAuthority.authorized(link)?.first == epoch) return true
        referenceGrants.remove(link, epoch)
        return false
    }

    fun clearReferenceGrants() = referenceGrants.clear()

    private fun activeChat(): ConversationId? =
        if (admitted()) workspace.snapshot().workspace.activeConversationId else null

    fun submitEdit(operation: OperationId, intent: ObjectiveEditIntent): ConversationPlanReviewSnapshot? =
        activeChat()?.let { saved(host.submit(it, operation, intent, UUID.randomUUID().toString())) }

    fun submitHistory(intent: ObjectiveHistoryIntent): ConversationPlanReviewSnapshot? =
        activeChat()?.let { saved(host.submitHistory(it, intent, UUID.randomUUID().toString())) }

    fun submitPlan(intent: ObjectivePlanIntent): ConversationPlanReviewSnapshot? =
        activeChat()?.let { saved(host.submitPlan(it, intent, UUID.randomUUID().toString())) }

    private fun saved(result: WorkspacePlanReviewWriteResult?): ConversationPlanReviewSnapshot? =
        (result as? WorkspacePlanReviewWriteResult.Saved)?.review

    fun decideNow(review: ConversationPlanReviewSnapshot, approve: Boolean): ConversationPlanReviewSnapshot? =
        runBlocking {
            val intent = if (approve) ConversationPlanReviewIntent.Approve(review.context())
            else ConversationPlanReviewIntent.Discard(review.context())
            (decide(intent, IdempotencyKey("w02:decision:" + UUID.randomUUID())) as? ConversationReviewDecision.Saved)?.review
        }

    fun applyNow(review: ConversationPlanReviewSnapshot): ConversationApplyHostOutcome =
        runBlocking { request(review.context()) }

    override suspend fun reviews(conversationId: ConversationId): List<ConversationPlanReviewSnapshot> =
        withContext(Dispatchers.IO) {
            if (activeChat() != conversationId) return@withContext emptyList()
            workspace.reviews(conversationId).filter { it.reviewId.startsWith("w02:objective:") }
                .takeIf { activeChat() == conversationId }.orEmpty()
        }

    override suspend fun decide(
        intent: ConversationPlanReviewIntent,
        idempotencyKey: IdempotencyKey,
    ): ConversationReviewDecision = withContext(Dispatchers.IO) {
        val context = when (intent) {
            is ConversationPlanReviewIntent.Approve -> intent.context
            is ConversationPlanReviewIntent.Discard -> intent.context
            else -> return@withContext ConversationReviewDecision.Rejected
        }
        if (activeChat() != context.conversationId || !context.reviewId.startsWith("w02:objective:"))
            return@withContext ConversationReviewDecision.Rejected
        workspace.decide(intent, idempotencyKey)
    }

    override suspend fun request(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        withContext(Dispatchers.IO) {
            if (activeChat() != context.conversationId || !context.reviewId.startsWith("w02:objective:"))
                return@withContext ConversationApplyHostOutcome.Denied("objective_foreground_unavailable")
            when (workspace.load(context)?.review?.state) {
                ConversationPlanReviewState.APPROVED -> host.executeAfterExplicitConfirmation(context)
                ConversationPlanReviewState.APPLYING, ConversationPlanReviewState.RECOVERING_UNKNOWN ->
                    host.reconcileCommittedOnly(context)
                else -> ConversationApplyHostOutcome.Denied("objective_review_unavailable")
            }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        clearReferenceGrants()
        host.close()
        probe.close()
    }
}
