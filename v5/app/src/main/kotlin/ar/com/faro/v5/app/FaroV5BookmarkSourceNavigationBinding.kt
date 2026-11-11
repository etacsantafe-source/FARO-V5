package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkAuthorityGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.surface.FaroV5BookmarkSourceTarget
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/** One explicit ACTIVE-page click. No text, tokens, SQL door or new owner is exported. */
internal class FaroV5BookmarkSourceNavigationBinding(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val reader: FaroV5BookmarkReadHostBinding,
    private val authority: FaroV5LocalInstallReadAuthority,
    private val principal: String,
    private val live: () -> Boolean,
    private val selection: ConversationWorkspaceSelectionPort,
    private val workspaceRead: ConversationWorkspaceReadPort,
    private val timelineRead: ConversationTimelineReadPort,
) : AutoCloseable {
    private val consumed = AtomicBoolean(false)
    @Volatile private var closed = false
    private class Lease(val target: FaroV5BookmarkSourceTarget, val current: () -> Boolean)
    @Volatile private var lease: Lease? = null
    private fun epochs(): String? = runCatching {
        if (closed || !live() || pointer.read() != pinned) return@runCatching null
        val metadata = authority.epochOrNull(principal, FaroV5LocalReadDomain.BOOKMARK_METADATA, pinned)
            ?: return@runCatching null
        val message = authority.epochOrNull(principal, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, pinned)
            ?: return@runCatching null
        "$metadata:$message"
    }.getOrNull()

    /** Worker only. Selection acknowledgement alone never authorizes opening a cached chat. */
    suspend fun open(page: FaroV5BookmarkHostPage, item: FaroV5BookmarkHostItem): FaroV5BookmarkSourceTarget? {
        if (!consumed.compareAndSet(false, true)) return null
        return try {
            val initial = epochs() ?: return null
            // Object membership: reconstructed metadata from another page cannot authorize a click.
            fun current() = epochs() == initial && reader.isPageCurrent(page)
            if (!current() || page.items.none { it === item }) return null
            val before = workspace.bookmarkReadAdmission(item.conversationId, item.messageId)
            if (before.targetAvailable != true) return null
            val target = BookmarkTarget(item.conversationId, item.messageId, pinned.generationId.value, before.authorityToken)
            val gate = FaroV5WorkspaceBookmarkAuthorityGate(workspace, pinned, pointer, initial, ::epochs, ::current)
            val saved = auxiliary.bookmarkReviewSnapshotForTrustedHost(gate, target) ?: return null
            if (saved.activeId != item.id || !current() ||
                workspace.bookmarkReadAdmission(item.conversationId, item.messageId) != before) return null
            if (selection.select(item.conversationId, IdempotencyKey(UUID.randomUUID().toString())) !is
                ConversationSelectionResult.Saved) return null
            // Selection may legitimately advance Workspace revision: establish NEW fences.
            val after = workspace.bookmarkReadAdmission(item.conversationId, item.messageId)
            if (!current() || after.targetAvailable != true || after.authorityToken != before.authorityToken) return null
            val selected = workspaceRead.readSnapshot()
            val timeline = timelineRead.readTimeline(item.conversationId) ?: return null
            if (selected.activeConversationId != item.conversationId ||
                timeline.conversationId != item.conversationId || timeline.authorityToken != after.authorityToken ||
                timeline.messages.none { it.id == item.messageId && it.conversationId == item.conversationId }) return null
            fun publishCurrent() = runCatching {
                current() && workspace.bookmarkReadAdmission(item.conversationId, item.messageId) == after &&
                    workspace.snapshot().workspace == selected
            }.getOrDefault(false)
            // Detect remove/re-add (different bookmark ID) and concurrent Auxiliary revisions.
            if (auxiliary.bookmarkReviewSnapshotForTrustedHost(gate, target) != saved || !publishCurrent() ||
                workspaceRead.readSnapshot() != selected || timelineRead.readTimeline(item.conversationId) != timeline ||
                !publishCurrent()) return null
            FaroV5BookmarkSourceTarget(item.conversationId, item.messageId).also { lease = Lease(it, ::publishCurrent) }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { null }
    }
    fun isCurrent(target: FaroV5BookmarkSourceTarget): Boolean {
        val value = lease ?: return false
        return value.target === target && value.current()
    }
    override fun close() { closed = true; lease = null }
}
