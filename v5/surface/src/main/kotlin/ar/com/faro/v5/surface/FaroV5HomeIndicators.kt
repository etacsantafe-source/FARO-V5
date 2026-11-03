package ar.com.faro.v5.surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S3 Inicio: four user-approved indicators. The owner supplies real counts.
 * A missing/failed read is UNKNOWN, never a fabricated zero.
 */
enum class FaroV5HomeIndicator(
    val label: String,
    val destination: FaroV5ShellDestination,
) {
    PENDIENTES("Pendientes", FaroV5ShellDestination.ASUNTOS),
    AVISOS("Avisos", FaroV5ShellDestination.AVISOS),
    ACTIVIDAD_RECIENTE("Actividad reciente", FaroV5ShellDestination.ACTIVIDAD),
    GUARDADOS("Guardados", FaroV5ShellDestination.GUARDADOS),
}

data class FaroV5HomeCount(
    val count: Int,
    val asOfEpochMillis: Long,
    val canonicalSource: String,
) {
    init {
        require(count >= 0)
        require(asOfEpochMillis >= 0)
        require(canonicalSource.isNotBlank())
    }
}

/** Read-only adapter for N-022 home.read. No new operation or source of truth. */
interface FaroV5HomeReadPort {
    suspend fun readIndicators(): Map<FaroV5HomeIndicator, FaroV5HomeCount>
}

internal fun faroV5HomeCountText(count: FaroV5HomeCount?): String =
    count?.count?.toString() ?: "No disponible"

/** Spoken label is explicit: a missing owner must not be announced as zero. */
internal fun faroV5HomeIndicatorAnnouncement(
    indicator: FaroV5HomeIndicator,
    count: FaroV5HomeCount?,
): String = "${indicator.label}: ${faroV5HomeCountText(count)}"

@Composable
internal fun FaroV5HomeScreen(
    readPort: FaroV5HomeReadPort?,
    onNavigate: (FaroV5ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts by produceState<Map<FaroV5HomeIndicator, FaroV5HomeCount>?>(
        initialValue = null,
        key1 = readPort,
    ) {
        value = if (readPort == null) null else try {
            withContext(Dispatchers.IO) { readPort.readIndicators() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(FaroV5Spacing.large),
    ) {
        Text(
            "Inicio",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        FaroV5HomeIndicator.entries.forEach { indicator ->
            val value = faroV5HomeCountText(counts?.get(indicator))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onNavigate(indicator.destination) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = faroV5HomeIndicatorAnnouncement(
                            indicator, counts?.get(indicator)
                        )
                    }
                    .padding(vertical = FaroV5Spacing.medium),
            ) {
                Text(indicator.label, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
