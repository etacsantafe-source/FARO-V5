package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.workspace.*
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/**
 * W01-B1 DEV only. The trusted foreground callback is an injected admission
 * seam, NOT proof of an authenticated human, an owner grant or Apply authority.
 * Not composed in any shipping Activity, and never executes Finance writes.
 */
internal class S4W01B1FinanceReviewHost(
    private val generationId: String,
    private val workspace: ConversationWorkspaceStore,
    private val finance: FinanceSnapshotReadApi,
    private val catalog: OperationContractLookupPort,
    private val materialGate: MaterialWriteGate,
    private val currentGenerationIdOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val trustedForegroundRequest: () -> Boolean,
) {
    init { require(generationId.isNotBlank()) }

    private fun current(): Boolean = runCatching {
        hostReady() && currentGenerationIdOrNull() == generationId
    }.getOrDefault(false)

    fun createAccountPending(
        conversationId: ConversationId,
        confirmed: FinanceAccountCreate,
        explicitlyConfirmedOpening: Boolean,
        humanIntentKey: String,
    ): WorkspacePlanReviewWriteResult? {
        if (!explicitlyConfirmedOpening) return null
        return submit(conversationId, humanIntentKey, "account",
            reviewText = "Crear cuenta «${confirmed.displayName.value}» (${confirmed.currency.value}), " +
                "saldo inicial declarado ${confirmed.openingBalanceMinor} unidades menores. Revisar cambios.",
        ) {
            val observed = finance.read()
            val preview = FinanceManualDraftPreviews.account(
                accountIdRaw = confirmed.accountId.value,
                displayNameRaw = confirmed.displayName.value,
                currencyRaw = confirmed.currency.value,
                nature = confirmed.nature,
                explicitOpeningBalanceMinor = confirmed.openingBalanceMinor,
                snapshot = observed,
            ) ?: return@submit null
            FinanceManualReviewedProposal.prepareAccount(
                preview, confirmed, humanIntentKey, { finance.read() }, catalog,
            )
        }
    }

    fun createEntryPending(
        conversationId: ConversationId,
        confirmed: FinanceEntryRecord,
        humanIntentKey: String,
    ): WorkspacePlanReviewWriteResult? =
        submit(conversationId, humanIntentKey, "entry",
            reviewText = "Registrar ${confirmed.kind.name} de ${confirmed.amountMinor} unidades menores " +
                "en cuenta ${confirmed.accountId.value}: ${confirmed.concept.value}. Revisar cambios.",
        ) {
            val observed = finance.read()
            val preview = FinanceManualDraftPreviews.entry(
                entryIdRaw = confirmed.entryId.value,
                accountIdRaw = confirmed.accountId.value,
                amountMinor = confirmed.amountMinor,
                kind = confirmed.kind,
                conceptRaw = confirmed.concept.value,
                categoryRaw = confirmed.category.value,
                effectiveAtEpochMillis = confirmed.effectiveAtEpochMillis,
                recordedAtEpochMillis = confirmed.recordedAtEpochMillis,
                snapshot = observed,
            ) ?: return@submit null
            FinanceManualReviewedProposal.prepareEntry(
                preview, confirmed, humanIntentKey, { finance.read() }, catalog,
            )
        }

    fun createActiveW01OperationPending(
        conversationId: ConversationId,
        operationId: OperationId,
        payload: CanonicalPayload,
        humanIntentKey: String,
    ): WorkspacePlanReviewWriteResult? {
        val preview = S4W01F1FinanceReviewOperations.preview(operationId, payload) ?: return null
        return submit(conversationId, humanIntentKey, operationId.value, preview.text) {
            val contract = catalog.current(operationId)?.takeIf {
                it.canonicalExecutor == FinanceOperations.EXECUTOR_ID &&
                    it.executionProfile == ExecutionProfile.REALITY_MUTATION && it.exposure == ContractExposure.BINDABLE
            } ?: return@submit null
            PreparedOperation(contract, payload, DependencyStampSet.empty(), IdempotencyKey(humanIntentKey))
        }
    }

    private fun submit(
        conversationId: ConversationId,
        humanIntentKey: String,
        kind: String,
        reviewText: String,
        prepare: () -> PreparedOperation?,
    ): WorkspacePlanReviewWriteResult? = runCatching {
        materialGate.withMaterialWrite {
            if (!current() || !trustedForegroundRequest()) return@withMaterialWrite null
            val before = workspace.snapshot()
            if (before.workspace.activeConversationId != conversationId) return@withMaterialWrite null
            val timeline = runBlocking { workspace.readTimeline(conversationId) }
                ?: return@withMaterialWrite null
            if (timeline.revision != before.revision ||
                timeline.authorityToken.isBlank()
            ) return@withMaterialWrite null

            val operation = prepare() ?: return@withMaterialWrite null
            val plan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("finance"), operation, emptySet(),
            ))
            val identity = MessageDigest.getInstance("SHA-256")
                .digest(("${conversationId.value}:$kind:$humanIntentKey")
                    .toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val reviewId = "w01:finance:" + identity
            val existing = runBlocking { workspace.reviews(conversationId) }
                .firstOrNull { it.reviewId == reviewId }
            if (!current() || !trustedForegroundRequest()) return@withMaterialWrite null
            val after = workspace.snapshot()
            if (after.revision != before.revision ||
                after.workspace.activeConversationId != conversationId
            ) return@withMaterialWrite null
            if (existing != null) {
                return@withMaterialWrite if (
                    existing.planFingerprint == plan.planFingerprint &&
                    existing.reviewText == reviewText &&
                    existing.state == ConversationPlanReviewState.PENDING
                ) WorkspacePlanReviewWriteResult.Saved(existing, true) else null
            }

            workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversationId = conversationId,
                reviewId = reviewId,
                authorityToken = timeline.authorityToken,
                expectedWorkspaceRevision = before.revision,
                planFingerprint = plan.planFingerprint,
                reviewText = reviewText,
                planEnvelope = PreparedPlanEnvelopeCodec.encode(plan),
                idempotencyKey = IdempotencyKey("w01:review:" + identity),
            ))
        }
    }.getOrNull()
}
