package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Durable A->B->A must never revive an old Chat capability, with no UI listener. */
class FaroV5ActiveHostPointerABATest {
    @TempDir lateinit var root: Path

    @Test fun staleComposerSelectionReviewAndApplyFailClosedAfterDurableABA(): Unit {
        val generation = StorageGenerationId("flow-aba-generation")
        val other = StorageGenerationId("flow-aba-other")
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val currentId = Supplier { pointer.read()?.generationId?.value ?: "" }
        val sameDurablePointer = BooleanSupplier { pointer.read() == pinned }
        val ready = BooleanSupplier { true }
        val gate = SingleProcessMaterialWriteGate()
        val chat = ConversationId("aba-chat")

        RealityStore(root.resolve("aba-reality.db").toString()).use { reality ->
            ConversationWorkspaceStore.withPlanVerifier(
                root.resolve("aba-workspace.db").toString(),
                CanonicalPreparedPlanEnvelopeVerifier,
            ).use { workspace ->
                val selection = FaroV5ActiveChatSelectionSession(
                    generation.value, workspace, gate, currentId, ready, sameDurablePointer,
                )
                val composer = FaroV5ActiveChatComposerSession(
                    generation.value, workspace, gate, currentId, ready, sameDurablePointer,
                )
                val review = FaroV5ActiveChatReviewSession(
                    generation.value, workspace, gate, currentId, ready, sameDurablePointer,
                )
                val apply = FaroV5ActiveReviewedApplySession(
                    generation.value, workspace, reality, gate, currentId, ready, sameDurablePointer,
                )
                assertTrue(runBlocking {
                    selection.port.createAndSelect(chat, 1L, IdempotencyKey("aba-create"))
                } is ConversationSelectionResult.Saved)
                val draft = requireNotNull(runBlocking { composer.port.read(chat) })
                assertTrue(runBlocking { review.port.reviews(chat) }.isEmpty())

                assertNotNull(pointer.compareAndSet(generation, other))
                val returned = requireNotNull(pointer.compareAndSet(other, generation))
                assertTrue(returned.sequence > pinned.sequence)
                assertEquals(generation.value, currentId.get())
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { selection.port.select(chat, IdempotencyKey("aba-stale-select")) }
                }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { composer.port.read(chat) }
                }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { composer.port.save(ConversationDraftSaveRequest(
                        chat, draft.authorityToken, draft.revision,
                        "never written", IdempotencyKey("aba-stale-save"),
                    )) }
                }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { review.port.reviews(chat) }
                }
                val fakeContext = ConversationPlanReviewContext(
                    chat, "aba-review", "aba-authority", 0L, PlanFingerprint("aba-plan"),
                )
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { review.port.decide(
                        ConversationPlanReviewIntent.Approve(fakeContext),
                        IdempotencyKey("aba-stale-review"),
                    ) }
                }
                assertThrows(IllegalStateException::class.java) {
                    apply.executeAfterHumanRequest(fakeContext)
                }
                assertNull(reality.read().displayName)
            }
        }
    }
}
