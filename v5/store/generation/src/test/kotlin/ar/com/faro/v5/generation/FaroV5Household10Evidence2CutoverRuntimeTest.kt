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
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** F2B2C: real SQLite DEV-only FBS2v7 journaled cutover, crash/replay and rollback. */
class FaroV5Household10Evidence2CutoverRuntimeTest {
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


    private class Interruption : Error("injected F2B2C restart")
    private val activation = MaintenanceOperationId("f2b2c-activate-typed")
    private val journal get() = FileBootstrapJournal(root)
    private val fences get() = FileGenerationReplayFence(root)

    private data class Scenario(
        val origin: ActiveGenerationPointer,
        val original: StagedStorageGeneration,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val archive: ByteArray,
        val originalDigest: String,
    )
    private fun scenario(): Scenario {
        val provisional=origin()
        val original=stage("original-finance",provisional)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(original,pointer).close()
        BundledSQLiteDriver().open(original.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO finance_budget_limit VALUES('2026-10','Origen','ARS',7000,1,1000,'HUMAN')")
        }
        val selected=checkNotNull(pointer.compareAndSet(provisional.generationId,original.descriptor.generationId))
        val source=source(selected)
        val destination=target(selected)
        val bytes=capture(source,selected)
        return Scenario(selected,original,source,destination,bytes,
            FaroV5Household10DevCutoverOwner.fingerprint(original))
    }
    private fun runtime(
        closed: () -> Boolean = { true },
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        afterAuthorization: () -> Unit = {},
        afterFence: () -> Unit = {},
        healthy: () -> Boolean = { true },
        gate: SingleProcessMaterialWriteGate = SingleProcessMaterialWriteGate(),
    ) = FaroV5Household10DevCutoverRuntime.acquireEvidence2(
        root, gate, closed, fault, afterAuthorization, afterFence, healthy,
    )
    private fun FaroV5Household10DevCutoverRuntime.startScenario(
        case: Scenario,
        confirm: () -> Boolean = { true },
        bytes: ByteArray = case.archive,
        pass: CharArray = key,
    ) = start(activation,case.target,case.origin,bytes,pass,confirm)
    private fun hasFence(case: Scenario) =
        fences.isInstalled(MaintenanceOperationKind.RESTORE,case.target.descriptor.generationId)
    private fun assertOriginIntact(case: Scenario) {
        assertEquals(case.originalDigest,FaroV5Household10DevCutoverOwner.fingerprint(case.original))
    }
    private fun assertSelected(case: Scenario) {
        assertEquals(ActiveGenerationPointer(case.origin.sequence+1,case.target.descriptor.generationId),
            pointer.read())
        assertEquals(BootstrapPhase.COMPLETED,journal.read(activation)?.phase)
        assertTrue(checkNotNull(journal.read(activation)).materialWritesAccepted)
        assertTrue(hasFence(case))
        assertOriginIntact(case)
    }

