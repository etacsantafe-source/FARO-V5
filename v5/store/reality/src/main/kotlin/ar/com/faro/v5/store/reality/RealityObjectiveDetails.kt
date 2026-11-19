package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ar.com.faro.v5.owner.objectives.api.ObjectiveDetails
import ar.com.faro.v5.owner.objectives.api.ObjectiveHistoryEntry
import ar.com.faro.v5.owner.objectives.api.ObjectiveHistoryEntryCodec
import ar.com.faro.v5.owner.objectives.api.ObjectiveId
import ar.com.faro.v5.owner.objectives.api.ObjectiveMeasurement
import ar.com.faro.v5.owner.objectives.api.ObjectiveMetric
import ar.com.faro.v5.owner.objectives.api.ObjectiveMetricUnit
import ar.com.faro.v5.owner.objectives.api.ObjectivePriority
import ar.com.faro.v5.owner.objectives.api.ObjectivePlan
import ar.com.faro.v5.owner.objectives.api.ObjectivePlanCodec
import java.time.LocalDate

/** Extension of the existing objective owner, on the caller's Reality connection. */
internal class RealityObjectiveDetails(private val connection: SQLiteConnection) {
    fun read(): Map<ObjectiveId, ObjectiveDetails> {
        val history = readHistory()
        val plans = readPlans()
        if (!installed()) {
            check(history.isEmpty() && plans.isEmpty()) { "Orphan Objective extension" }
            return emptyMap()
        }
        val measurements = mutableMapOf<ObjectiveId, MutableList<ObjectiveMeasurement>>()
        connection.prepare("""
            SELECT objective_id, sequence, recorded_at_ms, unit_kind, unit, value
            FROM objective_measurement ORDER BY objective_id, sequence
        """.trimIndent()).use { st ->
            while (st.step()) {
                val id = ObjectiveId.of(st.getText(0))
                measurements.getOrPut(id, ::mutableListOf).add(
                    ObjectiveMeasurement(Math.toIntExact(st.getLong(1)), st.getLong(2), readUnit(st.getText(3), st.getText(4)), st.getLong(5)),
                )
            }
        }
        return buildMap {
            connection.prepare("""
                SELECT objective_id, due_epoch_day, priority, metric_target, metric_kind, metric_unit
                FROM objective_details
            """.trimIndent()).use { st ->
                while (st.step()) {
                    val id = ObjectiveId.of(st.getText(0))
                    put(id, ObjectiveDetails(
                        target = if (st.isNull(3)) null else ObjectiveMetric(
                            st.getLong(3), readUnit(st.getText(4), st.getText(5)),
                        ),
                        dueDate = if (st.isNull(1)) null else LocalDate.ofEpochDay(st.getLong(1)),
                        priority = ObjectivePriority.valueOf(st.getText(2)),
                        measurements = measurements.remove(id).orEmpty(),
                        history = history.remove(id).orEmpty(),
                        plan = plans.remove(id) ?: ObjectivePlan(),
                    ))
                }
            }
            check(measurements.isEmpty() && history.isEmpty() && plans.isEmpty()) {
                "Orphan objective details"
            }
        }
    }

