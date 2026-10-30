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

    @Test fun unknownUserMessageCannotMintFaroReply() {
        ConversationWorkspaceStore(db()).use { store ->
            seed(store)
            assertEquals(FaroV5AssistantTurnOutcome.INVALID_TURN,
                session(store).run(request.copy(userMessageId = ConversationMessageId("unknown")),
                    NeverCancelled))
            assertEquals(0, calls.get())
        }
    }
}
