package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkMutationAuthorityGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.CancellationException

/** No Reality outcome, raw receipt, source text, token or mutation port reaches the UI. */
internal sealed interface FaroV5BookmarkMutationOutcome {
    class Saved internal constructor(val revision: Long, val replayed: Boolean) : FaroV5BookmarkMutationOutcome
    data object Unavailable : FaroV5BookmarkMutationOutcome
    data object UnknownReconcile : FaroV5BookmarkMutationOutcome
    data class Stale(val revision: Long) : FaroV5BookmarkMutationOutcome
    data object IdentityConflict : FaroV5BookmarkMutationOutcome
    data class Rejected(val reason: BookmarkRejection) : FaroV5BookmarkMutationOutcome
}

/** Two-owner DEV bridge, dormant until a separately permitted human-confirmation UI mounts it. */
internal class FaroV5BookmarkMutationHostBinding(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val principal: String,
    private val expectedHostEpoch: String,
    private val hostEpoch: () -> String?,
    private val hostReady: () -> Boolean,
    private val authority: FaroV5LocalInstallBookmarkMutationAuthority?,
) : AutoCloseable {
    @Volatile private var closed = false
    private class Lease(val outcome: FaroV5BookmarkMutationOutcome.Saved, val current: () -> Boolean)
    @Volatile private var lease: Lease? = null
    init { require(principal.isNotBlank() && expectedHostEpoch.isNotBlank()); check(pointer.read() == pinned) }
    private fun live() = runCatching {
        !closed && hostReady() && pointer.read() == pinned && hostEpoch() == expectedHostEpoch
    }.getOrDefault(false)

    /** Target is only a consent pin, never evidence: the owner resolves persisted bookmark IDs itself. */
    suspend fun mutateAuthorized(request: BookmarkMutationRequest, consentTarget: BookmarkTarget?): FaroV5BookmarkMutationOutcome {
        lease = null
        if (!live()) return FaroV5BookmarkMutationOutcome.Unavailable
        val access = authority ?: return FaroV5BookmarkMutationOutcome.Unavailable
        val initial = access.epochOrNull(principal, request, consentTarget)
            ?: return FaroV5BookmarkMutationOutcome.Unavailable
        var enteredOwner = false
        return try {
            val before = workspace.bookmarkReadAdmission()
            fun current() = runCatching {
                live() && access.epochOrNull(principal, request, consentTarget) == initial &&
                    workspace.bookmarkReadAdmission() == before
            }.getOrDefault(false)
            if (!current()) return FaroV5BookmarkMutationOutcome.Unavailable
            val gate = FaroV5WorkspaceBookmarkMutationAuthorityGate(workspace, pinned, pointer,
                request, initial, { r, target -> access.epochOrNull(principal, r, target) }, ::current)
            enteredOwner = true
            val result = auxiliary.bookmarkMutatorForTrustedHost(gate).mutate(request)
            // Cross-owner atomicity is not claimed. A committed effect with lost authority is unknown.
            if (!current()) return FaroV5BookmarkMutationOutcome.UnknownReconcile
            when (result) {
                is BookmarkMutationResult.Saved -> FaroV5BookmarkMutationOutcome.Saved(result.revision, result.replayed).also {
                    lease = Lease(it, ::current)
                }
                is BookmarkMutationResult.Stale -> FaroV5BookmarkMutationOutcome.Stale(result.currentRevision)
                BookmarkMutationResult.Unavailable -> FaroV5BookmarkMutationOutcome.Unavailable
                BookmarkMutationResult.IdentityConflict -> FaroV5BookmarkMutationOutcome.IdentityConflict
                is BookmarkMutationResult.Rejected -> FaroV5BookmarkMutationOutcome.Rejected(result.reason)
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) {
            if (enteredOwner) FaroV5BookmarkMutationOutcome.UnknownReconcile else FaroV5BookmarkMutationOutcome.Unavailable
        }
    }

    /** Fresh Workspace check, not a cached bool. A delayed/replaced callback cannot announce success. */
    fun isResultCurrent(result: FaroV5BookmarkMutationOutcome.Saved): Boolean {
        val value = lease ?: return false
        return value.outcome === result && value.current()
    }
    override fun close() { closed = true; lease = null }
}
