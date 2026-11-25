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
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** W05 C2C0: isolated cutover with an authenticated Evidence2 snapshot and real SQLite stores. */
class FaroV5Household11Evidence2DevJournaledCutoverTest {
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
    private val ticket get() = root.resolve("bootstrap/household11-evidence2-dev-cutover-v2.bin")
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


    private fun runtime(
        host: () -> Boolean = { true },
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        supports: () -> Boolean = { true },
        healthy: () -> Boolean = { true },
    ) = FaroV5Household11Evidence2DevJournaledCutover.acquire(
        root, gate, host, fault, supports, healthy,
    )

    private fun FaroV5Household11Evidence2DevJournaledCutover.start(
        s: Fixture,
        bytes: ByteArray = s.ciphertext,
        secret: CharArray = key,
        confirmed: () -> Boolean = { true },
    ) = start(op, s.target, s.origin, bytes, secret, confirmed)

    private fun selected(s: Fixture) = ActiveGenerationPointer(
        s.origin.sequence + 1L, s.target.descriptor.generationId,
    )
    private fun rolledBack(s: Fixture) = ActiveGenerationPointer(
        s.origin.sequence + 2L, s.origin.generationId,
    )

    private fun assertActive(s: Fixture) {
        assertEquals(selected(s), pointer.read())
        assertEquals(BootstrapPhase.COMPLETED, journal.read(op)?.phase)
        assertTrue(checkNotNull(journal.read(op)).materialWritesAccepted)
        assertTrue(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        val typed = SealedCommercialEvidence2SnapshotReader.read(
            s.target.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE),
        )
        assertEquals(s.snapshot.base.base.base.fourStore.commercialEvidence.stagedTyped, typed)
        val reality = s.target.directory.resolve(GenerationStoreFiles.REALITY)
        assertEquals(1, RealityHousehold11NeedsRowsPort
            .readTrustedSealedStageEleven(reality).needs.size)
        assertEquals(2, RealityHousehold11ReceiptRowsPort
            .readTrustedSealedStageEleven(reality).receipts.size)
    }

