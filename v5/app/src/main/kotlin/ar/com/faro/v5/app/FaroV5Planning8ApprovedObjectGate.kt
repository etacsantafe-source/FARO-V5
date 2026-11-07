package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemAddPayload
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemStatus
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.owner.reminders.api.ReminderCreatePayload
import ar.com.faro.v5.owner.reminders.api.ReminderOperations

/** N033C2B4: the exact operation/object, never a host-wide or Search-only grant. */
internal data class Planning8ObjectScope(
    val operationId: OperationId,
    val objectId: String,
) {
    init { require(objectId.isNotBlank() && objectId.length <= 128) }
}

/**
 * Future trusted owner boundary. Null means DENY, not unrestricted access.
 * A real host implementation MUST issue unique revocable epochs per scope,
 * check user/owner policy at each call, and never reuse an epoch after revoke.
 * This interface DOES NOT grant or persist permission.
 */
internal fun interface Planning8ObjectAclReadPort {
    fun epochOrNull(scope: Planning8ObjectScope, pointer: ActiveGenerationPointer): String?
}

/**
 * A READ-ONLY, one-shot check of the canonical Workspace human Review and
 * object scope. Never an Apply capability, writable lease, or approval token.
 * Production host has NO Planning8 ACL provider yet: default is fail closed.
 * Actual Apply must independently enforce permission under the write gate,
 * immediately before AND inside the Reality transaction in a future unit.
 */
internal class FaroV5Planning8ApprovedObjectGate(
    expectedGenerationId: String,
    private val pointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
    private val workspace: TrustedConversationApplyReviewPort,
    private val catalog: OperationContractLookupPort?,
    private val acl: Planning8ObjectAclReadPort? = null,
) {
    private val generation = StorageGenerationId(expectedGenerationId)

    fun hasCurrentApprovedObjectReview(context: ConversationPlanReviewContext): Boolean =
        runCatching {
            val pinned = pointer.read() ?: return@runCatching false
            if (pinned.generationId != generation || !hostReady()) return@runCatching false
            val approved = workspace.load(context) ?: return@runCatching false
            val review = approved.review
            if (review.context() != context ||
                review.state != ConversationPlanReviewState.APPROVED
            ) return@runCatching false
            val plan = PreparedPlanEnvelopeCodec.decode(approved.envelope())
            if (plan.planFingerprint != review.planFingerprint) return@runCatching false
            val nodes = plan.applyPlan.nodesInTopologicalOrder()
            if (nodes.size != 1 || nodes.single().dependsOn.isNotEmpty())
                return@runCatching false
            val prepared = nodes.single().preparedOperation
            val contract = prepared.contract
            // A full descriptor lookup is mandatory; ID/capability alone is not authority.
            val official = catalog?.current(contract.operationId)
                ?: return@runCatching false
            if (official != contract ||
                contract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
                contract.exposure != ContractExposure.BINDABLE
            ) return@runCatching false
            val scope = when (contract.operationId) {
                ReminderOperations.CREATE -> {
                    if (contract.capabilityId != ReminderOperations.CAPABILITY_ID ||
                        contract.canonicalExecutor != ReminderOperations.EXECUTOR_ID
                    ) return@runCatching false
                    val rule = ReminderCreatePayload.decode(prepared.payload)
                        ?: return@runCatching false
                    if (rule.linkedMatterId != null) return@runCatching false
                    Planning8ObjectScope(contract.operationId, rule.id.value)
                }
                MonthlyPlanOperations.ITEM_ADD -> {
                    if (contract.capabilityId != MonthlyPlanOperations.CAPABILITY_ID ||
                        contract.canonicalExecutor != MonthlyPlanOperations.EXECUTOR_ID
                    ) return@runCatching false
                    val item = MonthlyPlanItemAddPayload.decode(prepared.payload)
                        ?: return@runCatching false
                    if (item.status != MonthlyPlanItemStatus.PLANNED ||
                        item.paymentConfirmedAtEpochMillis != null
                    ) return@runCatching false
                    Planning8ObjectScope(contract.operationId, item.id.value)
                }
                else -> return@runCatching false
            }
            val owner = acl ?: return@runCatching false
            if (pointer.read() != pinned || !hostReady()) return@runCatching false
            val epoch = owner.epochOrNull(scope, pinned)?.takeIf { it.isNotBlank() }
                ?: return@runCatching false
            // Check ACL again, then re-read the canonical human decision:
            // a real user can DISCARD an approved Review while ACL is read.
            if (pointer.read() != pinned || !hostReady() ||
                catalog.current(contract.operationId) != contract ||
                owner.epochOrNull(scope, pinned) != epoch
            ) return@runCatching false
            val latest = workspace.load(context) ?: return@runCatching false
            latest.review == review &&
                latest.review.state == ConversationPlanReviewState.APPROVED &&
                latest.envelope().contentEquals(approved.envelope()) &&
                pointer.read() == pinned && hostReady() &&
                catalog.current(contract.operationId) == contract
        }.getOrDefault(false)
}
