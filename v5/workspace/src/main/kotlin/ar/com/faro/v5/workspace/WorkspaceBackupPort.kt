package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import java.security.MessageDigest
import java.util.Base64
import ar.com.faro.v5.kernel.contracts.ConversationDraftPolicy
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationTimelinePolicy
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry

/**
 * Staged V5 metadata + drafts + messages + prepared-review semantic backup contract.
 * NOT yet an encrypted five-store backup or a valid Reality execution receipt.
 * Full activation and cross-store recovery are owned by S2.A6.
 */
data class WorkspaceBackupDraft(
    val conversationId: ConversationId,
    val revision: Long,
    val text: String,
)

data class WorkspaceBackupMessage(
    val sequence: Long,
    val message: ConversationMessage,
)

data class WorkspaceBackupReceipt(
    val key: String,
    val fingerprint: String,
    val committedRevision: Long,
    val changed: Boolean,
)

/** Opaque canonical frozen plan is preserved, not decoded by Workspace.
 * Base64 is used for semantic snapshot equality; do not print this payload.
 */
data class WorkspaceBackupPlanReview(
    val reviewId: String,
    val conversationId: ConversationId,
    val planFingerprint: PlanFingerprint,
    val reviewText: String,
    val revision: Long,
    val state: ConversationPlanReviewState,
    val envelopeBase64: String,
    val envelopeSha256: String,
    val transactionId: String? = null,
    val receiptId: String? = null,
) {
    override fun toString(): String =
        "WorkspaceBackupPlanReview(id=" + reviewId + ", state=" + state + ")"
}

