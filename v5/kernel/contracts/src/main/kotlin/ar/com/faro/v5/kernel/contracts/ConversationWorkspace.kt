package ar.com.faro.v5.kernel.contracts

/**
 * Stable identity of a user-facing conversation, independent of its selected screen.
 * It is not an operation ID, Apply ID or a legacy V4 chat-main identifier.
 */
@JvmInline
value class ConversationId(val value: String) {
    init {
        require(value.isNotBlank()) { "conversation id must not be blank" }
    }
}

/**
 * Read-only metadata supplied by the canonical Workspace owner.
 * Pending review is a derived, authoritative state: the surface must not invent it.
 */
data class ConversationWorkspaceEntry(
    val id: ConversationId,
    val title: String,
    val lastActivityEpochMillis: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hasPendingReview: Boolean = false,
) {
    init {
        require(lastActivityEpochMillis >= 0) {
            "last activity must not be negative"
        }
    }
}

/**
 * A snapshot, not a persistence engine. No conversation is created by reading it.
 * An active ID may be stale after recovery; projection then fails closed to no selection.
 */
data class ConversationWorkspaceSnapshot(
    val conversations: List<ConversationWorkspaceEntry>,
    val activeConversationId: ConversationId?,
) {
    init {
        require(conversations.map { it.id }.toSet().size == conversations.size) {
            "conversation identities must be unique"
        }
    }
}

/**
 * Only the composition root may bind this to a real Workspace read authority.
 * No mutation, storage, model provider or Android dependency is exposed.
 */
fun interface ConversationWorkspaceReadPort {
    suspend fun readSnapshot(): ConversationWorkspaceSnapshot
}

/** Pure drawer projection. Archive is reversible and never treated as delete. */
data class ConversationWorkspaceDrawerProjection(
    val pinned: List<ConversationWorkspaceEntry>,
    val recent: List<ConversationWorkspaceEntry>,
    val older: List<ConversationWorkspaceEntry>,
    val archived: List<ConversationWorkspaceEntry>,
    val activeConversationId: ConversationId?,
) {
    val hasMore: Boolean get() = older.isNotEmpty() || archived.isNotEmpty()
    val allConversations: List<ConversationWorkspaceEntry>
        get() = pinned + recent + older + archived
}

/**
 * V4.4 UX parity without V4 storage/runtime: up to seven unpinned recent,
 * pinned outside the quota, archived recoverable via all-conversations.
 */
object ConversationWorkspaceProjector {
    const val RECENT_LIMIT: Int = 7

    fun project(snapshot: ConversationWorkspaceSnapshot): ConversationWorkspaceDrawerProjection {
        val activityOrder =
            compareByDescending<ConversationWorkspaceEntry> { it.lastActivityEpochMillis }
                .thenBy { it.id.value }
        val unarchived = snapshot.conversations.filterNot { it.archived }
        val pinned = unarchived.filter { it.pinned }.sortedWith(activityOrder)
        val ordinary = unarchived.filterNot { it.pinned }.sortedWith(activityOrder)
        val active =
            snapshot.activeConversationId?.takeIf { id ->
                unarchived.any { it.id == id }
            }
        return ConversationWorkspaceDrawerProjection(
            pinned = pinned,
            recent = ordinary.take(RECENT_LIMIT),
            older = ordinary.drop(RECENT_LIMIT),
            archived = snapshot.conversations.filter { it.archived }.sortedWith(activityOrder),
            activeConversationId = active,
        )
    }
}

/** Workspace mutations only through the active-generation host and shared gate. */
sealed interface ConversationSelectionResult {
    data class Saved(val snapshot: ConversationWorkspaceSnapshot, val replayed: Boolean) : ConversationSelectionResult
    data class Stale(val snapshot: ConversationWorkspaceSnapshot) : ConversationSelectionResult
    data object Rejected : ConversationSelectionResult
    data object IdentityConflict : ConversationSelectionResult
}
interface ConversationWorkspaceSelectionPort {
    suspend fun createAndSelect(id: ConversationId, atMillis: Long, key: IdempotencyKey): ConversationSelectionResult
    suspend fun select(id: ConversationId, key: IdempotencyKey): ConversationSelectionResult
}
