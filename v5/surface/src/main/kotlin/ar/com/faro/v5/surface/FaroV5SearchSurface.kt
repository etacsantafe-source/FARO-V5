package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** N-018 results are only rendered when a real authorized owner is bound. */
enum class FaroV5SearchCoverage { COMPLETE, PARTIAL, UNAVAILABLE }
enum class FaroV5SearchOpenResult { CONFIRMED, STALE, UNAVAILABLE }

data class FaroV5SearchHit(
    val namespace: String,
    val opaqueId: String,
    val title: String,
    val canonicalSource: String,
    val asOfEpochMillis: Long,
) {
    init {
        require(namespace.isNotBlank() && opaqueId.isNotBlank())
        require(title.isNotBlank() && canonicalSource.isNotBlank())
        require(asOfEpochMillis >= 0)
    }
}

data class FaroV5SearchPage(
    val coverage: FaroV5SearchCoverage,
    val hits: List<FaroV5SearchHit>,
    val indexDegraded: Boolean,
) {
    init {
        require(hits.size <= 50)
        require(coverage != FaroV5SearchCoverage.UNAVAILABLE || hits.isEmpty())
        require(hits.map { it.namespace to it.opaqueId }.distinct().size == hits.size)
    }
}

/**
 * Composition root MUST provide the genuine generation/ACL authority,
 * source-specific invalidations and a newly authorized selection on open.
 * A search hit is never sufficient authorization for selecting a conversation.
 * Until those owners are available the host MUST leave this port null.
 */
interface FaroV5SearchReadPort {
    val invalidations: Flow<Unit>
    suspend fun search(query: String): FaroV5SearchPage
    suspend fun openAuthorized(hit: FaroV5SearchHit): FaroV5SearchOpenResult
}

/** Partial and unavailable results must never be announced as a definitive absence. */
object FaroV5SearchSurfacePolicy {
    @JvmStatic
    fun message(page: FaroV5SearchPage): String = when (page.coverage) {
        FaroV5SearchCoverage.UNAVAILABLE -> "Búsqueda no disponible. No se leyeron datos."
        FaroV5SearchCoverage.PARTIAL ->
            if (page.hits.isEmpty()) "Cobertura parcial: pueden existir más resultados."
            else "Cobertura parcial: hay resultados que todavía no se pudieron consultar."
        FaroV5SearchCoverage.COMPLETE ->
            if (page.hits.isEmpty()) "No se encontraron resultados en las fuentes consultadas."
            else "Resultados de las fuentes autorizadas."
    }
}

private sealed interface FaroV5SearchScreenState {
    data object Idle : FaroV5SearchScreenState
    data object Loading : FaroV5SearchScreenState
    data class Ready(val page: FaroV5SearchPage) : FaroV5SearchScreenState
    data object Unavailable : FaroV5SearchScreenState
}

@Composable
fun FaroV5SearchScreen(
    readPort: FaroV5SearchReadPort?,
    onOpenConversation: () -> Unit,
) {
    var query by remember(readPort) { mutableStateOf("") }
    var requestVersion by remember(readPort) { mutableLongStateOf(0L) }
    var state by remember(readPort) {
        mutableStateOf<FaroV5SearchScreenState>(FaroV5SearchScreenState.Idle)
    }
    val scope = rememberCoroutineScope()

    // Immediately clear previously displayed data on genuine owner invalidation.
    // The port cannot be installed without such a source.
    LaunchedEffect(readPort) {
        readPort?.invalidations?.collect {
            requestVersion++
            state = FaroV5SearchScreenState.Idle
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(FaroV5Spacing.large),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.medium),
    ) {
        Text("Buscar", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                requestVersion++
                state = FaroV5SearchScreenState.Idle
            },
            label = { Text("Buscar en FARO") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = readPort != null && query.isNotBlank()
                && state != FaroV5SearchScreenState.Loading,
            onClick = {
                val port = readPort
                if (port != null) {
                    val version = ++requestVersion
                    val requestedQuery = query
                    state = FaroV5SearchScreenState.Loading
                    scope.launch {
                        val outcome = try {
                            withContext(Dispatchers.IO) { port.search(requestedQuery) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (requestVersion == version) {
                            state = if (outcome == null) FaroV5SearchScreenState.Unavailable
                            else FaroV5SearchScreenState.Ready(outcome)
                        }
                    }
                }
            },
        ) { Text("Buscar") }

        if (readPort == null) {
            Text("Búsqueda no disponible: falta vincular la autoridad de lectura.")
        } else when (val current = state) {
            FaroV5SearchScreenState.Idle ->
                Text("Ingresá una consulta para buscar solo en fuentes autorizadas.")
            FaroV5SearchScreenState.Loading ->
                CircularProgressIndicator()
            FaroV5SearchScreenState.Unavailable ->
                Text("Búsqueda no disponible. No se leyeron datos.")
            is FaroV5SearchScreenState.Ready -> {
                Text(FaroV5SearchSurfacePolicy.message(current.page))
                if (current.page.indexDegraded &&
                    current.page.coverage != FaroV5SearchCoverage.UNAVAILABLE) {
                    Text("Índice no disponible: consulta canónica limitada.")
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
                ) {
                    items(
                        items = current.page.hits,
                        key = { it.namespace + "\u0000" + it.opaqueId },
                    ) { hit ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val version = ++requestVersion
                                state = FaroV5SearchScreenState.Loading
                                scope.launch {
                                    val opened = try {
                                        withContext(Dispatchers.IO) {
                                            readPort.openAuthorized(hit)
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        FaroV5SearchOpenResult.UNAVAILABLE
                                    }
                                    if (requestVersion == version) {
                                        state = FaroV5SearchScreenState.Unavailable
                                        if (opened == FaroV5SearchOpenResult.CONFIRMED) {
                                            onOpenConversation()
                                        }
                                    }
                                }
                            },
                        ) {
                            Column {
                                Text(hit.title)
                                Text(
                                    "Fuente: " + hit.canonicalSource,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
