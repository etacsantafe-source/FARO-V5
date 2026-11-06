package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5AvisosSurfaceContractTest {
    private val item = FaroV5AvisoItem(
        "notice-opaque", "Una novedad real", FaroV5AvisoImportance.INFORMATIVO,
    )

    @Test
    fun authorizedEmptyAndPartialCoverageRemainDistinct() {
        assertEquals(
            "No hay avisos abiertos.",
            FaroV5AvisosSurfacePolicy.message(FaroV5AvisosPage(emptyList(), true)),
        )
        assertTrue(
            FaroV5AvisosSurfacePolicy.message(FaroV5AvisosPage(emptyList(), false))
                .contains("parcial"),
        )
        assertTrue(
            FaroV5AvisosSurfacePolicy.message(FaroV5AvisosPage(listOf(item), true))
                .contains("autorizada"),
        )
    }

    @Test
    fun consentOnlySummaryCountsPrioritiesWithoutExposingNoticeDetails() {
        val sensitive = FaroV5AvisoItem(
            "private-identifier", "Aviso privado",
            FaroV5AvisoImportance.REQUIERE_ATENCION,
        )
        val summary = FaroV5AvisosSurfacePolicy.summary(
            FaroV5AvisosPage(listOf(sensitive, item), complete = true),
        )
        assertEquals(
            "Resumen solicitado: 1 requieren atención y 1 informativos en la página " +
                "autorizada. Cobertura completa de la fuente autorizada.",
            summary,
        )
        assertFalse(summary.contains("private-identifier"))
        assertFalse(summary.contains("Aviso privado"))
        assertFalse(summary.contains("Una novedad real"))
    }

    @Test
    fun summaryDistinguishesAuthorizedEmptyFromPartialWithoutFakeGlobalTotals() {
        val zero = FaroV5AvisosSurfacePolicy.summary(
            FaroV5AvisosPage(emptyList(), complete = true),
        )
        assertTrue(zero.contains("0 requieren atención y 0 informativos"))
        val partial = FaroV5AvisosSurfacePolicy.summary(
            FaroV5AvisosPage(listOf(item), complete = false),
        )
        assertTrue(partial.contains("Cobertura parcial"))
        assertFalse(partial.contains("Cobertura completa"))
    }

    @Test
    fun duplicateIdentityAndBlankSummaryAreDenied() {
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5AvisosPage(listOf(item, item), true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            item.copy(summary = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            item.copy(opaqueId = "")
        }
    }
}
