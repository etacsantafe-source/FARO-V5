package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationAttachmentIngressResult

/**
 * Ephemeral result from the Android picker. TextPendingApproval is never a
 * model request, a durable Workspace attachment, or permission to transmit.
 * It must be discarded on cancel, conversation switch, activity destruction
 * or failed Workspace authority recheck. No URI is retained.
 */
sealed interface FaroV5AttachmentIngressResult {
    data class LocalPreview(val message: String) : FaroV5AttachmentIngressResult
    data class TextPendingApproval(
        val ingress: ConversationAttachmentIngressResult.Ready,
        val text: String,
    ) : FaroV5AttachmentIngressResult
}
