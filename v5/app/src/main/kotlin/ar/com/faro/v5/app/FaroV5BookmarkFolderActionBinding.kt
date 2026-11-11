package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceBookmarkAuthorityGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Literal metadata-only review. Identity, rather than its fields, authorizes confirmation. */
internal class FaroV5BookmarkFolderReview(val label: String)

/** DEV action adapter; read and mutation grants remain distinct. No grant is issued by prepare. */
internal class FaroV5BookmarkFolderActionBinding(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: ConversationWorkspaceStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val principal: String,
    private val expectedEpoch: String,
    private val epoch: () -> String?,
    private val ready: () -> Boolean,
    private val reads: FaroV5LocalInstallReadAuthority,
    private val mutations: FaroV5LocalInstallBookmarkMutationAuthority,
) : AutoCloseable {
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    private class Prepared(val review: FaroV5BookmarkFolderReview, val request: BookmarkMutationRequest,
        val target: BookmarkTarget?, val current: () -> Boolean)
    @Volatile private var prepared: Prepared? = null
    @Volatile private var successCurrent: (() -> Boolean)? = null
    fun isSuccessCurrent(): Boolean = successCurrent?.invoke() == true
    private val mutator = FaroV5BookmarkMutationHostBinding(pinned, pointer, workspace, auxiliary,
        principal, expectedEpoch, epoch, ready, mutations)
    private fun live() = runCatching { !closed && ready() && pointer.read() == pinned && epoch() == expectedEpoch }.getOrDefault(false)
    private fun epochs(): String? {
        if (!live()) return null
        val a = reads.epochOrNull(principal, FaroV5LocalReadDomain.BOOKMARK_METADATA, pinned) ?: return null
        val b = reads.epochOrNull(principal, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, pinned) ?: return null
        return if (live()) "$a:$b" else null
    }
    suspend fun prepare(mutation: BookmarkMutation, item: FaroV5BookmarkHostItem? = null): FaroV5BookmarkFolderReview? = withContext(Dispatchers.IO) {
        if (!busy.compareAndSet(false, true)) return@withContext null
        try {
            prepared = null
            successCurrent = null
            mutations.revokeAll()
            val initial = epochs() ?: return@withContext null
            val before = workspace.bookmarkReadAdmission()
            fun current() = runCatching { live() && epochs() == initial && workspace.bookmarkReadAdmission() == before }.getOrDefault(false)
            val gate = FaroV5WorkspaceBookmarkAuthorityGate(workspace, pinned, pointer, initial, ::epochs, ::current)
            val folders = auxiliary.bookmarkFoldersForTrustedHost(gate, pinned.generationId.value, before.authorityToken)
                ?: return@withContext null
            var target: BookmarkTarget? = null
            var revision = folders.scopeRevision
            val label: String
            when (mutation) {
                is BookmarkMutation.LifecycleSet, is BookmarkMutation.FavoriteSet, is BookmarkMutation.FolderAssign -> {
                    val id = when (mutation) {
                        is BookmarkMutation.LifecycleSet -> mutation.id
                        is BookmarkMutation.FavoriteSet -> mutation.id
                        is BookmarkMutation.FolderAssign -> mutation.id
                        else -> error("unreachable")
                    }
                    if (mutation is BookmarkMutation.LifecycleSet && mutation.lifecycle == BookmarkLifecycle.ACTIVE) {
                        val result = auxiliary.bookmarkRemovedByIdForTrustedHost(gate,
                            BookmarkReadRequest(pinned.generationId.value, before.authorityToken), id)
                            ?: return@withContext null
                        val candidate = result.items.singleOrNull() ?: return@withContext null
                        target = BookmarkTarget(candidate.conversationId, candidate.messageId, pinned.generationId.value, before.authorityToken)
                        revision = candidate.scopeRevision
                        label = "Restaurar referencia quitada"
                    } else {
                        if (item == null || item.id != id) return@withContext null
                        val exactTarget = BookmarkTarget(item.conversationId, item.messageId, pinned.generationId.value, before.authorityToken)
                        val state = auxiliary.bookmarkReviewSnapshotForTrustedHost(gate, exactTarget) ?: return@withContext null
                        if (state.activeId != id) return@withContext null
                        target = exactTarget
                        revision = state.scopeRevision
                        label = when (mutation) {
                            is BookmarkMutation.LifecycleSet -> "Quitar referencia de Guardados"
                            is BookmarkMutation.FavoriteSet -> if (mutation.favorite) "Marcar favorito" else "Quitar favorito"
                            is BookmarkMutation.FolderAssign -> if (mutation.folderId == null) "Mover referencia a raíz" else {
                                val folder = folders.folders.singleOrNull { it.id == mutation.folderId } ?: return@withContext null
                                "Mover referencia a «${folder.name}»"
                            }
                            else -> error("unreachable")
                        }
                    }
                }
                is BookmarkMutation.FolderCreate -> { label = "Crear carpeta «${mutation.folder.name}»" }
                is BookmarkMutation.FolderRename -> {
                    val folder = folders.folders.singleOrNull { it.id == mutation.id } ?: return@withContext null
                    label = "Renombrar «${folder.name}» a «${mutation.name}»"
                }
                is BookmarkMutation.FolderDelete -> {
                    val folder = folders.folders.singleOrNull { it.id == mutation.id } ?: return@withContext null
                    label = "Eliminar carpeta vacía «${folder.name}»"
                }
                is BookmarkMutation.Add -> return@withContext null // M02 owns Add.
            }
            if (!current() || revision != folders.scopeRevision) return@withContext null
            val review = FaroV5BookmarkFolderReview(label)
            val request = BookmarkMutationRequest(pinned.generationId.value, before.authorityToken, revision,
                IdempotencyKey(UUID.randomUUID().toString()), mutation)
            prepared = Prepared(review, request, target, ::current)
            if (current()) review else { prepared = null; null }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { null
        } finally { busy.set(false) }
    }
    fun isCurrent(review: FaroV5BookmarkFolderReview): Boolean = prepared?.let { it.review === review && it.current() } == true
    suspend fun confirm(review: FaroV5BookmarkFolderReview): String = withContext(Dispatchers.IO) {
        if (!busy.compareAndSet(false, true)) return@withContext "Acción en curso"
        try {
            val value = prepared
            prepared = null
            if (value == null || value.review !== review || !value.current()) return@withContext "Revisá nuevamente la acción"
            if (!mutations.grantAfterTrustedForegroundMutationAction(value.request, value.target)) return@withContext "Guardados no disponible"
            when (val result = mutator.mutateAuthorized(value.request, value.target)) {
                is FaroV5BookmarkMutationOutcome.Saved -> if (mutator.isResultCurrent(result) && value.current()) { successCurrent = value.current; "Guardados actualizado" } else "Resultado pendiente de verificar"
                is FaroV5BookmarkMutationOutcome.Stale -> "Guardados cambió. Revisá nuevamente la acción"
                FaroV5BookmarkMutationOutcome.IdentityConflict -> "Conflicto de identidad. Revisá nuevamente la acción"
                FaroV5BookmarkMutationOutcome.UnknownReconcile -> "Resultado pendiente de verificar"
                is FaroV5BookmarkMutationOutcome.Rejected -> if (result.reason == BookmarkRejection.FOLDER_NOT_EMPTY) "La carpeta no está vacía" else "No se pudo completar la acción"
                FaroV5BookmarkMutationOutcome.Unavailable -> "Guardados no disponible"
            }
        } finally { mutations.revokeAll(); busy.set(false) }
    }
    fun cancel(review: FaroV5BookmarkFolderReview) { if (prepared?.review === review) prepared = null }
    override fun close() { closed = true; prepared = null; mutator.close(); reads.close(); mutations.close() }
}
