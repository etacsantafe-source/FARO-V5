package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03MatterReviewHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selection = try {
            val start = StorageGenerationId("matter-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(start, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("matters-cutover"),
                StorageGenerationDescriptor(StorageGenerationId("matters-active"), "matters", 2L),
                requireNotNull(pointer.compareAndSet(null, start)), 3L,
            ) { true }
        } catch (failure: Throwable) {
            runtime.close()
            throw failure
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("matters-chat")
        var foreground = true
        var policyAllows = true
        val host = S4W03MatterReviewHost(
            selection.pointer, pointer, workspace, reality, gate, BuiltInContractCatalog.create(),
            "matter-test-principal", "epoch", { "epoch" }, { runtime.isCurrent(selection) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, _, _ -> if (policyAllows) "allow-current" else null },
        )

        init { select(chat) }

        fun select(conversation: ConversationId) = runBlocking {
            assertTrue(workspace.createAndSelect(
                conversation, 5L, IdempotencyKey("select-" + conversation.value),
            ) is ConversationSelectionResult.Saved)
        }

        fun form(title: String = "Trámite", due: Long? = 3000L) = MatterReviewForm(
            MatterTitle.of(title), MatterDetail.of("Documentos a presentar"),
            MatterPriority.HIGH, LocalDate.of(2027, 5, 1), due,
        )

        fun submit(
            key: String, change: MatterReviewChange = MatterReviewChange.Create(form()),
            revision: Long = reality.matterPersistence.read().revision,
            at: Long = 100 + revision,
            undoOf: Int? = null,
        ): ConversationPlanReviewSnapshot {
            val intent = MatterReviewIntent(MatterId.of("case"), revision, at, change, undoOf)
            return (host.submit(chat, intent, key) as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun decide(intent: ConversationPlanReviewIntent, key: String): ConversationPlanReviewSnapshot =
            (runBlocking { workspace.decide(intent, IdempotencyKey(key)) } as ConversationReviewDecision.Saved).review

        fun approve(review: ConversationPlanReviewSnapshot) =
            decide(ConversationPlanReviewIntent.Approve(review.context()), "approve-" + review.reviewId)

        fun apply(review: ConversationPlanReviewSnapshot) =
            host.executeAfterExplicitConfirmation(review.context())

        override fun close() {
            host.close()
            runtime.close()
        }
    }

    @Test fun createEditAndRealReceipts() = Rig().use { rig ->
        val pending = rig.submit("create")
        assertEquals(pending, rig.submit("create"))
        assertEquals(0L, rig.reality.matterPersistence.read().revision)
        assertTrue(rig.apply(pending) is ConversationApplyHostOutcome.Denied)
        val ready = rig.approve(pending)
        val created = rig.apply(ready) as ConversationApplyHostOutcome.Committed
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        val changed = rig.approve(rig.submit("edit",
            MatterReviewChange.Edit(rig.form("Trámite editado"), "Dato corregido")))
        val edited = rig.apply(changed) as ConversationApplyHostOutcome.Committed
        val current = rig.reality.matterPersistence.read()
        assertEquals(2L, current.revision)
        assertEquals("Trámite editado", current.matters.single().title.value)
        assertEquals(listOf(MatterHistoryKind.CREATED, MatterHistoryKind.EDITED),
            current.matters.single().details.history.map { it.kind })
        for (receipt in listOf(created.receipt, edited.receipt)) {
            assertEquals(receipt, rig.reality.executionReceiptLookup.find(receipt.transactionId))
        }
    }

    @Test fun discardAndReceiptOnlyDoNotMutate() = Rig().use { rig ->
        val discarded = rig.decide(ConversationPlanReviewIntent.Discard(rig.submit("discard").context()), "discard")
        assertTrue(rig.apply(discarded) is ConversationApplyHostOutcome.Denied)
        val ready = rig.approve(rig.submit("unknown-receipt"))
        assertTrue(rig.host.reconcileCommittedOnly(ready.context()) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.matterPersistence.read().revision)
    }

    @Test fun concurrentStaleEditsOneWinner() = Rig().use { rig ->
        assertTrue(rig.apply(rig.approve(rig.submit("create"))) is ConversationApplyHostOutcome.Committed)
        val first = rig.approve(rig.submit("first", MatterReviewChange.Edit(rig.form("Primero"), "Cambio uno")))
        val stale = rig.approve(rig.submit("stale", MatterReviewChange.Edit(rig.form("Segundo"), "Cambio dos")))
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(stale) is ConversationApplyHostOutcome.Denied)
        assertEquals("Primero", rig.reality.matterPersistence.read().matters.single().title.value)
        assertEquals(2L, rig.reality.matterPersistence.read().revision)
    }

    @Test fun policyForegroundAndSelectionFailClosed() = Rig().use { rig ->
        val ready = rig.approve(rig.submit("deny"))
        rig.policyAllows = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.policyAllows = true
        rig.foreground = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.foreground = true
        rig.select(ConversationId("other-matters-chat"))
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.matterPersistence.read().revision)
    }

    @Test fun archiveAndExplicitUndoPreserveAllEvents() = Rig().use { rig ->
        assertTrue(rig.apply(rig.approve(rig.submit("create"))) is ConversationApplyHostOutcome.Committed)
        val archived = rig.submit("archive",
            MatterReviewChange.Transition(MatterStatus.OPEN, MatterStatus.ARCHIVED, "Cerrar"))
        assertTrue(rig.apply(rig.approve(archived)) is ConversationApplyHostOutcome.Committed)
        val undo = rig.submit("undo",
            MatterReviewChange.Transition(MatterStatus.ARCHIVED, MatterStatus.OPEN, "Reabrir"), undoOf = 2)
        assertTrue(rig.apply(rig.approve(undo)) is ConversationApplyHostOutcome.Committed)
        val result = rig.reality.matterPersistence.read().matters.single()
        assertEquals(MatterStatus.OPEN, result.status)
        assertEquals(3, result.details.history.size)
        assertEquals(listOf(1, 2, 3), result.details.history.map { it.sequence })
        assertTrue(result.details.history.last().reason.startsWith("Undo #2:"))
    }

    @Test fun editedContentUndoIsNewReviewedCompensation() = Rig().use { rig ->
        assertTrue(rig.apply(rig.approve(rig.submit("create"))) is ConversationApplyHostOutcome.Committed)
        val previous = rig.reality.matterPersistence.read().matters.single()
        assertTrue(rig.apply(rig.approve(rig.submit("edit",
            MatterReviewChange.Edit(rig.form("Otro"), "Editar"))))
            is ConversationApplyHostOutcome.Committed)
        val original = MatterReviewForm(previous.title, previous.detail, previous.details.priority,
            previous.details.reviewDate, previous.dueAtEpochMillis)
        val undo = rig.submit("undo-edit",
            MatterReviewChange.Edit(original, "Restaurar valores confirmados"), undoOf = 2)
        assertTrue(rig.apply(rig.approve(undo)) is ConversationApplyHostOutcome.Committed)
        val result = rig.reality.matterPersistence.read().matters.single()
        assertEquals("Trámite", result.title.value)
        assertEquals(3, result.details.history.size)
        assertEquals(3L, rig.reality.matterPersistence.read().revision)
    }

    @Test fun canonicalPayloadRejectsTrailingBytesAndInvalidChanges() {
        val create = MatterReviewIntent(MatterId.of("case"), 0, 100,
            MatterReviewChange.Create(MatterReviewForm(MatterTitle.of("Caso"), MatterDetail.of(""))))
        val encoded = MatterReviewPayload.encode(create)
        assertEquals(create, MatterReviewPayload.decode(encoded))
        val bytes = encoded.copyBytes() + byteArrayOf(1)
        assertNull(MatterReviewPayload.decode(CanonicalPayload.fromEncoded("matters.reviewed-mutation.v1", bytes)))
        assertNull(MatterReviewIntent(MatterId.of("missing"), 0, 100,
            MatterReviewChange.Transition(MatterStatus.OPEN, MatterStatus.ARCHIVED, "Cerrar"))
            .applyOn(MatterSnapshot(0, emptyList())))
    }

    @Test fun snoozeAndInvalidTransitionAreExplicit() = Rig().use { rig ->
        assertTrue(rig.apply(rig.approve(rig.submit("create"))) is ConversationApplyHostOutcome.Committed)
        val bad = MatterReviewIntent(MatterId.of("case"), 1, 101,
            MatterReviewChange.Transition(MatterStatus.OPEN, MatterStatus.OPEN, "No permitido"))
        assertNull(bad.applyOn(rig.reality.matterPersistence.read()))
        val snooze = rig.submit("snooze", MatterReviewChange.Snooze(9999L, "Nueva fecha"))
        assertTrue(rig.apply(rig.approve(snooze)) is ConversationApplyHostOutcome.Committed)
        val saved = rig.reality.matterPersistence.read().matters.single()
        assertEquals(9999L, saved.dueAtEpochMillis)
        assertEquals(MatterStatus.OPEN, saved.status)
        assertEquals(2, saved.details.history.size)
    }
}
