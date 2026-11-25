package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold12StagedFilePort
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W05ManualPurchaseDevApplyHostTest {
    @TempDir lateinit var root: Path

    private val chat = ConversationId("purchase-dev-test")
    private val ten = HouseholdNeedQuantity(10_000L, HouseholdNeedUnit.UNIT)

    @Test
    fun explicitPurchaseCommitsOneRowAndReceipt() = Rig().use { rig ->
        val pending = rig.submit()
        val approved = rig.approve(pending)
        val result = rig.apply(approved) as ReviewedConversationApplyResult.Committed
        val row = checkNotNull(rig.host.find("purchase-1"))
        assertEquals("yerba", row.stockItemRef)
        assertEquals("UNIT", row.unit)
        assertEquals("MANUAL_EXPLICIT", row.provenance)
        assertEquals("manual-human", row.confirmedBy)
        assertEquals(10_000, row.quantityThousandths)
        assertEquals(1234L, row.confirmedAtMs)
        rig.host.close()
        rig.graph().use { graph ->
            assertEquals(result.receipt, graph.receipts.find(result.receipt.transactionId))
            assertEquals(ApplyAttemptJournalState.COMMITTED,
                graph.attempts.find(result.receipt.transactionId)?.state)
            val state = graph.snapshotPurchaseReceipts()
            assertEquals(listOf(row), state.purchases)
            assertTrue(state.receipts.receipts.isEmpty())
        }
    }

    @Test
    fun repeatedIntentKeepsTheSingleOriginalPurchase() = Rig().use { rig ->
        val pending = rig.submit()
        assertEquals(pending, rig.submit())
        val approved = rig.approve(pending)
        val committed = rig.apply(approved) as ReviewedConversationApplyResult.Committed
        assertEquals(committed.review, rig.submit())
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.host.close()
        rig.graph().use {
            assertEquals(1, it.snapshotPurchaseReceipts().purchases.size)
            assertEquals(committed.receipt, it.receipts.find(committed.receipt.transactionId))
        }
    }

    @Test
    fun conflictingApprovedPurchaseCannotReplaceTheOriginal() = Rig().use { rig ->
        val first = rig.approve(rig.submit())
        val other = rig.approve(rig.submit("conflicting", HouseholdNeedQuantity(9_000L, HouseholdNeedUnit.UNIT)))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, rig.apply(first))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(other))
        assertEquals(10_000, rig.host.find("purchase-1")?.quantityThousandths)
    }

    @Test
    fun pendingForgedAndRevokedApprovalCannotWrite() = Rig().use { rig ->
        val pending = rig.submit()
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(pending))
        val approved = rig.approve(pending)
        val forged = approved.context().copy(planFingerprint = PlanFingerprint("f".repeat(64)))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java,
            rig.host.executeAfterExplicitConfirmation(forged))
        rig.foreground = false
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.foreground = true
        rig.allow = { false }
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.allow = { true }
        assertNull(rig.host.find("purchase-1"))
    }

    @Test
    fun staleEpochAndFullPointerAbaRejectAlreadyApprovedPlan(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit())
        rig.epoch = "changed"
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.epoch = "test-epoch"
        assertNull(rig.host.find("purchase-1"))
        val other = StorageGenerationId("other-purchase-generation")
        checkNotNull(rig.pointer.compareAndSet(rig.active.generationId, other))
        checkNotNull(rig.pointer.compareAndSet(other, rig.active.generationId))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
    }

    @Test
    fun denialAtFinalCommitRollsBackBothRowAndReceipt() = Rig().use { rig ->
        val approved = rig.approve(rig.submit())
        var checks = 0
        rig.allow = { ++checks < 7 }
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        assertEquals(7, checks)
        rig.allow = { true }
        val attempt = runBlocking { rig.host.reviewDecisions.reviews(chat) }.single()
        val tx = checkNotNull(attempt.transactionId)
        assertNull(rig.host.find("purchase-1"))
        rig.host.close()
        rig.graph().use {
            assertNull(it.receipts.find(tx))
            assertEquals(ApplyAttemptJournalState.PREPARED, it.attempts.find(tx)?.state)
        }
    }

    @Test
    fun reconcilesCommittedReceiptAfterWorkspaceInterruptionWithoutNewEffect(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit())
        rig.interruptWorkspace = true
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        val applying = runBlocking { rig.host.reviewDecisions.reviews(chat) }.single()
        assertEquals(ConversationPlanReviewState.APPLYING, applying.state)
        assertEquals(10_000, rig.host.find("purchase-1")?.quantityThousandths)
        rig.host.close()
        rig.host = rig.open()
        val result = rig.host.reconcileCommittedOnly(applying.context()) as ReviewedConversationApplyResult.Committed
        assertEquals(applying.transactionId, result.receipt.transactionId)
        assertEquals(10_000, rig.host.find("purchase-1")?.quantityThousandths)
    }

    @Test
    fun refusesASelectedSql12WithoutCompletedCutoverProof() {
        assertThrows(IllegalStateException::class.java) { Rig(completed = false).close() }
    }

    private inner class Rig(completed: Boolean = true) : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        private val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("purchase-origin")))
        private val stage = prepareSql12()
        val active = checkNotNull(pointer.compareAndSet(origin.generationId, stage.descriptor.generationId))
        var foreground = true
        var epoch = "test-epoch"
        var allow: () -> Boolean = { true }
        var interruptWorkspace = false
        var host: S4W05ManualPurchaseDevApplyHost

        init {
            if (completed) seedCompletedCutoverFixture()
            host = open()
            val selected = runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("select")) }
            assertInstanceOf(ConversationSelectionResult.Saved::class.java, selected)
        }

        private fun prepareSql12(): StagedStorageGeneration {
            val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("purchase-sql12"), "purchase-dev-fixture", 1L),
            )
            val nine = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
                MaintenanceOperationId("purchase-fixture-nine"), fresh, origin, "a".repeat(64), 2L,
            )
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(nine, pointer).close()
            val reality = nine.directory.resolve(GenerationStoreFiles.REALITY)
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
            RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
            RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
            CommercialEvidenceStore(
                fileName = nine.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
            ).close()
            return nine
        }

        /** Fixture for a completed B3B operation. B3B's switch and recovery have their own CI. */
        private fun seedCompletedCutoverFixture() {
            val op = MaintenanceOperationId("purchase-fixture-cutover")
            val target = active.generationId
            FileBootstrapJournal(root).create(BootstrapJournalEntry(
                op, MaintenanceOperationKind.RESTORE, origin.generationId, target, BootstrapPhase.COMPLETED,
                materialWritesAccepted = true,
            ))
            FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, target)
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).use { out ->
                out.writeUTF("FARO_SQL12_EVIDENCE2_CUTOVER_V1")
                out.writeUTF(op.value)
                out.writeLong(origin.sequence)
                out.writeUTF(origin.generationId.value)
                out.writeUTF(target.value)
                out.writeUTF(origin.generationId.value)
                repeat(3) { out.writeUTF("a".repeat(64)) }
            }
            val payload = buffer.toByteArray()
            val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
            Files.write(root.resolve("bootstrap/sql12-evidence2-cutover.ticket"), payload + checksum)
        }

        fun open() = S4W05ManualPurchaseDevApplyHost(
            root, active, gate, BuiltInContractCatalog.create(), "manual-human", epoch, { epoch },
            { true }, { foreground },
            { principal, operation, _ -> principal == "manual-human" &&
                operation == OperationId("shopping.purchase.record") && allow() },
            { 1234L },
        ) { real ->
            object : TrustedConversationApplyReviewPort by real {
                override fun advance(
                    intent: ConversationPlanReviewIntent,
                    key: IdempotencyKey,
                    attempts: ApplyAttemptLookupPort,
                    receipts: ExecutionReceiptLookupPort,
                ): ConversationReviewDecision {
                    if (intent is ConversationPlanReviewIntent.ObserveCommitted && interruptWorkspace) {
                        interruptWorkspace = false
                        return ConversationReviewDecision.StaleOrUnavailable
                    }
                    return real.advance(intent, key, attempts, receipts)
                }
            }
        }

        fun graph() = FaroV5GenerationStoreGraph.openActiveHousehold12DevForPurchaseHost(
            root, active, gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        )

        fun submit(key: String = "same-intent", quantity: HouseholdNeedQuantity = ten) =
            checkNotNull(host.submit(chat, key, "purchase-1", "yerba", quantity))

        fun approve(pending: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            (runBlocking { host.reviewDecisions.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-" + pending.reviewId),
            ) } as ConversationReviewDecision.Saved).review

        fun apply(approved: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(approved.context())
        override fun close() = host.close()
    }
}
