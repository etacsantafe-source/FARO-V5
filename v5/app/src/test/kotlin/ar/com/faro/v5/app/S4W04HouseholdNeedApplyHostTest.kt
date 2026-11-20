package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04HouseholdNeedApplyHostTest {
    @TempDir lateinit var root: Path

    private val chat = ConversationId("household-test")
    private val name = HouseholdNeedName("Yerba")
    private val fields = HouseholdNeedFields(
        name, HouseholdNeedQuantity(500, HouseholdNeedUnit.GRAM), HouseholdNeedPriority.NORMAL, null,
    )

    @Test
    fun appliesAllFourOperationsAndLeavesDurableReceipts() = Rig().use { rig ->
        val add = rig.approve(rig.submit("add", HouseholdNeedReviewRequest.Add(fields)))
        val added = rig.apply(add) as ReviewedConversationApplyResult.Committed
        val edit = rig.approve(rig.submit("edit", HouseholdNeedReviewRequest.Edit(
            name, fields.copy(quantity = HouseholdNeedQuantity(750, HouseholdNeedUnit.GRAM)),
        )))
        val edited = rig.apply(edit) as ReviewedConversationApplyResult.Committed
        assertEquals(750L, rig.snapshot().needs.single().quantity.thousandths)
        val complete = rig.approve(rig.submit("complete", HouseholdNeedReviewRequest.Complete(name)))
        val done = rig.apply(complete) as ReviewedConversationApplyResult.Committed
        assertEquals(HouseholdNeedStatus.COMPLETED, rig.snapshot().needs.single().status)
        val remove = rig.approve(rig.submit("remove", HouseholdNeedReviewRequest.Remove(name)))
        val removed = rig.apply(remove) as ReviewedConversationApplyResult.Committed
        assertEquals(4L, rig.snapshot().revision)
        assertTrue(rig.snapshot().needs.isEmpty())
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(remove))
        rig.host.close()
        rig.openGraph().use { graph ->
            for (result in listOf(added, edited, done, removed)) {
                assertEquals(result.receipt, graph.reality.executionReceiptLookup.find(result.receipt.transactionId))
                val attempt = graph.reality.applyAttemptLookup.find(result.receipt.transactionId)
                assertEquals(ApplyAttemptJournalState.COMMITTED, attempt?.state)
            }
            assertEquals(4L, graph.reality.householdNeedsPersistenceForTrustedDev().read().revision)
            assertEquals(0L, graph.reality.financePersistence.read().revision)
            assertEquals(0L, graph.reality.objectivePersistence.read().revision)
            assertEquals(0L, graph.reality.matterPersistence.read().revision)
        }
    }

    @Test
    fun pendingOrForgedReviewCannotWrite() = Rig().use { rig ->
        val pending = rig.submit("pending", HouseholdNeedReviewRequest.Add(fields))
        assertEquals(pending, rig.submit("pending", HouseholdNeedReviewRequest.Add(fields)))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(pending))
        val approved = rig.approve(pending)
        val forged = approved.context().copy(planFingerprint = PlanFingerprint("a".repeat(64)))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.host.executeAfterExplicitConfirmation(forged))
        assertEquals(0L, rig.snapshot().revision)
    }

    @Test
    fun foregroundPolicyAndPointerAbaDenyApproval() = Rig().use { rig ->
        val approved = rig.approve(rig.submit("grant", HouseholdNeedReviewRequest.Add(fields)))
        rig.foreground = false
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.foreground = true
        rig.allow = { false }
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        assertEquals(0L, rig.snapshot().revision)
        rig.allow = { true }
        val other = StorageGenerationId("other-generation")
        checkNotNull(rig.pointer.compareAndSet(rig.active.generationId, other))
        checkNotNull(rig.pointer.compareAndSet(other, rig.active.generationId))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        assertNull(rig.host.snapshot())
    }

    @Test
    fun staleApprovedChangeCannotOverwriteANewerCommit() = Rig().use { rig ->
        val a = rig.approve(rig.submit("a", HouseholdNeedReviewRequest.Add(fields)))
        val b = rig.approve(rig.submit("b", HouseholdNeedReviewRequest.Add(
            fields.copy(name = HouseholdNeedName("Café")),
        )))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, rig.apply(a))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(b))
        assertEquals(listOf(name), rig.snapshot().needs.map { it.name })
    }

    @Test
    fun revocationAtFinalGateRollsBackBothEffectAndReceipt() = Rig().use { rig ->
        val ready = rig.approve(rig.submit("revoke", HouseholdNeedReviewRequest.Add(fields)))
        var grantChecks = 0
        rig.allow = { ++grantChecks < 5 }
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(ready))
        assertEquals(5, grantChecks)
        val recovering = rig.reviews().single()
        assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN, recovering.state)
        assertEquals(0L, rig.snapshot().revision)
        rig.host.close()
        rig.openGraph().use {
            val transaction = checkNotNull(recovering.transactionId)
            assertNull(it.reality.executionReceiptLookup.find(transaction))
            assertEquals(ApplyAttemptJournalState.PREPARED, it.reality.applyAttemptLookup.find(transaction)?.state)
        }
    }

    @Test
    fun reopensAndReconcilesExistingReceiptAfterWorkspaceInterruption(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("recover", HouseholdNeedReviewRequest.Add(fields)))
        rig.interruptWorkspaceCommit = true
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        val applying = rig.reviews().single()
        assertEquals(ConversationPlanReviewState.APPLYING, applying.state)
        assertEquals(1L, rig.snapshot().revision)
        rig.reopen()
        val result = rig.host.reconcileCommittedOnly(applying.context()) as ReviewedConversationApplyResult.Committed
        assertEquals(applying.transactionId, result.receipt.transactionId)
        assertEquals(ConversationPlanReviewState.COMMITTED, result.review.state)
        assertEquals(1L, rig.snapshot().revision)
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java,
            rig.host.reconcileCommittedOnly(applying.context()))
    }

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("origin")))
        val stage = stage()
        val active = checkNotNull(pointer.compareAndSet(origin.generationId, stage.descriptor.generationId))
        var foreground = true
        var allow: () -> Boolean = { true }
        var interruptWorkspaceCommit = false
        var host = open()

        init {
            val selected = runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("select")) }
            assertInstanceOf(ConversationSelectionResult.Saved::class.java, selected)
        }

        private fun stage(): StagedStorageGeneration {
            val eight = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("household-apply"), "apply-fixture", 1L),
            )
            val quiescence = object : MaterialWriteQuiescencePort {
                override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
            }
            val nine = FaroV5Finance9StagedMigration(root, quiescence).migrate(
                MaintenanceOperationId("prepare-nine"), eight, origin, "a".repeat(64), 2L,
            )
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(nine, pointer).close()
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(nine.directory.resolve(GenerationStoreFiles.REALITY))
            return nine
        }

        private fun open() = S4W04HouseholdNeedApplyHost(
            root, stage, active, gate, BuiltInContractCatalog.create(), "household-human", "test-epoch",
            { "test-epoch" }, { true }, { foreground },
            { principal, _, _ -> principal == "household-human" && allow() },
        ) { real ->
            object : TrustedConversationApplyReviewPort by real {
                override fun advance(
                    intent: ConversationPlanReviewIntent,
                    key: IdempotencyKey,
                    attempts: ApplyAttemptLookupPort,
                    receipts: ExecutionReceiptLookupPort,
                ): ConversationReviewDecision {
                    if (intent is ConversationPlanReviewIntent.ObserveCommitted && interruptWorkspaceCommit) {
                        interruptWorkspaceCommit = false
                        return ConversationReviewDecision.StaleOrUnavailable
                    }
                    return real.advance(intent, key, attempts, receipts)
                }
            }
        }

        fun openGraph() = FaroV5GenerationStoreGraph.openActiveHousehold10DevForApplyHost(
            root, stage, active, gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        )

        fun reopen() {
            host.close()
            host = open()
        }

        fun snapshot() = checkNotNull(host.snapshot())
        fun reviews() = runBlocking { host.reviewDecisions.reviews(chat) }

        fun submit(key: String, request: HouseholdNeedReviewRequest): ConversationPlanReviewSnapshot =
            (host.submit(chat, request, key) as S4W04HouseholdNeedApplyHost.Submission.Saved).review

        fun approve(pending: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            (runBlocking { host.reviewDecisions.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-" + pending.reviewId),
            ) } as ConversationReviewDecision.Saved).review

        fun apply(approved: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(approved.context())
        override fun close() = host.close()
    }
}
