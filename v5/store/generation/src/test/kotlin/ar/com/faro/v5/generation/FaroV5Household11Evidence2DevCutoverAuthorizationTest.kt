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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** W05 C2B1B: Evidence2 durable foreground ticket/journal authorization, no cutover. */
class FaroV5Household11Evidence2DevCutoverAuthorizationTest {
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

    private fun fixture(): Fixture {
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
        afterAuthorization: () -> Unit = {},
        afterTicket: () -> Unit = {},
        afterJournal: () -> Unit = {},
        afterStaged: () -> Unit = {},
    ) = FaroV5Household11Evidence2DevCutoverAuthorization.acquire(
        root, gate, host, afterAuthorization, afterTicket, afterJournal, afterStaged,
    )

    private fun FaroV5Household11Evidence2DevCutoverAuthorization.authorize(
        s: Fixture,
        bytes: ByteArray = s.ciphertext,
        key: CharArray = this@FaroV5Household11Evidence2DevCutoverAuthorizationTest.key,
        foreground: () -> Boolean = { true },
    ) = authorize(op, s.target, s.origin, bytes, key, foreground)

    private fun assertValidated(s: Fixture) {
        assertEquals(s.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
        assertFalse(checkNotNull(journal.read(op)).materialWritesAccepted)
        assertFalse(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        assertTrue(Files.isRegularFile(ticket))
    }

    @Test fun authorizationCreatesDurableValidatedJournalWithoutSwitch() {
        val s = fixture()
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(s.target.directory.resolve(it))
        }
        runtime().use { owner ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                owner.authorize(s).status)
            assertValidated(s)
        }
        for ((name, bytes) in before) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                bytes, Files.readAllBytes(s.target.directory.resolve(name)),
            )
        }
    }

    @Test fun originalArchiveRetryIsIdempotentAndRecoveryOnlyObserves() {
        val s = fixture()
        runtime().use { owner ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                owner.authorize(s).status)
        }
        val oldTicket = Files.readAllBytes(ticket)
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(oldTicket, Files.readAllBytes(ticket))
        assertValidated(s)
    }

    @Test fun crashAfterTicketRequiresIdenticalArchiveToFinishJournal() {
        val s = fixture()
        runtime(afterTicket = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertTrue(Files.isRegularFile(ticket))
        assertNull(journal.read(op))
        assertEquals(s.origin, pointer.read())
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.TICKET_ONLY,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s, key = "wrong-key".toCharArray()).status)
            assertNull(journal.read(op))
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        assertValidated(s)
    }

    @Test fun crashAfterJournalPreparingResumesCanonicalTransitions() {
        val s = fixture()
        runtime(afterJournal = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertEquals(BootstrapPhase.PREPARING, journal.read(op)?.phase)
        assertEquals(s.origin, pointer.read())
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.PREPARING,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        assertValidated(s)
    }

    @Test fun crashAfterStagedJournalCanResumeWithoutRestagingSqlite() {
        val s = fixture()
        runtime(afterStaged = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertEquals(BootstrapPhase.STAGED, journal.read(op)?.phase)
        val targetBytes = Files.readAllBytes(s.target.directory.resolve(GenerationStoreFiles.REALITY))
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.STAGED,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            targetBytes, Files.readAllBytes(s.target.directory.resolve(GenerationStoreFiles.REALITY)),
        )
        assertValidated(s)
    }

    @Test fun wrongPassphraseNonceAndFbs2v7CannotReplaceDurableTicket() {
        val s = fixture()
        runtime().use { owner ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                owner.authorize(s).status)
        }
        val oldTicket = Files.readAllBytes(ticket)
        val alteredNonce = crypto.sealWithHouseholdReceipts11(s.snapshot, key)
        val older = crypto.sealWithHouseholdEvidence2(s.snapshot.base, key)
        runtime().use { fresh ->
            for ((bytes,key) in listOf(
                alteredNonce to key,
                older to key,
                s.ciphertext to "wrong-pass".toCharArray(),
            )) {
                assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                    fresh.authorize(s, bytes, key).status)
            }
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(oldTicket, Files.readAllBytes(ticket))
        assertValidated(s)
        Files.write(root.resolve("bootstrap/household11-dev-cutover-v1.bin"), "foreign".toByteArray())
        runtime().use { owner ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                owner.authorize(s).status)
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                owner.recoverReadOnly().status)
        }
    }

    @Test fun foreignJournalAndRevokedHostCannotCreateAuthority() {
        val s = fixture()
        val other = BootstrapJournalEntry(
            MaintenanceOperationId("w05-c2b1b-foreign"),
            MaintenanceOperationKind.RESTORE,
            s.origin.generationId, s.target.descriptor.generationId,
            BootstrapPhase.PREPARING,
        )
        journal.create(other)
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
        assertNull(journal.read(op))
        assertEquals(s.origin, pointer.read())
        assertThrows(IllegalStateException::class.java) {
            runtime(host = { false })
        }
    }

    @Test fun pointerAbaAndPostAuthorizationDriftFailWithoutSwitch() {
        val s = fixture()
        var confirmations = 0
        runtime().use { owner ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                owner.authorize(s, foreground = { ++confirmations; confirmations == 1 }).status)
        }
        assertEquals(2, confirmations)
        assertFalse(Files.exists(ticket))
        runtime(afterAuthorization = {
            val intermediate = StorageGenerationId("w05-c2b1b-aba")
            checkNotNull(pointer.compareAndSet(s.origin.generationId, intermediate))
            checkNotNull(pointer.compareAndSet(intermediate, s.origin.generationId))
        }).use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
        assertNull(journal.read(op))
        // New original pointer sequence is deliberately different: fail closed.
        runtime().use { fresh ->
            assertEquals(FaroV5Household11Evidence2DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
    }
}
