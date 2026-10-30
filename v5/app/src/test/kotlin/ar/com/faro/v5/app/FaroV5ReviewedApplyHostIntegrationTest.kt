package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * F1 host integration: real Android Kotlin adapter, Workspace and Reality SQLite.
 * No shipping V2 activation or simulated user-facing Apply permission.
 */
class FaroV5ReviewedApplyHostIntegrationTest {
    @TempDir lateinit var dir: Path
    private val chat = ConversationId("host-approved-chat")
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
        val operation = PreparedOperation(
            descriptor,
            ProfileDeclaredUpdatePayload.payload(
                ProfileDeclaredUpdate(DeclaredDisplayName.of("FARO Verified"))
            ),
            DependencyStampSet.of(reality.read().dependencyStamp()),
            IdempotencyKey("host-profile-apply"),
        )
        return PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"), operation, emptySet()))
    }

    private fun approved(
        workspace: ConversationWorkspaceStore,
        plan: PreparedPlan,
    ): ConversationPlanReviewSnapshot {
        assertTrue(call {
            workspace.createAndSelect(chat, 10L, IdempotencyKey("host-chat-create"))
        } is ConversationSelectionResult.Saved)
        val token = requireNotNull(call { workspace.read(chat) }).authorityToken
        val created = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
            chat, "review-host", token, workspace.snapshot().revision,
            plan.planFingerprint, "Modificar nombre declarado en perfil",
            PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("host-review-create"),
        )) as WorkspacePlanReviewWriteResult.Saved
        val accepted = call {
            workspace.decide(
                ConversationPlanReviewIntent.Approve(created.review.context()),
                IdempotencyKey("host-human-approval"),
            )
        } as ConversationReviewDecision.Saved
        return accepted.review
    }

    private fun host(
        workspace: ConversationWorkspaceStore,
        reality: RealityStore,
        active: Supplier<String>,
        ready: BooleanSupplier,
    ) = FaroV5ActiveReviewedApplySession(
        "generation-host", workspace, reality, SingleProcessMaterialWriteGate(),
        active, ready,
    )

    @Test fun approvedPlanCommitsThroughRealHostAndRestoresWithoutReplay() {
        var current = "generation-host"
        var ready = true
        RealityStore(db("reality.db"),
            transactionIdFactory = { TransactionId("tx-host") },
            receiptIdFactory = { ReceiptId("receipt-host") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val review = approved(workspace, prepared(reality))
                val session = host(workspace, reality, Supplier { current }, BooleanSupplier { ready })
                assertEquals(
                    ConversationApplyHostOutcome.Denied("v2_not_certified"),
                    call { session.port.request(review.context()) },
                )
                assertNull(reality.read().displayName)
                current = "stale-generation"
                assertThrows(IllegalStateException::class.java) {
                    session.executeAfterHumanRequest(review.context())
                }
                current = "generation-host"
                ready = false
                assertThrows(IllegalStateException::class.java) {
                    session.executeAfterHumanRequest(review.context())
                }
                assertNull(reality.read().displayName)
                ready = true
                val committed = session.executeAfterHumanRequest(review.context())
                    as ConversationApplyHostOutcome.Committed
                assertEquals(ConversationPlanReviewState.COMMITTED, committed.review.state)
                assertEquals(committed.receipt, committed.review.receipt)
                assertEquals("FARO Verified", reality.read().displayName!!.value)
                assertEquals(1L, reality.read().revision)
                assertFalse(workspace.snapshot().workspace.conversations.single().hasPendingReview)
            }
        }
        RealityStore(db("reality.db"),
            transactionIdFactory = { error("duplicate transaction forbidden") },
            receiptIdFactory = { error("duplicate receipt forbidden") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val restored = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.COMMITTED, restored.state)
                assertEquals(restored.receipt,
                    reality.executionReceiptLookup.find(restored.transactionId!!))
                val session = host(workspace, reality, Supplier { "generation-host" }, BooleanSupplier { true })
                assertEquals(
                    ConversationApplyHostOutcome.Denied("approved_context_unavailable"),
                    session.executeAfterHumanRequest(restored.context()),
                )
                assertEquals(1L, reality.read().revision)
            }
        }
    }

    @Test fun hostRecoversCrashAfterRealityCommitBeforeWorkspaceDelivery() {
        lateinit var identity: ApplyAttemptIdentity
        RealityStore(db("reality-crash.db"),
            transactionIdFactory = { TransactionId("tx-host-crash") },
            receiptIdFactory = { ReceiptId("receipt-host-crash") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace-crash.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val plan = prepared(reality)
                val review = approved(workspace, plan)
                identity = reality.applyAttemptIdentities.resolve(plan)
                val started = workspace.advance(
                    ConversationPlanReviewIntent.BeginApply(review.context(), identity),
                    IdempotencyKey("host-begin-before-crash"),
                    reality.applyAttemptLookup, reality.executionReceiptLookup,
                ) as ConversationReviewDecision.Saved
                assertEquals(ConversationPlanReviewState.APPLYING, started.review.state)
                val binding = ApplyBindingResolutionGate(
                    listOf(ProfileUpdateDeclaredBinding(reality)),
                ).resolve(plan) as ApplyBindingResolution.Resolved
                val executed = ApplyTransactionExecutor(reality, reality).execute(
                    plan, identity, binding,
                ) as ApplyTransactionExecution.Completed
                assertEquals(identity.transactionId, executed.receipt.transactionId)
                // Process death: no Workspace ObserveCommitted yet.
            }
        }
        RealityStore(db("reality-crash.db"),
            transactionIdFactory = { error("must not allocate a second attempt") },
            receiptIdFactory = { error("must not issue a second receipt") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                db("workspace-crash.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val applying = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.APPLYING, applying.state)
                val session = host(workspace, reality,
                    Supplier { "generation-host" }, BooleanSupplier { true })
                val recovered = session.executeAfterHumanRequest(applying.context())
                    as ConversationApplyHostOutcome.Committed
                assertEquals(identity.transactionId, recovered.receipt.transactionId)
                assertEquals(ConversationPlanReviewState.COMMITTED, recovered.review.state)
                assertEquals(recovered.receipt, recovered.review.receipt)
                assertEquals(1L, reality.read().revision)
                assertFalse(workspace.snapshot().workspace.conversations.single().hasPendingReview)
            }
        }
    }
}