data class WorkspaceBackupSnapshot(
    val schemaVersion: Int,
    val revision: Long,
    val activeConversationId: ConversationId?,
    val conversations: List<ConversationWorkspaceEntry>,
    val receipts: List<WorkspaceBackupReceipt>,
    val drafts: List<WorkspaceBackupDraft> = emptyList(),
    val messages: List<WorkspaceBackupMessage> = emptyList(),
    val planReviews: List<WorkspaceBackupPlanReview> = emptyList(),
) {
    fun validateForRestore() {
        check(schemaVersion == SCHEMA_VERSION) { "unsupported Workspace backup version" }
        check(revision >= 0) { "negative Workspace backup revision" }
        check(conversations.size <= MAX_RECORDS && receipts.size <= MAX_RECORDS &&
            drafts.size <= MAX_RECORDS && messages.size <= MAX_RECORDS &&
            planReviews.size <= MAX_PLAN_REVIEWS) {
            "Workspace backup record count exceeded"
        }

        val ids = mutableSetOf<ConversationId>()
        conversations.forEach { entry ->
            check(ids.add(entry.id)) { "duplicate Workspace backup conversation id" }
            check(entry.id.value.length <= 128 &&
                entry.id.value.none { it.code < 32 || it.code == 127 }) {
                "invalid Workspace backup conversation id"
            }
            check(entry.title.isNotEmpty() &&
                entry.title == entry.title.trim() &&
                entry.title.length <= 80 &&
                entry.title.none { it.code < 32 || it.code == 127 }) {
                "invalid Workspace backup title"
            }
            check(entry.lastActivityEpochMillis >= 0) {
                "invalid Workspace backup activity timestamp"
            }
            check(!entry.archived || !entry.pinned) { "archived conversation cannot be pinned" }
        }
        check(conversations == conversations.sortedBy { it.id.value }) {
            "Workspace backup conversation order is not canonical"
        }
        check(activeConversationId == null ||
            conversations.any { it.id == activeConversationId && !it.archived }) {
            "Workspace backup active conversation does not exist"
        }

        val reviewIds = mutableSetOf<String>()
        val pendingByChat = mutableSetOf<ConversationId>()
        var totalPlanBytes = 0L
        planReviews.forEach { review ->
            check(review.reviewId.length in 1..128 && review.reviewId.none {
                it.code < 32 || it.code == 127
            } && reviewIds.add(review.reviewId) && review.conversationId in ids) {
                "invalid or duplicate Workspace review identity/owner"
            }
            check(review.planFingerprint.value.matches(Regex("[0-9a-f]{64}"))) {
                "invalid Workspace prepared plan fingerprint"
            }
            check(review.revision >= 0 &&
                review.reviewText.isNotBlank() && review.reviewText.length <= 4_000) {
                "invalid Workspace review revision/text"
            }
            check(review.envelopeBase64.length <= 700_000) {
                "Workspace plan envelope Base64 exceeds bound"
            }
            val bytes = try {
                Base64.getDecoder().decode(review.envelopeBase64)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("invalid Workspace plan envelope encoding", error)
            }
            check(bytes.isNotEmpty() && bytes.size <= 524_288 &&
                Base64.getEncoder().encodeToString(bytes) == review.envelopeBase64) {
                "invalid or noncanonical Workspace plan envelope"
            }
            totalPlanBytes += bytes.size
            check(totalPlanBytes <= MAX_TOTAL_PLAN_BYTES) {
                "Workspace aggregate plan envelope limit exceeded"
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            check(review.envelopeSha256 == digest) {
                "Workspace prepared plan envelope was altered"
            }
            when (review.state) {
                ConversationPlanReviewState.PENDING,
                ConversationPlanReviewState.APPROVED,
                ConversationPlanReviewState.DISCARDED ->
                    check(review.transactionId == null && review.receiptId == null)
                ConversationPlanReviewState.APPLYING,
                ConversationPlanReviewState.RECOVERING_UNKNOWN,
                ConversationPlanReviewState.REJECTED ->
                    check(!review.transactionId.isNullOrBlank() && review.receiptId == null)
                ConversationPlanReviewState.COMMITTED ->
                    check(!review.transactionId.isNullOrBlank() &&
                        !review.receiptId.isNullOrBlank())
            }
            if (review.state in setOf(
                ConversationPlanReviewState.PENDING, ConversationPlanReviewState.APPROVED,
                ConversationPlanReviewState.APPLYING, ConversationPlanReviewState.RECOVERING_UNKNOWN,
            )) pendingByChat.add(review.conversationId)
        }
        check(planReviews == planReviews.sortedBy { it.reviewId }) {
            "Workspace reviews not in canonical order"
        }
        conversations.forEach { chat ->
            check(chat.hasPendingReview == (chat.id in pendingByChat)) {
                "Workspace pending metadata does not match plan authority"
            }
        }

        val draftIds = mutableSetOf<ConversationId>()
        var totalDraftChars = 0L
        drafts.forEach { draft ->
            check(draftIds.add(draft.conversationId) && draft.conversationId in ids) {
                "Workspace backup draft must reference one existing conversation"
            }
            check(draft.revision in 0..revision &&
                draft.text.length <= ConversationDraftPolicy.MAX_TEXT_CHARS) {
                "Workspace backup draft revision or size invalid"
            }
            totalDraftChars += draft.text.length
            check(totalDraftChars <= MAX_TOTAL_DRAFT_CHARS) {
                "Workspace backup aggregate draft size exceeded"
            }
        }
        check(drafts == drafts.sortedBy { it.conversationId.value }) {
            "Workspace backup drafts are not in canonical order"
        }

        val messageIds = mutableSetOf<String>()
        val messageSequences = mutableSetOf<Long>()
        var totalMessageChars = 0L
        val messagesByConversation = mutableMapOf<ConversationId, MutableList<ConversationMessage>>()
        messages.forEach { item ->
            val message = item.message
            check(item.sequence > 0 && messageSequences.add(item.sequence) &&
                message.conversationId in ids && messageIds.add(message.id.value)) {
                "Workspace backup message owner/id invalid"
            }
            totalMessageChars += message.text.length
            check(totalMessageChars <= MAX_TOTAL_MESSAGE_CHARS) {
                "Workspace backup aggregate message limit exceeded"
            }
            messagesByConversation.getOrPut(message.conversationId) { mutableListOf() } += message
        }
        check(messages == messages.sortedBy { it.sequence }) {
            "Workspace backup messages must follow append sequence"
        }
        messagesByConversation.forEach { (chat, items) ->
            ConversationTimelineSnapshot(chat, "backup-validation", revision, items)
            check(items.size <= ConversationTimelinePolicy.MAX_MESSAGES_PER_CHAT)
        }

        val keys = mutableSetOf<String>()
        receipts.forEach { receipt ->
            check(keys.add(receipt.key)) { "duplicate Workspace idempotency key in backup" }
            check(receipt.key.isNotBlank() && receipt.key.length <= 160 &&
                receipt.key.none { it.code < 32 || it.code == 127 }) {
                "invalid Workspace backup receipt key"
            }
            check(receipt.fingerprint.matches(Regex("[0-9a-f]{64}"))) {
                "invalid Workspace backup receipt fingerprint"
            }
            check(receipt.committedRevision in 0..revision) {
                "Workspace backup receipt revision outside authority bound"
            }
        }
        check(receipts == receipts.sortedBy { it.key }) {
            "Workspace backup receipts not in canonical order"
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 4
        const val MAX_PLAN_REVIEWS: Int = 64
        const val MAX_TOTAL_PLAN_BYTES: Long = 4_194_304
        const val MAX_RECORDS: Int = 100_000
        const val MAX_TOTAL_DRAFT_CHARS: Long = 2_000_000
        const val MAX_TOTAL_MESSAGE_CHARS: Long = 4_000_000
    }
}

/** A narrow administrative port; read/write calls are serialized inside the store. */
class WorkspaceBackupPort internal constructor(
    private val readSnapshot: () -> WorkspaceBackupSnapshot,
    private val restoreSnapshot: (WorkspaceBackupSnapshot) -> WorkspaceBackupSnapshot,
) {
    fun snapshot(): WorkspaceBackupSnapshot = readSnapshot()

    /** Restores only into a fresh generation, or verifies identical existing state. */
    fun restoreOrVerify(snapshot: WorkspaceBackupSnapshot): WorkspaceBackupSnapshot =
        restoreSnapshot(snapshot)
}
