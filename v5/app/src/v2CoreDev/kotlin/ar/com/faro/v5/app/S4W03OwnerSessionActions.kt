package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterReviewIntent
import ar.com.faro.v5.owner.matters.api.MatterReviewOperations
import ar.com.faro.v5.owner.matters.api.MatterSnapshot
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshot
import ar.com.faro.v5.owner.reminders.api.S4W03ReminderIntent
import ar.com.faro.v5.owner.reminders.api.S4W03ReminderOperations
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal const val W03_MATTERS_DEV_ID = "ar.com.faro.alpha.w03mattersdev"

/** Borrowed owners for one explicit local Android DEV session. */
internal class S4W03OwnerSessionActions(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val reality: RealityStore,
    auxiliary: AuxiliaryDeliveryStore,
    private val materialGate: MaterialWriteGate,
    private val principal: String,
    private val epoch: String,
    private val ownerReady: () -> Boolean,
    private val foreground: () -> Boolean,
    private val onClose: () -> Unit = {},
) : ConversationReviewPort, ConversationApplyRequestPort, AutoCloseable {
    private enum class Owner { MATTER, REMINDER }

    private val closed = AtomicBoolean(false)
    private val matterGrant = AtomicReference<String?>(null)
    private val reminderGrant = AtomicReference<String?>(null)
    private val projection = AtomicReference<String?>(null)
    private val matterLinks = ConcurrentHashMap<String, String>()
    private val catalog = BuiltInContractCatalog.create()
    private val matterRead = OperationId("matter.read")
    private val reminderRead = OperationId("reminder.read")
    private val noticeRoute = OperationId("notice.route")
    private val policy = Planning8TrustedObjectPolicyPort { requester, scope, generation ->
        if (requester == principal && generation == pinned && admitted()) policyEpoch(scope)
        else null
    }
    private val currentEpoch = { epoch.takeIf { admitted() } }
    private val matterHost = S4W03MatterReviewHost(
        pinned, pointer, workspace, reality, materialGate, catalog, principal, epoch,
        currentEpoch, ::current, foreground, policy,
    )
    private val reminderHost = S4W03ReminderReviewHost(
        pinned, pointer, workspace, reality, materialGate, catalog, principal, epoch,
        currentEpoch, ::current, foreground, policy,
    )
    private val reconcileHost = S4W03ReminderNoticeReconcileHost(
        pinned, pointer, reality, auxiliary, materialGate, principal, epoch,
        currentEpoch, ::current, foreground, policy,
    )

    private fun current() = !closed.get() && ownerReady() && pointer.read() == pinned
    private fun admitted() = runCatching { current() && foreground() }.getOrDefault(false)

    private fun policyEpoch(scope: Planning8ObjectScope): String? = when (scope.operationId) {
        matterRead -> matterLinks[scope.objectId].takeIf { matterGrant.get() != null }
        reminderRead -> projection.get().takeIf { scope.objectId == "reminders" && reminderGrant.get() != null }
        noticeRoute -> projection.get().takeIf { scope.objectId == "reminder-local" && reminderGrant.get() != null }
        in MatterReviewOperations.supported -> matterGrant.get()
        in S4W03ReminderOperations.supported -> reminderGrant.get()
        else -> null
    }

    private fun grant(store: AtomicReference<String?>): Boolean {
        if (!admitted()) return false
        val token = UUID.randomUUID().toString()
        store.set(token)
        return if (admitted()) true else {
            store.compareAndSet(token, null)
            false
        }
    }

    fun grantMatterReadAfterExplicitRequest(): Boolean {
        matterLinks.clear()
        return grant(matterGrant)
    }

    fun grantReminderReadAfterExplicitRequest(): Boolean = grant(reminderGrant)

    fun clearViewGrants() {
        matterGrant.set(null)
        reminderGrant.set(null)
        projection.set(null)
        matterLinks.clear()
    }

    private fun <T> read(grant: AtomicReference<String?>, block: () -> T): T? = runCatching {
        materialGate.withMaterialWrite {
            val before = grant.get()
            if (before == null || !admitted()) return@withMaterialWrite null
            block().takeIf { admitted() && grant.get() == before }
        }
    }.getOrNull()

    fun matterSnapshot(): MatterSnapshot? = read(matterGrant) { reality.matterPersistence.read() }

    fun reminderSnapshot(): ReminderSnapshot? = read(reminderGrant) {
        reality.planningDueReadOwnersForTrustedHost().reminders.read()
    }

    fun authorizeMatterReference(id: MatterId): Boolean {
        val token = matterGrant.get() ?: return false
        if (matterSnapshot()?.matters?.none { it.id == id } != false) return false
        val grant = UUID.randomUUID().toString()
        matterLinks[id.value] = grant
        return if (admitted() && matterGrant.get() == token) true else {
            matterLinks.remove(id.value, grant)
            false
        }
    }

    fun reconcileLocalNoticesAfterExplicitRequest(
        fromEpochMillis: Long, untilEpochMillis: Long,
    ): S4W03NoticeReconcileResult {
        if (!admitted() || reminderGrant.get() == null) return S4W03NoticeReconcileResult.Denied
        val batch = UUID.randomUUID().toString()
        if (!projection.compareAndSet(null, batch)) return S4W03NoticeReconcileResult.Denied
        return try {
            reconcileHost.reconcile(fromEpochMillis, untilEpochMillis)
        } finally {
            projection.compareAndSet(batch, null)
        }
    }

    private fun activeChat(): ConversationId? =
        if (admitted()) workspace.snapshot().workspace.activeConversationId else null

    fun submitMatter(intent: MatterReviewIntent, key: String): ConversationPlanReviewSnapshot? =
        activeChat()?.takeIf { allowed(Owner.MATTER) }?.let { saved(matterHost.submit(it, intent, key)) }

    fun submitReminder(intent: S4W03ReminderIntent, key: String): ConversationPlanReviewSnapshot? =
        activeChat()?.takeIf { allowed(Owner.REMINDER) }?.let { saved(reminderHost.submit(it, intent, key)) }

    private fun saved(result: WorkspacePlanReviewWriteResult?) =
        (result as? WorkspacePlanReviewWriteResult.Saved)?.review

    private fun owner(reviewId: String): Owner? = when {
        reviewId.startsWith("w03:matter:") -> Owner.MATTER
        reviewId.startsWith("w03:reminder:") -> Owner.REMINDER
        else -> null
    }

    private fun allowed(owner: Owner): Boolean = admitted() && when (owner) {
        Owner.MATTER -> matterGrant.get() != null
        Owner.REMINDER -> reminderGrant.get() != null
    }

    override suspend fun reviews(conversationId: ConversationId): List<ConversationPlanReviewSnapshot> =
        withContext(Dispatchers.IO) {
            if (activeChat() != conversationId) return@withContext emptyList()
            workspace.reviews(conversationId).filter { owner(it.reviewId)?.let(::allowed) == true }
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
        if (activeChat() != context.conversationId || owner(context.reviewId)?.let(::allowed) != true)
            return@withContext ConversationReviewDecision.Rejected
        workspace.decide(intent, idempotencyKey)
    }

    override suspend fun request(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        withContext(Dispatchers.IO) {
            val denied = ConversationApplyHostOutcome.Denied("w03_owner_unavailable")
            val owner = owner(context.reviewId) ?: return@withContext denied
            if (activeChat() != context.conversationId || !allowed(owner)) return@withContext denied
            val state = workspace.load(context)?.review?.state ?: return@withContext denied
            when (state) {
                ConversationPlanReviewState.APPROVED -> when (owner) {
                    Owner.MATTER -> matterHost.executeAfterExplicitConfirmation(context)
                    Owner.REMINDER -> reminderHost.executeAfterExplicitConfirmation(context)
                }
                ConversationPlanReviewState.APPLYING, ConversationPlanReviewState.RECOVERING_UNKNOWN -> when (owner) {
                    Owner.MATTER -> matterHost.reconcileCommittedOnly(context)
                    Owner.REMINDER -> reminderHost.reconcileCommittedOnly(context)
                }
                else -> denied
            }
        }

    fun decideNow(review: ConversationPlanReviewSnapshot, approve: Boolean): ConversationPlanReviewSnapshot? =
        runBlocking {
            val request = if (approve) ConversationPlanReviewIntent.Approve(review.context())
            else ConversationPlanReviewIntent.Discard(review.context())
            (decide(request, IdempotencyKey("w03:decision:" + UUID.randomUUID())) as? ConversationReviewDecision.Saved)?.review
        }

    fun applyNow(review: ConversationPlanReviewSnapshot): ConversationApplyHostOutcome =
        runBlocking { request(review.context()) }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        clearViewGrants()
        matterHost.close()
        reminderHost.close()
        reconcileHost.close()
        onClose()
    }
}
