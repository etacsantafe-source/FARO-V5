package ar.com.faro.v5.kernel.contracts

/**
 * A5: untrusted metadata for a single user-picked, ephemeral file.
 * The Android adapter owns bytes and short-lived URI grants; this contract
 * deliberately contains neither a URI nor a payload or remote-send capability.
 */
data class ConversationAttachmentCandidate(
    val conversationId: ConversationId,
    val pickerRequestId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
)

enum class ConversationAttachmentRejection {
    EMPTY_SELECTION,
    TOO_MANY,
    STALE_CONVERSATION,
    STALE_PICKER,
    INVALID_NAME,
    UNSUPPORTED_MIME,
    INVALID_SIZE,
    TOO_LARGE,
    INVALID_DIGEST,
    DUPLICATE,
    TOTAL_TOO_LARGE,
}

sealed interface ConversationAttachmentIngressResult {
    data class Ready(
        val conversationId: ConversationId,
        val pickerRequestId: String,
        val items: List<ConversationAttachmentCandidate>,
        val totalBytes: Long,
    ) : ConversationAttachmentIngressResult

    data class Rejected(val reason: ConversationAttachmentRejection) :
        ConversationAttachmentIngressResult
}

/**
 * A pure gate, not a file reader or egress authorization.
 * In particular, Ready never authorizes a model upload or persists evidence.
 * The platform must independently bound actual stream reads and validate
 * content signatures; provider-declared MIME/length cannot be trusted.
 */
object ConversationAttachmentIngressPolicy {
    const val MAX_FILES = 4
    const val MAX_FILE_BYTES = 12L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    const val MAX_NAME_CHARS = 240

    private val mimeAllowlist = setOf(
        "image/jpeg", "image/png", "image/webp", "image/heic",
        "application/pdf", "application/json",
        "text/plain", "text/markdown", "text/csv",
    )

    fun evaluate(
        activeConversationId: ConversationId,
        activePickerRequestId: String,
        candidates: List<ConversationAttachmentCandidate>,
    ): ConversationAttachmentIngressResult {
        fun rejected(reason: ConversationAttachmentRejection) =
            ConversationAttachmentIngressResult.Rejected(reason)

        if (candidates.isEmpty()) return rejected(ConversationAttachmentRejection.EMPTY_SELECTION)
        if (candidates.size > MAX_FILES) return rejected(ConversationAttachmentRejection.TOO_MANY)
        if (activePickerRequestId.isBlank()) return rejected(ConversationAttachmentRejection.STALE_PICKER)

        val digests = mutableSetOf<String>()
        var totalBytes = 0L
        for (item in candidates) {
            if (item.conversationId != activeConversationId) {
                return rejected(ConversationAttachmentRejection.STALE_CONVERSATION)
            }
            if (item.pickerRequestId != activePickerRequestId) {
                return rejected(ConversationAttachmentRejection.STALE_PICKER)
            }
            if (item.displayName.isBlank() ||
                item.displayName.length > MAX_NAME_CHARS ||
                item.displayName.any { it.code < 32 || it.code == 127 }
            ) return rejected(ConversationAttachmentRejection.INVALID_NAME)
            if (item.mimeType !in mimeAllowlist) {
                return rejected(ConversationAttachmentRejection.UNSUPPORTED_MIME)
            }
            if (item.sizeBytes <= 0) return rejected(ConversationAttachmentRejection.INVALID_SIZE)
            if (item.sizeBytes > MAX_FILE_BYTES) {
                return rejected(ConversationAttachmentRejection.TOO_LARGE)
            }
            if (item.sha256Hex.length != 64 ||
                item.sha256Hex.any { it !in '0'..'9' && it !in 'a'..'f' }
            ) return rejected(ConversationAttachmentRejection.INVALID_DIGEST)
            if (!digests.add(item.sha256Hex)) {
                return rejected(ConversationAttachmentRejection.DUPLICATE)
            }
            totalBytes += item.sizeBytes
            if (totalBytes > MAX_TOTAL_BYTES) {
                return rejected(ConversationAttachmentRejection.TOTAL_TOO_LARGE)
            }
        }
        return ConversationAttachmentIngressResult.Ready(
            conversationId = activeConversationId,
            pickerRequestId = activePickerRequestId,
            items = candidates.toList(),
            totalBytes = totalBytes,
        )
    }
}
