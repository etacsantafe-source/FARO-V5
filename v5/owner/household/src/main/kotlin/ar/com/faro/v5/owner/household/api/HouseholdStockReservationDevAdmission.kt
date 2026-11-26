package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.owner.household.api.HouseholdStockReservationDevDecision.Blocked

data class HouseholdStockReservationPosition(
    val principal: String,
    val generation: HouseholdReviewGeneration,
    val stockItem: String,
    val unit: HouseholdNeedUnit,
    val revision: Long,
    val physicalThousandths: Long,
    val reservedThousandths: Long,
    val release: HouseholdStockReservationReleasePosition? = null,
)

data class HouseholdStockReservationReleasePosition(
    val reservationRef: String,
    val remainingThousandths: Long,
)

/** Must return a fresh, principal-bound observation of the SQL14 owner. */
fun interface HouseholdStockReservationPositionPort {
    fun current(request: HouseholdStockReservationDevRequest): HouseholdStockReservationPosition?
}

/** Revocable permission to admit this exact reservation action to foreground Review. */
fun interface HouseholdStockReservationDevConsentPort {
    fun explicitlyAllows(request: HouseholdStockReservationDevRequest): Boolean
}

sealed interface HouseholdStockReservationDevDecision {
    data class ReviewOnly(
        val review: HouseholdStockReservationReview,
        val contract: OperationContractDescriptor,
        val position: HouseholdStockReservationPosition,
    ) : HouseholdStockReservationDevDecision

    enum class Blocked : HouseholdStockReservationDevDecision {
        CONTEXT, CONTRACT, CONSENT, POSITION, QUANTITY, STALE_REVIEW,
    }
}

class HouseholdStockReservationDevAdmission(
    private val contracts: OperationContractLookupPort,
    private val context: () -> HouseholdStockReceiveDevContext?,
    private val positions: HouseholdStockReservationPositionPort,
    private val consent: HouseholdStockReservationDevConsentPort,
) {
    fun review(request: HouseholdStockReservationDevRequest): HouseholdStockReservationDevDecision {
        if (!request.isWellFormed() || context() != request.context) return Blocked.CONTEXT
        val operation = request.action.operation
        val contract = contracts.current(operation)
        if (contract == null || contract.operationId != operation || contract.capabilityId != CapabilityId("N-035") ||
            contract.canonicalExecutor != ExecutorId("household") ||
            contract.executionProfile != ExecutionProfile.REALITY_MUTATION || contract.exposure != ContractExposure.BINDABLE
        ) return Blocked.CONTRACT
        if (!consent.explicitlyAllows(request)) return Blocked.CONSENT

        val position = positions.current(request)
        if (position == null || position.principal != request.context.principal ||
            position.generation != request.context.activePointer || position.stockItem != request.stockItem ||
            position.unit != request.quantity.unit || position.revision != request.ledgerRevision ||
            position.physicalThousandths < 0 || position.reservedThousandths !in 0L..position.physicalThousandths
        ) return Blocked.POSITION

        val limit = when (request.action) {
            HouseholdStockReservationAction.RESERVE -> position.physicalThousandths - position.reservedThousandths
            HouseholdStockReservationAction.RELEASE -> {
                val release = position.release
                if (release == null || release.reservationRef != request.reservationRef ||
                    release.remainingThousandths !in 0L..position.reservedThousandths
                ) return Blocked.POSITION
                release.remainingThousandths
            }
        }
        if (request.quantity.thousandths > limit) return Blocked.QUANTITY
        if (!consent.explicitlyAllows(request)) return Blocked.CONSENT
        if (context() != request.context) return Blocked.CONTEXT
        if (contracts.current(operation) != contract) return Blocked.CONTRACT
        return HouseholdStockReservationDevDecision.ReviewOnly(HouseholdStockReservationReview(request), contract, position)
    }

    fun revalidate(previous: HouseholdStockReservationDevDecision.ReviewOnly): HouseholdStockReservationDevDecision =
        when (val current = review(previous.review.request)) {
            is Blocked -> current
            previous -> current
            else -> Blocked.STALE_REVIEW
        }
}
