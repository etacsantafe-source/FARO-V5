package ar.com.faro.v5.app

import ar.com.faro.v5.surface.FaroV5FinanceAccountCertainty
import ar.com.faro.v5.surface.FaroV5FinanceAccountLine
import ar.com.faro.v5.surface.FaroV5FinanceCoverage
import ar.com.faro.v5.surface.FaroV5FinanceUiSnapshot

/**
 * D1B1B: real ACTIVE FinanceSnapshot but not Finance9 confirmed provenance.
 * No monetary amount from the legacy owner may be labeled VERIFIED. D1B1C
 * performs the actual Finance9 cutover separately.
 */
internal object S4W01D1B1BActiveFinanceUiMapper {
    fun mapObserved(observed: S4W01D1B1AActiveFinanceObservation?): FaroV5FinanceUiSnapshot {
        if (observed == null) return FaroV5FinanceUiSnapshot(FaroV5FinanceCoverage.UNAVAILABLE)
        return runCatching {
            require(observed.generationId.isNotBlank() &&
                observed.pointerSequence > 0L &&
                observed.ownerRevision == observed.source.revision &&
                observed.source.accounts.size <= 100)
            val accounts = observed.source.accounts.map { account ->
                // No conversion and no finance8 ledger treated as Finance9 opening.
                FaroV5FinanceAccountLine(
                    opaqueAccountId = account.accountId.value,
                    label = account.displayName.value,
                    currencyCode = account.currency.value,
                    certainty = FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED,
                )
            }
            FaroV5FinanceUiSnapshot(
                coverage = if (accounts.isEmpty()) FaroV5FinanceCoverage.EMPTY_CONFIRMED
                           else FaroV5FinanceCoverage.PARTIAL,
                generationId = observed.generationId,
                pointerSequence = observed.pointerSequence,
                sourceRevision = observed.ownerRevision,
                accounts = accounts,
            )
        }.getOrElse { FaroV5FinanceUiSnapshot(FaroV5FinanceCoverage.UNAVAILABLE) }
    }
}
