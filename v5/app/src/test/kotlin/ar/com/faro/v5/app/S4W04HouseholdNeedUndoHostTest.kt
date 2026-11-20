package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04HouseholdNeedUndoHostTest {
    @TempDir lateinit var root: Path

    private val name = HouseholdNeedName("Yerba")
    private val fields = HouseholdNeedFields(
        name, HouseholdNeedQuantity(500, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.NORMAL, LocalDate.of(2026, 12, 5),
    )
    private val chat = ConversationId("undo-needs")

    @Test
    fun undoAddRequiresAnotherApprovalAndCannotExecuteTwice(): Unit = Rig().use { rig ->
        val added = rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val pending = rig.undoReview("undo-add", added.review.context())
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java,
            rig.host.executeAfterExplicitConfirmation(pending.context()))
        assertEquals(1L, rig.snapshot().revision)
        val approved = rig.approve(pending)
        val reversed = rig.commit(approved)
        assertNotEquals(added.receipt, reversed.receipt)
        assertEquals(2L, rig.snapshot().revision)
        assertTrue(rig.snapshot().needs.isEmpty())
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java,
            rig.host.executeAfterExplicitConfirmation(approved.context()))
        assertInstanceOf(S4W04HouseholdNeedApplyHost.Submission.Rejected::class.java,
            rig.host.submitUndo(added.review.context(), "second-undo"))
        assertEquals(2L, rig.snapshot().revision)
    }

    @Test
    fun undoEditRestoresNameQuantityUnitPriorityAndDate(): Unit = Rig().use { rig ->
        rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val before = rig.snapshot().needs.single()
        val edited = rig.mutate("edit", HouseholdNeedReviewRequest.Edit(name, HouseholdNeedFields(
            HouseholdNeedName("Yerba grande"), HouseholdNeedQuantity(2_000, HouseholdNeedUnit.KILOGRAM),
            HouseholdNeedPriority.HIGH, null,
        )))
        val result = rig.undo("undo-edit", edited)
        assertEquals(before.copy(revision = 3), rig.snapshot().needs.single())
        assertEquals(edited.receipt, rig.host.history(chat).single { it.receipt == result.receipt }.undoneReceipt)
    }

    @Test
    fun undoCompleteReopensTheNeed(): Unit = Rig().use { rig ->
        rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val completed = rig.mutate("complete", HouseholdNeedReviewRequest.Complete(name))
        assertEquals(HouseholdNeedStatus.COMPLETED, rig.snapshot().needs.single().status)
        rig.undo("undo-complete", completed)
        assertEquals(HouseholdNeedStatus.OPEN, rig.snapshot().needs.single().status)
        assertEquals(3L, rig.snapshot().revision)
    }

    @Test
    fun undoRemoveRestoresCompletedStateWithDurableHistoryAndLink(): Unit = Rig().use { rig ->
        val a = rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val b = rig.mutate("complete", HouseholdNeedReviewRequest.Complete(name))
        val before = rig.snapshot().needs.single()
        val c = rig.mutate("remove", HouseholdNeedReviewRequest.Remove(name))
        assertTrue(rig.snapshot().needs.isEmpty())
        rig.reopen()
        val source = rig.host.history(chat).single { it.receipt == c.receipt }
        assertTrue(source.canUndo)
        val undo = rig.commit(rig.approve(rig.undoReview("undo-remove", source.review)))
        rig.reopen()
        assertEquals(before.copy(revision = 4), rig.snapshot().needs.single())
        val history = rig.host.history(chat)
        assertEquals(listOf(a.receipt, b.receipt, c.receipt, undo.receipt), history.map { it.receipt })
        assertEquals(c.receipt, history.last().undoneReceipt)
        assertFalse(history.single { it.receipt == c.receipt }.canUndo)
    }

    @Test
    fun rejectsStaleUndoAndMissingRowAba(): Unit = Rig().use { rig ->
        val a = rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val revertAdd = rig.approve(rig.undoReview("undo-add", a.review.context()))
        rig.mutate("complete", HouseholdNeedReviewRequest.Complete(name))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java,
            rig.host.executeAfterExplicitConfirmation(revertAdd.context()))
        val removed = rig.mutate("remove", HouseholdNeedReviewRequest.Remove(name))
        rig.mutate("add-other", HouseholdNeedReviewRequest.Add(fields.copy(name = HouseholdNeedName("Café"))))
        assertInstanceOf(S4W04HouseholdNeedApplyHost.Submission.Rejected::class.java,
            rig.host.submitUndo(removed.review.context(), "obsolete-deletion"))
        assertEquals(4L, rig.snapshot().revision)
    }

    @Test
    fun rejectsDifferentPrincipalAndAlteredSource(): Unit = Rig().use { rig ->
        val added = rig.mutate("add", HouseholdNeedReviewRequest.Add(fields))
        val wrong = added.review.context().copy(planFingerprint = PlanFingerprint("b".repeat(64)))
        assertInstanceOf(S4W04HouseholdNeedApplyHost.Submission.Rejected::class.java,
            rig.host.submitUndo(wrong, "altered-source"))
        rig.reopen("other-principal")
        assertInstanceOf(S4W04HouseholdNeedApplyHost.Submission.Rejected::class.java,
            rig.host.submitUndo(added.review.context(), "foreign"))
        assertTrue(rig.host.history(chat).isEmpty())
        assertEquals(1L, rig.snapshot().revision)
    }

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        private val pointer = FileActiveGenerationPointer(root)
        private val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("origin")))
        private val staged = stage()
        private val active = checkNotNull(pointer.compareAndSet(origin.generationId, staged.descriptor.generationId))
        var host = open("owner-principal")

        init {
            val selected = runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("select")) }
            assertInstanceOf(ConversationSelectionResult.Saved::class.java, selected)
        }

        private fun stage(): StagedStorageGeneration {
            val eight = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("household-undo"), "undo-fixture", 1L),
            )
            val nine = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
                MaintenanceOperationId("prepare-nine"), eight, origin, "a".repeat(64), 2L,
            )
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(nine, pointer).close()
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(nine.directory.resolve(GenerationStoreFiles.REALITY))
            return nine
        }

        private fun open(principal: String) = S4W04HouseholdNeedApplyHost(
            root, staged, active, gate, BuiltInContractCatalog.create(), principal, "epoch",
            { "epoch" }, { true }, { true }, { actual, _, _ -> actual == principal },
        )

        fun reopen(principal: String = "owner-principal") {
            host.close()
            host = open(principal)
        }

        fun snapshot() = checkNotNull(host.snapshot())

        fun approve(pending: ConversationPlanReviewSnapshot) =
            (runBlocking { host.reviewDecisions.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-" + pending.reviewId),
            ) } as ConversationReviewDecision.Saved).review

        fun commit(approved: ConversationPlanReviewSnapshot) =
            host.executeAfterExplicitConfirmation(approved.context()) as ReviewedConversationApplyResult.Committed

        fun mutate(key: String, request: HouseholdNeedReviewRequest): ReviewedConversationApplyResult.Committed {
            val pending = (host.submit(chat, request, key) as S4W04HouseholdNeedApplyHost.Submission.Saved).review
            return commit(approve(pending))
        }

        fun undoReview(key: String, source: ConversationPlanReviewContext) =
            (host.submitUndo(source, key) as S4W04HouseholdNeedApplyHost.Submission.Saved).review

        fun undo(key: String, effect: ReviewedConversationApplyResult.Committed) = commit(approve(undoReview(key, effect.review.context())))
        override fun close() = host.close()
    }
}
