package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FaroV5MessageTimelineStatusTest {
    private fun faro(status: ConversationMessageState, text: String = "") =
        ConversationMessage(
            ConversationMessageId("faro-1"), ConversationId("original-chat"),
            ConversationTurnId("turn-a"), ConversationMessageRole.FARO,
            status, text, 100L,
        )

    @Test fun persistedProcessingWithoutRuntimeEvidenceIsNotAdvertisedAsLive() {
        assertEquals("Respuesta pendiente de verificar",
            faroV5MessageStatusLabel(faro(ConversationMessageState.PROCESSING)))
    }

    @Test fun processingLabelRequiresExplicitlyConfirmedLiveTurn() {
        assertEquals("Procesando…",
            faroV5MessageStatusLabel(faro(ConversationMessageState.PROCESSING), true))
    }

    @Test fun offlineStateNeverClaimsAssistantOutput() {
        assertEquals("Sin conexión para responder",
            faroV5MessageStatusLabel(faro(ConversationMessageState.OFFLINE)))
    }

    @Test fun completedMessageHasNoFailureOrProcessingBadge() {
        assertNull(faroV5MessageStatusLabel(
            faro(ConversationMessageState.COMPLETED, "Respuesta legítima")))
    }
}
