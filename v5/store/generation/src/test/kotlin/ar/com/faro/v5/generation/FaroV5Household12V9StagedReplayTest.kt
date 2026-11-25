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
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRowsPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Authenticated capture and replay on an inactive five-store SQL12 generation. */
class FaroV5Household12V9StagedReplayTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-c0b2b-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-c0b2b-v8-replay")

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
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c0b2b-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-c0b2b-finance-" + id),
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
        val src = stage("w05-c0b2b-source", origin)
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
        stage("w05-c0b2b-target", origin).also {
            if (evidence2) {
                toEvidence2(it, false)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(it, allowAuxiliary3 = false)
            } else {
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(it, allowAuxiliary3 = false)
            }
        }

    private fun fixture(evidence2: Boolean = true): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c0b2b-active")))
        val src = source(origin, evidence2)
        val dest = target(origin, evidence2)
        val old = FaroV5Household10ArchiveCoordinator(root, gate, {})
        val captureOp = MaintenanceOperationId("w05-c0b2b-capture")
        val backup = BackupSetId("w05-c0b2b-base-backup")
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
        return Fixture(origin, src, dest, payload, evidence2, crypto.sealWithHouseholdPurchases12(payload, key))
    }

    private fun replay(after: (Household12StagePhase) -> Unit = {}) =
        FaroV5Household12V9StagedReplay(root, gate, {}, after)

    private fun completed(f: Fixture) =
        FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)

    private fun restore(f: Fixture, archive: ByteArray = f.ciphertext, passphrase: CharArray = key) =
        replay().restoreAuthenticatedV9(op, f.target, f.origin, archive, passphrase)

    private fun reality(f: Fixture) = f.target.directory.resolve(GenerationStoreFiles.REALITY)

    private fun observed(f: Fixture): FaroV5Household12Fbs2Snapshot {
        val bytes = replay().captureAuthenticated(
            MaintenanceOperationId("capture-restored-sql12"), f.target, f.origin, f.evidence2,
            BackupSetId("restored-sql12"), "5.0-dev", 4_000L, key,
        )
        return when (val opened = crypto.openWithHouseholdPurchases12(bytes, key)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(opened.reason)
        }
    }

    @Test
    fun authenticatedEvidence2RestoresAndRecapturesAllFiveStoresAndPurchases() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val recovered = observed(f)
        assertEquals(f.snapshot.purchases, recovered.purchases)
        assertEquals(f.snapshot.base.receipts, recovered.base.receipts)
        assertEquals(f.snapshot.base.base.household, recovered.base.base.household)
        val expectedOwners = f.snapshot.base.base.base.base
        val observedOwners = recovered.base.base.base.base
        assertEquals(expectedOwners, observedOwners.copy(
            fourStore = observedOwners.fourStore.copy(manifest = expectedOwners.fourStore.manifest),
        ))
        assertEquals(f.snapshot.base.base.base.receipts, recovered.base.base.base.receipts)
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun evidence1V9UsesItsExactArchiveProfile() {
        val f = fixture(evidence2 = false)
        assertEquals(completed(f), restore(f))
        assertEquals(1, observed(f).base.base.base.base.fourStore.commercialEvidence.schemaVersion)
    }

    @Test
    fun replaysOnlyTheOriginalCiphertextWithoutDuplicatingRows() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val before = Files.readAllBytes(reality(f)).toList()
        assertEquals(completed(f), restore(f))
        assertEquals(before, Files.readAllBytes(reality(f)).toList())
        val changed = crypto.sealWithHouseholdPurchases12(f.snapshot, key)
        assertEquals(FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            restore(f, changed))
    }

    @Test
    fun interruptionBeforeTheSql12CommitRollsBackAndResumesWithSamePin() {
        val f = fixture()
        val interrupted = replay { phase ->
            if (phase == Household12StagePhase.BEFORE_ROW_COMMIT) error("interruption before COMMIT")
        }
        assertTrue(interrupted.restoreAuthenticatedV9(op, f.target, f.origin, f.ciphertext, key)
            is FiveStoreStagedRestoreResult.Blocked)
        val after = RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(reality(f))
        assertTrue(after.purchases.isEmpty())
        assertTrue(after.receipts.receipts.isEmpty())
        assertEquals(0L, after.receipts.revision)
        assertEquals(completed(f), restore(f))
    }

    @Test
    fun legacyV8ImportKeepsReceiptsAndStartsWithEmptyPurchases() {
        val f = fixture()
        val old = crypto.sealWithHouseholdReceipts11(f.snapshot.base, key)
        assertTrue(restore(f, old) is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(completed(f), replay().restoreAuthenticatedV8AsTwelve(op, f.target, f.origin, old, key))
        val restored = observed(f)
        assertEquals(f.snapshot.base.receipts, restored.base.receipts)
        assertTrue(restored.purchases.isEmpty())
        assertEquals(completed(f), replay().restoreAuthenticatedV8AsTwelve(op, f.target, f.origin, old, key))
    }

    @Test
    fun refusesCorruptCiphertextAndWrongPassphraseBeforeMigration() {
        val f = fixture()
        val changed = f.ciphertext.clone().also { it[it.lastIndex] = 0 }
        changed[changed.lastIndex - 1] = (changed[changed.lastIndex - 1].toInt() xor 1).toByte()
        assertTrue(restore(f, changed) is FiveStoreStagedRestoreResult.Blocked)
        assertTrue(restore(f, passphrase = "other-local-test-passphrase".toCharArray())
            is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(9L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f)))
    }

    @Test
    fun refusesAbaAndRevokedLeaseWithoutWritingPurchaseRows() {
        val f = fixture()
        val denied = FaroV5Household12V9StagedReplay(root, gate, { error("lease revoked") })
        assertTrue(denied.restoreAuthenticatedV9(op, f.target, f.origin, f.ciphertext, key)
            is FiveStoreStagedRestoreResult.Blocked)
        val interrupted = replay { phase ->
            if (phase == Household12StagePhase.MIGRATED_TO_TWELVE) {
                val other = checkNotNull(pointer.compareAndSet(f.origin, StorageGenerationId("another-generation")))
                checkNotNull(pointer.compareAndSet(other, f.origin.generationId))
            }
        }
        assertTrue(interrupted.restoreAuthenticatedV9(op, f.target, f.origin, f.ciphertext, key)
            is FiveStoreStagedRestoreResult.Blocked)
        val after = RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(reality(f))
        assertTrue(after.purchases.isEmpty())
        assertEquals(0L, after.receipts.revision)
    }

    @Test
    fun refusesDivergentOwnerRowsOnCompletedStage() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        BundledSQLiteDriver().open(reality(f).toString()).use { db ->
            db.execSQL("UPDATE finance_budget_limit SET limit_minor = limit_minor + 1")
        }
        val before = Files.readAllBytes(reality(f)).toList()
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(before, Files.readAllBytes(reality(f)).toList())
        assertEquals(f.origin, pointer.read())
    }
}
