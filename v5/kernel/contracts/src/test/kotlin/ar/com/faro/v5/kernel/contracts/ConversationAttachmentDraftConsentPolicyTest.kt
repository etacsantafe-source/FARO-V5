package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationAttachmentDraftConsentPolicyTest {
    private val chat = ConversationId("chat-a")
    private val picker = "picker-42"
    private val digest = "a".repeat(64)
    private fun file(
        conversation: ConversationId = chat,
        request: String = picker,
        mime: String = "text/plain",
        size: Long = 256,
        hash: String = digest,
    ) = ConversationAttachmentCandidate(
        conversation, request, "nota.txt", mime, size, hash,
    )
    private fun ingress(vararg files: ConversationAttachmentCandidate) =
        ConversationAttachmentIngressResult.Ready(chat, picker, files.toList(),
            files.sumOf { it.sizeBytes })
    private fun consent(
        confirmed: Boolean = true,
        conversation: ConversationId = chat,
        request: String = picker,
        hash: String = digest,
        size: Long = 256,
    ) = DraftConsent(conversation, request, hash, size, confirmed)
    private fun decision(
        ready: ConversationAttachmentIngressResult.Ready = ingress(file()),
        grant: DraftConsent = consent(),
        active: ConversationId = chat,
        activePicker: String = picker,
    ) = ConversationAttachmentDraftConsentPolicy.evaluate(
        ready, active, activePicker, grant,
    )
    private fun denied(
        reason: AttachmentDraftDenial,
        ready: ConversationAttachmentIngressResult.Ready = ingress(file()),
        grant: DraftConsent = consent(),
        active: ConversationId = chat,
        activePicker: String = picker,
    ) = assertEquals(AttachmentDraftDecision.Denied(reason),
        decision(ready, grant, active, activePicker))

    @Test fun pickerSelectionAloneNeverGrantsDraftConsent() {
        denied(AttachmentDraftDenial.NOT_CONFIRMED, grant = consent(confirmed = false))
    }

    @Test fun staleConversationIsDeniedEvenWithPriorConfirmation() {
        denied(AttachmentDraftDenial.STALE_CONVERSATION,
            active = ConversationId("chat-b"))
        denied(AttachmentDraftDenial.STALE_CONVERSATION,
            grant = consent(conversation = ConversationId("chat-b")))
    }

    @Test fun stalePickerAndBlankPickerAreDenied() {
        denied(AttachmentDraftDenial.STALE_PICKER, activePicker = "new-picker")
        denied(AttachmentDraftDenial.STALE_PICKER, activePicker = "")
        denied(AttachmentDraftDenial.STALE_PICKER, grant = consent(request = "old"))
    }

    @Test fun multiSelectionCannotBeImplicitlyCombinedIntoOnePrompt() {
        denied(AttachmentDraftDenial.MULTIPLE_FILES,
            ready = ingress(file(), file(hash = "b".repeat(64))))
    }

    @Test fun pdfAndImagesCannotMasqueradeAsTextForGroq() {
        for (mime in listOf("application/pdf", "image/png", "image/jpeg", "image/heic")) {
            denied(AttachmentDraftDenial.UNSUPPORTED_TEXT,
                ready = ingress(file(mime = mime)))
        }
    }

    @Test fun oversizedTextCannotEnterModelDraft() {
        denied(AttachmentDraftDenial.TOO_LARGE_FOR_DRAFT,
            ready = ingress(file(size = 8193)))
        denied(AttachmentDraftDenial.TOO_LARGE_FOR_DRAFT,
            ready = ingress(file(size = 0)))
    }

    @Test fun digestOrByteCountChangeInvalidatesApproval() {
        denied(AttachmentDraftDenial.CONTENT_CHANGED,
            ready = ingress(file(hash = "b".repeat(64))))
        denied(AttachmentDraftDenial.CONTENT_CHANGED,
            ready = ingress(file(size = 257)))
    }

    @Test fun exactBoundaryAllowsOnlyDraftStageAndNeverNetworkSend() {
        val allowed = decision(
            ready = ingress(file(mime = "text/markdown", size = 8192)),
            grant = consent(size = 8192),
        )
        assertTrue(allowed is AttachmentDraftDecision.ApprovedForDraftOnly)
        assertEquals(digest,
            (allowed as AttachmentDraftDecision.ApprovedForDraftOnly).candidate.sha256Hex)
    }
}
