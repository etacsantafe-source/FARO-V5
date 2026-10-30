package ar.com.faro.v5.maintenance

import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Historical V1 wire fixture, deliberately independent of the current encoder.
 * No SQLite/native driver: directed pure JVM oracle for legacy restore safety.
 */
class LegacyGenerationAndBackupV1T1Test {
    private val descriptor = StorageGenerationDescriptor(
        generationId = StorageGenerationId("legacy-generation-001"),
        schemaSetFingerprint = "schema-v1-stable",
        createdAtEpochMillis = 42L,
    )

    // FARO envelope magic + version 1 + payload size + DataOutputStream V1
    // (writeUTF, writeUTF, writeLong) + SHA-256 of exact raw payload.
    private val historicalDescriptorRecord: ByteArray =
        ("4641524f000000010000003100156c65676163792d67656e65726174696f6e2d303031" +
            "0010736368656d612d76312d737461626c65000000000000002a" +
            "a817dccc81b6a77db7952e2642b2d6900458a9fbf38622d947e35785b3e9d9b5")
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun generationDescriptorV1EncodingMatchesIndependentGoldenBytes() {
        assertEquals(93, historicalDescriptorRecord.size)
        assertArrayEquals(historicalDescriptorRecord, StorageGenerationDescriptorCodec.encode(descriptor))
        assertEquals(descriptor, StorageGenerationDescriptorCodec.decode(historicalDescriptorRecord))
    }

    @Test fun generationDescriptorRejectsTamperTruncationAndTrailingData() {
        val tampered = historicalDescriptorRecord.copyOf()
        tampered[25] = (tampered[25].toInt() xor 1).toByte()
        assertThrows(IllegalStateException::class.java) {
            StorageGenerationDescriptorCodec.decode(tampered)
        }
        assertThrows(IllegalStateException::class.java) {
            StorageGenerationDescriptorCodec.decode(
                historicalDescriptorRecord.copyOf(historicalDescriptorRecord.size - 1)
            )
        }
        assertThrows(IllegalStateException::class.java) {
            StorageGenerationDescriptorCodec.decode(historicalDescriptorRecord + byteArrayOf(1))
        }
    }

    @Test fun semanticBackupV1RejectsUnknownNewVersionWithoutReinterpretingPayload() {
        assertEquals(1, BackupSetManifest.FORMAT_VERSION)
        val unknownVersion = ByteBuffer.allocate(8)
            .putInt(0x46425331) // FBS1
            .putInt(2) // Future V2 requires explicit codec dispatch.
            .array()
        assertThrows(IllegalStateException::class.java) {
            SemanticBackupSetCodec.decode(unknownVersion)
        }
    }

    @Test fun historicalBackupClassPolicyIsPinnedUntilVersionedMigration() {
        val expected = listOf(
            Triple("REALITY_DOMAIN", true, "restorable Reality domain authority"),
            Triple("REALITY_COMMITTED_RECEIPTS", true, "committed receipts retained without pending Apply state"),
            Triple("COMMERCIAL_EVIDENCE", true, "restorable commercial Evidence authority"),
            Triple("WEB_EVIDENCE", true, "restorable retained Web Evidence"),
            Triple("AUXILIARY_NOTICES", true, "retained Notice authority without delivery projections"),
            Triple("REBUILDABLE_PROJECTIONS", false, "rebuild from restored authority"),
            Triple("SECRETS", false, "Vault and credential material is excluded"),
            Triple("PREPARED_APPLY_ATTEMPTS", false, "prepared Apply state is unsafe to replay"),
            Triple("REALITY_OUTBOX_EFFECTS", false, "pending external effects are unsafe to replay"),
            Triple("DELIVERY_INTENTS", false, "delivery intents are reprojected after restore"),
            Triple("TEMP_STAGING", false, "temporary and staged runtime state is excluded"),
        )
        val actual = BackupClassPolicy.decisions.map {
            Triple(it.contentClass.name, it.included, it.reason)
        }
        assertEquals(expected, actual)
    }
}
