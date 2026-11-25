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
import ar.com.faro.v5.owner.household.api.*
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

class S4W05StockReceiveDevApplyHostTest {
    @TempDir lateinit var root: Path

    private val chat = ConversationId("purchase-dev-test")
    private val ten = HouseholdNeedQuantity(10_000L, HouseholdNeedUnit.UNIT)

    @Test
    fun receiveSevenThenThreeAndRetryWithoutExtraPhysicalStock(): Unit = Rig().use { rig ->
        assertEquals(10_000L, rig.position().pendingThousandths)
        val pending = rig.submit("seven", 7_000L)
        val first = assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, rig.apply(rig.approve(pending)))
        val partial = rig.position()
        assertEquals(7_000L, partial.purchase.receivedThousandths)
        assertEquals(3_000L, partial.pendingThousandths)
        assertEquals(1L, partial.ledgerRevision)
        val repeated = rig.submit("seven", 7_000L)
        assertEquals(first.review, repeated)
        assertEquals(first, rig.apply(repeated))
        assertNull(rig.host.submit(chat, "seven", "purchase-1", "yerba", amount(6_000L)))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
            rig.apply(rig.approve(rig.submit("three", 3_000L))))
        rig.reopen()
        val full = rig.position()
        assertEquals(10_000L, full.purchase.receivedThousandths)
        assertEquals(0L, full.pendingThousandths)
        assertEquals(2L, full.ledgerRevision)
    }

    @Test
    fun incompatibleUnitsOverReceiptAndMissingPurchaseProduceNoReview(): Unit = Rig().use { rig ->
        assertNull(rig.host.submit(chat, "large", "purchase-1", "yerba", amount(11_000L)))
        assertNull(rig.host.submit(chat, "unit", "purchase-1", "yerba",
            HouseholdNeedQuantity(1_000L, HouseholdNeedUnit.GRAM)))
        assertNull(rig.host.submit(chat, "other", "other-purchase", "yerba", amount(1_000L)))
        assertNull(rig.host.submit(chat, "item", "purchase-1", "other-item", amount(1_000L)))
        assertEquals(0L, rig.position().purchase.receivedThousandths)
    }

    @Test
    fun unapprovedWrongGrantAndBackgroundCannotReceive(): Unit = Rig().use { rig ->
        val pending = rig.submit("not-yet", 7_000L)
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(pending))
        val approved = rig.approve(pending)
        rig.allowedOperation = OperationId("shopping.purchase.record")
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.allowedOperation = HouseholdStockReceiveReview.OPERATION
        rig.foreground = false
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.foreground = true
        assertEquals(0L, rig.position().purchase.receivedThousandths)
    }

    @Test
    fun differentPrincipalCannotReadOrReceiveConfirmedPurchase(): Unit = Rig().use { rig ->
        rig.reopen("another-human")
        assertNull(rig.host.position("purchase-1"))
        assertNull(rig.host.submit(chat, "other-owner", "purchase-1", "yerba", amount(1_000L)))
    }

    @Test
    fun staleLedgerReviewCannotOverReceive(): Unit = Rig().use { rig ->
        val existing = rig.approve(rig.submit("older", 4_000L))
        val newer = rig.approve(rig.submit("newer", 7_000L))
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java, rig.apply(newer))
        val refreshed = runBlocking { rig.host.reviewDecisions.reviews(chat) }
            .single { it.reviewId == existing.reviewId }
        assertFalse(rig.apply(refreshed) is ReviewedConversationApplyResult.Committed)
        assertEquals(7_000L, rig.position().purchase.receivedThousandths)
        assertEquals(1L, rig.position().ledgerRevision)
    }

    @Test
    fun staleEpochAndFullPointerAbaDenyApprovedReceipt(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("signed", 7_000L))
        rig.epoch++
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
        rig.epoch--
        assertEquals(0L, rig.position().purchase.receivedThousandths)
        val other = StorageGenerationId("stock-aba-alternate")
        checkNotNull(rig.pointer.compareAndSet(rig.active.generationId, other))
        checkNotNull(rig.pointer.compareAndSet(other, rig.active.generationId))
        assertInstanceOf(ReviewedConversationApplyResult.Denied::class.java, rig.apply(approved))
    }

    @Test
    fun reconcilesReceiptAfterWorkspaceInterruptionWithoutReplay(): Unit = Rig().use { rig ->
        val approved = rig.approve(rig.submit("interrupted", 7_000L))
        rig.interruptWorkspace = true
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        assertEquals(7_000L, rig.position().purchase.receivedThousandths)
        rig.reopen()
        val applying = runBlocking { rig.host.reviewDecisions.reviews(chat) }.single { it.reviewId == approved.reviewId }
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
            rig.host.reconcileCommittedOnly(applying.context()))
        assertEquals(7_000L, rig.position().purchase.receivedThousandths)
        assertEquals(1L, rig.position().ledgerRevision)
    }

    @Test
    fun forcedTransactionRollbackLeavesLedgerAndRevisionUnchanged(): Unit = Rig().use { rig ->
        rig.host.close()
        FaroV5GenerationStoreGraph.openActiveHousehold12DevForStockReceiveHost(
            root, rig.active, rig.gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        ).use { session ->
            val generation = HouseholdReviewGeneration(rig.active.generationId.value, rig.active.sequence)
            val rows = session.stockReceipts("manual-human", generation)
            val observed = checkNotNull(rows.position("purchase-1"))
            val request = HouseholdStockReceiveDevRequest(HouseholdStockReceiveReview.OPERATION,
                HouseholdStockReceiveDevContext("manual-human", rig.epoch, generation, true, true),
                "yerba", "purchase-1", observed.purchase.revision, "forced-rollback", amount(7_000L),
            )
            session.transactions.run(TransactionId("forced-rollback")) { scope ->
                assertTrue(rows.receive(HouseholdStockReceiveReview(request, observed.ledgerRevision), 0L, 1L, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(observed, rows.position("purchase-1"))
        }
        rig.reopen()
        assertEquals(0L, rig.position().ledgerRevision)
        assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
            rig.apply(rig.approve(rig.submit("after-rollback", 7_000L))))
    }

    private fun amount(thousandths: Long) = HouseholdNeedQuantity(thousandths, HouseholdNeedUnit.UNIT)

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        private val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("purchase-origin")))
        private val stage = prepareSql12()
        val active = checkNotNull(pointer.compareAndSet(origin.generationId, stage.descriptor.generationId))
        var foreground = true
        var epoch = 1L
        var allowedOperation = HouseholdStockReceiveReview.OPERATION
        var interruptWorkspace = false
        var host: S4W05StockReceiveDevApplyHost

        init {
            seedCompletedCutoverFixture()
            S4W05ManualPurchaseDevApplyHost(
                root, active, gate, BuiltInContractCatalog.create(), "manual-human", "purchase-test",
                { "purchase-test" }, { true }, { true },
                { _, operation, _ -> operation == OperationId("shopping.purchase.record") },
                { 1234L },
            ).use { purchase ->
                val selected = runBlocking { purchase.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("select")) }
                assertInstanceOf(ConversationSelectionResult.Saved::class.java, selected)
                val pending = checkNotNull(purchase.submit(chat, "manual-source", "purchase-1", "yerba", ten))
                val approved = approve(purchase.reviewDecisions, pending)
                assertInstanceOf(ReviewedConversationApplyResult.Committed::class.java,
                    purchase.executeAfterExplicitConfirmation(approved.context()))
            }
            host = open()
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

        fun open(principal: String = "manual-human") = S4W05StockReceiveDevApplyHost(
            root, active, gate, BuiltInContractCatalog.create(), principal, epoch, { epoch },
            { true }, { foreground },
            { actor, operation, _ -> actor == principal && operation == allowedOperation },
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

        fun reopen(principal: String = "manual-human") {
            host.close()
            host = open(principal)
        }

        fun submit(key: String, quantity: Long) =
            checkNotNull(host.submit(chat, key, "purchase-1", "yerba", amount(quantity)))

        fun approve(pending: ConversationPlanReviewSnapshot) = approve(host.reviewDecisions, pending)

        private fun approve(port: ConversationReviewPort, pending: ConversationPlanReviewSnapshot) =
            (runBlocking { port.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-" + pending.reviewId),
            ) } as ConversationReviewDecision.Saved).review

        fun position() = checkNotNull(host.position("purchase-1"))
        fun apply(approved: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(approved.context())
        override fun close() = host.close()
    }
}
