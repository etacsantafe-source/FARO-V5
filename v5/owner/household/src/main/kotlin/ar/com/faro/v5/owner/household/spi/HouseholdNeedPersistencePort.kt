package ar.com.faro.v5.owner.household.spi

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import java.time.LocalDate

data class HouseholdNeedFields(
    val name: HouseholdNeedName,
    val quantity: HouseholdNeedQuantity,
    val priority: HouseholdNeedPriority,
    val dueDate: LocalDate?,
)

data class HouseholdNeedsSnapshot(val revision: Long, val needs: List<HouseholdNeed>)

sealed interface HouseholdNeedChange {
    val id: HouseholdNeedId

    data class Add(override val id: HouseholdNeedId, val fields: HouseholdNeedFields) : HouseholdNeedChange

    sealed interface Existing : HouseholdNeedChange {
        val expectedNeedRevision: Long
    }

    data class Edit(
        override val id: HouseholdNeedId,
        override val expectedNeedRevision: Long,
        val fields: HouseholdNeedFields,
    ) : Existing

    data class Complete(
        override val id: HouseholdNeedId,
        override val expectedNeedRevision: Long,
    ) : Existing

    data class Remove(
        override val id: HouseholdNeedId,
        override val expectedNeedRevision: Long,
    ) : Existing

    /** Used only after receipt-linked Undo has its own approved transaction. */
    data class RestorePreimage(
        override val id: HouseholdNeedId,
        val expected: HouseholdNeed?,
        val previous: HouseholdNeed?,
    ) : HouseholdNeedChange {
        init {
            require(expected != null || previous != null)
            require(listOfNotNull(expected, previous).all { it.id == id })
        }
    }
}

class HouseholdNeedConflict(message: String) : IllegalStateException(message)

/** Owner SPI for a transaction admitted by Reality. Host authorization precedes entry to the SPI. */
interface HouseholdNeedPersistencePort {
    fun read(): HouseholdNeedsSnapshot

    fun mutate(
        expectedRevision: Long,
        change: HouseholdNeedChange,
        scope: RealityTransactionScope,
    ): HouseholdNeedsSnapshot
}
