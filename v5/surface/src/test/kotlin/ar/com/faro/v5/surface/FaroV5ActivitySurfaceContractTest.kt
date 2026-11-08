package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5ActivitySurfaceContractTest {
    private val commit = FaroV5ActivityEntry(
        source = FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT,
        opaqueId = "private-receipt",
        kind = FaroV5ActivityEventKind.APPLY_COMMITTED,
        timestampEpochMillis = 2000L,
        timeProvenance = FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED,
    )
    private val message = FaroV5ActivityEntry(
        source = FaroV5ActivitySource.WORKSPACE_MESSAGE,
        opaqueId = "private-message",
        kind = FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE,
        timestampEpochMillis = 1000L,
        timeProvenance = FaroV5ActivityTimeProvenance.WORKSPACE_PRODUCER_DECLARED,
    )

    @Test fun zeroIsOnlyTwoOwnerEmptyNotGlobalHistory() {
        val empty = FaroV5ActivityPage(emptyList(), true, false)
        assertTrue(FaroV5ActivitySurfacePolicy.message(empty).contains("dos fuentes"))
        assertFalse(empty.globallyComplete)
        assertFalse(FaroV5ActivitySurfacePolicy.message(empty).contains("toda FARO"))
    }

    @Test fun partialEmptyAndPopulatedBothSayCoverageIsPartial() {
        val empty = FaroV5ActivityPage(emptyList(), false, true)
        assertTrue(FaroV5ActivitySurfacePolicy.message(empty).contains("Cobertura parcial"))
        val populated = FaroV5ActivityPage(listOf(commit), true, false)
        assertTrue(FaroV5ActivitySurfacePolicy.message(populated).contains("Cobertura parcial"))
        assertFalse(populated.globallyComplete)
    }

    @Test fun explicitlyRequestedSummaryUsesPageOnlyAndNeverLeaksPrivateIds() {
        val page = FaroV5ActivityPage(listOf(commit, message), true, false)
        val summary = FaroV5ActivitySurfacePolicy.summary(page)
        assertTrue(summary.contains("1 aplicaciones confirmadas y 1 mensajes"))
        assertTrue(summary.contains("no toda FARO"))
        assertFalse(summary.contains("private-receipt"))
        assertFalse(summary.contains("private-message"))
        assertFalse(summary.contains("2000"))
    }

    @Test fun stableSortingSeparatesSourcesAndHonorsTimeProvenance() {
        val page = FaroV5ActivityPage(listOf(message, commit), true, false)
        assertEquals(listOf(commit, message), FaroV5ActivitySurfacePolicy.ordered(page))
        assertEquals("Aplicación confirmada", FaroV5ActivitySurfacePolicy.label(commit))
        assertEquals("Mensaje de usuario", FaroV5ActivitySurfacePolicy.label(message))
        assertTrue(FaroV5ActivitySurfacePolicy.timeLabel(commit).contains("Confirmación registrada"))
        assertTrue(FaroV5ActivitySurfacePolicy.timeLabel(message).contains("declarada"))
    }

    @Test fun rejectsSyntheticCrossDomainKindAndDuplicateRowIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            commit.copy(kind = FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            message.copy(timeProvenance = FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            message.copy(timestampEpochMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5ActivityPage(listOf(commit, commit), false, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5ActivityPage(List(101) { commit.copy(opaqueId = "id-$it") }, false, false)
        }
    }
}
