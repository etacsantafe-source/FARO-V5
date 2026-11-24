package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
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
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11DevJournaledCutoverTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val journal get() = FileBootstrapJournal(root)
    private val gate = SingleProcessMaterialWriteGate()
    private val lease = MaterialWriteGateQuiescence(gate)
    private val key = "w05-a1b3c1b-dev-sql11-test".toCharArray()
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val operation = MaintenanceOperationId("w05-a1b3c1b-activate")
    private val ticket get() = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
    private class Crash : Error("DEV process death simulation")

    private data class Scenario(
        val active: ActiveGenerationPointer,
        val original: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val ciphertext: ByteArray,
    )

    private fun stage(name: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "w05-c1b-stage", 100L),
        )
        val finance = FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("w05-c1b-finance-" + name),
            fresh, origin, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(finance, pointer).close()
        return finance
    }

    private fun scenario(): Scenario {
        val initial = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c1b-bootstrap"),
        ))
        val original = stage("w05-c1b-origin", initial)
        val active = checkNotNull(pointer.compareAndSet(
            initial.generationId, original.descriptor.generationId,
        ))
        val source = stage("w05-c1b-source", active)
        val target = stage("w05-c1b-target", active)
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Alimentos','ARS',180000,1,1000,'HUMAN')")
        }
        val base = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("w05-c1b-capture"), BackupSetId("w05-c1b-backup"),
            source, active, "5.0-dev", 3000L, key,
        )
        val snapBase = when (val p = crypto.openWithReceipts(base, key)) {
            is FiveStoreAuthenticatedPayload.Opened -> p.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("unexpected archive " + p.reason)
        }
        val needs = Household10BackupSection(3L, listOf(HouseholdNeed(
            HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(500L, HouseholdNeedUnit.GRAM),
            HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 1L,
        )))
        val receipts = Household11ReceiptBackupSection(2L, listOf(
            Household11ReceiptBackupRow(
                "receipt-a","intent-a","purchase","yerba",7000,"UNIT",0L,3000L,1L,
            ),
            Household11ReceiptBackupRow(
                "receipt-b","intent-b","purchase","yerba",3000,"UNIT",0L,3001L,2L,
            ),
        ))
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(snapBase, needs), receipts,
        )
        val encrypted = crypto.sealWithHouseholdReceipts11(snapshot, key)
        assertEquals(
            FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value),
            FaroV5Household11V8FullStageReplay(root, lease, {})
                .restoreAuthenticatedStagedEleven(operation, target, active, encrypted, key),
        )
        FaroV5Household11DevCutoverAuthorization.acquire(root, gate, { true }).use {
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                it.authorize(operation, target, active, encrypted, key, { true }).status)
        }
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        return Scenario(active, original, target, snapshot, encrypted)
    }

    private fun runtime(
        host: () -> Boolean = { true },
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        supports: () -> Boolean = { true },
        healthy: () -> Boolean = { true },
    ) = FaroV5Household11DevJournaledCutover.acquire(
        root, gate, host, fault, supports, healthy,
    )

    private fun FaroV5Household11DevJournaledCutover.start(
        s: Scenario,
        bytes: ByteArray = s.ciphertext,
        secret: CharArray = key,
        confirmed: () -> Boolean = { true },
    ) = start(operation, s.target, s.active, bytes, secret, confirmed)

    private fun selected(s: Scenario) = ActiveGenerationPointer(
        s.active.sequence + 1L, s.target.descriptor.generationId,
    )
    private fun rolledBack(s: Scenario) = ActiveGenerationPointer(
        s.active.sequence + 2L, s.active.generationId,
    )

    private fun assertActive(s: Scenario) {
        assertEquals(selected(s), pointer.read())
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        assertTrue(checkNotNull(journal.read(operation)).materialWritesAccepted)
        assertTrue(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        val reality = s.target.directory.resolve(GenerationStoreFiles.REALITY)
        assertEquals(1, RealityHousehold11NeedsRowsPort
            .readTrustedSealedStageEleven(reality).needs.size)
        assertEquals(2, RealityHousehold11ReceiptRowsPort
            .readTrustedSealedStageEleven(reality).receipts.size)
    }

    @Test fun authorizedSql11SwitchCompletesWithFullPointerJournalFenceAndExactStores() {
        val s = scenario()
        val oldOrigin = FaroV5Household10DevCutoverOwner.fingerprint(s.original)
        val oldTarget = FaroV5Household10DevCutoverOwner.fingerprint(s.target)
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.start(s).status)
        }
        assertActive(s)
        assertEquals(oldOrigin, FaroV5Household10DevCutoverOwner.fingerprint(s.original))
        assertEquals(oldTarget, FaroV5Household10DevCutoverOwner.fingerprint(s.target))
    }

    @Test fun restartAfterCompletedSwitchIsIdempotentAndNeverReissuesPointerCas() {
        val s = scenario()
        runtime().use { assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
            it.start(s).status) }
        val selected = checkNotNull(pointer.read())
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.recover().status)
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.start(s).status)
        }
        assertEquals(selected, pointer.read())
        assertActive(s)
    }

    @Test fun startupOnlyValidatedC1aJournalNeedsSeparateForegroundSwitchAuthorization() {
        val s = scenario()
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.AWAITING_FOREGROUND,
                it.recover().status)
            assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
                it.start(s, confirmed = { false }).status)
        }
        assertEquals(s.active, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
    }

    @Test fun crashAfterTargetPointerBeforeJournalAdvanceRecoversExactlyOnce() {
        val s = scenario()
        runtime(fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE)
                throw Crash()
        }).use { assertThrows(Crash::class.java) { it.start(s) } }
        assertEquals(selected(s), pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
        assertFalse(checkNotNull(journal.read(operation)).materialWritesAccepted)
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.recover().status)
        }
        assertActive(s)
    }

    @Test fun unhealthySql11ReopenRollsBackBeforeWritesAndPreservesOldGeneration() {
        val s = scenario()
        runtime(healthy = { false }).use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ROLLED_BACK,
                it.start(s).status)
        }
        assertEquals(rolledBack(s), pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
        assertFalse(checkNotNull(journal.read(operation)).materialWritesAccepted)
        assertFalse(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, s.target.descriptor.generationId,
        ))
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ROLLED_BACK,
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
        assertEquals(BootstrapPhase.ROLLBACK_PREPARED, journal.read(operation)?.phase)
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ROLLED_BACK,
                it.recover().status)
        }
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
    }

    @Test fun changedCiphertextNoncePassphraseAndCorruptTicketAreRejected() {
        val s = scenario()
        val altered = crypto.sealWithHouseholdReceipts11(s.snapshot, key)
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
                it.start(s, bytes = altered).status)
            assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
                it.start(s, secret = "bad-passphrase".toCharArray()).status)
        }
        assertEquals(s.active, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        val bytes = Files.readAllBytes(ticket)
        Files.write(ticket, bytes.also { a -> a[a.lastIndex] = (a.last().toInt() xor 1).toByte() })
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
                it.recover().status)
        }
        assertEquals(s.active, pointer.read())
    }

    @Test fun foreignPointerDriftHostRevocationAndRealityDriftNeverActivate() {
        val s = scenario()
        assertThrows(IllegalStateException::class.java) {
            runtime(host = { false })
        }
        val targetReality = s.target.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(targetReality.toString()).use { db ->
            db.execSQL("UPDATE household_stock_receipt_state SET revision=9 WHERE singleton_id=1")
        }
        runtime().use { assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
            it.start(s).status) }
        assertEquals(s.active, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        val foreign = StorageGenerationId("w05-c1b-foreign-pointer")
        checkNotNull(pointer.compareAndSet(s.active.generationId, foreign))
        runtime().use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.BLOCKED,
                it.recover().status)
        }
        assertEquals(foreign, checkNotNull(pointer.read()).generationId)
    }
}
