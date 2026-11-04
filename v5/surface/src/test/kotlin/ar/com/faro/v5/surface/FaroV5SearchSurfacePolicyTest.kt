package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5SearchSurfacePolicyTest {
    private val record = FaroV5SearchHit(
        "workspace.conversations", "c:opaque", "Café del mes",
        "workspace.conversations", 100L,
    )

    @Test
    fun partialAndUnavailableNeverClaimNoMatches() {
        val partial = FaroV5SearchPage(FaroV5SearchCoverage.PARTIAL, emptyList(), true)
        val unavailable = FaroV5SearchPage(FaroV5SearchCoverage.UNAVAILABLE, emptyList(), true)
        val complete = FaroV5SearchPage(FaroV5SearchCoverage.COMPLETE, emptyList(), false)
        assertTrue(FaroV5SearchSurfacePolicy.message(partial).contains("parcial"))
        assertTrue(FaroV5SearchSurfacePolicy.message(unavailable).contains("no disponible"))
        assertTrue(FaroV5SearchSurfacePolicy.message(complete).startsWith("No se encontraron"))
        assertEquals(
            "Resultados de las fuentes autorizadas.",
            FaroV5SearchSurfacePolicy.message(
                FaroV5SearchPage(FaroV5SearchCoverage.COMPLETE, listOf(record), false),
            ),
        )
    }

    @Test
    fun forbiddenDataAndAmbiguousHitIdentitiesCannotEnterPresentation() {
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5SearchPage(FaroV5SearchCoverage.UNAVAILABLE, listOf(record), true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5SearchPage(FaroV5SearchCoverage.COMPLETE, listOf(record, record), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(canonicalSource = "")
        }
    }
}
