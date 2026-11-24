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
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** W05 C2A: authenticated typed Evidence2 survives FBS2v8 SQL9 -> SQL11 STAGED. */
class FaroV5Household11V8Evidence2FullStageReplayTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-c2a-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-c2a-v8-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val ciphertext: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c2a-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-c2a-finance-" + id),
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
            evidenceIdFactory = { CommercialEvidenceId("c2a-legacy") },
        ).use {
            it.record(observation("legacy"), IdempotencyKey("c2a-legacy"),
                PayloadFingerprint("c".repeat(64)))
        }
        var id = 0
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 300L },
            evidenceIdFactory = { CommercialEvidenceId("c2a-typed-" + (++id)) },
        ).use { owner ->
            if (withRows) {
                owner.recordWithTypedTerms(
                    observation("unknown"), CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("c2a-unknown"), PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(
                    observation("known"), knownTerms(),
                    IdempotencyKey("c2a-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }

    private fun source(origin: ActiveGenerationPointer): StagedStorageGeneration {
        val src = stage("w05-c2a-source", origin)
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
        toEvidence2(src, true)
        FaroV5GenerationStoreGraph.requireSealedHousehold10Evidence2ArchiveStage(src)
        return src
    }

    private fun target(origin: ActiveGenerationPointer): StagedStorageGeneration =
        stage("w05-c2a-target", origin).also {
            toEvidence2(it, false)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                it, allowAuxiliary3 = false)
        }

    private fun fixture(): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c2a-active")))
        val src = source(origin)
        val dest = target(origin)
        val archivedV7 = FaroV5Household10ArchiveCoordinator(root, gate, {})
            .captureAuthenticatedEvidence2(
                MaintenanceOperationId("w05-c2a-capture"),
                BackupSetId("w05-c2a-v7-backup"), src, origin,
                "5.0-dev", 3000L, key)
        val snapshotV7 = when (val result = crypto.openWithHouseholdEvidence2(archivedV7,key)) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(result.reason)
        }
        val receipt = Household11ReceiptBackupSection(2L, listOf(
            Household11ReceiptBackupRow("receipt-1", "intent-1", "purchase",
                "yerba", 7000, "UNIT", 0L, 3000L, 1L),
            Household11ReceiptBackupRow("receipt-2", "intent-2", "purchase",
                "yerba", 3000, "UNIT", 0L, 3001L, 2L),
        ))
        val payload = FaroV5Household11Fbs2Snapshot(snapshotV7, receipt)
        val bytes = crypto.sealWithHouseholdReceipts11(payload, key)
        return Fixture(origin, src, dest, payload, bytes)
    }

    private fun restore(f: Fixture, bytes: ByteArray = f.ciphertext,
                        pass: CharArray = key) =
        FaroV5Household11V8FullStageReplay(root, gate, {}).restoreAuthenticatedStagedEleven(
            op, f.target, f.origin, bytes, pass)

    private fun verified(f: Fixture) =
        FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)

    private fun verifySnapshot(f: Fixture) {
        val actualTyped = CommercialEvidenceStore(
            fileName = commercial(f.target), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true,
        ).use { it.stagedTypedBackup.snapshot() }
        assertEquals(
            f.snapshot.base.base.base.fourStore.commercialEvidence.stagedTyped,
            actualTyped)
        val reality = f.target.directory.resolve(GenerationStoreFiles.REALITY)
        assertEquals(11L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality))
        assertEquals(3L, RealityHousehold11NeedsRowsPort
            .readTrustedSealedStageEleven(reality).revision)
        assertEquals(1, RealityHousehold11NeedsRowsPort
            .readTrustedSealedStageEleven(reality).needs.size)
        assertEquals(2, RealityHousehold11ReceiptRowsPort
            .readTrustedSealedStageEleven(reality).receipts.size)
    }

    @Test fun typedArchiveRestoresFiveStoresAndSQL11WithoutPointerActivation() {
        val f = fixture()
        val before = pointer.read()
        assertEquals(verified(f), restore(f))
        assertEquals(before, pointer.read())
        verifySnapshot(f)
        assertNotEquals(Files.readAllBytes(f.source.directory.resolve(
            GenerationStoreFiles.REALITY)).toList(), Files.readAllBytes(
            f.target.directory.resolve(GenerationStoreFiles.REALITY)).toList())
    }

    @Test fun completedTypedRestoreIsIdempotent() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertEquals(verified(f), restore(f))
        assertEquals(f.origin, pointer.read())
    }

    @Test fun interruptionAfterTenReentersSameAuthenticatedCiphertext() {
        val f = fixture()
        val interrupted = FaroV5Household11V8FullStageReplay(root, gate, {}, {
            if (it == Household11FullStagePhase.MIGRATED_TO_TEN) throw Crash()
        })
        assertThrows(Crash::class.java) {
            interrupted.restoreAuthenticatedStagedEleven(
                op, f.target, f.origin, f.ciphertext, key)
        }
        assertEquals(verified(f), restore(f))
        assertEquals(f.origin, pointer.read())
    }

    @Test fun interruptionAfterElevenReentersWithoutDoubleReceipts() {
        val f = fixture()
        val interrupted = FaroV5Household11V8FullStageReplay(root, gate, {}, {
            if (it == Household11FullStagePhase.MIGRATED_TO_ELEVEN) throw Crash()
        })
        assertThrows(Crash::class.java) {
            interrupted.restoreAuthenticatedStagedEleven(
                op, f.target, f.origin, f.ciphertext, key)
        }
        assertEquals(verified(f), restore(f))
        assertEquals(2, RealityHousehold11ReceiptRowsPort
            .readTrustedSealedStageEleven(f.target.directory.resolve(
                GenerationStoreFiles.REALITY)).receipts.size)
    }

    @Test fun wrongPassphraseAndDifferentCiphertextAreDenied() {
        val f = fixture()
        assertTrue(restore(f, pass = "wrong-password".toCharArray())
            is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(verified(f), restore(f))
        val different = crypto.sealWithHouseholdReceipts11(f.snapshot, key)
        assertTrue(restore(f, bytes = different) is FiveStoreStagedRestoreResult.Blocked)
    }

    @Test fun staleFullPointerOrRevokedHostCannotRestore() {
        val f = fixture()
        checkNotNull(pointer.compareAndSet(f.origin.generationId,
            StorageGenerationId("foreign-active")))
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
    }

    private class Crash : Error("C2A injected process death")
}
