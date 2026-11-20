package ar.com.faro.v5.owner.household.api

import java.time.LocalDate

@JvmInline
value class HouseholdNeedId(val value: String) {
    init {
        require(value.length in 1..128 && value.none { it.isWhitespace() || it.isISOControl() }) {
            "household need id must be an opaque token of 1..128 characters"
        }
    }
}

@JvmInline
value class HouseholdNeedName(val value: String) {
    init {
        require(value.length in 1..160 && value.isNotBlank() && value == value.trim() &&
            value.none(Char::isISOControl)) {
            "household need name must be trimmed, with 1..160 characters and no control characters"
        }
    }
}

enum class HouseholdNeedUnit { UNIT, PACKAGE, GRAM, KILOGRAM, MILLILITER, LITER }

/** Quantity in thousandths of the selected unit. */
data class HouseholdNeedQuantity(
    val thousandths: Long,
    val unit: HouseholdNeedUnit,
) {
    init {
        require(thousandths in 1L..Int.MAX_VALUE.toLong()) {
            "household need quantity must be positive and fit in signed 32-bit thousandths"
        }
    }
}

enum class HouseholdNeedPriority { LOW, NORMAL, HIGH }

enum class HouseholdNeedStatus { OPEN, COMPLETED }

data class HouseholdNeed(
    val id: HouseholdNeedId,
    val name: HouseholdNeedName,
    val quantity: HouseholdNeedQuantity,
    val priority: HouseholdNeedPriority,
    val status: HouseholdNeedStatus,
    val revision: Long,
    val dueDate: LocalDate? = null,
) {
    init {
        require(revision >= 0L) { "household need revision must be non-negative" }
        require(dueDate == null || dueDate.year in 1..9999) {
            "household need due date must use a four-digit civil year between 1 and 9999"
        }
    }
}
