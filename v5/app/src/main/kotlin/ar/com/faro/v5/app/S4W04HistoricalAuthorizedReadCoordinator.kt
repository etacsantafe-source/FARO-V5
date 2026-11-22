package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision.RecheckedForDisplay
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Decision.Annotated
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.GroupCoverage
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Row
import ar.com.faro.v5.app.S4W04ObservedCartBudget.Decision.ObservedEstimate
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Decision.Display
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot

/**
 * F1: a trusted host supplies all SIX read/identity callbacks; this coordinator
 * owns no grants, DB, Activity, UI, Apply or current quote authority.
 *
 * F2 must provide a SAME-GENERATION, trusted ACTIVE source adapter and foreground
 * revocation before an Android screen may consume the returned historical state.
 */
internal class S4W04HistoricalAuthorizedReadCoordinator(
    private val currentHousehold: () -> HouseholdNeedsSnapshot?,
    private val currentFence: () -> CommercialNeedReadFence?,
    private val currentEvidence: () -> CommercialEvidenceStagedTypedPortabilitySnapshot?,
    private val householdAdmission: (HouseholdNeedId) -> Boolean,
    private val evidenceAdmission: (CommercialProductKey) -> Boolean,
    private val readNow: () -> Long?,
) {
    enum class Rejection {
        NO_EXPLICIT_SELECTION,
        INVALID_REQUEST,
        NOT_AUTHORIZED_OR_STALE,
        HISTORICAL_SOURCE_UNAVAILABLE,
        INVALID_SELECTED_OBSERVATION,
        NO_COMPARABLE_OBSERVATIONS,
        FAILED_HISTORICAL_RECHECK,
        INCONSISTENT_PRESENTATION,
        POST_READ_CHANGED,
    }

    sealed interface Decision {
        data class Historical(val display: Display) : Decision
        data class Rejected(val reason: Rejection) : Decision
    }

    fun project(
        draft: S4W04CommercialCartDraft,
        explicitObservations: List<S4W04ObservedCartBudget.ExplicitChoice>,
        asOfEpochMillis: Long,
        maxObservationAgeMillis: Long,
    ): Decision {
        if (explicitObservations.isEmpty()) return denied(Rejection.NO_EXPLICIT_SELECTION)
        if (asOfEpochMillis < 0L || maxObservationAgeMillis !in 1L..MAX_AGE_MILLIS ||
            draft.lines.size !in 1..200 ||
            explicitObservations.size > draft.lines.size ||
            explicitObservations.map { it.needId }.toSet().size != explicitObservations.size
        ) return denied(Rejection.INVALID_REQUEST)
        if (!admitted(draft)) return denied(Rejection.NOT_AUTHORIZED_OR_STALE)
        return try {
            val coverage = S4W04CommercialCartCoverage(
                currentHouseholdSnapshot = currentHousehold,
                currentFence = currentFence,
                currentCommercialSnapshot = currentEvidence,
                householdAdmission = householdAdmission,
                commercialReadAdmission = evidenceAdmission,
            )
            val baseline = when (val result = coverage.project(draft, asOfEpochMillis, maxObservationAgeMillis)) {
                is CommercialCartCoverageDecision.Observed -> result.view
                is CommercialCartCoverageDecision.Rejected ->
                    return denied(if (result.reason == CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
                        Rejection.HISTORICAL_SOURCE_UNAVAILABLE else Rejection.NOT_AUTHORIZED_OR_STALE)
            }
            // No automatic cheapest merchant or current price inference. Human selection exact.
            val observed = when (val result = S4W04ObservedCartBudget.estimate(baseline, explicitObservations)) {
                is ObservedEstimate -> result.estimate
                is S4W04ObservedCartBudget.Decision.Rejected ->
                    return denied(Rejection.INVALID_SELECTED_OBSERVATION)
            }
            if (observed.lines.isEmpty() || observed.subtotals.isEmpty()) {
                return denied(Rejection.NO_COMPARABLE_OBSERVATIONS)
            }

            // F1 models NO promotion; absent promotions remain unmodeled, not 0%-confirmed.
            val rows = observed.subtotals.map { subtotal ->
                val key = subtotal.key
                val ids = observed.lines.filter {
                    it.currencyCode == key.currencyCode && it.merchant == key.merchant &&
                        it.localityCode == key.localityCode && it.paymentMethod == key.paymentMethod
                }.map { it.needId }.sortedBy { it.value }
                Row(
                    key = key,
                    observedBaseSubtotalMinor = subtotal.estimatedMinor,
                    modeledBaseMinor = 0L,
                    modeledHypotheticalDiscountMinor = 0L,
                    modeledHypotheticalAfterMinor = 0L,
                    selectedNeedIds = ids,
                    modeledNeedIds = emptyList(),
                    groupCoverage = GroupCoverage.PARTIAL,
                )
            }
            val summary = Summary(
                observed.cartFingerprint, observed.sourceWatermark, observed.asOfEpochMillis,
                rows, observed.missingNeedIds,
                draft.lines.map { it.needId }.sortedBy { it.value },
                Coverage.PARTIAL,
            )
            val disclosures = S4W04DeferredCommercialBenefitNotes.annotate(summary, emptyList()) as? Annotated
                ?: return denied(Rejection.INCONSISTENT_PRESENTATION)
            val preview = S4W04CommercialPreReviewEnvelope.preview(summary, disclosures) as? Preview
                ?: return denied(Rejection.INCONSISTENT_PRESENTATION)
            val proof = S4W04CommercialLiveReadRecheck(coverage, readNow)
                .recheck(draft, baseline, preview) as? RecheckedForDisplay
                ?: return denied(Rejection.FAILED_HISTORICAL_RECHECK)
            val display = S4W04CommercialReviewPresentation.project(
                draft, baseline, observed, explicitObservations, preview, proof,
            ) as? Display ?: return denied(Rejection.INCONSISTENT_PRESENTATION)

            // Revalidate last: ACL and full D1 source snapshot must remain identical.
            if (!admitted(draft)) return denied(Rejection.NOT_AUTHORIZED_OR_STALE)
            val finalCoverage = coverage.project(draft, asOfEpochMillis, maxObservationAgeMillis)
            if (finalCoverage !is CommercialCartCoverageDecision.Observed ||
                finalCoverage.view != baseline || !admitted(draft)
            ) return denied(Rejection.POST_READ_CHANGED)

            Decision.Historical(display)
        } catch (_: Exception) {
            denied(Rejection.HISTORICAL_SOURCE_UNAVAILABLE)
        }
    }

    private fun admitted(draft: S4W04CommercialCartDraft) = runCatching {
        draft.lines.all { householdAdmission(it.needId) && evidenceAdmission(it.productKey) }
    }.getOrDefault(false)

    private fun denied(reason: Rejection) = Decision.Rejected(reason)

    private companion object {
        const val MAX_AGE_MILLIS = 365L * 24L * 60L * 60L * 1000L
    }
}
