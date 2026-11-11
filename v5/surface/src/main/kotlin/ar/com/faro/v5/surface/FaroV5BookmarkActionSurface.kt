package ar.com.faro.v5.surface

import androidx.compose.runtime.*
import androidx.compose.material3.*
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import kotlinx.coroutines.launch

enum class FaroV5BookmarkChatAction { SAVE_OR_REMOVE, FAVORITE }
/** Opaque review identity: no authority, owner revision, key or source text. */
class FaroV5BookmarkChatReview(val conversationId: ConversationId, val messageId: ConversationMessageId, val label: String)
interface FaroV5BookmarkChatActions {
    suspend fun prepare(conversationId: ConversationId, messageId: ConversationMessageId, action: FaroV5BookmarkChatAction): FaroV5BookmarkChatReview?
    fun isCurrent(review: FaroV5BookmarkChatReview): Boolean
    suspend fun confirm(review: FaroV5BookmarkChatReview): String
    fun cancel(review: FaroV5BookmarkChatReview)
}
internal val LocalFaroV5BookmarkChatActions = staticCompositionLocalOf<FaroV5BookmarkChatActions?> { null }

@Composable
internal fun FaroV5BookmarkActionSurface(conversationId: ConversationId, messageId: ConversationMessageId, actions: FaroV5BookmarkChatActions) {
    val scope = rememberCoroutineScope()
    var busy by remember(actions, conversationId, messageId) { mutableStateOf(false) }
    var review by remember(actions, conversationId, messageId) { mutableStateOf<FaroV5BookmarkChatReview?>(null) }
    var status by remember(actions, conversationId, messageId) { mutableStateOf<String?>(null) }
    DisposableEffect(actions, conversationId, messageId) { onDispose { review?.let(actions::cancel) } }
    fun prepare(action: FaroV5BookmarkChatAction) {
        if (busy || review != null) return
        busy = true
        scope.launch { try {
            review = actions.prepare(conversationId, messageId, action)
            status = if (review == null) "Guardados no disponible" else null
        } finally { busy = false } }
    }
    TextButton(enabled = !busy && review == null, onClick = { prepare(FaroV5BookmarkChatAction.SAVE_OR_REMOVE) }) { Text("Guardar / Quitar") }
    TextButton(enabled = !busy && review == null, onClick = { prepare(FaroV5BookmarkChatAction.FAVORITE) }) { Text("Favorito") }
    status?.let { Text(it) }
    review?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) { actions.cancel(item); review = null } },
            title = { Text(item.label) },
            text = { Text("Mensaje seleccionado: " + item.messageId.value) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (!busy) { busy = true; scope.launch { try {
                    status = if (actions.isCurrent(item)) actions.confirm(item) else "Revisá nuevamente la acción"
                } finally { actions.cancel(item); review = null; busy = false } } }
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { actions.cancel(item); review = null }) { Text("Cancelar") } })
    }
}
