package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationAttachmentIngressPolicyTest {
    private val chat = ConversationId("chat-a")
    private val picker = "picker-request-1"

    private fun candidate(
        conversationId: ConversationId = chat,
        pickerRequestId: String = picker,
        name: String = "imagen.png",
        mime: String = "image/png",
        bytes: Long = 1024,
        digest: String = "a".repeat(64),
    ) = ConversationAttachmentCandidate(
        conversationId, pickerRequestId, name, mime, bytes, digest,
    )

    private fun result(vararg items: ConversationAttachmentCandidate) =
        ConversationAttachmentIngressPolicy.evaluate(chat, picker, items.toList())

    private fun rejected(
        reason: ConversationAttachmentRejection,
        vararg items: ConversationAttachmentCandidate,
    ) = assertEquals(ConversationAttachmentIngressResult.Rejected(reason), result(*items))

    @Test fun emptySelectionNeverCreatesAttachmentTurn() {
        rejected(ConversationAttachmentRejection.EMPTY_SELECTION)
    }

    @Test fun moreThanFourItemsIsRejectedBeforeReading() {
        rejected(
            ConversationAttachmentRejection.TOO_MANY,
            *List(5) { candidate(digest = it.toString(16).padStart(64, '0')) }.toTypedArray(),
        )
    }

    @Test fun staleConversationCannotAttachToTheNewChat() {
        rejected(ConversationAttachmentRejection.STALE_CONVERSATION,
            candidate(conversationId = ConversationId("chat-b")))
    }

    @Test fun stalePickerRequestCannotReuseOldResults() {
        rejected(ConversationAttachmentRejection.STALE_PICKER,
            candidate(pickerRequestId = "previous"))
        assertEquals(
            ConversationAttachmentIngressResult.Rejected(ConversationAttachmentRejection.STALE_PICKER),
            ConversationAttachmentIngressPolicy.evaluate(chat, "", listOf(candidate())),
        )
    }

    @Test fun controlCharactersAndBlankNamesFailClosed() {
        rejected(ConversationAttachmentRejection.INVALID_NAME, candidate(name = ""))
        rejected(ConversationAttachmentRejection.INVALID_NAME, candidate(name = "foto\n.jpg"))
        rejected(ConversationAttachmentRejection.INVALID_NAME, candidate(name = "x".repeat(241)))
    }

    @Test fun unrecognizedAndExecutableFormatsAreBlocked() {
        rejected(ConversationAttachmentRejection.UNSUPPORTED_MIME,
            candidate(mime = "application/octet-stream"))
        rejected(ConversationAttachmentRejection.UNSUPPORTED_MIME,
            candidate(mime = "application/x-msdownload"))
        rejected(ConversationAttachmentRejection.UNSUPPORTED_MIME,
            candidate(mime = "image/svg+xml"))
    }

    @Test fun emptyOrNegativeBytesCannotBeUploaded() {
        rejected(ConversationAttachmentRejection.INVALID_SIZE, candidate(bytes = 0))
        rejected(ConversationAttachmentRejection.INVALID_SIZE, candidate(bytes = -1))
    }

    @Test fun moreThanTwelveMibInOneFileIsRejected() {
        rejected(ConversationAttachmentRejection.TOO_LARGE,
            candidate(bytes = ConversationAttachmentIngressPolicy.MAX_FILE_BYTES + 1))
    }

    @Test fun digestMustBeExactCanonicalLowercaseSha256() {
        rejected(ConversationAttachmentRejection.INVALID_DIGEST, candidate(digest = "aaa"))
        rejected(ConversationAttachmentRejection.INVALID_DIGEST, candidate(digest = "G".repeat(64)))
        rejected(ConversationAttachmentRejection.INVALID_DIGEST, candidate(digest = "A".repeat(64)))
    }

    @Test fun duplicateBytesCannotBeSilentlyStagedTwice() {
        rejected(ConversationAttachmentRejection.DUPLICATE, candidate(),
            candidate(name = "otra.png"))
    }

    @Test fun totalPayloadLimitProtectsSmallHeap() {
        rejected(
            ConversationAttachmentRejection.TOTAL_TOO_LARGE,
            candidate(bytes = 10L * 1024 * 1024, digest = "a".repeat(64)),
            candidate(bytes = 10L * 1024 * 1024, digest = "b".repeat(64)),
            candidate(bytes = 5L * 1024 * 1024, digest = "c".repeat(64)),
        )
    }

    @Test fun exactBoundaryCanBeSelectedWithoutAnySendAuthorization() {
        val ready = result(
            candidate(bytes = ConversationAttachmentIngressPolicy.MAX_FILE_BYTES),
            candidate(name = "doc.pdf", mime = "application/pdf",
                bytes = ConversationAttachmentIngressPolicy.MAX_FILE_BYTES,
                digest = "b".repeat(64)),
        )
        assertTrue(ready is ConversationAttachmentIngressResult.Ready)
        assertEquals(ConversationAttachmentIngressPolicy.MAX_TOTAL_BYTES,
            (ready as ConversationAttachmentIngressResult.Ready).totalBytes)
        assertEquals(chat, ready.conversationId)
        assertEquals(picker, ready.pickerRequestId)
    }

    @Test fun supportedTextAndImageFormatsStaySelectable() {
        for (mime in listOf(
            "text/plain", "text/markdown", "text/csv", "application/json",
            "image/png", "image/jpeg", "image/webp", "image/heic",
        )) {
            assertTrue(result(candidate(mime = mime)) is ConversationAttachmentIngressResult.Ready)
        }
    }

    @Test fun aSuccessfulResultContainsNoProviderUriOrPayload() {
        val ready = result(candidate())
        assertTrue(ready is ConversationAttachmentIngressResult.Ready)
        assertEquals(1, (ready as ConversationAttachmentIngressResult.Ready).items.size)
        assertEquals(1024, ready.totalBytes)
    }
}
