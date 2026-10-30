package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot

/**
 * Read-only A3 timeline. Caller supplies canonical conversation-scoped data.
 * The UI never inserts a model placeholder, changes Reality or sends a retry.
 * A6 may bind the canonical Workspace port only after V2 certification.
 */
@Composable
fun FaroV5MessageTimeline(
    timeline: ConversationTimelineSnapshot,
    modifier: Modifier = Modifier,
    onReply: ((ConversationMessageId) -> Unit)? = null,
    onRetry: ((ConversationMessageId) -> Unit)? = null,
    onCopyText: ((String) -> Unit)? = null,
    activeProcessingTurns: Set<ConversationTurnId> = emptySet(),
) {
    val byId = timeline.messages.associateBy { it.id }
    val scrollState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = scrollState,
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
    ) {
        if (timeline.messages.isEmpty()) {
            item(key = "timeline-empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(FaroV5Spacing.large),
                    verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
                ) {
                    Text("¿En qué querés trabajar?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Podés preguntar, planificar o preparar cambios. Tus datos sólo cambian cuando aprobás una aplicación.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(timeline.messages, key = { "msg-" + it.id.value }) { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.role == ConversationMessageRole.USER) {
                    Arrangement.End
                } else Arrangement.Start,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    shape = MaterialTheme.shapes.large,
                    color = if (message.role == ConversationMessageRole.USER) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(FaroV5Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
                    ) {
                        val quoted = message.replyToMessageId?.let(byId::get)
                        if (quoted != null) {
                            Text(
                                "Respuesta a: " + quoted.text.take(100),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (message.text.isNotEmpty()) {
                            SelectionContainer {
                                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val status = faroV5MessageStatusLabel(message, message.turnId in activeProcessingTurns)
                        if (status != null) {
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(FaroV5Spacing.small)) {
                            if (onCopyText != null && message.text.isNotEmpty()) {
                                TextButton(onClick = { onCopyText(message.text) }) {
                                    Text("Copiar")
                                }
                            }
                            if (onReply != null) {
                                TextButton(onClick = { onReply(message.id) }) {
                                    Text("Responder")
                                }
                            }
                            if (onRetry != null && message.role == ConversationMessageRole.FARO &&
                                message.state in setOf(
                                    ConversationMessageState.FAILED,
                                    ConversationMessageState.OFFLINE,
                                    ConversationMessageState.PARTIAL,
                                )
                            ) {
                                TextButton(onClick = { onRetry(message.id) }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pure visible status; never a fabricated provider/model answer. */
internal fun faroV5MessageStatusLabel(
    message: ConversationMessage,
    isActuallyProcessing: Boolean = false,
): String? =
    when (message.state) {
        ConversationMessageState.ACCEPTED -> null
        ConversationMessageState.PROCESSING -> if (isActuallyProcessing) "Procesando…" else "Respuesta pendiente de verificar"
        ConversationMessageState.COMPLETED -> null
        ConversationMessageState.PARTIAL -> "Resultado parcial"
        ConversationMessageState.FAILED -> "No se pudo completar"
        ConversationMessageState.OFFLINE -> "Sin conexión para responder"
    }
