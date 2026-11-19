package ar.com.faro.v5.generation

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val stageOwner get() = FaroV5WorkspaceV2StageOwner(root)
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }
    private val candidate = NoticeCandidate(
        NoticeKey.of("reminder:codec-occ"),
        SourceRef.of("reminder:codec-rule"),
        OccurrenceRef.of("codec-occ"),
        NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
        NoticeSummary.of("Aviso de prueba"),
        DestinationRef.of("faro:avisos.local"),
    )

    private fun descriptor(name: String) = StorageGenerationDescriptor(
        StorageGenerationId(name), "auxiliary4-fbs2-codec", 100L,
    )

    private fun snapshot(): FaroV5Auxiliary4Fbs2Snapshot {
        val baseStage = stageOwner.stageFreshPlanning8AndVerify(descriptor("codec-base"))
        val base = FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(baseStage).use { graph ->
            val notice = graph.auxiliary.routeReminderLocalOnly(candidate) { true }.notice
            graph.auxiliary.mutateReminderNoticeAttention(
                notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
            graph.auxiliary.mutateReminderNoticeAttention(
                notice.noticeId, 2L, NoticeAttention.DISMISSED,
            ) { true }
            FaroV5FiveStoreBackupCapture(graph, lease).capture(
                MaintenanceOperationId("codec-capture"), BackupSetId("codec-backup"),
                "5.0-dev", 123L,
            ).copy(finance9 = Finance9SectionSnapshot.empty())
        }
        val stage4 = stageOwner.stageFreshAndVerify(descriptor("codec-receipts"))
        val receipts = FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            stage4, FileActiveGenerationPointer(root),
        ).use { store ->
            val id = store.routeReminderLocalOnly(candidate) { true }.notice.noticeId
            store.mutateReminderNoticeAttentionWithReceipt("a-seen", id, 1L, NoticeAttention.SEEN) { true }
            store.mutateReminderNoticeAttentionWithReceipt("b-dismissed", id, 2L, NoticeAttention.DISMISSED) { true }
            store.readIsolatedStage4NoticeReceipts()
        }
        assertEquals(2, receipts.size)
        return FaroV5Auxiliary4Fbs2Snapshot(base, receipts)
    }

    @Test fun realStagedReceiptsAndAllFiveStoresRoundTripWithoutLosingHistory() {
        val snapshot = snapshot()
        val bytes = FaroV5Auxiliary4Fbs2Codec.encode(snapshot)
        assertEquals(5, ByteBuffer.wrap(bytes).getInt(4))
        val restored = FaroV5Auxiliary4Fbs2Codec.decode(bytes)
        assertEquals(snapshot, restored)
        assertEquals(listOf(2L, 3L), restored.receipts.map { it.resultRevision })
        assertArrayEquals(bytes, FaroV5Auxiliary4Fbs2Codec.encode(restored))
    }

    @Test fun emptyLedgerIsExplicitAndLegacyReadersRejectTheVersion() {
        val snapshot = snapshot().copy(receipts = emptyList())
        val bytes = FaroV5Auxiliary4Fbs2Codec.encode(snapshot)
        val legacy = FaroV5FiveStoreSemanticCodec.encode(snapshot.base)
        assertEquals(snapshot, FaroV5Auxiliary4Fbs2Codec.decode(bytes))
        assertEquals(snapshot.base, FaroV5FiveStoreSemanticCodec.decode(legacy))
        assertThrows(IllegalStateException::class.java) {
            FaroV5FiveStoreSemanticCodec.decode(bytes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5Auxiliary4Fbs2Codec.decode(legacy)
        }
    }

    @Test fun malformedLengthsTruncatedSectionsAndDigestMismatchFailClosed() {
        val bytes = FaroV5Auxiliary4Fbs2Codec.encode(snapshot())
        val invalidLedgerLength = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(12, 0) }
        val corruptedLedger = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val corruptedBase = bytes.copyOf().also { it[80] = (it[80].toInt() xor 1).toByte() }
        listOf(invalidLedgerLength, corruptedLedger, corruptedBase, bytes.copyOf(bytes.size - 1), bytes + 0.toByte())
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) {
                    FaroV5Auxiliary4Fbs2Codec.decode(invalid)
                }
            }
    }

    @Test fun duplicateUnsortedOrphanedAndForgedReceiptsCannotBeEncoded() {
        val snapshot = snapshot()
        val first = snapshot.receipts.first()
        val invalidLedgers = listOf(
            listOf(first, first),
            snapshot.receipts.reversed(),
            listOf(first.copy(noticeId = NoticeId.of("unknown-notice"))),
            listOf(first.copy(requestHash = "0".repeat(64))),
            listOf(first.copy(expectedRevision = 8L, resultRevision = 9L)),
            listOf(first.copy(expectedRevision = Long.MAX_VALUE, resultRevision = Long.MIN_VALUE)),
        )
        invalidLedgers.forEach { ledger ->
            assertThrows(IllegalArgumentException::class.java) {
                FaroV5Auxiliary4Fbs2Codec.encode(snapshot.copy(receipts = ledger))
            }
        }
    }

    @Test fun finance9BaseCannotBeOmitted() {
        val snapshot = snapshot()
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5Auxiliary4Fbs2Codec.encode(snapshot.copy(base = snapshot.base.copy(finance9 = null)))
        }
    }
}