    /** The canonical objective row must already exist in the same transaction. */
    fun write(id: ObjectiveId, previous: ObjectiveDetails, updated: ObjectiveDetails) {
        if (updated == previous) return
        require(updated.retainsHistoryOf(previous)) { "Objective history is append-only" }

        if (updated == ObjectiveDetails()) {
            writePlan(id, previous.plan, updated.plan)
            connection.prepare("DELETE FROM objective_details WHERE objective_id = ?").use { st ->
                st.bindText(1, id.value)
                st.step()
            }
            return
        }

        install()
        connection.prepare("""
            INSERT INTO objective_details(objective_id, due_epoch_day, priority, metric_target, metric_kind, metric_unit)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(objective_id) DO UPDATE SET
                due_epoch_day = excluded.due_epoch_day,
                priority = excluded.priority,
                metric_target = excluded.metric_target,
                metric_kind = excluded.metric_kind,
                metric_unit = excluded.metric_unit
        """.trimIndent()).use { st ->
            st.bindText(1, id.value)
            updated.dueDate?.let { st.bindLong(2, it.toEpochDay()) } ?: st.bindNull(2)
            st.bindText(3, updated.priority.name)
            val target = updated.target
            if (target == null) {
                st.bindNull(4)
                st.bindNull(5)
                st.bindNull(6)
            } else {
                st.bindLong(4, target.value)
                st.bindText(5, target.unit.kind.name)
                st.bindText(6, target.unit.label)
            }
            st.step()
        }
        connection.prepare("""
            INSERT INTO objective_measurement(objective_id, sequence, recorded_at_ms, unit_kind, unit, value)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { st ->
            for (index in previous.measurements.size until updated.measurements.size) {
                val entry = updated.measurements[index]
                st.bindText(1, id.value)
                st.bindLong(2, entry.sequence.toLong())
                st.bindLong(3, entry.recordedAtEpochMillis)
                st.bindText(4, entry.unit.kind.name)
                st.bindText(5, entry.unit.label)
                st.bindLong(6, entry.value)
                st.step()
                st.reset()
            }
        }
        appendHistory(id, previous.history.size, updated.history)
        writePlan(id, previous.plan, updated.plan)
    }

    private fun readPlans(): MutableMap<ObjectiveId, ObjectivePlan> {
        val plans = mutableMapOf<ObjectiveId, ObjectivePlan>()
        if (!hasPlanTable()) return plans
        connection.prepare("SELECT objective_id, plan_blob FROM objective_plan").use { st ->
            while (st.step()) {
                val id = ObjectiveId.of(st.getText(0))
                val plan = ObjectivePlanCodec.decode(st.getBlob(1))
                check(plan != ObjectivePlan()) { "Stored objective plan must have content" }
                plans[id] = plan
            }
        }
        return plans
    }

    private fun writePlan(id: ObjectiveId, previous: ObjectivePlan, plan: ObjectivePlan) {
        if (previous == plan) return
        if (plan == ObjectivePlan()) {
            connection.prepare("DELETE FROM objective_plan WHERE objective_id = ?").use { st ->
                st.bindText(1, id.value)
                st.step()
            }
            return
        }
        if (!hasPlanTable()) {
            connection.execSQL("""
                CREATE TABLE objective_plan (
                    objective_id TEXT NOT NULL PRIMARY KEY REFERENCES objective_details(objective_id),
                    plan_blob BLOB NOT NULL CHECK(length(plan_blob) BETWEEN 1 AND 40000)
                )
            """.trimIndent())
        }
        connection.prepare("""
            INSERT INTO objective_plan(objective_id, plan_blob) VALUES (?, ?)
            ON CONFLICT(objective_id) DO UPDATE SET plan_blob = excluded.plan_blob
        """.trimIndent()).use { st ->
            st.bindText(1, id.value)
            st.bindBlob(2, ObjectivePlanCodec.encode(plan))
            st.step()
        }
    }

    private fun hasPlanTable(): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = 'objective_plan'"
    ).use { it.step() }

    private fun readHistory(): MutableMap<ObjectiveId, MutableList<ObjectiveHistoryEntry>> {
        val result = mutableMapOf<ObjectiveId, MutableList<ObjectiveHistoryEntry>>()
        if (!hasHistoryTable()) return result
        connection.prepare("""
            SELECT objective_id, sequence, event_blob FROM objective_history
            ORDER BY objective_id, sequence
        """.trimIndent()).use { st ->
            while (st.step()) {
                val event = ObjectiveHistoryEntryCodec.decode(st.getBlob(2))
                check(event.sequence.toLong() == st.getLong(1)) { "Objective event sequence mismatch" }
                result.getOrPut(ObjectiveId.of(st.getText(0)), ::mutableListOf).add(event)
            }
        }
        return result
    }

    private fun appendHistory(id: ObjectiveId, previousSize: Int, history: List<ObjectiveHistoryEntry>) {
        if (previousSize == history.size) return
        if (!hasHistoryTable()) {
            connection.execSQL("""
                CREATE TABLE objective_history (
                    objective_id TEXT NOT NULL REFERENCES objective_details(objective_id),
                    sequence INTEGER NOT NULL CHECK(sequence BETWEEN 1 AND 100000),
                    event_blob BLOB NOT NULL CHECK(length(event_blob) BETWEEN 1 AND 8192),
                    PRIMARY KEY(objective_id, sequence)
                )
            """.trimIndent())
        }
        connection.prepare("""
            INSERT INTO objective_history(objective_id, sequence, event_blob) VALUES (?, ?, ?)
        """.trimIndent()).use { st ->
            for (index in previousSize until history.size) {
                val event = history[index]
                st.bindText(1, id.value)
                st.bindLong(2, event.sequence.toLong())
                st.bindBlob(3, ObjectiveHistoryEntryCodec.encode(event))
                st.step()
                st.reset()
            }
        }
    }

    private fun hasHistoryTable(): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = 'objective_history'"
    ).use { it.step() }

    private fun installed(): Boolean = connection.prepare("""
        SELECT count(*) FROM sqlite_schema WHERE type = 'table'
        AND name IN ('objective_details', 'objective_measurement')
    """.trimIndent()).use { st ->
        check(st.step())
        val count = st.getLong(0)
        check(count == 0L || count == 2L) { "Incomplete Objective extension schema" }
        count == 2L
    }

    private fun install() {
        if (installed()) return
        connection.execSQL("""
            CREATE TABLE objective_details (
                objective_id TEXT NOT NULL PRIMARY KEY REFERENCES objective_record(objective_id),
                due_epoch_day INTEGER,
                priority TEXT NOT NULL CHECK(priority IN ('LOW', 'NORMAL', 'HIGH')),
                metric_target INTEGER,
                metric_kind TEXT,
                metric_unit TEXT,
                CHECK((metric_target IS NULL AND metric_kind IS NULL AND metric_unit IS NULL) OR
                      (metric_target IS NOT NULL AND metric_target > 0 AND metric_kind IS NOT NULL
                       AND metric_kind IN ('QUANTITY','MONETARY') AND metric_unit IS NOT NULL))
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE objective_measurement (
                objective_id TEXT NOT NULL REFERENCES objective_details(objective_id),
                sequence INTEGER NOT NULL CHECK(sequence BETWEEN 1 AND 100000),
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= 0),
                unit_kind TEXT NOT NULL CHECK(unit_kind IN ('QUANTITY','MONETARY')),
                unit TEXT NOT NULL,
                value INTEGER NOT NULL CHECK(value >= 0),
                PRIMARY KEY(objective_id, sequence)
            )
        """.trimIndent())
    }

    private fun readUnit(kind: String, label: String) = ObjectiveMetricUnit(
        ObjectiveMetricUnit.Kind.valueOf(kind), label,
    )
}
