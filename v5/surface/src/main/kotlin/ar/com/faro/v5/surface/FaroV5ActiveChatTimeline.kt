package ar.com.faro.v5.surface

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import kotlinx.coroutines.CancellationException

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
) {
    var projection by remember(activeConversationId, timelineReadPort, refreshRevision) {
        mutableStateOf<ChatTimelineProjection>(
            if (activeConversationId == null) ChatTimelineProjection.Unselected
            else ChatTimelineProjection.Loading
        )
    }
    LaunchedEffect(activeConversationId, timelineReadPort, refreshRevision) {
        if (activeConversationId != null) {
            projection = try {
                val snapshot = timelineReadPort.readTimeline(activeConversationId)
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
        is ChatTimelineProjection.Ready -> FaroV5MessageTimeline(state.value)
    }
}
