package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.finance.api.*

/**
 * W01-B2A dormant read-only, canonical APPROVED Finance scope.
 * No human authentication, owner grant, Apply, Reality transaction or receipt.
 * A scope is INFORMATION only; never hand it to a Surface as a capability.
 */
internal data class S4W01B2FinanceApprovedScope(
    val operationId: OperationId,
    val objectId: String,
    val accountId: FinanceAccountId,
    val accountCurrencyIfDeclared: FinanceCurrencyCode?,
    val planFingerprint: PlanFingerprint,
) {
    init { require(objectId.isNotBlank() && objectId.length <= 128) }
}

internal class S4W01B2FinanceApprovedScopeReader(
    expectedGenerationId: String,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: TrustedConversationApplyReviewPort,
    private val catalog: OperationContractLookupPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
) {
    private val expectedGeneration = StorageGenerationId(expectedGenerationId)

    init {
        require(expectedHostEpoch.isNotBlank())
        require(pinned.generationId == expectedGeneration)
    }

    fun read(context: ConversationPlanReviewContext): S4W01B2FinanceApprovedScope? =
        runCatching {
            if (pointer.read() != pinned ||
                !hostReady() || hostEpochOrNull() != expectedHostEpoch
            ) return@runCatching null

            val loaded = workspace.load(context) ?: return@runCatching null
            val review = loaded.review
            if (review.context() != context ||
                review.state != ConversationPlanReviewState.APPROVED
            ) return@runCatching null

            val envelope = loaded.envelope()
            val plan = PreparedPlanEnvelopeCodec.decode(envelope)
            if (plan.planFingerprint != review.planFingerprint) return@runCatching null
            val node = plan.applyPlan.nodesInTopologicalOrder().singleOrNull()
                ?: return@runCatching null
            if (node.dependsOn.isNotEmpty()) return@runCatching null
            val op = node.preparedOperation
            val descriptor = op.contract
            if (descriptor.capabilityId != FinanceOperations.CAPABILITY_ID ||
                descriptor.canonicalExecutor != FinanceOperations.EXECUTOR_ID ||
                descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
                descriptor.exposure != ContractExposure.BINDABLE ||
                catalog.current(descriptor.operationId) != descriptor
            ) return@runCatching null

            val scope = when (descriptor.operationId) {
                FinanceOperations.ACCOUNT_CREATE -> {
                    val account = FinanceAccountCreatePayload.decode(op.payload)
                        ?: return@runCatching null
                    S4W01B2FinanceApprovedScope(
                        descriptor.operationId, account.accountId.value,
                        account.accountId, account.currency, plan.planFingerprint,
                    )
                }
                FinanceOperations.ENTRY_RECORD -> {
                    val entry = FinanceEntryRecordPayload.decode(op.payload)
                        ?: return@runCatching null
                    S4W01B2FinanceApprovedScope(
                        descriptor.operationId, entry.entryId.value,
                        entry.accountId, null, plan.planFingerprint,
                    )
                }
                else -> return@runCatching null
            }

            // This is a READ-ONLY double-check, not a commit-time ACL fence.
            if (pointer.read() != pinned || !hostReady() ||
                hostEpochOrNull() != expectedHostEpoch ||
                catalog.current(descriptor.operationId) != descriptor
            ) return@runCatching null
            val latest = workspace.load(context) ?: return@runCatching null
            if (latest.review != review ||
                latest.review.state != ConversationPlanReviewState.APPROVED ||
                !latest.envelope().contentEquals(envelope) ||
                pointer.read() != pinned || !hostReady() ||
                hostEpochOrNull() != expectedHostEpoch ||
                catalog.current(descriptor.operationId) != descriptor
            ) null else scope
        }.getOrNull()
}
