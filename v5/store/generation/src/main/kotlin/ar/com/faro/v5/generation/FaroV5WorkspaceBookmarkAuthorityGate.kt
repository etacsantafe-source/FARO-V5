package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.BookmarkTarget
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.BookmarkAuthorityGate
import ar.com.faro.v5.workspace.ConversationWorkspaceStore

/**
 * Workspace-backed READ gate. The trusted host must separately issue a foreground
 * grant and pin a verified generation. This class never issues grants, mounts UI,
 * authenticates an Android UID or permits mutation (default false is retained).
 */
class FaroV5WorkspaceBookmarkAuthorityGate(
    private val workspace: ConversationWorkspaceStore,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedReadGrantEpoch: String,
    private val readGrantEpoch: () -> String?,
    private val admitted: () -> Boolean,
) : BookmarkAuthorityGate {
    init { require(expectedReadGrantEpoch.isNotBlank() && expectedReadGrantEpoch.length <= 160) }

    private fun live(): Boolean = runCatching {
        admitted() && pointer.read() == pinned && readGrantEpoch() == expectedReadGrantEpoch
    }.getOrDefault(false)

    override fun isScopeActive(generationId: String, authorityToken: String): Boolean = runCatching {
        if (generationId != pinned.generationId.value || !live()) return@runCatching false
        val before = workspace.bookmarkReadAdmission()
        val after = workspace.bookmarkReadAdmission()
        before == after && before.authorityToken == authorityToken && live()
    }.getOrDefault(false)

    override fun isTargetAvailable(target: BookmarkTarget): Boolean = runCatching {
        if (!isScopeActive(target.generationId, target.authorityToken)) return@runCatching false
        val facts = workspace.bookmarkReadAdmission(target.conversationId, target.messageId)
        val after = workspace.bookmarkReadAdmission()
        facts.targetAvailable == true && facts.authorityToken == target.authorityToken &&
            after.authorityToken == facts.authorityToken && after.ownerRevision == facts.ownerRevision && live()
    }.getOrDefault(false)
}
