package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot

/**
 * Host-supplied identity of a specific admitted Household read. This is NOT a grant.
 * The future C0B host must obtain these values from its trusted session and recheck
 * its own Household and Commercial read authorities before publishing any evidence.
 */
internal data class CommercialNeedReadFence(
    val generationId: String,
    val pointerSequence: Long,
    val hostEpoch: String,
    val principal: String,
) {
    init {
        require(generationId.isNotBlank() && generationId.length <= 160)
        require(pointerSequence > 0L)
        require(hostEpoch.isNotBlank() && hostEpoch.length <= 160)
        require(principal.isNotBlank() && principal.length <= 160)
    }
}

/**
 * An ephemeral, explicitly human-selected *candidate* mapping, not a catalog match
 * and not a durable price/quote/stock record. No receipt, SQL, Apply or I/O here.
 */
internal data class CommercialNeedProductCandidate internal constructor(
    val need: HouseholdNeed,
    val householdRevision: Long,
    val productKey: CommercialProductKey,
    val readFence: CommercialNeedReadFence,
)

internal enum class CommercialNeedProductRejection {
    NOT_AUTHORIZED,
    NEED_NOT_FOUND,
    NEED_NOT_OPEN,
    CLARIFICATION_REQUIRED,
    PRODUCT_NOT_PRESENTED,
    STALE_SNAPSHOT,
    STALE_READ_FENCE,
}

internal sealed interface CommercialNeedProductDecision {
    data class Candidate(val binding: CommercialNeedProductCandidate) : CommercialNeedProductDecision
    data class Rejected(val reason: CommercialNeedProductRejection) : CommercialNeedProductDecision
}

/** Stateless C0A seam. The host MUST supply a previously admitted snapshot/fence. */
internal object S4W04CommercialNeedProductSelection {
    private const val MAX_PRESENTED_KEYS = 200

    fun select(
        snapshot: HouseholdNeedsSnapshot?,
        needId: HouseholdNeedId?,
        productKey: CommercialProductKey?,
        presentedProducts: List<CommercialProductKey>,
        readFence: CommercialNeedReadFence?,
        explicitlyChosen: Boolean,
        isOwnerAdmitted: () -> Boolean,
    ): CommercialNeedProductDecision {
        if (!isOwnerAdmitted() || snapshot == null || readFence == null || snapshot.revision < 0L) {
            return denied(CommercialNeedProductRejection.NOT_AUTHORIZED)
        }
        // Never resolve an ambiguous product by name, vendor, quantity or unit.
        if (!explicitlyChosen || needId == null || productKey == null) {
            return denied(CommercialNeedProductRejection.CLARIFICATION_REQUIRED)
        }
        val matches = snapshot.needs.filter { it.id == needId }
        if (matches.size != 1) return denied(CommercialNeedProductRejection.NEED_NOT_FOUND)
        val need = matches.single()
        if (need.status != HouseholdNeedStatus.OPEN) {
            return denied(CommercialNeedProductRejection.NEED_NOT_OPEN)
        }
        // No fuzzy match; even duplicate candidate keys make the selection unsafe.
        if (presentedProducts.isEmpty() || presentedProducts.size > MAX_PRESENTED_KEYS ||
            presentedProducts.size != presentedProducts.toSet().size || productKey !in presentedProducts
        ) {
            return denied(CommercialNeedProductRejection.PRODUCT_NOT_PRESENTED)
        }
        if (!isOwnerAdmitted()) return denied(CommercialNeedProductRejection.NOT_AUTHORIZED)
        return CommercialNeedProductDecision.Candidate(
            CommercialNeedProductCandidate(need, snapshot.revision, productKey, readFence)
        )
    }

    fun revalidate(
        candidate: CommercialNeedProductCandidate,
        currentSnapshot: HouseholdNeedsSnapshot?,
        currentFence: CommercialNeedReadFence?,
        isOwnerAdmitted: () -> Boolean,
    ): CommercialNeedProductDecision {
        if (!isOwnerAdmitted() || currentSnapshot == null || currentFence == null) {
            return denied(CommercialNeedProductRejection.NOT_AUTHORIZED)
        }
        if (candidate.readFence != currentFence) {
            return denied(CommercialNeedProductRejection.STALE_READ_FENCE)
        }
        val current = currentSnapshot.needs.filter { it.id == candidate.need.id }
        if (currentSnapshot.revision != candidate.householdRevision || current.size != 1 ||
            current.single().revision != candidate.need.revision ||
            current.single() != candidate.need || current.single().status != HouseholdNeedStatus.OPEN
        ) {
            return denied(CommercialNeedProductRejection.STALE_SNAPSHOT)
        }
        if (!isOwnerAdmitted()) return denied(CommercialNeedProductRejection.NOT_AUTHORIZED)
        return CommercialNeedProductDecision.Candidate(candidate)
    }

    private fun denied(reason: CommercialNeedProductRejection) =
        CommercialNeedProductDecision.Rejected(reason)
}
