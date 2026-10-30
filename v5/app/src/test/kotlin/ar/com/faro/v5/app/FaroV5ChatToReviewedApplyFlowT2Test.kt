package ar.com.faro.v5.app

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
 * F1 directed cross-boundary flow: real generation-scoped Composer -> Workspace
 * timeline -> neutral model reply -> human Review -> trusted Apply -> Reality receipt.
 * No live provider, Android UI E2E or V2 activation is inferred.
 * The shipping UI Apply port remains denied while V2_ACTIVE_ENABLED=false.
 * This is NOT Android UI E2E, OS delivery, activation, or T4 certification.
 */
class FaroV5ChatToReviewedApplyFlowT2Test {
    @TempDir lateinit var root: Path
    private val chat = ConversationId("flow-chat")
    private fun db(name: String) = root.resolve(name).toString()

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
            IdempotencyKey("flow-profile-operation"),
        )
        return PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"), operation, emptySet()))
    }

    @Test fun composerToHumanReviewToTrustedReceiptSurvivesReopenWithoutReplay() {
        val generation = "flow-generation"
        val pointer = Supplier { generation }
        val ready = BooleanSupplier { true }
        val gate = SingleProcessMaterialWriteGate()

        RealityStore(
            db("reality.db"),
            transactionIdFactory = { TransactionId("flow-transaction") },
            receiptIdFactory = { ReceiptId("flow-receipt") },
        ).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val selection = FaroV5ActiveChatSelectionSession(
                    generation, workspace, gate, pointer, ready,
                )
                assertTrue(call {
                    selection.port.createAndSelect(chat, 10L, IdempotencyKey("flow-create"))
                } is ConversationSelectionResult.Saved)

                val composer = FaroV5ActiveChatComposerSession(
                    generation, workspace, gate, pointer, ready,
                )
                val draft = requireNotNull(call { composer.port.read(chat) })
                val saved = call {
                    composer.port.save(ConversationDraftSaveRequest(
                        chat, draft.authorityToken, draft.revision,
                        "Quiero actualizar mi perfil", IdempotencyKey("flow-save"),
                    ))
                } as ConversationDraftWriteResult.Saved
                val sent = call {
                    composer.port.sendDraft(ConversationDraftSendRequest(
                        ConversationDraftSendCandidate(
                            chat, saved.snapshot.authorityToken,
                            saved.snapshot.revision, saved.snapshot.text,
                        ),
                        ConversationMessageId("flow-message"),
                        ConversationTurnId("flow-turn"),
                        1710000000000L, IdempotencyKey("flow-send"),
                    ))
                } as ConversationTimelineWriteResult.Saved
                assertFalse(sent.replayed)

                // The same authoritative Workspace turn is consumed by a trusted,
                // explicitly admitted model seam. No provider credentials, UI
                // Apply capability or alternate chat database are introduced.
                val model = ModelGateway { reasoning, _ ->
                    assertEquals("Quiero actualizar mi perfil",
                        String(reasoning.input.copyBytes(), Charsets.UTF_8))
                    ReasoningResult.Success(
                        ModelBindingId("fake-local"),
                        CanonicalPayload.fromEncoded(
                            "text/plain", "FARO preparó una respuesta".toByteArray(Charsets.UTF_8)
                        ),
                    )
                }
                val assistant = FaroV5ActiveChatReasoningSession(
                    generation, workspace, gate, pointer, ready, model, { true },
                )
                assertEquals(
                    FaroV5AssistantTurnOutcome.COMPLETED,
                    assistant.run(
                        FaroV5AssistantTurnRequest(
                            chat, ConversationMessageId("flow-message"),
                            ConversationTurnId("flow-turn"), ConversationMessageId("flow-faro"),
                            ModelProfileId("test-profile"), 1710000000001L,
                            IdempotencyKey("flow-start-faro"),
                            IdempotencyKey("flow-resolve-faro"),
                        ),
                        NeverCancelled,
                    ),
                )

                val read = FaroV5ActiveChatReadSession(
                    generation, workspace, workspace, pointer, ready,
                )
                assertEquals(
                    listOf("Quiero actualizar mi perfil", "FARO preparó una respuesta"),
                    call { read.timelineReadPort.readTimeline(chat) }!!.messages.map { it.text },
                )

                val plan = prepared(reality)
                val token = requireNotNull(call { workspace.read(chat) }).authorityToken
                val pending = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                    chat, "flow-review", token, workspace.snapshot().revision,
                    plan.planFingerprint, "Actualizar perfil",
                    PreparedPlanEnvelopeCodec.encode(plan),
                    IdempotencyKey("flow-create-review"),
                )) as WorkspacePlanReviewWriteResult.Saved
                val review = FaroV5ActiveChatReviewSession(
                    generation, workspace, gate, pointer, ready,
                )
                val approved = call {
                    review.port.decide(
                        ConversationPlanReviewIntent.Approve(pending.review.context()),
                        IdempotencyKey("flow-approve"),
                    )
                } as ConversationReviewDecision.Saved

                val apply = FaroV5ActiveReviewedApplySession(
                    generation, workspace, reality, gate, pointer, ready,
                )
                assertEquals(
                    ConversationApplyHostOutcome.Denied("v2_not_certified"),
                    call { apply.port.request(approved.review.context()) },
                )
                assertNull(reality.read().displayName)

                // Trusted internal seam only; shipping UI port stays disabled.
                val committed = apply.executeAfterHumanRequest(approved.review.context())
                    as ConversationApplyHostOutcome.Committed
                assertEquals("FARO Verified", reality.read().displayName!!.value)
                assertEquals(committed.receipt, committed.review.receipt)
                assertEquals(ConversationPlanReviewState.COMMITTED,
                    call { review.port.reviews(chat) }.single().state)
                assertEquals(1L, reality.read().revision)
            }
        }

        RealityStore(
            db("reality.db"),
            transactionIdFactory = { error("unexpected second transaction") },
            receiptIdFactory = { error("unexpected second receipt") },
        ).use { reality ->
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                db("workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                assertEquals("FARO Verified", reality.read().displayName!!.value)
                assertEquals(1L, reality.read().revision)
                assertEquals(
                    listOf("Quiero actualizar mi perfil", "FARO preparó una respuesta"),
                    call { workspace.readTimeline(chat) }!!.messages.map { it.text },
                )
                val restored = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.COMMITTED, restored.state)
                assertEquals(
                    restored.receipt,
                    reality.executionReceiptLookup.find(restored.transactionId!!),
                )
                assertFalse(workspace.snapshot().workspace.conversations.single().hasPendingReview)
            }
        }
    }
}
