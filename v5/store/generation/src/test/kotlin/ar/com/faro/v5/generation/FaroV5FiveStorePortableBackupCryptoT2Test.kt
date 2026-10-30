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
import ar.com.faro.v5.maintenance.PortableBackupArchiveCrypto
import ar.com.faro.v5.maintenance.PortableBackupOpenFailure
import ar.com.faro.v5.maintenance.PortableBackupOpenResult
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.WorkspaceBackupPlanReview
import ar.com.faro.v5.workspace.WorkspaceBackupSnapshot
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStorePortableBackupCryptoT2Test {
    @TempDir lateinit var temp: Path
    private val passphrase = "correct horse battery staple".toCharArray()

    private fun sample(): FaroV5FiveStoreSemanticSet {
        val root = temp.resolve("source")
        Files.createDirectories(root)
        val four = RealityStore(root.resolve("reality.db").toString()).use { reality ->
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
                            operationId = MaintenanceOperationId("fbs2-aead-capture"),
                            backupSetId = BackupSetId("fbs2-aead-backup"),
                            sourceGenerationId = StorageGenerationId("gen-five-aead-1"),
                            productVersion = "5-aead-native-test",
                            createdAtEpochMillis = 12345L,
                        )
                    }
                }
            }
        }
        val chat = ConversationId("confidential-chat")
        val envelope = "CONFIDENTIAL_PENDING_PLAN".toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(envelope)
            .joinToString("") { "%02x".format(it) }
        val workspace = WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 1L,
            activeConversationId = chat,
            conversations = listOf(
                ConversationWorkspaceEntry(
                    id = chat,
                    title = "CONFIDENTIAL_CHAT",
                    lastActivityEpochMillis = 1234L,
                    hasPendingReview = true,
                )
            ),
            receipts = emptyList(),
            planReviews = listOf(
                WorkspaceBackupPlanReview(
                    reviewId = "confidential-review",
                    conversationId = chat,
                    planFingerprint = PlanFingerprint("b".repeat(64)),
                    reviewText = "CONFIDENTIAL_REVIEW",
                    revision = 1L,
                    state = ConversationPlanReviewState.PENDING,
                    envelopeBase64 = Base64.getEncoder().encodeToString(envelope),
                    envelopeSha256 = hash,
                )
            ),
        )
        return FaroV5FiveStoreSemanticSet(four, workspace)
    }

    @Test fun completeFiveStoreRoundTripAuthenticatesAndHidesPreparedPlan() {
        val expected = sample()
        val archive = FaroV5FiveStorePortableBackupCrypto().seal(expected, passphrase)
        assertTrue(archive.size <= FaroV5FiveStorePortableBackupCrypto.MAX_ARCHIVE_BYTES)
        val raw = String(archive, StandardCharsets.ISO_8859_1)
        assertFalse(raw.contains("CONFIDENTIAL_CHAT"))
        assertFalse(raw.contains("CONFIDENTIAL_REVIEW"))
        assertFalse(raw.contains("CONFIDENTIAL_PENDING_PLAN"))
        val opened = assertInstanceOf(
            FiveStorePortableBackupOpenResult.Opened::class.java,
            FaroV5FiveStorePortableBackupCrypto().open(archive, passphrase),
        )
        assertEquals(expected, opened.backupSet)
        assertEquals("FiveStorePortableBackupOpenResult.Opened", opened.toString())
    }

    @Test fun randomizedArchiveSaltAndNonceNeverProduceDeterministicCiphertext() {
        val set = sample()
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        val first = crypto.seal(set, passphrase)
        val second = crypto.seal(set, passphrase)
        assertFalse(first.contentEquals(second))
        assertEquals(first.size, second.size)
    }

    @Test fun wrongPassphraseAndAuthenticatedTamperRejectWithoutAnyPayload() {
        val archive = FaroV5FiveStorePortableBackupCrypto().seal(sample(), passphrase)
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        val wrong = crypto.open(archive, "wrong horse battery staple".toCharArray())
        assertEquals(
            FiveStorePortableBackupOpenResult.Rejected(
                FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
            ), wrong
        )
        val cipherTampered = archive.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertEquals(
            FiveStorePortableBackupOpenResult.Rejected(
                FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
            ), crypto.open(cipherTampered, passphrase)
        )
        val saltTampered = archive.copyOf().apply {
            this[36] = (this[36].toInt() xor 1).toByte()
        }
        assertEquals(
            FiveStorePortableBackupOpenResult.Rejected(
                FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
            ), crypto.open(saltTampered, passphrase)
        )
    }

    @Test fun headerVersionKdfAndSizeGuardsRejectBeforeUntrustedKdfWork() {
        val archive = FaroV5FiveStorePortableBackupCrypto().seal(sample(), passphrase)
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        val changes = listOf(
            archive.copyOf().apply { this[0] = 0 },
            archive.copyOf().apply { ByteBuffer.wrap(this).putInt(8, 1) },
            archive.copyOf().apply { ByteBuffer.wrap(this).putInt(12, 2) },
            archive.copyOf().apply { ByteBuffer.wrap(this).putInt(20, 600_001) },
            archive.copyOf().apply { ByteBuffer.wrap(this).putInt(32, Int.MAX_VALUE) },
            archive.copyOf(archive.size - 1),
            archive + byteArrayOf(0),
            ByteArray(FaroV5FiveStorePortableBackupCrypto.MAX_ARCHIVE_BYTES + 1),
        )
        changes.forEach { corrupted ->
            assertEquals(
                FiveStorePortableBackupOpenResult.Rejected(
                    FiveStorePortableBackupOpenFailure.INVALID_ENVELOPE,
                ), crypto.open(corrupted, passphrase)
            )
        }
    }

    @Test fun oldFaroBak1CannotBeConfusedWithNewFaroBak2InEitherDirection() {
        val set = sample()
        val v1 = PortableBackupArchiveCrypto().seal(set.fourStore, passphrase)
        val v2 = FaroV5FiveStorePortableBackupCrypto().seal(set, passphrase)
        assertEquals(
            FiveStorePortableBackupOpenResult.Rejected(
                FiveStorePortableBackupOpenFailure.INVALID_ENVELOPE
            ),
            FaroV5FiveStorePortableBackupCrypto().open(v1, passphrase),
        )
        assertEquals(
            PortableBackupOpenResult.Rejected(PortableBackupOpenFailure.INVALID_ENVELOPE),
            PortableBackupArchiveCrypto().open(v2, passphrase),
        )
    }

    @Test fun weakBlankOrNulPassphrasesCannotSealOrOpen() {
        val source = sample()
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        val bad = listOf("short".toCharArray(), "            ".toCharArray(), "unsafe\u0000passphrase".toCharArray())
        bad.forEach { phrase ->
            assertThrows(IllegalArgumentException::class.java) { crypto.seal(source, phrase) }
            assertThrows(IllegalArgumentException::class.java) { crypto.open(byteArrayOf(0), phrase) }
        }
    }
}
