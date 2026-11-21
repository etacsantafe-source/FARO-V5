package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialObservedComparisonCoverage
import ar.com.faro.v5.evidence.CommercialObservedComparisonGroup
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialTypedPriceComparison
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.util.Collections

/** An observed Evidence2 price history is NEVER a confirmed current commercial quote. */
internal enum class CommercialCartLineCoverageStatus {
    NO_DATA,
    HISTORICAL_PARTIAL,
    COMPARABLE_OBSERVED_ONLY,
}

internal enum class CommercialCartCoverageRejection {
    NOT_AUTHORIZED,
    STALE_CART,
    INVALID_WINDOW,
    SOURCE_UNVERIFIED,
    SOURCE_CHANGED,
}

internal data class CommercialCartCoverageLine(
    val needId: HouseholdNeedId,
    val productKey: CommercialProductKey,
    val requestedPackCount: Int,
    val history: List<CommercialObservedHistoryEntry>,
    val groups: List<CommercialObservedComparisonGroup>,
    val status: CommercialCartLineCoverageStatus,
    val truncated: Boolean,
) {
    val observedRecordsCount: Int get() = history.size
}

internal data class CommercialCartCoverageView(
    val cartFingerprint: String,
    val sourceWatermark: String,
    val asOfEpochMillis: Long,
    val maxObservationAgeMillis: Long,
    val lines: List<CommercialCartCoverageLine>,
    val notice: String = "OBSERVED_ONLY_NOT_A_CURRENT_QUOTE_OR_BUDGET",
)

internal sealed interface CommercialCartCoverageDecision {
    data class Observed(val view: CommercialCartCoverageView) : CommercialCartCoverageDecision
    data class Rejected(val reason: CommercialCartCoverageRejection) : CommercialCartCoverageDecision
}

/**
 * D1 isolated STAGED DEV projection, read only. The host supplies already-admitted
 * Household, commercial grants and its single Evidence2 source snapshot.
 * No store is opened here, no quote/stock verification, no money calculation.
 */
internal class S4W04CommercialCartCoverage(
    private val currentHouseholdSnapshot: () -> HouseholdNeedsSnapshot?,
    private val currentFence: () -> CommercialNeedReadFence?,
    private val currentCommercialSnapshot: () -> CommercialEvidenceStagedTypedPortabilitySnapshot?,
    private val householdAdmission: (HouseholdNeedId) -> Boolean,
    private val commercialReadAdmission: (CommercialProductKey) -> Boolean,
) {
    fun project(
        draft: S4W04CommercialCartDraft,
        asOfEpochMillis: Long,
        maxObservationAgeMillis: Long,
    ): CommercialCartCoverageDecision {
        if (asOfEpochMillis < 0L || maxObservationAgeMillis !in 1L..MAX_AGE_MILLIS) {
            return rejected(CommercialCartCoverageRejection.INVALID_WINDOW)
        }
        if (!admitted(draft)) return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        val householdBefore = runCatching { currentHouseholdSnapshot() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        val fenceBefore = runCatching { currentFence() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        if (!cartCurrent(draft, householdBefore, fenceBefore)) {
            return rejected(CommercialCartCoverageRejection.STALE_CART)
        }
        // Snapshot copy prevents mutable input list aliases from quietly changing
        // the "before" value during comparison; terms and observations are immutable.
        val source = runCatching { currentCommercialSnapshot() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
        val before = source.copy(records = source.records.toList())

        val lines = mutableListOf<CommercialCartCoverageLine>()
        for (line in draft.lines) {
            if (!admitted(draft)) return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
            val history = runCatching {
                CommercialTypedPriceComparison.project(
                    before, line.productKey, asOfEpochMillis,
                    maxObservationAgeMillis, limit = 200,
                )
            }.getOrNull() ?: return rejected(CommercialCartCoverageRejection.SOURCE_UNVERIFIED)
            val status = when (history.coverage) {
                CommercialObservedComparisonCoverage.NO_OBSERVATIONS ->
                    CommercialCartLineCoverageStatus.NO_DATA
                CommercialObservedComparisonCoverage.PARTIAL_OBSERVATIONS_ONLY ->
                    CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL
                CommercialObservedComparisonCoverage.COMPARABLE_OBSERVED_HISTORY_ONLY ->
                    CommercialCartLineCoverageStatus.COMPARABLE_OBSERVED_ONLY
            }
            lines += CommercialCartCoverageLine(
                needId = line.needId,
                productKey = line.productKey,
                requestedPackCount = line.requestedPackCount,
                history = Collections.unmodifiableList(history.history.toList()),
                groups = Collections.unmodifiableList(history.groups.map { group ->
                    group.copy(
                        merchantEntries = Collections.unmodifiableList(
                            group.merchantEntries.toList()
                        )
                    )
                }),
                status = status,
                truncated = history.truncated,
            )
        }
        if (!admitted(draft)) return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        val after = runCatching { currentCommercialSnapshot() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.SOURCE_CHANGED)
        if (before != after) return rejected(CommercialCartCoverageRejection.SOURCE_CHANGED)
        val householdAfter = runCatching { currentHouseholdSnapshot() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        val fenceAfter = runCatching { currentFence() }.getOrNull()
            ?: return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        if (!admitted(draft)) return rejected(CommercialCartCoverageRejection.NOT_AUTHORIZED)
        if (householdAfter != householdBefore || fenceAfter != fenceBefore ||
            !cartCurrent(draft, householdAfter, fenceAfter)
        ) return rejected(CommercialCartCoverageRejection.STALE_CART)
        return CommercialCartCoverageDecision.Observed(
            CommercialCartCoverageView(
                cartFingerprint = draft.fingerprint,
                sourceWatermark = before.sourceWatermark,
                asOfEpochMillis = asOfEpochMillis,
                maxObservationAgeMillis = maxObservationAgeMillis,
                lines = Collections.unmodifiableList(lines.toList()),
            )
        )
    }

    private fun cartCurrent(
        draft: S4W04CommercialCartDraft,
        snapshot: HouseholdNeedsSnapshot,
        fence: CommercialNeedReadFence,
    ): Boolean =
        runCatching {
            S4W04CommercialCartDraft.revalidate(
                draft, snapshot, fence,
            ) { draft.lines.all { line -> admitsHousehold(line.needId) } } is
                S4W04CommercialCartDraft.Decision.Draft
        }.getOrDefault(false)

    private fun admitted(draft: S4W04CommercialCartDraft) =
        draft.lines.all { admitsHousehold(it.needId) && admitsCommercial(it.productKey) }

    private fun admitsHousehold(needId: HouseholdNeedId) =
        runCatching { householdAdmission(needId) }.getOrDefault(false)

    private fun admitsCommercial(productKey: CommercialProductKey) =
        runCatching { commercialReadAdmission(productKey) }.getOrDefault(false)

    private fun rejected(reason: CommercialCartCoverageRejection) =
        CommercialCartCoverageDecision.Rejected(reason)

    companion object {
        private const val MAX_AGE_MILLIS = 365L * 24L * 60L * 60L * 1000L
    }
}
