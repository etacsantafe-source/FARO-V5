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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** W05 C2C1: ACTIVE lease acceptance using C2C0 and genuine five-store SQL11 fixtures. */
class FaroV5Household11Evidence2ActiveDevReadHostTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val key = "w05-c2b1b-typed-fixture-only".toCharArray()
    private val op = MaintenanceOperationId("w05-c2b1b-v8-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val ciphertext: ByteArray,
    )

    private val journal get() = FileBootstrapJournal(root)
    private class Crash : Error("injected Evidence2 DEV restart")

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c2b1b-dev-stage", 100L))
        val finance = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("w05-c2b1b-finance-" + id),
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
            evidenceIdFactory = { CommercialEvidenceId("c2b1b-legacy") },
        ).use {
            it.record(observation("legacy"), IdempotencyKey("c2b1b-legacy"),
                PayloadFingerprint("c".repeat(64)))
        }
        var id = 0
        CommercialEvidenceStore(
            fileName = commercial(stage), stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true, clock = { 300L },
            evidenceIdFactory = { CommercialEvidenceId("c2b1b-typed-" + (++id)) },
        ).use { owner ->
            if (withRows) {
                owner.recordWithTypedTerms(
                    observation("unknown"), CommercialEvidenceTypedTerms.UNKNOWN,
                    IdempotencyKey("c2b1b-unknown"), PayloadFingerprint("a".repeat(64)))
                owner.recordWithTypedTerms(
                    observation("known"), knownTerms(),
                    IdempotencyKey("c2b1b-known"), PayloadFingerprint("b".repeat(64)))
            }
        }
    }

    private fun source(origin: ActiveGenerationPointer): StagedStorageGeneration {
        val src = stage("w05-c2b1b-source", origin)
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
        stage("w05-c2b1b-target", origin).also {
            toEvidence2(it, false)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                it, allowAuxiliary3 = false)
        }

    private fun scenario(): Fixture {
        val bootstrap = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c2b1b-bootstrap")))
        val originalStage = stage("w05-c2b1b-origin", bootstrap)
        val origin = checkNotNull(pointer.compareAndSet(
            bootstrap.generationId, originalStage.descriptor.generationId))
        val src = source(origin)
        val dest = target(origin)
        val archivedV7 = FaroV5Household10ArchiveCoordinator(root, quiescence, {})
            .captureAuthenticatedEvidence2(
                MaintenanceOperationId("w05-c2b1b-capture"),
                BackupSetId("w05-c2b1b-v7-backup"), src, origin,
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
        val stagedFixture = Fixture(origin, src, dest, payload, bytes)
        assertEquals(verified(stagedFixture), restore(stagedFixture))
        FaroV5Household11Evidence2DevCutoverAuthorization.acquire(root, gate, { true }).use {
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                it.authorize(op, dest, origin, bytes, key) { true }.status)
        }
        return stagedFixture
    }

    private fun restore(f: Fixture, bytes: ByteArray = f.ciphertext,
                        pass: CharArray = key) =
        FaroV5Household11V8FullStageReplay(root, quiescence, {}).restoreAuthenticatedStagedEleven(
            op, f.target, f.origin, bytes, pass)

    private fun verified(f: Fixture) =
        FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)


    private fun reopen(host: () -> Boolean = { true }) =
        FaroV5Household11Evidence2ActiveDevReadHost.reopen(root, gate, host)

    private fun cutover(s: Fixture, healthy: Boolean = true) =
        FaroV5Household11Evidence2DevJournaledCutover.acquire(
            root, gate, { true }, targetRuntimeHealthy = { healthy },
        ).use { it.start(op, s.target, s.origin, s.ciphertext, key) { true } }

    @Test fun validatedTicketNeedsForegroundAndNeverSelectsTarget() {
        val s = scenario()
        val result = reopen()
        assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.AWAITING_FOREGROUND, result.status)
        assertNull(result.session)
        assertEquals(s.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
    }

    @Test fun incompleteAuthorizedCutoverRecoversAndLeasesOnlyActiveTarget() {
        val s = scenario()
        FaroV5Household11Evidence2DevJournaledCutover.acquire(root, gate, { true }, fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) throw Crash()
        }).use { owner ->
            assertThrows(Crash::class.java) { owner.start(op, s.target, s.origin, s.ciphertext, key) { true } }
        }
        checkNotNull(reopen().session).use { session ->
            assertEquals(s.target.directory.resolve(GenerationStoreFiles.REALITY),
                session.readPinnedReality { it })
            assertEquals(3L, session.expectedRows().needRevision)
        }
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)?.phase)
    }

    @Test fun rollbackWithNoFenceNeverExposesTarget() {
        val s = scenario()
        assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ROLLED_BACK, cutover(s, false).status)
        val result = reopen()
        assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.ROLLED_BACK, result.status)
        assertNull(result.session)
        assertEquals(s.origin.generationId, pointer.read()?.generationId)
    }

    @Test fun revokedHostAndPointerAbaCannotReadOrReacquire() {
        val s = scenario()
        val selected = checkNotNull(cutover(s).pointer)
        var live = true
        val session = checkNotNull(reopen { live }.session)
        session.use {
            live = false
            assertThrows(IllegalStateException::class.java) { session.readPinnedReality { Unit } }
        }
        assertThrows(IllegalStateException::class.java) { session.readPinnedReality { Unit } }
        val other = StorageGenerationId("w05-c2c1-aba")
        checkNotNull(pointer.compareAndSet(selected.generationId, other))
        checkNotNull(pointer.compareAndSet(other, selected.generationId))
        assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.BLOCKED, reopen().status)
    }

    @Test fun completedReopensTwiceAndExportsRealFixtureForAndroid() {
        val s = scenario()
        val selected = checkNotNull(cutover(s).pointer)
        repeat(2) {
            val result = reopen()
            assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.ACTIVE_ELEVEN, result.status)
            checkNotNull(result.session).use { session ->
                assertEquals("reality.db", session.readPinnedReality { it.fileName.toString() })
            }
        }
        assertEquals(selected, pointer.read())
        System.getenv("FARO_C2C1_ANDROID_FIXTURE")?.let { output ->
            val out = Path.of(output)
            copyFixture(out.resolve("sealed"))
            val reality = s.target.directory.resolve(GenerationStoreFiles.REALITY)
            BundledSQLiteDriver().open(reality.toString()).use { db ->
                db.prepare("PRAGMA wal_autocheckpoint=0").use { it.step() }
                db.execSQL("UPDATE household_need_state SET revision=4 WHERE singleton_id=1")
                assertTrue(Files.size(reality.resolveSibling("reality.db-wal")) > 32)
                copyFixture(out.resolve("wal"))
            }
        }
    }

    @Test fun foreignProfileAndMissingStoreFailWithoutCreate() {
        val s = scenario()
        checkNotNull(cutover(s).pointer)
        val legacy = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
        Files.writeString(legacy, "foreign profile")
        assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.BLOCKED, reopen().status)
        Files.delete(legacy)
        val missing = s.target.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        Files.delete(missing)
        assertEquals(FaroV5Household11Evidence2ActiveDevReadHost.Status.BLOCKED, reopen().status)
        assertTrue(Files.notExists(missing))
    }

    private fun copyFixture(destination: Path) {
        Files.walk(root).use { stream ->
            stream.forEach { source ->
                val target = destination.resolve(root.relativize(source))
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else if (!source.fileName.toString().endsWith("-shm")) {
                    Files.copy(source, target)
                }
            }
        }
    }
}
