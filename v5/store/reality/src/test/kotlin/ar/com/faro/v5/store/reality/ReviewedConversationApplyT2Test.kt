package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Native five-owner host boundary: real Workspace + real Reality SQLite. */
class ReviewedConversationApplyT2Test {
    @TempDir lateinit var dir: Path
    private val chat = ConversationId("approved-chat")
    private val reviewId = "review-1"
    private fun db(name: String) = dir.resolve(name).toString()
    private fun <T> call(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return requireNotNull(result).getOrThrow()
    }

    private fun prepared(reality: RealityStore): PreparedPlan {
        val descriptor = OperationContractDescriptor(
            ProfileOperations.UPDATE_DECLARED,
            ProfileOperations.CAPABILITY_ID,
            ProfileOperations.EXECUTOR_ID,
            ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"),
            ContractFingerprint("profile-update-declared-v1"),
            ContractExposure.BINDABLE,
        )
        val prepared = PreparedOperation(
            descriptor,
            ProfileDeclaredUpdatePayload.payload(
                ProfileDeclaredUpdate(DeclaredDisplayName.of("FARO Verified"))
            ),
            DependencyStampSet.of(reality.read().dependencyStamp()),
            IdempotencyKey("profile-apply"),
        )
        return PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"), prepared, emptySet()))
    }

