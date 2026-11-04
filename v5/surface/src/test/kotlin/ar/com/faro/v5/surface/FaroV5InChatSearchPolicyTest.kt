package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5InChatSearchPolicyTest {
    private val conversation = ConversationId("chat-search-policy")
    private fun user(id: String, turn: String, text: String) = ConversationMessage(
        ConversationMessageId(id), conversation, ConversationTurnId(turn),
        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED, text, 100L,
    )
    private fun faro(id: String, turn: String, state: ConversationMessageState, text: String) =
        ConversationMessage(
            ConversationMessageId(id), conversation, ConversationTurnId(turn),
            ConversationMessageRole.FARO, state, text, 200L,
        )
    private fun snapshot(vararg messages: ConversationMessage) =
        ConversationTimelineSnapshot(conversation, "real-workspace-token", 5L, messages.toList())

    @Test fun spanishAccentsAndWordOrderResolveToExactCanonicalMessageId() {
        val timeline = snapshot(user("m1", "t1", "Café de noviembre pendiente"))
        assertEquals(listOf(ConversationMessageId("m1")),
            FaroV5InChatSearchPolicy.matchingMessageIds(timeline, "PENDIENTE cafe"))
    }

    @Test fun partialAssistantResponseIsNotAConfirmedSearchResult() {
        val timeline = snapshot(
            user("m1", "t1", "Consulta"),
            faro("m2", "t1", ConversationMessageState.PARTIAL, "Clave parcial"),
            user("m3", "t2", "Otra consulta"),
            faro("m4", "t2", ConversationMessageState.COMPLETED, "Clave confirmada"),
        )
        assertEquals(listOf(ConversationMessageId("m4")),
            FaroV5InChatSearchPolicy.matchingMessageIds(timeline, "clave"))
    }

    @Test fun termsMustOccurInSameMessageNotAcrossTurns() {
        val timeline = snapshot(
            user("m1", "t1", "Pagar electricidad"),
            user("m2", "t2", "Saldo vencido"),
        )
        assertTrue(FaroV5InChatSearchPolicy.matchingMessageIds(
            timeline, "electricidad vencido",
        ).isEmpty())
    }

    @Test fun blankAndOverlongQueryDoNotProduceSearchResults() {
        val timeline = snapshot(user("m1", "t1", "Texto visible"))
        assertTrue(FaroV5InChatSearchPolicy.matchingMessageIds(timeline, "  .. ").isEmpty())
        assertTrue(FaroV5InChatSearchPolicy.matchingMessageIds(
            timeline, "x".repeat(201),
        ).isEmpty())
    }

    @Test fun newCanonicalSnapshotCannotReviveOldMessageId() {
        val previous = snapshot(user("old", "t1", "Pago anterior"))
        val current = snapshot(user("new", "t2", "Pago nuevo"))
        assertEquals(listOf(ConversationMessageId("old")),
            FaroV5InChatSearchPolicy.matchingMessageIds(previous, "pago"))
        assertEquals(listOf(ConversationMessageId("new")),
            FaroV5InChatSearchPolicy.matchingMessageIds(current, "pago"))
    }
}
