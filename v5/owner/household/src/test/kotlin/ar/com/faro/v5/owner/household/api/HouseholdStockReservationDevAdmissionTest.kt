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
import ar.com.faro.v5.owner.household.api.HouseholdStockReservationDevDecision.Blocked
import ar.com.faro.v5.owner.household.api.HouseholdStockReservationDevDecision.ReviewOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HouseholdStockReservationDevAdmissionTest {
    private val active = HouseholdStockReceiveDevContext(
        "human", 2, HouseholdReviewGeneration("sql14", 3), isolatedDev = true, foreground = true,
    )
    private val reserve = HouseholdStockReservationDevRequest(
        HouseholdStockReservationAction.RESERVE, active, "yerba", quantity(2_000), "reserve-intent", 4,
    )
    private val release = reserve.copy(
        action = HouseholdStockReservationAction.RELEASE, intentKey = "release-intent",
        quantity = quantity(1_000), reservationRef = "reserve-event",
    )
    private val unreserved = HouseholdStockReservationPosition(
        "human", active.activePointer, "yerba", HouseholdNeedUnit.UNIT, 4, 3_000, 0,
    )
    private val reserved = unreserved.copy(reservedThousandths = 2_000,
        release = HouseholdStockReservationReleasePosition("reserve-event", 2_000))
    private var liveContext: HouseholdStockReceiveDevContext? = active
    private var position: HouseholdStockReservationPosition? = unreserved
    private var grant: (HouseholdStockReservationDevRequest) -> Boolean = { it == reserve || it == release }
    private var catalog: (OperationId) -> OperationContractDescriptor? = ::descriptor

    private val owner = HouseholdStockReservationDevAdmission(
        { catalog(it) }, { liveContext }, { position }, { grant(it) },
    )

    @Test
    fun reserveUsesPhysicalMinusExistingReservationsForReviewOnly() {
        val admitted = assertInstanceOf(ReviewOnly::class.java, owner.review(reserve))
        assertEquals(reserve, admitted.review.request)
        assertEquals(unreserved, admitted.position)
        assertEquals(OperationId("stock.reserve"), admitted.contract.operationId)
        assertEquals(admitted, owner.revalidate(admitted))
        grant = { true }
        assertEquals(Blocked.QUANTITY, owner.review(reserve.copy(quantity = quantity(4_000))))
        position = reserved
        assertEquals(Blocked.QUANTITY, owner.review(reserve))
        assertInstanceOf(ReviewOnly::class.java, owner.review(reserve.copy(quantity = quantity(1_000))))
    }

    @Test
    fun releaseIsTiedToTheOriginalEventItemUnitAndPrincipal() {
        position = reserved
        val admitted = assertInstanceOf(ReviewOnly::class.java, owner.review(release))
        assertEquals(OperationId("stock.release"), admitted.contract.operationId)
        grant = { true }
        assertEquals(Blocked.POSITION, owner.review(release.copy(reservationRef = "wrong")))
        assertEquals(Blocked.POSITION, owner.review(release.copy(stockItem = "wrong")))
        assertEquals(Blocked.POSITION, owner.review(release.copy(quantity = HouseholdNeedQuantity(1_000, HouseholdNeedUnit.GRAM))))
        assertEquals(Blocked.QUANTITY, owner.review(release.copy(quantity = quantity(2_001))))
        position = reserved.copy(principal = "another-human")
        assertEquals(Blocked.POSITION, owner.review(release))
    }

    @Test
    fun contractsAndConsentBelongToEachSeparateOperation() {
        position = reserved
        grant = { it == reserve }
        assertEquals(Blocked.CONSENT, owner.review(release))
        grant = { true }
        val exact = descriptor(release.action.operation)
        for (different in listOf(
            null, exact.copy(operationId = OperationId("stock.receive")),
            exact.copy(capabilityId = CapabilityId("N-016")),
            exact.copy(canonicalExecutor = ExecutorId("finance")),
            exact.copy(executionProfile = ExecutionProfile.READ_ONLY),
            exact.copy(exposure = ContractExposure.INTERNAL_ONLY),
        )) {
            catalog = { different }
            assertEquals(Blocked.CONTRACT, owner.review(release))
        }
    }

    @Test
    fun deniesAbaEpochRevisionAndAuthorityChangesInLiveReview() {
        val previous = assertInstanceOf(ReviewOnly::class.java, owner.review(reserve))
        liveContext = active.copy(activePointer = active.activePointer.copy(sequence = 5))
        assertEquals(Blocked.CONTEXT, owner.revalidate(previous))
        liveContext = active.copy(hostEpoch = 3)
        assertEquals(Blocked.CONTEXT, owner.revalidate(previous))
        liveContext = active
        position = unreserved.copy(revision = 5)
        assertEquals(Blocked.POSITION, owner.revalidate(previous))
        position = unreserved.copy(reservedThousandths = 1_000)
        assertEquals(Blocked.STALE_REVIEW, owner.revalidate(previous))
        var checks = 0
        grant = { ++checks == 1 }
        assertEquals(Blocked.CONSENT, owner.review(reserve))
    }

    @Test
    fun canonicalPayloadBindsOperationIntentOwnerAndQuantity() {
        val a = HouseholdStockReservationReview(reserve)
        val b = HouseholdStockReservationReview(release)
        assertEquals(a, HouseholdStockReservationReview.decode(a.payload))
        assertEquals(b, HouseholdStockReservationReview.decode(b.payload))
        assertNotEquals(a.payload, b.payload)
        assertNotEquals(a.payload, HouseholdStockReservationReview(reserve.copy(intentKey = "another")).payload)
        val trailing = CanonicalPayload.fromEncoded(a.payload.formatId, a.payload.copyBytes() + 0.toByte())
        val unknown = CanonicalPayload.fromEncoded(a.payload.formatId, a.payload.copyBytes().also { it[0] = 99 })
        assertNull(HouseholdStockReservationReview.decode(trailing))
        assertNull(HouseholdStockReservationReview.decode(unknown))
        assertThrows(IllegalArgumentException::class.java) { quantity(Int.MAX_VALUE.toLong() + 1) }
    }

    @Test
    fun wideOwnerBalancesAndInvalidRequestTokensFailClosed() {
        grant = { true }
        position = unreserved.copy(physicalThousandths = Long.MAX_VALUE, reservedThousandths = Long.MAX_VALUE - 1)
        assertEquals(Blocked.QUANTITY, owner.review(reserve))
        assertInstanceOf(ReviewOnly::class.java, owner.review(reserve.copy(quantity = quantity(1))))
        assertEquals(Blocked.CONTEXT, owner.review(reserve.copy(intentKey = "bad intent")))
        assertEquals(Blocked.CONTEXT, owner.review(reserve.copy(reservationRef = "bogus")))
        assertEquals(Blocked.CONTEXT, owner.review(release.copy(reservationRef = null)))
        assertEquals(Blocked.CONTEXT, owner.review(reserve.copy(context = active.copy(foreground = false))))
        position = unreserved.copy(physicalThousandths = 3, reservedThousandths = 4)
        assertEquals(Blocked.POSITION, owner.review(reserve))
    }

    private fun quantity(thousandths: Long) = HouseholdNeedQuantity(thousandths, HouseholdNeedUnit.UNIT)

    private fun descriptor(id: OperationId) = OperationContractDescriptor(
        id, CapabilityId("N-035"), ExecutorId("household"), ExecutionProfile.REALITY_MUTATION,
        ContractVersion("1"), ContractFingerprint("stock-reservation-fixture"), ContractExposure.BINDABLE,
    )
}
