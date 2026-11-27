package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.owner.household.api.HouseholdStockConsumptionDevDecision.Blocked
import ar.com.faro.v5.owner.household.api.HouseholdStockConsumptionDevDecision.ReviewOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HouseholdStockConsumptionDevAdmissionTest {
    private val active = HouseholdStockReceiveDevContext(
        "human", 2, HouseholdReviewGeneration("sql15", 3), isolatedDev = true, foreground = true,
    )
    private val request = HouseholdStockConsumptionDevRequest(
        active, "yerba", quantity(1_000), "reception-1", "consume-intent-1", 4,
    )
    private val source = HouseholdStockConsumptionSourcePosition(
        "reception-1", "human", "yerba", HouseholdNeedUnit.UNIT, 2_000,
    )
    private val stock = HouseholdStockConsumptionPosition(
        "human", active.activePointer, "yerba", HouseholdNeedUnit.UNIT, 4,
        physicalThousandths = 3_000, reservedThousandths = 1_000, source = source,
    )
    private var liveContext: HouseholdStockReceiveDevContext? = active
    private var position: HouseholdStockConsumptionPosition? = stock
    private var grant: (HouseholdStockConsumptionDevRequest) -> Boolean = { it == request }
    private var catalog: (OperationId) -> OperationContractDescriptor? = ::descriptor

    private val owner = HouseholdStockConsumptionDevAdmission(
        { catalog(it) }, { liveContext }, { position }, { grant(it) },
    )

    @Test
    fun explicitConsumptionOnlyProducesReviewNoApply() {
        val approved = assertInstanceOf(ReviewOnly::class.java, owner.review(request))
        assertEquals(request, approved.review.request)
        assertEquals(stock, approved.position)
        assertEquals(OperationId("stock.consume"), approved.contract.operationId)
        assertEquals(approved, owner.revalidate(approved))
    }

    @Test
    fun noConsumptionOfReservedOrUnavailableStock() {
        grant = { true }
        assertEquals(Blocked.QUANTITY, owner.review(request.copy(quantity = quantity(2_001))))
        position = stock.copy(reservedThousandths = 2_500)
        assertEquals(Blocked.QUANTITY, owner.review(request))
        position = stock.copy(physicalThousandths = 2, reservedThousandths = 3)
        assertEquals(Blocked.POSITION, owner.review(request))
    }

    @Test
    fun sourceMustMatchActualOwnerPrincipalItemUnitAndRemaining() {
        grant = { true }
        assertEquals(Blocked.POSITION, owner.review(request.copy(sourceRef = "invented-origin")))
        position = stock.copy(source = source.copy(principal = "different"))
        assertEquals(Blocked.POSITION, owner.review(request))
        position = stock.copy(source = source.copy(stockItem = "different"))
        assertEquals(Blocked.POSITION, owner.review(request))
        position = stock.copy(source = source.copy(unit = HouseholdNeedUnit.GRAM))
        assertEquals(Blocked.POSITION, owner.review(request))
        position = stock.copy(source = source.copy(remainingThousandths = 999))
        assertEquals(Blocked.QUANTITY, owner.review(request))
        position = stock.copy(source = source.copy(remainingThousandths = -1))
        assertEquals(Blocked.POSITION, owner.review(request))
    }

    @Test
    fun contractIsExactN035HouseholdRealityMutationBindable() {
        val exact = descriptor(HouseholdStockConsumptionReview.OPERATION)
        for (different in listOf(
            null, exact.copy(operationId = OperationId("stock.receive")),
            exact.copy(capabilityId = CapabilityId("N-016")),
            exact.copy(canonicalExecutor = ExecutorId("finance")),
            exact.copy(executionProfile = ExecutionProfile.READ_ONLY),
            exact.copy(exposure = ContractExposure.INTERNAL_ONLY),
        )) {
            catalog = { different }
            assertEquals(Blocked.CONTRACT, owner.review(request))
        }
    }

    @Test
    fun contextAndConsentAreCheckedBeforeAndAfterPosition() {
        grant = { false }
        assertEquals(Blocked.CONSENT, owner.review(request))
        grant = { true }
        liveContext = active.copy(hostEpoch = 3)
        assertEquals(Blocked.CONTEXT, owner.review(request))
        liveContext = active
        assertEquals(Blocked.CONTEXT, owner.review(request.copy(context = active.copy(foreground = false))))
        var calls = 0
        grant = { ++calls == 1 }
        assertEquals(Blocked.CONSENT, owner.review(request))
    }

    @Test
    fun abaOrPositionChangeDeniesRevalidation() {
        val prior = assertInstanceOf(ReviewOnly::class.java, owner.review(request))
        liveContext = active.copy(activePointer = active.activePointer.copy(sequence = 4))
        assertEquals(Blocked.CONTEXT, owner.revalidate(prior))
        liveContext = active
        position = stock.copy(revision = 5)
        assertEquals(Blocked.POSITION, owner.revalidate(prior))
        position = stock.copy(reservedThousandths = 1_500)
        assertEquals(Blocked.STALE_REVIEW, owner.revalidate(prior))
    }

    @Test
    fun canonicalPayloadRoundTripsWithoutMutationOrExtraFields() {
        val review = HouseholdStockConsumptionReview(request)
        assertEquals(review, HouseholdStockConsumptionReview.decode(review.payload))
        assertNotEquals(review.payload, HouseholdStockConsumptionReview(request.copy(intentKey = "other-intent")).payload)
        assertNotEquals(review.payload, HouseholdStockConsumptionReview(request.copy(sourceRef = "another-source")).payload)
        assertNull(HouseholdStockConsumptionReview.decode(CanonicalPayload.fromEncoded(
            review.payload.formatId, review.payload.copyBytes() + 0.toByte())))
        val badCode = review.payload.copyBytes().also { it[0] = 99 }
        assertNull(HouseholdStockConsumptionReview.decode(CanonicalPayload.fromEncoded(
            review.payload.formatId, badCode)))
    }

    @Test
    fun invalidReservationOrNoncanonicalInputsAreDenied() {
        assertEquals(Blocked.CONTEXT, owner.review(request.copy(reservationRef = "reserved-event")))
        assertEquals(Blocked.CONTEXT, owner.review(request.copy(intentKey = "invalid intent")))
        assertEquals(Blocked.CONTEXT, owner.review(request.copy(sourceRef = "")))
        assertEquals(Blocked.CONTEXT, owner.review(request.copy(ledgerRevision = -1)))
        assertThrows(IllegalArgumentException::class.java) { quantity(Int.MAX_VALUE.toLong() + 1) }
        assertThrows(IllegalArgumentException::class.java) {
            HouseholdStockConsumptionReview(request.copy(reservationRef = "reserved"))
        }
    }

    private fun quantity(value: Long) = HouseholdNeedQuantity(value, HouseholdNeedUnit.UNIT)

    private fun descriptor(id: OperationId) = OperationContractDescriptor(
        id, CapabilityId("N-035"), ExecutorId("household"), ExecutionProfile.REALITY_MUTATION,
        ContractVersion("1"), ContractFingerprint("stock-consumption-fixture"), ContractExposure.BINDABLE,
    )
}
