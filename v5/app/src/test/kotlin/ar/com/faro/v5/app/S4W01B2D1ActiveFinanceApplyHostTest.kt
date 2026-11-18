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

        fun operation(id: OperationId, payload: CanonicalPayload, intent: String): ConversationPlanReviewSnapshot =
            (review.createActiveW01OperationPending(chat, id, payload, intent) as WorkspacePlanReviewWriteResult.Saved).review

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
        val reader = rig.reality.finance9ActiveDevOpeningProvenanceRead { rig.runtime.isCurrent(rig.selection) }
        val confirmed = requireNotNull(reader.read()).accounts.single()
        assertEquals(100L, confirmed.confirmedOpeningMinor)
        assertEquals(440L, confirmed.ledgerBalanceMinor)
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

    @Test fun appliesReviewedTransferAndReceivesOneReceipt() = Rig().use { rig ->
        val ars = FinanceCurrencyCode.of("ARS")
        val destination = FinanceAccountId.of("savings")
        assertTrue(rig.apply(rig.approve(rig.account())) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(rig.approve(rig.account(destination, "destination"))) is ConversationApplyHostOutcome.Committed)
        val transfer = FinanceTransferRecord(
            FinanceTransferId("reviewed-transfer"), rig.accountId, destination, ars, 30, 90, 100,
            FinanceEntryConcept.of("Ahorros"), false,
        )
        val review = rig.operation(FinanceOperations.TRANSFER_RECORD, FinanceTransferRecordPayload.payload(transfer), "transfer")
        assertTrue(review.reviewText.contains("ARS 0.30"))
        assertTrue(rig.apply(review) is ConversationApplyHostOutcome.Denied)
        val result = rig.apply(rig.approve(review)) as ConversationApplyHostOutcome.Committed
        assertEquals(result.receipt, rig.reality.executionReceiptLookup.find(result.receipt.transactionId))
        val ledger = rig.reality.financePersistence.read()
        assertEquals(70L, ledger.ledgerMinor(rig.accountId))
        assertEquals(130L, ledger.ledgerMinor(destination))
        assertEquals(2, ledger.entries.size)
    }

    @Test fun appliesReviewedCommitmentsPaymentAndBudgetThroughSameGraph() = Rig().use { rig ->
        val ars = FinanceCurrencyCode.of("ARS")
        val month = java.time.YearMonth.of(2026, 11)
        val concept = FinanceEntryConcept.of("Cuota real")
        val category = FinanceEntryCategory.of("Cuotas")
        assertTrue(rig.apply(rig.approve(rig.account())) is ConversationApplyHostOutcome.Committed)
        val debt = FinanceCommitmentCreate.Debt(FinanceCommitmentId("debt"), "Deuda", "Acreedor", ars, 100, 100)
        val declaration = FinanceCommitmentCreate.PlanItem(
            FinanceCommitmentId("item"), null, debt.id, month, 1, month.atDay(10), ars, 100, 100,
        )
        val expense = FinanceEntryRecord(FinanceEntryId.of("debt-expense"), rig.accountId, 50,
            FinanceEntryKind.EXPENSE, concept, category, 90, 100)
        val payment = FinanceCommitmentPayment(declaration.id, debt.id, ars, 0, expense)
        val commits = listOf(
            Triple(FinanceOperations.DEBT_CREATE, FinanceCommitmentCreatePayload.payload(debt), "debt"),
            Triple(FinanceOperations.PLAN_ITEM_CREATE, FinanceCommitmentCreatePayload.payload(declaration), "item"),
            Triple(FinanceOperations.DEBT_PAYMENT_RECORD,
                FinanceCommitmentPaymentPayload.payload(FinanceOperations.DEBT_PAYMENT_RECORD, payment), "pay"),
        )
        for ((id, payload, intent) in commits) {
            val result = rig.apply(rig.approve(rig.operation(id, payload, intent))) as ConversationApplyHostOutcome.Committed
            assertEquals(result.receipt, rig.reality.executionReceiptLookup.find(result.receipt.transactionId))
        }
        assertEquals(listOf(expense.entryId), rig.reality.financePersistence.read().entries.map { it.entryId })
        val key = FinanceBudgetKey(month, category, ars)
        val budget = FinanceBudgetChange(key, rig.reality.financePersistence.read().revision, 500, 100)
        val review = rig.operation(FinanceOperations.BUDGET_SET, FinanceBudgetChangePayload.payload(budget), "budget")
        val result = rig.apply(rig.approve(review)) as ConversationApplyHostOutcome.Committed
        assertEquals(result.receipt, rig.reality.executionReceiptLookup.find(result.receipt.transactionId))
        val observed = rig.reality.finance9ActiveDevBudgetRead { true }
            .read(FinanceBudgetQuery(key, java.time.ZoneOffset.UTC))
        assertEquals(500L, observed.limitMinor)
        assertEquals(50L, observed.uncategorizedCommitmentsMinor)
    }

    @Test fun refusesMalformedAndAlteredAdditionalReviewText() = Rig().use { rig ->
        val ars = FinanceCurrencyCode.of("ARS")
        val debt = FinanceCommitmentCreate.Debt(FinanceCommitmentId("tamper"), "Deuda", "Acreedor", ars, 100, 100)
        val payload = FinanceCommitmentCreatePayload.payload(debt)
        val corrupt = CanonicalPayload.fromEncoded(payload.formatId, payload.copyBytes() + byteArrayOf(0))
        assertNull(rig.review.createActiveW01OperationPending(rig.chat, FinanceOperations.DEBT_CREATE, corrupt, "corrupt"))
        val review = rig.approve(rig.operation(FinanceOperations.DEBT_CREATE, payload, "original"))
        val stored = requireNotNull(rig.workspace.load(review.context()))
        val before = rig.workspace.snapshot()
        val forged = rig.workspace.createPlanReview(ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest(
            conversationId = rig.chat,
            reviewId = "w01:finance:forged",
            authorityToken = review.authorityToken,
            expectedWorkspaceRevision = before.revision,
            planFingerprint = review.planFingerprint,
            reviewText = "Declarar deuda por ARS 9.00.",
            planEnvelope = stored.envelope(),
            idempotencyKey = IdempotencyKey("forged-review"),
        )) as WorkspacePlanReviewWriteResult.Saved
        assertTrue(rig.apply(rig.approve(forged.review)) is ConversationApplyHostOutcome.Denied)
        val calendar = rig.reality.finance9ActiveDevCommitmentCalendar { true }.read(
            FinanceCommitmentCalendarQuery(java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 11),
                java.time.LocalDate.of(2026, 11, 18), ars),
        )
        assertTrue(requireNotNull(calendar).debts.isEmpty())
    }
}
