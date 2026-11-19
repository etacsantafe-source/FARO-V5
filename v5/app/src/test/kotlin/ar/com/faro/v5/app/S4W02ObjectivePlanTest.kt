package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5G4DomainSemanticCodec
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
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Path
import java.time.LocalDate
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W02ObjectivePlanTest {
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
        val granted = mutableMapOf<ObjectiveLink, String>()
        var readCount = 0
        var revokeOnRead = false
        val references = ObjectiveReferenceAuthority(granted::get) { link, _ ->
            readCount++
            if (revokeOnRead) granted.remove(link)
            if (link == financeLink) "Caja local" else null
        }
        val host = S4W02ObjectiveReviewHost(
            selection.pointer, pointer, workspace, reality, gate, BuiltInContractCatalog.create(),
            "objective-test-principal", "epoch", { "epoch" }, { runtime.isCurrent(selection) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, _, _ -> if (policyAllows) "allow-current" else null }, references,
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

        fun plan(key: String, change: ObjectivePlanChange): ConversationPlanReviewSnapshot? {
            val snapshot = reality.objectivePersistence.read()
            val record = snapshot.objectives.single()
            val intent = ObjectivePlanIntent(record.id, snapshot.revision, record.updatedAtEpochMillis + 1, change)
            return (host.submitPlan(chat, intent, key) as? WorkspacePlanReviewWriteResult.Saved)?.review
        }

        fun create() {
            assertTrue(apply(approve(submit("create"))) is ConversationApplyHostOutcome.Committed)
        }

        override fun close() {
            host.close()
            runtime.close()
        }
    }


    private val financeLink = ObjectiveLink(ObjectiveLinkOwner.FINANCE, "private-cash-id")
    private val matterLink = ObjectiveLink(ObjectiveLinkOwner.MATTER, "private-case-id")

    @Test fun persistsAnUndatedStepAndItsCompletionThroughReviewAndBackup() = Rig().use { rig ->
        rig.create()
        val step = ObjectiveNextStep("step", "Anotar ideas")
        val pending = requireNotNull(rig.plan("step", ObjectivePlanChange.PutStep(step)))
        assertTrue(rig.reality.objectivePersistence.read().objectives.single().details.plan.steps.isEmpty())
        val first = rig.apply(rig.approve(pending)) as ConversationApplyHostOutcome.Committed
        assertEquals(first.receipt, rig.reality.executionReceiptLookup.find(first.receipt.transactionId))
        val complete = requireNotNull(rig.plan("complete", ObjectivePlanChange.PutStep(step.copy(done = true))))
        assertTrue(rig.apply(rig.approve(complete)) is ConversationApplyHostOutcome.Committed)
        val snapshot = rig.reality.domainBackup.snapshot()
        val bytes = FaroV5G4DomainSemanticCodec.encode(snapshot)
        assertEquals(4, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(snapshot, FaroV5G4DomainSemanticCodec.decode(bytes))
        RealityStore(":memory:").use { target ->
            target.domainBackupRestore.restoreOrVerify(FaroV5G4DomainSemanticCodec.decode(bytes))
            assertEquals(step.copy(done = true), target.objectivePersistence.read().objectives.single().details.plan.steps.single())
        }
    }

    @Test fun independentReferenceGrantIsCheckedBeforeReviewApplyAndProjection() = Rig().use { rig ->
        rig.create()
        assertNull(rig.plan("denied", ObjectivePlanChange.SetLink(financeLink, true)))
        assertEquals(0, rig.readCount)
        rig.granted[financeLink] = "finance-1"
        assertNull(rig.plan("matter-denied", ObjectivePlanChange.SetLink(matterLink, true)))
        assertEquals(0, rig.readCount)
        val initial = requireNotNull(rig.plan("financial", ObjectivePlanChange.SetLink(financeLink, true)))
        val approved = rig.approve(initial)
        rig.granted.clear()
        assertTrue(rig.apply(approved) is ConversationApplyHostOutcome.Denied)
        assertEquals(1L, rig.reality.objectivePersistence.read().revision)
        rig.granted[financeLink] = "finance-2"
        val committed = rig.apply(approved) as ConversationApplyHostOutcome.Committed
        assertEquals(committed.receipt, rig.reality.executionReceiptLookup.find(committed.receipt.transactionId))
        assertEquals(setOf(financeLink), rig.reality.objectivePersistence.read().objectives.single().details.plan.links)
        assertEquals(ObjectiveLinkView.Visible(financeLink, "Caja local"), rig.references.observe(financeLink))
        rig.revokeOnRead = true
        assertEquals(ObjectiveLinkView.Unavailable(ObjectiveLinkOwner.FINANCE), rig.references.observe(financeLink))
        assertEquals(ObjectiveLinkView.Unavailable(ObjectiveLinkOwner.MATTER), rig.references.observe(matterLink))
    }

    @Test fun rejectsSelfIndirectCyclesAndDanglingDependencies() {
        fun goal(id: String, link: String? = null): ObjectiveRecord = ObjectiveRecord(
            ObjectiveId.of(id), ObjectiveTitle.of(id), ObjectiveDetail.of(""), ObjectiveStatus.ACTIVE, 10,
            ObjectiveDetails(plan = ObjectivePlan(links = link?.let {
                setOf(ObjectiveLink(ObjectiveLinkOwner.OBJECTIVE, it))
            }.orEmpty())),
        )
        val a = goal("a", "b")
        val b = goal("b", "c")
        val c = goal("c")
        val snapshot = ObjectiveSnapshot(3, listOf(a,b,c))
        for (target in listOf("c", "a", "missing")) {
            val command = ObjectivePlanIntent(
                c.id, 3, 11, ObjectivePlanChange.SetLink(ObjectiveLink(ObjectiveLinkOwner.OBJECTIVE, target), true),
            )
            assertNull(command.applyOn(snapshot))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveSnapshot(3, listOf(a,b,goal("c","a")))
        }
    }
}
