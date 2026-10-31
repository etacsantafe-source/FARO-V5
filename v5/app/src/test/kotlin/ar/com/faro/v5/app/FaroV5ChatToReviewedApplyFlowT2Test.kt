package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
                val modelCalls = AtomicInteger(0)
                val model = ModelGateway { reasoning, _ ->
                    modelCalls.incrementAndGet()
                    assertEquals(ModelProfileId("test-profile"), reasoning.profileId)
                    assertEquals("Quiero actualizar mi perfil",
                        String(reasoning.input.copyBytes(), Charsets.UTF_8))
                    ReasoningResult.Success(
                        ModelBindingId("fake-local"),
                        CanonicalPayload.fromEncoded(
                            "text/plain", "{\"type\":\"profile.update_declared\",\"display_name\":\"FARO Verified\"}".toByteArray(Charsets.UTF_8)
                        ),
                    )
                }
                val assistant = FaroV5ActiveChatReasoningSession(
                    generation, workspace, gate, pointer, ready, model, { true },
                )
                val bridge = FaroV5ModelReviewBridge(
                    generation, workspace, reality, gate, pointer, ready,
                )
                val dispatch = FaroV5HostAcceptedTurnDispatchPort(
                    FaroV5HostAssistantTurnDispatcher(
                        assistant, Executors.newSingleThreadExecutor(), ready,
                        { conversationId, turnId -> bridge.propose(conversationId, turnId) },
                    ),
                    Supplier { ModelProfileId("test-profile") }, ready,
                )
                val accepted = ConversationAcceptedUserTurn(
                    chat, ConversationMessageId("flow-message"),
                    ConversationTurnId("flow-turn"), 1710000000000L,
                )
                try {
                    val first = CountDownLatch(1)
                    assertTrue(dispatch.dispatchAcceptedTurn(accepted) { first.countDown() })
                    assertTrue(first.await(20, TimeUnit.SECONDS))
                    // Repeated UI delivery of the same committed receipt never
                    // triggers a second model call or a second FARO message.
                    val second = CountDownLatch(1)
                    assertTrue(dispatch.dispatchAcceptedTurn(accepted) { second.countDown() })
                    assertTrue(second.await(20, TimeUnit.SECONDS))
                    assertEquals(1, modelCalls.get())
                } finally {
                    dispatch.close()
                }

                val read = FaroV5ActiveChatReadSession(
                    generation, workspace, workspace, pointer, ready,
                )
                assertEquals(
                    listOf("Quiero actualizar mi perfil", "{\"type\":\"profile.update_declared\",\"display_name\":\"FARO Verified\"}"),
                    call { read.timelineReadPort.readTimeline(chat) }!!.messages.map { it.text },
                )

                // The model supplies only a bounded intent. The trusted host
                // reconstructs the plan, dependency stamp and fingerprint from
                // authoritative Reality and persists it to Workspace for review.
                val pending = call { workspace.reviews(chat) }.single()
                assertEquals(ConversationPlanReviewState.PENDING, pending.state)
                val replay = bridge.propose(chat, ConversationTurnId("flow-turn"))
                    as WorkspacePlanReviewWriteResult.Saved
                assertTrue(replay.replayed)
                assertEquals(ConversationPlanReviewState.PENDING, pending.state)
                // Model-driven proposal is NOT approval or an Apply effect.
                assertNull(reality.read().displayName)
                assertNull(bridge.propose(chat, ConversationTurnId("missing-turn")))
                val review = FaroV5ActiveChatReviewSession(
                    generation, workspace, gate, pointer, ready,
                )
                val approved = call {
                    review.port.decide(
                        ConversationPlanReviewIntent.Approve(pending.context()),
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
                    listOf("Quiero actualizar mi perfil", "{\"type\":\"profile.update_declared\",\"display_name\":\"FARO Verified\"}"),
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
    @Test fun forgedModelPlanMetadataNeverBecomesReviewOrRealityWrite() {
        val generation = "forged-generation"
        val pointer = Supplier { generation }
        val ready = BooleanSupplier { true }
        val gate = SingleProcessMaterialWriteGate()
        RealityStore(db("forged-reality.db")).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                db("forged-workspace.db"), CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                assertTrue(call {
                    workspace.createAndSelect(chat, 1L, IdempotencyKey("forged-create"))
                } is ConversationSelectionResult.Saved)
                val before = requireNotNull(call { workspace.readTimeline(chat) })
                val turn = ConversationTurnId("forged-turn")
                val user = ConversationMessageId("forged-user")
                assertTrue(call {
                    workspace.writeTimeline(ConversationTimelineWriteRequest(
                        chat, before.authorityToken, before.revision,
                        IdempotencyKey("forged-accept"),
                        ConversationTimelineIntent.AcceptUser(ConversationMessage(
                            user, chat, turn, ConversationMessageRole.USER,
                            ConversationMessageState.ACCEPTED, "Cambiar mi nombre", 2L,
                        )),
                    ))
                } is ConversationTimelineWriteResult.Saved)
                val gateway = ModelGateway { _, _ ->
                    ReasoningResult.Success(
                        ModelBindingId("synthetic"),
                        CanonicalPayload.fromEncoded(
                            "text/plain",
                            """{"type":"profile.update_declared","display_name":"X","planFingerprint":"forged"}"""
                                .toByteArray(Charsets.UTF_8),
                        ),
                    )
                }
                val assistant = FaroV5ActiveChatReasoningSession(
                    generation, workspace, gate, pointer, ready, gateway, { true },
                )
                assertEquals(
                    FaroV5AssistantTurnOutcome.COMPLETED,
                    assistant.run(
                        FaroV5AssistantTurnRequest(
                            chat, user, turn, ConversationMessageId("forged-faro"),
                            ModelProfileId("test-profile"), 3L,
                            IdempotencyKey("forged-start"), IdempotencyKey("forged-resolve"),
                        ),
                        NeverCancelled,
                    ),
                )
                val bridge = FaroV5ModelReviewBridge(
                    generation, workspace, reality, gate, pointer, ready,
                )
                assertNull(bridge.propose(chat, turn))
                assertTrue(call { workspace.reviews(chat) }.isEmpty())
                assertNull(reality.read().displayName)
            }
        }
    }

}
