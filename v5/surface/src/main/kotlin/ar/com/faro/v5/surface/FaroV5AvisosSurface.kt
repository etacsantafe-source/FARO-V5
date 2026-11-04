package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** S3.A4 read-only projection. Only the canonical Auxiliary owner supplies items. */
enum class FaroV5AvisoImportance { REQUIERE_ATENCION, INFORMATIVO }

data class FaroV5AvisoItem(
    val opaqueId: String,
    val summary: String,
    val importance: FaroV5AvisoImportance,
) {
    init {
        require(opaqueId.isNotBlank())
        require(summary.isNotBlank())
        require(summary.length <= 512)
    }
}

data class FaroV5AvisosPage(
    val items: List<FaroV5AvisoItem>,
    val complete: Boolean,
) {
    init {
        require(items.size <= 100)
        require(items.map { it.opaqueId }.distinct().size == items.size)
    }
}

interface FaroV5AvisosReadPort {
    val invalidations: Flow<Unit>
    /** null is UNAVAILABLE, not empty. No read can mutate attention or OS delivery. */
    suspend fun readNotices(): FaroV5AvisosPage?
}

object FaroV5AvisosSurfacePolicy {
    @JvmStatic
    fun message(page: FaroV5AvisosPage): String = when {
        !page.complete -> "Cobertura parcial: pueden existir más avisos."
        page.items.isEmpty() -> "No hay avisos abiertos."
        else -> "Avisos abiertos de la fuente autorizada."
    }
}

/** Never render cached notices across generation, readiness or owner changes. */
@Composable
fun FaroV5AvisosScreen(readPort: FaroV5AvisosReadPort?) {
    val page by produceState<FaroV5AvisosPage?>(initialValue = null, key1 = readPort) {
        if (readPort == null) {
            value = null
        } else {
            readPort.invalidations.onStart { emit(Unit) }.collectLatest {
                value = null
                value = try {
                    withContext(Dispatchers.IO) { readPort.readNotices() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(FaroV5Spacing.large),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.medium),
    ) {
        Text(
            "Avisos",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        val current = page
        if (current == null) {
            Text("Avisos no disponibles. No se consultaron datos.")
        } else {
            Text(FaroV5AvisosSurfacePolicy.message(current))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
            ) {
                items(current.items, key = { it.opaqueId }) { item ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = FaroV5Spacing.small)) {
                        Text(
                            if (item.importance == FaroV5AvisoImportance.REQUIERE_ATENCION)
                                "Requiere atención" else "Informativo",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
