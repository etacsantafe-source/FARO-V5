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

/** W05 C2B1A: exact immutable typed Evidence2 semantics vs FBS2v8; no cutover. */
class FaroV5Household11Evidence2SemanticPreflightTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-c2b1a-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-c2b1a-v8-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val ciphertext: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c2b1a-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-c2b1a-finance-" + id),
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
            evidenceIdFactory = { CommercialEvidenceId("c2b1a-legacy") },
        ).use {
            it.record(observation("legacy"), IdempotencyKey("c2b1a-legacy"),
                PayloadFingerprint("c".repeat(64)))
        }
        var id = 0
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 300L },
            evidenceIdFactory = { CommercialEvidenceId("c2b1a-typed-" + (++id)) },
        ).use { owner ->
            if (withRows) {
                owner.recordWithTypedTerms(
                    observation("unknown"), CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("c2b1a-unknown"), PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(
                    observation("known"), knownTerms(),
                    IdempotencyKey("c2b1a-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }

    private fun source(origin: ActiveGenerationPointer): StagedStorageGeneration {
        val src = stage("w05-c2b1a-source", origin)
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
        stage("w05-c2b1a-target", origin).also {
            toEvidence2(it, false)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                it, allowAuxiliary3 = false)
        }

    private fun fixture(): Fixture {
        val bootstrap = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c2b1a-bootstrap")))
        val originalStage = stage("w05-c2b1a-origin", bootstrap)
        val origin = checkNotNull(pointer.compareAndSet(
            bootstrap.generationId, originalStage.descriptor.generationId))
        val src = source(origin)
        val dest = target(origin)
        val archivedV7 = FaroV5Household10ArchiveCoordinator(root, gate, {})
            .captureAuthenticatedEvidence2(
                MaintenanceOperationId("w05-c2b1a-capture"),
                BackupSetId("w05-c2b1a-v7-backup"), src, origin,
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


    private fun inspect(
        f: Fixture,
        bytes: ByteArray = f.ciphertext,
        pass: CharArray = key,
        host: () -> Unit = {},
        after: () -> Unit = {},
    ) = FaroV5Household11Evidence2SemanticPreflight(root, gate, host, after)
        .inspect(op, f.target, f.origin, bytes, pass)

    @Test fun authenticatedAbsentUnknownKnownAndAllFieldsReadBackWithoutMutation() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.target.directory.resolve(it))
        }
        val typed = SealedCommercialEvidence2SnapshotReader.read(
            f.target.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE))
        assertEquals(f.snapshot.base.base.base.fourStore.commercialEvidence.stagedTyped, typed)
        assertEquals(3, typed.rows.size)
        assertEquals(null, typed.rows[0].terms)
        assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, typed.rows[1].terms)
        assertEquals(knownTerms(), typed.rows[2].terms)
        val result = inspect(f)
        assertEquals(f.origin, result.origin)
        assertEquals(f.origin, pointer.read())
        assertEquals(f.target.descriptor.generationId, result.targetGenerationId)
        for ((name, bytes) in before) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(bytes,
                Files.readAllBytes(f.target.directory.resolve(name)))
        }
        assertEquals(null, FileBootstrapJournal(root).read(op))
        assertTrue(FileBootstrapJournal(root).pending().isEmpty())
        assertTrue(Files.notExists(root.resolve("bootstrap/household11-dev-cutover-v1.bin")))
    }

    @Test fun changedKnownConditionWithRecomputedValidFingerprintStillFails() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        val changed = knownTerms().copy(conditions = "Términos adulterados")
        BundledSQLiteDriver().open(commercial(f.target)).use { db ->
            db.execSQL("UPDATE commercial_evidence_quote_terms " +
                "SET conditions='Términos adulterados', terms_fingerprint='" +
                changed.fingerprint() + "' WHERE evidence_id='c2b1a-typed-2'")
        }
        // The historical schema/row-count-only C2B0 check is insufficient here.
        FaroV5Household11DevCutoverPreflight(
            root, gate, {}, expectedEvidenceSchema = 2,
        ).inspect(op, f.target, f.origin, f.ciphertext, key)
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun physicalUnknownRowRemovedIsNotMistakenForAbsentLegacy() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        BundledSQLiteDriver().open(commercial(f.target)).use { db ->
            db.execSQL("DELETE FROM commercial_evidence_quote_terms " +
                "WHERE evidence_id='c2b1a-typed-1'")
        }
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun invalidTermsFingerprintIsRejectedEvenWithHealthySqlite() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        BundledSQLiteDriver().open(commercial(f.target)).use { db ->
            db.execSQL("UPDATE commercial_evidence_quote_terms " +
                "SET terms_fingerprint='" + "a".repeat(64) +
                "' WHERE evidence_id='c2b1a-typed-2'")
        }
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun wrongPasswordAndResealedNonceNeverQualify() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertThrows(Exception::class.java) {
            inspect(f, pass = "wrong-c2b1a-secret".toCharArray())
        }
        val nonceChanged = crypto.sealWithHouseholdReceipts11(f.snapshot, key)
        assertThrows(Exception::class.java) { inspect(f, bytes = nonceChanged) }
        assertEquals(f.origin, pointer.read())
        assertEquals(null, FileBootstrapJournal(root).read(op))
    }

    @Test fun revokedHostOrPointerAbaAfterSemanticObservationFails() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertThrows(IllegalStateException::class.java) {
            inspect(f, host = { error("Host closed state revoked") })
        }
        assertThrows(IllegalStateException::class.java) {
            inspect(f, after = {
                val foreign = StorageGenerationId("w05-c2b1a-aba")
                checkNotNull(pointer.compareAndSet(f.origin.generationId, foreign))
                checkNotNull(pointer.compareAndSet(foreign, f.origin.generationId))
            })
        }
        assertEquals(f.origin.generationId, checkNotNull(pointer.read()).generationId)
        assertEquals(null, FileBootstrapJournal(root).read(op))
    }
}
