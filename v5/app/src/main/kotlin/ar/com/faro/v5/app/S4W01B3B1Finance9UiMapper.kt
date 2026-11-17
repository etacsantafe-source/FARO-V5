package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceRead
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceState
import ar.com.faro.v5.surface.FaroV5FinanceAccountCertainty
import ar.com.faro.v5.surface.FaroV5FinanceAccountLine
import ar.com.faro.v5.surface.FaroV5FinanceCoverage
import ar.com.faro.v5.surface.FaroV5FinanceUiSnapshot

/**
 * W01-B3B1B1A STAGED9 DEV ONLY, INERT VALUE MAPPER.
 *
 * An ObservedSourceRead is a forgeable DTO, NEVER an authenticated read grant.
 * This object does not implement FaroV5FinanceReadPort or persist/cache values;
 * no Activity, CompositionRoot, Chat or shipping navigation may mount it.
 * Future B3B1B1B must authenticate local principal, UID, pointer/host epoch and
 * cross-process invalidations independently BEFORE presenting these values.
 */
internal object S4W01B3B1Finance9UiMapper {
    /** null or malformed source is UNAVAILABLE with no account identifiers. */
    fun mapObserved(observed: RealityFinance9ObservedSourceRead?): FaroV5FinanceUiSnapshot {
        if (observed == null) {
            return FaroV5FinanceUiSnapshot(FaroV5FinanceCoverage.UNAVAILABLE)
        }
        return runCatching {
            val source = observed.openingSource
            val lines = source.accounts.map { account ->
                val sqlConfirmed =
                    account.state == RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL
                val knownMinorScale = account.currencyCode == "ARS" ||
                    account.currencyCode == "USD"
                val verified = sqlConfirmed && knownMinorScale
                val certainty = when {
                    verified -> FaroV5FinanceAccountCertainty.VERIFIED
                    sqlConfirmed -> FaroV5FinanceAccountCertainty.PARTIAL
                    else -> FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED
                }
                FaroV5FinanceAccountLine(
                    opaqueAccountId = account.accountId,
                    label = account.displayName,
                    currencyCode = account.currencyCode,
                    certainty = certainty,
                    confirmedBalanceMinor = if (verified) account.ledgerBalanceMinor else null,
                    sourceRevision = if (verified) source.sourceRevision else null,
                )
            }
            val coverage = when {
                lines.isEmpty() -> FaroV5FinanceCoverage.EMPTY_CONFIRMED
                lines.all { it.certainty == FaroV5FinanceAccountCertainty.VERIFIED } ->
                    FaroV5FinanceCoverage.READY
                else -> FaroV5FinanceCoverage.PARTIAL
            }
            FaroV5FinanceUiSnapshot(
                coverage = coverage,
                generationId = observed.observedPolicy.generationId,
                pointerSequence = observed.observedPolicy.pointerSequence,
                sourceRevision = source.sourceRevision,
                accounts = lines,
            )
        }.getOrElse { FaroV5FinanceUiSnapshot(FaroV5FinanceCoverage.UNAVAILABLE) }
    }
}
