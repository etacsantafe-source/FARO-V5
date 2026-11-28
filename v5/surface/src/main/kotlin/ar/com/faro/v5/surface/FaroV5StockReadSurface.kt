package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale

enum class FaroV5StockUiUnit(val label: String) {
    UNIT("unidades"),
    PACKAGE("paquetes"),
    GRAM("gramos"),
    KILOGRAM("kilogramos"),
    MILLILITER("mililitros"),
    LITER("litros"),
}

enum class FaroV5StockUiMovement(val label: String) {
    RECEIPT("Recepción confirmada"),
    RECEIPT_UNDO("Recepción revertida"),
    RESERVE("Reserva"),
    RELEASE("Liberación de reserva"),
    CONSUME("Consumo"),
    CONSUME_UNDO("Consumo revertido"),
}

data class FaroV5StockUiPosition(
    val item: String,
    val unit: FaroV5StockUiUnit,
    val physical: Long,
    val reserved: Long,
    val consumed: Long,
    val pendingReceipt: Long,
) {
    init {
        require(item.isNotBlank() && reserved in 0..physical && consumed >= 0 && pendingReceipt >= 0)
    }

    val available: Long get() = physical - reserved
}

data class FaroV5StockUiHistory(
    val item: String,
    val unit: FaroV5StockUiUnit,
    val movement: FaroV5StockUiMovement,
    val quantity: Long,
    val recordedAtMs: Long,
    val revision: Long,
) {
    init {
        require(item.isNotBlank() && quantity > 0 && recordedAtMs >= 0 && revision > 0)
    }
}

/** The app supplies this state only after owner admission and a verified read. */
sealed interface FaroV5StockUiState {
    class Ready(
        positions: List<FaroV5StockUiPosition>,
        history: List<FaroV5StockUiHistory>,
    ) : FaroV5StockUiState {
        val positions: List<FaroV5StockUiPosition> = Collections.unmodifiableList(positions.toList())
        val history: List<FaroV5StockUiHistory> =
            Collections.unmodifiableList(history.sortedByDescending { it.revision })

        init {
            val keys = this.positions.map { it.item to it.unit }.toSet()
            require(keys.isNotEmpty() && keys.size == this.positions.size)
            require(this.history.map { it.revision }.distinct().size == this.history.size)
            require(this.history.all { (it.item to it.unit) in keys })
        }
    }

    object EmptyConfirmed : FaroV5StockUiState

    enum class Hidden(val message: String) : FaroV5StockUiState {
        UNAVAILABLE("Stock no disponible"),
        DENIED("Stock no disponible. Falta autorización de lectura."),
        STALE("Stock no disponible. Cambió la versión de los datos."),
    }
}

internal object FaroV5StockReadText {
    fun amount(thousandths: Long, unit: FaroV5StockUiUnit): String =
        BigDecimal.valueOf(thousandths, 3).stripTrailingZeros().toPlainString().replace('.', ',') +
            " " + unit.label

    fun balances(position: FaroV5StockUiPosition): List<String> = with(position) {
        listOf(
            "Físico: " + amount(physical, unit),
            "Reservado: " + amount(reserved, unit),
            "Disponible: " + amount(available, unit),
            "Consumido: " + amount(consumed, unit),
            "Pendiente de recibir: " + amount(pendingReceipt, unit),
        )
    }

    private val date = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("es-AR"))

    fun recordedAt(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        date.format(Instant.ofEpochMilli(ms).atZone(zone))
}

/** Read-only content for the existing SmartCompra destination. Its parent owns admission and lifecycle. */
@Composable
fun FaroV5StockReadSurface(state: FaroV5StockUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { StockHeading("Stock") }
        when (state) {
            FaroV5StockUiState.EmptyConfirmed -> item { Text("Todavía no registraste stock") }
            is FaroV5StockUiState.Hidden -> item {
                Text(state.message, modifier = Modifier.semantics { heading() })
            }
            is FaroV5StockUiState.Ready -> {
                items(state.positions, key = { it.unit.name + ":" + it.item }) { position ->
                    Column(
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(position.item, style = MaterialTheme.typography.titleMedium)
                        FaroV5StockReadText.balances(position).forEach { Text(it) }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item { StockHeading("Historial de stock") }
                    items(state.history, key = { it.revision }) { entry ->
                        Column(
                            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(entry.movement.label, style = MaterialTheme.typography.titleMedium)
                            Text(entry.item)
                            Text(FaroV5StockReadText.amount(entry.quantity, entry.unit))
                            Text(FaroV5StockReadText.recordedAt(entry.recordedAtMs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockHeading(text: String) {
    Text(text, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
