package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HouseholdStockUndoDevAdmissionTest {
    private val originalPointer = HouseholdReviewGeneration("sql12-original", 3L)
    private val selectedPointer = HouseholdReviewGeneration("sql13-selected", 4L)
    private val originalContext = HouseholdStockReceiveDevContext(
        "human", 7L, originalPointer, isolatedDev = true, foreground = true,
    )
    private val currentContext = originalContext.copy(hostEpoch = 9L, activePointer = selectedPointer)
    private val originalReceive = HouseholdStockReceiveReview(
        HouseholdStockReceiveDevRequest(
            OperationId("stock.receive"), originalContext, "yerba", "p1", 1L,
            "physical-receipt-intent", HouseholdNeedQuantity(7_000L, HouseholdNeedUnit.UNIT),
        ),
        1L,
    )
    private val fingerprint = PlanFingerprint("source-plan")
    private val sourceContext = ConversationPlanReviewContext(
        ConversationId("source-chat"), "review-receive", "trusted-token", 5L, fingerprint,
    )
    private val sourceReceipt = ExecutionReceipt(
        ReceiptId("apply-receipt-distinct"), TransactionId("tx-received"), fingerprint,
    )
    private val original = HouseholdStockUndoCommittedOrigin(sourceContext, sourceReceipt, originalReceive)
    private val undoIntent = UndoExecuteIntent(
        sourceContext, sourceReceipt, OperationId("stock.receive"),
        "physical-receipt-intent", originalReceive.payload, 100L,
    )
    private val request = HouseholdStockUndoDevRequest(
        currentContext, undoIntent, "new-undo-intent", expectedLedgerRevision = 2L,
    )
    private val position = HouseholdStockUndoCurrentPosition(
        selectedPointer, "human", "physical-receipt-intent", "p1", "yerba",
        HouseholdNeedQuantity(7_000L, HouseholdNeedUnit.UNIT), 2L, 10_000L, 20L,
    )
    private val descriptor = OperationContractDescriptor(
        OperationId("undo.execute"), CapabilityId("N-016"), ExecutorId("undo-coordinator"),
        ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
        ContractFingerprint("stock-undo-fixture"), ContractExposure.BINDABLE,
    )
    private var contract: OperationContractDescriptor? = descriptor
    private var context: HouseholdStockReceiveDevContext? = currentContext
    private var source: HouseholdStockUndoCommittedOrigin? = original
    private var candidate: HouseholdStockUndoCurrentPosition? = position
    private var allows: (HouseholdStockUndoDevRequest) -> Boolean = { true }
    private val admission = HouseholdStockUndoDevAdmission(
        { contract }, { context }, { source }, { candidate }, { allows(it) },
    )

    @Test fun exactCommittedOriginAndDistinctApplyReceiptCanPrepareReviewOnly() {
        val decision = assertInstanceOf(
            HouseholdStockUndoDevDecision.ReviewOnly::class.java, admission.review(request),
        )
        assertEquals(original, decision.origin)
        assertEquals(position, decision.position)
        assertEquals(descriptor, decision.contract)
        assertNotEquals(sourceReceipt.receiptId.value, position.receiptId)
        assertNotEquals(originalPointer, selectedPointer) // valid journaled SQL12 -> SQL13 cutover
        assertEquals(decision, admission.revalidate(decision))
    }

    @Test fun requiresCanonicalUndoContractAndCoordinator() {
        listOf(
            null, descriptor.copy(operationId = OperationId("stock.receive")),
            descriptor.copy(capabilityId = CapabilityId("N-035")),
            descriptor.copy(canonicalExecutor = ExecutorId("household")),
            descriptor.copy(executionProfile = ExecutionProfile.READ_ONLY),
            descriptor.copy(exposure = ContractExposure.INTERNAL_ONLY),
        ).forEach {
            contract = it
            assertEquals(HouseholdStockUndoDevDecision.Blocked.CONTRACT, admission.review(request))
        }
    }

    @Test fun missingOrMismatchedOriginalCommittedReceiptFails() {
        source = null
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request))
        source = original.copy(sourceReceipt = sourceReceipt.copy(transactionId = TransactionId("tx-other")))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request))
        source = original.copy(sourceReview = sourceContext.copy(reviewId = "other"))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request))
    }

    @Test fun rejectsForgedOriginalPayloadAndUnrelatedSourceOperation() {
        val other = undoIntent.copy(sourceOperationId = OperationId("shopping_need.add"))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request.copy(intent = other)))
        val changed = undoIntent.copy(originalCreatePayload = CanonicalPayload.fromEncoded(
            "faro.household.stock.receive.v1", byteArrayOf(1, 2, 3),
        ))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request.copy(intent = changed)))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN,
            admission.review(request.copy(intent = undoIntent.copy(targetObjectId = "other-receipt"))))
    }

    @Test fun cannotConfusePhysicalReceiptWithApplyReceipt() {
        val forged = undoIntent.copy(targetObjectId = sourceReceipt.receiptId.value)
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN, admission.review(request.copy(intent = forged)))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.ORIGIN,
            admission.review(request.copy(undoIntentKey = "physical-receipt-intent")))
    }

    @Test fun rejectsWrongStockOwnershipQuantityAndTimestamps() {
        listOf(
            position.copy(principal = "other"),
            position.copy(purchaseReference = "p2"),
            position.copy(stockItem = "coffee"),
            position.copy(quantity = HouseholdNeedQuantity(8_000, HouseholdNeedUnit.UNIT)),
            position.copy(quantity = HouseholdNeedQuantity(7_000, HouseholdNeedUnit.GRAM)),
            position.copy(netReceivedThousandths = 1_000),
            position.copy(receivedAtMs = 101L),
        ).forEach {
            candidate = it
            assertEquals(HouseholdStockUndoDevDecision.Blocked.POSITION, admission.review(request))
        }
    }

    @Test fun rejectsRevisionStalenessAndCompensatedReceipt() {
        candidate = position.copy(ledgerRevision = 3L)
        assertEquals(HouseholdStockUndoDevDecision.Blocked.POSITION, admission.review(request))
        candidate = null
        assertEquals(HouseholdStockUndoDevDecision.Blocked.POSITION, admission.review(request))
    }

    @Test fun revokedConsentForegroundPointerAbaAndHostEpochBlock() {
        allows = { false }
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONSENT, admission.review(request))
        allows = { true }
        context = currentContext.copy(hostEpoch = 10L)
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        context = currentContext.copy(activePointer = HouseholdReviewGeneration("sql13-selected", 5L))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        context = currentContext.copy(foreground = false)
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONTEXT, admission.review(request))
        context = currentContext
        var checks = 0
        allows = { ++checks == 1 }
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONSENT, admission.review(request))
    }

    @Test fun secondOriginOrPositionReadChangeFailsClosed() {
        var reads = 0
        val flaky = HouseholdStockUndoDevAdmission(
            { descriptor }, { currentContext }, {
                if (++reads == 1) original else null
            }, { position }, { true },
        )
        assertEquals(HouseholdStockUndoDevDecision.Blocked.STALE_REVIEW, flaky.review(request))
        var positions = 0
        val stale = HouseholdStockUndoDevAdmission(
            { descriptor }, { currentContext }, { original }, {
                if (++positions == 1) position else position.copy(ledgerRevision = 3L)
            }, { true },
        )
        assertEquals(HouseholdStockUndoDevDecision.Blocked.STALE_REVIEW, stale.review(request))
    }

    @Test fun previewCarriesNoGrantAndRedactsSensitiveOrigin() {
        val reviewed = assertInstanceOf(
            HouseholdStockUndoDevDecision.ReviewOnly::class.java, admission.review(request),
        )
        assertFalse(request.toString().contains("trusted-token"))
        assertFalse(original.toString().contains("trusted-token"))
        assertFalse(reviewed.toString().contains("trusted-token"))
        assertNotNull(UndoExecutePayload.decode(UndoExecutePayload.payload(undoIntent)))
        assertEquals(HouseholdStockUndoDevDecision.Blocked.CONTEXT,
            admission.review(request.copy(undoIntentKey = " ")))
    }
}
