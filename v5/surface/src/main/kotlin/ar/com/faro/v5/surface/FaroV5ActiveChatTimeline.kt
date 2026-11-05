package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ChatTimelineProjection {
    data object Loading : ChatTimelineProjection
    data object Unselected : ChatTimelineProjection
    data object Missing : ChatTimelineProjection
    data object Failed : ChatTimelineProjection
    data class Ready(val value: ConversationTimelineSnapshot) : ChatTimelineProjection
}

/** Renders only the canonical active-conversation timeline; never fabricates an AI reply. */
@Composable
fun FaroV5ActiveChatTimeline(
    activeConversationId: ConversationId?,
    timelineReadPort: ConversationTimelineReadPort,
    refreshRevision: Long = 0L,
    focusMessageId: ConversationMessageId? = null,
) {
    var query by remember(activeConversationId, timelineReadPort) { mutableStateOf("") }
    var projection by remember(activeConversationId, timelineReadPort, refreshRevision) {
        mutableStateOf<ChatTimelineProjection>(
            if (activeConversationId == null) ChatTimelineProjection.Unselected
            else ChatTimelineProjection.Loading
        )
    }
    LaunchedEffect(activeConversationId, timelineReadPort, refreshRevision) {
        if (activeConversationId != null) {
            projection = try {
                val snapshot = withContext(Dispatchers.IO) {
                    timelineReadPort.readTimeline(activeConversationId)
                }
                if (snapshot == null) ChatTimelineProjection.Missing
                else ChatTimelineProjection.Ready(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ChatTimelineProjection.Failed
            }
        }
    }
    when (val state = projection) {
        ChatTimelineProjection.Loading -> Text("Cargando conversación…")
        ChatTimelineProjection.Unselected -> Text("No hay una conversación activa.")
        ChatTimelineProjection.Missing -> Text("Esta conversación ya no está disponible.")
        ChatTimelineProjection.Failed -> Text("No se pudo recuperar la conversación.")
        is ChatTimelineProjection.Ready -> {
            val search = remember(state.value, query) {
                FaroV5InChatSearchPolicy.search(state.value, query)
            }
            val matches = search.messageIds
            var position by remember(state.value, query) { mutableIntStateOf(0) }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(FaroV5InChatSearchPolicy.MAX_QUERY_CHARS) },
                    label = { Text("Buscar en esta conversación") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (matches.isEmpty() && !search.complete)
                                "Cobertura parcial: puede haber más coincidencias."
                            else if (matches.isEmpty()) "Sin coincidencias en los mensajes cargados."
                            else if (!search.complete)
                                "Coincidencia ${position + 1} de ${matches.size} · cobertura parcial"
                            else "Coincidencia ${position + 1} de ${matches.size}",
                            modifier = Modifier.padding(FaroV5Spacing.small),
                        )
                        Row {
                            TextButton(
                                enabled = matches.size > 1,
                                onClick = { position = (position + matches.size - 1) % matches.size },
                            ) { Text("Anterior") }
                            TextButton(
                                enabled = matches.size > 1,
                                onClick = { position = (position + 1) % matches.size },
                            ) { Text("Siguiente") }
                        }
                    }
                }
                FaroV5MessageTimeline(
                    timeline = state.value,
                    modifier = Modifier.weight(1f),
                    focusMessageId = if (query.isNotBlank()) matches.getOrNull(position)
                    else focusMessageId?.takeIf { target ->
                        state.value.messages.any { it.id == target }
                    },
                )
            }
        }
    }
}
