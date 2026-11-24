package ar.com.faro.v5.owner.household.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HouseholdStockReceiptPreviewTest {
    private fun units(amount: Long, unit: HouseholdNeedUnit = HouseholdNeedUnit.UNIT) =
        HouseholdNeedQuantity(amount, unit)

    private fun input(purchased: Long = 10_000, received: Long = 0, candidate: Long = 7_000) =
        HouseholdStockReceiptInput("purchase-opaque-1", units(purchased), received, units(candidate))

    @Test fun partialReceptionOnlyShowsSevenOfTen() {
        val r = HouseholdStockReceiptPreview.calculate(input()) as HouseholdStockReceiptDecision.Ready
        assertEquals(7_000, r.afterReceiptThousandths)
        assertEquals(3_000, r.pendingThousandths)
        assertEquals(0, r.previouslyReceivedThousandths)
    }

    @Test fun secondReceptionCompletesRemainingThree() {
        val r = HouseholdStockReceiptPreview.calculate(input(received = 7_000, candidate = 3_000))
            as HouseholdStockReceiptDecision.Ready
        assertEquals(10_000, r.afterReceiptThousandths)
        assertEquals(0, r.pendingThousandths)
    }

    @Test fun overReceptionIsRejected() {
        assertEquals(HouseholdStockReceiptDecision.Rejected.EXCEEDS_PURCHASE,
            HouseholdStockReceiptPreview.calculate(input(received = 7_000, candidate = 4_000)))
    }

    @Test fun packageAndUnitCannotMix() {
        assertEquals(HouseholdStockReceiptDecision.Rejected.INCOMPATIBLE_UNIT,
            HouseholdStockReceiptPreview.calculate(input().copy(candidate = units(7_000, HouseholdNeedUnit.PACKAGE))))
    }

    @Test fun gramsAndKilogramsCannotConvertImplicitly() {
        assertEquals(HouseholdStockReceiptDecision.Rejected.INCOMPATIBLE_UNIT,
            HouseholdStockReceiptPreview.calculate(input().copy(
                purchased = units(1_000, HouseholdNeedUnit.KILOGRAM),
                candidate = units(500, HouseholdNeedUnit.GRAM))))
    }

    @Test fun missingPurchaseSnapshotFailsClosed() {
        assertEquals(HouseholdStockReceiptDecision.Rejected.UNAVAILABLE,
            HouseholdStockReceiptPreview.calculate(null))
    }

    @Test fun invalidPurchaseReferenceFailsClosed() {
        for (bad in listOf("", "two words", " bad", "bad\nline", "x".repeat(129))) {
            assertEquals(HouseholdStockReceiptDecision.Rejected.INVALID_PURCHASE_REFERENCE,
                HouseholdStockReceiptPreview.calculate(input().copy(purchaseReference = bad)))
        }
    }

    @Test fun invalidPreviouslyReceivedLedgerFailsClosed() {
        for (invalid in listOf(-1L, 10_001L, Long.MAX_VALUE)) {
            assertEquals(HouseholdStockReceiptDecision.Rejected.INVALID_RECEIVED_LEDGER,
                HouseholdStockReceiptPreview.calculate(input(received = invalid)))
        }
    }

    @Test fun zeroNegativeAndOverflowingQuantitiesCannotBeBuilt() {
        for (invalid in listOf(0L, -1L, Int.MAX_VALUE.toLong() + 1)) {
            assertThrows(IllegalArgumentException::class.java) { units(invalid) }
        }
    }

    @Test fun previewIsPureAndHandlesIntegerMaximumWithoutOverflow() {
        val original = input(purchased = Int.MAX_VALUE.toLong(),
            received = Int.MAX_VALUE.toLong() - 1, candidate = 1)
        val a = HouseholdStockReceiptPreview.calculate(original)
        val b = HouseholdStockReceiptPreview.calculate(original)
        assertEquals(a, b)
        assertEquals(original.previouslyReceivedThousandths, Int.MAX_VALUE.toLong() - 1)
        val good = a as HouseholdStockReceiptDecision.Ready
        assertEquals(Int.MAX_VALUE.toLong(), good.afterReceiptThousandths)
        assertEquals(0, good.pendingThousandths)
        assertEquals(HouseholdStockReceiptDecision.Rejected.EXCEEDS_PURCHASE,
            HouseholdStockReceiptPreview.calculate(original.copy(candidate = units(2))))
    }
}
