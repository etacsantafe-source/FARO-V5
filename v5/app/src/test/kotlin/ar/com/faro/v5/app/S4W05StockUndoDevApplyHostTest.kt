package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold12StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold13StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRequest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Test fixture uses REAL Workspace+Reality COMMITTED source from N-035, then stages SQL13. */
class S4W05StockUndoDevApplyHostTest {
    @TempDir lateinit var root: Path
    private val chat = ConversationId("undo-stock-chat")
    private val catalog = BuiltInContractCatalog.create()
    private val operation = OperationId("undo.execute")

    @Test
    fun committedOriginalSevenOfTenCanUndoToThreeWithIndependentReceipt(): Unit = Rig().use { rig ->
        val before = requireNotNull(rig.host.position(rig.receiptSeven))
        assertEquals(10_000L, before.netReceivedThousandths)
        val pending = rig.submit("undo-seven", rig.sourceSeven)
        val committed = assertInstanceOf(
            ReviewedConversationApplyResult.Committed::class.java, rig.apply(rig.approve(pending)),
        )
        assertNotEquals(rig.originalSevenReceipt, committed.receipt)
        assertNull(rig.host.position(rig.receiptSeven))
        val remainder = requireNotNull(rig.host.position(rig.receiptThree))
        assertEquals(3_000L, remainder.netReceivedThousandths)
        assertEquals(3L, remainder.ledgerRevision)
        assertEquals(committed, rig.host.executeAfterExplicitConfirmation(committed.review.context()))
    }

    @Test
    fun sourceMustBeVerifiedCommittedNotJustAStockRow(): Unit = Rig().use { rig ->
        assertNull(rig.host.submitUndo(rig.sourceSeven.copy(reviewId = "fake-source"), "fake"))
        assertNull(rig.host.submitUndo(rig.sourceSeven.copy(
            planFingerprint = PlanFingerprint("forged-fingerprint"),
        ), "fingerprint"))
        assertEquals(10_000L, requireNotNull(rig.host.position(rig.receiptSeven)).netReceivedThousandths)
    }

    @Test
    fun approvalAndSeparateN016GrantAreMandatory(): Unit = Rig().use { rig ->
        val pending = rig.submit("consent", rig.sourceSeven)
        assertFalse(rig.host.executeAfterExplicitConfirmation(pending.context())
            is ReviewedConversationApplyResult.Committed)
        val approved = rig.approve(pending)
        rig.allowedOperation = OperationId("stock.receive")
        assertFalse(rig.host.executeAfterExplicitConfirmation(approved.context())
            is ReviewedConversationApplyResult.Committed)
        rig.allowedOperation = operation
        rig.foreground = false
        assertFalse(rig.host.executeAfterExplicitConfirmation(approved.context())
            is ReviewedConversationApplyResult.Committed)
        rig.foreground = true
        assertEquals(10_000L, requireNotNull(rig.host.position(rig.receiptSeven)).netReceivedThousandths)
    }

