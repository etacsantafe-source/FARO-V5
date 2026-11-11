package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkAuthorityGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.CancellationException

/** Presentation metadata only. Source content and Workspace authority never leave the owner. */
internal data class FaroV5BookmarkHostItem(
    val id: BookmarkId,
    val conversationId: ConversationId,
    val messageId: ConversationMessageId,
    val folderId: BookmarkFolderId?,
    val favorite: Boolean,
)
internal data class FaroV5BookmarkHostPage(
    val items: List<FaroV5BookmarkHostItem>,
    val folders: List<BookmarkFolder>,
    val hasMore: Boolean,
)

/** Real two-owner READ binding; no mutation, grants, source navigation or account authentication. */
internal class FaroV5BookmarkReadHostBinding(
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val principalId: String,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val readAuthority: FaroV5LocalInstallReadAuthority? = null,
) : AutoCloseable {
    @Volatile private var closed = false
    private data class Epochs(val metadata: String, val message: String) {
        val combined get() = metadata + ":" + message
    }
    private data class Lease(val page: FaroV5BookmarkHostPage, val epochs: Epochs)
    @Volatile private var lease: Lease? = null

    init {
        require(principalId.isNotBlank() && expectedHostEpoch.isNotBlank())
        check(activePointer.read() == pinned) { "Guardados host cannot bind stale generation" }
    }

    private fun hostCurrent(): Boolean = runCatching {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun epochs(): Epochs? = runCatching {
        if (!hostCurrent()) return@runCatching null
        val metadata = readAuthority?.epochOrNull(
            principalId, FaroV5LocalReadDomain.BOOKMARK_METADATA, pinned,
        ) ?: return@runCatching null
        val message = readAuthority.epochOrNull(
            principalId, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, pinned,
        ) ?: return@runCatching null
        if (hostCurrent()) Epochs(metadata, message) else null
    }.getOrNull()

    /** Runs on the host worker. Null is unavailable, never a fabricated empty result. */
    suspend fun readAuthorized(cursor: BookmarkId? = null): FaroV5BookmarkHostPage? {
        lease = null
        val initial = epochs() ?: return null
        return try {
            val before = workspace.bookmarkReadAdmission()
            val gate = FaroV5WorkspaceBookmarkAuthorityGate(
                workspace, pinned, activePointer, initial.combined,
                { epochs()?.combined }, ::hostCurrent,
            )
            val result = auxiliary.bookmarkReaderForTrustedHost(gate).read(
                BookmarkReadRequest(pinned.generationId.value, before.authorityToken, cursor),
            )
            val available = result as? BookmarkReadResult.Available ?: return null
            val page = FaroV5BookmarkHostPage(
                items = available.page.bookmarks.map {
                    FaroV5BookmarkHostItem(it.id, it.target.conversationId,
                        it.target.messageId, it.folderId, it.favorite)
                },
                folders = available.page.folders,
                hasMore = available.page.hasMore,
            )
            if (workspace.bookmarkReadAdmission() != before || epochs() != initial ||
                !hostCurrent()
            ) return null
            lease = Lease(page, initial)
            page
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    /** Final UI publish fence: no SQL and no new grant. Page identity prevents stale callback reuse. */
    fun isPageCurrent(page: FaroV5BookmarkHostPage): Boolean {
        val current = lease ?: return false
        return current.page === page && hostCurrent() && epochs() == current.epochs
    }

    override fun close() {
        closed = true
        lease = null
    }
}
