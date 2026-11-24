package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11DevCutoverAuthorizationTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val journal get() = FileBootstrapJournal(root)
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "w05-a1b3c1a-dev-only".toCharArray()
    private val op = MaintenanceOperationId("w05-a1b3c1a-authorization")
    private val ticket get() = root.resolve("bootstrap/household11-dev-cutover-v1.bin")

    private data class Scenario(
        val active: ActiveGenerationPointer,
        val original: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val archive: ByteArray,
    )
    private class Crash : Error("injected DEV restart")

    private fun stage(id: String, active: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c1a-dev-stage", 100L),
        )
        val finance = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("w05-c1a-finance-" + id),
            fresh, active, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            finance, pointer,
        ).close()
        return finance
    }

    private fun scenario(): Scenario {
        val bootstrap = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c1a-bootstrap"),
        ))
        val original = stage("w05-c1a-origin", bootstrap)
        val active = checkNotNull(pointer.compareAndSet(
            bootstrap.generationId, original.descriptor.generationId,
        ))
        val source = stage("w05-c1a-source", active)
        val target = stage("w05-c1a-target", active)
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Comida','ARS',180000,1,1000,'HUMAN')")
        }
        val baseCipher = FaroV5Auxiliary4ArchiveCoordinator(root, quiescence).captureAuthenticated(
            MaintenanceOperationId("w05-c1a-fixture"), BackupSetId("w05-c1a-backup"),
            source, active, "5.0-dev", 3000L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(baseCipher, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("fixture invalid: " + opened.reason)
        }
        val h10 = Household10BackupSection(3L, listOf(HouseholdNeed(
            HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(500L, HouseholdNeedUnit.GRAM),
            HouseholdNeedPriority.HIGH, HouseholdNeedStatus.OPEN, 1L,
        )))
        val h11 = Household11ReceiptBackupSection(2L, listOf(
            Household11ReceiptBackupRow(
                "receipt-1","intent-1","purchase","yerba",7000,"UNIT",0L,3000L,1L,
            ),
            Household11ReceiptBackupRow(
                "receipt-2","intent-2","purchase","yerba",3000,"UNIT",0L,3001L,2L,
            ),
        ))
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, h10), h11,
        )
        val archive = crypto.sealWithHouseholdReceipts11(snapshot, passphrase)
        val staged = FaroV5Household11V8FullStageReplay(root, quiescence, {})
            .restoreAuthenticatedStagedEleven(op, target, active, archive, passphrase)
        assertEquals(
            FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value),
            staged,
        )
        return Scenario(active, original, target, snapshot, archive)
    }

    private fun runtime(
        host: () -> Boolean = { true },
        afterAuthorization: () -> Unit = {},
        afterTicket: () -> Unit = {},
        afterJournal: () -> Unit = {},
        afterStaged: () -> Unit = {},
    ) = FaroV5Household11DevCutoverAuthorization.acquire(
        root, gate, host, afterAuthorization, afterTicket, afterJournal, afterStaged,
    )

    private fun FaroV5Household11DevCutoverAuthorization.authorize(
        s: Scenario,
        bytes: ByteArray = s.archive,
        key: CharArray = passphrase,
        foreground: () -> Boolean = { true },
    ) = authorize(op, s.target, s.active, bytes, key, foreground)

    private fun assertValidated(s: Scenario) {
        assertEquals(s.active, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(op)?.phase)
        assertFalse(checkNotNull(journal.read(op)).materialWritesAccepted)
        assertFalse(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        assertTrue(Files.isRegularFile(ticket))
    }

    @Test fun authorizationCreatesDurableValidatedJournalWithoutSwitch() {
        val s = scenario()
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(s.target.directory.resolve(it))
        }
        runtime().use { owner ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
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
        val s = scenario()
        runtime().use { owner ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                owner.authorize(s).status)
        }
        val oldTicket = Files.readAllBytes(ticket)
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(oldTicket, Files.readAllBytes(ticket))
        assertValidated(s)
    }

    @Test fun crashAfterTicketRequiresIdenticalArchiveToFinishJournal() {
        val s = scenario()
        runtime(afterTicket = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertTrue(Files.isRegularFile(ticket))
        assertNull(journal.read(op))
        assertEquals(s.active, pointer.read())
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.TICKET_ONLY,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s, key = "wrong-passphrase".toCharArray()).status)
            assertNull(journal.read(op))
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        assertValidated(s)
    }

    @Test fun crashAfterJournalPreparingResumesCanonicalTransitions() {
        val s = scenario()
        runtime(afterJournal = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertEquals(BootstrapPhase.PREPARING, journal.read(op)?.phase)
        assertEquals(s.active, pointer.read())
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.PREPARING,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        assertValidated(s)
    }

    @Test fun crashAfterStagedJournalCanResumeWithoutRestagingSqlite() {
        val s = scenario()
        runtime(afterStaged = { throw Crash() }).use {
            assertThrows(Crash::class.java) { it.authorize(s) }
        }
        assertEquals(BootstrapPhase.STAGED, journal.read(op)?.phase)
        val targetBytes = Files.readAllBytes(s.target.directory.resolve(GenerationStoreFiles.REALITY))
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.STAGED,
                fresh.recoverReadOnly().status)
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.authorize(s).status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            targetBytes, Files.readAllBytes(s.target.directory.resolve(GenerationStoreFiles.REALITY)),
        )
        assertValidated(s)
    }

    @Test fun wrongPassphraseNonceAndFbs2v7CannotReplaceDurableTicket() {
        val s = scenario()
        runtime().use { owner ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                owner.authorize(s).status)
        }
        val oldTicket = Files.readAllBytes(ticket)
        val alteredNonce = crypto.sealWithHouseholdReceipts11(s.snapshot, passphrase)
        val older = crypto.sealWithHousehold(s.snapshot.base, passphrase)
        runtime().use { fresh ->
            for ((bytes,key) in listOf(
                alteredNonce to passphrase,
                older to passphrase,
                s.archive to "wrong-pass".toCharArray(),
            )) {
                assertEquals(FaroV5Household11DevCutoverAuthorization.Status.BLOCKED,
                    fresh.authorize(s, bytes, key).status)
            }
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                fresh.recoverReadOnly().status)
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(oldTicket, Files.readAllBytes(ticket))
        assertValidated(s)
    }

    @Test fun foreignJournalAndRevokedHostCannotCreateAuthority() {
        val s = scenario()
        val other = BootstrapJournalEntry(
            MaintenanceOperationId("w05-c1a-foreign"),
            MaintenanceOperationKind.RESTORE,
            s.active.generationId, s.target.descriptor.generationId,
            BootstrapPhase.PREPARING,
        )
        journal.create(other)
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
        assertNull(journal.read(op))
        assertEquals(s.active, pointer.read())
        assertThrows(IllegalStateException::class.java) {
            runtime(host = { false })
        }
    }

    @Test fun pointerAbaAndPostAuthorizationDriftFailWithoutSwitch() {
        val s = scenario()
        runtime(afterAuthorization = {
            val intermediate = StorageGenerationId("w05-c1a-aba")
            checkNotNull(pointer.compareAndSet(s.active.generationId, intermediate))
            checkNotNull(pointer.compareAndSet(intermediate, s.active.generationId))
        }).use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
        assertNull(journal.read(op))
        // New original pointer sequence is deliberately different: fail closed.
        runtime().use { fresh ->
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.BLOCKED,
                fresh.authorize(s).status)
        }
        assertFalse(Files.exists(ticket))
    }
}
