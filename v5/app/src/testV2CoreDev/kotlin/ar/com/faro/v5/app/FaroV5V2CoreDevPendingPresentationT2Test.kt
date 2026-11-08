package ar.com.faro.v5.app

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FaroV5V2CoreDevPendingPresentationT2Test {
    private fun page(
        items: List<FaroV5N031DueFact> = emptyList(),
        complete: Boolean = true,
    ) = FaroV5N031ThreeOwnerDuePage(items, 2L, 3L, 4L, complete)

    private fun fact(source: FaroV5N031DueSource) = FaroV5N031DueFact(
        source, "PRIVATE-OPAQUE-ID", LocalDate.of(2026, 11, 8),
        1794168000000, "PRIVATE-PROVENANCE",
    )

    @Test fun absentAuthorityNeverMeansZero() {
        val result = FaroV5V2CoreDevPendingPresentation.render(null)
        assertEquals(FaroV5V2CoreDevPendingPresentation.unavailable, result)
        assertTrue(result.contains("No disponible"))
        assertFalse(result.contains("Pendientes: 0"))
    }

    @Test fun emptyAuthorizedResultOnlyClaimsBoundedStoredWindow() {
        val result = FaroV5V2CoreDevPendingPresentation.render(page())
        assertTrue(result.contains("Sin vencimientos registrados en estas tres fuentes y ventana"))
        assertTrue(result.contains("no representa todos los Pendientes"))
    }

    @Test fun partialEmptyResultNeverClaimsZero() {
        val result = FaroV5V2CoreDevPendingPresentation.render(page(complete = false))
        assertTrue(result.contains("total desconocido"))
        assertFalse(result.contains("Sin vencimientos registrados"))
    }

    @Test fun sourceLabelsAndCivilDateWithoutSyntheticTimestamp() {
        val result = FaroV5V2CoreDevPendingPresentation.render(page(
            FaroV5N031DueSource.entries.map(::fact),
        ))
        assertTrue(result.contains("Asunto — 08/11/2026"))
        assertTrue(result.contains("Recordatorio — 08/11/2026"))
        assertTrue(result.contains("Plan mensual — 08/11/2026"))
    }

    @Test fun opaqueIdsProvenanceAndAmountsNeverRendered() {
        val result = FaroV5V2CoreDevPendingPresentation.render(page(
            listOf(fact(FaroV5N031DueSource.MATTER_DUE)),
        ))
        assertFalse(result.contains("PRIVATE-OPAQUE-ID"))
        assertFalse(result.contains("PRIVATE-PROVENANCE"))
        assertFalse(result.contains("1794168000000"))
    }

    @Test fun truncatedPageStillShowsRecordsButNotGlobalCount() {
        val result = FaroV5V2CoreDevPendingPresentation.render(page(
            listOf(fact(FaroV5N031DueSource.MONTHLY_PLAN_ITEM)), complete = false,
        ))
        assertTrue(result.contains("Cobertura parcial"))
        assertTrue(result.contains("Plan mensual"))
        assertFalse(result.contains("Pendientes: 1"))
    }
}
