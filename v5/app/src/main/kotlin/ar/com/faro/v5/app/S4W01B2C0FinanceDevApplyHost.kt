package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityStore

/**
 * W01-B2C0 DEV-only: isolated tests of canonical Finance Review -> Reality Apply.
 *
 * NOT composed by an Activity, a shipping V2 graph, Search, Chat or Surface.
 * The supplied policy/grant comes from the isolated test harness, NOT from
 * authenticated human intent. Consequently this class is NOT a production
 * Apply authority even when its SQLite transaction returns COMMITTED.
 *
 * Locks: MaterialWriteGate -> Finance grant owner -> Workspace CAS -> Reality SQL.
 * No Workspace or policyEpoch queries inside Reality.run. Cross-process policy
 * revocation through SQL COMMIT is NOT fenced and remains STOP for production.
 */
internal class S4W01B2C0FinanceDevApplyHost(
    private val generationId: String,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: TrustedConversationApplyReviewPort,
    private val reality: RealityStore,
    private val materialGate: MaterialWriteGate,
    private val catalog: OperationContractLookupPort,
    private val grants: S4W01B2FinanceObjectGrantOwner,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
) {
    init {
        require(generationId.isNotBlank() &&
            expectedHostEpoch.isNotBlank() &&
            pinned.generationId.value == generationId)
        require(pointer.read() == pinned) { "DEV generation pointer must be pinned" }
    }

    private fun current(): Boolean = runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch && pointer.read() == pinned
    }.getOrDefault(false)

    private val approved = S4W01B2FinanceApprovedScopeReader(
        generationId, pinned, pointer, workspace, catalog, expectedHostEpoch,
        hostEpochOrNull, ::current,
    )

    private fun scopeForNode(
        node: ApplyPlanNode, fingerprint: PlanFingerprint,
    ): S4W01B2FinanceApprovedScope? = runCatching {
        val operation = node.preparedOperation
        val descriptor = operation.contract
        if (node.dependsOn.isNotEmpty() ||
            catalog.current(descriptor.operationId) != descriptor ||
            descriptor.capabilityId != FinanceOperations.CAPABILITY_ID ||
            descriptor.canonicalExecutor != FinanceOperations.EXECUTOR_ID ||
            descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            descriptor.exposure != ContractExposure.BINDABLE
        ) return@runCatching null
        when (descriptor.operationId) {
            FinanceOperations.ACCOUNT_CREATE -> FinanceAccountCreatePayload
                .decode(operation.payload)?.let {
                    S4W01B2FinanceApprovedScope(
                        descriptor.operationId, it.accountId.value, it.accountId,
                        it.currency, fingerprint,
                    )
                }
            FinanceOperations.ENTRY_RECORD -> FinanceEntryRecordPayload
                .decode(operation.payload)?.let {
                    S4W01B2FinanceApprovedScope(
                        descriptor.operationId, it.entryId.value, it.accountId,
                        null, fingerprint,
                    )
                }
            else -> null
        }
    }.getOrNull()

    /**
     * Explicitly *TEST-ONLY* entry. This does not authenticate a human, mint
     * a principal or grant productive authorization. Never register it as a
     * ConversationApplyRequestPort.
     */
    fun executeOnlyWithSimulatedDevPolicy(
        context: ConversationPlanReviewContext,
    ): ConversationApplyHostOutcome = materialGate.withMaterialWrite {
        if (!current()) return@withMaterialWrite ConversationApplyHostOutcome.Denied("finance_dev_host_unavailable")

        val scope = approved.read(context) ?: return@withMaterialWrite ConversationApplyHostOutcome.Denied("finance_approved_scope_unavailable")
        val first = workspace.load(context) ?: return@withMaterialWrite ConversationApplyHostOutcome.Denied("finance_review_unavailable")
        if (first.review.context() != context ||
            first.review.state != ConversationPlanReviewState.APPROVED ||
            first.review.planFingerprint != scope.planFingerprint
        ) return@withMaterialWrite ConversationApplyHostOutcome.Denied("finance_review_not_approved")
        val sealedEnvelope = first.envelope()

        val nonce = grants.grantAfterTrustedPolicyCheck(context, scope)
            ?: return@withMaterialWrite ConversationApplyHostOutcome.Denied("finance_dev_grant_unavailable")
        val lease = grants.withCurrentGrantLease(scope, pinned, nonce) {
            var admittedIdentity: ApplyAttemptIdentity? = null
            val authorization = ApplyTransactionAuthorizationPort {
                    plan, identity, node, transactionScope ->
                runCatching {
                    val single = plan.applyPlan.nodesInTopologicalOrder().singleOrNull()
                    admittedIdentity == identity &&
                        transactionScope.transactionId == identity.transactionId &&
                        identity.planFingerprint == plan.planFingerprint &&
                        plan.planFingerprint == scope.planFingerprint &&
                        single?.nodeId == node.nodeId &&
                        scopeForNode(node, plan.planFingerprint) == scope &&
                        current()
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
                    operationRevalidators = listOf(
                        FinanceAccountCreateRevalidation(),
                        FinanceEntryRecordRevalidation(reality.financePersistence),
                    ),
                ),
                bindings = ApplyBindingResolutionGate(listOf(
                    FinanceAccountCreateBinding(reality.financePersistence),
                    FinanceEntryRecordBinding(reality.financePersistence),
                )),
                executor = ApplyTransactionExecutor(reality, reality, authorization),
                attempts = reality.applyAttemptLookup,
                receipts = reality.executionReceiptLookup,
                afterBeginApply = { applying, identity ->
                    // Workspace reads MUST happen before entering Reality.run.
                    val after = runCatching {
                        workspace.load(applying.context())
                    }.getOrNull()
                    val verified = after != null &&
                        after.review == applying &&
                        applying.state == ConversationPlanReviewState.APPLYING &&
                        applying.transactionId == identity.transactionId &&
                        applying.planFingerprint == identity.planFingerprint &&
                        context.expectedRevision < Long.MAX_VALUE &&
                        applying.context().expectedRevision == context.expectedRevision + 1L &&
                        after.envelope().contentEquals(sealedEnvelope) &&
                        grants.epochOrNull(scope, pinned) == nonce &&
                        current()
                    if (verified) admittedIdentity = identity
                    verified
                },
            )
            coordinator.apply(context).toHostOutcome()
        }
        when (lease) {
            is S4W01B2FinanceLeaseResult.Admitted -> lease.value
            S4W01B2FinanceLeaseResult.Denied ->
                ConversationApplyHostOutcome.Denied("finance_dev_scope_lease_unavailable")
        }
    }
}
