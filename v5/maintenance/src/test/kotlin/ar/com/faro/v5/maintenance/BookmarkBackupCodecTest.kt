package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.store.auxiliary.*
import ar.com.faro.v5.store.reality.*
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BookmarkBackupCodecTest {
    private fun snapshot() = BookmarkBackupSnapshot(
        BookmarkBackupScope("gen-a", "a".repeat(64), 1),
        listOf(BookmarkFolder(BookmarkFolderId("f1"), "Ideas")),
        listOf(BookmarkBackupItem(BookmarkId("b1"), "gen-a", ConversationId("chat"),
            ConversationMessageId("msg"), BookmarkFolderId("f1"), true, BookmarkLifecycle.REMOVED, 2)),
        listOf(BookmarkBackupReceipt("key", "b".repeat(64), 1)),
    )
    private fun capture(bookmarks: BookmarkBackupSnapshot?, timed: Boolean = false): SemanticBackupSet {
        val r = RealityBackupSnapshot(8, 0, 0, RealitySemanticMigrationData(null, emptyList(), emptyList()),
            if (timed) listOf(RealityCommittedReceiptBackup("r", "t", "p", 123)) else emptyList())
        val c = CommercialEvidenceBackupSnapshot(1, emptyList())
        val w = WebEvidenceBackupSnapshot(2, emptyList(), emptyList())
        val a = AuxiliaryBackupSnapshot(3, emptyList(), bookmarks)
        return SemanticBackupCaptureCoordinator(
            object : MaterialWriteQuiescencePort {
                override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
            },
            object : RealityBackupPort {
                override fun snapshot() = r
                override fun restoreOrVerify(snapshot: RealityBackupSnapshot) = error("read only")
            },
            object : CommercialEvidenceBackupPort {
                override fun snapshot() = c
                override fun restoreOrVerify(snapshot: CommercialEvidenceBackupSnapshot) = error("read only")
            },
            object : WebEvidenceBackupPort {
                override fun snapshot() = w
                override fun restoreOrVerify(snapshot: WebEvidenceBackupSnapshot) = error("read only")
            },
            object : AuxiliaryBackupPort {
                override fun snapshot() = a
                override fun restoreOrVerify(snapshot: AuxiliaryBackupSnapshot) = error("read only")
            },
        ).capture(MaintenanceOperationId("op"), BackupSetId("set"), StorageGenerationId("gen-a"), "5-test", 100)
    }

    @Test fun versionThreeRoundtripRetainsTombstonesReceiptsAndTimedReality() {
        val set = capture(snapshot(), timed = true)
        val bytes = SemanticBackupSetCodec.encode(set)
        assertEquals(3, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(set, SemanticBackupSetCodec.decode(bytes))
        assertEquals(4L, set.manifest.sections.single { it.sectionId == "auxiliary" }.recordCount)
    }

    @Test fun manifestDetectsEveryBookmarkFamilyIncludingReceipts() {
        val set = capture(snapshot())
        val b = snapshot()
        val changes = listOf(
            b.copy(scope = b.scope!!.copy(authorityDigest = "c".repeat(64))),
            b.copy(folders = listOf(b.folders.single().copy(name = "Changed"))),
            b.copy(items = listOf(b.items.single().copy(favorite = false))),
            b.copy(receipts = listOf(b.receipts.single().copy(requestHash = "d".repeat(64)))),
        )
        changes.forEach {
            val forged = set.copy(auxiliary = set.auxiliary.copy(bookmarks = it))
            assertFalse(SemanticBackupSetIntegrity.matches(forged))
            assertThrows(IllegalArgumentException::class.java) { SemanticBackupSetCodec.encode(forged) }
        }
    }

    @Test fun legacyVersionsRemainDistinctFromVerifiedEmptySnapshot() {
        for (timed in listOf(false, true)) {
            val legacy = capture(null, timed)
            val bytes = SemanticBackupSetCodec.encode(legacy)
            assertEquals(if (timed) 2 else 1, ByteBuffer.wrap(bytes).getInt(4))
            assertEquals(legacy, SemanticBackupSetCodec.decode(bytes))
            assertNull(SemanticBackupSetCodec.decode(bytes).auxiliary.bookmarks)
            assertArrayEquals(bytes, SemanticBackupSetCodec.encode(SemanticBackupSetCodec.decode(bytes)))
        }
        val empty = capture(BookmarkBackupSnapshot.EMPTY)
        assertEquals(BookmarkBackupSnapshot.EMPTY, SemanticBackupSetCodec.decode(SemanticBackupSetCodec.encode(empty)).auxiliary.bookmarks)
    }

    @Test fun truncatedTrailingAndUnsupportedVersionAreRejected() {
        val bytes = SemanticBackupSetCodec.encode(capture(snapshot()))
        assertThrows(Exception::class.java) { SemanticBackupSetCodec.decode(bytes.copyOf(bytes.size - 1)) }
        assertThrows(Exception::class.java) { SemanticBackupSetCodec.decode(bytes + byteArrayOf(1)) }
        val future = bytes.copyOf()
        ByteBuffer.wrap(future).putInt(4, 99)
        assertThrows(Exception::class.java) { SemanticBackupSetCodec.decode(future) }
    }
}
