package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test fun moreThan250MessagesReportsPartialCoverageInsteadOfFalseEmpty() {
        val messages = (1..251).map { index ->
            user("m-$index", "turn-$index", "nota número $index")
        }
        val result = FaroV5InChatSearchPolicy.search(
            snapshot(*messages.toTypedArray()), "nota",
        )
        assertFalse(result.complete)
        assertEquals(250, result.messageIds.size)
        assertEquals(ConversationMessageId("m-2"), result.messageIds.first())
        assertEquals(ConversationMessageId("m-251"), result.messageIds.last())
    }

    @Test fun characterBudgetIsFailClosedAndNeverIndexesOlderText() {
        val timeline = snapshot(
            user("old", "t1", "clave vieja " + "a".repeat(31_900)),
            user("mid", "t2", "sin datos " + "b".repeat(31_900)),
            user("latest", "t3", "clave nueva " + "c".repeat(31_900)),
        )
        val result = FaroV5InChatSearchPolicy.search(timeline, "clave")
        assertFalse(result.complete)
        assertEquals(listOf(ConversationMessageId("latest")), result.messageIds)
    }
}
