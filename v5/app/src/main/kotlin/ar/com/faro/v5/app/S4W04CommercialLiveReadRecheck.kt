package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Warning as PreviewWarning
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage as PreviewCoverage
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED

/** A momentary DEV display check using the host's D1 read admission and clock. */
internal class S4W04CommercialLiveReadRecheck(
    private val coverage: S4W04CommercialCartCoverage,
    private val readNowEpochMillis: () -> Long?,
) {
    enum class Rejection {
        INVALID_PREVIEW_OR_BASELINE,
        MISMATCHED_FENCE,
        CLOCK_INVALID,
        DENIED_OR_STALE,
        SOURCE_UNVERIFIED,
        SOURCE_CHANGED,
        STALE_OBSERVATION_FOR_PREVIEW,
    }

    enum class Coverage { PARTIAL, OBSERVED_ALL_SELECTED }

    enum class Warning {
        READ_ONLY_OBSERVED_CHECK_NOT_APPLY,
        NO_CURRENT_PRICE_OR_BENEFIT_CERTIFICATION,
        RECHECK_BEFORE_FUTURE_ACTION,
    }

    sealed interface Decision {
        data class RecheckedForDisplay(
            val previewDigest: String,
            val checkedAtEpochMillis: Long,
            val sourceWatermark: String,
            val cartFingerprint: String,
            val selectedNeedCount: Int,
            val coverage: Coverage,
        ) : Decision {
            val warnings: List<Warning> get() = Warning.entries
        }

        data class Rejected(val reason: Rejection) : Decision
    }

    fun recheck(
        draft: S4W04CommercialCartDraft,
        baselineCoverage: CommercialCartCoverageView,
        preview: Preview,
    ): Decision {
        if (preview.cartFingerprint != draft.fingerprint ||
            baselineCoverage.cartFingerprint != draft.fingerprint ||
            preview.sourceWatermark != baselineCoverage.sourceWatermark ||
            preview.asOfEpochMillis != baselineCoverage.asOfEpochMillis
        ) return Decision.Rejected(Rejection.MISMATCHED_FENCE)

        if (!digestFormat.matches(preview.digest) || preview.sourceWatermark.isBlank() ||
            preview.asOfEpochMillis < 0L || preview.warnings != PreviewWarning.entries ||
            baselineCoverage.maxObservationAgeMillis !in 1L..MAX_AGE_MILLIS ||
            baselineCoverage.notice != OBSERVED_NOTICE || !matchesDraft(baselineCoverage, draft) ||
            preview.rows.size !in 1..MAX_LINES ||
            preview.rows.any { it.estimate.selectedNeedIds.size !in 1..MAX_LINES }
        ) return Decision.Rejected(Rejection.INVALID_PREVIEW_OR_BASELINE)

        val selected = preview.rows.flatMap { row ->
            row.estimate.selectedNeedIds.map { it to row.estimate.key }
        }
        val groupByNeed = selected.toMap()
        val draftNeeds = draft.lines.map { it.needId }.toSet()
        if (selected.size != groupByNeed.size || !draftNeeds.containsAll(groupByNeed.keys) ||
            preview.missingNeedIds != (draftNeeds - groupByNeed.keys).sortedBy { it.value }
        ) return Decision.Rejected(Rejection.INVALID_PREVIEW_OR_BASELINE)

        val asOf = preview.asOfEpochMillis
        val before = now() ?: return Decision.Rejected(Rejection.CLOCK_INVALID)
        if (before < asOf || before - asOf > MAX_AGE_MILLIS) {
            return Decision.Rejected(Rejection.CLOCK_INVALID)
        }

        val window = baselineCoverage.maxObservationAgeMillis
        val historical = when (val first = project(draft, asOf, window)) {
            is CommercialCartCoverageDecision.Observed -> first.view
            is CommercialCartCoverageDecision.Rejected -> return rejectProjection(first.reason)
        }
        if (historical != baselineCoverage) return Decision.Rejected(Rejection.SOURCE_CHANGED)

        val current = when (val projected = project(draft, before, window)) {
            is CommercialCartCoverageDecision.Observed -> projected.view
            is CommercialCartCoverageDecision.Rejected -> return rejectProjection(projected.reason)
        }
        // D1 classifies records by time, but time does not change the underlying history items.
        if (current.sourceWatermark != historical.sourceWatermark ||
            !matchesDraft(current, draft) ||
            current.lines.zip(historical.lines).any { (live, prior) ->
                live.truncated != prior.truncated ||
                    live.history.map { it.item } != prior.history.map { it.item }
            }
        ) return Decision.Rejected(Rejection.SOURCE_CHANGED)

        for ((live, prior) in current.lines.zip(historical.lines)) {
            val key = groupByNeed[live.needId] ?: continue
            if (live.truncated || live.status != CommercialCartLineCoverageStatus.COMPARABLE_OBSERVED_ONLY) {
                return Decision.Rejected(Rejection.STALE_OBSERVATION_FOR_PREVIEW)
            }
            // D3A carries the group, but omits the exact selected record. Every historical
            // candidate for that selection must still be comparable to avoid substituting one.
            val candidates = prior.history.filter { entry ->
                val observation = entry.item.observation
                val terms = entry.item.terms
                entry.comparisonStatus == COMPARABLE_BASE_OBSERVED &&
                    observation.merchant == key.merchant && observation.currencyCode == key.currencyCode &&
                    terms != null && terms.localityCode == key.localityCode && terms.paymentMethod == key.paymentMethod &&
                    terms.packCount == 1
            }
            if (candidates.isEmpty() || !live.history.containsAll(candidates)) {
                return Decision.Rejected(Rejection.STALE_OBSERVATION_FOR_PREVIEW)
            }
        }

        when (val last = project(draft, asOf, window)) {
            is CommercialCartCoverageDecision.Observed ->
                if (last.view != historical) return Decision.Rejected(Rejection.SOURCE_CHANGED)
            is CommercialCartCoverageDecision.Rejected -> return rejectProjection(last.reason)
        }

        val after = now() ?: return Decision.Rejected(Rejection.CLOCK_INVALID)
        if (after < before || after - before > MAX_DURATION_MILLIS || after - asOf > MAX_AGE_MILLIS) {
            return Decision.Rejected(Rejection.CLOCK_INVALID)
        }

        val displayCoverage = if (groupByNeed.keys != draftNeeds || preview.coverage == PreviewCoverage.PARTIAL) {
            Coverage.PARTIAL
        } else {
            Coverage.OBSERVED_ALL_SELECTED
        }
        return Decision.RecheckedForDisplay(
            preview.digest, after, historical.sourceWatermark, draft.fingerprint,
            groupByNeed.size, displayCoverage,
        )
    }

    private fun matchesDraft(view: CommercialCartCoverageView, draft: S4W04CommercialCartDraft): Boolean {
        val lines = draft.lines
        return lines.size in 1..MAX_LINES && view.lines.size == lines.size &&
            lines.map { it.needId }.toSet().size == lines.size &&
            view.cartFingerprint == draft.fingerprint &&
            view.lines.zip(lines).all { (read, expected) ->
                read.needId == expected.needId && read.productKey == expected.productKey &&
                    read.requestedPackCount == expected.requestedPackCount
            }
    }

    private fun project(draft: S4W04CommercialCartDraft, at: Long, window: Long): CommercialCartCoverageDecision =
        try {
            coverage.project(draft, at, window)
        } catch (_: Exception) {
            CommercialCartCoverageDecision.Rejected(CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
        }

    private fun now(): Long? = try {
        readNowEpochMillis()?.takeIf { it >= 0L }
    } catch (_: Exception) {
        null
    }

    private fun rejectProjection(reason: CommercialCartCoverageRejection): Decision.Rejected =
        Decision.Rejected(when (reason) {
            CommercialCartCoverageRejection.NOT_AUTHORIZED,
            CommercialCartCoverageRejection.STALE_CART -> Rejection.DENIED_OR_STALE
            CommercialCartCoverageRejection.INVALID_WINDOW -> Rejection.INVALID_PREVIEW_OR_BASELINE
            CommercialCartCoverageRejection.SOURCE_UNVERIFIED -> Rejection.SOURCE_UNVERIFIED
            CommercialCartCoverageRejection.SOURCE_CHANGED -> Rejection.SOURCE_CHANGED
        })

    private companion object {
        const val MAX_LINES = 200
        const val MAX_AGE_MILLIS = 365L * 24 * 60 * 60 * 1000
        const val MAX_DURATION_MILLIS = 60_000L
        const val OBSERVED_NOTICE = "OBSERVED_ONLY_NOT_A_CURRENT_QUOTE_OR_BUDGET"
        val digestFormat = Regex("[0-9a-f]{64}")
    }
}
