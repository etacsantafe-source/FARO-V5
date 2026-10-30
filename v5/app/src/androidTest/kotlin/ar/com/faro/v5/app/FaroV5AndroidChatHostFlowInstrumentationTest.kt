package ar.com.faro.v5.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F1 Android instrumentation: real Android SQLite Workspace, Composer,
 * host-owned asynchronous model dispatch, timeline persistence and reopen.
 *
 * Synthetic local model only: no network, API key, V2 cutover, active pointer,
 * material Apply or OS delivery. This is NOT full F1 UI E2E or physical T4.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5AndroidChatHostFlowInstrumentationTest {
    @Test
    fun acceptedComposerTurnReachesOneModelReplyAndSurvivesAndroidReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(
            context.noBackupFilesDir, "faro-f1-android-flow-" + UUID.randomUUID()
        )
        assertTrue(directory.mkdirs())
        val database = File(directory, "workspace.db").absolutePath
        val generation = "f1-android-isolated"
        val chat = ConversationId("f1-android-chat")
        val pointer = Supplier { generation }
        val ready = BooleanSupplier { true }
        val gate = SingleProcessMaterialWriteGate()
        val modelCalls = AtomicInteger(0)
        try {
            ConversationWorkspaceStore.withPlanVerifier(
                database, CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val selection = FaroV5ActiveChatSelectionSession(
                    generation, workspace, gate, pointer, ready,
                )
                assertTrue(selection.port.createAndSelect(
                    chat, 1710000000000L, IdempotencyKey("android-create"),
                ) is ConversationSelectionResult.Saved)

                val composer = FaroV5ActiveChatComposerSession(
                    generation, workspace, gate, pointer, ready,
                )
                val draft = requireNotNull(composer.port.read(chat))
                val saved = composer.port.save(ConversationDraftSaveRequest(
                    chat, draft.authorityToken, draft.revision,
                    "Hola FARO", IdempotencyKey("android-save"),
                )) as ConversationDraftWriteResult.Saved
                val accepted = composer.port.sendDraft(ConversationDraftSendRequest(
                    ConversationDraftSendCandidate(
                        chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                        saved.snapshot.text,
                    ),
                    ConversationMessageId("android-user"),
                    ConversationTurnId("android-turn"),
                    1710000000001L, IdempotencyKey("android-send"),
                )) as ConversationTimelineWriteResult.Saved
                assertFalse(accepted.replayed)

                val gateway = ModelGateway { request, _ ->
                    modelCalls.incrementAndGet()
                    assertEquals(ModelProfileId("android-synthetic"), request.profileId)
                    assertEquals("Hola FARO", String(
                        request.input.copyBytes(), Charsets.UTF_8,
                    ))
                    ReasoningResult.Success(
                        ModelBindingId("synthetic-no-network"),
                        CanonicalPayload.fromEncoded(
                            "text/plain", "Hola desde FARO".toByteArray(Charsets.UTF_8),
                        ),
                    )
                }
                val reasoning = FaroV5ActiveChatReasoningSession(
                    generation, workspace, gate, pointer, ready, gateway, { true },
                )
                val dispatch = FaroV5HostAcceptedTurnDispatchPort(
                    FaroV5HostAssistantTurnDispatcher(
                        reasoning,
                        Executors.newSingleThreadExecutor(),
                        ready,
                    ),
                    Supplier { ModelProfileId("android-synthetic") },
                    ready,
                )
                val turn = ConversationAcceptedUserTurn(
                    chat, ConversationMessageId("android-user"),
                    ConversationTurnId("android-turn"), 1710000000001L,
                )
                try {
                    repeat(2) {
                        val completed = CountDownLatch(1)
                        assertTrue(dispatch.dispatchAcceptedTurn(turn) {
                            completed.countDown()
                        })
                        assertTrue("Assistant callback timeout",
                            completed.await(30, TimeUnit.SECONDS))
                    }
                    assertEquals("Exactly one provider request per accepted turn",
                        1, modelCalls.get())
                } finally {
                    dispatch.close()
                }
                val timeline = FaroV5ActiveChatReadSession(
                    generation, workspace, workspace, pointer, ready,
                ).timelineReadPort.readTimeline(chat)
                assertEquals(
                    listOf("Hola FARO", "Hola desde FARO"),
                    requireNotNull(timeline).messages.map { it.text },
                )
                assertEquals(
                    listOf(ConversationMessageState.ACCEPTED,
                        ConversationMessageState.COMPLETED),
                    timeline.messages.map { it.state },
                )
            }
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                database, CanonicalPreparedPlanEnvelopeVerifier,
            ).use { reopened ->
                val recovered = requireNotNull(reopened.readTimeline(chat))
                assertEquals(
                    listOf("Hola FARO", "Hola desde FARO"),
                    recovered.messages.map { it.text },
                )
                assertEquals(1, modelCalls.get())
                assertFalse("Shipping V2 remains disabled",
                    FaroV5HostCertification.V2_ACTIVE_ENABLED)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
