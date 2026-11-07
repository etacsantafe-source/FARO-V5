package ar.com.faro.v5.owner.reminders.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReminderCanonicalOperationTest {
    private fun rule() = ReminderRule(
        ReminderRuleId.of("rule-1"), "Vencimiento", "America/Argentina/Cordoba",
        LocalDate.of(2026, 11, 30), LocalTime.of(9, 0), ReminderRecurrence.MONTHLY,
        true, "matter-1", 1_000L
    )
    private fun wrap(bytes: ByteArray, format: String = ReminderCreatePayload.FORMAT_ID) =
        CanonicalPayload.fromEncoded(format, bytes)

    @Test fun roundTripIsImmutableAndTypedD229Ids() {
        val original = rule()
        val payload = ReminderCreatePayload.payload(original)
        assertEquals(original, ReminderCreatePayload.decode(payload))
        assertArrayEquals(ReminderCreatePayload.encodeBytes(original), payload.copyBytes())
        val mutated = payload.copyBytes()
        mutated[0] = 0
        assertEquals(original, ReminderCreatePayload.decode(payload))
        assertEquals(OperationId("reminder.create"), ReminderOperations.CREATE)
        assertEquals(CapabilityId("N-032"), ReminderOperations.CAPABILITY_ID)
        assertEquals(ExecutorId("planning"), ReminderOperations.EXECUTOR_ID)
    }

    @Test fun civilTimeAndZoneAreNeverExpandedToOccurrence() {
        val x = rule().copy(anchorLocalDate=LocalDate.of(2027, 2, 28),
            anchorLocalTime=LocalTime.of(2, 30), zoneId="America/New_York")
        val out = ReminderCreatePayload.decode(ReminderCreatePayload.payload(x))!!
        assertEquals(x, out)
        assertEquals(ReminderRecurrence.MONTHLY, out.recurrence)
    }

    @Test fun wrongFormatAndVersionAreRejected() {
        val original = ReminderCreatePayload.encodeBytes(rule())
        assertNull(ReminderCreatePayload.decode(wrap(original, "other.v1")))
        val changed = original.copyOf()
        changed[7] = 2
        assertNull(ReminderCreatePayload.decode(wrap(changed)))
    }

    @Test fun malformedUtf8CannotBecomeReplacementCharacter() {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use {
                it.writeInt(0x52435231)
                it.writeInt(1)
                it.writeInt(1)
                it.writeByte(0xff)
            }
        }.toByteArray()
        assertNull(ReminderCreatePayload.decode(wrap(bytes)))
        assertThrows(Exception::class.java) {
            ReminderCreatePayload.encodeBytes(rule().copy(title="\uD800"))
        }
    }

    @Test fun trailingTruncatedAndOversizedBytesAreRejected() {
        val good = ReminderCreatePayload.encodeBytes(rule())
        assertNull(ReminderCreatePayload.decode(wrap(good + byteArrayOf(0))))
        assertNull(ReminderCreatePayload.decode(wrap(good.copyOf(good.size-1))))
        assertNull(ReminderCreatePayload.decode(wrap(ByteArray(ReminderCreatePayload.MAX_PAYLOAD_BYTES+1))))
    }

    @Test fun noncanonicalBooleanAndInvalidRulesAreRejected() {
        val bytes = ByteArrayOutputStream().also { s ->
            DataOutputStream(s).use { d ->
                d.writeInt(0x52435231); d.writeInt(1)
                for (value in listOf("rule-1","Vencimiento","America/Argentina/Cordoba",
                        "2026-11-30","09:00","MONTHLY")) {
                    val b=value.toByteArray(Charsets.UTF_8)
                    d.writeInt(b.size); d.write(b)
                }
                d.writeByte(2)
                d.writeByte(0)
                d.writeLong(1L)
            }
        }.toByteArray()
        assertNull(ReminderCreatePayload.decode(wrap(bytes)))
        assertThrows(IllegalArgumentException::class.java) { rule().copy(updatedAtEpochMillis=-1) }
    }
}
