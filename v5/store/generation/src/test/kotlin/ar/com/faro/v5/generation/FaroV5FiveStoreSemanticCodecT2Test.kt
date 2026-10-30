package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.SemanticBackupSet
import ar.com.faro.v5.maintenance.SemanticBackupSetCodec
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.WorkspaceBackupPlanReview
import ar.com.faro.v5.workspace.WorkspaceBackupReceipt
import ar.com.faro.v5.workspace.WorkspaceBackupSnapshot
import ar.com.faro.v5.workspace.WorkspaceSemanticBackupCodec
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStoreSemanticCodecT2Test {
    @TempDir lateinit var temp: Path

    private fun fourStore(): SemanticBackupSet {
        val root = temp.resolve("four")
        Files.createDirectories(root)
        return RealityStore(root.resolve("reality.db").toString()).use { reality ->
            CommercialEvidenceStore(root.resolve("commercial.db").toString()).use { commercial ->
                WebEvidenceStore(root.resolve("web.db").toString()).use { web ->
                    AuxiliaryDeliveryStore.open(root.resolve("auxiliary.db").toString()).use { auxiliary ->
                        SemanticBackupCaptureCoordinator(
                            quiescence = object : MaterialWriteQuiescencePort {
                                override fun <T> withMaterialWritesQuiesced(
                                    operationId: MaintenanceOperationId,
                                    block: () -> T,
                                ): T = block()
                            },
                            reality = reality.backup,
                            commercialEvidence = commercial.backup,
                            webEvidence = web.backup,
                            auxiliary = auxiliary.backup,
                        ).capture(
                            operationId = MaintenanceOperationId("five-store-sample"),
                            backupSetId = BackupSetId("five-store-source"),
                            sourceGenerationId = StorageGenerationId("source-five-0001"),
                            productVersion = "5-a6-test",
                            createdAtEpochMillis = 12345L,
                        )
                    }
                }
            }
        }
    }

    private fun workspace(withPlan: Boolean = false): WorkspaceBackupSnapshot {
        val id = ConversationId("chat-five")
        val envelope = byteArrayOf(11, 22, 33)
        val digest = MessageDigest.getInstance("SHA-256").digest(envelope)
            .joinToString("") { "%02x".format(it) }
        return WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 1L,
            activeConversationId = id,
            conversations = listOf(ConversationWorkspaceEntry(
                id = id,
                title = "Chat cinco almacenes",
                lastActivityEpochMillis = 100L,
                hasPendingReview = withPlan,
            )),
            receipts = listOf(WorkspaceBackupReceipt("create-five", "a".repeat(64), 1L, true)),
            planReviews = if (withPlan) listOf(
                WorkspaceBackupPlanReview(
                    reviewId = "review-five",
                    conversationId = id,
                    planFingerprint = PlanFingerprint("b".repeat(64)),
                    reviewText = "Confirmar acción",
                    revision = 1L,
                    state = ConversationPlanReviewState.PENDING,
                    envelopeBase64 = Base64.getEncoder().encodeToString(envelope),
                    envelopeSha256 = digest,
                )
            ) else emptyList(),
        )
    }

    @Test fun exactFiveStoreSemanticRoundTripDoesNotChangeLegacyFbs1() {
        val source = FaroV5FiveStoreSemanticSet(fourStore(), workspace(withPlan = true))
        val oldBytes = SemanticBackupSetCodec.encode(source.fourStore)
        val encoded = FaroV5FiveStoreSemanticCodec.encode(source)
        assertEquals(source, FaroV5FiveStoreSemanticCodec.decode(encoded))
        assertArrayEquals(encoded, FaroV5FiveStoreSemanticCodec.encode(source))
        assertArrayEquals(oldBytes, SemanticBackupSetCodec.encode(source.fourStore))
        assertTrue(encoded.size < FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES)
    }

    @Test fun emptyReviewFiveStoreRoundTripsAndPreservesRevisionAndReceipts() {
        val source = FaroV5FiveStoreSemanticSet(fourStore(), workspace())
        val copy = FaroV5FiveStoreSemanticCodec.decode(FaroV5FiveStoreSemanticCodec.encode(source))
        assertEquals(source.workspace.revision, copy.workspace.revision)
        assertEquals(source.workspace.receipts, copy.workspace.receipts)
        assertTrue(copy.workspace.planReviews.isEmpty())
        assertEquals(source.fourStore.manifest, copy.fourStore.manifest)
    }

    @Test fun v1AndWorkspaceOnlyPlaintextsMustNeverBeDecodedAsFiveStore() {
        val source = FaroV5FiveStoreSemanticSet(fourStore(), workspace())
        val v1 = SemanticBackupSetCodec.encode(source.fourStore)
        val wsb2 = WorkspaceSemanticBackupCodec.encode(source.workspace)
        assertThrows(IllegalStateException::class.java) { FaroV5FiveStoreSemanticCodec.decode(v1) }
        assertThrows(IllegalStateException::class.java) { FaroV5FiveStoreSemanticCodec.decode(wsb2) }
    }

    @Test fun oneByteMutationInEitherSectionRejectsBeforeAnyRestore() {
        val original = FaroV5FiveStoreSemanticCodec.encode(
            FaroV5FiveStoreSemanticSet(fourStore(), workspace(withPlan = true)),
        )
        val legacyTamper = original.copyOf().apply { this[80] = (this[80].toInt() xor 1).toByte() }
        val workspaceTamper = original.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5FiveStoreSemanticCodec.decode(legacyTamper)
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5FiveStoreSemanticCodec.decode(workspaceTamper)
        }
    }

    @Test fun hostileLengthsAndExtraOrMissingBytesFailClosed() {
        val original = FaroV5FiveStoreSemanticCodec.encode(
            FaroV5FiveStoreSemanticSet(fourStore(), workspace()),
        )
        val oversizedFirst = original.copyOf().apply {
            ByteBuffer.wrap(this).putInt(8, Int.MAX_VALUE)
        }
        val negativeSecond = original.copyOf().apply {
            ByteBuffer.wrap(this).putInt(12, -1)
        }
        val wrongMagic = original.copyOf().apply { this[0] = 0 }
        val wrongVersion = original.copyOf().apply { ByteBuffer.wrap(this).putInt(4, 2) }
        for (corrupt in listOf(
            oversizedFirst, negativeSecond, wrongMagic, wrongVersion,
            original.copyOf(original.size - 1), original + byteArrayOf(0),
        )) {
            assertThrows(Exception::class.java) {
                FaroV5FiveStoreSemanticCodec.decode(corrupt)
            }
        }
    }

    @Test fun explicitInnerEncodingBudgetsRejectInsteadOfBuildingOversizedTemporaryBytes() {
        val source = fourStore()
        val ws = workspace(withPlan = true)
        assertThrows(IllegalStateException::class.java) {
            SemanticBackupSetCodec.encodeWithin(source, 1)
        }
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.encodeWithin(ws, 1)
        }
    }
}
