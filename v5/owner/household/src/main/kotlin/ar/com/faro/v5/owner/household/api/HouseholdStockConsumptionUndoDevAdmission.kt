package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.*

/**
 * W05-A2C1D: policy-only N-016 Review for undo of an ORIGINAL COMMITTED stock.consume.
 * D never writes SQL15, creates a receipt, opens a writer or authorizes Apply.
 * The future trusted E/F host must authenticate BOTH Workspace and Reality COMMITTED.
 * This version admits only full reversal of a previously untouched consumption.
 */
data class HouseholdStockConsumptionUndoDevRequest(
    val context: HouseholdStockReceiveDevContext,
    val intent: UndoExecuteIntent,
    val undoIntentKey: String,
    val expectedLedgerRevision: Long,
) {
    override fun toString(): String = "HouseholdStockConsumptionUndoDevRequest(redacted)"
}

/** Trusted host adapter proof, not a permission constructed from a lone SQL15 row. */
data class HouseholdStockConsumptionUndoCommittedOrigin(
    val sourceReview: ConversationPlanReviewContext,
    val sourceReceipt: ExecutionReceipt,
    val stockReview: HouseholdStockConsumptionReview,
) {
    override fun toString(): String = "HouseholdStockConsumptionUndoCommittedOrigin(redacted)"
}

/** Captured from live SQL15 owner under a pinned generation, not supplied by chat. */
data class HouseholdStockConsumptionUndoCurrentPosition(
    val activePointer: HouseholdReviewGeneration,
    val principal: String,
    val consumptionRef: String,
    val stockItem: String,
    val unit: HouseholdNeedUnit,
    val sourceRef: String,
    val consumedQuantity: HouseholdNeedQuantity,
    val ledgerRevision: Long,
    val remainingUndoableThousandths: Long,
    val consumedAtMs: Long,
    val newUndoIntentAvailable: Boolean,
) {
    override fun toString(): String = "HouseholdStockConsumptionUndoCurrentPosition(redacted)"
}

fun interface TrustedConsumptionUndoCommittedOriginPort {
    /** Authenticate original Review COMMITTED and matching Reality receipt/attempt. */
    fun verified(intent: UndoExecuteIntent): HouseholdStockConsumptionUndoCommittedOrigin?
}

fun interface TrustedConsumptionUndoPositionPort {
    fun current(consumptionRef: String): HouseholdStockConsumptionUndoCurrentPosition?
}

fun interface HouseholdStockConsumptionUndoConsentPort {
    /** Fresh N-016 consent, NEVER inherited from stock.consume or stock.receive. */
    fun explicitlyAllows(request: HouseholdStockConsumptionUndoDevRequest): Boolean
}

sealed interface HouseholdStockConsumptionUndoDevDecision {
    data class ReviewOnly(
        val request: HouseholdStockConsumptionUndoDevRequest,
        val contract: OperationContractDescriptor,
        val origin: HouseholdStockConsumptionUndoCommittedOrigin,
        val position: HouseholdStockConsumptionUndoCurrentPosition,
    ) : HouseholdStockConsumptionUndoDevDecision {
        override fun toString(): String = "HouseholdStockConsumptionUndoDevDecision.ReviewOnly(redacted)"
    }

    enum class Blocked : HouseholdStockConsumptionUndoDevDecision {
        CONTEXT, CONTRACT, CONSENT, ORIGIN, POSITION, STALE_REVIEW,
    }
}

class HouseholdStockConsumptionUndoDevAdmission(
    private val contracts: OperationContractLookupPort,
    private val currentContext: () -> HouseholdStockReceiveDevContext?,
    private val originals: TrustedConsumptionUndoCommittedOriginPort,
    private val positions: TrustedConsumptionUndoPositionPort,
    private val consent: HouseholdStockConsumptionUndoConsentPort,
) {
    private val operation = OperationId("undo.execute")
    private val originalOperation = HouseholdStockConsumptionReview.OPERATION

    fun review(request: HouseholdStockConsumptionUndoDevRequest): HouseholdStockConsumptionUndoDevDecision {
        if (!isCurrent(request)) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONTEXT
        val descriptor = contracts.current(operation)
        if (descriptor == null || descriptor.operationId != operation ||
            descriptor.capabilityId != CapabilityId("N-016") ||
            descriptor.canonicalExecutor != ExecutorId("undo-coordinator") ||
            descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            descriptor.exposure != ContractExposure.BINDABLE
        ) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONTRACT

        if (!consent.explicitlyAllows(request)) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONSENT
        val intent = request.intent
        if (intent.sourceOperationId != originalOperation ||
            intent.targetObjectId == request.undoIntentKey
        ) return HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN

        val verified = originals.verified(intent)
            ?: return HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN
        val original = verified.stockReview
        val stock = original.request
        if (verified.sourceReview != intent.sourceReview ||
            verified.sourceReceipt != intent.sourceReceipt ||
            verified.sourceReceipt.planFingerprint != verified.sourceReview.planFingerprint ||
            original.payload != intent.originalCreatePayload ||
            HouseholdStockConsumptionReview.decode(intent.originalCreatePayload) != original ||
            !stock.context.isolatedDev || !stock.context.foreground ||
            stock.context.principal != request.context.principal ||
            stock.intentKey != intent.targetObjectId || stock.intentKey == request.undoIntentKey ||
            stock.reservationRef != null
        ) return HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN

        val position = positions.current(intent.targetObjectId)
            ?: return HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION
        if (position.activePointer != request.context.activePointer ||
            position.principal != request.context.principal ||
            position.consumptionRef != stock.intentKey ||
            position.stockItem != stock.stockItem ||
            position.unit != stock.quantity.unit ||
            position.sourceRef != stock.sourceRef ||
            position.consumedQuantity != stock.quantity ||
            position.ledgerRevision != request.expectedLedgerRevision ||
            position.consumedQuantity.thousandths <= 0 ||
            // V1 review is FULL-ONLY: partial Undo needs its own canonical payload and B1-B5.
            position.remainingUndoableThousandths != position.consumedQuantity.thousandths ||
            position.consumedAtMs < 0 ||
            intent.cancelledAtEpochMillis < position.consumedAtMs ||
            !position.newUndoIntentAvailable
        ) return HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION

        if (!consent.explicitlyAllows(request)) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONSENT
        if (!isCurrent(request)) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONTEXT
        if (contracts.current(operation) != descriptor) return HouseholdStockConsumptionUndoDevDecision.Blocked.CONTRACT
        if (originals.verified(intent) != verified || positions.current(intent.targetObjectId) != position) {
            return HouseholdStockConsumptionUndoDevDecision.Blocked.STALE_REVIEW
        }
        return HouseholdStockConsumptionUndoDevDecision.ReviewOnly(request, descriptor, verified, position)
    }

    fun revalidate(
        previous: HouseholdStockConsumptionUndoDevDecision.ReviewOnly,
    ): HouseholdStockConsumptionUndoDevDecision =
        when (val latest = review(previous.request)) {
            is HouseholdStockConsumptionUndoDevDecision.Blocked -> latest
            previous -> latest
            else -> HouseholdStockConsumptionUndoDevDecision.Blocked.STALE_REVIEW
        }

    private fun isCurrent(request: HouseholdStockConsumptionUndoDevRequest): Boolean {
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
