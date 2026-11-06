package ar.com.faro.v5.owner.monthlyplan.api

import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MonthlyPlanModelTest {
    private fun item()=MonthlyPlanItem(
        MonthlyPlanItemId.of("bill1"),"Alquiler",YearMonth.of(2026,11),
        LocalDate.of(2026,12,5),MonthlyPlanItemStatus.PLANNED,120000L,"ARS",null,100L)

    @Test fun periodDistinctFromCivilDueDateAndNoInferredPayment() {
        val x=item()
        assertEquals(YearMonth.of(2026,11),x.period)
        assertEquals(LocalDate.of(2026,12,5),x.dueDate)
        assertNull(x.paymentConfirmedAtEpochMillis)
    }
    @Test fun paidNeedsExplicitConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(status=MonthlyPlanItemStatus.PAID_CONFIRMED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(paymentConfirmedAtEpochMillis=10L)
        }
        assertEquals(MonthlyPlanItemStatus.PAID_CONFIRMED,
            item().copy(status=MonthlyPlanItemStatus.PAID_CONFIRMED,
                paymentConfirmedAtEpochMillis=10L).status)
    }
    @Test fun amountAndCurrencyMustTravelTogether() {
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(currencyCode=null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(expectedAmountMinor=null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(currencyCode="ars")
        }
    }
    @Test fun invalidIdsAndNegativeValuesFail() {
        assertThrows(IllegalArgumentException::class.java) { MonthlyPlanItemId.of(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(expectedAmountMinor=-1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(updatedAtEpochMillis=-1L)
        }
    }
    @Test fun duplicateIdsAndNegativeSnapshotRevisionFail() {
        assertEquals(1,MonthlyPlanSnapshot(1L,listOf(item())).items.size)
        assertThrows(IllegalArgumentException::class.java) {
            MonthlyPlanSnapshot(1L,listOf(item(),item()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonthlyPlanSnapshot(-1L,emptyList())
        }
    }
    @Test fun paymentTimestampAndExpectedAmountRemainIndependent() {
        val x=item().copy(status=MonthlyPlanItemStatus.PAID_CONFIRMED,
            paymentConfirmedAtEpochMillis=123456L)
        assertEquals(120000L,x.expectedAmountMinor)
        assertEquals(123456L,x.paymentConfirmedAtEpochMillis)
        assertThrows(IllegalArgumentException::class.java) {
            x.copy(paymentConfirmedAtEpochMillis=-1L)
        }
    }
}
