package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.*

/**
 * G1A: policy-only admission for N-016 Undo of an N-035 stock.receive receipt.
 * This file does NOT authenticate COMMITTED itself; the G1B host must supply
 * a trusted origin from BOTH sealed Workspace Review and Reality Apply journal.
 * No write, Apply binding, authorization grant, UI or SQL13 opener lives here.
 */
data class HouseholdStockUndoDevRequest(
    val context: HouseholdStockReceiveDevContext,
    val intent: UndoExecuteIntent,
    val undoIntentKey: String,
    val expectedLedgerRevision: Long,
) {
    override fun toString(): String = "HouseholdStockUndoDevRequest(redacted)"
}

/** Only constructed by the G1B trusted provenance adapter after checking the original COMMITTED source. */
data class HouseholdStockUndoCommittedOrigin(
    val sourceReview: ConversationPlanReviewContext,
    val sourceReceipt: ExecutionReceipt,
    val stockReview: HouseholdStockReceiveReview,
) {
    override fun toString(): String = "HouseholdStockUndoCommittedOrigin(redacted)"
}

/** G0's SQL13 receipt candidate projected into owner types, not an authority token. */
data class HouseholdStockUndoCurrentPosition(
    val activePointer: HouseholdReviewGeneration,
    val principal: String,
    val receiptId: String,
    val purchaseReference: String,
    val stockItem: String,
    val quantity: HouseholdNeedQuantity,
    val ledgerRevision: Long,
    val netReceivedThousandths: Long,
    val receivedAtMs: Long,
)

fun interface TrustedStockUndoCommittedOriginPort {
    /** Null means untrusted, missing, stale or uncommitted origin; do not infer from a receipt row. */
    fun verified(intent: UndoExecuteIntent): HouseholdStockUndoCommittedOrigin?
}

fun interface TrustedStockUndoPositionPort {
    fun current(receiptId: String): HouseholdStockUndoCurrentPosition?
}

fun interface HouseholdStockUndoDevConsentPort {
    /** Must be a new N-016 consent, never inherited from N-035 stock.receive. */
    fun explicitlyAllows(request: HouseholdStockUndoDevRequest): Boolean
}

sealed interface HouseholdStockUndoDevDecision {
    /** A preview, not permission to call RealityStockCompensationApplyPort. */
    data class ReviewOnly(
        val request: HouseholdStockUndoDevRequest,
        val contract: OperationContractDescriptor,
        val origin: HouseholdStockUndoCommittedOrigin,
        val position: HouseholdStockUndoCurrentPosition,
    ) : HouseholdStockUndoDevDecision {
        override fun toString(): String = "HouseholdStockUndoDevDecision.ReviewOnly(redacted)"
    }

    enum class Blocked : HouseholdStockUndoDevDecision {
        CONTEXT, CONTRACT, CONSENT, ORIGIN, POSITION, STALE_REVIEW,
    }
}

class HouseholdStockUndoDevAdmission(
    private val contracts: OperationContractLookupPort,
    private val currentContext: () -> HouseholdStockReceiveDevContext?,
    private val origin: TrustedStockUndoCommittedOriginPort,
    private val positions: TrustedStockUndoPositionPort,
    private val consent: HouseholdStockUndoDevConsentPort,
) {
    private val operation = OperationId("undo.execute")
    private val originalOperation = OperationId("stock.receive")

    fun review(request: HouseholdStockUndoDevRequest): HouseholdStockUndoDevDecision {
        if (!isCurrent(request)) return HouseholdStockUndoDevDecision.Blocked.CONTEXT
        val contract = contracts.current(operation)
        if (contract == null || contract.operationId != operation ||
            contract.capabilityId != CapabilityId("N-016") ||
            contract.canonicalExecutor != ExecutorId("undo-coordinator") ||
            contract.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            contract.exposure != ContractExposure.BINDABLE
        ) return HouseholdStockUndoDevDecision.Blocked.CONTRACT

        if (!consent.explicitlyAllows(request)) return HouseholdStockUndoDevDecision.Blocked.CONSENT
        val intent = request.intent
        if (intent.sourceOperationId != originalOperation || intent.targetObjectId == request.undoIntentKey) {
            return HouseholdStockUndoDevDecision.Blocked.ORIGIN
        }
        val verified = origin.verified(intent) ?: return HouseholdStockUndoDevDecision.Blocked.ORIGIN
        val stock = verified.stockReview.request
        if (verified.sourceReceipt != intent.sourceReceipt || verified.sourceReview != intent.sourceReview ||
            verified.sourceReceipt.planFingerprint != verified.sourceReview.planFingerprint ||
            verified.stockReview.payload != intent.originalCreatePayload ||
            stock.operation != originalOperation || !stock.context.isolatedDev || !stock.context.foreground ||
            stock.context.principal != request.context.principal || stock.intentKey != intent.targetObjectId ||
            stock.intentKey == request.undoIntentKey
        ) return HouseholdStockUndoDevDecision.Blocked.ORIGIN

        // The originating SQL12 generation may be DIFFERENT from selected SQL13 after journaled cutover.
        // G1B must authenticate origin lineage, not reject this historic pointer difference here.
        val position = positions.current(intent.targetObjectId)
            ?: return HouseholdStockUndoDevDecision.Blocked.POSITION
        if (position.activePointer != request.context.activePointer ||
            position.principal != request.context.principal ||
            position.receiptId != stock.intentKey ||
            position.purchaseReference != stock.purchaseReference ||
            position.stockItem != stock.stockItem ||
            position.quantity != stock.quantity ||
            position.ledgerRevision != request.expectedLedgerRevision ||
            position.quantity.thousandths <= 0 ||
            position.netReceivedThousandths < position.quantity.thousandths ||
            position.receivedAtMs < 0 || intent.cancelledAtEpochMillis < position.receivedAtMs
        ) return HouseholdStockUndoDevDecision.Blocked.POSITION

        if (!consent.explicitlyAllows(request)) return HouseholdStockUndoDevDecision.Blocked.CONSENT
        if (!isCurrent(request)) return HouseholdStockUndoDevDecision.Blocked.CONTEXT
        if (origin.verified(intent) != verified ||
            positions.current(intent.targetObjectId) != position
        ) return HouseholdStockUndoDevDecision.Blocked.STALE_REVIEW
        return HouseholdStockUndoDevDecision.ReviewOnly(request, contract, verified, position)
    }

    fun revalidate(review: HouseholdStockUndoDevDecision.ReviewOnly): HouseholdStockUndoDevDecision =
        when (val latest = this.review(review.request)) {
            is HouseholdStockUndoDevDecision.Blocked -> latest
            review -> latest
            else -> HouseholdStockUndoDevDecision.Blocked.STALE_REVIEW
        }

    private fun isCurrent(request: HouseholdStockUndoDevRequest): Boolean {
        val context = request.context
        val key = request.undoIntentKey
        return context.isolatedDev && context.foreground && context.hostEpoch > 0 &&
            request.expectedLedgerRevision > 0 &&
            key.length in 1..128 && key.none { it.isWhitespace() || it.isISOControl() } &&
            context.principal.length in 1..128 &&
            context.principal.none { it.isWhitespace() || it.isISOControl() } &&
            currentContext() == context
    }
}
