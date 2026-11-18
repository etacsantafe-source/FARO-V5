package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityFinance9ActiveDevGrant
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.security.MessageDigest

/** F02/F03 Apply for the isolated active Finance9 session. */
internal class S4W01B2D1ActiveFinanceApplyHost(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val reality: RealityStore,
    private val materialGate: MaterialWriteGate,
    private val catalog: OperationContractLookupPort,
    principalHash: String,
    private val hostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val ownerReady: () -> Boolean,
    private val trustedForegroundRequest: () -> Boolean,
) {
    private val grants = reality.finance9ActiveDevGrantIssuer(
        principalHash, pinned.generationId.value, pinned.sequence, ::current,
    )

    private val approved = S4W01B2FinanceApprovedScopeReader(
        pinned.generationId.value, pinned, pointer, workspace, catalog, hostEpoch,
        hostEpochOrNull, ::current,
    )

    private fun current(): Boolean = runCatching {
        ownerReady() && trustedForegroundRequest() &&
            hostEpochOrNull() == hostEpoch && pointer.read() == pinned
    }.getOrDefault(false)

    /** Check Workspace only before entering Reality SQL. */
    private fun isSelectedReview(context: ConversationPlanReviewContext): Boolean =
        context.reviewId.startsWith("w01:finance:") &&
            current() && workspace.snapshot().workspace.activeConversationId == context.conversationId

    private fun financeNode(envelope: ByteArray, fingerprint: PlanFingerprint): ApplyPlanNode? =
        runCatching {
            val plan = PreparedPlanEnvelopeCodec.decode(envelope)
            if (plan.planFingerprint != fingerprint) return@runCatching null
            val node = plan.applyPlan.nodesInTopologicalOrder().singleOrNull() ?: return@runCatching null
            val descriptor = node.preparedOperation.contract
            node.takeIf {
                it.dependsOn.isEmpty() &&
                    (descriptor.operationId == FinanceOperations.ACCOUNT_CREATE ||
                        descriptor.operationId == FinanceOperations.ENTRY_RECORD) &&
                    catalog.current(descriptor.operationId) == descriptor
            }
        }.getOrNull()

    fun executeAfterExplicitConfirmation(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        materialGate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("finance_review_unavailable")
            if (!isSelectedReview(context)) return@withMaterialWrite denied
            val scope = approved.read(context) ?: return@withMaterialWrite denied
            val review = workspace.load(context) ?: return@withMaterialWrite denied
            val envelope = review.envelope()
            val node = financeNode(envelope, scope.planFingerprint) ?: return@withMaterialWrite denied
            if (node.preparedOperation.contract.operationId != scope.operationId) return@withMaterialWrite denied
            val opening = if (scope.operationId == FinanceOperations.ACCOUNT_CREATE) {
                FinanceAccountCreatePayload.decode(node.preparedOperation.payload)?.openingBalanceMinor
                    ?: return@withMaterialWrite denied
            } else null

            // Keep the complete canonical intent, including inputs above the SQL key length.
            val digest = MessageDigest.getInstance("SHA-256").digest(
                node.preparedOperation.idempotencyKey.value.toByteArray(Charsets.UTF_8)
            ).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val grantKey = IdempotencyKey("w01:apply:" + digest)

            var admitted: ApplyAttemptIdentity? = null
            var grant: RealityFinance9ActiveDevGrant? = null
            val authorize = ApplyTransactionAuthorizationPort { plan, identity, checked, transaction ->
                grant != null && admitted == identity &&
                    transaction.transactionId == identity.transactionId &&
                    plan.planFingerprint == scope.planFingerprint && checked == node && current()
            }
            val finalGate = ApplyTransactionFinalCommitGate { plan, identity, receipt, checked, transaction ->
                checked.singleOrNull() == node &&
                    authorize.isAuthorized(plan, identity, node, transaction) &&
                    grant?.consumeInsideFinalCommit(transaction, identity, receipt) == true
            }
            coordinator(authorize, finalGate) { applying, identity ->
                val reread = workspace.load(applying.context())
                if (reread == null || reread.review != applying ||
                    applying.state != ConversationPlanReviewState.APPLYING ||
                    applying.transactionId != identity.transactionId ||
                    applying.planFingerprint != scope.planFingerprint ||
                    identity.planFingerprint != scope.planFingerprint ||
                    applying.conversationId != context.conversationId ||
                    applying.reviewId != context.reviewId ||
                    applying.authorityToken != context.authorityToken ||
                    applying.revision <= context.expectedRevision ||
                    applying.revision - context.expectedRevision != 1L ||
                    !reread.envelope().contentEquals(envelope) || !isSelectedReview(applying.context())
                ) return@coordinator false
                grant = grants.issue(scope.operationId, scope.objectId, scope.planFingerprint, grantKey, opening)
                if (grant == null) return@coordinator false
                admitted = identity
                true
            }.apply(context).toHostOutcome()
        }

    fun reconcileCommittedOnly(context: ConversationPlanReviewContext): ConversationApplyHostOutcome =
        materialGate.withMaterialWrite {
            val denied = ConversationApplyHostOutcome.Denied("finance_recovery_unavailable")
            if (!isSelectedReview(context)) return@withMaterialWrite denied
            val review = workspace.load(context) ?: return@withMaterialWrite denied
            if (financeNode(review.envelope(), context.planFingerprint) == null) return@withMaterialWrite denied
            coordinator().reconcileCommittedOnly(context).toHostOutcome()
        }

    private fun coordinator(
        authorization: ApplyTransactionAuthorizationPort? = null,
        finalGate: ApplyTransactionFinalCommitGate? = null,
        afterBegin: ((ConversationPlanReviewSnapshot, ApplyAttemptIdentity) -> Boolean)? = null,
    ) = ReviewedConversationApplyCoordinator(
        workspace = workspace,
        decode = PreparedPlanEnvelopeCodec::decode,
        retry = ApplyRetryReceiptGate(reality.applyAttemptIdentities, reality.executionReceiptLookup),
        preflight = ApplyPreflightGate(
            contracts = catalog,
            dependencyStamps = reality,
            operationRevalidators = listOf(
                FinanceAccountCreateRevalidation(),
                FinanceEntryRecordRevalidation(reality.financePersistence),
            ),
        ),
        bindings = ApplyBindingResolutionGate(listOf(
            FinanceAccountCreateBinding(reality.financePersistence),
            FinanceEntryRecordBinding(reality.financePersistence),
        )),
        executor = ApplyTransactionExecutor(reality, reality, authorization, finalGate),
        attempts = reality.applyAttemptLookup,
        receipts = reality.executionReceiptLookup,
        afterBeginApply = afterBegin,
    )
}