    private fun setupReview(
        workspace: ConversationWorkspaceStore,
        plan: PreparedPlan,
        approve: Boolean,
    ): ConversationPlanReviewSnapshot {
        assertTrue(call {
            workspace.createAndSelect(chat, 10L, IdempotencyKey("chat-create"))
        } is ConversationSelectionResult.Saved)
        val token = requireNotNull(call { workspace.read(chat) }).authorityToken
        val result = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, reviewId, token, workspace.snapshot().revision,
            plan.planFingerprint, "Modificar nombre declarado en perfil",
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("create-review"),
        ))
        val pending = (result as WorkspacePlanReviewWriteResult.Saved).review
        if (!approve) return pending
        val accepted = call {
            workspace.decide(
                ConversationPlanReviewIntent.Approve(pending.context()),
                IdempotencyKey("human-approval"),
            )
        }
        return (accepted as ConversationReviewDecision.Saved).review
    }

    private fun coordinator(
        workspace: ConversationWorkspaceStore,
        reality: RealityStore,
    ): ReviewedConversationApplyCoordinator {
        val contracts = OperationContractLookupPort { operation ->
            if (operation == ProfileOperations.UPDATE_DECLARED) OperationContractDescriptor(
                ProfileOperations.UPDATE_DECLARED,
                ProfileOperations.CAPABILITY_ID,
                ProfileOperations.EXECUTOR_ID,
                ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"),
                ContractFingerprint("profile-update-declared-v1"),
                ContractExposure.BINDABLE,
            ) else null
        }
        return ReviewedConversationApplyCoordinator(
            workspace = workspace,
            decode = PreparedPlanEnvelopeCodec::decode,
            retry = ApplyRetryReceiptGate(
                reality.applyAttemptIdentities, reality.executionReceiptLookup,
            ),
            preflight = ApplyPreflightGate(
                contracts, reality, listOf(ProfileUpdateDeclaredRevalidation()),
            ),
            bindings = ApplyBindingResolutionGate(
                listOf(ProfileUpdateDeclaredBinding(reality)),
            ),
            executor = ApplyTransactionExecutor(reality, reality),
            attempts = reality.applyAttemptLookup,
            receipts = reality.executionReceiptLookup,
        )
    }

    @Test fun realHumanApprovalCommitsRealityAndWorkspaceReceiptAndRestoresBoth() {
        RealityStore(db("reality.db"),
            transactionIdFactory = { TransactionId("tx-approved") },
            receiptIdFactory = { ReceiptId("receipt-approved") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val plan = prepared(reality)
                val pending = setupReview(workspace, plan, approve = false)
                val runner = coordinator(workspace, reality)
                assertEquals(
                    ReviewedConversationApplyResult.Denied("approved_context_unavailable"),
                    runner.apply(pending.context()),
                )
                assertNull(reality.read().displayName)
                assertNull(reality.applyAttemptLookup.find(TransactionId("tx-approved")))
                val approved = call {
                    workspace.decide(
                        ConversationPlanReviewIntent.Approve(pending.context()),
                        IdempotencyKey("human-approval"),
                    )
                } as ConversationReviewDecision.Saved
                val committed = runner.apply(approved.review.context())
                assertTrue(committed is ReviewedConversationApplyResult.Committed)
                val receipt = (committed as ReviewedConversationApplyResult.Committed).receipt
                assertEquals("FARO Verified", reality.read().displayName!!.value)
                assertEquals(1L, reality.read().revision)
                assertEquals(receipt, committed.review.receipt)
                assertEquals(ConversationPlanReviewState.COMMITTED, committed.review.state)
                assertFalse(workspace.snapshot().workspace.conversations.single().hasPendingReview)
            }
        }
        RealityStore(db("reality.db"),
            transactionIdFactory = { error("must not create second transaction") },
            receiptIdFactory = { error("must not create second receipt") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val restored = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.COMMITTED, restored.state)
                assertEquals(
                    restored.receipt,
                    reality.executionReceiptLookup.find(restored.transactionId!!),
                )
                assertEquals("FARO Verified", reality.read().displayName!!.value)
                assertEquals(1L, reality.read().revision)
                assertEquals(
                    ReviewedConversationApplyResult.Denied("approved_context_unavailable"),
                    coordinator(workspace, reality).apply(restored.context()),
                )
            }
        }
    }

    @Test fun crashAfterRealityCommitBeforeWorkspaceDeliveryReconcilesWithoutDoubleApply() {
        lateinit var approvedPlan: PreparedPlan
        lateinit var identity: ApplyAttemptIdentity
        RealityStore(db("reality.db"),
            transactionIdFactory = { TransactionId("tx-crash") },
            receiptIdFactory = { ReceiptId("receipt-crash") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                approvedPlan = prepared(reality)
                val approved = setupReview(workspace, approvedPlan, approve = true)
                identity = reality.applyAttemptIdentities.resolve(approvedPlan)
                val started = workspace.advance(
                    ConversationPlanReviewIntent.BeginApply(approved.context(), identity),
                    IdempotencyKey("begin-before-crash"),
                    reality.applyAttemptLookup,
                    reality.executionReceiptLookup,
                ) as ConversationReviewDecision.Saved
                assertEquals(ConversationPlanReviewState.APPLYING, started.review.state)
                val binding = ApplyBindingResolutionGate(
                    listOf(ProfileUpdateDeclaredBinding(reality)),
                ).resolve(approvedPlan) as ApplyBindingResolution.Resolved
                val committed = ApplyTransactionExecutor(reality, reality).execute(
                    approvedPlan, identity, binding,
                )
                assertTrue(committed is ApplyTransactionExecution.Completed)
                // Process dies NOW, after Reality COMMIT but before Workspace projection.
            }
        }
        RealityStore(db("reality.db"),
            transactionIdFactory = { error("must not resolve a new transaction") },
            receiptIdFactory = { error("must not execute a second commit") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val applying = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.APPLYING, applying.state)
                val reconciled = coordinator(workspace, reality).apply(applying.context())
                assertTrue(reconciled is ReviewedConversationApplyResult.Committed)
                assertEquals(ConversationPlanReviewState.COMMITTED,
                    (reconciled as ReviewedConversationApplyResult.Committed).review.state)
                assertEquals(identity.transactionId, reconciled.receipt.transactionId)
                assertEquals(1L, reality.read().revision)
                assertFalse(workspace.snapshot().workspace.conversations.single().hasPendingReview)
            }
        }
    }

    @Test fun fabricatedReceiptAndWrongPlanAreRejectedByActualRealityLookup() {
        RealityStore(db("reality.db"),
            transactionIdFactory = { TransactionId("tx-uncommitted") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val plan = prepared(reality)
                val approved = setupReview(workspace, plan, approve = true)
                assertNull(workspace.load(approved.context().copy(
                    planFingerprint = PlanFingerprint("0".repeat(64)),
                )))
                val identity = reality.applyAttemptIdentities.resolve(plan)
                val begun = workspace.advance(
                    ConversationPlanReviewIntent.BeginApply(approved.context(), identity),
                    IdempotencyKey("begin"), reality.applyAttemptLookup,
                    reality.executionReceiptLookup,
                ) as ConversationReviewDecision.Saved
                val counterfeit = ExecutionReceipt(
                    ReceiptId("forged"), identity.transactionId, plan.planFingerprint,
                )
                val denied = workspace.advance(
                    ConversationPlanReviewIntent.ObserveCommitted(
                        begun.review.context(), counterfeit,
                    ),
                    IdempotencyKey("forged-observation"), reality.applyAttemptLookup,
                    reality.executionReceiptLookup,
                )
                assertEquals(ConversationReviewDecision.Rejected, denied)
                assertEquals(ConversationPlanReviewState.APPLYING,
                    call { workspace.reviews(chat) }.single().state)
                assertNull(reality.executionReceiptLookup.find(identity.transactionId))
                assertNull(reality.read().displayName)
            }
        }
    }
}
