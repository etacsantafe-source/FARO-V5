package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FaroV5HomeIndicatorsContractTest {
    @Test
    fun exactlyFourApprovedIndicatorsWithCanonicalNavigation() {
        assertEquals(
            listOf("Pendientes", "Avisos", "Actividad reciente", "Guardados"),
            FaroV5HomeIndicator.entries.map { it.label },
        )
        assertEquals(
            listOf(
                FaroV5ShellDestination.ASUNTOS,
                FaroV5ShellDestination.AVISOS,
                FaroV5ShellDestination.ACTIVIDAD,
                FaroV5ShellDestination.GUARDADOS,
            ),
            FaroV5HomeIndicator.entries.map { it.destination },
        )
    }

    @Test
    fun unavailableIsNotFakeZeroAndRealZeroIsDistinct() {
        assertEquals("No disponible", faroV5HomeCountText(null))
        assertEquals("0", faroV5HomeCountText(FaroV5HomeCount(0, 1L, "home.read")))
        assertEquals("8", faroV5HomeCountText(FaroV5HomeCount(8, 1L, "home.read")))
    }

    @Test
    fun rejectsNegativeCountsAndMissingProvenance() {
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5HomeCount(-1, 1L, "home.read")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5HomeCount(0, -1L, "home.read")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5HomeCount(0, 1L, " ")
        }
    }
}
