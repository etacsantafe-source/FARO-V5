package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceRead
import ar.com.faro.v5.store.reality.RealityFinance9ObservedReadPolicy
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceAccount
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceState
import ar.com.faro.v5.surface.FaroV5FinanceAccountCertainty
import ar.com.faro.v5.surface.FaroV5FinanceCoverage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic, forgeable observed DTOs: these tests prove mapping ONLY, not access. */
class S4W01B3B1Finance9UiMapperTest {
    private fun confirmed(
        id: String = "account-ars",
        currency: String = "ARS",
        balance: Long = 100L,
    ) = RealityFinance9OpeningProvenanceAccount(
        accountId = id, displayName = "Cuenta " + id, currencyCode = currency,
        state = RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL,
        confirmedOpeningMinor = 0L, ledgerBalanceMinor = balance,
    )

    private fun unknown(
        id: String,
        state: RealityFinance9OpeningProvenanceState,
    ) = RealityFinance9OpeningProvenanceAccount(
        accountId = id, displayName = "Cuenta " + id, currencyCode = "ARS",
        state = state,
    )

    private fun observed(
        accounts: List<RealityFinance9OpeningProvenanceAccount>,
        revision: Long = 17L,
    ) = RealityFinance9ObservedSourceRead(
        observedPolicy = RealityFinance9ObservedReadPolicy(
            generationId = "dev-generation", pointerSequence = 7L,
            policyEpoch = 9L, globalPolicyRevision = 3L,
        ),
        openingSource = RealityFinance9OpeningProvenanceSnapshot(
            sourceRevision = revision, accounts = accounts,
        ),
    )

    @Test fun m01NullSourceIsUnavailableAndCarriesNoIdentifiers() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(null)
        assertEquals(FaroV5FinanceCoverage.UNAVAILABLE, page.coverage)
        assertTrue(page.accounts.isEmpty())
        assertNull(page.generationId)
        assertNull(page.sourceRevision)
    }

    @Test fun m02EmptyObservedSourceIsDistinguishedFromUnavailable() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(emptyList()))
        assertEquals(FaroV5FinanceCoverage.EMPTY_CONFIRMED, page.coverage)
        assertEquals("dev-generation", page.generationId)
        assertEquals(7L, page.pointerSequence)
        assertEquals(17L, page.sourceRevision)
        assertTrue(page.accounts.isEmpty())
    }

    @Test fun m03RealObservedArsUsdAndNegativeAreKeptSeparate() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(
            confirmed("ars", "ARS", -157L), confirmed("usd", "USD", 300L),
        )))
        assertEquals(FaroV5FinanceCoverage.READY, page.coverage)
        assertEquals(listOf("ARS", "USD"), page.accounts.map { it.currencyCode })
        assertEquals(listOf(-157L, 300L), page.accounts.map { it.confirmedBalanceMinor })
        assertTrue(page.accounts.all { it.certainty == FaroV5FinanceAccountCertainty.VERIFIED })
        assertTrue(page.accounts.all { it.sourceRevision == 17L })
    }

    @Test fun m04ObservedConfirmedZeroIsNotInventedFromNull() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(
            confirmed(balance = 0L),
        )))
        assertEquals(FaroV5FinanceCoverage.READY, page.coverage)
        assertEquals(0L, page.accounts.single().confirmedBalanceMinor)
    }

    @Test fun m05LegacyUnverifiedOpeningHasNoAmountOrZero() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(
            confirmed(), unknown("legacy", RealityFinance9OpeningProvenanceState.LEGACY_UNVERIFIED),
        )))
        assertEquals(FaroV5FinanceCoverage.PARTIAL, page.coverage)
        val row = page.accounts[1]
        assertEquals(FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED, row.certainty)
        assertNull(row.confirmedBalanceMinor)
        assertNull(row.sourceRevision)
    }

    @Test fun m06MissingOpeningHasNoAmountOrZero() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(
            unknown("missing", RealityFinance9OpeningProvenanceState.MISSING_CONFIRMATION),
        )))
        assertEquals(FaroV5FinanceCoverage.PARTIAL, page.coverage)
        assertEquals(FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED,
            page.accounts.single().certainty)
        assertNull(page.accounts.single().confirmedBalanceMinor)
    }

    @Test fun m07UnknownCurrencyScaleCannotDisplayMinorUnits() {
        val page = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(
            confirmed("yen", "JPY", 10000L),
        )))
        assertEquals(FaroV5FinanceCoverage.PARTIAL, page.coverage)
        assertEquals(FaroV5FinanceAccountCertainty.PARTIAL, page.accounts.single().certainty)
        assertNull(page.accounts.single().confirmedBalanceMinor)
        assertNull(page.accounts.single().sourceRevision)
    }

    @Test fun m08MalformedDuplicateSourceDeniesConstructionAndMapperIsStateless() {
        assertThrows(IllegalArgumentException::class.java) {
            observed(listOf(confirmed("dup"), confirmed("dup")))
        }
        val first = S4W01B3B1Finance9UiMapper.mapObserved(observed(listOf(confirmed())))
        val second = S4W01B3B1Finance9UiMapper.mapObserved(null)
        assertEquals(FaroV5FinanceCoverage.READY, first.coverage)
        assertEquals(FaroV5FinanceCoverage.UNAVAILABLE, second.coverage)
        assertTrue(second.accounts.isEmpty())
    }
}
