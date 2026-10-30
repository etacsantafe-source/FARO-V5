package ar.com.faro.v5.kernel.contracts

/**
 * Pure policy for existing conversation metadata. This is NOT a persistence writer,
 * OperationContract executor or Apply authorization. The canonical Workspace owner
 * must re-read current state and enforce in-flight/receipt guards atomically.
 */
sealed interface ConversationWorkspaceIntent {
    val conversationId: ConversationId

    data class Activate(override val conversationId: ConversationId) : ConversationWorkspaceIntent

    data class Rename(
        override val conversationId: ConversationId,
        val title: String,
    ) : ConversationWorkspaceIntent

    data class SetPinned(
        override val conversationId: ConversationId,
        val pinned: Boolean,
    ) : ConversationWorkspaceIntent

    data class SetArchived(
        override val conversationId: ConversationId,
        val archived: Boolean,
    ) : ConversationWorkspaceIntent
}

enum class ConversationWorkspaceRejection {
    NOT_FOUND,
    ARCHIVED,
    INVALID_TITLE,
    PENDING_REVIEW,
}

sealed interface ConversationWorkspaceTransition {
    data class Accepted(
        val snapshot: ConversationWorkspaceSnapshot,
        val changed: Boolean,
    ) : ConversationWorkspaceTransition

    data class Rejected(
        val reason: ConversationWorkspaceRejection,
    ) : ConversationWorkspaceTransition
}

/**
 * V4.4 parity for metadata with V5 fail-closed active selection.
 * NO implicit creation, auto-selection, delete, Apply, IO or state authority.
 */
object ConversationWorkspaceTransitions {
    const val MAX_TITLE_CHARS: Int = 80

    fun evaluate(
        current: ConversationWorkspaceSnapshot,
        intent: ConversationWorkspaceIntent,
    ): ConversationWorkspaceTransition {
        val existing =
            current.conversations.firstOrNull { it.id == intent.conversationId }
                ?: return ConversationWorkspaceTransition.Rejected(
                    ConversationWorkspaceRejection.NOT_FOUND
                )
        val next =
            when (intent) {
                is ConversationWorkspaceIntent.Activate -> {
                    if (existing.archived) {
                        return ConversationWorkspaceTransition.Rejected(
                            ConversationWorkspaceRejection.ARCHIVED
                        )
                    }
                    return accept(current, current.conversations, existing.id)
                }
                is ConversationWorkspaceIntent.Rename -> {
                    val title = intent.title.trim()
                    if (
                        title.isEmpty() ||
                        title.length > MAX_TITLE_CHARS ||
                        title.any { it.code < 32 || it.code == 127 }
                    ) {
                        return ConversationWorkspaceTransition.Rejected(
                            ConversationWorkspaceRejection.INVALID_TITLE
                        )
                    }
                    existing.copy(title = title)
                }
                is ConversationWorkspaceIntent.SetPinned -> {
                    if (existing.archived && intent.pinned) {
                        return ConversationWorkspaceTransition.Rejected(
                            ConversationWorkspaceRejection.ARCHIVED
                        )
                    }
                    existing.copy(pinned = intent.pinned)
                }
                is ConversationWorkspaceIntent.SetArchived -> {
                    if (intent.archived && !existing.archived && existing.hasPendingReview) {
                        return ConversationWorkspaceTransition.Rejected(
                            ConversationWorkspaceRejection.PENDING_REVIEW
                        )
                    }
                    existing.copy(
                        archived = intent.archived,
                        pinned = if (intent.archived) false else existing.pinned,
                    )
                }
            }

        val conversations = current.conversations.map { item ->
            if (item.id == existing.id) next else item
        }
        val active =
            if (next.archived && current.activeConversationId == existing.id) {
                null
            } else {
                current.activeConversationId
            }
        return accept(current, conversations, active)
    }

    private fun accept(
        current: ConversationWorkspaceSnapshot,
        conversations: List<ConversationWorkspaceEntry>,
        active: ConversationId?,
    ): ConversationWorkspaceTransition.Accepted {
        val next = ConversationWorkspaceSnapshot(conversations.toList(), active)
        return ConversationWorkspaceTransition.Accepted(
            snapshot = next,
            changed = next != current,
        )
    }
}
