package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkAuthorityGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.surface.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only preparation and one-shot human consent. Tokens never cross the surface boundary. */
internal class FaroV5BookmarkReviewPreparationBinding(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val principal: String,
    private val expectedHostEpoch: String,
    private val hostEpoch: () -> String?,
    private val hostReady: () -> Boolean,
    private val readAuthority: FaroV5LocalInstallReadAuthority,
    private val mutationAuthority: FaroV5LocalInstallBookmarkMutationAuthority,
) : FaroV5BookmarkChatActions, AutoCloseable {
    @Volatile private var closed = false
    private val busy = AtomicBoolean(false)
    private class Prepared(val review: FaroV5BookmarkChatReview, val request: BookmarkMutationRequest,
        val target: BookmarkTarget, val current: () -> Boolean)
    @Volatile private var successCurrent: (() -> Boolean)? = null
    @Volatile private var prepared: Prepared? = null
    private val mutator = FaroV5BookmarkMutationHostBinding(pinned, pointer, workspace, auxiliary,
        principal, expectedHostEpoch, hostEpoch, hostReady, mutationAuthority)
    private fun live() = runCatching {
        !closed && hostReady() && pointer.read() == pinned && hostEpoch() == expectedHostEpoch
    }.getOrDefault(false)
    private fun epochs(): String? {
        if (!live()) return null
        val a = readAuthority.epochOrNull(principal, FaroV5LocalReadDomain.BOOKMARK_METADATA, pinned) ?: return null
        val b = readAuthority.epochOrNull(principal, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, pinned) ?: return null
        return if (live()) "$a:$b" else null
    }
    override suspend fun prepare(conversationId: ConversationId, messageId: ConversationMessageId,
        action: FaroV5BookmarkChatAction): FaroV5BookmarkChatReview? = withContext(Dispatchers.IO) {
        if (!busy.compareAndSet(false, true)) return@withContext null
        try {
            prepared = null
            successCurrent = null
            mutationAuthority.revokeAll()
            val initial = epochs() ?: return@withContext null
            val selection = workspace.snapshot()
            if (selection.workspace.activeConversationId != conversationId) return@withContext null
            val before = workspace.bookmarkReadAdmission(conversationId, messageId)
            if (selection.revision != before.ownerRevision) return@withContext null
            if (before.targetAvailable != true) return@withContext null
            val target = BookmarkTarget(conversationId, messageId, pinned.generationId.value, before.authorityToken)
            fun current() = runCatching {
                live() && epochs() == initial && workspace.snapshot() == selection &&
                    workspace.bookmarkReadAdmission(conversationId, messageId) == before
            }.getOrDefault(false)
            val gate = FaroV5WorkspaceBookmarkAuthorityGate(workspace, pinned, pointer, initial, ::epochs, ::current)
            val state = auxiliary.bookmarkReviewSnapshotForTrustedHost(gate, target) ?: return@withContext null
            if (!current()) return@withContext null
            val id = state.activeId
            val mutation: BookmarkMutation
            val label: String
            if (id == null) {
                mutation = BookmarkMutation.Add(BookmarkMetadata(BookmarkId(UUID.randomUUID().toString()), target,
                    favorite = action == FaroV5BookmarkChatAction.FAVORITE))
                label = if (action == FaroV5BookmarkChatAction.FAVORITE) "Guardar como favorito" else "Guardar mensaje"
            } else if (action == FaroV5BookmarkChatAction.FAVORITE) {
                mutation = BookmarkMutation.FavoriteSet(id, state.favorite != true)
                label = if (state.favorite == true) "Quitar favorito" else "Marcar favorito"
            } else {
                mutation = BookmarkMutation.LifecycleSet(id, BookmarkLifecycle.REMOVED)
                label = "Quitar de Guardados"
            }
            val review = FaroV5BookmarkChatReview(conversationId, messageId, label)
            val request = BookmarkMutationRequest(target.generationId, target.authorityToken,
                state.scopeRevision, IdempotencyKey(UUID.randomUUID().toString()), mutation)
            prepared = Prepared(review, request, target, ::current)
            if (current()) review else { prepared = null; null }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { prepared = null; null
        } finally { busy.set(false) }
    }
    override fun isCurrent(review: FaroV5BookmarkChatReview): Boolean {
        val value = prepared ?: return false
        return value.review === review && value.current()
    }
    override suspend fun confirm(review: FaroV5BookmarkChatReview): String {
        val outcome = withContext(Dispatchers.IO) {
        if (!busy.compareAndSet(false, true)) return@withContext "Acción en curso"
        try {
            val value = prepared
            prepared = null // consume BEFORE grant, double-tap/recomposition cannot repeat
            if (value == null || value.review !== review || !value.current()) return@withContext "Revisá nuevamente la acción"
            if (!mutationAuthority.grantAfterTrustedForegroundMutationAction(value.request, value.target))
                return@withContext "Guardados no disponible"
            when (val result = mutator.mutateAuthorized(value.request, value.target)) {
                is FaroV5BookmarkMutationOutcome.Saved -> if (mutator.isResultCurrent(result) && value.current()) {
                    successCurrent = value.current
                    "Guardados actualizado"
                } else "Resultado pendiente de verificar"
                is FaroV5BookmarkMutationOutcome.Stale -> "Guardados cambió. Revisá nuevamente la acción"
                FaroV5BookmarkMutationOutcome.UnknownReconcile -> "Resultado pendiente de verificar"
                FaroV5BookmarkMutationOutcome.IdentityConflict -> "Conflicto de identidad. Revisá nuevamente la acción"
                is FaroV5BookmarkMutationOutcome.Rejected -> "No se pudo completar la acción"
                FaroV5BookmarkMutationOutcome.Unavailable -> "Guardados no disponible"
            }
        } finally { mutationAuthority.revokeAll(); busy.set(false) }
        }
        return if (outcome == "Guardados actualizado" && successCurrent?.invoke() != true)
            "Resultado pendiente de verificar" else outcome
    }
    override fun cancel(review: FaroV5BookmarkChatReview) {
        if (prepared?.review === review) prepared = null
    }
    override fun close() { closed = true; prepared = null; mutator.close(); readAuthority.close(); mutationAuthority.close() }
}
