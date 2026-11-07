package ar.com.faro.v5.owner.monthlyplan.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MonthlyPlanCanonicalOperationTest {
    private fun item() = MonthlyPlanItem(
        MonthlyPlanItemId.of("bill-1"),"Alquiler",YearMonth.of(2026,11),
        LocalDate.of(2026,12,5),MonthlyPlanItemStatus.PLANNED,120000L,"ARS",null,100L
    )
    private fun wrap(bytes: ByteArray, format: String = MonthlyPlanItemAddPayload.FORMAT_ID) =
        CanonicalPayload.fromEncoded(format, bytes)

    @Test fun roundTripIsImmutableAndTypedD229Ids() {
        val original = item()
        val payload = MonthlyPlanItemAddPayload.payload(original)
        assertEquals(original, MonthlyPlanItemAddPayload.decode(payload))
        assertArrayEquals(MonthlyPlanItemAddPayload.encodeBytes(original), payload.copyBytes())
        val mutated = payload.copyBytes()
        mutated[0] = 0
        assertEquals(original, MonthlyPlanItemAddPayload.decode(payload))
        assertEquals(OperationId("monthly_plan.item.add"), MonthlyPlanOperations.ITEM_ADD)
        assertEquals(CapabilityId("N-028"), MonthlyPlanOperations.CAPABILITY_ID)
        assertEquals(ExecutorId("planning"), MonthlyPlanOperations.EXECUTOR_ID)
    }

    @Test fun monthCivilDueDateAndPaymentRemainIndependent() {
        val original = item()
        assertEquals(original, MonthlyPlanItemAddPayload.decode(MonthlyPlanItemAddPayload.payload(original)))
        val paid = original.copy(status=MonthlyPlanItemStatus.PAID_CONFIRMED,
            paymentConfirmedAtEpochMillis=250L)
        assertEquals(paid, MonthlyPlanItemAddPayload.decode(MonthlyPlanItemAddPayload.payload(paid)))
        assertNull(MonthlyPlanItemAddPayload.decode(MonthlyPlanItemAddPayload.payload(original))!!.paymentConfirmedAtEpochMillis)
    }

    @Test fun wrongFormatAndVersionAreRejected() {
        val original = MonthlyPlanItemAddPayload.encodeBytes(item())
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(original,"other.v1")))
        val changed = original.copyOf()
        changed[7] = 2
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(changed)))
    }

    @Test fun malformedUtf8IsRejected() {
        val bytes = ByteArrayOutputStream().also { s ->
            DataOutputStream(s).use {
                it.writeInt(0x4d435231); it.writeInt(1)
                it.writeInt(1); it.writeByte(0xff)
            }
        }.toByteArray()
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(bytes)))
        assertThrows(Exception::class.java) {
            MonthlyPlanItemAddPayload.encodeBytes(item().copy(title="\uD800"))
        }
    }

    @Test fun trailingTruncatedAndOversizedBytesAreRejected() {
        val good = MonthlyPlanItemAddPayload.encodeBytes(item())
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(good + byteArrayOf(0))))
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(good.copyOf(good.size-1))))
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(ByteArray(MonthlyPlanItemAddPayload.MAX_PAYLOAD_BYTES+1))))
    }

    @Test fun invalidBooleanAndImpossibleAmountsAreRejected() {
        val bytes = ByteArrayOutputStream().also { s ->
            DataOutputStream(s).use { d ->
                d.writeInt(0x4d435231);d.writeInt(1)
                for (v in listOf("bill-1","Alquiler","2026-11","2026-12-05","PLANNED")) {
                    val b=v.toByteArray(Charsets.UTF_8)
                    d.writeInt(b.size);d.write(b)
                }
                d.writeByte(2)
                d.writeByte(0)
                d.writeLong(100L)
            }
        }.toByteArray()
        assertNull(MonthlyPlanItemAddPayload.decode(wrap(bytes)))
        assertThrows(IllegalArgumentException::class.java) { item().copy(expectedAmountMinor=-1L) }
    }
}
