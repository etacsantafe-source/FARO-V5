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
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRowsPort
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
class FaroV5Household15V12StagedReplayTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-a2c0b3-v12-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-a2c0b3-v12-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household15Fbs2Snapshot,
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
        val v11 = FaroV5Household14Fbs2Snapshot(payload, listOf(reserve, release))
        val consume = Household15ConsumptionBackupRow(
            "consume", "consume-intent", Household15ConsumptionEventType.CONSUME,
            "consume", "yerba", "UNIT", 1_000, "local-human", "manual-use",
            null, 3_005L, 3_006L, 6L,
        )
        val undo = consume.copy(
            eventId = "consume-undo", intentKey = "consume-undo-intent",
            type = Household15ConsumptionEventType.CONSUME_UNDO,
            quantityThousandths = 500, occurredAtMs = 3_007L, recordedAtMs = 3_008L, revision = 7L,
        )
        val modern = FaroV5Household15Fbs2Snapshot(v11, listOf(consume, undo))
        return Fixture(origin, src, dest, modern, evidence2, crypto.sealWithHouseholdConsumption15(modern, key))
    }

    private fun replay(
        fence: () -> Unit = {},
        after: (Household15StagePhase) -> Unit = {},
    ) = FaroV5Household15V12StagedReplay(root, gate, fence, after)

    private fun completed(f: Fixture) = FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)
    private fun reality(stage: StagedStorageGeneration) = stage.directory.resolve(GenerationStoreFiles.REALITY)
    private fun restore(f: Fixture, archive: ByteArray = f.ciphertext, passphrase: CharArray = key) =
        replay().restoreAuthenticatedV12(op, f.target, f.origin, archive, passphrase)

    @Test
    fun authenticatedV12ReplaysFiveStoresToSealedSql15AndRetriesIdempotently() {
        val f = fixture()
        val sourceBefore = Files.readAllBytes(reality(f.source))
        assertEquals(completed(f), restore(f))
        assertEquals(15L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f.target)))
        val observed = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        assertEquals(listOf("consume", "consume-undo"), observed.consumptions.map { it.eventId })
        assertEquals(listOf("reserve", "release"), observed.historical.reservations.map { it.eventId })
        assertEquals(2, observed.historical.historical.historical.receipts.receipts.size)
        assertEquals(1, observed.historical.historical.compensations.size)
        assertEquals(1, observed.historical.historical.historical.purchases.size)
        assertEquals(completed(f), restore(f))
        assertEquals(observed, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {})
        assertEquals(f.origin, pointer.read())
        assertArrayEquals(sourceBefore, Files.readAllBytes(reality(f.source)))
    }

    @Test
    fun rejectsWrongPassphraseCorruptCiphertextAndV11WithoutTouchingStores() {
        val f = fixture()
        val original = GenerationStoreFiles.WORKSPACE_V2.associateWith { Files.readAllBytes(f.target.directory.resolve(it)) }
        val corrupt = f.ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val v11 = crypto.sealWithHouseholdReservations14(f.snapshot.base, key)
        for (result in listOf(
            restore(f, passphrase = "different secret".toCharArray()),
            restore(f, archive = corrupt),
            restore(f, archive = v11),
        )) {
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                (result as FiveStoreStagedRestoreResult.Blocked).reason)
        }
        original.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(f.target.directory.resolve(name))) }
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun interruptedAfterSql15MigrationResumesExactArchiveAndRejectsReEncryption() {
        val f = fixture()
        assertThrows(Interruption::class.java) {
            replay(after = {
                if (it == Household15StagePhase.SQL15_STAGED_MIGRATED) throw Interruption()
            }).restoreAuthenticatedV12(op, f.target, f.origin, f.ciphertext, key)
        }
        assertEquals(15L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f.target)))
        assertTrue(RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
            .consumptions.isEmpty())
        val distinctCiphertext = crypto.sealWithHouseholdConsumption15(f.snapshot, key)
        assertEquals(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT,
            (restore(f, archive = distinctCiphertext) as FiveStoreStagedRestoreResult.Blocked).reason)
        assertEquals(completed(f), restore(f))
        assertEquals(completed(f), restore(f))
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun interruptionBeforeConsumptionCommitRollsBackRowsAndReplaysOnce() {
        val f = fixture()
        assertThrows(Interruption::class.java) {
            replay(after = {
                if (it == Household15StagePhase.BEFORE_CONSUMPTION_COMMIT) throw Interruption()
            }).restoreAuthenticatedV12(op, f.target, f.origin, f.ciphertext, key)
        }
        val empty = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        assertTrue(empty.consumptions.isEmpty())
        assertEquals(completed(f), restore(f))
        assertEquals(2, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
            .consumptions.size)
    }

    @Test
    fun sidecarsOrRevokedExclusiveHostBlockBeforeSql15WithoutPointerSwitch() {
        val f = fixture()
        val file = reality(f.target)
        val sidecar = Files.createFile(file.resolveSibling(file.fileName.toString() + "-wal"))
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
        Files.delete(sidecar)
        var allowed = true
        val halted = replay(fence = { check(allowed) }, after = {
            if (it == Household15StagePhase.BASE_FIVE_STORES_VERIFIED) allowed = false
        })
        assertTrue(halted.restoreAuthenticatedV12(op, f.target, f.origin, f.ciphertext, key)
            is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(14L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file))
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun sourceEqualsTargetOrPointerAbaIsDenied() {
        val f = fixture()
        val same = replay().restoreAuthenticatedV12(op, f.source, f.origin, f.ciphertext, key)
        assertEquals(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
            (same as FiveStoreStagedRestoreResult.Blocked).reason)
        val moved = checkNotNull(pointer.compareAndSet(f.origin.generationId, StorageGenerationId("other-active")))
        val aba = checkNotNull(pointer.compareAndSet(moved.generationId, f.origin.generationId))
        assertTrue(restore(f) is FiveStoreStagedRestoreResult.Blocked)
        assertEquals(aba, pointer.read())
        assertEquals(9L, RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality(f.target)))
    }

    @Test
    fun divergentSealedSql15ConsumptionCannotBeSilentlyReplayed() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        BundledSQLiteDriver().open(reality(f.target).toString()).use { db ->
            db.execSQL("""
                INSERT INTO household_stock_consumption_event VALUES
                ('extra','extra-intent','CONSUME','extra','yerba','UNIT',200,
                 'local-human','manual-use',NULL,3010,3010,8)
            """.trimIndent())
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.step() && it.getLong(0) == 0L) }
        }
        val before = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        val rejected = restore(f) as FiveStoreStagedRestoreResult.Blocked
        assertEquals(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED, rejected.reason)
        assertEquals(before, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {})
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun divergentHistoricalReservationInSql15IsDeniedWithoutOverwrite() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        BundledSQLiteDriver().open(reality(f.target).toString()).use { db ->
            db.execSQL("""
                INSERT INTO household_stock_reservation_event VALUES
                ('late-release','late-release-intent','RELEASE','reserve','yerba','UNIT',
                 200,'local-human',3010,8)
            """.trimIndent())
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.step() && it.getLong(0) == 0L) }
        }
        val before = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        val rejected = restore(f) as FiveStoreStagedRestoreResult.Blocked
        assertEquals(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED, rejected.reason)
        assertEquals(before, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {})
        assertEquals(f.origin, pointer.read())
    }

    // W05-A2C0C0: admission is READ_ONLY evidence, never an activation ticket.
    private fun preCutover() = FaroV5Household15PreCutoverReadback(root, gate, {})

    private fun verifySealed(f: Fixture, archive: ByteArray = f.ciphertext, phrase: CharArray = key) =
        preCutover().verifyOnly(op, f.target, f.origin, archive, phrase)

    @Test
    fun precutoverReadbackAuthenticatesOriginalSql15StageAndNeverWritesAnyStore() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.target.directory.resolve(it))
        }
        val evidence = verifySealed(f)
        assertEquals(f.source.descriptor.generationId, evidence.sourceGenerationId)
        assertEquals(f.target.descriptor.generationId, evidence.stageGenerationId)
        assertTrue(evidence.authenticatedArchiveSha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(evidence, verifySealed(f))
        before.forEach { (name, bytes) ->
            assertArrayEquals(bytes, Files.readAllBytes(f.target.directory.resolve(name)))
        }
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun precutoverRejectsWrongPassphraseWrongVersionTamperingAndResealedBytes() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val corrupt = f.ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val legacy = crypto.sealWithHouseholdReservations14(f.snapshot.base, key)
        val resealed = crypto.sealWithHouseholdConsumption15(f.snapshot, key)
        for ((bytes, secret) in listOf(
            f.ciphertext to "different secret".toCharArray(),
            corrupt to key,
            legacy to key,
            resealed to key,
        )) {
            assertTrue(runCatching { verifySealed(f, bytes, secret) }.isFailure)
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(2, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
            .consumptions.size)
    }

    @Test
    fun precutoverRefusesSemanticallyDivergentSql15RowsWithoutRepairingThem() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        BundledSQLiteDriver().open(reality(f.target).toString()).use { db ->
            db.execSQL("""
                INSERT INTO household_stock_consumption_event VALUES
                ('extra','extra-intent','CONSUME','extra','yerba','UNIT',200,
                 'local-human','manual-use',NULL,3010,3010,8)
            """.trimIndent())
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.step() && it.getLong(0) == 0L) }
        }
        val before = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        assertEquals(before, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {})
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun precutoverDeniesSidecarsAndPointerAbaAndDoesNotSwitchIt() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val file = reality(f.target)
        val sidecar = Files.createFile(file.resolveSibling(file.fileName.toString() + "-wal"))
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        Files.delete(sidecar)
        val moved = checkNotNull(pointer.compareAndSet(f.origin.generationId, StorageGenerationId("precutover-aba")))
        val aba = checkNotNull(pointer.compareAndSet(moved.generationId, f.origin.generationId))
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        assertEquals(aba, pointer.read())
    }

    @Test
    fun precutoverRequiresExactSql15AndFiveHealthyStoreSchemas() {
        val f = fixture()
        assertTrue(runCatching { verifySealed(f) }.isFailure) // SQL9 target not yet replayed.
        assertEquals(completed(f), restore(f))
        BundledSQLiteDriver().open(reality(f.target).toString()).use { db ->
            db.execSQL("ALTER TABLE objective_record RENAME TO objective_record_invalid")
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.step() && it.getLong(0) == 0L) }
        }
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun sql15SemanticGateRejectsFinance9MutationEvenWithIdenticalStockAndHealthySqlite() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val before = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        val rootFile = reality(f.target)
        BundledSQLiteDriver().open(rootFile.toString()).use { db ->
            db.execSQL(
                "INSERT INTO finance_budget_limit VALUES " +
                    "('2026-12','Alimentos','ARS',12345,1,4500,'HUMAN')",
            )
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L)
            }
        }
        assertEquals(before, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(rootFile) {})
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        assertEquals(f.origin, pointer.read())
    }

    @Test
    fun sql15SemanticGateRejectsNewEvidence2QuoteWithoutChangingStockLedger() {
        val f = fixture()
        assertEquals(completed(f), restore(f))
        val before = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {}
        CommercialEvidenceStore(
            fileName = commercial(f.target), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 400L },
            evidenceIdFactory = { CommercialEvidenceId("w05-a2c0c1a-added-evidence") },
        ).use { store ->
            store.recordWithTypedTerms(
                observation("after-backup"), knownTerms(),
                IdempotencyKey("w05-a2c0c1a-added-intent"),
                PayloadFingerprint("d".repeat(64)),
            )
        }
        assertEquals(before, RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality(f.target)) {})
        assertTrue(runCatching { verifySealed(f) }.isFailure)
        assertEquals(f.origin, pointer.read())
    }

    private class Interruption : Error()
}
