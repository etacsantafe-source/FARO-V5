package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.RequestScopedBookmarkAuthorityGate
import ar.com.faro.v5.workspace.ConversationWorkspaceStore

/** One immutable invocation, never a global current-request variable. No UI or grant issuance. */
class FaroV5WorkspaceBookmarkMutationAuthorityGate(
    private val workspace: ConversationWorkspaceStore,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedRequest: BookmarkMutationRequest,
    private val expectedEpoch: String,
    private val requestEpoch: (BookmarkMutationRequest, BookmarkTarget?) -> String?,
    private val admitted: () -> Boolean,
) : RequestScopedBookmarkAuthorityGate {
    init { require(expectedEpoch.isNotBlank() && expectedEpoch.length <= 160) }
    private fun live() = runCatching { admitted() && pointer.read() == pinned }.getOrDefault(false)

    override fun isScopeActive(generationId: String, authorityToken: String): Boolean = runCatching {
        if (!live() || generationId != pinned.generationId.value ||
            generationId != expectedRequest.generationId || authorityToken != expectedRequest.authorityToken
        ) return@runCatching false
        val before = workspace.bookmarkReadAdmission()
        before == workspace.bookmarkReadAdmission() && before.authorityToken == authorityToken && live()
    }.getOrDefault(false)

    override fun isTargetAvailable(target: BookmarkTarget): Boolean = runCatching {
        if (!isScopeActive(target.generationId, target.authorityToken)) return@runCatching false
        val facts = workspace.bookmarkReadAdmission(target.conversationId, target.messageId)
        val after = workspace.bookmarkReadAdmission()
        facts.targetAvailable == true && facts.authorityToken == target.authorityToken &&
            after.authorityToken == facts.authorityToken && facts.ownerRevision == after.ownerRevision && live()
    }.getOrDefault(false)

    override fun isMutationAuthorized(request: BookmarkMutationRequest, resolvedTarget: BookmarkTarget?): Boolean =
        runCatching {
            if (request != expectedRequest || !isScopeActive(request.generationId, request.authorityToken) ||
                requestEpoch(request, resolvedTarget) != expectedEpoch
            ) return@runCatching false
            val sourceNeeded = when (request.mutation) {
                is BookmarkMutation.Add, is BookmarkMutation.FavoriteSet,
                is BookmarkMutation.LifecycleSet, is BookmarkMutation.FolderAssign -> true
                else -> false
            }
            if (sourceNeeded && (resolvedTarget == null || !isTargetAvailable(resolvedTarget)))
                return@runCatching false
            if (!sourceNeeded && resolvedTarget != null) return@runCatching false
            isScopeActive(request.generationId, request.authorityToken) &&
                requestEpoch(request, resolvedTarget) == expectedEpoch && live()
        }.getOrDefault(false)
}
