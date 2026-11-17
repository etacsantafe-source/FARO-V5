package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * W01-B3A: pure UI policy contracts. No Finance database, biometric,
 * productive Android screen, real user balance or TalkBack hardware tested.
 */
class FaroV5FinanceReadSurfaceContractTest {
    private fun verified(
        id: String = "account-ars",
        currency: String = "ARS",
        minor: Long = 0L,
        revision: Long = 7L,
    ) = FaroV5FinanceAccountLine(
        id, "Cuenta de prueba", currency,
        FaroV5FinanceAccountCertainty.VERIFIED, minor, revision,
    )

    private fun snapshot(
        coverage: FaroV5FinanceCoverage,
        accounts: List<FaroV5FinanceAccountLine> = listOf(verified()),
    ) = FaroV5FinanceUiSnapshot(
        coverage, "generation-local-test", 3L, 7L, accounts,
    )

    @Test fun t01DeniedUnavailableAndStaleNeverCreateFakeZeroOrRetainAccounts() {
        assertEquals(
            "Finanzas no disponible. No se muestran saldos supuestos.",
            FaroV5FinanceSurfacePolicy.overview(null),
        )
        for (coverage in listOf(
            FaroV5FinanceCoverage.DENIED,
            FaroV5FinanceCoverage.UNAVAILABLE,
            FaroV5FinanceCoverage.STALE,
        )) {
            val denied = FaroV5FinanceUiSnapshot(coverage)
            assertTrue(denied.accounts.isEmpty())
            assertFalse(FaroV5FinanceSurfacePolicy.overview(denied).contains("ARS 0"))
            assertThrows(IllegalArgumentException::class.java) {
                denied.copy(accounts = listOf(verified()))
            }
            assertThrows(IllegalArgumentException::class.java) {
                denied.copy(sourceRevision = 0)
            }
        }
    }

    @Test fun t02ConfirmedEmptyRequiresRealSourceRevisionAndNoAccountRows() {
        val empty = snapshot(FaroV5FinanceCoverage.EMPTY_CONFIRMED, emptyList())
        assertEquals(7L, empty.sourceRevision)
        assertTrue(FaroV5FinanceSurfacePolicy.overview(empty).contains("confirmó"))
        assertThrows(IllegalArgumentException::class.java) {
            empty.copy(accounts = listOf(verified()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            empty.copy(sourceRevision = null)
        }
    }

    @Test fun t03ReadyRequiresVerifiedBalancesAndRejectsDuplicateIdentity() {
        val ready = snapshot(FaroV5FinanceCoverage.READY)
        assertEquals("ARS 0.00", FaroV5FinanceSurfacePolicy.balanceText(ready.accounts.single()))
        assertFalse(FaroV5FinanceSurfacePolicy.confirmedNegative(ready.accounts.single()))
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(accounts = listOf(verified(), verified()))
        }
        val unknown = FaroV5FinanceAccountLine(
            "missing", "Sin confirmación", "ARS",
            FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(accounts = listOf(unknown))
        }
    }

    @Test fun t04UnconfirmedOpeningAndPartialCannotCarryAnAmount() {
        for (certainty in listOf(
            FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED,
            FaroV5FinanceAccountCertainty.PARTIAL,
            FaroV5FinanceAccountCertainty.DENIED,
        )) {
            val row = FaroV5FinanceAccountLine("unverified", "Cuenta", "ARS", certainty)
            assertNull(row.confirmedBalanceMinor)
            assertFalse(FaroV5FinanceSurfacePolicy.balanceText(row).contains("ARS 0"))
            assertThrows(IllegalArgumentException::class.java) {
                row.copy(confirmedBalanceMinor = 0L)
            }
            assertThrows(IllegalArgumentException::class.java) {
                row.copy(sourceRevision = 1L)
            }
        }
    }

    @Test fun t05ArsUsdAreSeparatedNegativeBalanceVisibleWithoutFxSum() {
        val ars = verified(id = "ars", minor = -125L)
        val usd = verified(id = "usd", currency = "USD", minor = 350L)
        val page = snapshot(FaroV5FinanceCoverage.READY, listOf(usd, ars))
        assertEquals(listOf("ARS", "USD"), FaroV5FinanceSurfacePolicy.currencies(page))
        assertEquals("ARS -1.25", FaroV5FinanceSurfacePolicy.balanceText(ars))
        assertEquals("USD 3.50", FaroV5FinanceSurfacePolicy.balanceText(usd))
        assertTrue(FaroV5FinanceSurfacePolicy.confirmedNegative(ars))
        assertFalse(FaroV5FinanceSurfacePolicy.confirmedNegative(usd))
    }

    @Test fun t06GenerationPointerAndRevisionMustBeAdmittedNotStale() {
        val ready = snapshot(FaroV5FinanceCoverage.READY)
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(generationId = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(pointerSequence = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(sourceRevision = 8L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ready.copy(sourceRevision = null)
        }
    }

    @Test fun t07UnsupportedCurrencyNeverInheritsArsUsdMinorExponent() {
        assertThrows(IllegalArgumentException::class.java) {
            verified(currency = "JPY")
        }
        val unknown = FaroV5FinanceAccountLine(
            "jpy", "Cuenta con escala pendiente", "JPY",
            FaroV5FinanceAccountCertainty.PARTIAL,
        )
        assertTrue(FaroV5FinanceSurfacePolicy.balanceText(unknown).contains("no disponible"))
        assertEquals(listOf("JPY"), FaroV5FinanceSurfacePolicy.currencies(
            snapshot(FaroV5FinanceCoverage.PARTIAL, listOf(unknown)),
        ))
        assertThrows(IllegalArgumentException::class.java) {
            unknown.copy(currencyCode = "ars")
        }
    }

    @Test fun t08ProjectionIsUnavailableUntilW08NoInventedSeries() {
        val page = snapshot(FaroV5FinanceCoverage.READY)
        assertEquals(
            FaroV5FinanceProjectionAvailability.NO_DISPONIBLE_HASTA_W08,
            page.projection,
        )
        assertTrue(FaroV5FinanceSurfacePolicy.projectionMessage().contains("W08"))
        assertFalse(FaroV5FinanceSurfacePolicy.projectionMessage().contains("$0"))
        assertEquals(
            FaroV5FinanceProjectionAvailability.entries,
            listOf(FaroV5FinanceProjectionAvailability.NO_DISPONIBLE_HASTA_W08),
        )
    }
}
