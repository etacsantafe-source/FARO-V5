@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package ar.com.faro.v5.app

import ar.com.faro.v5.generation.*
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.owner.household.api.*
import kotlinx.coroutines.runBlocking

import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Journaled isolated cutover from COMPLETED SQL14 ACTIVE to authenticated SQL15. */
class S4W05StockConsumptionDevApplyHostTest {
    @TempDir lateinit var root: Path
    private val writeGate = SingleProcessMaterialWriteGate()
    private val gate = MaterialWriteGateQuiescence(writeGate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-a2b0c1-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-a2c0c2-v12-cutover")
    private val op14 = MaintenanceOperationId("w05-a2c0c2-v11-precursor")
    private val precursorOperation = MaintenanceOperationId("w05-a2b0c1-v9-precursor")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household15Fbs2Snapshot,
        val ciphertext: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-a2b0c1-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-a2b0c1-finance-" + id),
            fresh, origin, "a".repeat(64), 1000L)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(finance, pointer).close()
        return finance
    }

    private fun commercial(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()

    private fun observation(source: String) = CommercialPriceObservation(
        productKey = CommercialProductKey("yerba-1000"),
        merchant = CommercialMerchant("comercio-local"),
        currencyCode = CommercialCurrencyCode("ARS"),
        amountMinor = 12000L,
        sourceKind = CommercialEvidenceSourceKind.MANUAL,
        sourceRef = "manual:" + source,
        observedAtEpochMillis = 100L,
    )

    private fun knownTerms() = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
        issuedAtEpochMillis = 100L,
        validFromEpochMillis = 110L,
        validUntilEpochMillis = 200L,
        presentationQuantityThousandths = 1000L,
        presentationUnit = CommercialPresentationUnit.GRAM,
        packCount = 1,
        paymentMethod = CommercialPaymentMethod.DEBIT,
        eligibility = CommercialEligibility.GENERAL,
        conditions = "Solo socios",
        promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
        promotionValue = 1200L,
        promotionSourceRef = "manual:flyer",
    )

    private fun toEvidence2(stage: StagedStorageGeneration, withRows: Boolean) {
        if (withRows) CommercialEvidenceStore(
            fileName = commercial(stage), clock = { 100L },
            evidenceIdFactory = { CommercialEvidenceId("c0b2b-legacy") },
        ).use {
            it.record(observation("legacy"), IdempotencyKey("c0b2b-legacy"),
                PayloadFingerprint("c".repeat(64)))
        }
        var id = 0
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 300L },
            evidenceIdFactory = { CommercialEvidenceId("c0b2b-typed-" + (++id)) },
        ).use { owner ->
            if (withRows) {
                owner.recordWithTypedTerms(
                    observation("unknown"), CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("c0b2b-unknown"), PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(
                    observation("known"), knownTerms(),
                    IdempotencyKey("c0b2b-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }

    private fun source(origin: ActiveGenerationPointer, evidence2: Boolean): StagedStorageGeneration {
        val src = stage("w05-a2b0c1-source", origin)
        val reality = src.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        if (evidence2) toEvidence2(src, true)
        if (evidence2) FaroV5GenerationStoreGraph.requireSealedHousehold10Evidence2ArchiveStage(src)
        else FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(src)
        return src
    }

    private fun target(
        origin: ActiveGenerationPointer,
        evidence2: Boolean,
        id: String = "w05-a2b0c1-target",
    ): StagedStorageGeneration =
        stage(id, origin).also {
            if (evidence2) {
                toEvidence2(it, false)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(it, allowAuxiliary3 = false)
            } else {
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(it, allowAuxiliary3 = false)
            }
        }

    private fun fixture(): Fixture {
        val evidence2 = true
        val initial = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("w05-cutover-initial")))
        val old = stage("w05-cutover-origin", initial)
        val oldReality = old.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(oldReality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(oldReality)
        toEvidence2(old, false)
        val origin = checkNotNull(pointer.compareAndSet(initial.generationId, old.descriptor.generationId))
        val src = source(origin, evidence2)
        val dest = target(origin, evidence2)
        val archiveCoordinator = FaroV5Household10ArchiveCoordinator(root, gate, {})
        val captureOp = MaintenanceOperationId("w05-a2b0c1-capture")
        val backup = BackupSetId("w05-a2b0c1-base-backup")
        val archivedBase = if (evidence2) {
            archiveCoordinator.captureAuthenticatedEvidence2(captureOp, backup, src, origin, "5.0-dev", 3000L, key)
        } else {
            archiveCoordinator.captureAuthenticated(captureOp, backup, src, origin, "5.0-dev", 3000L, key)
        }
        val openedBase = if (evidence2) crypto.openWithHouseholdEvidence2(archivedBase, key)
            else crypto.openWithHousehold(archivedBase, key)
        val snapshotV7 = when (val result = openedBase) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(result.reason)
        }
        val receipt = Household11ReceiptBackupSection(2L, listOf(
            Household11ReceiptBackupRow("receipt-1", "intent-1", "purchase",
                "yerba", 7000, "UNIT", 0L, 3000L, 1L),
            Household11ReceiptBackupRow("receipt-2", "intent-2", "purchase",
                "yerba", 3000, "UNIT", 0L, 3001L, 2L),
        ))
        val payload12 = FaroV5Household12Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(snapshotV7, receipt),
            listOf(Household12PurchaseBackupRow(
                "purchase", "yerba", 10_000, "UNIT", "local-human", 3_000, "MANUAL_EXPLICIT", 1,
            )),
        )
        val bytes12 = crypto.sealWithHouseholdPurchases12(payload12, key)
        val priorReplay = FaroV5Household12V9StagedReplay(root, gate, {})
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(dest.descriptor.generationId.value),
            priorReplay.restoreAuthenticatedV9(precursorOperation, dest, origin, bytes12, key))
        FaroV5Household12Evidence2DevJournaledCutover.acquire(root, writeGate, { true }).use { prior ->
            assertEquals(
                FaroV5Household12Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND,
                prior.authorize(precursorOperation, dest, origin, bytes12, key) { true }.status,
            )
            assertEquals(
                FaroV5Household12Evidence2DevJournaledCutover.Status.ACTIVE_TWELVE,
                prior.start(precursorOperation, dest, origin, bytes12, key) { true }.status,
            )
        }
        val selected12 = checkNotNull(pointer.read())
        check(FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection(root, selected12))
        val target13 = target(selected12, evidence2, "w05-a2b0c1-sql13-target")
        val op13 = MaintenanceOperationId("w05-a2b0c1-sql13-restore")
        val historical = FaroV5Household13Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(snapshotV7, receipt.copy(revision = 3)),
            payload12.purchases,
            listOf(Household13CompensationBackupRow(
                "undo-1", "undo-intent-1", "receipt-1", "purchase",
                7_000, "UNIT", "local-human", 3_002L, 3L,
            )),
        )
        val v10 = crypto.sealWithHouseholdCompensation13(historical, key)
        val replay13 = FaroV5Household13V10StagedReplay(root, gate, {})
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target13.descriptor.generationId.value),
            replay13.restoreAuthenticatedV10(op13, target13, selected12, v10, key))
        FaroV5Household13Evidence2DevJournaledCutover.acquire(root, writeGate, { true }).use { owner ->
            assertEquals(FaroV5Household13Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND,
                owner.authorize(op13, target13, selected12, v10, key) { true }.status)
            assertEquals(FaroV5Household13Evidence2DevJournaledCutover.Status.ACTIVE_THIRTEEN,
                owner.start(op13, target13, selected12, v10, key) { true }.status)
        }
        val selected13 = checkNotNull(pointer.read())
        assertTrue(FaroV5Household13Evidence2DevJournaledCutover.hasCompletedSelection(root, selected13))
        val target14 = target(selected13, evidence2, "w05-a2b0c1-sql14-target")
        val reserve = Household14ReservationBackupRow(
            "reserve", "reserve-intent", Household14ReservationEventType.RESERVE,
            "reserve", "yerba", "UNIT", 2_000, "local-human", 3_003L, 4L,
        )
        val release = reserve.copy(eventId = "release", intentKey = "release-intent",
            type = Household14ReservationEventType.RELEASE, quantityThousandths = 1_000,
            recordedAtMs = 3_004L, revision = 5L)
        val payload = FaroV5Household14Fbs2Snapshot(historical, listOf(reserve, release))
        val v11 = crypto.sealWithHouseholdReservations14(payload, key)
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target14.descriptor.generationId.value),
            FaroV5Household14V11StagedReplay(root, gate, {}).restoreAuthenticatedV11(op14, target14, selected13, v11, key))
        FaroV5Household14Evidence2DevJournaledCutover.acquire(root, writeGate, { true }).use { previous ->
            assertEquals(FaroV5Household14Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND,
                previous.authorize(op14, target14, selected13, v11, key) { true }.status)
            assertEquals(FaroV5Household14Evidence2DevJournaledCutover.Status.ACTIVE_FOURTEEN,
                previous.start(op14, target14, selected13, v11, key) { true }.status)
        }
        val selected14 = checkNotNull(pointer.read())
        check(FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root, selected14))
        // Fixture-only: rebuild the canonical FBS1 manifest WITH its fingerprint.
        // Copying sourceGenerationId alone is invalid and must remain rejected by crypto.
        val oldFive = snapshotV7.base.base
        val oldFour = oldFive.fourStore
        val reauthorizedFour = SemanticBackupCaptureCoordinator(
            gate,
            object : RealityBackupPort {
                override fun snapshot(): RealityBackupSnapshot = oldFour.reality
                override fun restoreOrVerify(snapshot: RealityBackupSnapshot): RealityBackupSnapshot =
                    error("C2 fixture cannot restore Reality")
            },
            object : CommercialEvidenceBackupPort {
                override fun snapshot(): CommercialEvidenceBackupSnapshot = oldFour.commercialEvidence
                override fun restoreOrVerify(
                    snapshot: CommercialEvidenceBackupSnapshot,
                ): CommercialEvidenceBackupSnapshot = error("C2 fixture cannot restore Evidence2")
            },
            object : WebEvidenceBackupPort {
                override fun snapshot(): WebEvidenceBackupSnapshot = oldFour.webEvidence
                override fun restoreOrVerify(snapshot: WebEvidenceBackupSnapshot): WebEvidenceBackupSnapshot =
                    error("C2 fixture cannot restore WebEvidence")
            },
            object : AuxiliaryBackupPort {
                override fun snapshot(): AuxiliaryBackupSnapshot = oldFour.auxiliary
                override fun restoreOrVerify(snapshot: AuxiliaryBackupSnapshot): AuxiliaryBackupSnapshot =
                    error("C2 fixture cannot restore Auxiliary")
            },
        ).capture(
            MaintenanceOperationId("a2c0c2-valid-selected14-fbs1"),
            oldFour.manifest.backupSetId,
            selected14.generationId,
            oldFour.manifest.productVersion,
            oldFour.manifest.createdAtEpochMillis,
        )
        check(SemanticBackupSetIntegrity.matches(reauthorizedFour))
        val rebasedV7 = snapshotV7.copy(base = snapshotV7.base.copy(
            base = oldFive.copy(fourStore = reauthorizedFour),
        ))
        val rebasedHistorical = historical.copy(base = historical.base.copy(base = rebasedV7))
        val rebased14 = payload.copy(base = rebasedHistorical)
        val consume = Household15ConsumptionBackupRow(
            "consume", "consume-intent", Household15ConsumptionEventType.CONSUME,
            "consume", "yerba", "UNIT", 1_000, "local-human", "purchase",
            null, 3_005L, 3_006L, 6L,
        )
        val consumeUndo = consume.copy(
            eventId = "consume-undo", intentKey = "consume-undo-intent",
            type = Household15ConsumptionEventType.CONSUME_UNDO,
            quantityThousandths = 500, occurredAtMs = 3_007L,
            recordedAtMs = 3_008L, revision = 7L,
        )
        val modern = FaroV5Household15Fbs2Snapshot(rebased14, listOf(consume, consumeUndo))
        val v12 = crypto.sealWithHouseholdConsumption15(modern, key)
        val target15 = target(selected14, evidence2, "w05-a2c0c2-sql15-target")
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target15.descriptor.generationId.value),
            FaroV5Household15V12StagedReplay(root, gate, {}).restoreAuthenticatedV12(
                op, target15, selected14, v12, key))
        val evidence = FaroV5Household15PreCutoverReadback(root, gate, {}).verifyOnly(
            op, target15, selected14, v12, key)
        assertEquals(selected14.generationId, evidence.sourceGenerationId)
        return Fixture(selected14, target15, modern, v12)
    }

    private val journal get() = FileBootstrapJournal(root)
    private val replayFence get() = FileGenerationReplayFence(root)
    private val awaitForeground get() = FaroV5Household15Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND
    private val active get() = FaroV5Household15Evidence2DevJournaledCutover.Status.ACTIVE_FIFTEEN
    private val blocked get() = FaroV5Household15Evidence2DevJournaledCutover.Status.BLOCKED
    private val rolledBack get() = FaroV5Household15Evidence2DevJournaledCutover.Status.ROLLED_BACK

    private fun owner(
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        healthy: () -> Boolean = { true },
        prepared: () -> Unit = {},
    ) = FaroV5Household15Evidence2DevJournaledCutover.acquire(
        root, writeGate, { true }, fault, healthy, afterSwitchPrepared = prepared,
    )

    private fun authorize(owner: FaroV5Household15Evidence2DevJournaledCutover, f: Fixture) =
        owner.authorize(op, f.target, f.origin, f.ciphertext, key) { true }

    private fun start(owner: FaroV5Household15Evidence2DevJournaledCutover, f: Fixture) =
        owner.start(op, f.target, f.origin, f.ciphertext, key) { true }

    private val chat = ConversationId("consumption-dev-chat")
    private val catalog = BuiltInContractCatalog.create()

    private fun prepareSelection(): ActiveGenerationPointer {
        val f = fixture()
        owner().use {
            check(authorize(it, f).status == awaitForeground)
            check(start(it, f).status == active)
        }
        return checkNotNull(pointer.read()).also {
            check(FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection(root, it))
        }
    }

    private fun qty(n: Long) = HouseholdNeedQuantity(n, HouseholdNeedUnit.UNIT)
    private fun approve(host: S4W05StockConsumptionDevApplyHost, pending: ConversationPlanReviewSnapshot) =
        (runBlocking { host.reviewDecisions.decide(
            ConversationPlanReviewIntent.Approve(pending.context()),
            IdempotencyKey("approve-" + pending.reviewId),
        ) } as ConversationReviewDecision.Saved).review

    private inner class Rig : AutoCloseable {
        val selected = prepareSelection()
        var foreground = true
        var epoch = 11L
        var allowed = true
        var interruptWorkspace = false
        var host = open()

        init {
            assertTrue(runBlocking { host.chatSelection.createAndSelect(
                chat, 1L, IdempotencyKey("select-consumption-chat"))
            } is ConversationSelectionResult.Saved)
        }

        fun open(actor: String = "local-human") = S4W05StockConsumptionDevApplyHost(
            root, selected, writeGate, catalog, actor, epoch, { epoch }, { true },
            { foreground }, { name, op, item ->
                allowed && name == "local-human" &&
                    op == HouseholdStockConsumptionReview.OPERATION && item == "yerba"
            }, { 9_000L },
        ) { real ->
            object : TrustedConversationApplyReviewPort by real {
                override fun advance(
                    intent: ConversationPlanReviewIntent, key: IdempotencyKey,
                    attempts: ApplyAttemptLookupPort, receipts: ExecutionReceiptLookupPort,
                ): ConversationReviewDecision {
                    if (intent is ConversationPlanReviewIntent.ObserveCommitted && interruptWorkspace) {
                        interruptWorkspace = false
                        return ConversationReviewDecision.StaleOrUnavailable
                    }
                    return real.advance(intent, key, attempts, receipts)
                }
            }
        }

        fun position() = checkNotNull(host.position("yerba", qty(1L), "purchase"))
        fun submit(id: String, amount: Long) =
            checkNotNull(host.submit(chat, id, "yerba", qty(amount), "purchase"))
        fun apply(review: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(review.context())
        fun commit(id: String, amount: Long): ReviewedConversationApplyResult.Committed =
            assertIsCommitted(apply(approve(host, submit(id, amount))))
        fun assertIsCommitted(result: ReviewedConversationApplyResult) =
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                ReviewedConversationApplyResult.Committed::class.java, result)
        fun reopen() { host.close(); host = open() }
        override fun close() = host.close()
    }

    @Test
    fun consumptionRequiresExplicitApprovalAndPersistsSingleReceipt(): Unit = Rig().use { rig ->
        assertEquals(2_500L, rig.position().physicalThousandths)
        assertEquals(1_000L, rig.position().reservedThousandths)
        val pending = rig.submit("consume-1", 1_000L)
        assertEquals(2_500L, rig.position().physicalThousandths)
        assertFalse(rig.apply(pending) is ReviewedConversationApplyResult.Committed)
        val approved = approve(rig.host, pending)
        val result = rig.assertIsCommitted(rig.apply(approved))
        assertEquals(1_500L, rig.position().physicalThousandths)
        assertEquals(1_000L, rig.position().reservedThousandths)
        assertEquals(result, rig.apply(result.review))
        rig.reopen()
        assertEquals(1_500L, rig.position().physicalThousandths)
        assertEquals(1_000L, rig.position().reservedThousandths)
        assertEquals(8L, rig.position().revision)
    }

    @Test
    fun staleRevisionOversubscriptionAndConsentRevocationAreRejected(): Unit = Rig().use { rig ->
        val stale = approve(rig.host, rig.submit("stale", 1_000L))
        val valid = approve(rig.host, rig.submit("valid", 500L))
        rig.assertIsCommitted(rig.apply(valid))
        assertFalse(rig.apply(stale) is ReviewedConversationApplyResult.Committed)
        assertNull(rig.host.submit(chat, "overavailable", "yerba", qty(1_500L), "purchase"))
        val later = approve(rig.host, rig.submit("revoked", 500L))
        rig.allowed = false
        assertFalse(rig.apply(later) is ReviewedConversationApplyResult.Committed)
        assertNull(rig.host.position("yerba", qty(1L), "purchase"))
        rig.allowed = true
        assertEquals(2_000L, rig.position().physicalThousandths)
    }

    @Test
    fun rootLeaseEpochAndPointerAbaDenySecondApplyHost(): Unit = Rig().use { rig ->
        val approved = approve(rig.host, rig.submit("epoch", 500L))
        assertThrows(IllegalStateException::class.java) { rig.open().close() }
        rig.epoch++
        assertFalse(rig.apply(approved) is ReviewedConversationApplyResult.Committed)
        rig.epoch--
        val other = StorageGenerationId("another-generation")
        checkNotNull(pointer.compareAndSet(rig.selected.generationId, other))
        checkNotNull(pointer.compareAndSet(other, rig.selected.generationId))
        assertFalse(rig.apply(approved) is ReviewedConversationApplyResult.Committed)
        // ABA changed pointer sequence: the old selected host must fail closed on reads too.
        assertThrows(IllegalStateException::class.java) { rig.position() }
    }

    @Test
    fun committedReceiptReconcilesInterruptedWorkspaceWithoutReplay(): Unit = Rig().use { rig ->
        val pending = rig.submit("interruption", 500L)
        val approved = approve(rig.host, pending)
        rig.interruptWorkspace = true
        assertEquals(ReviewedConversationApplyResult.RecoveryRequired, rig.apply(approved))
        assertEquals(2_000L, rig.position().physicalThousandths)
        rig.reopen()
        val applying = runBlocking { rig.host.reviewDecisions.reviews(chat) }
            .single { it.reviewId == approved.reviewId }
        rig.assertIsCommitted(rig.host.reconcileCommittedOnly(applying.context()))
        assertEquals(2_000L, rig.position().physicalThousandths)
    }

    @Test
    fun withoutGenuineCompletedSql15SelectionHostRefusesAdmission() {
        Files.createDirectories(root)
        val imagined = ActiveGenerationPointer(1, StorageGenerationId("fabricated-sql15"))
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveHousehold15DevForConsumptionHost(
                root, imagined, writeGate,
                ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier, { true },
            ).close()
        }
    }
}