    @Test fun t01ActivatesTypedAndReopensExistingReadOnlyHostAfterRestart() {
        val case=scenario()
        // RED_FIX diagnostic only: reveal the true exception suppressed by
        // typed preflight's fail-closed Boolean before journaled rollback.
        val diagnosticGate=SingleProcessMaterialWriteGate()
        runtime(gate=diagnosticGate, fault={ point ->
            if (point==GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) {
                val selected=checkNotNull(pointer.read())
                val direct=try {
                    FaroV5GenerationStoreGraph.verifyReopenedHousehold10Evidence2Dev(
                        root,case.target,selected,diagnosticGate,{true},
                    )
                } catch (failure: Throwable) {
                    throw AssertionError("F2B2C_DIRECT_TYPED_ACTIVE_HEALTH_EXCEPTION",failure)
                }
                check(direct) { "F2B2C_DIRECT_TYPED_ACTIVE_HEALTH_RETURNED_FALSE" }
                val preflight=FaroV5Household10DevCutoverPreflight(
                    root,MaterialWriteGateQuiescence(diagnosticGate),{},
                    typedEvidence2=true,materialWriteGate=diagnosticGate,
                )
                check(preflight.verifyAlreadyActive(activation,case.target,selected)) {
                    "F2B2C_TYPED_PREFLIGHT_FALSE_AFTER_DIRECT_HEALTH_TRUE"
                }
            }
        }).use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN,
            it.startScenario(case).status) }
        assertSelected(case)
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN,
            it.recover()?.status) }
        assertSelected(case)
        val selected=checkNotNull(pointer.read())
        FaroV5GenerationStoreGraph.openActiveHousehold10Evidence2DevForTrustedHost(
            root,case.target,selected,SingleProcessMaterialWriteGate(),{true},
        ).use { session ->
            val typed=checkNotNull(session.authorizedCommercialTypedSnapshot({true},{true}))
            assertEquals(3,typed.records.size)
            assertTrue(typed.records.any { it.terms == null })
            assertTrue(typed.records.any { it.terms == CommercialEvidenceTypedTerms.UNKNOWN })
            assertTrue(typed.records.any { it.terms?.promotionKind == CommercialPromotionKind.DISCOUNT_PERCENT })
            assertEquals(8L,session.readNeeds {true}.revision)
        }
    }

    @Test fun t02CrashAfterPointerPublishRecoversWithoutSecondCas() {
        val case=scenario()
        runtime(fault={ if(it==GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
            throw Interruption() }).use {
            assertThrows(Interruption::class.java) {it.startScenario(case)}
        }
        val switched=checkNotNull(pointer.read())
        assertEquals(case.origin.sequence+1,switched.sequence)
        assertEquals(BootstrapPhase.SWITCH_PREPARED,journal.read(activation)?.phase)
        assertFalse(hasFence(case))
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN,
            it.recover()?.status) }
        assertEquals(switched,pointer.read())
        assertSelected(case)
    }

    @Test fun t03CrashAfterFenceRecoversAndCorruptionCannotRollBack() {
        val case=scenario()
        runtime(afterFence={ throw Interruption() }).use {
            assertThrows(Interruption::class.java) {it.startScenario(case)}
        }
        val selected=pointer.read()
        assertEquals(BootstrapPhase.HEALTH_VERIFIED,journal.read(activation)?.phase)
        assertTrue(hasFence(case))
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN,
            it.recover()?.status) }
        BundledSQLiteDriver().open(case.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("UPDATE household_need SET name='Tampered' WHERE need_id='need-1'")
        }
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
            it.recover()?.status) }
        assertEquals(selected,pointer.read())
        assertTrue(hasFence(case))
        assertOriginIntact(case)
    }

    @Test fun t04BadTargetHealthRollsBackBeforeFenceAndPreservesOrigin() {
        val case=scenario()
        runtime(healthy={false}).use { assertEquals(
            FaroV5Household10DevCutoverRuntime.Status.ROLLED_BACK,
            it.startScenario(case).status) }
        assertEquals(ActiveGenerationPointer(case.origin.sequence+2,case.origin.generationId),
            pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED,journal.read(activation)?.phase)
        assertFalse(hasFence(case))
        assertOriginIntact(case)
    }

    @Test fun t05RefusesRevokedForegroundWrongSecretTamperAndOldEnvelopeBeforeJournal() {
        val case=scenario()
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
            it.startScenario(case,confirm={false}).status) }
        val wrong="invalid-passphrase-typed".toCharArray()
        val altered=case.archive.copyOf().also {
            it[it.lastIndex]=(it.last().toInt() xor 1).toByte()
        }
        val v5=FaroV5FiveStorePortableBackupCrypto().sealWithReceipts(open(case.archive).base,key)
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
                it.startScenario(case,pass=wrong).status)
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
                it.startScenario(case,bytes=altered).status)
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
                it.startScenario(case,bytes=v5).status)
        }
        assertNull(journal.read(activation))
        assertEquals(case.origin,pointer.read())
        assertFalse(hasFence(case))
    }

    @Test fun t06ExclusiveRootLeaseTicketProfileAndTargetMutationFailClosed() {
        val case=scenario()
        runtime(afterAuthorization={throw Interruption()}).use {
            assertThrows(IllegalStateException::class.java) { runtime().close() }
            assertThrows(Interruption::class.java) { it.startScenario(case) }
        }
        assertEquals(BootstrapPhase.PREPARING,journal.read(activation)?.phase)
        assertEquals(case.origin,pointer.read())
        // The legacy owner cannot interpret a typed v2 ticket in the shared root.
        FaroV5Household10DevCutoverRuntime.acquire(
            root,SingleProcessMaterialWriteGate(),{true},
        ).use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
            it.recover()?.status) }
        BundledSQLiteDriver().open(case.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("UPDATE household_need SET name='Different' WHERE need_id='need-1'")
        }
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
            it.recover()?.status) }
        assertEquals(case.origin,pointer.read())
        assertFalse(hasFence(case))
        assertOriginIntact(case)
        val away=StorageGenerationId("f2b2c-aba-other")
        checkNotNull(pointer.compareAndSet(case.origin.generationId,away))
        val replaced=checkNotNull(pointer.compareAndSet(away,case.origin.generationId))
        runtime().use { assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED,
            it.recover()?.status) }
        assertEquals(replaced,pointer.read())
    }
}
