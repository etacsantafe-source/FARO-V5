package ar.com.faro.v5.app

import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Decision.RecheckedForDisplay
import ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelope.Decision.Preview
import ar.com.faro.v5.app.S4W04MerchantWhatIfSummary.Coverage.PARTIAL as PARTIAL_PREVIEW
import ar.com.faro.v5.app.S4W04ObservedCartBudget.Estimate
import ar.com.faro.v5.app.S4W04ObservedCartBudget.EstimatedLine
import ar.com.faro.v5.app.S4W04ObservedCartBudget.ExplicitChoice
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.util.Collections

/** Traces an explicit historical selection for display. */
internal object S4W04ExplicitObservationTrace {
    enum class Rejection {
        MISMATCHED_FENCE,
        INVALID_COVERAGE_OR_DRAFT,
        INVALID_ESTIMATE_OR_SELECTION,
        PREVIEW_INCONSISTENT,
        INVALID_RECHECK,
        OVERFLOW,
    }

    enum class Provenance { HISTORICAL_USER_EXPLICIT }
    enum class Warning { ONLY_HISTORICAL_EVIDENCE_NOT_CURRENT_QUOTE, NO_FINANCE_STOCK_APPLY }

    data class TracedLine(
        val needId: HouseholdNeedId,
        val productKey: String,
        val groupKey: GroupKey,
        val observedAtEpochMillis: Long,
        val sourceRef: String,
        val requestedPackCount: Int,
        val observedAmountMinor: Long,
        val estimatedMinor: Long,
    ) {
        val provenance: Provenance get() = Provenance.HISTORICAL_USER_EXPLICIT
    }

    sealed interface Decision {
        @ConsistentCopyVisibility
        data class TracedForDisplay internal constructor(
            val previewDigest: String,
            val traceDigest: String,
            val asOfEpochMillis: Long,
            val checkedAtEpochMillis: Long,
            val coverage: Coverage,
            val lines: List<TracedLine>,
        ) : Decision {
            val warnings: List<Warning> get() = Warning.entries
        }

        data class Rejected(val reason: Rejection) : Decision
    }

    private const val MAX_LINES = 200
    private const val COVERAGE_NOTICE = "OBSERVED_ONLY_NOT_A_CURRENT_QUOTE_OR_BUDGET"
    private val digestFormat = Regex("[0-9a-f]{64}")

    fun trace(
        draft: S4W04CommercialCartDraft,
        historicalCoverage: CommercialCartCoverageView,
        estimate: Estimate,
        explicitChoices: List<ExplicitChoice>,
        preview: Preview,
        rechecked: RecheckedForDisplay,
    ): Decision {
        val watermark = historicalCoverage.sourceWatermark
        val asOf = historicalCoverage.asOfEpochMillis
        if (draft.fingerprint != historicalCoverage.cartFingerprint ||
            draft.fingerprint != estimate.cartFingerprint ||
            draft.fingerprint != preview.cartFingerprint ||
            draft.fingerprint != rechecked.cartFingerprint ||
            watermark != estimate.sourceWatermark || watermark != preview.sourceWatermark ||
            watermark != rechecked.sourceWatermark || asOf != estimate.asOfEpochMillis ||
            asOf != preview.asOfEpochMillis || preview.digest != rechecked.previewDigest ||
            !digestFormat.matches(preview.digest) || !watermark.validText() || asOf < 0L
        ) return Decision.Rejected(Rejection.MISMATCHED_FENCE)

        if (!matchesDraft(draft, historicalCoverage)) {
            return Decision.Rejected(Rejection.INVALID_COVERAGE_OR_DRAFT)
        }
        if (explicitChoices.size !in 1..MAX_LINES || estimate.lines.size !in 1..MAX_LINES) {
            return Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION)
        }

