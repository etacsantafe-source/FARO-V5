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
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11V8BaseReplayTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "w05-a1b3b0-dev-only-passphrase".toCharArray()
    private val restoreOp = MaintenanceOperationId("w05-v8-base-replay")

    private data class Fixture(
        val origin: ar.com.faro.v5.maintenance.ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val archive: ByteArray,
    )

    private fun stage(id: String): StagedStorageGeneration {
        val active = checkNotNull(pointer.read())
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "v8-replay", 100L),
        )
        val migrated = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("finance-stage-" + id),
            prepared, active, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            migrated, pointer,
        ).close()
        return migrated
    }

    private fun fixture(): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("w05-v8-origin")))
        val source = stage("w05-v8-replay-source")
        val target = stage("w05-v8-replay-target")
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES" +
                "('2026-11','Comida','ARS',300000,1,1000,'HUMAN')")
            db.execSQL("INSERT INTO matter_record(matter_id,title,detail,status," +
                "due_at_epoch_millis,updated_at_epoch_millis) " +
                "VALUES('matter-v8','Revisión','Detalle','OPEN',NULL,1000)")
            db.execSQL("UPDATE matter_state SET revision=1 WHERE singleton_id=1")
        }
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).use { owner ->
            val notice = owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:v8-replay-occur"),
                SourceRef.of("reminder:v8-replay-rule"),
                OccurrenceRef.of("v8-replay-occur"),
                NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso local V8"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt(
                "v8-replay-tap", notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
        }
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(source, origin).use {
            val ws = checkNotNull(it.workspace)
            ws.backup.restoreOrVerify(ws.backup.snapshot().copy(revision = 6L))
        }
        val previous = FaroV5Auxiliary4ArchiveCoordinator(root, gate).captureAuthenticated(
            MaintenanceOperationId("capture-base-for-v8"), BackupSetId("backup-v8-base"),
            source, origin, "5.0-dev", 3000L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(previous, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Source fixture invalid: " + opened.reason)
        }
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, Household10BackupSection(0L, emptyList())),
            Household11ReceiptBackupSection(2L, listOf(
                Household11ReceiptBackupRow("r-1", "intent-1", "purchase-1", "yerba",
                    7000, "UNIT", 0L, 3000L, 1L),
                Household11ReceiptBackupRow("r-2", "intent-2", "purchase-1", "yerba",
                    3000, "UNIT", 0L, 3000L, 2L),
            )),
        )
        return Fixture(origin, source, target, snapshot,
            crypto.sealWithHouseholdReceipts11(snapshot, passphrase))
    }

    private fun replay(
        afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
        afterFinance: () -> Unit = {},
        afterAdmission: () -> Unit = {},
        host: () -> Unit = {},
    ) = FaroV5Household11V8BaseReplay(
        root, gate, host, afterStore, afterFinance, afterAdmission,
    )

    private fun restore(f: Fixture, archive: ByteArray = f.archive, secret: CharArray = passphrase) =
        replay().restoreAuthenticatedBaseToNine(
            restoreOp, f.target, f.origin, archive, secret,
        )

    private fun verified(f: Fixture) =
        FiveStoreStagedRestoreResult.StagedVerified(f.target.descriptor.generationId.value)

    private fun blocked(reason: FiveStoreStagedRestoreBlock) =
        FiveStoreStagedRestoreResult.Blocked(reason)

    private fun pinned(f: Fixture, bytes: ByteArray = f.archive) =
        FileRestoreArchiveIdentity(root).requireExistingPinnedArchive(
            restoreOp, f.target.descriptor.generationId, bytes,
        )

    @Test fun restoresFiveOwnerFinance9BaseAndPreservesOriginAndNoSql11() {
        val f = fixture()
        val sourceBefore = GenerationStoreFiles.WORKSPACE_V2.map {
            Files.readAllBytes(f.source.directory.resolve(it))
        }
        assertEquals(verified(f), restore(f))
        assertEquals(f.origin, pointer.read())
        pinned(f)
        BundledSQLiteDriver().open(f.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.prepare("PRAGMA user_version").use { stmt ->
                check(stmt.step())
                assertEquals(9L, stmt.getLong(0))
            }
            it.prepare("SELECT limit_minor FROM finance_budget_limit").use { stmt ->
                check(stmt.step())
                assertEquals(300000L, stmt.getLong(0))
            }
            it.prepare("SELECT count(*) FROM matter_record").use { stmt ->
                check(stmt.step())
                assertEquals(1L, stmt.getLong(0))
            }
        }
        sourceBefore.zip(GenerationStoreFiles.WORKSPACE_V2).forEach { (bytes, filename) ->
            assertArrayEquals(bytes, Files.readAllBytes(f.source.directory.resolve(filename)))
        }
    }

    @Test fun exactSameCiphertextOperationAndTargetCanRetryIdempotently() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        assertEquals(verified(f), restore(f))
        pinned(f)
        assertEquals(f.origin, pointer.read())
    }

    @Test fun substitutionWithDifferentCiphertextOfSamePlaintextIsRejected() {
        val f = fixture()
        assertEquals(verified(f), restore(f))
        val secondNonce = crypto.sealWithHouseholdReceipts11(f.snapshot, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
            restore(f, secondNonce))
        pinned(f)
        assertEquals(f.origin, pointer.read())
    }

    @Test fun retryAfterFaultDuringAuxiliaryReplayFinishesUnderSameIdentity() {
        val f = fixture()
        val interrupted = replay(afterStore = {
            if (it == FiveStoreStagedRestoreSection.AUXILIARY) error("forced crash after auxiliary")
        }).restoreAuthenticatedBaseToNine(restoreOp, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        pinned(f)
        assertEquals(verified(f), restore(f))
        assertEquals(f.origin, pointer.read())
    }

    @Test fun retryAfterFinance9CommitDoesNotDuplicateOrOverwrite() {
        val f = fixture()
        val interrupted = replay(afterFinance = { error("forced crash after Finance9") })
            .restoreAuthenticatedBaseToNine(restoreOp, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED), interrupted)
        pinned(f)
        assertEquals(verified(f), restore(f))
        assertEquals(f.origin, pointer.read())
    }

    @Test fun wrongPassphraseTamperAndLegacyFbs2AreDeniedBeforePin() {
        val f = fixture()
        val ciphertextTamper = f.archive.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val oldV5 = crypto.sealWithReceipts(f.snapshot.base.base, passphrase)
        val oldV6 = crypto.sealWithHousehold(f.snapshot.base, passphrase)
        for ((bytes, key) in listOf(
            f.archive to "wrong-dev-passphrase".toCharArray(),
            ciphertextTamper to passphrase,
            oldV5 to passphrase,
            oldV6 to passphrase,
        )) {
            val result = restore(f, bytes, key) as FiveStoreStagedRestoreResult.Blocked
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, result.reason)
        }
        assertThrows(IllegalStateException::class.java) { pinned(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun sourceTargetConflictAndUnsafeFilesDoNotInstallPin() {
        val f = fixture()
        assertEquals(blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT),
            replay().restoreAuthenticatedBaseToNine(restoreOp, f.source, f.origin, f.archive, passphrase))
        val reality = f.target.directory.resolve(GenerationStoreFiles.REALITY)
        val link = Files.createLink(root.resolve("v8-replay-hardlink.db"), reality)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED), restore(f))
        Files.delete(link)
        val sidecar = reality.resolveSibling(reality.fileName.toString() + "-wal")
        Files.writeString(sidecar, "unsafe sidecar")
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED), restore(f))
        Files.delete(sidecar)
        assertThrows(IllegalStateException::class.java) { pinned(f) }
    }

    @Test fun hostRevocationAndPointerAbaAfterAdmissionAreRejectedBeforePin() {
        val f = fixture()
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED),
            replay(host = { error("closed DEV lease revoked") })
                .restoreAuthenticatedBaseToNine(restoreOp, f.target, f.origin, f.archive, passphrase))
        val result = replay(afterAdmission = {
            val intermediate = StorageGenerationId("w05-aba-intermediate")
            checkNotNull(pointer.compareAndSet(f.origin.generationId, intermediate))
            checkNotNull(pointer.compareAndSet(intermediate, f.origin.generationId))
        }).restoreAuthenticatedBaseToNine(restoreOp, f.target, f.origin, f.archive, passphrase)
        assertEquals(blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED), result)
        assertThrows(IllegalStateException::class.java) { pinned(f) }
    }
}
