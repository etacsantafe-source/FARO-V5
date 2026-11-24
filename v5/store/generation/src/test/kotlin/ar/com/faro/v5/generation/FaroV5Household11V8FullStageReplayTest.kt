package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
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
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11V8FullStageReplayTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "w05-a1b3b1-dev-only-passphrase".toCharArray()
    private val op = MaintenanceOperationId("w05-a1b3b1-replay")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val archive: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "fbs2v8-full-stage-dev", 100L),
        )
        val migrated = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-a1b3b1-finance-" + id),
            prepared, origin, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            migrated, pointer,
        ).close()
        return migrated
    }

    private fun need(id: String, revision: Long) = HouseholdNeed(
        HouseholdNeedId(id), HouseholdNeedName("Yerba " + id),
        HouseholdNeedQuantity(1000L * revision, HouseholdNeedUnit.UNIT),
        HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, revision,
    )

    private fun household() = Household10BackupSection(
        3L, listOf(need("a", 1L), need("b", 2L)),
    )

    private fun receipts() = Household11ReceiptBackupSection(2L, listOf(
        Household11ReceiptBackupRow("r-01", "int-01", "purchase", "yerba",
            7000, "UNIT", 0L, 3000L, 1L),
        Household11ReceiptBackupRow("r-02", "int-02", "purchase", "yerba",
            3000, "UNIT", 0L, 3001L, 2L),
    ))

    private fun fixture(
        needs: Household10BackupSection = household(),
        receipts: Household11ReceiptBackupSection = receipts(),
    ): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("w05-a1b3b1-active")))
        val source = stage("w05-a1b3b1-source", origin)
        val target = stage("w05-a1b3b1-target", origin)
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Alimentos','ARS',100000,1,1000,'HUMAN')")
            db.execSQL("INSERT INTO matter_record(matter_id,title,detail,status," +
                "due_at_epoch_millis,updated_at_epoch_millis) " +
                "VALUES('matter-a1b3b1','Revisar','Detalle','OPEN',NULL,1000)")
            db.execSQL("UPDATE matter_state SET revision=1 WHERE singleton_id=1")
        }
        val old = FaroV5Auxiliary4ArchiveCoordinator(root, gate).captureAuthenticated(
            MaintenanceOperationId("w05-b1-fixture-capture"), BackupSetId("backup-a1b3b1"),
            source, origin, "5.0-dev", 3000L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(old, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected ->
                error("Source fixture should authenticate: " + opened.reason)
        }
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, needs), receipts,
        )
        return Fixture(origin, source, target, snapshot,
            crypto.sealWithHouseholdReceipts11(snapshot, passphrase))
    }

    private fun replay(
        phase: (Household11FullStagePhase) -> Unit = {},
        host: () -> Unit = {},
    ) = FaroV5Household11V8FullStageReplay(root, gate, host, phase)

    private fun restore(f: Fixture, bytes: ByteArray = f.archive, key: CharArray = passphrase) =
        replay().restoreAuthenticatedStagedEleven(op, f.target, f.origin, bytes, key)

    private fun verified(f: Fixture) =
        FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)

    private fun blocked(reason: FiveStoreStagedRestoreBlock) =
        FiveStoreStagedRestoreResult.Blocked(reason)

    private fun file(f: Fixture) = f.target.directory.resolve(GenerationStoreFiles.REALITY)

    private fun version(f: Fixture) = BundledSQLiteDriver().open(file(f).toString()).use { db ->
        db.prepare("PRAGMA user_version").use {
            check(it.step())
            it.getLong(0)
        }
    }

    private fun pin(f: Fixture, bytes: ByteArray = f.archive) =
        FileRestoreArchiveIdentity(root).requireExistingPinnedArchive(
            op, f.target.descriptor.generationId, bytes,
        )

    @Test fun fullReplayFromStageNineRestoresNeedsReceiptsFinanceAndLeavesActiveUntouched() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertEquals(11L, version(f))
        assertEquals(2, RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(file(f)).needs.size)
        assertEquals(f.snapshot.receipts,
            FaroV5Household11ReceiptSql11Port(root, gate, {}).captureSealed(op, f.target, f.origin))
        BundledSQLiteDriver().open(file(f).toString()).use { db ->
            db.prepare("SELECT limit_minor FROM finance_budget_limit").use { statement ->
                check(statement.step())
                assertEquals(100000L, statement.getLong(0))
            }
        }
        pin(f)
        assertEquals(f.origin, pointer.read())
    }

    @Test fun completedStageElevenIsIdempotentAndDoesNotDuplicateReceipts() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertEquals(verified(f), restore(f))
        assertEquals(2, FaroV5Household11ReceiptSql11Port(root, gate, {})
            .captureSealed(op, f.target, f.origin).receipts.size)
    }

    @Test fun explicitlyRevisedEmptyNeedsAndReceiptsRetainRevisions() {
        val f = fixture(Household10BackupSection(7L, emptyList()),
            Household11ReceiptBackupSection(9L, emptyList()))
        assertEquals(verified(f), restore(f))
        val h10 = RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(file(f))
        assertEquals(7L, h10.revision)
        assertEquals(0, h10.needs.size)
        val h11 = FaroV5Household11ReceiptSql11Port(root, gate, {})
            .captureSealed(op, f.target, f.origin)
        assertEquals(9L, h11.revision)
        assertEquals(0, h11.receipts.size)
    }

    @Test fun crashAfterNineToTenResumesWithExactPin() {
        val f = fixture()
        val interrupted = replay(phase = {
            if (it == Household11FullStagePhase.MIGRATED_TO_TEN)
                error("crash after SQLite10 migration")
        }).restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        assertEquals(10L, version(f))
        pin(f)
        assertEquals(verified(f), restore(f))
    }

    @Test fun crashAfterNeedsRestoredDoesNotOverwriteHouseholdTen() {
        val f = fixture()
        val interrupted = replay(phase = {
            if (it == Household11FullStagePhase.HOUSEHOLD10_RESTORED)
                error("crash after need write")
        }).restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        assertEquals(10L, version(f))
        assertEquals(verified(f), restore(f))
        assertEquals(2, RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(file(f)).needs.size)
    }

    @Test fun crashAfterElevenMigrationKeepsNeedsAndFinishesReceipts() {
        val f = fixture()
        val interrupted = replay(phase = {
            if (it == Household11FullStagePhase.MIGRATED_TO_ELEVEN)
                error("crash just after SQL11 DDL")
        }).restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        assertEquals(11L, version(f))
        assertEquals(verified(f), restore(f))
    }

    @Test fun crashAfterReceiptCommitReplaysWithoutDuplicateStock() {
        val f = fixture()
        val interrupted = replay(phase = {
            if (it == Household11FullStagePhase.RECEIPTS11_RESTORED)
                error("crash after receipt COMMIT")
        }).restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        assertEquals(11L, version(f))
        assertEquals(verified(f), restore(f))
        assertEquals(2, FaroV5Household11ReceiptSql11Port(root, gate, {})
            .captureSealed(op, f.target, f.origin).receipts.size)
    }

    @Test fun samePlaintextDifferentCiphertextAndOperationIdentityAreRejected() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        val freshNonce = crypto.sealWithHouseholdReceipts11(f.snapshot, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            restore(f, freshNonce))
        pin(f)
        assertEquals(f.origin, pointer.read())
    }

    @Test fun wrongPassphraseCipherTamperAndOlderVersionAreNeverAccepted() {
        val f = fixture()
        val tampered = f.archive.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val oldV6 = crypto.sealWithHousehold(f.snapshot.base, passphrase)
        for ((bytes, key) in listOf(
            f.archive to "incorrect-passphrase".toCharArray(),
            tampered to passphrase,
            oldV6 to passphrase,
        )) {
            val outcome = restore(f, bytes, key) as FiveStoreStagedRestoreResult.Blocked
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, outcome.reason)
        }
        assertEquals(9L, version(f))
        assertThrows(IllegalStateException::class.java) { pin(f) }
    }

    @Test fun foreignNeedsAndUnsafeStageOrRevokedHostFailClosed() {
        val f = fixture()
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            replay(host = { error("DEV host no longer exclusive") })
                .restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase))
        val alias = Files.createLink(root.resolve("a1b3b1-alias.db"), file(f))
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED), restore(f))
        Files.delete(alias)
        val first = replay(phase = {
            if (it == Household11FullStagePhase.MIGRATED_TO_TEN) {
                BundledSQLiteDriver().open(file(f).toString()).use { db ->
                    db.execSQL("UPDATE household_need_state SET revision=1 WHERE singleton_id=1")
                    db.execSQL("INSERT INTO household_need VALUES " +
                        "('foreign','Foreign',1000,'UNIT','LOW','OPEN',1,NULL)")
                }
                error("interrupted by foreign need")
            }
        }).restoreAuthenticatedStagedEleven(op, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), first)
        assertEquals(10L, version(f))
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), restore(f))
        pin(f)
        assertEquals(f.origin, pointer.read())
    }
}
