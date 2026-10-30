package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationTimelinePolicyTest {
    private fun id(s: String = "chat-a") = ConversationId(s)
    private fun empty(s: String = "chat-a") =
        ConversationTimelineSnapshot(id(s), "generation-1", 0, emptyList())
    private fun user(
        messageId: String = "user-1",
        turn: String = "turn-1",
        chat: String = "chat-a",
        text: String = "Hola",
        reply: String? = null,
    ) = ConversationMessage(
        ConversationMessageId(messageId), id(chat), ConversationTurnId(turn),
        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
        text, 1, reply?.let(::ConversationMessageId),
    )
    private fun faro(
        messageId: String = "faro-1",
        turn: String = "turn-1",
        chat: String = "chat-a",
    ) = ConversationMessage(
        ConversationMessageId(messageId), id(chat), ConversationTurnId(turn),
        ConversationMessageRole.FARO, ConversationMessageState.PROCESSING, "", 2,
    )
    private fun append(s: ConversationTimelineSnapshot, msg: ConversationMessage) =
        (ConversationTimelinePolicy.evaluate(
            s, ConversationTimelineIntent.AcceptUser(msg),
        ) as ConversationTimelineTransition.Accepted).let {
            s.copy(messages = it.messages)
        }
    private fun begin(s: ConversationTimelineSnapshot, msg: ConversationMessage) =
        (ConversationTimelinePolicy.evaluate(
            s, ConversationTimelineIntent.StartFaro(msg),
        ) as ConversationTimelineTransition.Accepted).let {
            s.copy(messages = it.messages)
        }

    @Test fun emptySnapshotDoesNotCreatePhantomMessage() {
        assertTrue(empty().messages.isEmpty())
        assertTrue(ConversationTimelinePolicy.contextWindow(empty()).isEmpty())
    }

    @Test fun userTurnIsExplicitlyAcceptedWithoutFabricatingAssistantResponse() {
        val next = append(empty(), user())
        assertEquals(1, next.messages.size)
        assertEquals(ConversationMessageState.ACCEPTED, next.messages.single().state)
    }

    @Test fun secondConversationCannotReceiveFirstConversationUserTurn() {
        val attempt = ConversationTimelinePolicy.evaluate(
            empty("chat-b"), ConversationTimelineIntent.AcceptUser(user())
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.WRONG_CONVERSATION
        ), attempt)
    }

    @Test fun repliesMustReferenceExistingEarlierMessageWithinChat() {
        val invalid = ConversationTimelinePolicy.evaluate(
            empty(), ConversationTimelineIntent.AcceptUser(user(reply = "foreign"))
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.INVALID_REPLY
        ), invalid)
        val first = append(empty(), user())
        val second = append(first, user("user-2", "turn-2", reply = "user-1"))
        assertEquals(ConversationMessageId("user-1"), second.messages.last().replyToMessageId)
    }

    @Test fun duplicateUserTurnRejectedEvenWithDifferentMessageIdentity() {
        val first = append(empty(), user())
        val result = ConversationTimelinePolicy.evaluate(
            first, ConversationTimelineIntent.AcceptUser(user("user-2"))
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.DUPLICATE_TURN
        ), result)
    }

    @Test fun assistantCannotStartWithoutOriginalUserTurn() {
        val result = ConversationTimelinePolicy.evaluate(
            empty(), ConversationTimelineIntent.StartFaro(faro())
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.MISSING_TURN
        ), result)
    }

    @Test fun processingStatusHasNoFakeAssistantText() {
        assertThrows(IllegalArgumentException::class.java) {
            faro().copy(text = "Procesando…")
        }
        val started = begin(append(empty(), user()), faro())
        assertEquals("", started.messages.last().text)
    }

    @Test fun sameTurnCanHaveOnlyOneAssistantIdentity() {
        val started = begin(append(empty(), user()), faro())
        val result = ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.StartFaro(faro("faro-other"))
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.DUPLICATE_TURN
        ), result)
    }

    @Test fun modelCompletionStaysLinkedToOriginalConversationAndTurn() {
        val started = begin(append(empty(), user()), faro())
        val result = ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("faro-1"), ConversationTurnId("turn-1"),
                ConversationMessageState.COMPLETED, "Respuesta",
            )
        ) as ConversationTimelineTransition.Accepted
        assertEquals(id(), result.changedMessage.conversationId)
        assertEquals(ConversationTurnId("turn-1"), result.changedMessage.turnId)
        assertEquals("Respuesta", result.changedMessage.text)
    }

    @Test fun delayedResultWithWrongTurnFailsClosed() {
        val started = begin(append(empty(), user()), faro())
        val result = ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("faro-1"), ConversationTurnId("turn-2"),
                ConversationMessageState.COMPLETED, "No corresponde",
            )
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.WRONG_TURN
        ), result)
    }

    @Test fun terminalResponseCannotSilentlyBecomeSuccessAgain() {
        val started = begin(append(empty(), user()), faro())
        val done = (ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("faro-1"), ConversationTurnId("turn-1"),
                ConversationMessageState.COMPLETED, "Listo",
            )
        ) as ConversationTimelineTransition.Accepted).let { started.copy(messages = it.messages) }
        val late = ConversationTimelinePolicy.evaluate(
            done, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("faro-1"), ConversationTurnId("turn-1"),
                ConversationMessageState.FAILED, "",
            )
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.INVALID_TRANSITION
        ), late)
    }

    @Test fun failureAndOfflineContainNoFabricatedModelAnswer() {
        assertThrows(IllegalArgumentException::class.java) {
            faro().copy(state = ConversationMessageState.OFFLINE, text = "respuesta")
        }
        assertThrows(IllegalArgumentException::class.java) {
            faro().copy(state = ConversationMessageState.FAILED, text = "respuesta")
        }
    }

    @Test fun partialOutputCannotBeConfusedWithCompleted() {
        val started = begin(append(empty(), user()), faro())
        val result = ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("faro-1"), ConversationTurnId("turn-1"),
                ConversationMessageState.PARTIAL, "Alcancé a verificar",
            )
        ) as ConversationTimelineTransition.Accepted
        assertEquals(ConversationMessageState.PARTIAL, result.changedMessage.state)
    }

    @Test fun snapshotRejectsAssistantBeforeUserOrForeignChat() {
        assertThrows(IllegalArgumentException::class.java) {
            empty().copy(messages = listOf(faro()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            empty().copy(messages = listOf(user(chat = "chat-b")))
        }
    }

    @Test fun contextIgnoresUnconfirmedAssistantStatusAndNeverInventsReply() {
        val started = begin(append(empty(), user()), faro())
        assertEquals(listOf("Hola"), ConversationTimelinePolicy.contextWindow(started).map { it.text })
    }

    @Test fun contextBoundedByCountAndCharactersIndependentOfProvider() {
        var snapshot = empty()
        repeat(25) { i ->
            snapshot = append(snapshot, user("u-$i", "t-$i", text = "x".repeat(200)))
        }
        val context = ConversationTimelinePolicy.contextWindow(snapshot)
        assertEquals(16, context.size)
        assertEquals("u-9", context.first().id.value)
        assertEquals(3200, context.sumOf { it.text.length })
    }

    @Test fun missingMessageAndInvalidRoleAreExplicitRejections() {
        val started = append(empty(), user())
        val missing = ConversationTimelinePolicy.evaluate(
            started, ConversationTimelineIntent.ResolveFaro(
                ConversationMessageId("missing"), ConversationTurnId("turn-1"),
                ConversationMessageState.COMPLETED, "x",
            )
        )
        assertEquals(ConversationTimelineTransition.Rejected(
            ConversationTimelineRejection.MISSING_RESPONSE
        ), missing)
        assertFalse(started.messages.any { it.role == ConversationMessageRole.FARO })
    }
}
