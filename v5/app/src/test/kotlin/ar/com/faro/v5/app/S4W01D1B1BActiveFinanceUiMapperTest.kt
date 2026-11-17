package ar.com.faro.v5.app

import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.surface.FaroV5FinanceAccountCertainty
import ar.com.faro.v5.surface.FaroV5FinanceCoverage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class S4W01D1B1BActiveFinanceUiMapperTest {
    private fun account(id: String, name: String, currency: String, opening: Long) =
        FinanceAccountSnapshot(
            FinanceAccountId.of(id),
            FinanceAccountDisplayName.of(name),
            FinanceCurrencyCode.of(currency),
            FinanceAccountNature.CASH,
            opening,
        )

    private fun observation(vararg accounts: FinanceAccountSnapshot, revision: Long = 4) =
        S4W01D1B1AActiveFinanceObservation(
            generationId = "active-8",
            pointerSequence = 9,
            ownerRevision = revision,
            source = FinanceSnapshot(accounts.toList(), revision),
        )

    @Test fun b01AbsentRealSessionIsUnavailableNotZero() {
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(null)
        assertEquals(FaroV5FinanceCoverage.UNAVAILABLE, result.coverage)
        assertTrue(result.accounts.isEmpty())
        assertNull(result.generationId)
    }

    @Test fun b02ActiveEmptyOwnerIsConfirmedOnlyForItsOwnSource() {
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(observation())
        assertEquals(FaroV5FinanceCoverage.EMPTY_CONFIRMED, result.coverage)
        assertEquals("active-8", result.generationId)
        assertEquals(9L, result.pointerSequence)
    }

    @Test fun b03ArsAndUsdKeepSeparateUnverifiedAccounts() {
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(
            observation(account("ars", "Efectivo", "ARS", 1000),
                        account("usd", "Dólares", "USD", 5000)),
        )
        assertEquals(FaroV5FinanceCoverage.PARTIAL, result.coverage)
        assertEquals(listOf("ARS", "USD"), result.accounts.map { it.currencyCode })
        assertTrue(result.accounts.all {
            it.certainty == FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED &&
            it.confirmedBalanceMinor == null && it.sourceRevision == null
        })
    }

    @Test fun b04LargeLegacyOpeningNeverPromotesVerifiedMoney() {
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(
            observation(account("a", "Banco", "ARS", Long.MAX_VALUE)),
        )
        assertEquals(FaroV5FinanceCoverage.PARTIAL, result.coverage)
        assertNull(result.accounts.single().confirmedBalanceMinor)
    }

    @Test fun b05UnknownCurrencyIsNeverClaimedVerified() {
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(
            observation(account("eur", "Viaje", "EUR", 120)),
        )
        assertEquals(FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED,
                     result.accounts.single().certainty)
        assertNull(result.accounts.single().confirmedBalanceMinor)
    }

    @Test fun b06ContradictoryRevisionFailsClosedWithoutAccountIds() {
        val inconsistent = S4W01D1B1AActiveFinanceObservation(
            generationId = "active-8", pointerSequence = 9, ownerRevision = 5,
            source = FinanceSnapshot(listOf(account("secret", "Secreto", "ARS", 99)), 4),
        )
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(inconsistent)
        assertEquals(FaroV5FinanceCoverage.UNAVAILABLE, result.coverage)
        assertTrue(result.accounts.isEmpty())
        assertNull(result.sourceRevision)
    }
}
