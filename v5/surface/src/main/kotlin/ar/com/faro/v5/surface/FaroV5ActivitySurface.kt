package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** N015 presentation types only, not new history.query contracts or ACLs. */
enum class FaroV5ActivitySource { REALITY_COMMITTED_RECEIPT, WORKSPACE_MESSAGE }
enum class FaroV5ActivityTimeProvenance {
    REALITY_COMMIT_RECORDED,
    WORKSPACE_PRODUCER_DECLARED,
}
enum class FaroV5ActivityEventKind {
    APPLY_COMMITTED,
    WORKSPACE_USER_MESSAGE,
    WORKSPACE_ASSISTANT_MESSAGE,
}

/** An opaque source key identifies UI rows but MUST NOT be rendered as content. */
data class FaroV5ActivityEntry(
    val source: FaroV5ActivitySource,
    val opaqueId: String,
    val kind: FaroV5ActivityEventKind,
    val timestampEpochMillis: Long,
    val timeProvenance: FaroV5ActivityTimeProvenance,
) {
    init {
        require(opaqueId.isNotBlank() && opaqueId.length <= 256)
        require(timestampEpochMillis >= 0L)
        when (source) {
            FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT -> {
                require(kind == FaroV5ActivityEventKind.APPLY_COMMITTED)
                require(timeProvenance == FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED)
            }
            FaroV5ActivitySource.WORKSPACE_MESSAGE -> {
                require(kind != FaroV5ActivityEventKind.APPLY_COMMITTED)
                require(timeProvenance == FaroV5ActivityTimeProvenance.WORKSPACE_PRODUCER_DECLARED)
            }
        }
    }

    val rowKey: String get() = source.name + ":" + opaqueId
}

/**
 * Two documented owners only, not global Activity. completeWithinSources
 * describes page completeness for THOSE authorized sources, not all FARO.
 */
data class FaroV5ActivityPage(
    val entries: List<FaroV5ActivityEntry>,
    val completeWithinSources: Boolean,
    val hasUndatedLegacyReceipts: Boolean,
) {
    init {
        require(entries.size <= 100)
        require(entries.map { it.rowKey }.distinct().size == entries.size)
    }

    val globallyComplete: Boolean get() = false
}

/** A host MUST emit invalidation on generation, ACL epoch and owner changes. */
interface FaroV5ActivityReadPort {
    val invalidations: Flow<Unit>

    /** Null = unavailable/denied, not zero. Must never mutate owner history. */
    suspend fun readActivity(): FaroV5ActivityPage?
}

object FaroV5ActivitySurfacePolicy {
    @JvmStatic fun message(page: FaroV5ActivityPage): String = when {
        page.entries.isEmpty() && page.completeWithinSources ->
            "No hay actividad en las dos fuentes autorizadas. No incluye otras fuentes."
        page.entries.isEmpty() ->
            "Sin actividad en esta página. Cobertura parcial: pueden existir más eventos."
        else ->
            "Actividad de dos fuentes autorizadas. Cobertura parcial de FARO."
    }

    @JvmStatic fun ordered(page: FaroV5ActivityPage): List<FaroV5ActivityEntry> =
        page.entries.sortedWith(
            compareByDescending<FaroV5ActivityEntry> { it.timestampEpochMillis }
                .thenBy { it.source.ordinal }.thenBy { it.opaqueId }
        )

    @JvmStatic fun label(entry: FaroV5ActivityEntry): String = when (entry.kind) {
        FaroV5ActivityEventKind.APPLY_COMMITTED -> "Aplicación confirmada"
        FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE -> "Mensaje de usuario"
        FaroV5ActivityEventKind.WORKSPACE_ASSISTANT_MESSAGE -> "Respuesta registrada"
    }

    @JvmStatic fun timeLabel(entry: FaroV5ActivityEntry): String =
        when (entry.timeProvenance) {
            FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED ->
                "Confirmación registrada (UTC)"
            FaroV5ActivityTimeProvenance.WORKSPACE_PRODUCER_DECLARED ->
                "Hora declarada por emisor (UTC)"
        } + ": " + Instant.ofEpochMilli(entry.timestampEpochMillis).toString()

    /** Only an explicitly requested summary of the CURRENT authorized page. */
    @JvmStatic fun summary(page: FaroV5ActivityPage): String {
        val receipts = page.entries.count {
            it.source == FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT
        }
        val messages = page.entries.size - receipts
        val coverage = if (page.completeWithinSources) {
            "Se cubrieron las dos fuentes de esta consulta, no toda FARO."
        } else {
            "Página incompleta; pueden existir más eventos en esas fuentes."
        }
        return "Resumen local solicitado: $receipts aplicaciones confirmadas y " +
            "$messages mensajes en la página autorizada. $coverage"
    }
}

private data class FaroV5ActivityReadState(
    val page: FaroV5ActivityPage?,
    val authorizationEpoch: Long,
)

/** Existing Activity drawer destination; never auto-mark-read or initiate Apply. */
@Composable
fun FaroV5ActivityScreen(readPort: FaroV5ActivityReadPort?) {
    val state by produceState(
        initialValue = FaroV5ActivityReadState(null, 0L),
        key1 = readPort,
    ) {
        if (readPort != null) {
            var epoch = 0L
            readPort.invalidations.onStart { emit(Unit) }.collectLatest {
                epoch += 1L
                value = FaroV5ActivityReadState(null, epoch)
                val page = try {
                    withContext(Dispatchers.IO) { readPort.readActivity() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                value = FaroV5ActivityReadState(page, epoch)
            }
        }
    }

    val page = state.page
    var summaryRequested by remember(readPort, page, state.authorizationEpoch) {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(FaroV5Spacing.large),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.medium),
    ) {
        Text(
            "Actividad",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (page == null) {
            Text("Actividad no disponible. No se consultaron datos.")
        } else {
            Text(FaroV5ActivitySurfacePolicy.message(page))
            if (page.hasUndatedLegacyReceipts) {
                Text("Hay registros anteriores sin fecha verificable que no aparecen acá.")
            }
            Button(onClick = { summaryRequested = true }) {
                Text("Ver resumen local")
            }
            if (summaryRequested) {
                Text(FaroV5ActivitySurfacePolicy.summary(page))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small)) {
                items(
                    FaroV5ActivitySurfacePolicy.ordered(page),
                    key = { it.rowKey },
                ) { entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = FaroV5Spacing.small),
                    ) {
                        Text(
                            FaroV5ActivitySurfacePolicy.label(entry),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            FaroV5ActivitySurfacePolicy.timeLabel(entry),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
