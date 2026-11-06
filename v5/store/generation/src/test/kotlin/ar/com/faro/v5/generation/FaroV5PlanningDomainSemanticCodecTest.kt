package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5PlanningDomainSemanticCodecTest {
    private fun rule(id: String = "rule-a") = ReminderRule(
        ReminderRuleId.of(id), "Vencimiento", "America/Argentina/Cordoba",
        LocalDate.of(2026,11,30), LocalTime.of(9,0),
        ReminderRecurrence.MONTHLY, true, null, 100L,
    )
    private fun item() = MonthlyPlanItem(
        MonthlyPlanItemId.of("plan-a"), "Alquiler", YearMonth.of(2026,11),
        LocalDate.of(2026,12,5), MonthlyPlanItemStatus.PLANNED, 10000L,
        "ARS", null, 100L,
    )
    private fun full(): PlanningDomainSectionSnapshot {
        val rule=rule()
        val occ=ReminderOccurrence(
            ReminderOccurrenceId.of("occ-a"),rule.id,4000L,
            ReminderOccurrenceStatus.PENDING,null,100L,
        )
        return PlanningDomainSectionSnapshot(
            8,ReminderSnapshot(2L,listOf(rule),listOf(occ)),
            MonthlyPlanSnapshot(1L,listOf(item())),
        )
    }
    private fun empty()=PlanningDomainSectionSnapshot(
        8,ReminderSnapshot(0L,emptyList(),emptyList()),
        MonthlyPlanSnapshot(0L,emptyList()),
    )

    @Test fun nonemptyTwoOwnerRoundTripKeepsCivilAndPaymentSemantics() {
        val expected=full()
        val decoded=FaroV5PlanningDomainSemanticCodec.decode(
            FaroV5PlanningDomainSemanticCodec.encode(expected))
        assertEquals(expected,decoded)
        assertEquals(MonthlyPlanItemStatus.PLANNED,decoded.monthlyPlan.items.single().status)
        assertEquals("America/Argentina/Cordoba",decoded.reminders.rules.single().zoneId)
    }

    @Test fun emptySnapshotRoundTripsWithoutClaimingDurableOwner() {
        val expected=empty()
        assertEquals(expected,FaroV5PlanningDomainSemanticCodec.decode(
            FaroV5PlanningDomainSemanticCodec.encode(expected)))
    }

    @Test fun unknownMagicVersionOrSchemaFailsClosed() {
        val bytes=FaroV5PlanningDomainSemanticCodec.encode(full())
        for (offset in listOf(0,7,11)) {
            val changed=bytes.copyOf()
            changed[offset]=42
            assertThrows(Exception::class.java) {
                FaroV5PlanningDomainSemanticCodec.decode(changed)
            }
        }
        assertThrows(Exception::class.java) {
            FaroV5PlanningDomainSemanticCodec.encode(full().copy(schemaVersion=7))
        }
    }

    @Test fun truncatedOrTrailingBytesFailClosed() {
        val bytes=FaroV5PlanningDomainSemanticCodec.encode(full())
        assertThrows(Exception::class.java) {
            FaroV5PlanningDomainSemanticCodec.decode(bytes.copyOfRange(0,bytes.size-1))
        }
        assertThrows(Exception::class.java) {
            FaroV5PlanningDomainSemanticCodec.decode(bytes+byteArrayOf(1))
        }
    }

    @Test fun nonCanonicalUtf8IdOrderRejectedBeforeEncoding() {
        val wrong=empty().copy(
            reminders=ReminderSnapshot(2L,listOf(rule("rule-z"),rule("rule-a")),emptyList()),
        )
        assertThrows(Exception::class.java) {
            FaroV5PlanningDomainSemanticCodec.encode(wrong)
        }
    }

    @Test fun malformedUtf8AndOrphanOccurrenceAreNotAccepted() {
        val bytes=FaroV5PlanningDomainSemanticCodec.encode(full())
        // The title is an ASCII sequence; replace one byte inside it with
        // invalid unpaired UTF-8 while keeping all section lengths unchanged.
        val word="Vencimiento".toByteArray()
        val start=bytes.indices.firstOrNull { i ->
            i+word.size<=bytes.size && bytes.copyOfRange(i,i+word.size).contentEquals(word)
        } ?: error("test fixture title not present")
        val changed=bytes.copyOf()
        changed[start]=0xFF.toByte()
        assertThrows(Exception::class.java) {
            FaroV5PlanningDomainSemanticCodec.decode(changed)
        }
        val orphan=ReminderOccurrence(ReminderOccurrenceId.of("occ-z"),
            ReminderRuleId.of("missing"),2L,ReminderOccurrenceStatus.PENDING,null,2L)
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSnapshot(1L,listOf(rule()),listOf(orphan))
        }
        assertTrue(bytes.size < FaroV5PlanningDomainSemanticCodec.MAX_BYTES)
    }
}
