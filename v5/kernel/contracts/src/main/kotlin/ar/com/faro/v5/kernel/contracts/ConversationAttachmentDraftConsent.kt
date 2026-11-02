package ar.com.faro.v5.kernel.contracts

/**
 * A5 second gate: one explicit human confirmation may stage ONE verified text
 * attachment into the current user draft. It is NOT a provider-egress grant.
 *
 * The Android host must obtain confirmation from a visible user action AFTER
 * showing the file identity and BEFORE constructing [DraftConsent]. The host
 * must re-check Workspace identity and the actual bytes/digest immediately
 * before editing the draft. The user must still press Send, and the trusted
 * model gateway must independently authorize any remote egress.
 *
 * This policy contains no URI, bytes, credentials, persistent grant or network
 * operation. No approval can be inferred from opening the system picker.
 */
data class DraftConsent(
    val conversationId: ConversationId,
    val pickerRequestId: String,
    val sha256Hex: String,
    val sizeBytes: Long,
    val explicitlyConfirmed: Boolean,
)

enum class AttachmentDraftDenial {
    NOT_CONFIRMED,
    STALE_CONVERSATION,
    STALE_PICKER,
    MULTIPLE_FILES,
    UNSUPPORTED_TEXT,
    TOO_LARGE_FOR_DRAFT,
    CONTENT_CHANGED,
}

sealed interface AttachmentDraftDecision {
    data class ApprovedForDraftOnly(val candidate: ConversationAttachmentCandidate) :
        AttachmentDraftDecision
    data class Denied(val reason: AttachmentDraftDenial) : AttachmentDraftDecision
}

object ConversationAttachmentDraftConsentPolicy {
    /** Text only; deliberately far smaller than the general 24 MiB picker cap. */
    const val MAX_DRAFT_TEXT_BYTES: Long = 8L * 1024L
    private val supported = setOf(
        "text/plain", "text/markdown", "text/csv", "application/json",
    )

    fun evaluate(
        ingress: ConversationAttachmentIngressResult.Ready,
        activeConversationId: ConversationId,
        activePickerRequestId: String,
        consent: DraftConsent,
    ): AttachmentDraftDecision {
        fun deny(reason: AttachmentDraftDenial) = AttachmentDraftDecision.Denied(reason)
        if (!consent.explicitlyConfirmed) return deny(AttachmentDraftDenial.NOT_CONFIRMED)
        if (ingress.conversationId != activeConversationId ||
            consent.conversationId != activeConversationId
        ) return deny(AttachmentDraftDenial.STALE_CONVERSATION)
        if (activePickerRequestId.isBlank() ||
            ingress.pickerRequestId != activePickerRequestId ||
            consent.pickerRequestId != activePickerRequestId
        ) return deny(AttachmentDraftDenial.STALE_PICKER)
        if (ingress.items.size != 1) return deny(AttachmentDraftDenial.MULTIPLE_FILES)
        val file = ingress.items.single()
        if (file.conversationId != activeConversationId ||
            file.pickerRequestId != activePickerRequestId
        ) return deny(AttachmentDraftDenial.STALE_CONVERSATION)
        if (file.mimeType !in supported) return deny(AttachmentDraftDenial.UNSUPPORTED_TEXT)
        if (file.sizeBytes <= 0 || file.sizeBytes > MAX_DRAFT_TEXT_BYTES)
            return deny(AttachmentDraftDenial.TOO_LARGE_FOR_DRAFT)
        if (consent.sha256Hex != file.sha256Hex ||
            consent.sizeBytes != file.sizeBytes
        ) return deny(AttachmentDraftDenial.CONTENT_CHANGED)
        return AttachmentDraftDecision.ApprovedForDraftOnly(file)
    }
}
