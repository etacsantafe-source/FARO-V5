package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Native five-owner F1 integration under quarantine, not a V2 cutover.
 * No provider network, UI Apply, external delivery or physical T4.
 */
class FaroV5QuarantinedV2ChatHostBindingT2Test {
    @TempDir lateinit var root: Path

    private fun materialize(path: Path) {
        RealityStore(path.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(path.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(path.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(path.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(path.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
    }

    @Test fun fiveOwnerChatDispatchPersistsOnceAndClosesOnQuarantineLoss(): Unit = runBlocking {
        val stage = FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(StorageGenerationId("f1-quarantined"),
                "schema-five-store-1", 4L)
        )
        materialize(stage.directory)
        val active = Files.createDirectory(root.resolve("active-v1"))
        val activeRef = AtomicReference(active)
        val modelCalls = AtomicInteger(0)
        val gateway = ModelGateway { request, _ ->
            modelCalls.incrementAndGet()
            assertEquals(ModelProfileId("synthetic-local"), request.profileId)
            assertEquals("Hola FARO", String(request.input.copyBytes(), Charsets.UTF_8))
            ReasoningResult.Success(
                ModelBindingId("synthetic-no-network"),
                CanonicalPayload.fromEncoded(
                    "text/plain", "Respuesta local".toByteArray(Charsets.UTF_8)
                ),
            )
        }
        FaroV5QuarantinedV2ChatHostBinding.openForCertification(
            stage, SingleProcessMaterialWriteGate(),
            Supplier { activeRef.get() }, gateway,
            Supplier { ModelProfileId("synthetic-local") }, { true },
        ).use { host ->
            assertTrue(host.verifyQuarantined())
            val chat = ConversationId("five-owner-chat")
            assertTrue(host.selectionPort.createAndSelect(
                chat, 1710000000000L, IdempotencyKey("create")
            ) is ConversationSelectionResult.Saved)
            val draft = requireNotNull(host.composerPort.read(chat))
            val saved = host.composerPort.save(ConversationDraftSaveRequest(
                chat, draft.authorityToken, draft.revision,
                "Hola FARO", IdempotencyKey("save"),
            )) as ConversationDraftWriteResult.Saved
            val sent = host.composerPort.sendDraft(ConversationDraftSendRequest(
                ConversationDraftSendCandidate(
                    chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                    saved.snapshot.text,
                ),
                ConversationMessageId("user"), ConversationTurnId("turn"),
                1710000000001L, IdempotencyKey("send"),
            )) as ConversationTimelineWriteResult.Saved
            assertFalse(sent.replayed)
            val accepted = ConversationAcceptedUserTurn(
                chat, ConversationMessageId("user"),
                ConversationTurnId("turn"), 1710000000001L,
            )
            val completed = CountDownLatch(1)
            assertTrue(host.assistantTurnPort.dispatchAcceptedTurn(accepted) {
                completed.countDown()
            })
            assertTrue(completed.await(20, TimeUnit.SECONDS))
            assertEquals(1, modelCalls.get())
            val timeline = requireNotNull(host.timelineReadPort.readTimeline(chat))
            assertEquals(listOf("Hola FARO", "Respuesta local"),
                timeline.messages.map { it.text })
            assertEquals(listOf(ConversationMessageState.ACCEPTED,
                ConversationMessageState.COMPLETED),
                timeline.messages.map { it.state })
            assertTrue(host.reviewPort.reviews(chat).isEmpty())
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
            activeRef.set(stage.directory)
            assertFalse(host.verifyQuarantined())
            assertThrows(IllegalStateException::class.java) {
                runBlocking { host.composerPort.read(chat) }
            }
        }
    }
    @Test fun structuredModelReplyCreatesDurableHumanReviewWithoutApply(): Unit = runBlocking {
        val stage = FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(StorageGenerationId("f1-structured"),
                "schema-five-store-1", 4L)
        )
        materialize(stage.directory)
        val active = Files.createDirectory(root.resolve("active-structured-v1"))
        val modelCalls = AtomicInteger(0)
        val gateway = ModelGateway { _, _ ->
            modelCalls.incrementAndGet()
            ReasoningResult.Success(
                ModelBindingId("synthetic-no-network"),
                CanonicalPayload.fromEncoded(
                    "text/plain",
                    """{"type":"profile.update_declared","display_name":"Nuevo Nombre"}"""
                        .toByteArray(Charsets.UTF_8),
                ),
            )
        }
        val chat = ConversationId("structured-chat")
        val accepted = ConversationAcceptedUserTurn(
            chat, ConversationMessageId("structured-user"),
            ConversationTurnId("structured-turn"), 1710000000001L,
        )
        FaroV5QuarantinedV2ChatHostBinding.openForCertification(
            stage, SingleProcessMaterialWriteGate(), Supplier { active },
            gateway, Supplier { ModelProfileId("synthetic-local") }, { true },
        ).use { host ->
            assertTrue(host.selectionPort.createAndSelect(
                chat, 1710000000000L, IdempotencyKey("structured-create")
            ) is ConversationSelectionResult.Saved)
            val draft = requireNotNull(host.composerPort.read(chat))
            val saved = host.composerPort.save(ConversationDraftSaveRequest(
                chat, draft.authorityToken, draft.revision,
                "Cambiar mi nombre declarado", IdempotencyKey("structured-save"),
            )) as ConversationDraftWriteResult.Saved
            assertTrue(host.composerPort.sendDraft(ConversationDraftSendRequest(
                ConversationDraftSendCandidate(
                    chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                    saved.snapshot.text,
                ),
                accepted.userMessageId, accepted.turnId,
                accepted.acceptedAtEpochMillis, IdempotencyKey("structured-send"),
            )) is ConversationTimelineWriteResult.Saved)
            val first = CountDownLatch(1)
            assertTrue(host.assistantTurnPort.dispatchAcceptedTurn(accepted) {
                first.countDown()
            })
            assertTrue(first.await(20, TimeUnit.SECONDS))
            assertEquals(1, modelCalls.get())
            val reviews = host.reviewPort.reviews(chat)
            assertEquals(1, reviews.size)
            assertEquals(ConversationPlanReviewState.PENDING, reviews.single().state)
            assertEquals("Cambiar nombre declarado a «Nuevo Nombre»", reviews.single().reviewText)
            val again = CountDownLatch(1)
            assertTrue(host.assistantTurnPort.dispatchAcceptedTurn(accepted) {
                again.countDown()
            })
            assertTrue(again.await(20, TimeUnit.SECONDS))
            assertEquals(1, modelCalls.get())
            assertEquals(1, host.reviewPort.reviews(chat).size)
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        }
        RealityStore(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            assertNull(it.read().displayName)
        }
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            stage.directory.resolve(GenerationStoreFiles.WORKSPACE).toString(),
            ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier,
        ).use {
            assertEquals(ConversationPlanReviewState.PENDING,
                it.reviews(chat).single().state)
        }
    }

}
