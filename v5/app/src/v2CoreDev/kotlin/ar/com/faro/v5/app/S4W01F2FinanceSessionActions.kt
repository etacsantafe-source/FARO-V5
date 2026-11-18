package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.util.UUID
import kotlinx.coroutines.runBlocking

internal sealed interface W01FinanceDraft {
    data class Account(val value: FinanceAccountCreate) : W01FinanceDraft
    data class Entry(val value: FinanceEntryRecord) : W01FinanceDraft
    data class Operation(val id: OperationId, val payload: CanonicalPayload) : W01FinanceDraft
}

internal data class W01FinanceOverview(
    val finance: FinanceSnapshot,
    val openings: RealityFinance9OpeningProvenanceSnapshot?,
    val latestReceipts: List<ReceiptId>,
)

/** Foreground actions over the W01 session's existing Workspace and Reality. */
internal class S4W01F2FinanceSessionActions(
    pinned: ActiveGenerationPointer,
    pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val reality: RealityStore,
    gate: MaterialWriteGate,
    principalHash: String,
    epoch: String,
    private val admitted: () -> Boolean,
) {
    private val catalog = BuiltInContractCatalog.create()
    private val review = S4W01B1FinanceReviewHost(pinned.generationId.value, workspace, FinanceSnapshotReadApi(reality.financePersistence::read),
        catalog, gate, { pointer.read()?.generationId?.value }, admitted, admitted)
    private val apply = S4W01B2D1ActiveFinanceApplyHost(pinned, pointer, workspace, reality, gate, catalog,
        principalHash, epoch, { epoch.takeIf { admitted() } }, admitted, admitted)
    private val openings = reality.finance9ActiveDevOpeningProvenanceRead(admitted)
    private val budgets = reality.finance9ActiveDevBudgetRead(admitted)
    private val commitments = reality.finance9ActiveDevCommitmentCalendar(admitted)

    fun overview(): W01FinanceOverview? = visible {
        val source = reality.financePersistence.read()
        val confirmed = openings.read()?.takeIf { it.sourceRevision == source.revision }
        val receipts = workspace.snapshot().workspace.activeConversationId?.let(::receipts).orEmpty()
        W01FinanceOverview(source, confirmed, receipts)
    }

    fun calendar(query: FinanceCommitmentCalendarQuery): FinanceCommitmentCalendar? =
        visible { commitments.read(query) }

    fun budget(query: FinanceBudgetQuery): FinanceBudgetObservation? = visible { budgets.read(query) }

    fun prepare(draft: W01FinanceDraft): ConversationPlanReviewSnapshot? = visible {
        val chat = conversation() ?: return@visible null
        if (!admitted()) return@visible null
        val intent = UUID.randomUUID().toString()
        val pending = when (draft) {
            is W01FinanceDraft.Account -> review.createAccountPending(chat, draft.value, true, intent)
            is W01FinanceDraft.Entry -> review.createEntryPending(chat, draft.value, intent)
            is W01FinanceDraft.Operation -> review.createActiveW01OperationPending(chat, draft.id, draft.payload, intent)
        }
        (pending as? WorkspacePlanReviewWriteResult.Saved)?.review
    }

    fun confirm(pending: ConversationPlanReviewSnapshot): ConversationApplyHostOutcome? = visible {
        val approved = runBlocking {
            workspace.decide(ConversationPlanReviewIntent.Approve(pending.context()),
                IdempotencyKey("w01:approve:${pending.reviewId}"))
        } as? ConversationReviewDecision.Saved ?: return@visible null
        if (admitted() && approved.review.state == ConversationPlanReviewState.APPROVED)
            apply.executeAfterExplicitConfirmation(approved.review.context())
        else null
    }

    fun discard(pending: ConversationPlanReviewSnapshot): Boolean = visible {
        runBlocking {
            workspace.decide(ConversationPlanReviewIntent.Discard(pending.context()),
                IdempotencyKey("w01:discard:${pending.reviewId}"))
        } is ConversationReviewDecision.Saved
    } ?: false

    private fun receipts(chat: ConversationId): List<ReceiptId> {
        val reviews = runBlocking { workspace.reviews(chat) }.filter { it.reviewId.startsWith("w01:finance:") }
        for (item in reviews) {
            if (!admitted()) return emptyList()
            if (item.state == ConversationPlanReviewState.APPLYING ||
                item.state == ConversationPlanReviewState.RECOVERING_UNKNOWN
            ) {
                apply.reconcileCommittedOnly(item.context())
            }
        }
        return runBlocking { workspace.reviews(chat) }.asReversed().mapNotNull { item ->
            item.receipt?.takeIf { item.reviewId.startsWith("w01:finance:") &&
                reality.executionReceiptLookup.find(it.transactionId) == it
            }?.receiptId
        }.take(5)
    }

    private fun conversation(): ConversationId? {
        workspace.snapshot().workspace.activeConversationId?.let { return it }
        val id = ConversationId("w01-finance-" + UUID.randomUUID())
        val result = runBlocking { workspace.createAndSelect(
            id, System.currentTimeMillis(), IdempotencyKey("w01:select:${id.value}"),
        ) }
        return id.takeIf { result is ConversationSelectionResult.Saved }
    }

    private fun <T> visible(action: () -> T?): T? = runCatching {
        if (!admitted()) return@runCatching null
        action()?.takeIf { admitted() }
    }.getOrNull()
}
