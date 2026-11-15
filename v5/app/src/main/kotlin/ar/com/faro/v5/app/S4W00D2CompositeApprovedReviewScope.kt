package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemAddPayload
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItemStatus
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.owner.reminders.api.ReminderCreatePayload
import ar.com.faro.v5.owner.reminders.api.ReminderOperations

/**
 * W00-D2: internal, canonical READ_ONLY Review scope discovery.
 * Each returned requirement is a structural claim, NEVER permission, grant,
 * lease, user-intent proof, Apply ticket, receipt or authority for COMMIT.
 * This reader is deliberately NOT wired to Planning8's single-owner host.
 */
internal class S4W00D2CompositeApprovedReviewScope(
    expectedGenerationId: String,
    private val pointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
    private val workspace: TrustedConversationApplyReviewPort,
    private val catalog: OperationContractLookupPort?,
) {
    private val expectedGeneration = StorageGenerationId(expectedGenerationId)

    fun read(context: ConversationPlanReviewContext): List<S4W00D1ScopeRequirement>? =
        runCatching {
            val pinned = pointer.read() ?: return@runCatching null
            if (pinned.generationId != expectedGeneration || !hostReady())
                return@runCatching null
            val first = workspace.load(context) ?: return@runCatching null
            val review = first.review
            val envelope = first.envelope()
            if (review.context() != context ||
                review.state != ConversationPlanReviewState.APPROVED ||
                review.planFingerprint != context.planFingerprint
            ) return@runCatching null
            val plan = PreparedPlanEnvelopeCodec.decode(envelope)
            if (plan.planFingerprint != review.planFingerprint)
                return@runCatching null
            val nodes = plan.applyPlan.nodesInTopologicalOrder()
            if (nodes.size != 2 || nodes.map { it.nodeId }.toSet().size != 2)
                return@runCatching null

            val scopes = ArrayList<S4W00D1ScopeRequirement>(2)
            for (node in nodes) {
                val prepared = node.preparedOperation
                val descriptor = prepared.contract
                if (descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
                    descriptor.exposure != ContractExposure.BINDABLE ||
                    catalog?.current(descriptor.operationId) != descriptor
                ) return@runCatching null
                val scope = when (descriptor.operationId) {
                    ReminderOperations.CREATE -> {
                        if (descriptor.capabilityId != ReminderOperations.CAPABILITY_ID ||
                            descriptor.canonicalExecutor != ReminderOperations.EXECUTOR_ID
                        ) return@runCatching null
                        val value = ReminderCreatePayload.decode(prepared.payload)
                            ?: return@runCatching null
                        if (!value.enabled || value.linkedMatterId != null)
                            return@runCatching null
                        S4W00D1ScopeRequirement(
                            node.nodeId, descriptor.operationId,
                            descriptor.capabilityId.value, value.id.value,
                        )
                    }
                    MonthlyPlanOperations.ITEM_ADD -> {
                        if (descriptor.capabilityId != MonthlyPlanOperations.CAPABILITY_ID ||
                            descriptor.canonicalExecutor != MonthlyPlanOperations.EXECUTOR_ID
                        ) return@runCatching null
                        val value = MonthlyPlanItemAddPayload.decode(prepared.payload)
                            ?: return@runCatching null
                        if (value.status != MonthlyPlanItemStatus.PLANNED ||
                            value.paymentConfirmedAtEpochMillis != null
                        ) return@runCatching null
                        S4W00D1ScopeRequirement(
                            node.nodeId, descriptor.operationId,
                            descriptor.capabilityId.value, value.id.value,
                        )
                    }
                    else -> return@runCatching null
                }
                if (scope.objectId.isBlank() || scope.objectId.length > 128)
                    return@runCatching null
                scopes += scope
            }
            if (scopes.map { it.operationId }.toSet() !=
                setOf(ReminderOperations.CREATE, MonthlyPlanOperations.ITEM_ADD) ||
                scopes.map { it.authorityId }.toSet().size != 2
            ) return@runCatching null

            // Check the source a second time. Neither a cached Review nor a
            // capability label is allowed to become a grant.
            if (pointer.read() != pinned || !hostReady())
                return@runCatching null
            for (node in nodes) {
                if (catalog?.current(node.preparedOperation.contract.operationId) !=
                    node.preparedOperation.contract
                ) return@runCatching null
            }
            val latest = workspace.load(context) ?: return@runCatching null
            if (latest.review != review ||
                latest.review.context() != context ||
                latest.review.state != ConversationPlanReviewState.APPROVED ||
                !latest.envelope().contentEquals(envelope) ||
                pointer.read() != pinned || !hostReady()
            ) return@runCatching null
            scopes.toList()
        }.getOrNull()
}
