package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRowsPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Authenticated five-store FBS2v11 recovery into isolated SQL14 with reservation rows. */
class FaroV5Household14V11StagedReplayTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-a2b0b3-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-a2b0b3-v8-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household14Fbs2Snapshot,
        val evidence2: Boolean,
        val ciphertext: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-a2b0b3-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-a2b0b3-finance-" + id),
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
            evidenceIdFactory = { CommercialEvidenceId("a2b0b3-legacy") },
        ).use {
            it.record(observation("legacy"), IdempotencyKey("a2b0b3-legacy"),
                PayloadFingerprint("c".repeat(64)))
        }
        var id = 0
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 300L },
            evidenceIdFactory = { CommercialEvidenceId("a2b0b3-typed-" + (++id)) },
        ).use { owner ->
            if (withRows) {
                owner.recordWithTypedTerms(
                    observation("unknown"), CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("a2b0b3-unknown"), PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(
                    observation("known"), knownTerms(),
                    IdempotencyKey("a2b0b3-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }

    private fun source(origin: ActiveGenerationPointer, evidence2: Boolean): StagedStorageGeneration {
        val src = stage("w05-a2b0b3-source", origin)
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
        stage("w05-a2b0b3-target", origin).also {
            if (evidence2) {
                toEvidence2(it, false)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(it, allowAuxiliary3 = false)
            } else {
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(it, allowAuxiliary3 = false)
            }
        }

    private fun fixture(evidence2: Boolean = true): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-a2b0b3-active")))
        val src = source(origin, evidence2)
        val dest = target(origin, evidence2)
        val old = FaroV5Household10ArchiveCoordinator(root, gate, {})
        val captureOp = MaintenanceOperationId("w05-a2b0b3-capture")
        val backup = BackupSetId("w05-a2b0b3-base-backup")
        val archivedBase = if (evidence2) {
            old.captureAuthenticatedEvidence2(captureOp, backup, src, origin, "5.0-dev", 3000L, key)
        } else {
            old.captureAuthenticated(captureOp, backup, src, origin, "5.0-dev", 3000L, key)
        }
        val openedBase = if (evidence2) crypto.openWithHouseholdEvidence2(archivedBase, key)
            else crypto.openWithHousehold(archivedBase, key)
        val snapshotV7 = when (val result = openedBase) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(result.reason)
        }
        val receipt = Household11ReceiptBackupSection(3L, listOf(
            Household11ReceiptBackupRow("receipt-1", "intent-1", "purchase",
                "yerba", 7000, "UNIT", 0L, 3000L, 1L),
            Household11ReceiptBackupRow("receipt-2", "intent-2", "purchase",
                "yerba", 3000, "UNIT", 0L, 3001L, 2L),
        ))
        val payload = FaroV5Household13Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(snapshotV7, receipt),
            listOf(Household12PurchaseBackupRow(
                "purchase", "yerba", 10_000, "UNIT", "local-human", 3_000, "MANUAL_EXPLICIT", 1,
            )),
            listOf(Household13CompensationBackupRow("undo-1", "undo-intent-1",
                "receipt-1", "purchase", 7000, "UNIT", "local-human", 3002L, 3L)),
        )
        val reserve = Household14ReservationBackupRow(
            "reserve", "reserve-intent", Household14ReservationEventType.RESERVE,
            "reserve", "yerba", "UNIT", 2_000, "local-human", 3_003L, 4L,
        )
        val release = reserve.copy(eventId = "release", intentKey = "release-intent",
            type = Household14ReservationEventType.RELEASE, quantityThousandths = 1_000,
            recordedAtMs = 3_004L, revision = 5L)
        val modern = FaroV5Household14Fbs2Snapshot(payload, listOf(reserve, release))
        return Fixture(origin, src, dest, modern, evidence2, crypto.sealWithHouseholdReservations14(modern, key))
    }

    private fun replay(
        fence: () -> Unit = {},
        after: (Household14StagePhase) -> Unit = {},
    ) = FaroV5Household14V11StagedReplay(root, gate, fence, after)

    private fun completed(f: Fixture) = FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)
    private fun reality(stage: StagedStorageGeneration) = stage.directory.resolve(GenerationStoreFiles.REALITY)
    private fun restore(f: Fixture, archive: ByteArray = f.ciphertext, passphrase: CharArray = key) =
        replay().restoreAuthenticatedV11(op, f.target, f.origin, archive, passphrase)

    @Test
    fun authenticEvidence2ReplaysFiveStoresAndCanBeReopenedAndCaptured() {
        val f = fixture()
        val sourceBefore = Files.readAllBytes(reality(f.source))
        assertEquals(completed(f), restore(f))
        val restarted = replay()
        assertEquals(f.source.descriptor.generationId,
            restarted.verifyForEvidence2Cutover(op, f.target, f.origin, f.ciphertext, key))
        val archive = restarted.captureAuthenticated(
            MaintenanceOperationId("recapture-sql14"), f.target, f.origin, f.evidence2,
            BackupSetId("recaptured-sql14"), "5.0-dev", 4_000L, key,
        )
        val captured = when (val opened = crypto.openWithHouseholdReservations14(archive, key)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(opened.reason)
        }
        assertEquals(f.snapshot.reservations, captured.reservations)
        assertEquals(f.snapshot.base.compensations, captured.base.compensations)
        assertEquals(f.snapshot.base.purchases, captured.base.purchases)
        assertEquals(f.snapshot.base.base.receipts, captured.base.base.receipts)
        assertEquals(f.snapshot.base.base.base.household, captured.base.base.base.household)
        assertEquals(f.origin, pointer.read())
        assertArrayEquals(sourceBefore, Files.readAllBytes(reality(f.source)))
    }

    @Test
    fun v10ImportIsExplicitAndStartsWithAnEmptyLedger() {
        val f = fixture(evidence2 = false)
        val v10 = crypto.sealWithHouseholdCompensation13(f.snapshot.base, key)
        val rejected = restore(f, v10) as FiveStoreStagedRestoreResult.Blocked
        assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, rejected.reason)
        assertEquals(9L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f.target)))
        val owner = replay()
        assertEquals(completed(f), owner.restoreAuthenticatedV10AsFourteen(op, f.target, f.origin, v10, key))
        assertEquals(completed(f), owner.restoreAuthenticatedV10AsFourteen(op, f.target, f.origin, v10, key))
        assertTrue(RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(reality(f.target)) {}
            .reservations.isEmpty())
        assertThrows(IllegalStateException::class.java) {
            owner.verifyForEvidence2Cutover(op, f.target, f.origin, v10, key)
        }
    }

    @Test
    fun interruptedAfterMigrationRetainsOriginalPinAndResumesWithoutDuplicates() {
        val f = fixture()
        assertThrows(Interruption::class.java) {
            replay(after = { if (it == Household14StagePhase.MIGRATED_TO_FOURTEEN) throw Interruption() })
                .restoreAuthenticatedV11(op, f.target, f.origin, f.ciphertext, key)
        }
        assertEquals(14L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f.target)))
        assertTrue(RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(reality(f.target)) {}
            .reservations.isEmpty())
        val resealed = crypto.sealWithHouseholdReservations14(f.snapshot, key)
        val refused = restore(f, resealed) as FiveStoreStagedRestoreResult.Blocked
        assertEquals(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT, refused.reason)
        assertEquals(completed(f), restore(f))
        assertEquals(completed(f), restore(f))
        val rows = RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(reality(f.target)) {}
        assertEquals(listOf("reserve", "release"), rows.reservations.map { it.eventId })
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun wrongPassphraseAndCorruptedCiphertextLeaveAllTargetStoresUntouched() {
        val f = fixture()
        val originals = GenerationStoreFiles.WORKSPACE_V2.associateWith { Files.readAllBytes(f.target.directory.resolve(it)) }
        val altered = f.ciphertext.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
            (restore(f, altered) as FiveStoreStagedRestoreResult.Blocked).reason)
        assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
            (restore(f, passphrase = "different secret".toCharArray()) as FiveStoreStagedRestoreResult.Blocked).reason)
        originals.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(f.target.directory.resolve(name))) }
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun refusesSidecarsRevokedLeaseAndPointerAba() {
        val f = fixture()
        val file = reality(f.target)
        val wal = Files.createFile(file.resolveSibling(file.fileName.toString() + "-wal"))
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
        Files.delete(wal)
        var live = true
        val revoked = replay(fence = { check(live) }, after = {
            if (it == Household14StagePhase.BEFORE_RESERVATIONS) live = false
        })
        assertTrue(revoked.restoreAuthenticatedV11(op, f.target, f.origin, f.ciphertext, key)
            is FiveStoreStagedRestoreResult.Blocked)
        val rows = RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(reality(f.target)) {}
        assertTrue(rows.reservations.isEmpty())
        val moved = checkNotNull(pointer.compareAndSet(f.origin.generationId, StorageGenerationId("different-active")))
        val aba = checkNotNull(pointer.compareAndSet(moved.generationId, f.origin.generationId))
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(aba, pointer.read())
    }

    private class Interruption : Error()
}
