package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.assertEquals
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
