package ar.com.faro.v5.kernel.contracts

@JvmInline
value class ConversationMessageId(val value: String) {
    init { require(value.isNotBlank() && value.length <= 128 && value.none { it.code < 32 || it.code == 127 }) }
}

@JvmInline
value class ConversationTurnId(val value: String) {
    init { require(value.isNotBlank() && value.length <= 128 && value.none { it.code < 32 || it.code == 127 }) }
}

enum class ConversationMessageRole { USER, FARO }

/** Processing is a status only, never synthetic assistant text. */
enum class ConversationMessageState {
    ACCEPTED, PROCESSING, COMPLETED, PARTIAL, FAILED, OFFLINE
}

data class ConversationMessage(
    val id: ConversationMessageId,
    val conversationId: ConversationId,
    val turnId: ConversationTurnId,
    val role: ConversationMessageRole,
    val state: ConversationMessageState,
    val text: String,
    val createdAtEpochMillis: Long,
    val replyToMessageId: ConversationMessageId? = null,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(text.length <= ConversationTimelinePolicy.MAX_MESSAGE_CHARS)
        if (role == ConversationMessageRole.USER) {
            require(state == ConversationMessageState.ACCEPTED && text.isNotBlank()) {
                "user messages are committed only after acceptance"
            }
        } else {
            require(replyToMessageId == null) { "assistant reply is linked by turn, not user UI quote" }
            require(state != ConversationMessageState.ACCEPTED)
            when (state) {
                ConversationMessageState.PROCESSING, ConversationMessageState.FAILED,
                ConversationMessageState.OFFLINE -> require(text.isEmpty()) {
                    "status/failure must not forge assistant content"
                }
                ConversationMessageState.COMPLETED, ConversationMessageState.PARTIAL ->
                    require(text.isNotBlank()) { "assistant content must not be empty" }
                else -> error("unreachable assistant state")
            }
        }
    }
}

/** Snapshot supplied by Workspace owner. No active tab or provider is inferred. */
data class ConversationTimelineSnapshot(
    val conversationId: ConversationId,
    val authorityToken: String,
    val revision: Long,
    val messages: List<ConversationMessage>,
) {
    init {
        require(authorityToken.isNotBlank() && authorityToken.length <= 160)
        require(revision >= 0)
        require(messages.size <= ConversationTimelinePolicy.MAX_MESSAGES_PER_CHAT)
        require(messages.all { it.conversationId == conversationId })
        require(messages.map { it.id }.distinct().size == messages.size) { "duplicate message ids" }
        val seen = mutableMapOf<ConversationMessageId, ConversationMessage>()
        val userTurns = mutableSetOf<ConversationTurnId>()
        val faroTurns = mutableSetOf<ConversationTurnId>()
        messages.forEach { message ->
            if (message.role == ConversationMessageRole.USER) {
                require(userTurns.add(message.turnId)) { "duplicate user turn" }
                require(message.replyToMessageId == null ||
                    message.replyToMessageId in seen) { "reply must target an earlier same-chat message" }
            } else {
                require(message.turnId in userTurns && faroTurns.add(message.turnId)) {
                    "assistant must belong to one preceding user turn"
                }
            }
            seen[message.id] = message
        }
    }
}

sealed interface ConversationTimelineIntent {
    data class AcceptUser(val message: ConversationMessage) : ConversationTimelineIntent
    data class StartFaro(val message: ConversationMessage) : ConversationTimelineIntent
    data class ResolveFaro(
        val messageId: ConversationMessageId,
        val turnId: ConversationTurnId,
        val outcome: ConversationMessageState,
        val text: String,
    ) : ConversationTimelineIntent
}

data class ConversationTimelineWriteRequest(
    val conversationId: ConversationId,
    val authorityToken: String,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
    val intent: ConversationTimelineIntent,
)

enum class ConversationTimelineRejection {
    WRONG_CONVERSATION, INVALID_ROLE, MISSING_TURN, DUPLICATE_MESSAGE,
    DUPLICATE_TURN, INVALID_REPLY, MISSING_RESPONSE, WRONG_TURN,
    INVALID_TRANSITION, TOO_MANY_MESSAGES
}

sealed interface ConversationTimelineTransition {
    data class Accepted(
        val messages: List<ConversationMessage>,
        val changedMessage: ConversationMessage,
    ) : ConversationTimelineTransition
    data class Rejected(val reason: ConversationTimelineRejection) : ConversationTimelineTransition
}

sealed interface ConversationTimelineWriteResult {
    data class Saved(val snapshot: ConversationTimelineSnapshot, val replayed: Boolean) :
        ConversationTimelineWriteResult
    data class Stale(val current: ConversationTimelineSnapshot) : ConversationTimelineWriteResult
    data object NotFound : ConversationTimelineWriteResult
    data object IdentityConflict : ConversationTimelineWriteResult
    data object Unavailable : ConversationTimelineWriteResult
    data class Rejected(val reason: ConversationTimelineRejection) : ConversationTimelineWriteResult
}

