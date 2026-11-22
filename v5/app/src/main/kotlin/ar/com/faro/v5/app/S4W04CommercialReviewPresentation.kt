package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision.RecheckedForDisplay
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Decision.Annotated
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Notice
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.Decision.TracedForDisplay
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.TracedLine
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Decision.Summary
import ar.com.faro.v5.app.S4W04ObservedCartBudget.Estimate
import ar.com.faro.v5.app.S4W04ObservedCartBudget.ExplicitChoice
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/** Read-only projection of a historical commercial review with amounts scoped to each group. */
internal object S4W04CommercialReviewPresentation {
    enum class Rejection { INVALID_TRACE, GROUP_MISMATCH, INCOMPLETE_OR_INCONSISTENT_DISPLAY, OVERFLOW }

    enum class Warning {
        READ_ONLY_HISTORICAL_NOT_CURRENT,
        WHAT_IF_IS_NOT_PRICE,
        NO_FINANCE_STOCK_APPLY,
        RECHECK_AGAIN_BEFORE_ACTION,
    }

    enum class ScenarioScope { PARTIAL, ALL_MODELED }

    data class ScenarioDisclosure(
        val modeledBaseMinor: Long,
        val hypotheticalDiscountMinor: Long,
        val hypotheticalAfterMinor: Long,
        val scope: ScenarioScope,
    )

    @ConsistentCopyVisibility
    data class GroupDisplay internal constructor(
        val key: GroupKey,
        val selectedNeedCount: Int,
        val observedHistoricalSubtotalMinor: Long,
        val scenario: ScenarioDisclosure?,
        val unverifiedBenefits: List<Notice>,
        val lines: List<TracedLine>,
    )

    sealed interface Decision {
        @ConsistentCopyVisibility
        data class Display internal constructor(
            val previewDigest: String,
            val traceDigest: String,
            internal val cartFingerprint: String,
            internal val sourceWatermark: String,
            val asOfEpochMillis: Long,
            val checkedAtEpochMillis: Long,
            val coverage: Coverage,
            val groups: List<GroupDisplay>,
            val missingNeedIds: List<HouseholdNeedId>,
            val unmodeledNeedIds: List<HouseholdNeedId>,
        ) : Decision {
            val warnings: List<Warning> get() = Warning.entries
        }

        data class Rejected(val reason: Rejection) : Decision
    }

    fun project(
        draft: S4W04CommercialCartDraft,
        historicalCoverage: CommercialCartCoverageView,
        estimate: Estimate,
        explicitChoices: List<ExplicitChoice>,
        preview: Preview,
        rechecked: RecheckedForDisplay,
    ): Decision {
        val trace = when (val result = S4W04ExplicitObservationTrace.trace(
            draft, historicalCoverage, estimate, explicitChoices, preview, rechecked,
        )) {
            is TracedForDisplay -> result
            is S4W04ExplicitObservationTrace.Decision.Rejected -> return Decision.Rejected(
                when (result.reason) {
                    S4W04ExplicitObservationTrace.Rejection.OVERFLOW -> Rejection.OVERFLOW
                    S4W04ExplicitObservationTrace.Rejection.PREVIEW_INCONSISTENT -> Rejection.GROUP_MISMATCH
                    else -> Rejection.INVALID_TRACE
                },
            )
        }

        // D3C reconciles observed lines. Reseal D3A to also bind scenarios and benefit text.
        val canonical = reseal(preview)
            ?: return Decision.Rejected(Rejection.INCOMPLETE_OR_INCONSISTENT_DISPLAY)
        val byGroup = trace.lines.groupBy { it.groupKey }
        val groups = ArrayList<GroupDisplay>(canonical.rows.size)
        try {
            for ((row, disclosure) in canonical.rows) {
                val lines = byGroup[row.key] ?: return Decision.Rejected(Rejection.GROUP_MISMATCH)
                val modeledIds = row.modeledNeedIds.toSet()
                val historical = lines.fold(0L) { sum, line -> Math.addExact(sum, line.estimatedMinor) }
                val modeled = lines.filter { it.needId in modeledIds }.fold(0L) { sum, line ->
                    Math.addExact(sum, line.estimatedMinor)
                }
                if (historical != row.observedBaseSubtotalMinor || modeled != row.modeledBaseMinor) {
                    return Decision.Rejected(Rejection.GROUP_MISMATCH)
                }
                if (!row.key.merchant.safeLiteral() || lines.any { !it.sourceRef.safeLiteral() } ||
                    disclosure.notices.any {
                        it.details?.safeLiteral() == false || it.sourceRef?.safeLiteral() == false
                    }
                ) return Decision.Rejected(Rejection.INCOMPLETE_OR_INCONSISTENT_DISPLAY)

                val scenario = if (modeledIds.isEmpty()) null else ScenarioDisclosure(
                    modeled, row.modeledHypotheticalDiscountMinor, row.modeledHypotheticalAfterMinor,
                    if (modeledIds.size == lines.size) ScenarioScope.ALL_MODELED else ScenarioScope.PARTIAL,
                )
                groups.add(GroupDisplay(
                    row.key, lines.size, historical, scenario, frozen(disclosure.notices), frozen(lines),
                ))
            }
        } catch (_: ArithmeticException) {
            return Decision.Rejected(Rejection.OVERFLOW)
        }

        return Decision.Display(
            trace.previewDigest, trace.traceDigest, canonical.cartFingerprint, canonical.sourceWatermark,
            trace.asOfEpochMillis, trace.checkedAtEpochMillis, trace.coverage, frozen(groups),
            frozen(canonical.missingNeedIds), frozen(canonical.unmodeledNeedIds),
        )
    }

    private fun reseal(preview: Preview): Preview? {
        val summary = Summary(
            preview.cartFingerprint, preview.sourceWatermark, preview.asOfEpochMillis,
            preview.rows.map { it.estimate }, preview.missingNeedIds, preview.unmodeledNeedIds,
            preview.coverage,
        )
        val disclosures = Annotated(
            preview.cartFingerprint, preview.sourceWatermark, preview.asOfEpochMillis,
            preview.coverage, preview.unmodeledNeedIds, preview.rows.map { it.disclosure },
        )
        val canonical = S4W04CommercialPreReviewEnvelope.preview(summary, disclosures) as? Preview
        return canonical?.takeIf { it.digest == preview.digest }
    }

    private fun String.safeLiteral(): Boolean =
        Charsets.UTF_8.newEncoder().canEncode(this) && none {
            it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069'
        }

    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
