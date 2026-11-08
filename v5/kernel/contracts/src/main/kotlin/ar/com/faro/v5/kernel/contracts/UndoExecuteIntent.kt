package ar.com.faro.v5.kernel.contracts

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * N033C3: this is an UNTRUSTED intent for a NEW operation, NOT a grant.
 * The original COMMITTED Workspace Review, exact Reality receipt/journal,
 * original CREATE payload and live owner state must all be checked by a
 * separate trusted host before a fresh human-approved Undo Apply may run.
 */
data class UndoExecuteIntent(
    val sourceReview: ConversationPlanReviewContext,
    val sourceReceipt: ExecutionReceipt,
    val sourceOperationId: OperationId,
    val targetObjectId: String,
    val originalCreatePayload: CanonicalPayload,
    val cancelledAtEpochMillis: Long,
) {
    init {
        require(sourceReceipt.planFingerprint == sourceReview.planFingerprint) {
            "Undo source receipt fingerprint must match source Review"
        }
        require(sourceOperationId.value.isNotBlank() &&
            sourceOperationId.value.length <= 128 &&
            sourceOperationId.value.none { it.code < 32 || it.code == 127 })
        require(targetObjectId.isNotBlank() &&
            targetObjectId == targetObjectId.trim() &&
            targetObjectId.length <= 128 &&
            targetObjectId.none { it.code < 32 || it.code == 127 })
        require(originalCreatePayload.size in 1..UndoExecutePayload.MAX_SOURCE_BYTES)
        require(originalCreatePayload.formatId.length in 1..128)
        require(cancelledAtEpochMillis >= 0L)
    }

    // Review authority tokens and receipt IDs must not escape through logs.
    override fun toString(): String = "UndoExecuteIntent(redacted)"
}

/**
 * Strict, canonical, length-bounded payload for the existing D-229
 * undo.execute operation. Payload bytes never authenticate the human.
 * Encodes original CREATE data as opaque bytes; a future trusted host must
 * verify those bytes against the sealed COMMITTED origin envelope.
 */
object UndoExecutePayload {
    const val FORMAT_ID = "undo.execute.intent.v1"
    const val MAX_SOURCE_BYTES = 8192
    const val MAX_PAYLOAD_BYTES = 16384
    private const val MAX_FIELD_BYTES = 2048
    private const val MAGIC = 0x554E4431 // UND1
    private const val VERSION = 1

    fun payload(intent: UndoExecuteIntent): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(intent))

    fun encodeBytes(intent: UndoExecuteIntent): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.field(intent.sourceReview.conversationId.value)
            output.field(intent.sourceReview.reviewId)
            output.field(intent.sourceReview.authorityToken)
            output.writeLong(intent.sourceReview.expectedRevision)
            output.field(intent.sourceReview.planFingerprint.value)
            output.field(intent.sourceReceipt.receiptId.value)
            output.field(intent.sourceReceipt.transactionId.value)
            output.field(intent.sourceReceipt.planFingerprint.value)
            output.field(intent.sourceOperationId.value)
            output.field(intent.targetObjectId)
            output.field(intent.originalCreatePayload.formatId)
            val original = intent.originalCreatePayload.copyBytes()
            require(original.size in 1..MAX_SOURCE_BYTES)
            output.writeInt(original.size)
            output.write(original)
            output.writeLong(intent.cancelledAtEpochMillis)
        }
        return bytes.toByteArray().also {
            require(it.size in 1..MAX_PAYLOAD_BYTES) { "Undo intent exceeds byte budget" }
        }
    }

    fun decode(payload: CanonicalPayload): UndoExecuteIntent? {
        if (payload.formatId != FORMAT_ID ||
            payload.size !in 1..MAX_PAYLOAD_BYTES) return null
        val source = payload.copyBytes()
        return runCatching {
            val result = DataInputStream(ByteArrayInputStream(source)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val conversation = ConversationId(input.field())
                val reviewId = input.field()
                val authority = input.field()
                val revision = input.readLong()
                val fingerprint = PlanFingerprint(input.field())
                val receiptId = ReceiptId(input.field())
                val transactionId = TransactionId(input.field())
                val receiptFingerprint = PlanFingerprint(input.field())
                val operation = OperationId(input.field())
                val targetId = input.field()
                val originalFormat = input.field()
                val originalSize = input.readInt()
                require(originalSize in 1..MAX_SOURCE_BYTES)
                val originalBytes = ByteArray(originalSize)
                input.readFully(originalBytes)
                val cancelAt = input.readLong()
                require(input.read() == -1) { "Undo payload contains trailing bytes" }
                UndoExecuteIntent(
                    sourceReview = ConversationPlanReviewContext(
                        conversation, reviewId, authority, revision, fingerprint,
                    ),
                    sourceReceipt = ExecutionReceipt(
                        receiptId, transactionId, receiptFingerprint,
                    ),
                    sourceOperationId = operation,
                    targetObjectId = targetId,
                    originalCreatePayload = CanonicalPayload.fromEncoded(
                        originalFormat, originalBytes,
                    ),
                    cancelledAtEpochMillis = cancelAt,
                )
            }
            require(encodeBytes(result).contentEquals(source)) {
                "Undo intent bytes are not canonical"
            }
            result
        }.getOrNull()
    }

    private fun strictUtf8(value: String): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    private fun DataOutputStream.field(value: String) {
        val bytes = strictUtf8(value)
        require(bytes.size in 1..MAX_FIELD_BYTES) { "Undo intent field over budget" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.field(): String {
        val count = readInt()
        require(count in 1..MAX_FIELD_BYTES) { "Invalid Undo intent field length" }
        val encoded = ByteArray(count)
        readFully(encoded)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(encoded)).toString()
    }
}
