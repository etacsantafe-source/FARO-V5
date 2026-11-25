package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Authenticated capture and replay on an inactive five-store SQL12 generation. */
class FaroV5Household12Evidence2DevJournaledCutoverTest {
    @TempDir lateinit var root: Path
    private val writeGate = SingleProcessMaterialWriteGate()
    private val gate = MaterialWriteGateQuiescence(writeGate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-c0b3b-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-c0b3b-v8-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household12Fbs2Snapshot,
        val evidence2: Boolean,
        val ciphertext: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c0b3b-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-c0b3b-finance-" + id),
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
        val src = stage("w05-c0b3b-source", origin)
        val reality = src.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Alimentos','ARS',100000,1,1000,'HUMAN')")
        }
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=3 WHERE singleton_id=1")
            db.execSQL("INSERT INTO household_need VALUES " +
                "('need-yerba','Yerba',1000,'GRAM','NORMAL','OPEN',2,NULL)")
        }
        if (evidence2) toEvidence2(src, true)
        if (evidence2) FaroV5GenerationStoreGraph.requireSealedHousehold10Evidence2ArchiveStage(src)
        else FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(src)
        return src
    }

    private fun target(origin: ActiveGenerationPointer, evidence2: Boolean): StagedStorageGeneration =
        stage("w05-c0b3b-target", origin).also {
            if (evidence2) {
                toEvidence2(it, false)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(it, allowAuxiliary3 = false)
            } else {
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(it, allowAuxiliary3 = false)
            }
        }

    private fun fixture(evidence2: Boolean = true): Fixture {
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
        val captureOp = MaintenanceOperationId("w05-c0b3b-capture")
        val backup = BackupSetId("w05-c0b3b-base-backup")
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
        val payload = FaroV5Household12Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(snapshotV7, receipt),
            listOf(Household12PurchaseBackupRow(
                "purchase", "yerba", 10_000, "UNIT", "local-human", 3_000, "MANUAL_EXPLICIT", 1,
            )),
        )
        val bytes = crypto.sealWithHouseholdPurchases12(payload, key)
        val replay = FaroV5Household12V9StagedReplay(root, gate, {})
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(dest.descriptor.generationId.value),
            replay.restoreAuthenticatedV9(op, dest, origin, bytes, key))
        return Fixture(origin, src, dest, payload, evidence2, bytes)
    }

    private val journal get() = FileBootstrapJournal(root)
    private val replayFence get() = FileGenerationReplayFence(root)
    private val statusAwait get() = FaroV5Household12Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND
    private val statusActive get() = FaroV5Household12Evidence2DevJournaledCutover.Status.ACTIVE_TWELVE
    private val statusBlocked get() = FaroV5Household12Evidence2DevJournaledCutover.Status.BLOCKED
    private val statusRollback get() = FaroV5Household12Evidence2DevJournaledCutover.Status.ROLLED_BACK

    private fun cutover(
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        healthy: () -> Boolean = { true },
        prepared: () -> Unit = {},
    ) = FaroV5Household12Evidence2DevJournaledCutover.acquire(
        root, writeGate, { true }, fault, healthy, prepared,
    )

    private fun authorize(host: FaroV5Household12Evidence2DevJournaledCutover, f: Fixture) =
        host.authorize(op, f.target, f.origin, f.ciphertext, key) { true }

    private fun switch(host: FaroV5Household12Evidence2DevJournaledCutover, f: Fixture) =
        host.start(op, f.target, f.origin, f.ciphertext, key) { true }

    @Test
    fun validatedRequiresForegroundAndReopensCompletedSql12() {
        val f = fixture()
        cutover().use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
            assertEquals(statusAwait, host.recover().status)
            assertEquals(f.origin, pointer.read())
            assertThrows(IllegalStateException::class.java) {
                FaroV5Household12Evidence2DevJournaledCutover.reopenCompleted(root, f.origin, writeGate) { true }
            }
            assertEquals(statusActive, switch(host, f).status)
        }
        val selected = checkNotNull(pointer.read())
        assertEquals(f.origin.sequence + 1, selected.sequence)
        assertEquals(f.target.descriptor.generationId, selected.generationId)
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)?.phase)
        assertTrue(replayFence.isInstalled(MaintenanceOperationKind.RESTORE, selected.generationId))
        FaroV5Household12Evidence2DevJournaledCutover.reopenCompleted(root, selected, writeGate) { true }.use {
            val rows = checkNotNull(it.readPurchaseReceipts())
            assertEquals(10_000, rows.purchases.single().quantityThousandths)
            assertEquals(listOf(7_000, 3_000), rows.receipts.receipts.map { row -> row.quantityThousandths })
        }
    }

    @Test
    fun revokedForegroundLeavesValidatedJournalAndOriginalPointer() {
        val f = fixture()
        cutover().use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            var checks = 0
            val denied = host.start(op, f.target, f.origin, f.ciphertext, key) { ++checks == 1 }
            assertEquals(statusBlocked, denied.status)
            assertEquals(statusAwait, host.recover().status)
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
    }

    @Test
    fun resumesAuthorizedSwitchPreparedWithoutExtraPointerEffect() {
        val f = fixture()
        cutover(prepared = { throw SimulatedInterruption() }).use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            assertThrows(SimulatedInterruption::class.java) { switch(host, f) }
        }
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(op)?.phase)
        assertEquals(f.origin, pointer.read())
        cutover().use { restarted ->
            assertEquals(statusActive, restarted.recover().status)
            assertEquals(statusActive, restarted.recover().status)
        }
        assertEquals(f.origin.sequence + 1, pointer.read()?.sequence)
    }

    @Test
    fun resumesAfterPublishedPointerWithoutDuplicateSwitch() {
        val f = fixture()
        cutover(fault = { point ->
            if (point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) {
                throw SimulatedInterruption()
            }
        }).use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            assertThrows(SimulatedInterruption::class.java) { switch(host, f) }
        }
        val selected = pointer.read()
        assertEquals(f.origin.sequence + 1, selected?.sequence)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(op)?.phase)
        cutover().use { restarted -> assertEquals(statusActive, restarted.recover().status) }
        assertEquals(selected, pointer.read())
    }

    @Test
    fun failedTargetReopenRollsBackWithVerifiedOriginalPointer() {
        val f = fixture()
        cutover(healthy = { false }).use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            assertEquals(statusRollback, switch(host, f).status)
            assertEquals(statusRollback, host.recover().status)
        }
        assertEquals(ActiveGenerationPointer(f.origin.sequence + 2, f.origin.generationId), pointer.read())
        val entry = checkNotNull(journal.read(op))
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, entry.phase)
        assertFalse(entry.materialWritesAccepted)
        assertFalse(replayFence.isInstalled(MaintenanceOperationKind.RESTORE, f.target.descriptor.generationId))
    }

    @Test
    fun rejectsAbaAfterAuthorization() {
        val f = fixture()
        cutover().use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            val other = StorageGenerationId("foreign-sql12-pointer")
            checkNotNull(pointer.compareAndSet(f.origin.generationId, other))
            checkNotNull(pointer.compareAndSet(other, f.origin.generationId))
            assertEquals(statusBlocked, switch(host, f).status)
            assertEquals(statusBlocked, host.recover().status)
        }
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
        assertEquals(f.origin.sequence + 2, pointer.read()?.sequence)
    }

    @Test
    fun rejectsArchiveSubstitutionAndCorruptedTicket() {
        val f = fixture()
        cutover().use { host ->
            val v8 = crypto.sealWithHouseholdReceipts11(f.snapshot.base, key)
            assertEquals(statusBlocked, host.authorize(op, f.target, f.origin, v8, key) { true }.status)
            assertNull(journal.read(op))
            assertEquals(statusAwait, authorize(host, f).status)
            val freshlySealed = crypto.sealWithHouseholdPurchases12(f.snapshot, key)
            assertEquals(statusBlocked,
                host.start(op, f.target, f.origin, freshlySealed, key) { true }.status)
            Files.write(root.resolve("bootstrap/sql12-evidence2-cutover.ticket"), ByteArray(96))
            assertEquals(statusBlocked, host.recover().status)
        }
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun rejectsChangedStageBeforeSwitch() {
        val f = fixture()
        cutover().use { host ->
            assertEquals(statusAwait, authorize(host, f).status)
            BundledSQLiteDriver().open(f.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
                db.execSQL("UPDATE household_confirmed_purchase SET confirmed_by='different' WHERE purchase_ref='purchase'")
            }
            assertEquals(statusBlocked, switch(host, f).status)
            assertEquals(statusBlocked, host.recover().status)
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
    }

    private class SimulatedInterruption : Error()
}
