package ar.com.faro.v5.owner.household.api

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HouseholdNeedModelTest {
    private val openNeed = HouseholdNeed(
        id = HouseholdNeedId("need-01"),
        name = HouseholdNeedName("Yerba orgánica"),
        quantity = HouseholdNeedQuantity(1_500, HouseholdNeedUnit.KILOGRAM),
        priority = HouseholdNeedPriority.NORMAL,
        status = HouseholdNeedStatus.OPEN,
        revision = 0,
    )

    @Test
    fun representsAnOfflineNeedWithAnExplicitUnitAndPrecision() {
        assertEquals("Yerba orgánica", openNeed.name.value)
        assertEquals(1_500L, openNeed.quantity.thousandths)
        assertEquals(HouseholdNeedUnit.KILOGRAM, openNeed.quantity.unit)
        assertNotEquals(openNeed.quantity, openNeed.quantity.copy(unit = HouseholdNeedUnit.GRAM))
    }

    @Test
    fun rejectsBlankOverlongAndNonTokenIds() {
        for (id in listOf("", " ", "a b", "a\u0000b", "a".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { HouseholdNeedId(id) }
        }
        assertEquals(128, HouseholdNeedId("a".repeat(128)).value.length)
    }

    @Test
    fun rejectsBlankOrUntrimmedNames() {
        for (name in listOf("", "   ", " yerba", "yerba ")) {
            assertThrows(IllegalArgumentException::class.java) { HouseholdNeedName(name) }
        }
    }

    @Test
    fun rejectsControlCharactersAndEnforcesNameLength() {
        for (name in listOf("yer\nba", "yerba\u007F", "a".repeat(161))) {
            assertThrows(IllegalArgumentException::class.java) { HouseholdNeedName(name) }
        }
        assertEquals(160, HouseholdNeedName("a".repeat(160)).value.length)
    }

    @Test
    fun rejectsNonPositiveQuantities() {
        for (quantity in listOf(Long.MIN_VALUE, -1L, 0L)) {
            assertThrows(IllegalArgumentException::class.java) {
                HouseholdNeedQuantity(quantity, HouseholdNeedUnit.UNIT)
            }
        }
    }

    @Test
    fun rejectsScaleOverflowAtConstructionAndCopy() {
        assertEquals(
            Int.MAX_VALUE.toLong(),
            HouseholdNeedQuantity(Int.MAX_VALUE.toLong(), HouseholdNeedUnit.GRAM).thousandths,
        )
        for (quantity in listOf(Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                openNeed.quantity.copy(thousandths = quantity)
            }
        }
    }

    @Test
    fun acceptsMinimumNonzeroQuantity() {
        assertEquals(1L, HouseholdNeedQuantity(1, HouseholdNeedUnit.LITER).thousandths)
    }

    @Test
    fun rejectsNegativeRevision() {
        assertEquals(0L, openNeed.revision)
        assertThrows(IllegalArgumentException::class.java) { openNeed.copy(revision = -1) }
    }

    @Test
    fun rejectsCivilDatesOutsideTheSupportedRange() {
        assertEquals(null, openNeed.dueDate)
        val leapDay = LocalDate.of(2028, 2, 29)
        assertEquals(leapDay, openNeed.copy(dueDate = leapDay).dueDate)
        for (year in listOf(0, 10_000)) {
            assertThrows(IllegalArgumentException::class.java) {
                openNeed.copy(dueDate = LocalDate.of(year, 1, 1))
            }
        }
    }

    @Test
    fun applyingChangesToACopyDoesNotMutateItsSource() {
        val completed = openNeed.copy(
            quantity = openNeed.quantity.copy(thousandths = 2_000),
            priority = HouseholdNeedPriority.HIGH,
            status = HouseholdNeedStatus.COMPLETED,
            revision = 1,
        )

        assertEquals(openNeed.id, completed.id)
        assertEquals(HouseholdNeedStatus.OPEN, openNeed.status)
        assertEquals(1_500L, openNeed.quantity.thousandths)
        assertEquals(0L, openNeed.revision)
        assertEquals(HouseholdNeedStatus.COMPLETED, completed.status)
        assertEquals(2_000L, completed.quantity.thousandths)
        assertEquals(1L, completed.revision)
    }
}
