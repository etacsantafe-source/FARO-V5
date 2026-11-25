package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.owner.household.api.HouseholdStockReceiveDevDecision.Blocked
import ar.com.faro.v5.owner.household.api.HouseholdStockReceiveDevDecision.ReviewOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HouseholdStockReceiveDevAdmissionTest {
    private val operation = OperationId("stock.receive")
    private val initialContext = HouseholdStockReceiveDevContext(
        "principal", 2L, HouseholdReviewGeneration("active", 3L), isolatedDev = true, foreground = true,
    )
    private val request = HouseholdStockReceiveDevRequest(
        operation, initialContext, "item", "purchase", 1L, "attempt", quantity(7),
    )
    private val confirmed = ConfirmedStockPurchaseLine(
        "principal", initialContext.activePointer, "item", "purchase", 1L, quantity(10), 0L,
    )
    private val descriptor = OperationContractDescriptor(
        operation, CapabilityId("N-035"), ExecutorId("household"), ExecutionProfile.REALITY_MUTATION,
        ContractVersion("1"), ContractFingerprint("stock-fixture"), ContractExposure.BINDABLE,
    )
    private var contract: OperationContractDescriptor? = descriptor
    private var liveContext: HouseholdStockReceiveDevContext? = initialContext
    private var purchase: ConfirmedStockPurchaseLine? = confirmed
    private var allows: (HouseholdStockReceiveDevRequest) -> Boolean = { it == request }
    private val admission = HouseholdStockReceiveDevAdmission(
        { contract }, { liveContext }, { purchase }, { allows(it) },
    )

    @Test
    fun exactCatalogAndConfirmedSourceYieldReviewOnly() {
        val review = assertInstanceOf(ReviewOnly::class.java, admission.review(request))
        assertEquals(request, review.request)
        assertEquals(descriptor, review.contract)
        assertEquals(3_000L, review.preview.pendingThousandths)
    }

    @Test
    fun missingOrDifferentSourceCannotConfirmAPurchase() {
        purchase = null
        assertEquals(Blocked.PURCHASE, admission.review(request))
        purchase = confirmed.copy(purchaseReference = "budget")
        assertEquals(Blocked.PURCHASE, admission.review(request))
        purchase = confirmed.copy(revision = 2)
        assertEquals(Blocked.PURCHASE, admission.review(request))
    }

    @Test
    fun rejectsIncompatibleCatalogMetadata() {
        listOf(
            null,
            descriptor.copy(operationId = OperationId("stock.consume")),
            descriptor.copy(canonicalExecutor = ExecutorId("finance")),
            descriptor.copy(executionProfile = ExecutionProfile.READ_ONLY),
            descriptor.copy(executionProfile = ExecutionProfile.EXTERNAL_EFFECT),
            descriptor.copy(exposure = ContractExposure.INTERNAL_ONLY),
            descriptor.copy(capabilityId = CapabilityId("N-034")),
        ).forEach {
            contract = it
            assertEquals(Blocked.CONTRACT, admission.review(request))
        }
    }

    @Test
    fun rejectsOtherOperationOrConsentForAnotherItemOrPrincipal() {
        assertEquals(Blocked.CONTRACT, admission.review(request.copy(operation = OperationId("shopping_need.add"))))
        assertEquals(Blocked.CONSENT, admission.review(request.copy(stockItem = "another-item")))
        val otherPrincipal = initialContext.copy(principal = "another-principal")
        liveContext = otherPrincipal
        assertEquals(Blocked.CONSENT, admission.review(request.copy(context = otherPrincipal)))
    }

    @Test
    fun rejectsPointerAbaAndChangedContextOnRevalidation() {
        val review = assertInstanceOf(ReviewOnly::class.java, admission.review(request))
        listOf(
            initialContext.copy(activePointer = HouseholdReviewGeneration("active", 4L)),
            initialContext.copy(hostEpoch = 3),
            initialContext.copy(principal = "someone-else"),
        ).forEach {
            liveContext = it
            assertEquals(Blocked.CONTEXT, admission.revalidate(review))
        }
    }

    @Test
    fun rejectsMissingConsentBackgroundAndRevocationDuringReview() {
        allows = { false }
        assertEquals(Blocked.CONSENT, admission.review(request))
        liveContext = initialContext.copy(foreground = false)
        assertEquals(Blocked.CONTEXT, admission.review(request.copy(context = requireNotNull(liveContext))))
        liveContext = initialContext.copy(isolatedDev = false)
        assertEquals(Blocked.CONTEXT, admission.review(request.copy(context = requireNotNull(liveContext))))
        liveContext = initialContext
        var checks = 0
        allows = { ++checks == 1 }
        assertEquals(Blocked.CONSENT, admission.review(request))
    }

    @Test
    fun calculatesPartialReceiptAndRejectsOverReceiptAndIncompatibleUnits() {
        allows = { true }
        val first = assertInstanceOf(ReviewOnly::class.java, admission.review(request))
        assertEquals(7_000L, first.preview.afterReceiptThousandths)
        assertEquals(3_000L, first.preview.pendingThousandths)
        purchase = confirmed.copy(receivedThousandths = 7_000L)
        val second = assertInstanceOf(ReviewOnly::class.java, admission.review(request.copy(quantity = quantity(3))))
        assertEquals(0L, second.preview.pendingThousandths)
        assertEquals(Blocked.QUANTITY, admission.review(request.copy(quantity = quantity(4))))
        assertEquals(Blocked.QUANTITY, admission.review(request.copy(quantity = quantity(3, HouseholdNeedUnit.GRAM))))
        assertThrows(IllegalArgumentException::class.java) { quantity(-1) }
    }

    @Test
    fun replayRemainsReviewOnlyAndCallerCannotReplaceTrustedFacts() {
        val first = assertInstanceOf(ReviewOnly::class.java, admission.review(request))
        assertEquals(first, admission.review(request))
        assertEquals(first, admission.revalidate(first))
        assertEquals(Blocked.STALE_REVIEW, admission.revalidate(first.copy(purchase = confirmed.copy(purchased = quantity(12)))))
        assertEquals(Blocked.CONSENT, admission.revalidate(first.copy(request = request.copy(quantity = quantity(3)))))
    }

    private fun quantity(units: Long, unit: HouseholdNeedUnit = HouseholdNeedUnit.UNIT) =
        HouseholdNeedQuantity(units * 1_000, unit)
}
