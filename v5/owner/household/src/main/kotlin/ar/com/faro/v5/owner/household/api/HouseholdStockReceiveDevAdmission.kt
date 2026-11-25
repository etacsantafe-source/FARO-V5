package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId

data class HouseholdStockReceiveDevContext(
    val principal: String,
    val hostEpoch: Long,
    val activePointer: HouseholdReviewGeneration,
    val isolatedDev: Boolean,
    val foreground: Boolean,
)

data class HouseholdStockReceiveDevRequest(
    val operation: OperationId,
    val context: HouseholdStockReceiveDevContext,
    val stockItem: String,
    val purchaseReference: String,
    val purchaseRevision: Long,
    val intentKey: String,
    val quantity: HouseholdNeedQuantity,
)

data class ConfirmedStockPurchaseLine(
    val principal: String,
    val activePointer: HouseholdReviewGeneration,
    val stockItem: String,
    val purchaseReference: String,
    val revision: Long,
    val purchased: HouseholdNeedQuantity,
    val receivedThousandths: Long,
)

/** Future DEV composition must supply an owner-controlled adapter with confirmed purchase facts. */
fun interface TrustedConfirmedPurchaseLinePort {
    fun current(request: HouseholdStockReceiveDevRequest): ConfirmedStockPurchaseLine?
}

/** Reads live, revocable permission and explicit consent for this exact request. */
fun interface HouseholdStockReceiveDevConsentPort {
    fun explicitlyAllows(request: HouseholdStockReceiveDevRequest): Boolean
}

sealed interface HouseholdStockReceiveDevDecision {
    /** A review snapshot carrying no runtime authority and reserving no quantities. */
    data class ReviewOnly(
        val request: HouseholdStockReceiveDevRequest,
        val contract: OperationContractDescriptor,
        val purchase: ConfirmedStockPurchaseLine,
        val preview: HouseholdStockReceiptDecision.Ready,
    ) : HouseholdStockReceiveDevDecision

    enum class Blocked : HouseholdStockReceiveDevDecision {
        CONTEXT, CONTRACT, CONSENT, PURCHASE, QUANTITY, STALE_REVIEW,
    }
}

class HouseholdStockReceiveDevAdmission(
    private val contracts: OperationContractLookupPort,
    private val context: () -> HouseholdStockReceiveDevContext?,
    private val purchases: TrustedConfirmedPurchaseLinePort,
    private val consent: HouseholdStockReceiveDevConsentPort,
) {
    fun review(request: HouseholdStockReceiveDevRequest): HouseholdStockReceiveDevDecision {
        if (!isCurrent(request)) return HouseholdStockReceiveDevDecision.Blocked.CONTEXT

        val contract = contracts.current(request.operation)
        if (request.operation != OperationId("stock.receive") ||
            contract == null || contract.operationId != request.operation ||
            contract.capabilityId != CapabilityId("N-035") ||
            contract.canonicalExecutor != ExecutorId("household") ||
            contract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            contract.exposure != ContractExposure.BINDABLE
        ) return HouseholdStockReceiveDevDecision.Blocked.CONTRACT

        if (!consent.explicitlyAllows(request)) return HouseholdStockReceiveDevDecision.Blocked.CONSENT
        val purchase = purchases.current(request)
        if (purchase == null || purchase.principal != request.context.principal ||
            purchase.activePointer != request.context.activePointer ||
            purchase.stockItem != request.stockItem || purchase.purchaseReference != request.purchaseReference ||
            purchase.revision != request.purchaseRevision
        ) return HouseholdStockReceiveDevDecision.Blocked.PURCHASE

        val preview = HouseholdStockReceiptPreview.calculate(
            HouseholdStockReceiptInput(
                purchase.purchaseReference, purchase.purchased, purchase.receivedThousandths, request.quantity,
            ),
        )
        if (preview !is HouseholdStockReceiptDecision.Ready) return HouseholdStockReceiveDevDecision.Blocked.QUANTITY
        if (!consent.explicitlyAllows(request)) return HouseholdStockReceiveDevDecision.Blocked.CONSENT
        if (!isCurrent(request)) return HouseholdStockReceiveDevDecision.Blocked.CONTEXT
        return HouseholdStockReceiveDevDecision.ReviewOnly(request, contract, purchase, preview)
    }

    fun revalidate(review: HouseholdStockReceiveDevDecision.ReviewOnly): HouseholdStockReceiveDevDecision {
        val current = this.review(review.request)
        return when {
            current is HouseholdStockReceiveDevDecision.Blocked -> current
            current == review -> current
            else -> HouseholdStockReceiveDevDecision.Blocked.STALE_REVIEW
        }
    }

    private fun isCurrent(request: HouseholdStockReceiveDevRequest): Boolean =
        request.context.isolatedDev && request.context.foreground &&
            request.context.hostEpoch > 0 && request.purchaseRevision > 0 &&
            listOf(request.context.principal, request.stockItem, request.purchaseReference, request.intentKey)
                .all { token -> token.length in 1..128 && token.none { it.isWhitespace() || it.isISOControl() } } &&
            context() == request.context
}
