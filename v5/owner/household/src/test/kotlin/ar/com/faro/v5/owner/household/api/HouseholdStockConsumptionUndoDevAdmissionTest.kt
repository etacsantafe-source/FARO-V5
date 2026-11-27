package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HouseholdStockConsumptionUndoDevAdmissionTest {
    private val sourcePointer = HouseholdReviewGeneration("sql15-original", 3L)
    private val selectedPointer = HouseholdReviewGeneration("sql15-selected", 4L)
    private val sourceContext = HouseholdStockReceiveDevContext(
        "human", 7L, sourcePointer, isolatedDev = true, foreground = true,
    )
    private val currentContext = sourceContext.copy(hostEpoch = 9L, activePointer = selectedPointer)
    private val original = HouseholdStockConsumptionReview(
        HouseholdStockConsumptionDevRequest(
            sourceContext, "yerba", HouseholdNeedQuantity(4_000, HouseholdNeedUnit.UNIT),
            "purchase-1", "consume-intent-1", 5L,
        ),
    )
    private val sourceFingerprint = PlanFingerprint("committed-consume-plan")
    private val sourceReview = ConversationPlanReviewContext(
        ConversationId("original-chat"), "review-consume", "trusted-token", 5L, sourceFingerprint,
    )
    private val receipt = ExecutionReceipt(
        ReceiptId("apply-receipt-not-consumption-ref"), TransactionId("tx-consume"), sourceFingerprint,
    )
    private val verifiedOrigin = HouseholdStockConsumptionUndoCommittedOrigin(sourceReview, receipt, original)
    private val undo = UndoExecuteIntent(
        sourceReview, receipt, OperationId("stock.consume"), "consume-intent-1", original.payload, 100L,
    )
    private val request = HouseholdStockConsumptionUndoDevRequest(
        currentContext, undo, "new-consume-undo-intent", 6L,
    )
    private val position = HouseholdStockConsumptionUndoCurrentPosition(
        selectedPointer, "human", "consume-intent-1", "yerba",
        HouseholdNeedUnit.UNIT, "purchase-1", HouseholdNeedQuantity(4_000, HouseholdNeedUnit.UNIT),
        6L, 4_000L, 20L, true,
    )
    private val descriptor = OperationContractDescriptor(
        OperationId("undo.execute"), CapabilityId("N-016"), ExecutorId("undo-coordinator"),
        ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
        ContractFingerprint("consumption-undo-fixture"), ContractExposure.BINDABLE,
    )
    private var catalog: OperationContractDescriptor? = descriptor
    private var live: HouseholdStockReceiveDevContext? = currentContext
    private var origin: HouseholdStockConsumptionUndoCommittedOrigin? = verifiedOrigin
    private var current: HouseholdStockConsumptionUndoCurrentPosition? = position
    private var allows: (HouseholdStockConsumptionUndoDevRequest) -> Boolean = { true }
    private val admission = HouseholdStockConsumptionUndoDevAdmission(
        { catalog }, { live }, { origin }, { current }, { allows(it) },
    )

    @Test fun exactCommittedConsumptionAdmitsReviewOnlyWithoutApply() {
        val reviewed = assertInstanceOf(
            HouseholdStockConsumptionUndoDevDecision.ReviewOnly::class.java, admission.review(request),
        )
        assertEquals(verifiedOrigin, reviewed.origin)
        assertEquals(position, reviewed.position)
        assertEquals(descriptor, reviewed.contract)
        assertEquals(reviewed, admission.revalidate(reviewed))
        assertNotEquals(sourcePointer, selectedPointer)
        assertNotEquals(receipt.receiptId.value, position.consumptionRef)
    }

    @Test fun requiresFreshN016UndoCoordinatorContract() {
        val invalid = listOf(
            null,
            descriptor.copy(operationId = OperationId("stock.consume")),
            descriptor.copy(capabilityId = CapabilityId("N-035")),
            descriptor.copy(canonicalExecutor = ExecutorId("household")),
            descriptor.copy(executionProfile = ExecutionProfile.READ_ONLY),
            descriptor.copy(exposure = ContractExposure.INTERNAL_ONLY),
        )
        invalid.forEach {
            catalog = it
            assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONTRACT, admission.review(request))
        }
    }

    @Test fun missingOrForgedCommittedOriginalCannotCreateUndo() {
        origin = null
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN, admission.review(request))
        origin = verifiedOrigin.copy(sourceReceipt = receipt.copy(transactionId = TransactionId("other")))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN, admission.review(request))
        origin = verifiedOrigin.copy(sourceReview = sourceReview.copy(reviewId = "other"))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN, admission.review(request))
        origin = verifiedOrigin.copy(stockReview = HouseholdStockConsumptionReview(
            original.request.copy(sourceRef = "unrelated-source"),
        ))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN, admission.review(request))
    }

    @Test fun forbidsUnrelatedOperationPayloadOrTargetConfusion() {
        for (operation in listOf("stock.receive", "stock.reserve", "undo.execute")) {
            assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN,
                admission.review(request.copy(intent = undo.copy(sourceOperationId = OperationId(operation)))))
        }
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN,
            admission.review(request.copy(intent = undo.copy(targetObjectId = receipt.receiptId.value))))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN,
            admission.review(request.copy(intent = undo.copy(
                originalCreatePayload = CanonicalPayload.fromEncoded("bad.format", byteArrayOf(1, 2)),
            ))))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.ORIGIN,
            admission.review(request.copy(undoIntentKey = original.request.intentKey)))
    }

    @Test fun requiresExactPrincipalSourceItemUnitRevisionAndConsumption() {
        val invalid = listOf(
            position.copy(principal = "other"),
            position.copy(stockItem = "coffee"),
            position.copy(unit = HouseholdNeedUnit.GRAM),
            position.copy(sourceRef = "purchase-2"),
            position.copy(consumptionRef = "different-consume"),
            position.copy(consumedQuantity = HouseholdNeedQuantity(3_000, HouseholdNeedUnit.UNIT)),
            position.copy(activePointer = sourcePointer),
            position.copy(ledgerRevision = 7L),
            position.copy(consumedAtMs = 101L),
        )
        invalid.forEach {
            current = it
            assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION, admission.review(request))
        }
    }

    @Test fun onlyFullUntouchedConsumptionCanBeReversedInVersionOne() {
        for (remaining in listOf(-1L, 0L, 1_000L, 4_001L)) {
            current = position.copy(remainingUndoableThousandths = remaining)
            assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION, admission.review(request))
        }
        current = position.copy(newUndoIntentAvailable = false)
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION, admission.review(request))
        current = null
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.POSITION, admission.review(request))
    }

    @Test fun noConsentOrForegroundOrPointerAbaEverGrantsReview() {
        allows = { false }
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONSENT, admission.review(request))
        allows = { true }
        live = currentContext.copy(foreground = false)
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        live = currentContext.copy(hostEpoch = 10L)
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        live = currentContext.copy(activePointer = selectedPointer.copy(sequence = 5L))
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        live = currentContext
        var checks = 0
        allows = { ++checks == 1 }
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONSENT, admission.review(request))
    }

    @Test fun secondOriginPositionOrContractReadChangeFailsClosed() {
        var reads = 0
        val staleOrigin = HouseholdStockConsumptionUndoDevAdmission(
            { descriptor }, { currentContext }, {
                if (++reads == 1) verifiedOrigin else null
            }, { position }, { true },
        )
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.STALE_REVIEW,
            staleOrigin.review(request))
        reads = 0
        val stalePosition = HouseholdStockConsumptionUndoDevAdmission(
            { descriptor }, { currentContext }, { verifiedOrigin }, {
                if (++reads == 1) position else position.copy(ledgerRevision = 7L)
            }, { true },
        )
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.STALE_REVIEW,
            stalePosition.review(request))
        reads = 0
        val staleContract = HouseholdStockConsumptionUndoDevAdmission(
            { if (++reads == 1) descriptor else null }, { currentContext },
            { verifiedOrigin }, { position }, { true },
        )
        assertEquals(HouseholdStockConsumptionUndoDevDecision.Blocked.CONTRACT,
            staleContract.review(request))
    }

    @Test fun invalidUndoKeyAndRedactionKeepPreviewNonExecutable() {
        val reviewed = assertInstanceOf(
            HouseholdStockConsumptionUndoDevDecision.ReviewOnly::class.java, admission.review(request),
        )
        assertFalse(request.toString().contains("trusted-token"))
        assertFalse(verifiedOrigin.toString().contains("trusted-token"))
        assertFalse(reviewed.toString().contains("trusted-token"))
        assertNotNull(UndoExecutePayload.decode(UndoExecutePayload.payload(undo)))
        for (key in listOf("", "bad intent key", "consume-intent-1")) {
            assertNotEquals(HouseholdStockConsumptionUndoDevDecision.ReviewOnly::class.java,
                admission.review(request.copy(undoIntentKey = key))::class.java)
        }
    }
}
