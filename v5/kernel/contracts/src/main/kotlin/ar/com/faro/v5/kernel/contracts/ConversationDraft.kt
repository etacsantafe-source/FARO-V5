package ar.com.faro.v5.kernel.contracts

/**
 * A draft belongs to the canonical Workspace authority, never to a screen, provider
 * or the Reality store. authorityToken changes after a generation/restore switch.
 */
data class ConversationDraftSnapshot(
    val conversationId: ConversationId,
    val authorityToken: String,
    val revision: Long,
    val text: String,
) {
    init {
        require(authorityToken.isNotBlank() && authorityToken.length <= 160 &&
            authorityToken.none { it.code < 32 || it.code == 127 }) {
            "draft authority token invalid"
        }
        require(revision >= 0) { "draft revision cannot be negative" }
        require(text.length <= ConversationDraftPolicy.MAX_TEXT_CHARS) {
            "draft exceeds supported text size"
        }
    }
}

/** Pure UI state. Its displayed text is NOT the authoritative persisted draft. */
data class ConversationDraftEditorState(
    val persisted: ConversationDraftSnapshot,
    val text: String = persisted.text,
    val conflict: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = text != persisted.text
    init {
        require(text.length <= ConversationDraftPolicy.MAX_TEXT_CHARS)
    }
}

data class ConversationDraftSaveRequest(
    val conversationId: ConversationId,
    val authorityToken: String,
    val expectedRevision: Long,
    val text: String,
    val idempotencyKey: IdempotencyKey,
)

sealed interface ConversationDraftWriteResult {
    data class Saved(
        val snapshot: ConversationDraftSnapshot,
        val replayed: Boolean,
    ) : ConversationDraftWriteResult
    data class Stale(val current: ConversationDraftSnapshot) : ConversationDraftWriteResult
    data object NotFound : ConversationDraftWriteResult
    data object IdentityConflict : ConversationDraftWriteResult
    data object Unavailable : ConversationDraftWriteResult
    /** Aggregate Workspace capacity reached; no draft or receipt was written. */
    data object CapacityExceeded : ConversationDraftWriteResult
}

/**
 * Implemented by the Workspace owner only. No screen is permitted to implement
 * a local-authority fallback. Null read means no existing, validated conversation.
 */
interface ConversationDraftPort {
    suspend fun read(conversationId: ConversationId): ConversationDraftSnapshot?
    suspend fun save(request: ConversationDraftSaveRequest): ConversationDraftWriteResult
}

/** Candidate ONLY: delivery/clear-on-commit is owned by the message timeline in S2.A3. */
data class ConversationDraftSendCandidate(
    val conversationId: ConversationId,
    val authorityToken: String,
    val draftRevision: Long,
    val text: String,
)

/**
 * Prevents stale loads/acks, cross-chat writes and sending content that was
 * never acknowledged by canonical Workspace. It does not perform persistence.
 */
object ConversationDraftPolicy {
    const val MAX_TEXT_CHARS = 32_000

    fun edit(state: ConversationDraftEditorState, value: String): ConversationDraftEditorState {
        require(value.length <= MAX_TEXT_CHARS) { "draft exceeds maximum length" }
        return state.copy(text = value)
    }

    fun reconcile(
        state: ConversationDraftEditorState,
        remote: ConversationDraftSnapshot,
    ): ConversationDraftEditorState {
        if (remote.conversationId != state.persisted.conversationId) return state
        if (remote.authorityToken != state.persisted.authorityToken) {
            return state.copy(conflict = true)
        }
        if (remote.revision < state.persisted.revision) return state
        // An unresolved conflict is latched until a deliberate user review.
        if (state.conflict) return state
        if (remote == state.persisted) return state
        // Equal revision with divergent bytes cannot be a valid authoritative update.
        if (remote.revision == state.persisted.revision ||
            state.hasUnsavedChanges
        ) return state.copy(conflict = true)
        return ConversationDraftEditorState(persisted = remote)
    }

    fun saveRequest(
        state: ConversationDraftEditorState,
        idempotencyKey: IdempotencyKey,
    ): ConversationDraftSaveRequest? {
        if (state.conflict || !state.hasUnsavedChanges) return null
        return ConversationDraftSaveRequest(
            conversationId = state.persisted.conversationId,
            authorityToken = state.persisted.authorityToken,
            expectedRevision = state.persisted.revision,
            text = state.text,
            idempotencyKey = idempotencyKey,
        )
    }

    /** Older responses never erase a newer local edit or a newer authority revision. */
    fun acknowledge(
        state: ConversationDraftEditorState,
        request: ConversationDraftSaveRequest,
        receipt: ConversationDraftSnapshot,
    ): ConversationDraftEditorState {
        val base = state.persisted
        if (state.conflict) return state
        if (request.conversationId != base.conversationId ||
            receipt.conversationId != base.conversationId ||
            request.authorityToken != base.authorityToken ||
            receipt.authorityToken != base.authorityToken ||
            request.expectedRevision != base.revision ||
            receipt.revision <= base.revision ||
            receipt.text != request.text
        ) return state.copy(conflict = true)
        return state.copy(persisted = receipt, conflict = false)
    }

    /**
     * Only an explicit user resolution may rebase local text onto a changed
     * authoritative snapshot. No silent overwrite after restore/conflict.
     */
    fun resolveAfterReview(
        state: ConversationDraftEditorState,
        latest: ConversationDraftSnapshot,
        keepLocalText: Boolean,
    ): ConversationDraftEditorState {
        if (latest.conversationId != state.persisted.conversationId ||
            (latest.authorityToken == state.persisted.authorityToken &&
             latest.revision < state.persisted.revision)
        ) return state
        return ConversationDraftEditorState(
            persisted = latest,
            text = if (keepLocalText) state.text else latest.text,
        )
    }

    fun sendCandidate(state: ConversationDraftEditorState): ConversationDraftSendCandidate? {
        if (state.conflict || state.hasUnsavedChanges || state.text.isBlank()) return null
        return ConversationDraftSendCandidate(
            conversationId = state.persisted.conversationId,
            authorityToken = state.persisted.authorityToken,
            draftRevision = state.persisted.revision,
            text = state.text,
        )
    }
}
