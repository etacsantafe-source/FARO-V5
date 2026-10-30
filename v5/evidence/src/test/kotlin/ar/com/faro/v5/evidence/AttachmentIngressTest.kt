package ar.com.faro.v5.evidence

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AttachmentIngressTest {
    private val policy = AttachmentIngress.Policy.defaultPolicy()
    private val sourceFingerprint = "1".repeat(64)

    @Test
    fun acceptedAttachmentIsBoundedHashedAndWipeable() {
        val bytes = "hola faro".encodeToByteArray()
        val result = AttachmentIngress.readEphemeral(
            source(" factura.txt ", "text/plain", bytes.size.toLong(), bytes),
            policy,
        )

        assertTrue(result.isSuccess)
        val attachment = result.attachment()
        assertEquals("factura.txt", attachment.displayName())
        assertEquals("text/plain", attachment.mediaType())
        assertEquals(bytes.size, attachment.sizeBytes())
        assertEquals(sourceFingerprint, attachment.sourceFingerprintSha256())
        assertArrayEquals(bytes, attachment.copyBytes())
        assertFalse(attachment.isWiped)

        attachment.wipe()
        assertTrue(attachment.isWiped)
        assertThrows(IllegalStateException::class.java) { attachment.copyBytes() }
    }

    @Test
    fun unsupportedMimeAndOversizeFailBeforeOpeningBytes() {
        var opens = 0
        val unsupported = object : AttachmentIngress.Source {
            override fun sourceFingerprintSha256() = sourceFingerprint
            override fun metadata() = AttachmentIngress.Metadata("payload.exe", "application/octet-stream", 10)
            override fun openStream(): ByteArrayInputStream {
                opens += 1
                return ByteArrayInputStream(ByteArray(10))
            }
        }
        val unsupportedResult = AttachmentIngress.readEphemeral(unsupported, policy)
        assertEquals(AttachmentIngress.FailureKind.CONTENT_TYPE_REJECTED, unsupportedResult.failureKind())
        assertEquals(0, opens)

        val oversized = object : AttachmentIngress.Source {
            override fun sourceFingerprintSha256() = sourceFingerprint
            override fun metadata() = AttachmentIngress.Metadata(
                "big.pdf",
                "application/pdf",
                policy.maxFileBytes().toLong() + 1,
            )
            override fun openStream(): ByteArrayInputStream {
                opens += 1
                return ByteArrayInputStream(ByteArray(1))
            }
        }
        val oversizedResult = AttachmentIngress.readEphemeral(oversized, policy)
        assertEquals(AttachmentIngress.FailureKind.TOO_LARGE, oversizedResult.failureKind())
        assertEquals(0, opens)
    }

    @Test
    fun streamBoundIsEnforcedWhenProviderDoesNotDeclareSize() {
        val tinyPolicy = AttachmentIngress.Policy(
            8,
            4,
            16,
            setOf("application/pdf"),
            emptySet(),
        )
        val bytes = ByteArray(9) { it.toByte() }
        val result = AttachmentIngress.readEphemeral(
            source("x.pdf", "application/pdf", null, bytes),
            tinyPolicy,
        )
        assertEquals(AttachmentIngress.FailureKind.TOO_LARGE, result.failureKind())
    }

    @Test
    fun declaredSizeMismatchFailsClosed() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = AttachmentIngress.readEphemeral(
            source("x.pdf", "application/pdf", 4, bytes),
            policy,
        )
        assertEquals(AttachmentIngress.FailureKind.INVALID_METADATA, result.failureKind())
    }

    @Test
    fun batchBoundsPreserveV4SafetyEnvelope() {
        val first = AttachmentIngress.readEphemeral(
            source("a.txt", "text/plain", 1, byteArrayOf(1)),
            policy,
        ).attachment()
        val attachments = List(policy.maxAttachmentsPerTurn() + 1) { first }
        assertEquals(
            AttachmentIngress.FailureKind.TOO_MANY_ATTACHMENTS,
            AttachmentIngress.validateBatch(attachments, policy),
        )
        first.wipe()
    }

    @Test
    fun privateStagingUsesDigestPathAndSurvivesEphemeralWipe(@TempDir root: Path) {
        val bytes = "documento".encodeToByteArray()
        val attachment = AttachmentIngress.readEphemeral(
            source("recibo.pdf", "application/pdf", bytes.size.toLong(), bytes),
            policy,
        ).attachment()

        val store = AttachmentPrivateStagingStore(root)
        val staged = store.stage(attachment)
        attachment.wipe()

        assertTrue(staged.stagingId().startsWith("sha256:"))
        assertEquals(bytes.size.toLong(), staged.sizeBytes())
        store.open(staged).use { input ->
            assertArrayEquals(bytes, input.readBytes())
        }

        val stagedAgain = AttachmentIngress.readEphemeral(
            source("recibo.pdf", "application/pdf", bytes.size.toLong(), bytes),
            policy,
        ).attachment().let { duplicate ->
            try {
                store.stage(duplicate)
            } finally {
                duplicate.wipe()
            }
        }
        assertEquals(staged.stagingId(), stagedAgain.stagingId())

        store.delete(staged)
        assertThrows(IOException::class.java) { store.open(staged).use { } }
    }

    @Test
    fun invalidSourceFingerprintIsRejected() {
        val result = AttachmentIngress.readEphemeral(
            object : AttachmentIngress.Source {
                override fun sourceFingerprintSha256() = "raw-content-uri"
                override fun metadata() = AttachmentIngress.Metadata("x.txt", "text/plain", 1)
                override fun openStream() = ByteArrayInputStream(byteArrayOf(1))
            },
            policy,
        )
        assertEquals(AttachmentIngress.FailureKind.INVALID_SOURCE, result.failureKind())
    }

    private fun source(
        name: String,
        mediaType: String,
        size: Long?,
        bytes: ByteArray,
    ): AttachmentIngress.Source = object : AttachmentIngress.Source {
        override fun sourceFingerprintSha256() = sourceFingerprint
        override fun metadata() = AttachmentIngress.Metadata(name, mediaType, size)
        override fun openStream() = ByteArrayInputStream(bytes)
    }
}
