package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.workspace.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Directed Linux JVM/Workspace test; NOT Android E2E or live provider execution. */
class FaroV5ActiveChatReasoningSessionT2Test {
    @TempDir lateinit var root: Path
    private val chat = ConversationId("chat-f1")
    private val turn = ConversationTurnId("turn-f1")
    private val userId = ConversationMessageId("user-f1")
    private val faroId = ConversationMessageId("faro-f1")
    private val active = AtomicReference("gen-f1")
    private val calls = AtomicInteger(0)
    private val gate = SingleProcessMaterialWriteGate()
    private val request = FaroV5AssistantTurnRequest(
        chat, userId, turn, faroId, ModelProfileId("balanced"), 200L,
        IdempotencyKey("start-f1"), IdempotencyKey("resolve-f1"),
    )
    private fun db() = root.resolve("workspace.db").toString()
    private fun seed(store: ConversationWorkspaceStore) {
        assertTrue(runBlocking {
            store.createAndSelect(chat, 100L, IdempotencyKey("create-f1"))
        } is ConversationSelectionResult.Saved)
        val before = runBlocking { store.readTimeline(chat) }!!
        assertTrue(runBlocking {
            store.writeTimeline(ConversationTimelineWriteRequest(
                chat, before.authorityToken, before.revision, IdempotencyKey("accept-f1"),
                ConversationTimelineIntent.AcceptUser(ConversationMessage(
                    userId, chat, turn, ConversationMessageRole.USER,
                    ConversationMessageState.ACCEPTED, "Hola FARO", 150L,
                )),
            ))
        } is ConversationTimelineWriteResult.Saved)
    }
    private fun session(
        store: ConversationWorkspaceStore,
        allowed: Boolean = true,
        model: ModelGateway = ModelGateway { _, _ ->
            calls.incrementAndGet()
            ReasoningResult.Success(ModelBindingId("fake-local"), CanonicalPayload.fromEncoded(
                "text/plain", "Respuesta FARO".toByteArray(StandardCharsets.UTF_8)
            ))
        },
    ) = FaroV5ActiveChatReasoningSession(
        "gen-f1", store, gate, Supplier { active.get() },
        BooleanSupplier { true }, model, { allowed },
    )

