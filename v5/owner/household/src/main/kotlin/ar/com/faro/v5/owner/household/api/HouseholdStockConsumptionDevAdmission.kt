package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.owner.household.api.HouseholdStockConsumptionDevDecision.Blocked

/** Fresh, principal-bound proof of the requested source from the SQL15 owner. */
data class HouseholdStockConsumptionSourcePosition(
    val sourceRef: String,
    val principal: String,
    val stockItem: String,
    val unit: HouseholdNeedUnit,
    val remainingThousandths: Long,
)

data class HouseholdStockConsumptionPosition(
    val principal: String,
    val generation: HouseholdReviewGeneration,
    val stockItem: String,
    val unit: HouseholdNeedUnit,
    val revision: Long,
    val physicalThousandths: Long,
    val reservedThousandths: Long,
    val source: HouseholdStockConsumptionSourcePosition?,
)

fun interface HouseholdStockConsumptionPositionPort {
    fun current(request: HouseholdStockConsumptionDevRequest): HouseholdStockConsumptionPosition?
}

fun interface HouseholdStockConsumptionDevConsentPort {
    fun explicitlyAllows(request: HouseholdStockConsumptionDevRequest): Boolean
}

sealed interface HouseholdStockConsumptionDevDecision {
    data class ReviewOnly(
        val review: HouseholdStockConsumptionReview,
        val contract: OperationContractDescriptor,
        val position: HouseholdStockConsumptionPosition,
    ) : HouseholdStockConsumptionDevDecision

    enum class Blocked : HouseholdStockConsumptionDevDecision {
        CONTEXT, CONTRACT, CONSENT, POSITION, QUANTITY, STALE_REVIEW,
    }
}

/** Admits canonical foreground Review only. No stock mutation, journal or Apply. */
class HouseholdStockConsumptionDevAdmission(
    private val contracts: OperationContractLookupPort,
    private val context: () -> HouseholdStockReceiveDevContext?,
    private val positions: HouseholdStockConsumptionPositionPort,
    private val consent: HouseholdStockConsumptionDevConsentPort,
) {
    fun review(request: HouseholdStockConsumptionDevRequest): HouseholdStockConsumptionDevDecision {
        if (!request.isWellFormed() || context() != request.context) return Blocked.CONTEXT
        val operation = HouseholdStockConsumptionReview.OPERATION
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
            position.physicalThousandths < 0 ||
            position.reservedThousandths !in 0L..position.physicalThousandths
        ) return Blocked.POSITION

        val source = position.source
        if (source == null || source.sourceRef != request.sourceRef || source.principal != position.principal ||
            source.stockItem != position.stockItem || source.unit != position.unit ||
            source.remainingThousandths < 0 ||
            source.remainingThousandths > position.physicalThousandths
        ) return Blocked.POSITION
        val available = position.physicalThousandths - position.reservedThousandths
        if (request.quantity.thousandths > available || request.quantity.thousandths > source.remainingThousandths) {
            return Blocked.QUANTITY
        }
        if (!consent.explicitlyAllows(request)) return Blocked.CONSENT
        if (context() != request.context) return Blocked.CONTEXT
        if (contracts.current(operation) != contract) return Blocked.CONTRACT
        return HouseholdStockConsumptionDevDecision.ReviewOnly(HouseholdStockConsumptionReview(request), contract, position)
    }

    fun revalidate(previous: HouseholdStockConsumptionDevDecision.ReviewOnly): HouseholdStockConsumptionDevDecision =
        when (val current = review(previous.review.request)) {
            is Blocked -> current
            previous -> current
            else -> Blocked.STALE_REVIEW
        }
}
