package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedConflict
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.time.LocalDate

/** Runs inside the active scope on Reality's existing connection. */
internal class RealityHouseholdNeedPersistence(private val connection: SQLiteConnection) {
    fun mutate(
        rows: RealityHousehold10Rows,
        expectedRevision: Long,
        change: HouseholdNeedChange,
    ): HouseholdNeedsSnapshot {
        require(expectedRevision >= 0)
        if (rows.revision != expectedRevision) throw HouseholdNeedConflict("Household list changed")
        val old = rows.needs.firstOrNull { it.id == change.id.value }
        when (change) {
            is HouseholdNeedChange.Add -> if (old != null) throw HouseholdNeedConflict("Household need exists")
            is HouseholdNeedChange.Existing -> {
                require(change.expectedNeedRevision >= 0)
                if (old?.revision != change.expectedNeedRevision) throw HouseholdNeedConflict("Household need changed")
            }
        }
        val nextRevision = Math.addExact(rows.revision, 1)
        val new = when (change) {
            is HouseholdNeedChange.Add -> row(change.id, change.fields, HouseholdNeedStatus.OPEN.name, nextRevision)
            is HouseholdNeedChange.Edit -> row(change.id, change.fields, checkNotNull(old).status, nextRevision)
            is HouseholdNeedChange.Complete -> {
                val existing = checkNotNull(old)
                if (existing.status != HouseholdNeedStatus.OPEN.name) throw HouseholdNeedConflict("Household need is complete")
                existing.copy(status = HouseholdNeedStatus.COMPLETED.name, revision = nextRevision)
            }
            is HouseholdNeedChange.Remove -> null
        }
        val needs = rows.needs.filterNot { it.id == change.id.value }
        val updated = RealityHousehold10Rows(nextRevision, (needs + listOfNotNull(new)).sortedBy { it.id })
        Household10RowsSql.validate(updated)
        val result = typedSnapshot(updated)

        connection.execSQL("SAVEPOINT household_need_mutation")
        try {
            connection.prepare("UPDATE household_need_state SET revision=? WHERE singleton_id=1 AND revision=?").use {
                it.bindLong(1, nextRevision)
                it.bindLong(2, expectedRevision)
                it.step()
            }
            requireSingleChange()
            when {
                old == null -> connection.prepare("INSERT INTO household_need VALUES (?,?,?,?,?,?,?,?)").use {
                    it.bindText(1, checkNotNull(new).id)
                    it.bindFields(2, new)
                    it.step()
                }
                new == null -> connection.prepare("DELETE FROM household_need WHERE need_id=? AND revision=?").use {
                    it.bindText(1, old.id)
                    it.bindLong(2, old.revision)
                    it.step()
                }
                else -> connection.prepare("""
                    UPDATE household_need
                    SET name=?,quantity_thousandths=?,unit=?,priority=?,status=?,revision=?,due_epoch_day=?
                    WHERE need_id=? AND revision=?
                """.trimIndent()).use {
                    it.bindFields(1, new)
                    it.bindText(8, old.id)
                    it.bindLong(9, old.revision)
                    it.step()
                }
            }
            requireSingleChange()
            connection.execSQL("RELEASE SAVEPOINT household_need_mutation")
            return result
        } catch (failure: Throwable) {
            try {
                connection.execSQL("ROLLBACK TO SAVEPOINT household_need_mutation")
                connection.execSQL("RELEASE SAVEPOINT household_need_mutation")
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    private fun requireSingleChange() {
        val changed = connection.prepare("SELECT changes()").use { it.step() && it.getLong(0) == 1L }
        if (!changed) throw HouseholdNeedConflict("Household revision compare-and-set failed")
    }

    private fun row(id: HouseholdNeedId, fields: HouseholdNeedFields, status: String, revision: Long) =
        RealityHousehold10Row(
            id.value, fields.name.value, fields.quantity.thousandths.toInt(), fields.quantity.unit.name,
            fields.priority.name, status, revision, fields.dueDate?.toEpochDay(),
        )

    private fun SQLiteStatement.bindFields(start: Int, row: RealityHousehold10Row) {
        bindText(start, row.name)
        bindLong(start + 1, row.quantityThousandths.toLong())
        bindText(start + 2, row.unit)
        bindText(start + 3, row.priority)
        bindText(start + 4, row.status)
        bindLong(start + 5, row.revision)
        row.dueEpochDay?.let { bindLong(start + 6, it) } ?: bindNull(start + 6)
    }

    companion object {
        fun typedSnapshot(rows: RealityHousehold10Rows) = HouseholdNeedsSnapshot(
            rows.revision,
            rows.needs.map { row ->
                HouseholdNeed(
                    HouseholdNeedId(row.id), HouseholdNeedName(row.name),
                    HouseholdNeedQuantity(row.quantityThousandths.toLong(), HouseholdNeedUnit.valueOf(row.unit)),
                    HouseholdNeedPriority.valueOf(row.priority), HouseholdNeedStatus.valueOf(row.status),
                    row.revision, row.dueEpochDay?.let(LocalDate::ofEpochDay),
                )
            },
        )
    }
}