    @Test fun acceptedUserTurnGetsOneDurableReplyAndNoModelReplayAfterReopen() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                session(store).run(request, NeverCancelled))
            assertEquals(FaroV5AssistantTurnOutcome.ALREADY_RECORDED,
                session(store).run(request, NeverCancelled))
            assertEquals(1, calls.get())
            val messages = runBlocking { store.readTimeline(chat) }!!.messages
            assertEquals(listOf(ConversationMessageRole.USER, ConversationMessageRole.FARO),
                messages.map { it.role })
            assertEquals("Respuesta FARO", messages.last().text)
        }
        ConversationWorkspaceStore(db()).use { reopened ->
            assertEquals(FaroV5AssistantTurnOutcome.ALREADY_RECORDED,
                session(reopened).run(request, NeverCancelled))
            assertEquals(1, calls.get())
        }
    }

    @Test fun missingTrustedEgressAdmissionNeverCallsModelOrCreatesAssistant() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            assertEquals(FaroV5AssistantTurnOutcome.NOT_AUTHORIZED,
                session(store, allowed = false).run(request, NeverCancelled))
            assertEquals(0, calls.get())
            assertEquals(1, runBlocking { store.readTimeline(chat) }!!.messages.size)
        }
    }

    @Test fun offlineFailureIsTypedAndContainsNoInventedText() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val offline = ModelGateway { _, _ ->
                calls.incrementAndGet()
                ReasoningResult.Failure(AiFailure(
                    AiFailureKind.OFFLINE, RetryDisposition.RETRYABLE
                ))
            }
            assertEquals(FaroV5AssistantTurnOutcome.OFFLINE,
                session(store, model = offline).run(request, NeverCancelled))
            val reply = runBlocking { store.readTimeline(chat) }!!.messages.last()
            assertEquals(ConversationMessageState.OFFLINE, reply.state)
            assertEquals("", reply.text)
        }
    }

    @Test fun pointerSwitchDuringModelLeavesProcessingForRecoveryWithoutReplay() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val switching = ModelGateway { _, _ ->
                calls.incrementAndGet()
                active.set("other-gen")
                ReasoningResult.Success(ModelBindingId("fake"), CanonicalPayload.fromEncoded(
                    "text/plain", "No debe publicarse".toByteArray(StandardCharsets.UTF_8)
                ))
            }
            assertEquals(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED,
                session(store, model = switching).run(request, NeverCancelled))
            assertEquals(ConversationMessageState.PROCESSING,
                runBlocking { store.readTimeline(chat) }!!.messages.last().state)
            active.set("gen-f1")
            assertEquals(FaroV5AssistantTurnOutcome.IN_PROGRESS,
                session(store, model = switching).run(request, NeverCancelled))
            assertEquals(1, calls.get())
        }
    }

    @Test fun anotherConversationCanAdvanceGlobalRevisionWhileModelIsRunning() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val interleaved = ModelGateway { _, _ ->
                calls.incrementAndGet()
                assertTrue(runBlocking {
                    store.createAndSelect(
                        ConversationId("other-chat"), 300L, IdempotencyKey("other-create")
                    )
                } is ConversationSelectionResult.Saved)
                ReasoningResult.Success(ModelBindingId("fake"), CanonicalPayload.fromEncoded(
                    "text/plain", "Respuesta al chat original".toByteArray(StandardCharsets.UTF_8)
                ))
            }
            assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                session(store, model = interleaved).run(request, NeverCancelled))
            assertEquals("Respuesta al chat original",
                runBlocking { store.readTimeline(chat) }!!.messages.last().text)
            assertEquals(1, calls.get())
        }
    }

    @Test fun revokedEgressAfterDurableStartNeverCallsModel() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val checks = AtomicInteger(0)
            val session = FaroV5ActiveChatReasoningSession(
                "gen-f1", store, gate, Supplier { active.get() },
                BooleanSupplier { true },
                ModelGateway { _, _ ->
                    calls.incrementAndGet()
                    error("model must not run after consent is revoked")
                },
                { checks.incrementAndGet() == 1 },
            )
            assertEquals(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED,
                session.run(request, NeverCancelled))
            assertEquals(0, calls.get())
            assertEquals(2, checks.get())
            assertEquals(ConversationMessageState.PROCESSING,
                runBlocking { store.readTimeline(chat) }!!.messages.last().state)
        }
    }

    @Test fun concurrentRequestsForSameTurnNeverCallModelTwice() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val model = ModelGateway { _, _ ->
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "model test release timed out" }
                ReasoningResult.Success(ModelBindingId("fake"), CanonicalPayload.fromEncoded(
                    "text/plain", "Única respuesta".toByteArray(StandardCharsets.UTF_8)
                ))
            }
            val session = session(store, model = model)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first = pool.submit<FaroV5AssistantTurnOutcome> {
                    session.run(request, NeverCancelled)
                }
                assertTrue(entered.await(20, TimeUnit.SECONDS))
                val second = pool.submit<FaroV5AssistantTurnOutcome> {
                    session.run(request, NeverCancelled)
                }
                assertEquals(FaroV5AssistantTurnOutcome.IN_PROGRESS,
                    second.get(20, TimeUnit.SECONDS))
                release.countDown()
                assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                    first.get(20, TimeUnit.SECONDS))
                assertEquals(1, calls.get())
                assertEquals(2, runBlocking { store.readTimeline(chat) }!!.messages.size)
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }


    @Test fun interruptedReplyRequiresExclusiveRecoveryAndNeverReplaysModelAfterReopen() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val interrupted = ModelGateway { _, _ ->
                calls.incrementAndGet()
                active.set("other-gen")
                ReasoningResult.Success(ModelBindingId("fake"), CanonicalPayload.fromEncoded(
                    "text/plain", "Uncommitted reply".toByteArray(StandardCharsets.UTF_8)
                ))
            }
            assertEquals(FaroV5AssistantTurnOutcome.RECOVERY_REQUIRED,
                session(store, model = interrupted).run(request, NeverCancelled))
            assertEquals(ConversationMessageState.PROCESSING,
                runBlocking { store.readTimeline(chat) }!!.messages.last().state)
        }
        active.set("gen-f1")
        ConversationWorkspaceStore(db()).use { reopened ->
            val recovery = session(reopened)
            assertEquals(FaroV5AssistantTurnOutcome.NOT_AUTHORIZED,
                recovery.settleInterruptedAfterRecovery(request, BooleanSupplier { false }))
            assertEquals(ConversationMessageState.PROCESSING,
                runBlocking { reopened.readTimeline(chat) }!!.messages.last().state)
            assertEquals(FaroV5AssistantTurnOutcome.FAILED,
                recovery.settleInterruptedAfterRecovery(request, BooleanSupplier { true }))
            assertEquals(FaroV5AssistantTurnOutcome.ALREADY_RECORDED,
                recovery.settleInterruptedAfterRecovery(request, BooleanSupplier { true }))
            assertEquals(FaroV5AssistantTurnOutcome.ALREADY_RECORDED,
                recovery.run(request, NeverCancelled))
            assertEquals(1, calls.get())
            val reply = runBlocking { reopened.readTimeline(chat) }!!.messages.last()
            assertEquals(ConversationMessageState.FAILED, reply.state)
            assertEquals("", reply.text)
        }
    }

    @Test fun unknownUserMessageCannotMintFaroReply() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            assertEquals(FaroV5AssistantTurnOutcome.INVALID_TURN,
                session(store).run(request.copy(userMessageId = ConversationMessageId("unknown")),
                    NeverCancelled))
            assertEquals(0, calls.get())
        }
    }

    @Test fun hostDispatcherRunsModelOffCallerThreadAndSharesInFlightTurn() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val workerThread = AtomicReference("")
            val callerThread = Thread.currentThread().name
            val model = ModelGateway { _, _ ->
                workerThread.set(Thread.currentThread().name)
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS))
                ReasoningResult.Success(ModelBindingId("fake"), CanonicalPayload.fromEncoded(
                    "text/plain", "Respuesta en segundo plano".toByteArray(StandardCharsets.UTF_8)
                ))
            }
            val dispatcher = FaroV5HostAssistantTurnDispatcher(
                session(store, model = model), Executors.newSingleThreadExecutor(),
                BooleanSupplier { active.get() == "gen-f1" },
            )
            try {
                val first = dispatcher.submit(request)
                assertTrue(entered.await(20, TimeUnit.SECONDS))
                val duplicate = dispatcher.submit(request)
                assertSame(first, duplicate)
                assertFalse(first.isDone)
                release.countDown()
                assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                    first.get(20, TimeUnit.SECONDS))
                assertNotEquals(callerThread, workerThread.get())
                assertEquals(1, calls.get())
                assertEquals(ConversationMessageState.COMPLETED,
                    runBlocking { store.readTimeline(chat) }!!.messages.last().state)
            } finally {
                release.countDown()
                dispatcher.close()
            }
        }
    }

    @Test fun hostDispatcherDeniesStaleOrClosedGenerationWithoutModelCall() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            val dispatcher = FaroV5HostAssistantTurnDispatcher(
                session(store), Executors.newSingleThreadExecutor(),
                BooleanSupplier { active.get() == "gen-f1" },
            )
            try {
                active.set("other-gen")
                assertEquals(FaroV5AssistantTurnOutcome.STALE,
                    dispatcher.submit(request).get(20, TimeUnit.SECONDS))
                active.set("gen-f1")
                dispatcher.close()
                assertEquals(FaroV5AssistantTurnOutcome.STALE,
                    dispatcher.submit(request).get(20, TimeUnit.SECONDS))
                assertEquals(0, calls.get())
                assertEquals(1, runBlocking { store.readTimeline(chat) }!!.messages.size)
            } finally {
                dispatcher.close()
            }
        }
    }
    private fun seedHistoryAndLaterTurn(store: ConversationWorkspaceStore) {
        seed(store)
        fun append(intent: ConversationTimelineIntent, key: String) {
            val before = runBlocking { store.readTimeline(chat) }!!
            assertTrue(runBlocking {
                store.writeTimeline(ConversationTimelineWriteRequest(
                    chat, before.authorityToken, before.revision,
                    IdempotencyKey(key), intent,
                ))
            } is ConversationTimelineWriteResult.Saved)
        }
        append(ConversationTimelineIntent.StartFaro(ConversationMessage(
            ConversationMessageId("history-reply"), chat, turn,
            ConversationMessageRole.FARO, ConversationMessageState.PROCESSING,
            "", 160L,
        )), "history-start")
        append(ConversationTimelineIntent.ResolveFaro(
            ConversationMessageId("history-reply"), turn,
            ConversationMessageState.COMPLETED, "Respuesta anterior",
        ), "history-resolve")
        append(ConversationTimelineIntent.AcceptUser(ConversationMessage(
            ConversationMessageId("user-second"), chat, ConversationTurnId("turn-second"),
            ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
            "Segunda pregunta", 180L,
        )), "history-second")
        append(ConversationTimelineIntent.AcceptUser(ConversationMessage(
            ConversationMessageId("user-future"), chat, ConversationTurnId("turn-future"),
            ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
            "MENSAJE FUTURO", 190L,
        )), "history-future")
    }

    private fun secondRequest() = request.copy(
        userMessageId = ConversationMessageId("user-second"),
        turnId = ConversationTurnId("turn-second"),
        faroMessageId = ConversationMessageId("faro-second"),
        startKey = IdempotencyKey("start-second"),
        resolveKey = IdempotencyKey("resolve-second"),
    )

    @Test fun trustedMultiTurnIncludesOnlyEarlierSameChatMessages() {
        ConversationWorkspaceStore(db()).use { store ->
            seedHistoryAndLaterTurn(store)
            val captured = AtomicReference("")
            val model = ModelGateway { reasoning, _ ->
                calls.incrementAndGet()
                captured.set(String(reasoning.input.copyBytes(), StandardCharsets.UTF_8))
                ReasoningResult.Success(ModelBindingId("fake-local"),
                    CanonicalPayload.fromEncoded("text/plain",
                        "Respuesta segunda".toByteArray(StandardCharsets.UTF_8)))
            }
            assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                session(store, model = model).run(secondRequest(), NeverCancelled))
            val input = captured.get()
            assertTrue(input.contains("USUARIO:\nHola FARO"))
            assertTrue(input.contains("FARO:\nRespuesta anterior"))
            assertTrue(input.contains("USUARIO:\nSegunda pregunta"))
            assertFalse(input.contains("MENSAJE FUTURO"))
            assertEquals(1, calls.get())
        }
    }

    @Test fun deniedHistoricalUserAlsoSuppressesItsDerivedAssistantReply() {
        ConversationWorkspaceStore(db()).use { store ->
            seedHistoryAndLaterTurn(store)
            val captured = AtomicReference("")
            val model = ModelGateway { reasoning, _ ->
                calls.incrementAndGet()
                captured.set(String(reasoning.input.copyBytes(), StandardCharsets.UTF_8))
                ReasoningResult.Success(ModelBindingId("fake-local"),
                    CanonicalPayload.fromEncoded("text/plain",
                        "Respuesta privada".toByteArray(StandardCharsets.UTF_8)))
            }
            val reasoning = FaroV5ActiveChatReasoningSession(
                "gen-f1", store, gate, Supplier { active.get() },
                BooleanSupplier { true }, model,
                { it.id != userId },
            )
            assertEquals(FaroV5AssistantTurnOutcome.COMPLETED,
                reasoning.run(secondRequest(), NeverCancelled))
            assertEquals("Segunda pregunta", captured.get())
            assertEquals(1, calls.get())
        }
    }

}