    @Test
    fun staleApprovedLedgerRevisionCannotApplyAnotherStockState(): Unit = Rig().use { rig ->
        val old = rig.approve(rig.submit("old-seven", rig.sourceSeven))
        val newer = rig.approve(rig.submit("new-three", rig.sourceThree))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, rig.apply(newer))
        val freshOld = runBlocking { rig.host.reviewDecisions.reviews(chat) }
            .single { it.reviewId == old.reviewId }
        assertFalse(rig.apply(freshOld) is ReviewedConversationApplyResult.Committed)
        assertEquals(7_000L, requireNotNull(rig.host.position(rig.receiptSeven)).netReceivedThousandths)
    }

    @Test
    fun oneReceiptCannotBeUndoneTwiceEvenWithNewUserIntent(): Unit = Rig().use { rig ->
        val first = rig.apply(rig.approve(rig.submit("first", rig.sourceSeven)))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, first)
        assertNull(rig.host.submitUndo(rig.sourceSeven, "second"))
        rig.reopen()
        assertNull(rig.host.submitUndo(rig.sourceSeven, "after-reopen"))
        assertNull(rig.host.position(rig.receiptSeven))
        assertEquals(3_000L, requireNotNull(rig.host.position(rig.receiptThree)).netReceivedThousandths)
    }

    @Test
    fun aDifferentOwnerOrHostEpochCannotUndoTheOriginal(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("epoch", rig.sourceSeven))
        rig.epoch += 1
        assertFalse(rig.apply(approved) is ReviewedConversationApplyResult.Committed)
        rig.epoch -= 1
        rig.reopen("another-human")
        assertNull(rig.host.position(rig.receiptSeven))
        assertNull(rig.host.submitUndo(rig.sourceSeven, "cross-owner"))
    }

    @Test
    fun pointerAbaAfterApprovalDeniesAndLeavesHistory(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("aba", rig.sourceSeven))
        val other = StorageGenerationId("alternate-aba")
        checkNotNull(rig.pointer.compareAndSet(rig.selected.generationId, other))
        checkNotNull(rig.pointer.compareAndSet(other, rig.selected.generationId))
        assertFalse(rig.apply(approved) is ReviewedConversationApplyResult.Committed)
    }

    @Test
    fun crashAfterRealityCommitReconcilesWithoutSecondUndo(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("interrupted", rig.sourceSeven))
        rig.interruptWorkspace = true
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        rig.reopen()
        val applying = runBlocking { rig.host.reviewDecisions.reviews(chat) }
            .single { it.reviewId == approved.reviewId }
        val reconciled = assertInstanceOf(
            ReviewedConversationApplyResult.Committed::class.java,
            rig.host.reconcileCommittedOnly(applying.context()),
        )
        assertEquals(approved.reviewId, reconciled.review.reviewId)
        assertNull(rig.host.position(rig.receiptSeven))
        assertEquals(3_000L, requireNotNull(rig.host.position(rig.receiptThree)).netReceivedThousandths)
    }

    @Test
    fun rollbackOfG0CompensationCannotPersistPartialEffect(): Unit = Rig().use { rig ->
        rig.host.close()
        FaroV5GenerationStoreGraph.openActiveHousehold13DevForUndoHost(
            root, rig.selected, rig.gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        ).use { session ->
            val gen = HouseholdReviewGeneration(rig.selected.generationId.value, rig.selected.sequence)
            val rows = session.compensations("manual-human", gen)
            val position = requireNotNull(rows.position(rig.receiptSeven))
            session.transactions.run(TransactionId("force-undo-rollback")) { scope ->
                assertTrue(rows.compensate(
                    RealityHousehold13CompensationRequest(
                        gen, position.receiptId, position.purchaseRef, "force-comp", "force-intent",
                        position.quantityThousandths, position.unit, position.ledgerRevision, 6000L,
                    ), scope,
                ))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertNotNull(rows.position(rig.receiptSeven))
        }
        rig.host = rig.open()
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
            rig.apply(rig.approve(rig.submit("after-rollback", rig.sourceSeven))))
    }

    private fun approve(port: ConversationReviewPort, pending: ConversationPlanReviewSnapshot) =
        (runBlocking { port.decide(
            ConversationPlanReviewIntent.Approve(pending.context()),
            IdempotencyKey("approve-" + pending.reviewId),
        ) } as ConversationReviewDecision.Saved).review

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val original = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("stock-undo-origin")))
        val stage12 = sql12Stage()
        val active12 = checkNotNull(pointer.compareAndSet(original.generationId, stage12.descriptor.generationId))
        var foreground = true
        var epoch = 5L
        var allowedOperation = operation
        var interruptWorkspace = false
        lateinit var sourceSeven: ConversationPlanReviewContext
        lateinit var sourceThree: ConversationPlanReviewContext
        lateinit var originalSevenReceipt: ExecutionReceipt
        lateinit var receiptSeven: String
        lateinit var receiptThree: String
        lateinit var selected: ActiveGenerationPointer
        lateinit var host: S4W05StockUndoDevApplyHost

        init {
            seedSql12Ticket()
            S4W05ManualPurchaseDevApplyHost(
                root, active12, gate, catalog, "manual-human", "purchase-test",
                { "purchase-test" }, { true }, { true },
                { _, op, _ -> op == OperationId("shopping.purchase.record") },
                { 1234L },
            ).use { purchase ->
                val outcome = runBlocking {
                    purchase.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("undo-select"))
                }
                assertInstanceOf(ConversationSelectionResult.Saved::class.java, outcome)
                val pending = checkNotNull(purchase.submit(
                    chat, "purchase-intent", "purchase-1", "yerba",
                    HouseholdNeedQuantity(10_000L, HouseholdNeedUnit.UNIT),
                ))
                assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
                    purchase.executeAfterExplicitConfirmation(approve(purchase.reviewDecisions, pending).context()))
            }
            S4W05StockReceiveDevApplyHost(
                root, active12, gate, catalog, "manual-human", 3L, { 3L },
                { true }, { true },
                { _, op, _ -> op == OperationId("stock.receive") },
                { 3000L },
            ).use { receive ->
                fun commit(key: String, amount: Long): ReviewedConversationApplyResult.Committed {
                    val pending = checkNotNull(receive.submit(
                        chat, key, "purchase-1", "yerba",
                        HouseholdNeedQuantity(amount, HouseholdNeedUnit.UNIT),
                    ))
                    return assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
                        receive.executeAfterExplicitConfirmation(approve(receive.reviewDecisions, pending).context()))
                }
                val seven = commit("seven", 7_000L)
                val three = commit("three", 3_000L)
                sourceSeven = seven.review.context()
                sourceThree = three.review.context()
                originalSevenReceipt = seven.receipt
                // The physical stock receipt ID is the ORIGINAL RECEIVE intent key, not Apply receipt.
                val prefix = "w05:receive:"
                assertTrue(sourceSeven.reviewId.startsWith(prefix))
            }
            // Decode both REAL sealed COMMITTED N-035 envelopes before migrating SQL12.
            FaroV5GenerationStoreGraph.openActiveHousehold12DevForStockReceiveHost(
                root, active12, gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
            ).use { session ->
                fun physical(context: ConversationPlanReviewContext): String {
                    val sealed = checkNotNull(session.workspace.loadCommitted(context))
                    val plan = PreparedPlanEnvelopeCodec.decode(sealed.envelope())
                    val node = plan.applyPlan.nodesInTopologicalOrder().single()
                    return checkNotNull(
                        HouseholdStockReceiveReview.decode(node.preparedOperation.payload)
                    ).request.intentKey
                }
                receiptSeven = physical(sourceSeven)
                receiptThree = physical(sourceThree)
                assertNotEquals(originalSevenReceipt.receiptId.value, receiptSeven)
            }
            selected = sql13StageAndTicket()
            host = open()
        }

        private fun sql12Stage(): StagedStorageGeneration {
            val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("stock-undo-sql12"), "fixture-dev", 1L),
            )
            val stage = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
                MaintenanceOperationId("undo-finance9"), fresh, original, "a".repeat(64), 2L,
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
            return stage
        }

        private fun seedSql12Ticket() {
            val op = MaintenanceOperationId("stock-undo-sql12-selection")
            FileBootstrapJournal(root).create(BootstrapJournalEntry(
                op, MaintenanceOperationKind.RESTORE, original.generationId,
                active12.generationId, BootstrapPhase.COMPLETED, materialWritesAccepted = true,
            ))
            FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, active12.generationId)
            sealTicket("sql12", op, original, active12.generationId)
        }

        private fun sql13StageAndTicket(): ActiveGenerationPointer {
            // Isolated fixture: G0, FBS2v10 and cutover were independently B7 certified.
            // Copy the five CLOSED SQL12 owners and migrate the target Reality to SQL13.
            val target = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("stock-undo-sql13"), "fixture-dev", 3L),
            )
            for (file in GenerationStoreFiles.WORKSPACE_V2) {
                Files.copy(stage12.directory.resolve(file), target.directory.resolve(file),
                    StandardCopyOption.REPLACE_EXISTING)
            }
            RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(
                target.directory.resolve(GenerationStoreFiles.REALITY))
            val selected = checkNotNull(pointer.compareAndSet(
                active12.generationId, target.descriptor.generationId))
            val op = MaintenanceOperationId("stock-undo-sql13-selection")
            FileBootstrapJournal(root).create(BootstrapJournalEntry(
                op, MaintenanceOperationKind.RESTORE, active12.generationId,
                target.descriptor.generationId, BootstrapPhase.COMPLETED,
                materialWritesAccepted = true,
            ))
            FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, target.descriptor.generationId)
            sealTicket("sql13", op, active12, target.descriptor.generationId)
            return selected
        }

        private fun sealTicket(
            version: String, operation: MaintenanceOperationId,
            origin: ActiveGenerationPointer, target: StorageGenerationId,
        ) {
            val stream = ByteArrayOutputStream()
            DataOutputStream(stream).use { out ->
                out.writeUTF(if (version == "sql12") "FARO_SQL12_EVIDENCE2_CUTOVER_V1"
                    else "FARO_SQL13_EVIDENCE2_CUTOVER_V1")
                out.writeUTF(operation.value)
                out.writeLong(origin.sequence)
                out.writeUTF(origin.generationId.value)
                out.writeUTF(target.value)
                out.writeUTF(origin.generationId.value)
                repeat(3) { out.writeUTF("a".repeat(64)) }
            }
            val payload = stream.toByteArray()
            val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
            Files.write(root.resolve("bootstrap/" + version + "-evidence2-cutover.ticket"), payload + checksum)
        }

        fun open(principal: String = "manual-human"): S4W05StockUndoDevApplyHost =
            S4W05StockUndoDevApplyHost(
                root, selected, gate, catalog, principal, epoch, { epoch },
                { true }, { foreground },
                { actor, op, _ -> actor == principal && op == allowedOperation },
                { 6000L },
            ) { delegate ->
                object : TrustedConversationApplyReviewPort by delegate {
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
                        return delegate.advance(intent, key, attempts, receipts)
                    }
                }
            }

        fun submit(key: String, context: ConversationPlanReviewContext) =
            checkNotNull(host.submitUndo(context, key))

        fun approve(pending: ConversationPlanReviewSnapshot) =
            this@S4W05StockUndoDevApplyHostTest.approve(host.reviewDecisions, pending)

        fun apply(approved: ConversationPlanReviewSnapshot): ReviewedConversationApplyResult {
            val outcome = host.executeAfterExplicitConfirmation(approved.context())
            if (outcome == ReviewedConversationApplyResult.RecoveryRequired) {
                val review = runBlocking { host.reviewDecisions.reviews(chat) }
                    .firstOrNull { it.reviewId == approved.reviewId }
                val position = host.position(receiptSeven)
                throw AssertionError(
                    "G1B_DIAG ${host.diagnosticStages}; " +
                        "reviewState=${review?.state}; hasTx=${review?.transactionId != null}; " +
                        "physicalStillAvailable=${position != null}"
                )
            }
            return outcome
        }

        fun reopen(principal: String = "manual-human") {
            host.close()
            host = open(principal)
        }

        override fun close() = host.close()
    }
}
