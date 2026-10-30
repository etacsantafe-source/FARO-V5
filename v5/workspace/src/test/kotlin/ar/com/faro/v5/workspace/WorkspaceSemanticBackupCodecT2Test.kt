package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceSemanticBackupCodecT2Test {
    private val id = ConversationId("chat-one")
    private val envelope = byteArrayOf(1, 2, 3, 4)

    private fun sample(): WorkspaceBackupSnapshot {
        val hash = MessageDigest.getInstance("SHA-256").digest(envelope)
            .joinToString("") { "%02x".format(it) }
        return WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 2L,
            activeConversationId = id,
            conversations = listOf(
                ConversationWorkspaceEntry(
                    id = id,
                    title = "Café ☕ y cuentas",
                    lastActivityEpochMillis = 1234L,
                    pinned = true,
                    hasPendingReview = true,
                )
            ),
            receipts = listOf(
                WorkspaceBackupReceipt(
                    key = "write-1",
                    fingerprint = "a".repeat(64),
                    committedRevision = 2L,
                    changed = true,
                )
            ),
            drafts = listOf(WorkspaceBackupDraft(id, 2L, "mañana 🚗")),
            messages = listOf(
                WorkspaceBackupMessage(
                    sequence = 1L,
                    message = ConversationMessage(
                        id = ConversationMessageId("msg-one"),
                        conversationId = id,
                        turnId = ConversationTurnId("turn-one"),
                        role = ConversationMessageRole.USER,
                        state = ConversationMessageState.ACCEPTED,
                        text = "¿cuánto gasté?",
                        createdAtEpochMillis = 2000L,
                    ),
                ),
                WorkspaceBackupMessage(
                    sequence = 2L,
                    message = ConversationMessage(
                        id = ConversationMessageId("msg-two"),
                        conversationId = id,
                        turnId = ConversationTurnId("turn-one"),
                        role = ConversationMessageRole.FARO,
                        state = ConversationMessageState.COMPLETED,
                        text = "Revisemos juntos.",
                        createdAtEpochMillis = 2001L,
                    ),
                ),
            ),
            planReviews = listOf(
                WorkspaceBackupPlanReview(
                    reviewId = "review-one",
                    conversationId = id,
                    planFingerprint = PlanFingerprint("b".repeat(64)),
                    reviewText = "Revisar pago",
                    revision = 2L,
                    state = ConversationPlanReviewState.PENDING,
                    envelopeBase64 = Base64.getEncoder().encodeToString(envelope),
                    envelopeSha256 = hash,
                )
            ),
        )
    }

    @Test fun completeWorkspaceAuthorityRoundTripsWithUtf8AndPendingEnvelope() {
        val expected = sample()
        val encoded = WorkspaceSemanticBackupCodec.encode(expected)
        assertEquals(expected, WorkspaceSemanticBackupCodec.decode(encoded))
        assertArrayEquals(encoded, WorkspaceSemanticBackupCodec.encode(expected))
        assertFalse(encoded.contentEquals(byteArrayOf(0x46, 0x42, 0x53, 0x31)))
    }

    @Test fun emptyWorkspaceRoundTripsWithoutInventingAuthority() {
        val empty = WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 0,
            activeConversationId = null,
            conversations = emptyList(),
            receipts = emptyList(),
        )
        val encoded = WorkspaceSemanticBackupCodec.encode(empty)
        assertEquals(empty, WorkspaceSemanticBackupCodec.decode(encoded))
        assertArrayEquals(encoded, WorkspaceSemanticBackupCodec.encode(WorkspaceSemanticBackupCodec.decode(encoded)))
    }

    @Test fun truncatedAndTrailingAndWrongMagicRejectBeforeRestoration() {
        val encoded = WorkspaceSemanticBackupCodec.encode(sample())
        assertThrows(Exception::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertThrows(Exception::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded + byteArrayOf(0x00))
        }
        assertThrows(Exception::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded.copyOf().apply { this[0] = 0 })
        }
        assertThrows(Exception::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded.copyOf().apply { ByteBuffer.wrap(this).putInt(4, 2) })
        }
    }

    @Test fun hostileCountRejectsBeforeAllocatingHugeLists() {
        val empty = WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 0,
            activeConversationId = null,
            conversations = emptyList(),
            receipts = emptyList(),
        )
        val encoded = WorkspaceSemanticBackupCodec.encode(empty)
        // Header: WSB2 + version + schema + revision + optional-active marker.
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded.copyOf().apply {
                ByteBuffer.wrap(this).putInt(21, Int.MAX_VALUE)
            })
        }
    }

    @Test fun noncanonicalBooleanRejectsRatherThanInventingActiveChat() {
        val empty = WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = 0,
            activeConversationId = null,
            conversations = emptyList(),
            receipts = emptyList(),
        )
        val encoded = WorkspaceSemanticBackupCodec.encode(empty)
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.decode(encoded.copyOf().apply {
                // WSB2 version+schema+revision then the optional-active boolean.
                this[20] = 2
            })
        }
    }

    @Test fun invalidCanonicalSnapshotAndOversizedPayloadFailClosed() {
        val invalid = sample().copy(
            receipts = listOf(
                WorkspaceBackupReceipt("dup", "a".repeat(64), 1L, true),
                WorkspaceBackupReceipt("dup", "a".repeat(64), 2L, false),
            )
        )
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.encode(invalid)
        }
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.decode(
                ByteArray(WorkspaceSemanticBackupCodec.MAX_PLAINTEXT_BYTES + 1)
            )
        }
    }

    @Test fun corruptedPreparedPlanEnvelopeDigestCannotDecodeAsAuthority() {
        val encoded = WorkspaceSemanticBackupCodec.encode(sample())
        val signature = Base64.getEncoder().encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val offset = (0..encoded.size - signature.size).first {
            encoded.copyOfRange(it, it + signature.size).contentEquals(signature)
        }
        val altered = encoded.copyOf()
        altered[offset] = if (altered[offset] == 'A'.code.toByte()) 'B'.code.toByte() else 'A'.code.toByte()
        assertThrows(IllegalStateException::class.java) {
            WorkspaceSemanticBackupCodec.decode(altered)
        }
    }
}
