package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Linux SQLite isolated STAGED-only. Never activates Auxiliary4. */
class S4W03NoticeReceiptStage4Test {
    @TempDir lateinit var root: Path

    private fun fresh(name: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "aux-notice-stage4", 1L),
        )

    private fun file(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()

    private fun candidate() = NoticeCandidate(
        NoticeKey.of("reminder:stage4-occ"),
        SourceRef.of("reminder:stage4-rule"),
        OccurrenceRef.of("stage4-occ"),
        NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
        NoticeSummary.of("Aviso de vencimiento"),
        DestinationRef.of("faro:avisos.local"),
    )

    private fun seed(stage: StagedStorageGeneration): NoticeSnapshot =
        AuxiliaryDeliveryStore(file(stage)).use {
            it.routeReminderLocalOnly(candidate()) { true }.notice
        }

    private fun stage4(stage: StagedStorageGeneration) =
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            stage, FileActiveGenerationPointer(root),
        )

    @Test fun sealedV3MigratesToV4WithoutDroppingNoticeOrGuardadosAndBlocksIncompleteBackup() {
        val stage = fresh("stage-preserves")
        val before = seed(stage)
        AuxiliaryDeliveryStore(file(stage)).use { s ->
            assertEquals(3, s.backup.snapshot().schemaVersion)
        }
        BundledSQLiteDriver().open(file(stage)).use { db ->
            db.execSQL(
                "INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision) " +
                    "VALUES(1,'gen', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 1)"
            )
            db.execSQL("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES('f1','Ideas',1)")
            db.execSQL(
                "INSERT INTO bookmark_receipt(idempotency_key,request_hash,result_revision) " +
                    "VALUES('book-key','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',1)"
            )
        }
        stage4(stage).use { owner ->
            assertEquals(4, owner.maintenance.inspect().schemaVersion)
            assertEquals(listOf(before), owner.readNotices())
            assertEquals(0, owner.readIsolatedStage4NoticeReceipts().size)
            assertThrows(IllegalStateException::class.java) {
                owner.backup.snapshot()
            }
            assertThrows(IllegalStateException::class.java) {
                owner.portability.snapshot()
            }
            assertTrue(owner.maintenance.inspect().integrityHealthy)
            assertTrue(owner.maintenance.inspect().foreignKeysHealthy)
        }
        BundledSQLiteDriver().open(file(stage)).use { db ->
            fun count(table: String): Long =
                db.prepare("SELECT COUNT(*) FROM $table").use { st ->
                    assertTrue(st.step())
                    st.getLong(0)
                }
            assertEquals(1L, count("delivery_notice"))
            assertEquals(1L, count("bookmark_folder"))
            assertEquals(1L, count("bookmark_receipt"))
            assertEquals(0L, count("notice_attention_receipt"))
        }
    }

    @Test fun requestKeyReplaySurvivesReopenAndNeverReplaysTheSqlMutation() {
        val stage = fresh("stage-replay")
        val notice = seed(stage)
        stage4(stage).use { store ->
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, false),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "click-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(2L, store.readNotices().single().revision)
            assertEquals(1, store.readIsolatedStage4NoticeReceipts().size)
        }
        stage4(stage).use { store ->
            val before = store.readNotices().single()
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, true),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "click-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.IdentityConflict,
                store.mutateReminderNoticeAttentionWithReceipt(
                    "click-seen", notice.noticeId, 1L, NoticeAttention.DISMISSED,
                ) { true },
            )
            assertEquals(before, store.readNotices().single())
            assertEquals(1, store.readIsolatedStage4NoticeReceipts().size)
            assertEquals(2L, store.readIsolatedStage4NoticeReceipts().single().resultRevision)
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(3L, false),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "click-dismiss", notice.noticeId, 2L, NoticeAttention.DISMISSED,
                ) { true },
            )
            // A historical replay returns revision 2, not the CURRENT dismissed state.
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, true),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "click-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(3L, store.readNotices().single().revision)
            assertEquals(2, store.readIsolatedStage4NoticeReceipts().size)
        }
    }

    @Test fun failedReceiptInsertRollsBackNoticeCasAndKeepsStageHealthy() {
        val stage = fresh("stage-rollback")
        val n = seed(stage)
        stage4(stage).use {}
        BundledSQLiteDriver().open(file(stage)).use { db ->
            db.execSQL(
                "CREATE TRIGGER fail_notice_receipt BEFORE INSERT ON notice_attention_receipt " +
                    "BEGIN SELECT RAISE(ABORT, 'injected receipt error'); END"
            )
        }
        stage4(stage).use { owner ->
            assertThrows(Exception::class.java) {
                owner.mutateReminderNoticeAttentionWithReceipt(
                    "fail-key", n.noticeId, 1L, NoticeAttention.SEEN,
                ) { true }
            }
            assertEquals(n, owner.readNotices().single())
            assertTrue(owner.readIsolatedStage4NoticeReceipts().isEmpty())
            assertTrue(owner.maintenance.inspect().foreignKeysHealthy)
            assertTrue(owner.maintenance.inspect().integrityHealthy)
        }
    }

    @Test fun deniedStaleTerminalAndOtherDomainAreNotReceipted() {
        val stage = fresh("stage-negative")
        val n = seed(stage)
        stage4(stage).use { store ->
            assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied,
                store.mutateReminderNoticeAttentionWithReceipt(
                    "denied", n.noticeId, 1L, NoticeAttention.SEEN,
                ) { false })
            assertEquals(S4W03NoticeAttentionReceiptV4Result.Stale(1L),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "stale", n.noticeId, 99L, NoticeAttention.SEEN,
                ) { true })
            assertEquals(S4W03NoticeAttentionReceiptV4Result.NotFound,
                store.mutateReminderNoticeAttentionWithReceipt(
                    "missing", NoticeId.of("unknown"), 1L, NoticeAttention.SEEN,
                ) { true })
            assertTrue(store.readIsolatedStage4NoticeReceipts().isEmpty())
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, false),
                store.mutateReminderNoticeAttentionWithReceipt(
                    "dismiss", n.noticeId, 1L, NoticeAttention.DISMISSED,
                ) { true },
            )
            assertEquals(S4W03NoticeAttentionReceiptV4Result.Terminal,
                store.mutateReminderNoticeAttentionWithReceipt(
                    "resurrect", n.noticeId, 2L, NoticeAttention.SEEN,
                ) { true })
            assertEquals(1, store.readIsolatedStage4NoticeReceipts().size)
            assertEquals(NoticeAttention.DISMISSED, store.readNotices().single().attention)
        }
    }

    @Test fun activeGenerationAndPointerAbaAreRejectedWithoutAnyMigration() {
        val stage = fresh("stage-active-denied")
        val ptr = FileActiveGenerationPointer(root)
        val file = file(stage)
        assertEquals(3, AuxiliaryDeliveryStore(file).use { it.backup.snapshot().schemaVersion })
        requireNotNull(ptr.compareAndSet(null, stage.descriptor.generationId))
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, ptr).close()
        }
        assertEquals(3, AuxiliaryDeliveryStore(file).use { it.backup.snapshot().schemaVersion })
        val other = freshWithoutRealPointer("stage-aba")
        val changing = object : ActiveGenerationPointerPort {
            var reads = 0
            override fun read(): ActiveGenerationPointer? {
                reads++
                return if (reads == 1) null
                else ActiveGenerationPointer(2L, StorageGenerationId("different"))
            }
            override fun compareAndSet(
                expectedGenerationId: StorageGenerationId?,
                targetGenerationId: StorageGenerationId,
            ): ActiveGenerationPointer? = null
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
                other, changing,
            ).close()
        }
        assertEquals(3, AuxiliaryDeliveryStore(file(other)).use { it.backup.snapshot().schemaVersion })
    }

    // Create a separate root so the first test's ACTIVE pointer cannot affect stage creation.
    private fun freshWithoutRealPointer(id: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root.resolve("other-root")).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "aux-notice-stage4", 1L),
        )

    @Test fun corruptOrFutureStageIsNotSilentlyUpgradedOrRepaired() {
        val stage = fresh("stage-corrupt")
        BundledSQLiteDriver().open(file(stage)).use {
            it.execSQL("PRAGMA user_version=5")
        }
        assertThrows(IllegalStateException::class.java) { stage4(stage).close() }
        BundledSQLiteDriver().open(file(stage)).use {
            it.execSQL("PRAGMA user_version=3")
        }
        stage4(stage).use { assertEquals(4, it.maintenance.inspect().schemaVersion) }
        BundledSQLiteDriver().open(file(stage)).use {
            it.execSQL("DROP TABLE notice_attention_receipt")
        }
        assertThrows(IllegalStateException::class.java) { stage4(stage).close() }
    }
}
