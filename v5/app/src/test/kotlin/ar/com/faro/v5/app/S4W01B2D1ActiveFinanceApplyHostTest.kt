package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W01B2D1ActiveFinanceApplyHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val catalog = BuiltInContractCatalog.create()
        val pointer = FileActiveGenerationPointer(root)
        val original = StorageGenerationId("planning8-origin")
        val next = StorageGenerationDescriptor(StorageGenerationId("finance9-target"), "runtime", 20L)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selection = try {
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(original, "planning", 10L),
            )
            runtime.start(
                MaintenanceOperationId("finance-reviews-cutover"), next,
                requireNotNull(pointer.compareAndSet(null, original)), 30L,
            ) { true }
        } catch (failure: Throwable) {
            runtime.close()
            throw failure
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("finance-review")
        val accountId = FinanceAccountId.of("cash")
        var foreground = true

        val review = S4W01B1FinanceReviewHost(
            graph.generationId, workspace, FinanceSnapshotReadApi { reality.financePersistence.read() },
            catalog, gate, { pointer.read()?.generationId?.value },
            { runtime.isCurrent(selection) }, { foreground },
        )
        val host = S4W01B2D1ActiveFinanceApplyHost(
            selection.pointer, pointer, workspace, reality, gate, catalog, "a".repeat(64),
            "host-epoch", { "host-epoch" }, { runtime.isCurrent(selection) }, { foreground },
        )

        init {
            select(chat)
        }

        fun select(id: ConversationId) {
            assertTrue(runBlocking {
                workspace.createAndSelect(id, 60L, IdempotencyKey("select-" + id.value))
            } is ConversationSelectionResult.Saved)
        }

        fun account(id: FinanceAccountId = accountId, intent: String = "finance-account-first"):
            ConversationPlanReviewSnapshot {
            val draft = FinanceAccountCreate(
                id, FinanceAccountDisplayName.of(id.value), FinanceCurrencyCode.of("ARS"),
                FinanceAccountNature.CASH, 100L,
            )
            val result = review.createAccountPending(chat, draft, true, intent)
            return (result as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun entry(): ConversationPlanReviewSnapshot {
            val draft = FinanceEntryRecord(
                FinanceEntryId.of("income"), accountId, 340L, FinanceEntryKind.INCOME,
                FinanceEntryConcept.of("Ingreso local"), FinanceEntryCategory.of("Otros"), 80L, 90L,
            )
            return (review.createEntryPending(
                chat, draft, "finance-entry-first",
            ) as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun approve(review: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(review.context()),
                    IdempotencyKey("approve-" + review.reviewId),
                )
            } as ConversationReviewDecision.Saved).review

        fun apply(review: ConversationPlanReviewSnapshot): ConversationApplyHostOutcome =
            host.executeAfterExplicitConfirmation(review.context())

        override fun close() = runtime.close()
    }

    @Test fun commitsAccountAndEntryFromApprovedReviewsWithRealReceipts() = Rig().use { rig ->
        val account = rig.approve(rig.account(intent = "account-" + "x".repeat(140)))
        val accountResult = rig.apply(account) as ConversationApplyHostOutcome.Committed
        val entry = rig.approve(rig.entry())
        val entryResult = rig.apply(entry) as ConversationApplyHostOutcome.Committed
        val committed = runBlocking { rig.workspace.reviews(rig.chat) }
        assertEquals(2, committed.size)
        assertTrue(committed.all { it.state == ConversationPlanReviewState.COMMITTED })
        for (result in listOf(accountResult, entryResult)) {
            assertEquals(result.receipt, rig.reality.executionReceiptLookup.find(result.receipt.transactionId))
            assertTrue(committed.any { it.receipt == result.receipt })
        }
        assertNotEquals(accountResult.receipt, entryResult.receipt)
        assertTrue(rig.apply(entry) is ConversationApplyHostOutcome.Denied)
        assertEquals(440L, rig.reality.financePersistence.read().ledgerMinor(rig.accountId))
        assertEquals(1, rig.reality.financePersistence.read().entries.size)
    }

    @Test fun rejectsPendingReviewAndAbsentForegroundConfirmation() = Rig().use { rig ->
        val pending = rig.account()
        assertTrue(rig.apply(pending) is ConversationApplyHostOutcome.Denied)
        val approved = rig.approve(pending)
        rig.foreground = false
        assertTrue(rig.apply(approved) is ConversationApplyHostOutcome.Denied)
        assertTrue(rig.reality.financePersistence.read().accounts.isEmpty())
    }

    @Test fun rejectsStaleFinanceDependencyBeforeApply() = Rig().use { rig ->
        val stale = rig.approve(rig.account(FinanceAccountId.of("stale"), "finance-stale-account"))
        val first = rig.approve(rig.account())
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(stale) is ConversationApplyHostOutcome.Denied)
        assertEquals(listOf(rig.accountId), rig.reality.financePersistence.read().accounts.map { it.accountId })
    }

    @Test fun rejectsApprovedReviewFromAnUnselectedConversation() = Rig().use { rig ->
        val approved = rig.approve(rig.account())
        rig.select(ConversationId("other-review"))
        assertTrue(rig.apply(approved) is ConversationApplyHostOutcome.Denied)
        assertTrue(rig.reality.financePersistence.read().accounts.isEmpty())
    }

    @Test fun recoveryDoesNotStartApplyForAnApprovedReview() = Rig().use { rig ->
        val approved = rig.approve(rig.account())
        val recovered = rig.host.reconcileCommittedOnly(approved.context())
        assertTrue(recovered is ConversationApplyHostOutcome.Denied)
        assertEquals(approved, runBlocking { rig.workspace.reviews(rig.chat) }.single())
        assertTrue(rig.reality.financePersistence.read().accounts.isEmpty())
    }
}
