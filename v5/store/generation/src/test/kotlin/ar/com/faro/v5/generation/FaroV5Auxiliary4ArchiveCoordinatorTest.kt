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
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4ArchiveCoordinatorTest {
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

    private fun source(origin: ActiveGenerationPointer): StagedStorageGeneration {
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
        return source
    }

    private fun capture(
        source: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        id: String,
    ) = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
        MaintenanceOperationId("capture-" + id), BackupSetId("backup-" + id),
        source, origin, "5.0-dev", 2000L, passphrase,
    )

    private fun open(bytes: ByteArray): FaroV5Auxiliary4Fbs2Snapshot =
        when (val result = FaroV5FiveStorePortableBackupCrypto().openWithReceipts(bytes, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Unexpected rejected archive: " + result.reason)
        }

    @Test fun roundTripPreservesReceiptAndFiveStoreDataWithoutTouchingActivePointer() {
        val origin = origin()
        val source = source(origin)
        val target = stage("archive-target", origin)
        val archive = capture(source, origin, "source")
        val expected = open(archive)
        assertEquals(1, expected.receipts.size)
        assertEquals(1, checkNotNull(expected.base.g4Domains).matters.matters.size)
        assertEquals(1, checkNotNull(expected.base.planningDomains).reminders.rules.size)
        assertTrue(FaroV5FiveStorePortableBackupCrypto().open(archive, passphrase)
            is FiveStorePortableBackupOpenResult.Rejected)
        val result = FaroV5Auxiliary4ArchiveCoordinator(root, lease).restoreAuthenticated(
            MaintenanceOperationId("restore-target"), target, origin, archive, passphrase,
        )
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value), result)
        val observed = open(capture(target, origin, "target"))
        assertEquals(expected.receipts, observed.receipts)
        assertEquals(expected.base.fourStore.auxiliary, observed.base.fourStore.auxiliary)
        assertEquals(expected.base.g4Domains, observed.base.g4Domains)
        assertEquals(expected.base.planningDomains, observed.base.planningDomains)
        assertEquals(expected.base.workspace, observed.base.workspace)
        assertEquals(expected.base.finance9, observed.base.finance9)
        assertEquals(origin, pointer.read())
    }

    @Test fun badPasswordAndTamperingLeaveAuxiliary3Unmodified() {
        val origin = origin()
        val archive = capture(source(origin), origin, "source")
        val target = stage("unauthenticated-target", origin)
        val coordinator = FaroV5Auxiliary4ArchiveCoordinator(root, lease)
        val operation = MaintenanceOperationId("restore-invalid")
        val blocked = FiveStoreStagedRestoreResult.Blocked(
            FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
        )
        assertEquals(blocked, coordinator.restoreAuthenticated(
            operation, target, origin, archive, "different-fixture-passphrase".toCharArray(),
        ))
        val tampered = archive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(blocked, coordinator.restoreAuthenticated(operation, target, origin, tampered, passphrase))
        AuxiliaryDeliveryStore(target.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { owner ->
            assertEquals(3, owner.backup.snapshot().schemaVersion)
            assertTrue(owner.readNotices().isEmpty())
        }
        assertEquals(origin, pointer.read())
    }

    @Test fun interruptedReplayResumesSameArchiveAndRefusesAnotherCiphertext() {
        val origin = origin()
        val source = source(origin)
        val archive = capture(source, origin, "source")
        val target = stage("interrupted-target", origin)
        val operation = MaintenanceOperationId("restore-interrupted")
        val interrupted = FaroV5Auxiliary4ArchiveCoordinator(root, lease) { section ->
            if (section == FiveStoreStagedRestoreSection.AUXILIARY) error("injected restart")
        }
        assertEquals(
            FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
            interrupted.restoreAuthenticated(operation, target, origin, archive, passphrase),
        )
        val coordinator = FaroV5Auxiliary4ArchiveCoordinator(root, lease)
        val alternate = capture(source, origin, "alternate")
        assertEquals(
            FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            coordinator.restoreAuthenticated(operation, target, origin, alternate, passphrase),
        )
        val expected = FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value)
        assertEquals(expected, coordinator.restoreAuthenticated(operation, target, origin, archive, passphrase))
        assertEquals(expected, coordinator.restoreAuthenticated(operation, target, origin, archive, passphrase))
        assertEquals(open(archive).receipts, open(capture(target, origin, "resumed")).receipts)
        assertEquals(origin, pointer.read())
    }

    @Test fun aCurrentlyActiveTargetIsRejectedBeforeItsVersionCanChange() {
        val origin = origin()
        val archive = capture(source(origin), origin, "source")
        val target = stage("now-active-target", origin)
        val current = checkNotNull(pointer.compareAndSet(origin.generationId, target.descriptor.generationId))
        assertEquals(
            FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            FaroV5Auxiliary4ArchiveCoordinator(root, lease).restoreAuthenticated(
                MaintenanceOperationId("restore-active"), target, current, archive, passphrase,
            ),
        )
        AuxiliaryDeliveryStore(target.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { owner ->
            assertEquals(3, owner.backup.snapshot().schemaVersion)
        }
        assertEquals(current, pointer.read())
    }
}
