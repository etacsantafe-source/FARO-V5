package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.FaroV5ClosedHousehold12DevMaintenance
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.HouseholdManualPurchaseReview
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.api.HouseholdStockReceiveReview
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

class FaroV5W05A1DevAcceptanceTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val catalog = BuiltInContractCatalog.create()
    private val principal = "a1c4-local-human"
    private val chat = ConversationId("a1c4-stock-history")
    private val purchaseRef = "purchase-1"

    @Test
    fun preservesAppliedPurchaseAndReceiptsThroughRecoveryCutoverAndUndo() {
        val active12 = seedSql12()
        val quantity7 = HouseholdNeedQuantity(7_000L, HouseholdNeedUnit.UNIT)
        receiveHost(active12, actor = "unrelated-human").use {
            assertNull(it.submit(chat, "wrong-principal", purchaseRef, "yerba", quantity7))
        }
        receiveHost(active12, permitted = false).use {
            assertNull(it.submit(chat, "missing-grant", purchaseRef, "yerba", quantity7))
        }

        receiveHost(active12, interruptWorkspace = true).use { host ->
            val pending = checkNotNull(host.submit(chat, "seven", purchaseRef, "yerba", quantity7))
            assertTrue(host.executeAfterExplicitConfirmation(pending.context()) is ReviewedConversationApplyResult.Denied)
            val approved = approve(host.reviewDecisions, pending, "receive-seven-approval")
            assertTrue(host.executeAfterExplicitConfirmation(pending.context()) is ReviewedConversationApplyResult.Denied)
            assertEquals(ReviewedConversationApplyResult.RecoveryRequired,
                host.executeAfterExplicitConfirmation(approved.context()))
            assertEquals(3_000L, checkNotNull(host.position(purchaseRef)).pendingThousandths)
        }

        val first = receiveHost(active12).use { host ->
            val recovering = runBlocking { host.reviewDecisions.reviews(chat) }
                .single { it.reviewId.startsWith("w05:receive:") }
            assertTrue(recovering.state in setOf(
                ConversationPlanReviewState.APPLYING, ConversationPlanReviewState.RECOVERING_UNKNOWN,
            ))
            val committed = host.reconcileCommittedOnly(recovering.context()) as ReviewedConversationApplyResult.Committed
            assertEquals(recovering.transactionId, committed.receipt.transactionId)
            assertEquals(1L, checkNotNull(host.position(purchaseRef)).ledgerRevision)
            val pending = checkNotNull(host.submit(chat, "three", purchaseRef, "yerba",
                HouseholdNeedQuantity(3_000L, HouseholdNeedUnit.UNIT)))
            val approved = approve(host.reviewDecisions, pending, "receive-three-approval")
            assertTrue(host.executeAfterExplicitConfirmation(approved.context()) is ReviewedConversationApplyResult.Committed)
            assertEquals(0L, checkNotNull(host.position(purchaseRef)).pendingThousandths)
            committed
        }

        val physical = FaroV5GenerationStoreGraph.openActiveHousehold12DevForPurchaseHost(
            root, active12, gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        ).use {
            val position = it.snapshotPurchaseReceipts()
            assertEquals(10_000, position.purchases.single().quantityThousandths)
            assertEquals("MANUAL_EXPLICIT", position.purchases.single().provenance)
            assertEquals(listOf(7_000, 3_000), position.receipts.receipts.map { row -> row.quantityThousandths })
            val purchase = runBlocking { it.workspace.reviews(chat) }.single { review ->
                review.reviewId.startsWith("w05:purchase:")
            }
            val purchaseReceipt = checkNotNull(purchase.receipt)
            assertEquals(purchaseReceipt, it.receipts.find(purchaseReceipt.transactionId))
            assertEquals(first.receipt, it.receipts.find(first.receipt.transactionId))
            assertEquals(ApplyAttemptJournalState.COMMITTED, it.attempts.find(first.receipt.transactionId)?.state)
            position.receipts.receipts.associate { row -> row.quantityThousandths to row.receiptId }
        }

        val active13 = transition(active12)
        S4W05StockUndoDevApplyHost(
            root, active13, gate, catalog, principal, 1L, { 1L }, { true }, { true },
            { actor, operation, _ -> actor == principal && operation == OperationId("undo.execute") },
            { 6000L },
        ).use { host ->
            val restored = runBlocking { host.reviewDecisions.reviews(chat) }
                .single { it.reviewId == first.review.reviewId }
            assertEquals(first.receipt, restored.receipt)
            assertNull(host.submitUndo(first.review.context(), "old-authority"))
            val pending = checkNotNull(host.submitUndo(restored.context(), "compensate-seven"))
            val approved = approve(host.reviewDecisions, pending, "compensation-approval")
            assertTrue(host.executeAfterExplicitConfirmation(approved.context()) is ReviewedConversationApplyResult.Committed)
            assertNull(host.submitUndo(restored.context(), "compensate-seven-twice"))
            assertNull(host.position(physical.getValue(7_000)))
            assertEquals(3_000L, checkNotNull(host.position(physical.getValue(3_000))).netReceivedThousandths)
            val historical = runBlocking { host.reviewDecisions.reviews(chat) }
            assertEquals(2, historical.count { it.reviewId.startsWith("w05:receive:") && it.receipt != null })
            assertEquals(1, historical.count { it.reviewId.startsWith("w05:stock-undo:") && it.receipt != null })
        }
    }

    private fun approve(
        reviews: ConversationReviewPort,
        pending: ConversationPlanReviewSnapshot,
        key: String,
    ): ConversationPlanReviewSnapshot = (runBlocking {
        reviews.decide(ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey(key))
    } as ConversationReviewDecision.Saved).review

    private fun receiveHost(
        selection: ActiveGenerationPointer,
        actor: String = principal,
        permitted: Boolean = true,
        interruptWorkspace: Boolean = false,
    ) = S4W05StockReceiveDevApplyHost(
        root, selection, gate, catalog, actor, 3L, { 3L }, { true }, { true },
        { requestActor, operation, target -> permitted && requestActor == actor &&
            operation == HouseholdStockReceiveReview.OPERATION && target == purchaseRef },
        { 3000L },
    ) { underlying ->
        object : TrustedConversationApplyReviewPort by underlying {
            override fun advance(
                intent: ConversationPlanReviewIntent,
                key: IdempotencyKey,
                attempts: ApplyAttemptLookupPort,
                receipts: ExecutionReceiptLookupPort,
            ): ConversationReviewDecision =
                if (interruptWorkspace && intent is ConversationPlanReviewIntent.ObserveCommitted) {
                    ConversationReviewDecision.StaleOrUnavailable
                } else underlying.advance(intent, key, attempts, receipts)
        }
    }

    private fun transition(origin: ActiveGenerationPointer): ActiveGenerationPointer {
        val operation = MaintenanceOperationId("global-a1-sql13-cutover")
        val selections = FileActiveGenerationPointer(root)
        fun descriptor(id: String) = StorageGenerationDescriptor(StorageGenerationId(id), "global-a1-dev", 20L)
        val stages = FaroV5ClosedHousehold12DevMaintenance.Stages(
            descriptor("global-a1-copy12"), descriptor("global-a1-bridge13"), descriptor("global-a1-active13"),
        )
        val key = "global-a1-isolated-fixture".toCharArray()
        val selected = FaroV5ClosedHousehold12DevMaintenance.acquire(
            root, gate, CanonicalPreparedPlanEnvelopeVerifier,
        ) { true }.use { owner ->
            val prepared = owner.prepare(operation, origin, stages, key) { true }
            assertEquals(origin, selections.read())
            assertEquals(BootstrapPhase.VALIDATED, FileBootstrapJournal(root).read(operation)?.phase)
            assertNull(owner.start(prepared, key) { false })
            assertEquals(origin, selections.read())
            checkNotNull(owner.start(prepared, key) { true })
        }
        key.fill('\u0000')
        assertEquals(ActiveGenerationPointer(origin.sequence + 1L, stages.v10Destination.generationId), selected)
        assertEquals(selected, selections.read())
        assertEquals(BootstrapPhase.COMPLETED, FileBootstrapJournal(root).read(operation)?.phase)
        return selected
    }

    private fun seedSql12(): ActiveGenerationPointer {
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("stock-ui-origin")))
        val seed = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("stock-ui-sql12"), "stock-ui-fixture", 1L),
        )
        val stage = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("stock-ui-finance9"), seed, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        val active = selectInitialSql12(origin, stage)
        S4W05ManualPurchaseDevApplyHost(
            root, active, gate, catalog, principal, "fixture-epoch", { "fixture-epoch" },
            { true }, { true }, { actor, operation, target ->
                actor == principal && operation == HouseholdManualPurchaseReview.OPERATION && target == purchaseRef
            }, { 1234L },
        ).use { host ->
            val selection = runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("stock-ui-select")) }
            assertTrue(selection is ConversationSelectionResult.Saved)
            val pending = checkNotNull(host.submit(chat, "stock-ui-purchase", purchaseRef, "yerba",
                HouseholdNeedQuantity(10_000L, HouseholdNeedUnit.UNIT)))
            val approval = runBlocking {
                host.reviewDecisions.decide(ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("stock-ui-approve"))
            } as ConversationReviewDecision.Saved
            assertTrue(host.executeAfterExplicitConfirmation(approval.review.context()) is ReviewedConversationApplyResult.Committed)
        }
        return active
    }

    /** Establish the initial isolated SQL12 fixture. SQL13 selection uses authenticated maintenance. */
    private fun selectInitialSql12(origin: ActiveGenerationPointer,
                                      target: StagedStorageGeneration): ActiveGenerationPointer {
        val active = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(origin.generationId, target.descriptor.generationId))
        val operation = MaintenanceOperationId("stock-ui-selection-12")
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, origin.generationId, active.generationId,
            BootstrapPhase.COMPLETED, materialWritesAccepted = true,
        ))
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, active.generationId)
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use {
            it.writeUTF("FARO_SQL12_EVIDENCE2_CUTOVER_V1")
            it.writeUTF(operation.value)
            it.writeLong(origin.sequence)
            it.writeUTF(origin.generationId.value)
            it.writeUTF(active.generationId.value)
            it.writeUTF(origin.generationId.value)
            repeat(3) { _ -> it.writeUTF("a".repeat(64)) }
        }
        val payload = stream.toByteArray()
        Files.write(root.resolve("bootstrap/sql12-evidence2-cutover.ticket"),
            payload + MessageDigest.getInstance("SHA-256").digest(payload))
        return active
    }
}