/** Read-only message timeline contract for the Chat surface; never exposes Apply or writes. */
fun interface ConversationTimelineReadPort {
    suspend fun readTimeline(conversationId: ConversationId): ConversationTimelineSnapshot?
}

interface ConversationTimelinePort {
    suspend fun readTimeline(conversationId: ConversationId): ConversationTimelineSnapshot?
    suspend fun writeTimeline(request: ConversationTimelineWriteRequest): ConversationTimelineWriteResult
}

/** Pure, deterministic identity/reply/lifecycle gate. Storage must enforce CAS and idempotency. */
object ConversationTimelinePolicy {
    const val MAX_MESSAGE_CHARS = 32_000
    const val MAX_MESSAGES_PER_CHAT = 100_000
    const val MAX_CONTEXT_MESSAGES = 16
    const val MAX_CONTEXT_CHARS = 12_000

    fun evaluate(
        current: ConversationTimelineSnapshot,
        intent: ConversationTimelineIntent,
    ): ConversationTimelineTransition {
        when (intent) {
            is ConversationTimelineIntent.AcceptUser -> {
                val message = intent.message
                if (message.conversationId != current.conversationId)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.WRONG_CONVERSATION)
                if (message.role != ConversationMessageRole.USER)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.INVALID_ROLE)
                if (current.messages.any { it.id == message.id })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.DUPLICATE_MESSAGE)
                if (current.messages.any { it.turnId == message.turnId && it.role == ConversationMessageRole.USER })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.DUPLICATE_TURN)
                if (message.replyToMessageId != null &&
                    current.messages.none { it.id == message.replyToMessageId })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.INVALID_REPLY)
                return append(current, message)
            }
            is ConversationTimelineIntent.StartFaro -> {
                val message = intent.message
                if (message.conversationId != current.conversationId)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.WRONG_CONVERSATION)
                if (message.role != ConversationMessageRole.FARO ||
                    message.state != ConversationMessageState.PROCESSING)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.INVALID_ROLE)
                if (current.messages.any { it.id == message.id })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.DUPLICATE_MESSAGE)
                if (current.messages.none { it.role == ConversationMessageRole.USER &&
                    it.turnId == message.turnId })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.MISSING_TURN)
                if (current.messages.any { it.role == ConversationMessageRole.FARO &&
                    it.turnId == message.turnId })
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.DUPLICATE_TURN)
                return append(current, message)
            }
            is ConversationTimelineIntent.ResolveFaro -> {
                val target = current.messages.firstOrNull { it.id == intent.messageId }
                    ?: return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.MISSING_RESPONSE)
                if (target.role != ConversationMessageRole.FARO)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.INVALID_ROLE)
                if (target.turnId != intent.turnId)
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.WRONG_TURN)
                if (target.state != ConversationMessageState.PROCESSING ||
                    intent.outcome !in setOf(
                        ConversationMessageState.COMPLETED, ConversationMessageState.PARTIAL,
                        ConversationMessageState.FAILED, ConversationMessageState.OFFLINE
                    ))
                    return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.INVALID_TRANSITION)
                val settled = target.copy(state = intent.outcome, text = intent.text)
                return ConversationTimelineTransition.Accepted(
                    current.messages.map { if (it.id == target.id) settled else it },
                    settled,
                )
            }
        }
    }

    private fun append(
        current: ConversationTimelineSnapshot,
        message: ConversationMessage,
    ): ConversationTimelineTransition {
        if (current.messages.size >= MAX_MESSAGES_PER_CHAT)
            return ConversationTimelineTransition.Rejected(ConversationTimelineRejection.TOO_MANY_MESSAGES)
        return ConversationTimelineTransition.Accepted(current.messages + message, message)
    }

    /**
     * Deterministic and bounded. Only accepted user turns and completed assistant
     * text are candidates; failed, offline, partial and processing never get
     * silently represented as successful context. Caller still MUST apply
     * ContextAssembler and DataEgressPolicy before any model invocation.
     */
    fun contextWindow(snapshot: ConversationTimelineSnapshot): List<ConversationMessage> {
        val eligible = snapshot.messages.filter {
            it.state == ConversationMessageState.ACCEPTED ||
                it.state == ConversationMessageState.COMPLETED
        }
        val selectedReversed = mutableListOf<ConversationMessage>()
        var characters = 0
        for (message in eligible.asReversed()) {
            if (selectedReversed.size == MAX_CONTEXT_MESSAGES ||
                characters + message.text.length > MAX_CONTEXT_CHARS) break
            selectedReversed += message
            characters += message.text.length
        }
        return selectedReversed.asReversed()
    }
}
