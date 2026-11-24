package ar.com.faro.v5.owner.household.api

/** Inert calculation only. Never records receipt, stock, purchase or payment. */
data class HouseholdStockReceiptInput(
    val purchaseReference: String,
    val purchased: HouseholdNeedQuantity,
    val previouslyReceivedThousandths: Long,
    val candidate: HouseholdNeedQuantity,
)

sealed interface HouseholdStockReceiptDecision {
    data class Ready(
        val purchasedThousandths: Long,
        val previouslyReceivedThousandths: Long,
        val candidateThousandths: Long,
        val afterReceiptThousandths: Long,
        val pendingThousandths: Long,
        val unit: HouseholdNeedUnit,
    ) : HouseholdStockReceiptDecision

    enum class Rejected : HouseholdStockReceiptDecision {
        UNAVAILABLE, INVALID_PURCHASE_REFERENCE, INVALID_RECEIVED_LEDGER,
        INCOMPATIBLE_UNIT, EXCEEDS_PURCHASE,
    }
}

/** Does NOT issue grants, confirm human intent, persist receipts or reserve stock. */
object HouseholdStockReceiptPreview {
    fun calculate(input: HouseholdStockReceiptInput?): HouseholdStockReceiptDecision {
        if (input == null) return HouseholdStockReceiptDecision.Rejected.UNAVAILABLE
        val key = input.purchaseReference
        if (key.length !in 1..128 || key.any { it.isWhitespace() || it.isISOControl() }) {
            return HouseholdStockReceiptDecision.Rejected.INVALID_PURCHASE_REFERENCE
        }
        val purchased = input.purchased.thousandths
        val already = input.previouslyReceivedThousandths
        if (already < 0 || already > purchased) {
            return HouseholdStockReceiptDecision.Rejected.INVALID_RECEIVED_LEDGER
        }
        if (input.purchased.unit != input.candidate.unit) {
            return HouseholdStockReceiptDecision.Rejected.INCOMPATIBLE_UNIT
        }
        val remaining = purchased - already
        if (input.candidate.thousandths > remaining) {
            return HouseholdStockReceiptDecision.Rejected.EXCEEDS_PURCHASE
        }
        val after = already + input.candidate.thousandths // bounded by purchased <= Int.MAX_VALUE
        return HouseholdStockReceiptDecision.Ready(
            purchasedThousandths = purchased,
            previouslyReceivedThousandths = already,
            candidateThousandths = input.candidate.thousandths,
            afterReceiptThousandths = after,
            pendingThousandths = purchased - after,
            unit = input.purchased.unit,
        )
    }
}
