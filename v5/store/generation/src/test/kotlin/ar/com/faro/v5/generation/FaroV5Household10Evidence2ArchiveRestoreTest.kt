package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** F2B2B02 SQLite-backed STAGED-only FBS2v7 authenticated replay; no cutover/Android. */
class FaroV5Household10Evidence2ArchiveRestoreTest {
    @TempDir lateinit var root: Path

    private val key = "household-typed-fixture-only".toCharArray()
    private val pointer get() = FileActiveGenerationPointer(root)
    private val quiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T = block()
    }
    private val restoreOp = MaintenanceOperationId("restore-typed-fbs2v7")
    private fun origin(): ActiveGenerationPointer =
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("typed-archive-origin")))
    private fun stage(name: String, active: ActiveGenerationPointer): StagedStorageGeneration {
        val source = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "f2b2b02-typed-stage", 100L),
        )
        return FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("migrate-"+name), source, active, "a".repeat(64), 1000L,
        )
    }
    private fun observation(name: String, currency: String = "ARS") = CommercialPriceObservation(
        productKey = CommercialProductKey("rice-1000"), merchant = CommercialMerchant("market"),
        currencyCode = CommercialCurrencyCode(currency), amountMinor = 12_000L,
        sourceKind = CommercialEvidenceSourceKind.MANUAL, sourceRef = "manual:"+name,
        observedAtEpochMillis = 100L,
    )
    private fun fullTerms() = CommercialEvidenceTypedTerms(
        localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
        issuedAtEpochMillis = 100L, validFromEpochMillis = 110L, validUntilEpochMillis = 200L,
        presentationQuantityThousandths = 1000L, presentationUnit = CommercialPresentationUnit.GRAM,
        packCount = 1, paymentMethod = CommercialPaymentMethod.DEBIT,
        eligibility = CommercialEligibility.GENERAL, conditions = "Solo socios",
        promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
        promotionValue = 1200L, promotionSourceRef = "manual:flyer",
    )
    private fun commercial(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()
    private fun upgradeEvidence(stage: StagedStorageGeneration, insert: Boolean = true) {
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
            clock = { 300L }, evidenceIdFactory = { CommercialEvidenceId("typed-"+java.util.UUID.randomUUID()) },
        ).use { owner ->
            if (insert) {
                owner.recordWithTypedTerms(observation("unknown","USD"),
                    CommercialEvidenceTypedTerms.UNKNOWN, IdempotencyKey("typed-unknown"),
                    PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(observation("known"), fullTerms(),
                    IdempotencyKey("typed-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }
    private fun source(active: ActiveGenerationPointer, makeTyped: Boolean = true): StagedStorageGeneration {
        val source = stage("source", active)
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES('2026-11','Comida','ARS',300000,1,1000,'HUMAN')")
        }
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).use { aux ->
            val notice = aux.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:typed-occ"),SourceRef.of("reminder:typed-source"),
                OccurrenceRef.of("typed-occ"),NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,NoticeSummary.of("Aviso"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            aux.mutateReminderNoticeAttentionWithReceipt("typed-attention", notice.noticeId,
                1L, NoticeAttention.SEEN) { true }
        }
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(source, active).use {
            val ws = checkNotNull(it.workspace)
            ws.backup.restoreOrVerify(ws.backup.snapshot().copy(revision = 9L))
        }
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=8")
            db.execSQL("INSERT INTO household_need VALUES ('need-1','Yerba',500,'GRAM','HIGH','OPEN',7,NULL)")
        }
        val seq = intArrayOf(0)
        CommercialEvidenceStore(fileName = commercial(source), clock = { 250L },
            evidenceIdFactory = { CommercialEvidenceId("legacy-"+(++seq[0])) }).use { old ->
            old.record(observation("absent"), IdempotencyKey("legacy-absent"),
                PayloadFingerprint("c".repeat(64)))
        }
        if (makeTyped) upgradeEvidence(source)
        return source
    }
    private fun target(active: ActiveGenerationPointer, name: String = "target"): StagedStorageGeneration {
        val target = stage(name,active)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(target,pointer).close()
        upgradeEvidence(target, insert = false)
        FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(target, false)
        return target
    }
    private fun coordinator(
        afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
        afterPhase: (Household10RestorePhase) -> Unit = {},
    ) = FaroV5Household10ArchiveCoordinator(root, quiescence, {}, afterStore, afterPhase)
    private fun capture(source: StagedStorageGeneration, active: ActiveGenerationPointer) =
        coordinator().captureAuthenticatedEvidence2(
            MaintenanceOperationId("capture-typed-fbs2v7"), BackupSetId("backup-typed-fbs2v7"),
            source, active, "5.0-dev", 2000L, key,
        )
    private fun open(bytes: ByteArray): FaroV5Household10Fbs2Snapshot =
        when (val result = FaroV5FiveStorePortableBackupCrypto().openWithHouseholdEvidence2(bytes,key)) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Unexpected rejected typed archive "+result.reason)
        }
    private fun restore(target: StagedStorageGeneration, active: ActiveGenerationPointer, bytes: ByteArray) =
        coordinator().restoreAuthenticatedEvidence2(restoreOp,target,active,bytes,key)
    private fun verified(target: StagedStorageGeneration) =
        FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value)
    private fun blocked(reason: FiveStoreStagedRestoreBlock) =
        FiveStoreStagedRestoreResult.Blocked(reason)

    @Test fun t01CapturesSealedTypedFiveStoreWithoutMutatingSource() {
        val active=origin();val source=source(active)
        val before=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(source.directory.resolve(it))
        }
        val bytes=capture(source,active);val snapshot=open(bytes)
        assertEquals(2,snapshot.base.base.fourStore.commercialEvidence.schemaVersion)
        val rows=checkNotNull(snapshot.base.base.fourStore.commercialEvidence.stagedTyped).rows
        assertEquals(3,rows.size)
        val byKey=rows.associateBy { it.record.idempotencyKey.value }
        assertNull(byKey.getValue("legacy-absent").terms)
        assertEquals(CommercialEvidenceTypedTerms.UNKNOWN,byKey.getValue("typed-unknown").terms)
        assertEquals(fullTerms(),byKey.getValue("typed-known").terms)
        assertEquals("USD",byKey.getValue("typed-unknown").record.observation.currencyCode.value)
        assertEquals(1,snapshot.base.receipts.size)
        assertEquals(8L,snapshot.household.revision)
        assertEquals(active,pointer.read())
        before.forEach { (name,data) ->
            assertArrayEquals(data,Files.readAllBytes(source.directory.resolve(name)))
        }
    }
    @Test fun t02RestoresFiveStoresFinanceHouseholdAndRetriesExactly() {
        val active=origin();val source=source(active);val archive=capture(source,active)
        val expected=open(archive);val target=target(active)
        assertEquals(verified(target),restore(target,active,archive))
        val actual=open(capture(target,active))
        assertEquals(expected.household,actual.household)
        assertEquals(expected.base.receipts,actual.base.receipts)
        assertEquals(expected.base.base.finance9,actual.base.base.finance9)
        assertEquals(expected.base.base.workspace,actual.base.base.workspace)
        assertEquals(expected.base.base.fourStore.commercialEvidence,
            actual.base.base.fourStore.commercialEvidence)
        assertEquals(verified(target),restore(target,active,archive))
        assertEquals(active,pointer.read())
    }
    @Test fun t03RejectsTamperWrongPasswordAndLegacyEnvelopeBeforeWrites() {
        val active=origin();val source=source(active,makeTyped=false)
        val legacy=coordinator().captureAuthenticated(
            MaintenanceOperationId("capture-v6"),BackupSetId("backup-old-v6"),
            source,active,"5.0-dev",2000L,key)
        upgradeEvidence(source);val bytes=capture(source,active);val target=target(active)
        val before=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(target.directory.resolve(it))
        }
        val altered=bytes.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        val attempts=listOf(
            bytes to "wrong-password-for-test".toCharArray(),
            altered to key,
            legacy to key,
        )
        for ((cipher,pass) in attempts) {
            val rejected=coordinator().restoreAuthenticatedEvidence2(
                restoreOp,target,active,cipher,pass)
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                (rejected as FiveStoreStagedRestoreResult.Blocked).reason)
        }
        before.forEach { (name,data) ->
            assertArrayEquals(data,Files.readAllBytes(target.directory.resolve(name)))
        }
        assertEquals(active,pointer.read())
    }
    @Test fun t04CrashAfterCommercialReplaysOnlySameArchive() {
        val active=origin();val source=source(active);val archive=capture(source,active)
        val target=target(active)
        val stopped=coordinator(afterStore = {
            if (it == FiveStoreStagedRestoreSection.COMMERCIAL_EVIDENCE) error("injected-stop")
        })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stopped.restoreAuthenticatedEvidence2(restoreOp,target,active,archive,key))
        val replacement=capture(source,active)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            restore(target,active,replacement))
        assertEquals(verified(target),restore(target,active,archive))
        assertEquals(active,pointer.read())
    }
    @Test fun t05CrashAfterHouseholdMigrationResumesPinnedIdentity() {
        val active=origin();val source=source(active);val archive=capture(source,active)
        val target=target(active)
        val stopped=coordinator(afterPhase={
            if (it == Household10RestorePhase.MIGRATED_TO_TEN) error("injected-restart")
        })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stopped.restoreAuthenticatedEvidence2(restoreOp,target,active,archive,key))
        assertEquals(verified(target),restore(target,active,archive))
        assertEquals(8L,open(capture(target,active)).household.revision)
        assertEquals(active,pointer.read())
    }
    @Test fun t06RejectsUntypedOrNonfreshForeignTargetWithoutPointerSwitch() {
        val active=origin();val source=source(active);val archive=capture(source,active)
        val untyped=stage("untyped",active)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            restore(untyped,active,archive))
        val target=target(active)
        CommercialEvidenceStore(fileName=commercial(target),stagedTypedTermsV2=true,
            stagedSemanticBackupV2=true,clock={ 300L },
            evidenceIdFactory={ CommercialEvidenceId("foreign") }).use { store ->
            store.recordWithTypedTerms(observation("foreign"),fullTerms(),
                IdempotencyKey("foreign-key"),PayloadFingerprint("d".repeat(64)))
        }
        val before=CommercialEvidenceStore(fileName=commercial(target),stagedTypedTermsV2=true,
            stagedSemanticBackupV2=true).use { it.stagedTypedBackup.snapshot() }
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            restore(target,active,archive))
        val after=CommercialEvidenceStore(fileName=commercial(target),stagedTypedTermsV2=true,
            stagedSemanticBackupV2=true).use { it.stagedTypedBackup.snapshot() }
        assertEquals(before,after)
        assertEquals(active,pointer.read())
    }
}