        val choices = explicitChoices.toList()
        val reconstructed = try {
            S4W04ObservedCartBudget.estimate(historicalCoverage, choices)
        } catch (_: ArithmeticException) {
            return Decision.Rejected(Rejection.OVERFLOW)
        } catch (_: Exception) {
            return Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION)
        }

        val exact = when (reconstructed) {
            is S4W04ObservedCartBudget.Decision.ObservedEstimate -> reconstructed.estimate
            is S4W04ObservedCartBudget.Decision.Rejected -> {
                return Decision.Rejected(when (reconstructed.reason) {
                    S4W04ObservedCartBudget.Rejection.INVALID_COVERAGE -> Rejection.INVALID_COVERAGE_OR_DRAFT
                    S4W04ObservedCartBudget.Rejection.OVERFLOW -> Rejection.OVERFLOW
                    else -> Rejection.INVALID_ESTIMATE_OR_SELECTION
                })
            }
        }
        if (exact != estimate || choices.any { !it.hasEncodableIdentity() }) {
            return Decision.Rejected(Rejection.INVALID_ESTIMATE_OR_SELECTION)
        }

        try {
            if (!matchesPreview(exact, preview)) return Decision.Rejected(Rejection.PREVIEW_INCONSISTENT)
        } catch (_: ArithmeticException) {
            return Decision.Rejected(Rejection.OVERFLOW)
        }

        val displayCoverage = if (preview.coverage == PARTIAL_PREVIEW || exact.missingNeedIds.isNotEmpty()) {
            Coverage.PARTIAL
        } else {
            Coverage.OBSERVED_ALL_SELECTED
        }
        if (rechecked.checkedAtEpochMillis < asOf || rechecked.selectedNeedCount != exact.lines.size ||
            rechecked.coverage != displayCoverage ||
            rechecked.warnings != S4W04CommercialLiveReadRecheck.Warning.entries
        ) return Decision.Rejected(Rejection.INVALID_RECHECK)

        val byNeed = choices.associateBy { it.needId }
        val digest = CanonicalHashBuilder.domain("w04.d3c.selection-trace.v1")
            .string("cartFingerprint", draft.fingerprint)
            .string("sourceWatermark", watermark)
            .string("asOf", asOf.toString())
            .string("previewDigest", preview.digest)
            .string("checkedAt", rechecked.checkedAtEpochMillis.toString())
            .string("coverage", displayCoverage.name)
            .string("lineCount", exact.lines.size.toString())

        val lines = exact.lines.mapIndexed { index, line ->
            val choice = byNeed.getValue(line.needId)
            digest.record(index, line, choice)
            TracedLine(
                line.needId, line.productKey, line.groupKey(), line.observedAtEpochMillis,
                line.sourceRef, line.requestedPackCount, line.observedAmountMinor, line.estimatedMinor,
            )
        }
        return Decision.TracedForDisplay(
            preview.digest, digest.sha256Hex(), asOf, rechecked.checkedAtEpochMillis,
            displayCoverage, Collections.unmodifiableList(lines),
        )
    }

    private fun matchesDraft(
        draft: S4W04CommercialCartDraft,
        coverage: CommercialCartCoverageView,
    ): Boolean {
        val byNeed = coverage.lines.associateBy { it.needId }
        return coverage.notice == COVERAGE_NOTICE && draft.lineCount in 1..MAX_LINES &&
            coverage.lines.size == draft.lineCount && byNeed.size == draft.lineCount &&
            draft.lines.all { line ->
                val observed = byNeed[line.needId]
                observed != null && observed.productKey == line.productKey &&
                    observed.requestedPackCount == line.requestedPackCount
            }
    }

    private fun matchesPreview(estimate: Estimate, preview: Preview): Boolean {
        val groups = estimate.lines.groupBy { it.groupKey() }
        if (preview.rows.size != groups.size ||
            preview.rows.map { it.estimate.key }.toSet() != groups.keys ||
            preview.missingNeedIds != estimate.missingNeedIds ||
            preview.unmodeledNeedIds.size > MAX_LINES
        ) return false

        val unmodeled = estimate.missingNeedIds.toMutableList()
        for (review in preview.rows) {
            val row = review.estimate
            val lines = groups.getValue(row.key)
            val selected = lines.map { it.needId }
            val modeled = row.modeledNeedIds.toSet()
            if (row.selectedNeedIds != selected || modeled.size != row.modeledNeedIds.size ||
                !selected.containsAll(modeled) ||
                row.observedBaseSubtotalMinor != lines.fold(0L) { sum, line ->
                    Math.addExact(sum, line.estimatedMinor)
                }
            ) return false
            unmodeled.addAll(selected.filterNot(modeled::contains))
        }
        val expected = if (unmodeled.isEmpty()) S4W04MerchantWhatIfSummary.Coverage.ALL_MODELED
            else PARTIAL_PREVIEW
        return preview.coverage == expected && preview.unmodeledNeedIds == unmodeled.sortedBy { it.value }
    }

    private fun EstimatedLine.groupKey() = GroupKey(currencyCode, merchant, localityCode, paymentMethod)

    private fun String.validText(): Boolean = isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(this)

    private fun ExplicitChoice.hasEncodableIdentity(): Boolean {
        val record = selectedObservation.item
        return listOfNotNull(
            needId.value, productKey, record.observation.merchant, record.observation.sourceKind,
            record.observation.sourceRef, record.terms?.conditions, record.terms?.promotionSourceRef,
        ).all { it.validText() }
    }

    private fun CanonicalHashBuilder.record(index: Int, line: EstimatedLine, choice: ExplicitChoice) {
        val field = "line[$index]"
        val selected = choice.selectedObservation
        val observation = selected.item.observation
        val terms = checkNotNull(selected.item.terms)
        string("$field.needId", line.needId.value)
        string("$field.productKey", line.productKey)
        string("$field.currency", line.currencyCode)
        string("$field.merchant", line.merchant)
        string("$field.locality", line.localityCode)
        string("$field.paymentMethod", line.paymentMethod.name)
        string("$field.observedAt", line.observedAtEpochMillis.toString())
        string("$field.acquiredAt", observation.acquiredAtEpochMillis.toString())
        string("$field.sourceKind", observation.sourceKind)
        string("$field.sourceRef", line.sourceRef)
        string("$field.termsFingerprint", terms.fingerprint())
        string("$field.comparisonStatus", selected.comparisonStatus.name)
        string("$field.requestedPackCount", line.requestedPackCount.toString())
        string("$field.observedAmountMinor", line.observedAmountMinor.toString())
        string("$field.estimatedMinor", line.estimatedMinor.toString())
        string("$field.provenance", Provenance.HISTORICAL_USER_EXPLICIT.name)
    }
}