    @Test fun authorizedSql11SwitchCompletesWithFullPointerJournalFenceAndExactStores() {
        val s = scenario()
        val original = FileStorageGenerationLayout(root).requireStaged(s.origin.generationId)
        val oldOrigin = FaroV5Household10DevCutoverOwner.fingerprint(original)
        val oldTarget = FaroV5Household10DevCutoverOwner.fingerprint(s.target)
        runtime().use {
            var approvals = 0
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.start(s, confirmed = { ++approvals; true }).status)
            assertEquals(2, approvals)
        }
        assertActive(s)
        assertEquals(oldOrigin, FaroV5Household10DevCutoverOwner.fingerprint(original))
        assertEquals(oldTarget, FaroV5Household10DevCutoverOwner.fingerprint(s.target))
    }

    @Test fun restartAfterCompletedSwitchIsIdempotentAndNeverReissuesPointerCas() {
        val s = scenario()
        runtime().use { assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ACTIVE_ELEVEN,
            it.start(s).status) }
        val selected = checkNotNull(pointer.read())
        runtime().use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.recover().status)
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.start(s).status)
        }
        assertEquals(selected, pointer.read())
        assertActive(s)
    }

    @Test fun startupOnlyValidatedEvidence2JournalNeedsSeparateForegroundSwitchAuthorization() {
        val s = scenario()
        runtime().use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND,
                it.recover().status)
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.BLOCKED,
                it.start(s, confirmed = { false }).status)
        }
        assertEquals(s.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
    }

    @Test fun crashAfterTargetPointerBeforeJournalAdvanceRecoversExactlyOnce() {
        val s = scenario()
        runtime(fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
                throw Crash()
        }).use { assertThrows(Crash::class.java) { it.start(s) } }
        assertEquals(selected(s), pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(op)?.phase)
        assertFalse(checkNotNull(journal.read(op)).materialWritesAccepted)
        runtime().use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.recover().status)
        }
        assertActive(s)
    }

    @Test fun unhealthySql11ReopenRollsBackBeforeWritesAndPreservesOldGeneration() {
        val s = scenario()
        runtime(healthy = { false }).use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ROLLED_BACK,
                it.start(s).status)
        }
        assertEquals(rolledBack(s), pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(op)?.phase)
        assertFalse(checkNotNull(journal.read(op)).materialWritesAccepted)
        assertFalse(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        runtime().use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ROLLED_BACK,
                it.recover().status)
        }
    }

    @Test fun crashAfterRollbackPointerBeforeJournalAdvanceRecoversOldAuthority() {
        val s = scenario()
        runtime(healthy = { false }, fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
                throw Crash()
        }).use { assertThrows(Crash::class.java) { it.start(s) } }
        assertEquals(rolledBack(s), pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_PREPARED, journal.read(op)?.phase)
        runtime().use {
            assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.ROLLED_BACK,
                it.recover().status)
        }
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(op)?.phase)
    }

    @Test fun legacyLinkedAndCorruptTicketsAreBlocked() {
        val s = scenario()
        val original = Files.readAllBytes(ticket)
        val legacy = ticket.resolveSibling("household11-dev-cutover-v1.bin")
        runtime().use { owner ->
            Files.write(legacy, original)
            assertBlocked(owner.start(s))
            Files.delete(ticket)
            assertBlocked(owner.recover())
            Files.createSymbolicLink(ticket, legacy.fileName)
            assertBlocked(owner.recover())
            Files.delete(ticket)
            Files.move(legacy, ticket)
            val alias = Files.createLink(ticket.resolveSibling("alias"), ticket)
            assertBlocked(owner.start(s))
            Files.delete(alias)
            Files.write(ticket, original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertBlocked(owner.recover())
        }
        assertUnswitched(s)
    }

    @Test fun alteredArchiveAndEvidence2SemanticDriftAreBlocked() {
        val s = scenario()
        runtime().use { owner ->
            assertBlocked(owner.start(s, secret = "wrong".toCharArray()))
            assertBlocked(owner.start(s, bytes = crypto.sealWithHouseholdReceipts11(s.snapshot, key)))
            assertBlocked(owner.start(s, bytes = crypto.sealWithHouseholdEvidence2(s.snapshot.base, key)))
            val fingerprint = FaroV5Household10DevCutoverOwner.fingerprint(s.target)
            BundledSQLiteDriver().open(commercial(s.target)).use { db ->
                db.execSQL("UPDATE commercial_evidence SET amount_minor=13000")
            }
            val changed = FaroV5Household10DevCutoverOwner.fingerprint(s.target)
            val bytes = Files.readAllBytes(ticket)
            val data = bytes.copyOf(bytes.size - 32)
            val rewritten = data.toString(Charsets.ISO_8859_1).replace(fingerprint, changed)
                .toByteArray(Charsets.ISO_8859_1)
            Files.write(ticket, rewritten + MessageDigest.getInstance("SHA-256").digest(rewritten))
            assertBlocked(owner.start(s))
        }
        assertUnswitched(s)
    }

    @Test fun foreignJournalHostRevocationLockAndPointerAbaAreBlocked() {
        val s = scenario()
        var hostClosed = true
        runtime(host = { hostClosed }).use { owner ->
            assertThrows(IllegalStateException::class.java) { runtime() }
            hostClosed = false
            assertBlocked(owner.recover())
            hostClosed = true
            val foreignId = MaintenanceOperationId("w05-c2c0-foreign")
            val foreign = journal.read(op)!!.copy(operationId = foreignId, phase = BootstrapPhase.PREPARING)
            journal.create(foreign)
            assertBlocked(owner.start(s))
            assertUnswitched(s)
        }
        val other = StorageGenerationId("w05-c2c0-aba")
        checkNotNull(pointer.compareAndSet(s.origin.generationId, other))
        val aba = checkNotNull(pointer.compareAndSet(other, s.origin.generationId))
        runtime().use { assertBlocked(it.recover()) }
        assertEquals(aba, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
    }

    @Test fun consentRevocationAndTargetSubstitutionAreBlocked() {
        val s = scenario()
        assertThrows(IllegalStateException::class.java) { runtime(host = { false }) }
        runtime().use { owner ->
            var approvals = 0
            assertBlocked(owner.start(s, confirmed = { ++approvals == 1 }))
            assertEquals(2, approvals)
            assertBlocked(owner.start(op, s.source, s.origin, s.ciphertext, key) { true })
        }
        assertUnswitched(s)
    }

    private fun assertBlocked(selection: FaroV5Household11Evidence2DevJournaledCutover.Selection) {
        assertEquals(FaroV5Household11Evidence2DevJournaledCutover.Status.BLOCKED, selection.status)
        assertNull(selection.pointer)
    }

    private fun assertUnswitched(s: Fixture) {
        assertEquals(s.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
        assertFalse(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
    }
}
