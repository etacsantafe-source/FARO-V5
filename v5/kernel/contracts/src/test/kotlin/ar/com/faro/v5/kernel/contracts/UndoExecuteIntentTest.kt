package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure codec: NO authority, SQL, host, permissions, or cancellation. */
class UndoExecuteIntentTest {
    private fun intent(): UndoExecuteIntent {
        val fingerprint = PlanFingerprint("source-plan-1")
        return UndoExecuteIntent(
            sourceReview = ConversationPlanReviewContext(
                ConversationId("chat-original"), "review-original",
                "secret-authority-token", 7L, fingerprint,
            ),
            sourceReceipt = ExecutionReceipt(
                ReceiptId("receipt-original"), TransactionId("tx-original"), fingerprint,
            ),
            sourceOperationId = OperationId("reminder.create"),
            targetObjectId = "recordatorio-uno",
            originalCreatePayload = CanonicalPayload.fromEncoded(
                "reminder.rule.create.v1", byteArrayOf(1, 2, 3, 4),
            ),
            cancelledAtEpochMillis = 10000L,
        )
    }

    @Test fun canonicalRoundtripPreservesExactSourceAndDestination() {
        val given = intent()
        val encoded = UndoExecutePayload.payload(given)
        assertEquals(given, UndoExecutePayload.decode(encoded))
        assertEquals("undo.execute.intent.v1", encoded.formatId)
        assertEquals("tx-original", UndoExecutePayload.decode(encoded)!!.sourceReceipt.transactionId.value)
    }

    @Test fun identicalInputHasStableBytesAndFingerprint() {
        val first = UndoExecutePayload.payload(intent())
        val again = UndoExecutePayload.payload(intent())
        assertArrayEquals(first.copyBytes(), again.copyBytes())
        assertEquals(first.fingerprint, again.fingerprint)
    }

    @Test fun originalPayloadIsDefensiveAndDifferentOriginalChangesIdentity() {
        val bytes = byteArrayOf(9, 8, 7)
        val create = CanonicalPayload.fromEncoded("reminder.rule.create.v1", bytes)
        val initial = intent().copy(originalCreatePayload = create)
        val frozen = UndoExecutePayload.payload(initial)
        bytes[0] = 33
        assertEquals(9.toByte(), initial.originalCreatePayload.copyBytes()[0])
        val changed = UndoExecutePayload.payload(initial.copy(
            originalCreatePayload = CanonicalPayload.fromEncoded(
                "reminder.rule.create.v1", byteArrayOf(33, 8, 7),
            ),
        ))
        assertNotEquals(frozen.fingerprint, changed.fingerprint)
    }

    @Test fun sourceReviewFingerprintMustMatchHistoricalReceipt() {
        assertThrows(IllegalArgumentException::class.java) {
            intent().copy(sourceReceipt = intent().sourceReceipt.copy(
                planFingerprint = PlanFingerprint("another-source"),
            ))
        }
    }

    @Test fun invalidTargetSourceBytesAndCancelClockAreDenied() {
        assertThrows(IllegalArgumentException::class.java) {
            intent().copy(targetObjectId = " object ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent().copy(originalCreatePayload = CanonicalPayload.fromEncoded(
                "reminder.rule.create.v1", byteArrayOf(),
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent().copy(cancelledAtEpochMillis = -1L)
        }
    }

    @Test fun wrongFormatMagicVersionAndTrailingDataFailClosed() {
        val valid = UndoExecutePayload.payload(intent())
        assertNull(UndoExecutePayload.decode(CanonicalPayload.fromEncoded(
            "unknown-v99", valid.copyBytes(),
        )))
        val invalidMagic = valid.copyBytes().also { it[0] = 0 }
        assertNull(UndoExecutePayload.decode(CanonicalPayload.fromEncoded(
            UndoExecutePayload.FORMAT_ID, invalidMagic,
        )))
        val invalidVersion = valid.copyBytes().also { it[7] = 2 }
        assertNull(UndoExecutePayload.decode(CanonicalPayload.fromEncoded(
            UndoExecutePayload.FORMAT_ID, invalidVersion,
        )))
        val trailing = valid.copyBytes() + byteArrayOf(0)
        assertNull(UndoExecutePayload.decode(CanonicalPayload.fromEncoded(
            UndoExecutePayload.FORMAT_ID, trailing,
        )))
    }

    @Test fun malformedUtf8InSourceReviewIsRejectedRatherThanReplaced() {
        val bytes = UndoExecutePayload.payload(intent()).copyBytes()
        // Header magic/version (8), then length prefix (4) and "chat-original".
        bytes[12] = 0xC3.toByte()
        bytes[13] = 0x28
        assertNull(UndoExecutePayload.decode(CanonicalPayload.fromEncoded(
            UndoExecutePayload.FORMAT_ID, bytes,
        )))
    }

    @Test fun sensitiveTokensAreNotLoggedAndOversizedFieldsAreDenied() {
        val value = intent()
        assertEquals("UndoExecuteIntent(redacted)", value.toString())
        assertFalse(value.toString().contains(value.sourceReview.authorityToken))
        assertThrows(IllegalArgumentException::class.java) {
            UndoExecutePayload.payload(value.copy(
                sourceReview = value.sourceReview.copy(
                    conversationId = ConversationId("C".repeat(2049)),
                ),
            ))
        }
    }
}
