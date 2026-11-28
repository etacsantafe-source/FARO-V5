package ar.com.faro.v5.surface

import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5StockReadSurfaceContractTest {
    private val original = FaroV5StockUiPosition("Yerba", FaroV5StockUiUnit.UNIT, 7_000, 2_000, 0, 3_000)
    private val receipt = FaroV5StockUiHistory("Yerba", FaroV5StockUiUnit.UNIT, FaroV5StockUiMovement.RECEIPT, 7_000, 0, 1)
    private val undo = receipt.copy(movement = FaroV5StockUiMovement.CONSUME_UNDO, quantity = 1_000, revision = 2)

    @Test fun quantitiesCarryExplicitUnitAndAvailability() {
        assertEquals(
            listOf("Físico: 7 unidades", "Reservado: 2 unidades", "Disponible: 5 unidades",
                "Consumido: 0 unidades", "Pendiente de recibir: 3 unidades"),
            FaroV5StockReadText.balances(original),
        )
        assertEquals("1,025 litros", FaroV5StockReadText.amount(1_025, FaroV5StockUiUnit.LITER))
        assertEquals("1025 mililitros", FaroV5StockReadText.amount(1_025_000, FaroV5StockUiUnit.MILLILITER))
    }

    @Test fun readinessSeparatesEmptyFromAllHiddenStates() {
        val empty: FaroV5StockUiState = FaroV5StockUiState.EmptyConfirmed
        assertFalse(empty is FaroV5StockUiState.Ready)
        FaroV5StockUiState.Hidden.entries.forEach {
            assertTrue(it.message.startsWith("Stock no disponible"))
            assertTrue(it.message.none(Char::isDigit))
        }
        assertThrows(IllegalArgumentException::class.java) { FaroV5StockUiState.Ready(emptyList(), emptyList()) }
    }

    @Test fun rejectsUnbalancedOrDuplicatedPositions() {
        assertThrows(IllegalArgumentException::class.java) { original.copy(reserved = 7_001) }
        assertThrows(IllegalArgumentException::class.java) { original.copy(physical = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5StockUiState.Ready(listOf(original, original), emptyList())
        }
        val grams = original.copy(unit = FaroV5StockUiUnit.GRAM)
        assertEquals(2, FaroV5StockUiState.Ready(listOf(original, grams), emptyList()).positions.size)
    }

    @Test fun historyLabelsUndoAndDateAndUsesDescendingRevision() {
        val state = FaroV5StockUiState.Ready(listOf(original), listOf(receipt, undo))
        assertEquals(listOf(2L, 1L), state.history.map { it.revision })
        assertEquals("Consumo revertido", state.history.first().movement.label)
        assertEquals("01/01/1970 00:00", FaroV5StockReadText.recordedAt(0, ZoneOffset.UTC))
    }

    @Test fun historyRejectsUnknownPositionAndRepeatedRevisions() {
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5StockUiState.Ready(listOf(original), listOf(receipt.copy(item = "Otro")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5StockUiState.Ready(listOf(original), listOf(receipt, receipt))
        }
    }
}
