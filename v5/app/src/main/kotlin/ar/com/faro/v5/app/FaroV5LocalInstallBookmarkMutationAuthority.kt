package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID

/** DEV dormant, memory-only exact-action consent. Constructing/resuming/reading grants nothing.
 * The trusted host calls grantAfterTrustedForegroundMutationAction only after human confirmation.
 * No host monitor is acquired from an Auxiliary/Workspace callback.
 */
internal class FaroV5LocalInstallBookmarkMutationAuthority(
    private val sandbox: FaroV5AndroidSandboxPrincipal,
    private val pointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
    private val foregroundReady: () -> Boolean,
) : AutoCloseable {
    private val baseline = sandbox.currentEvidenceOrNull()
    private val principal = baseline?.let { "local-install:" + it.sessionNonce }
    private data class Grant(val request: BookmarkMutationRequest, val target: BookmarkTarget?, val epoch: String)
    @Volatile private var grant: Grant? = null
    @Volatile private var poisoned = baseline == null
    @Volatile private var closed = false

    private fun live(): Boolean {
        if (closed || poisoned) return false
        val valid = runCatching {
            val original = baseline ?: return@runCatching false
            hostReady() && pointer.read() == original.pinnedGeneration &&
                sandbox.currentEvidenceOrNull() == original && hostReady()
        }.getOrDefault(false)
        if (!valid) { poisoned = true; grant = null; return false }
        if (!runCatching { foregroundReady() }.getOrDefault(false)) { grant = null; return false }
        return !closed && !poisoned
    }

    fun technicalPrincipalOrNull(): String? = if (live()) principal else null

    @Synchronized
    fun grantAfterTrustedForegroundMutationAction(
        request: BookmarkMutationRequest, target: BookmarkTarget?,
    ): Boolean {
        grant = null
        if (!live() || request.generationId != baseline?.pinnedGeneration?.generationId?.value) return false
        val action = request.mutation
        val needsSource = when (action) {
            is BookmarkMutation.Add, is BookmarkMutation.FavoriteSet,
            is BookmarkMutation.LifecycleSet, is BookmarkMutation.FolderAssign -> true
            else -> false
        }
        if (needsSource != (target != null)) return false
        if (target != null && (target.generationId != request.generationId ||
            target.authorityToken != request.authorityToken)) return false
        if (action is BookmarkMutation.Add && action.bookmark.target != target) return false
        val candidate = Grant(request, target, UUID.randomUUID().toString())
        grant = candidate
        if (!live() || grant !== candidate) return false
        return true
    }

    /** Exact source supplied by the owner under its mutex. Null is only valid for folder-only actions. */
    fun epochOrNull(principalId: String, request: BookmarkMutationRequest, target: BookmarkTarget?): String? {
        val before = grant ?: return null
        if (!live() || principalId != principal || before.request != request || before.target != target) return null
        return if (grant === before && !closed && !poisoned) before.epoch else null
    }

    @Synchronized fun revokeAll() { grant = null }
    @Synchronized override fun close() { closed = true; grant = null; poisoned = true }
}
