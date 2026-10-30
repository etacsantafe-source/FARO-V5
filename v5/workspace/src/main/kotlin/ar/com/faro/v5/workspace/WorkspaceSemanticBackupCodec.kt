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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Isolated, canonical Workspace authority payload for the future five-store backup.
 *
 * WSB2 is NOT FBS1, an encrypted archive, a BackupSetManifest, or an activation
 * permit. Only a later certified five-store coordinator may bind it to a
 * generation, authenticate the full set, and restore a fresh target.
 *
 * Limits apply before allocation, including to hostile decoded inputs. This
 * conservative plaintext ceiling deliberately fails closed rather than letting
 * an unbackupable Workspace silently enter the active V2 generation.
 */
object WorkspaceSemanticBackupCodec {
    private const val MAGIC = 0x57534232 // WSB2
    private const val VERSION = 1
    const val MAX_PLAINTEXT_BYTES = 32 * 1024 * 1024
    private const val MAX_STRING_BYTES = 1 * 1024 * 1024

    fun encode(snapshot: WorkspaceBackupSnapshot): ByteArray =
        encodeWithin(snapshot, MAX_PLAINTEXT_BYTES)

    /** Bound before materializing a five-store plaintext; no oversized temporary snapshots. */
    fun encodeWithin(snapshot: WorkspaceBackupSnapshot, maxBytes: Int): ByteArray {
        require(maxBytes in 1..MAX_PLAINTEXT_BYTES) { "invalid Workspace byte budget" }
        snapshot.validateForRestore()
        val buffer = BoundedBuffer(maxBytes)
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(snapshot.schemaVersion)
            output.writeLong(snapshot.revision)
            optionalString(output, snapshot.activeConversationId?.value)
            count(output, snapshot.conversations.size, WorkspaceBackupSnapshot.MAX_RECORDS)
            snapshot.conversations.forEach { chat ->
                string(output, chat.id.value)
                string(output, chat.title)
                output.writeLong(chat.lastActivityEpochMillis)
                output.writeBoolean(chat.pinned)
                output.writeBoolean(chat.archived)
                output.writeBoolean(chat.hasPendingReview)
            }
            count(output, snapshot.receipts.size, WorkspaceBackupSnapshot.MAX_RECORDS)
            snapshot.receipts.forEach { receipt ->
                string(output, receipt.key)
                string(output, receipt.fingerprint)
                output.writeLong(receipt.committedRevision)
                output.writeBoolean(receipt.changed)
            }
            count(output, snapshot.drafts.size, WorkspaceBackupSnapshot.MAX_RECORDS)
            snapshot.drafts.forEach { draft ->
                string(output, draft.conversationId.value)
                output.writeLong(draft.revision)
                string(output, draft.text)
            }
            count(output, snapshot.messages.size, WorkspaceBackupSnapshot.MAX_RECORDS)
            snapshot.messages.forEach { item ->
                output.writeLong(item.sequence)
                val message = item.message
                string(output, message.id.value)
                string(output, message.conversationId.value)
                string(output, message.turnId.value)
                string(output, message.role.name)
                string(output, message.state.name)
                string(output, message.text)
                output.writeLong(message.createdAtEpochMillis)
                optionalString(output, message.replyToMessageId?.value)
            }
            count(output, snapshot.planReviews.size, WorkspaceBackupSnapshot.MAX_PLAN_REVIEWS)
            snapshot.planReviews.forEach { review ->
                string(output, review.reviewId)
                string(output, review.conversationId.value)
                string(output, review.planFingerprint.value)
                string(output, review.reviewText)
                output.writeLong(review.revision)
                string(output, review.state.name)
                string(output, review.envelopeBase64)
                string(output, review.envelopeSha256)
                optionalString(output, review.transactionId)
                optionalString(output, review.receiptId)
            }
            output.flush()
        }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): WorkspaceBackupSnapshot {
        check(bytes.size in 1..MAX_PLAINTEXT_BYTES) {
            "Workspace semantic payload outside bound"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC) { "Workspace semantic magic mismatch" }
            check(input.readInt() == VERSION) { "Workspace semantic version unsupported" }
            val schema = input.readInt()
            check(schema == WorkspaceBackupSnapshot.SCHEMA_VERSION) {
                "Workspace semantic schema unsupported"
            }
            val revision = input.readLong()
            val active = optionalString(input)?.let(::ConversationId)
            val conversations = List(readCount(input, WorkspaceBackupSnapshot.MAX_RECORDS)) {
                ConversationWorkspaceEntry(
                    id = ConversationId(string(input)),
                    title = string(input),
                    lastActivityEpochMillis = input.readLong(),
                    pinned = boolean(input),
                    archived = boolean(input),
                    hasPendingReview = boolean(input),
                )
            }
            val receipts = List(readCount(input, WorkspaceBackupSnapshot.MAX_RECORDS)) {
                WorkspaceBackupReceipt(
                    key = string(input),
                    fingerprint = string(input),
                    committedRevision = input.readLong(),
                    changed = boolean(input),
                )
            }
            val drafts = List(readCount(input, WorkspaceBackupSnapshot.MAX_RECORDS)) {
                WorkspaceBackupDraft(
                    conversationId = ConversationId(string(input)),
                    revision = input.readLong(),
                    text = string(input),
                )
            }
            val messages = List(readCount(input, WorkspaceBackupSnapshot.MAX_RECORDS)) {
                WorkspaceBackupMessage(
                    sequence = input.readLong(),
                    message = ConversationMessage(
                        id = ConversationMessageId(string(input)),
                        conversationId = ConversationId(string(input)),
                        turnId = ConversationTurnId(string(input)),
                        role = ConversationMessageRole.valueOf(string(input)),
                        state = ConversationMessageState.valueOf(string(input)),
                        text = string(input),
                        createdAtEpochMillis = input.readLong(),
                        replyToMessageId = optionalString(input)?.let(::ConversationMessageId),
                    ),
                )
            }
            val reviews = List(readCount(input, WorkspaceBackupSnapshot.MAX_PLAN_REVIEWS)) {
                WorkspaceBackupPlanReview(
                    reviewId = string(input),
                    conversationId = ConversationId(string(input)),
                    planFingerprint = PlanFingerprint(string(input)),
                    reviewText = string(input),
                    revision = input.readLong(),
                    state = ConversationPlanReviewState.valueOf(string(input)),
                    envelopeBase64 = string(input),
                    envelopeSha256 = string(input),
                    transactionId = optionalString(input),
                    receiptId = optionalString(input),
                )
            }
            check(input.available() == 0) { "Workspace semantic payload has trailing bytes" }
            WorkspaceBackupSnapshot(
                schemaVersion = schema,
                revision = revision,
                activeConversationId = active,
                conversations = conversations,
                receipts = receipts,
                drafts = drafts,
                messages = messages,
                planReviews = reviews,
            ).also { it.validateForRestore() }
        }
    }

    private fun string(output: DataOutputStream, value: String) {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(value))
        check(encoded.remaining() <= MAX_STRING_BYTES) {
            "Workspace semantic string too long"
        }
        output.writeInt(encoded.remaining())
        if (encoded.hasArray()) {
            output.write(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining())
        } else {
            val data = ByteArray(encoded.remaining())
            encoded.get(data)
            output.write(data)
        }
    }

    private fun string(input: DataInputStream): String {
        val size = input.readInt()
        check(size in 0..MAX_STRING_BYTES && size <= input.available()) {
            "Workspace semantic string outside bound or truncated"
        }
        val data = ByteArray(size)
        input.readFully(data)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(data)).toString()
    }

    private fun optionalString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        value?.let { string(output, it) }
    }

    private fun optionalString(input: DataInputStream): String? =
        if (boolean(input)) string(input) else null

    /** Reject noncanonical boolean encodings instead of treating every nonzero byte as true. */
    private fun boolean(input: DataInputStream): Boolean = when (val byte = input.readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> error("Workspace semantic invalid boolean byte: $byte")
    }

    private fun count(output: DataOutputStream, n: Int, bound: Int) {
        check(n in 0..bound) { "Workspace semantic record count outside bound" }
        output.writeInt(n)
    }

    private fun readCount(input: DataInputStream, bound: Int): Int {
        val n = input.readInt()
        check(n in 0..bound) { "Workspace semantic record count outside bound" }
        return n
    }

    private class BoundedBuffer(private val bound: Int) : ByteArrayOutputStream() {
        override fun write(b: Int) {
            check(size() < bound) { "Workspace semantic payload exceeds bound" }
            super.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(off >= 0 && len >= 0 && off <= b.size - len)
            check(len <= bound - size()) { "Workspace semantic payload exceeds bound" }
            super.write(b, off, len)
        }
    }
}
