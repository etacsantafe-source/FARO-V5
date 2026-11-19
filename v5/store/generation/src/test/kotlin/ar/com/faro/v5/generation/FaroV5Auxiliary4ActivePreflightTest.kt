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
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4Result
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4ActivePreflightTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer get() = FileActiveGenerationPointer(root)

    private fun stage(name: String): StagedStorageGeneration {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("preflight-origin")))
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "auxiliary4-preflight", 100L),
        )
        return FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("preflight-migrate"), staged, origin, "a".repeat(64), 1000L,
        )
    }

    private fun schema4(stage: StagedStorageGeneration) =
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer)

    private fun select(stage: StagedStorageGeneration): ActiveGenerationPointer = checkNotNull(
        pointer.compareAndSet(checkNotNull(pointer.read()).generationId, stage.descriptor.generationId),
    )

    private fun open4(stage: StagedStorageGeneration, active: ActiveGenerationPointer) =
        FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Auxiliary4Dev(
            root, stage, active.sequence, verifier, gate,
        )

    private fun open3(stage: StagedStorageGeneration, active: ActiveGenerationPointer) =
        FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Dev(
            root, stage, active.sequence, verifier, gate,
        )

    private fun storageFingerprint(stage: StagedStorageGeneration) =
        GenerationStoreFiles.WORKSPACE_V2.associateWith { file ->
            MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(stage.directory.resolve(file))).toList()
        }

    private val candidate = NoticeCandidate(
        NoticeKey.of("reminder:preflight-occ"), SourceRef.of("reminder:preflight-rule"),
        OccurrenceRef.of("preflight-occ"), NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
        NoticeSummary.of("Aviso"), DestinationRef.of("faro:avisos.local"),
    )

    @Test fun auxiliary3IsNeverMigratedThroughTheActive4Opener() {
        val stage = stage("active-three")
        val active = select(stage)
        val before = storageFingerprint(stage)
        assertThrows(IllegalStateException::class.java) { open4(stage, active).close() }
        assertEquals(before, storageFingerprint(stage))
        open3(stage, active).use { assertEquals(3, it.auxiliary.maintenance.inspect().schemaVersion) }
        assertEquals(active, pointer.read())
    }

    @Test fun exactActive4UsesOneReceiptOwnerAndReplaysAfterReopen() {
        val stage = stage("active-four")
        val notice = schema4(stage).use { it.routeReminderLocalOnly(candidate) { true }.notice }
        val active = select(stage)
        assertThrows(IllegalStateException::class.java) { open3(stage, active).close() }
        open4(stage, active).use { graph ->
            assertTrue(graph.verifyHealthy())
            assertEquals(4, graph.auxiliary.maintenance.inspect().schemaVersion)
            assertThrows(IllegalStateException::class.java) { graph.auxiliary.snapshotStagedReceiptBackup() }
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, false),
                graph.auxiliary.mutateReminderNoticeAttentionWithReceipt(
                    "active-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
        }
        open4(stage, active).use { graph ->
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, true),
                graph.auxiliary.mutateReminderNoticeAttentionWithReceipt(
                    "active-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(2L, graph.auxiliary.readNotices().single().revision)
        }
    }

    @Test fun active4RequiresTheReceiptTableBeforeOpeningStores() {
        val stage = stage("missing-ledger")
        schema4(stage).close()
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use {
            it.execSQL("DROP TABLE notice_attention_receipt")
        }
        val active = select(stage)
        val before = storageFingerprint(stage)
        assertThrows(IllegalStateException::class.java) { open4(stage, active).close() }
        assertEquals(before, storageFingerprint(stage))
    }

    @Test fun futureSchemaIsRejectedWithoutAnyStoreByteChanging() {
        val stage = stage("future-ledger")
        schema4(stage).close()
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use {
            it.execSQL("PRAGMA user_version=5")
        }
        val active = select(stage)
        val before = storageFingerprint(stage)
        assertThrows(IllegalStateException::class.java) { open4(stage, active).close() }
        assertEquals(before, storageFingerprint(stage))
    }

    @Test fun pointerAbaRevokesExistingOwnerAndRejectsAStaleReopen() {
        val stage = stage("active-aba")
        val notice = schema4(stage).use { it.routeReminderLocalOnly(candidate) { true }.notice }
        val before = select(stage)
        open4(stage, before).use { graph ->
            val other = StorageGenerationId("intermediate")
            checkNotNull(pointer.compareAndSet(before.generationId, other))
            checkNotNull(pointer.compareAndSet(other, before.generationId))
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Denied,
                graph.auxiliary.mutateReminderNoticeAttentionWithReceipt(
                    "stale-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(1L, graph.auxiliary.readNotices().single().revision)
        }
        assertThrows(IllegalStateException::class.java) { open4(stage, before).close() }
    }
}
