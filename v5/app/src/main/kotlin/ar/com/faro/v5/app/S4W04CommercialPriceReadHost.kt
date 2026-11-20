package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceOperations
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot

/** These are recorded Evidence observations, not verified current prices or financial facts. */
internal data class CommercialPriceHistoryView(
    val productKey: CommercialProductKey,
    val records: List<CommercialEvidenceRecord>,
    val currenciesPresent: Set<CommercialCurrencyCode>,
    val localityKnown: Boolean = false,
    val quoteValidityKnown: Boolean = false,
) {
    init {
        require(!localityKnown && !quoteValidityKnown) {
            "Evidence schema v1 cannot certify locality or quote validity"
        }
        require(records.all { it.observation.productKey == productKey })
        require(currenciesPresent == records.map { it.observation.currencyCode }.toSet())
    }
}

internal enum class CommercialPriceReadRejection {
    NOT_AUTHORIZED,
    STALE_SELECTION,
    INVALID_LIMIT,
    WRONG_CONTRACT,
    STORE_UNAVAILABLE,
}

internal sealed interface CommercialPriceReadDecision {
    data class Observed(val history: CommercialPriceHistoryView) : CommercialPriceReadDecision
    data class Rejected(val reason: CommercialPriceReadRejection) : CommercialPriceReadDecision
}

/**
 * DEV integration of C0A selection to existing same-generation Commercial Evidence.
 *
 * The trusted caller supplies an already-open Household10 session (one root lease),
 * source-of-truth snapshot/fence callbacks and TWO independent owner grants.
 * Neither an untrusted product key nor a candidate is a capability token.
 */
internal class S4W04CommercialPriceReadHost(
    private val session: FaroV5GenerationStoreGraph.Household10DevApplySession,
    private val catalog: OperationContractLookupPort,
    private val currentSnapshot: () -> HouseholdNeedsSnapshot?,
    private val currentFence: () -> CommercialNeedReadFence?,
    private val householdAdmission: (HouseholdNeedId) -> Boolean,
    private val commercialAdmission: (CommercialProductKey) -> Boolean,
) {
    fun read(candidate: CommercialNeedProductCandidate, limit: Int): CommercialPriceReadDecision {
        if (limit !in 1..200) return rejected(CommercialPriceReadRejection.INVALID_LIMIT)
        val operation = CommercialEvidenceOperations.PRICE_HISTORY_READ
        val contract = catalog.current(operation) ?: return rejected(CommercialPriceReadRejection.WRONG_CONTRACT)
        if (contract.operationId != operation ||
            contract.capabilityId != CommercialEvidenceOperations.CAPABILITY_ID ||
            contract.canonicalExecutor != CommercialEvidenceOperations.EXECUTOR_ID ||
            contract.executionProfile != ExecutionProfile.READ_ONLY ||
            contract.exposure != ContractExposure.BINDABLE
        ) return rejected(CommercialPriceReadRejection.WRONG_CONTRACT)

        fun admitsHousehold() = runCatching {
            householdAdmission(candidate.need.id)
        }.getOrDefault(false)
        fun admitsCommercial() = runCatching {
            commercialAdmission(candidate.productKey)
        }.getOrDefault(false)

        if (!admitsHousehold() || !admitsCommercial()) {
            return rejected(CommercialPriceReadRejection.NOT_AUTHORIZED)
        }
        if (!selectionCurrent(candidate, ::admitsHousehold)) {
            return rejected(CommercialPriceReadRejection.STALE_SELECTION)
        }

        // The graph owns the same existing root lease and gates both admissions
        // before and after the read; there is NO writable Evidence port here.
        val records = runCatching {
            session.authorizedCommercialPriceHistory(
                contract, candidate.productKey, limit, ::admitsHousehold, ::admitsCommercial,
            )
        }.getOrNull() ?: return rejected(CommercialPriceReadRejection.STORE_UNAVAILABLE)

        if (!admitsHousehold() || !admitsCommercial()) {
            return rejected(CommercialPriceReadRejection.NOT_AUTHORIZED)
        }
        if (!selectionCurrent(candidate, ::admitsHousehold)) {
            return rejected(CommercialPriceReadRejection.STALE_SELECTION)
        }
        val observed = records.toList()
        return CommercialPriceReadDecision.Observed(
            CommercialPriceHistoryView(
                productKey = candidate.productKey,
                records = observed,
                currenciesPresent = observed.map { it.observation.currencyCode }.toSet(),
            )
        )
    }

    private fun selectionCurrent(
        candidate: CommercialNeedProductCandidate,
        admitsHousehold: () -> Boolean,
    ): Boolean = runCatching {
        S4W04CommercialNeedProductSelection.revalidate(
            candidate, currentSnapshot(), currentFence(), admitsHousehold,
        ) is CommercialNeedProductDecision.Candidate
    }.getOrDefault(false)

    private fun rejected(reason: CommercialPriceReadRejection) =
        CommercialPriceReadDecision.Rejected(reason)
}
