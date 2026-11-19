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
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.auxiliary.AuxiliaryReceiptBackupSnapshot
import ar.com.faro.v5.store.auxiliary.BookmarkBackupSnapshot
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4Result
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4ReceiptBackupTest {
    @TempDir lateinit var root: Path

    private fun fresh(name: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "auxiliary4-backup", 100L),
        )

    private fun file(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()

    private fun open4(
        stage: StagedStorageGeneration,
        pointer: ActiveGenerationPointerPort = FileActiveGenerationPointer(root),
    ) = FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer)

    private fun candidate(key: String) = NoticeCandidate(
        NoticeKey.of("reminder:$key"), SourceRef.of("reminder:rule-$key"),
        OccurrenceRef.of(key), NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
        NoticeSummary.of("Aviso de prueba"), DestinationRef.of("faro:avisos.local"),
    )

    private fun source(): AuxiliaryReceiptBackupSnapshot {
        val stage = fresh("snapshot-source")
        BundledSQLiteDriver().open(file(stage)).use { db ->
            db.execSQL(
                "INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision) " +
                    "VALUES(1,'gen','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',1)"
            )
            db.execSQL("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES('f1','Ideas',1)")
        }
        return open4(stage).use { owner ->
            val notice = owner.routeReminderLocalOnly(candidate("source")) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt("a-seen", notice.noticeId, 1, NoticeAttention.SEEN) { true }
            owner.mutateReminderNoticeAttentionWithReceipt("b-dismiss", notice.noticeId, 2, NoticeAttention.DISMISSED) { true }
            owner.snapshotStagedReceiptBackup().also {
                assertEquals(2, it.receipts.size)
                assertNotEquals(BookmarkBackupSnapshot.EMPTY, it.base.bookmarks)
            }
        }
    }

    private fun assertFresh(owner: AuxiliaryDeliveryStore) {
        val snapshot = owner.snapshotStagedReceiptBackup()
        assertTrue(snapshot.base.notices.isEmpty())
        assertEquals(BookmarkBackupSnapshot.EMPTY, snapshot.base.bookmarks)
        assertTrue(snapshot.receipts.isEmpty())
        assertEquals(0L, owner.maintenance.inspect().deliveryIntentCount)
    }

    @Test fun snapshotRestoresAllDurableRowsAndReplaysReceiptAfterReopening() {
        val snapshot = source()
        val target = fresh("target-backup")
        open4(target).use { owner ->
            owner.restoreOrVerifyStagedReceiptBackup(snapshot)
            assertEquals(snapshot, owner.snapshotStagedReceiptBackup())
        }
        open4(target).use { owner ->
            owner.restoreOrVerifyStagedReceiptBackup(snapshot)
            val id = snapshot.base.notices.single().noticeId
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, true),
                owner.mutateReminderNoticeAttentionWithReceipt("a-seen", id, 1L, NoticeAttention.SEEN) { true },
            )
            assertEquals(snapshot, owner.snapshotStagedReceiptBackup())
            assertEquals(0L, owner.maintenance.inspect().deliveryIntentCount)
            assertThrows(IllegalStateException::class.java) { owner.backup.snapshot() }
        }
    }

    @Test fun failedSecondReceiptRollsBackNoticeBookmarksAndFirstReceipt() {
        val snapshot = source()
        val target = fresh("target-rollback")
        open4(target).use {}
        BundledSQLiteDriver().open(file(target)).use { db ->
            db.execSQL(
                "CREATE TRIGGER fail_second_receipt BEFORE INSERT ON notice_attention_receipt " +
                    "WHEN NEW.request_key='b-dismiss' BEGIN SELECT RAISE(ABORT, 'injected'); END"
            )
        }
        open4(target).use { owner ->
            assertThrows(Exception::class.java) { owner.restoreOrVerifyStagedReceiptBackup(snapshot) }
            assertFresh(owner)
        }
        BundledSQLiteDriver().open(file(target)).use { db -> db.execSQL("DROP TRIGGER fail_second_receipt") }
        open4(target).use { owner ->
            owner.restoreOrVerifyStagedReceiptBackup(snapshot)
            assertEquals(snapshot, owner.snapshotStagedReceiptBackup())
        }
    }

    @Test fun canCompleteTheReceiptLedgerForAnExactlyRestoredLegacyBase() {
        val snapshot = source()
        val target = fresh("target-exact-base")
        AuxiliaryDeliveryStore(file(target)).use { owner ->
            assertEquals(snapshot.base, owner.backup.restoreOrVerify(snapshot.base))
            assertThrows(IllegalStateException::class.java) {
                owner.restoreOrVerifyStagedReceiptBackup(snapshot)
            }
        }
        open4(target).use { owner ->
            assertEquals(snapshot.base, owner.snapshotStagedReceiptBackup().base)
            assertTrue(owner.snapshotStagedReceiptBackup().receipts.isEmpty())
            owner.restoreOrVerifyStagedReceiptBackup(snapshot)
            assertEquals(snapshot, owner.snapshotStagedReceiptBackup())
        }
    }

    @Test fun aDifferentDestinationRemainsIntact() {
        val snapshot = source()
        val target = fresh("target-different")
        open4(target).use { owner ->
            val before = owner.routeReminderLocalOnly(candidate("unrelated")) { true }.notice
            assertThrows(IllegalStateException::class.java) { owner.restoreOrVerifyStagedReceiptBackup(snapshot) }
            assertEquals(listOf(before), owner.readNotices())
            assertTrue(owner.snapshotStagedReceiptBackup().receipts.isEmpty())
        }
    }

    @Test fun aChangedPointerImmediatelyBeforeCommitRollsBackAllRows() {
        val snapshot = source()
        val target = fresh("target-aba")
        val durable = FileActiveGenerationPointer(root)
        val origin = StorageGenerationId("origin-with-aba")
        requireNotNull(durable.compareAndSet(null, origin))
        val pinned = requireNotNull(durable.read())
        val pointer = object : ActiveGenerationPointerPort {
            var allowedReads = Int.MAX_VALUE
            override fun read(): ActiveGenerationPointer =
                if (allowedReads-- > 0) pinned else pinned.copy(sequence = pinned.sequence + 2)

            override fun compareAndSet(
                expectedGenerationId: StorageGenerationId?,
                targetGenerationId: StorageGenerationId,
            ): ActiveGenerationPointer? = null
        }
        open4(target, pointer).use { owner ->
            pointer.allowedReads = 1
            assertThrows(IllegalStateException::class.java) { owner.restoreOrVerifyStagedReceiptBackup(snapshot) }
        }
        open4(target, durable).use { owner -> assertFresh(owner) }
        assertEquals(pinned, durable.read())
    }
}
