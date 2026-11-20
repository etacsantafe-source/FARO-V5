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
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10ArchiveRestoreTest {
    @TempDir lateinit var root: Path

    private val passphrase = "aux4-archive-fixture-only".toCharArray()
    private val pointer get() = FileActiveGenerationPointer(root)
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun origin(): ActiveGenerationPointer =
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("archive-origin")))

    private fun stage(name: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "receipt-archive", 100L),
        )
        return FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("migrate-" + name), prepared, origin, "a".repeat(64), 1000L,
        )
    }

    private fun source(origin: ActiveGenerationPointer, needs: Boolean = true): StagedStorageGeneration {
        val source = stage("archive-source", origin)
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO matter_record(matter_id,title,detail,status,due_at_epoch_millis,updated_at_epoch_millis) " +
                "VALUES('matter-source','Asunto de prueba','Detalle','OPEN',NULL,1000)")
            db.execSQL("UPDATE matter_state SET revision=1 WHERE singleton_id=1")
            db.execSQL("INSERT INTO reminder_rule VALUES('source-rule','Aviso','UTC','2026-11-19'," +
                "'08:00','ONCE',1,'matter-source',1000)")
            db.execSQL("UPDATE reminder_state SET revision=1 WHERE singleton_id=1")
            db.execSQL("INSERT INTO finance_budget_limit VALUES('2026-11','Comida','ARS',300000,1,1000,'HUMAN')")
        }
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { db ->
            db.execSQL("INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision) " +
                "VALUES(1,'workspace-ref','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',1)")
            db.execSQL("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES('ideas','Ideas',1)")
        }
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).use { owner ->
            val notice = owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:source-occ"),
                SourceRef.of("reminder:source-rule"),
                OccurrenceRef.of("source-occ"),
                NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt(
                "source-click", notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
        }
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(source, origin).use {
            val workspace = checkNotNull(it.workspace)
            workspace.backup.restoreOrVerify(workspace.backup.snapshot().copy(revision = 9L))
        }
        val reality = source.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=8")
            if (needs) {
                db.execSQL("INSERT INTO household_need VALUES ('b','Leche',1000,'LITER','NORMAL','OPEN',6,NULL)")
                db.execSQL("INSERT INTO household_need VALUES ('a','Yerba',500,'GRAM','HIGH','COMPLETED',7,20000)")
            }
        }
        return source
    }

    private fun capture(source: StagedStorageGeneration, origin: ActiveGenerationPointer, probe: () -> Unit = {}) =
        FaroV5Household10ArchiveCoordinator(root, lease, probe).captureAuthenticated(
            MaintenanceOperationId("capture-household"), BackupSetId("backup-household"), source, origin,
            "5.0-dev", 2000L, passphrase,
        )

    private fun open(bytes: ByteArray): FaroV5Household10Fbs2Snapshot =
        when (val result = FaroV5FiveStorePortableBackupCrypto().openWithHousehold(bytes, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Unexpected rejected archive: " + result.reason)
        }

    private val op = MaintenanceOperationId("restore-household")
    private fun coordinator(
        afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
        afterPhase: (Household10RestorePhase) -> Unit = {},
    ) = FaroV5Household10ArchiveCoordinator(root, lease, {}, afterStore, afterPhase)

    private fun restore(target: StagedStorageGeneration, origin: ActiveGenerationPointer, archive: ByteArray) =
        coordinator().restoreAuthenticated(op, target, origin, archive, passphrase)

    private fun verified(target: StagedStorageGeneration) =
        FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value)

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)

    @Test fun roundTripsFiveStoreBaseHouseholdAndReceiptsWithExactReplay() {
        val origin = origin()
        val source = source(origin)
        val bytes = capture(source, origin)
        val expected = open(bytes)
        val target = stage("target", origin)
        assertEquals(verified(target), restore(target, origin, bytes))
        val actual = open(capture(target, origin))
        assertEquals(expected.household, actual.household)
        assertEquals(1, actual.base.receipts.size)
        assertEquals(9L, actual.base.base.workspace.revision)
        assertEquals(expected.base, actual.base.copy(base = actual.base.base.copy(
            fourStore = actual.base.base.fourStore.copy(manifest = expected.base.base.fourStore.manifest),
        )))
        assertEquals(verified(target), restore(target, origin, bytes))
        assertEquals(origin, pointer.read())
    }

    @Test fun preservesRevisionOnRevisedEmptyHousehold() {
        val origin = origin()
        val bytes = capture(source(origin, needs = false), origin)
        val target = stage("target", origin)
        assertEquals(verified(target), restore(target, origin, bytes))
        assertEquals(Household10BackupSection(8, emptyList()), open(capture(target, origin)).household)
    }

    @Test fun rejectsTamperedWrongPassphraseAndV5BeforeChangingTarget() {
        val origin = origin()
        val bytes = capture(source(origin), origin)
        val target = stage("target", origin)
        val files = GenerationStoreFiles.WORKSPACE_V2.map { target.directory.resolve(it) }
        val before = files.map { Files.readAllBytes(it) }
        val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val v5 = FaroV5FiveStorePortableBackupCrypto().sealWithReceipts(open(bytes).base, passphrase)
        val attempts = listOf(
            bytes to "incorrect-fixture-passphrase".toCharArray(), tampered to passphrase, v5 to passphrase,
        )
        for ((encrypted, key) in attempts) {
            val result = coordinator().restoreAuthenticated(op, target, origin, encrypted, key)
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, (result as FiveStoreStagedRestoreResult.Blocked).reason)
        }
        files.zip(before).forEach { (path, expected) -> assertArrayEquals(expected, Files.readAllBytes(path)) }
    }

    @Test fun resumesAfterInterruptedFiveStoreReplayAndRejectsAnotherCiphertext() {
        val origin = origin()
        val source = source(origin)
        val bytes = capture(source, origin)
        val target = stage("target", origin)
        val stopped = coordinator(afterStore = { if (it == FiveStoreStagedRestoreSection.AUXILIARY) error("restart") })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stopped.restoreAuthenticated(op, target, origin, bytes, passphrase))
        assertEquals(blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            restore(target, origin, capture(source, origin)))
        assertEquals(verified(target), restore(target, origin, bytes))
        assertEquals(origin, pointer.read())
    }

    @Test fun resumesAfterMigrationAndAfterHouseholdCommitWithoutAdoptingForeignOperations() {
        val origin = origin()
        val bytes = capture(source(origin), origin)
        val target = stage("target", origin)
        val stoppedAfterMigration = coordinator(afterPhase = {
            if (it == Household10RestorePhase.MIGRATED_TO_TEN) error("restart")
        })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stoppedAfterMigration.restoreAuthenticated(op, target, origin, bytes, passphrase))
        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        assertEquals(0L, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality).revision)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            coordinator().restoreAuthenticated(MaintenanceOperationId("foreign-retry"), target, origin, bytes, passphrase))
        val stoppedAfterRestore = coordinator(afterPhase = {
            if (it == Household10RestorePhase.HOUSEHOLD_RESTORED) error("restart")
        })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stoppedAfterRestore.restoreAuthenticated(op, target, origin, bytes, passphrase))
        assertEquals(8L, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality).revision)
        assertEquals(verified(target), restore(target, origin, bytes))
        assertEquals(origin, pointer.read())
    }

    @Test fun refusesChangedFinanceAfterMigrationBeforeRestoringHousehold() {
        val origin = origin()
        val bytes = capture(source(origin), origin)
        val target = stage("target", origin)
        val stopped = coordinator(afterPhase = {
            if (it == Household10RestorePhase.MIGRATED_TO_TEN) error("restart")
        })
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            stopped.restoreAuthenticated(op, target, origin, bytes, passphrase))
        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.execSQL("UPDATE finance_budget_limit SET limit_minor=1")
        }
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), restore(target, origin, bytes))
        assertEquals(0L, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality).revision)
        assertEquals(origin, pointer.read())
    }

    @Test fun refusesHardlinkedOrActiveTargetsAndPreservesNonWalSource() {
        val origin = origin()
        val source = source(origin)
        val bytes = capture(source, origin)
        val target = stage("target", origin)
        val path = target.directory.resolve(GenerationStoreFiles.REALITY)
        val alias = Files.createLink(root.resolve("alias.sqlite"), path)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            restore(target, origin, bytes))
        Files.delete(alias)
        val sourceWorkspace = source.directory.resolve(GenerationStoreFiles.WORKSPACE)
        BundledSQLiteDriver().open(sourceWorkspace.toString()).use {
            it.prepare("PRAGMA journal_mode=DELETE").use { stmt -> assertTrue(stmt.step()) }
        }
        val before = Files.readAllBytes(sourceWorkspace)
        assertThrows(IllegalStateException::class.java) { capture(source, origin) }
        assertArrayEquals(before, Files.readAllBytes(sourceWorkspace))
        val activated = checkNotNull(pointer.compareAndSet(origin.generationId, target.descriptor.generationId))
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            restore(target, activated, bytes))
    }
}
