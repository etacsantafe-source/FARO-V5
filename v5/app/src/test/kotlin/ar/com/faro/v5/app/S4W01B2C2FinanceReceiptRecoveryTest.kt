package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * W01-B2-C2: Finance receipt-only recovery on isolated SQLite.
 * It NEVER calls the productive Apply port and cannot mint a human grant.
 */
class S4W01B2C2FinanceReceiptRecoveryTest {
    @TempDir lateinit var root: Path
    private val catalog = BuiltInContractCatalog.create()

    private fun coordinator(
        workspace: ConversationWorkspaceStore,
        reality: RealityStore,
    ) = ReviewedConversationApplyCoordinator(
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
        executor = ApplyTransactionExecutor(reality, reality),
        attempts = reality.applyAttemptLookup,
        receipts = reality.executionReceiptLookup,
    )

    private inner class Rig(val label: String) : AutoCloseable {
        val dir = Files.createDirectories(root.resolve(label))
        val realityPath = dir.resolve("reality.db").toString()
        val workspacePath = dir.resolve("workspace.db").toString()
        val reality = RealityStore(realityPath)
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val chat = ConversationId("chat-" + label)
        val accountId = FinanceAccountId.of("finance-recover-" + label)
        val create = FinanceAccountCreate(
            accountId, FinanceAccountDisplayName.of("Recuperacion"),
            FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 75L,
        )
        val plan: PreparedPlan
        val approved: ConversationPlanReviewSnapshot

        init {
            val observed = reality.financePersistence.read()
            val preview = requireNotNull(FinanceManualDraftPreviews.account(
                create.accountId.value, create.displayName.value,
                create.currency.value, create.nature, 75L, observed,
            ))
            val proposed = requireNotNull(FinanceManualReviewedProposal.prepareAccount(
                preview, create, "human:recover:" + label,
                { reality.financePersistence.read() }, catalog,
            ))
            plan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("finance-recovery"), proposed, emptySet(),
            ))
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    chat, 100L, IdempotencyKey("chat-recovery-" + label),
                )
            } is ConversationSelectionResult.Saved)
            val token = requireNotNull(runBlocking {
                workspace.readTimeline(chat)
            }).authorityToken
            val pending = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                chat, "review-" + label, token, workspace.snapshot().revision,
                plan.planFingerprint, "Revisión recuperación Finance DEV",
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("propose-recovery-" + label),
            )) as WorkspacePlanReviewWriteResult.Saved
            approved = (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(pending.review.context()),
                    IdempotencyKey("approve-recovery-" + label),
                )
            } as ConversationReviewDecision.Saved).review
        }

        fun begin(): Pair<ConversationPlanReviewSnapshot, ApplyAttemptIdentity> {
            val identity = reality.applyAttemptIdentities.resolve(plan)
            val result = workspace.advance(
                ConversationPlanReviewIntent.BeginApply(approved.context(), identity),
                IdempotencyKey("start-recovery-" + label),
                reality.applyAttemptLookup, reality.executionReceiptLookup,
            ) as ConversationReviewDecision.Saved
            assertEquals(ConversationPlanReviewState.APPLYING, result.review.state)
            return result.review to identity
        }

        /** Simulate a crash exactly after the Reality commit but before Workspace Observe. */
        fun commitRealityWithoutWorkspaceObserve(
            identity: ApplyAttemptIdentity,
        ): ExecutionReceipt {
            val bound = ApplyBindingResolutionGate(
                listOf(FinanceAccountCreateBinding(reality.financePersistence)),
            ).resolve(plan) as ApplyBindingResolution.Resolved
            val completed = ApplyTransactionExecutor(reality, reality).execute(
                plan, identity, bound,
            ) as ApplyTransactionExecution.Completed
            assertEquals(identity.transactionId, completed.receipt.transactionId)
            return completed.receipt
        }

        fun recover(context: ConversationPlanReviewContext) =
            coordinator(workspace, reality).reconcileCommittedOnly(context)

        override fun close() {
            workspace.close()
            reality.close()
        }
    }

    @Test fun committedRealityReceiptRecoversWorkspaceAfterReopenWithoutSecondApply() {
        lateinit var expected: ExecutionReceipt
        lateinit var applying: ConversationPlanReviewContext
        lateinit var workspacePath: String
        lateinit var realityPath: String
        val chat = ConversationId("chat-crash")
        val account = FinanceAccountId.of("finance-recover-crash")
        Rig("crash").use { f ->
            workspacePath = f.workspacePath
            realityPath = f.realityPath
            assertEquals(chat, f.chat)
            assertEquals(account, f.accountId)
            val (begun, identity) = f.begin()
            applying = begun.context()
            expected = f.commitRealityWithoutWorkspaceObserve(identity)
            assertEquals(ConversationPlanReviewState.APPLYING,
                runBlocking { f.workspace.reviews(chat) }.single().state)
        }
        RealityStore(
            realityPath,
            transactionIdFactory = { error("recovery must never allocate transaction") },
            receiptIdFactory = { error("recovery must never mint receipt") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val result = coordinator(workspace, reality).reconcileCommittedOnly(applying)
                    as ReviewedConversationApplyResult.Committed
                assertEquals(expected, result.receipt)
                assertEquals(expected, result.review.receipt)
                assertEquals(ConversationPlanReviewState.COMMITTED, result.review.state)
                assertEquals(75L, reality.financePersistence.read().ledgerMinor(account))
                assertEquals(expected,
                    reality.executionReceiptLookup.find(expected.transactionId))
                assertEquals(ConversationPlanReviewState.COMMITTED,
                    runBlocking { workspace.reviews(chat) }.single().state)
            }
        }
    }

    @Test fun approvedWithoutStartedAttemptDoesNotAllocatePreparedOrReceipt() {
        Rig("not-started").use { f ->
            val result = f.recover(f.approved.context())
            assertTrue(result is ReviewedConversationApplyResult.Denied)
            assertEquals(0L, f.reality.financePersistence.read().revision)
            assertTrue(f.reality.financePersistence.read().accounts.isEmpty())
            assertEquals(ConversationPlanReviewState.APPROVED,
                runBlocking { f.workspace.reviews(f.chat) }.single().state)
        }
    }

    @Test fun applyingWithoutCommittedReceiptStaysRecoveryRequired() {
        Rig("prepared").use { f ->
            val (begun, identity) = f.begin()
            val result = f.recover(begun.context())
            assertEquals(ReviewedConversationApplyResult.RecoveryRequired, result)
            assertNull(f.reality.executionReceiptLookup.find(identity.transactionId))
            assertTrue(f.reality.financePersistence.read().accounts.isEmpty())
            assertEquals(ConversationPlanReviewState.APPLYING,
                runBlocking { f.workspace.reviews(f.chat) }.single().state)
        }
    }

    @Test fun wrongFingerprintOrAuthorityCannotRecoverAnotherReview() {
        Rig("context").use { f ->
            val (begun, identity) = f.begin()
            val receipt = f.commitRealityWithoutWorkspaceObserve(identity)
            val authentic = begun.context()
            val forgedFingerprint = authentic.copy(
                planFingerprint = PlanFingerprint("b".repeat(64)),
            )
            val forgedToken = authentic.copy(authorityToken = "forged-token")
            assertTrue(f.recover(forgedFingerprint) is ReviewedConversationApplyResult.Denied)
            assertTrue(f.recover(forgedToken) is ReviewedConversationApplyResult.Denied)
            assertEquals(ConversationPlanReviewState.APPLYING,
                runBlocking { f.workspace.reviews(f.chat) }.single().state)
            val genuine = f.recover(authentic) as ReviewedConversationApplyResult.Committed
            assertEquals(receipt, genuine.receipt)
        }
    }

    @Test fun secondRecoveryDoesNotDuplicateFinanceLedgerOrReceipt() {
        Rig("repeat").use { f ->
            val (begun, identity) = f.begin()
            val receipt = f.commitRealityWithoutWorkspaceObserve(identity)
            val first = f.recover(begun.context()) as ReviewedConversationApplyResult.Committed
            val revision = f.reality.financePersistence.read().revision
            val again = f.recover(first.review.context())
            assertTrue(again is ReviewedConversationApplyResult.Denied)
            assertEquals(revision, f.reality.financePersistence.read().revision)
            assertEquals(1, f.reality.financePersistence.read().accounts.size)
            assertEquals(receipt,
                f.reality.executionReceiptLookup.find(identity.transactionId))
        }
    }
}
