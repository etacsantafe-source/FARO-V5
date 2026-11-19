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
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W02ObjectiveReviewHostTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selection = try {
            val start = StorageGenerationId("objective-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(start, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("objectives-cutover"),
                StorageGenerationDescriptor(StorageGenerationId("objectives-active"), "objectives", 2L),
                requireNotNull(pointer.compareAndSet(null, start)), 3L,
            ) { true }
        } catch (failure: Throwable) {
            runtime.close()
            throw failure
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("objectives-chat")
        var foreground = true
        var policyAllows = true
        val host = S4W02ObjectiveReviewHost(
            selection.pointer, pointer, workspace, reality, gate, BuiltInContractCatalog.create(),
            "objective-test-principal", "epoch", { "epoch" }, { runtime.isCurrent(selection) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, _, _ -> if (policyAllows) "allow-current" else null },
        )

        init { select(chat) }

        fun select(conversation: ConversationId) = runBlocking {
            assertTrue(workspace.createAndSelect(
                conversation, 5L, IdempotencyKey("select-" + conversation.value),
            ) is ConversationSelectionResult.Saved)
        }

        fun submit(
            key: String,
            operation: OperationId = ObjectiveReviewOperations.CREATE,
            revision: Long = reality.objectivePersistence.read().revision,
            title: String = "Practicar",
        ): ConversationPlanReviewSnapshot {
            val form = ObjectiveReviewedEdit(
                ObjectiveId.of("goal"), ObjectiveTitle.of(title), ObjectiveDetail.of("Cada semana"),
                ObjectiveMetric(12, ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.QUANTITY, "sesiones")),
                LocalDate.of(2027, 5, 1), ObjectivePriority.HIGH,
            )
            val result = host.submit(chat, operation, ObjectiveEditIntent(revision, 100 + revision, form), key)
            return (result as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun decide(intent: ConversationPlanReviewIntent, key: String): ConversationPlanReviewSnapshot =
            (runBlocking { workspace.decide(intent, IdempotencyKey(key)) } as ConversationReviewDecision.Saved).review

        fun approve(review: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            decide(ConversationPlanReviewIntent.Approve(review.context()), "approve-" + review.reviewId)

        fun apply(review: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(review.context())

        override fun close() {
            host.close()
            runtime.close()
        }
    }

    @Test fun createsAndEditsOnlyOnceWithRealReceipts() = Rig().use { rig ->
        val pending = rig.submit("create")
        assertEquals(pending, rig.submit("create"))
        assertEquals(0, rig.reality.objectivePersistence.read().objectives.size)
        assertTrue(rig.apply(pending) is ConversationApplyHostOutcome.Denied)
        val ready = rig.approve(pending)
        val created = rig.apply(ready) as ConversationApplyHostOutcome.Committed
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        val before = rig.reality.objectivePersistence.read().objectives.single()
        assertEquals(ObjectiveStatus.PLANNED, before.status)
        val edit = rig.approve(rig.submit("edit", ObjectiveReviewOperations.UPDATE, title = "Practicar dibujo"))
        val updated = rig.apply(edit) as ConversationApplyHostOutcome.Committed
        for (receipt in listOf(created.receipt, updated.receipt)) {
            assertEquals(receipt, rig.reality.executionReceiptLookup.find(receipt.transactionId))
        }
        assertNotEquals(created.receipt, updated.receipt)
        val snapshot = rig.reality.objectivePersistence.read()
        assertEquals(2L, snapshot.revision)
        assertEquals(before.copy(title = ObjectiveTitle.of("Practicar dibujo"), updatedAtEpochMillis = 101), snapshot.objectives.single())
    }

    @Test fun discardAndReceiptOnlyPathLeaveRealityUntouched() = Rig().use { rig ->
        val review = rig.submit("discard")
        val discarded = rig.decide(ConversationPlanReviewIntent.Discard(review.context()), "discard")
        assertTrue(rig.apply(discarded) is ConversationApplyHostOutcome.Denied)
        val approved = rig.approve(rig.submit("recovery"))
        assertTrue(rig.host.reconcileCommittedOnly(approved.context()) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.objectivePersistence.read().revision)
    }

    @Test fun staleEditsDoNotReplaceTheFirstCommittedIntent() = Rig().use { rig ->
        assertTrue(rig.apply(rig.approve(rig.submit("create"))) is ConversationApplyHostOutcome.Committed)
        val first = rig.approve(rig.submit("first", ObjectiveReviewOperations.UPDATE, title = "Primero"))
        val stale = rig.approve(rig.submit("stale", ObjectiveReviewOperations.UPDATE, title = "Viejo"))
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(stale) is ConversationApplyHostOutcome.Denied)
        val result = rig.reality.objectivePersistence.read()
        assertEquals(2L, result.revision)
        assertEquals("Primero", result.objectives.single().title.value)
    }

    @Test fun deniedOrNonForegroundOrUnselectedReviewCannotCommit() = Rig().use { rig ->
        val ready = rig.approve(rig.submit("unauthorized"))
        rig.policyAllows = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.policyAllows = true
        rig.foreground = false
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        rig.foreground = true
        rig.select(ConversationId("other-objectives-chat"))
        assertTrue(rig.apply(ready) is ConversationApplyHostOutcome.Denied)
        assertEquals(0L, rig.reality.objectivePersistence.read().revision)
    }
}
