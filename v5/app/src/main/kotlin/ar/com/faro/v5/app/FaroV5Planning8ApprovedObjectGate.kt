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
import java.nio.ByteBuffer
import java.security.MessageDigest

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
/**
 * N033C3 narrow Undo scope: source operation + source transaction + target
 * object, length-framed before hashing to avoid ambiguous delimiters.
 * This is ONLY a scope identifier, never proof of source or ACL.
 */
internal fun planning8UndoScope(intent: UndoExecuteIntent): Planning8ObjectScope? =
    runCatching {
        val matchingOriginal = when (intent.sourceOperationId) {
            ReminderOperations.CREATE -> ReminderCreatePayload.decode(
                intent.originalCreatePayload,
            )?.let { it.id.value == intent.targetObjectId &&
                it.enabled && it.linkedMatterId == null } == true
            MonthlyPlanOperations.ITEM_ADD -> MonthlyPlanItemAddPayload.decode(
                intent.originalCreatePayload,
            )?.let { it.id.value == intent.targetObjectId &&
                it.status == MonthlyPlanItemStatus.PLANNED &&
                it.paymentConfirmedAtEpochMillis == null } == true
            else -> false
        }
        if (!matchingOriginal) return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            "n033c3-undo-object-v1", intent.sourceOperationId.value,
            intent.sourceReceipt.transactionId.value, intent.targetObjectId,
        ).forEach { segment ->
            val bytes = segment.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        Planning8ObjectScope(OperationId("undo.execute"), "undo-" + hex)
    }.getOrNull()

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
        currentApprovedObjectScopeOrNull(context) != null

    /** The SAME verified canonical decision, with target scope preserved.
     * An execution lease must never re-label Review(A) as authority for B.
     * Still READ_ONLY; nullable means deny.
     */
    /**
     * Request preadmission ONLY: returns the canonical approved object without
     * mistaking it for a grant. This is never the Apply authorization check.
     * The normal guard still requires a separate trusted object owner/nonce.
     */
    internal fun currentApprovedScopeForTrustedRequestOrNull(
        context: ConversationPlanReviewContext,
    ): Planning8ObjectScope? = approvedScope(context, requireGrant = false)

    internal fun currentApprovedObjectScopeOrNull(
        context: ConversationPlanReviewContext,
    ): Planning8ObjectScope? = approvedScope(context, requireGrant = true)

    /**
     * Only a trusted host after a real Workspace BeginApply may call this.
     * It verifies the same canonical operation/object and *exact* Reality
     * attempt identity in APPLYING, without granting any right to execute.
     * Never call it inside a Reality transaction (Workspace mutex order).
     */
    internal fun currentApplyingScopeForTrustedTransitionOrNull(
        context: ConversationPlanReviewContext,
        identity: ApplyAttemptIdentity,
    ): Planning8ObjectScope? = approvedScope(
        context, requireGrant = false, applyingIdentity = identity,
    )

    private fun approvedScope(
        context: ConversationPlanReviewContext,
        requireGrant: Boolean,
        applyingIdentity: ApplyAttemptIdentity? = null,
    ): Planning8ObjectScope? = runCatching {
            val pinned = pointer.read() ?: return@runCatching null
            if (pinned.generationId != generation || !hostReady()) return@runCatching null
            val approved = workspace.load(context) ?: return@runCatching null
            val review = approved.review
            val requiredState = if (applyingIdentity == null)
                ConversationPlanReviewState.APPROVED
            else ConversationPlanReviewState.APPLYING
            if (review.context() != context || review.state != requiredState ||
                (applyingIdentity != null &&
                    (review.transactionId != applyingIdentity.transactionId ||
                        review.planFingerprint != applyingIdentity.planFingerprint))
            ) return@runCatching null
            val plan = PreparedPlanEnvelopeCodec.decode(approved.envelope())
            if (plan.planFingerprint != review.planFingerprint) return@runCatching null
            val nodes = plan.applyPlan.nodesInTopologicalOrder()
            if (nodes.size != 1 || nodes.single().dependsOn.isNotEmpty())
                return@runCatching null
            val prepared = nodes.single().preparedOperation
            val contract = prepared.contract
            // A full descriptor lookup is mandatory; ID/capability alone is not authority.
            val official = catalog?.current(contract.operationId)
                ?: return@runCatching null
            if (official != contract ||
                contract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
                contract.exposure != ContractExposure.BINDABLE
            ) return@runCatching null
            val scope = when (contract.operationId) {
                ReminderOperations.CREATE -> {
                    if (contract.capabilityId != ReminderOperations.CAPABILITY_ID ||
                        contract.canonicalExecutor != ReminderOperations.EXECUTOR_ID
                    ) return@runCatching null
                    val rule = ReminderCreatePayload.decode(prepared.payload)
                        ?: return@runCatching null
                    if (rule.linkedMatterId != null) return@runCatching null
                    Planning8ObjectScope(contract.operationId, rule.id.value)
                }
                MonthlyPlanOperations.ITEM_ADD -> {
                    if (contract.capabilityId != MonthlyPlanOperations.CAPABILITY_ID ||
                        contract.canonicalExecutor != MonthlyPlanOperations.EXECUTOR_ID
                    ) return@runCatching null
                    val item = MonthlyPlanItemAddPayload.decode(prepared.payload)
                        ?: return@runCatching null
                    if (item.status != MonthlyPlanItemStatus.PLANNED ||
                        item.paymentConfirmedAtEpochMillis != null
                    ) return@runCatching null
                    Planning8ObjectScope(contract.operationId, item.id.value)
                }
                OperationId("undo.execute") -> {
                    if (contract.capabilityId != CapabilityId("N-016") ||
                        contract.canonicalExecutor != ExecutorId("undo-coordinator")
                    ) return@runCatching null
                    val intent = UndoExecutePayload.decode(prepared.payload)
                        ?: return@runCatching null
                    planning8UndoScope(intent) ?: return@runCatching null
                }
                else -> return@runCatching null
            }
            if (requireGrant) {
                val owner = acl ?: return@runCatching null
                if (pointer.read() != pinned || !hostReady()) return@runCatching null
                val epoch = owner.epochOrNull(scope, pinned)?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                // The existing Apply-side guard MUST still recheck the owner.
                if (pointer.read() != pinned || !hostReady() ||
                    catalog.current(contract.operationId) != contract ||
                    owner.epochOrNull(scope, pinned) != epoch
                ) return@runCatching null
            } else {
                // Grant issuance cannot read its own not-yet-created grant.
                // This path verifies only HUMAN Review and canonical scope.
                if (pointer.read() != pinned || !hostReady() ||
                    catalog.current(contract.operationId) != contract
                ) return@runCatching null
            }
            // A human can DISCARD before BeginApply: always re-read Workspace.
            val latest = workspace.load(context) ?: return@runCatching null
            if (latest.review == review &&
                latest.review.state == requiredState &&
                latest.envelope().contentEquals(approved.envelope()) &&
                pointer.read() == pinned && hostReady() &&
                catalog.current(contract.operationId) == contract
            ) scope else null
        }.getOrNull()
}
