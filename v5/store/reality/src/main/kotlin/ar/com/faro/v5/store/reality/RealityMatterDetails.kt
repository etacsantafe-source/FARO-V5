package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ar.com.faro.v5.owner.matters.api.MatterDetails
import ar.com.faro.v5.owner.matters.api.MatterHistoryEntry
import ar.com.faro.v5.owner.matters.api.MatterHistoryKind
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterPriority
import ar.com.faro.v5.owner.matters.api.MatterStatus
import java.time.LocalDate

/**
 * W03-A additive Matter owner extension. Shares RealityStore's single SQLite
 * connection and active transaction; it does not grant mutation permission.
 */
internal class RealityMatterDetails(private val connection: SQLiteConnection) {
    fun read(): MutableMap<MatterId, MatterDetails> {
        if (!installed()) return mutableMapOf()
        val histories = mutableMapOf<MatterId, MutableList<MatterHistoryEntry>>()
        connection.prepare("""
            SELECT matter_id, sequence, recorded_at_ms, kind, resulting_status, reason
            FROM matter_history ORDER BY matter_id, sequence
        """.trimIndent()).use { st ->
            while (st.step()) {
                val id = MatterId.of(st.getText(0))
                val event = MatterHistoryEntry(
                    sequence = Math.toIntExact(st.getLong(1)),
                    recordedAtEpochMillis = st.getLong(2),
                    kind = MatterHistoryKind.valueOf(st.getText(3)),
                    resultingStatus = MatterStatus.valueOf(st.getText(4)),
                    reason = st.getText(5),
                )
                histories.getOrPut(id, ::mutableListOf).add(event)
            }
        }
        return buildMap {
            connection.prepare("""
                SELECT matter_id, review_epoch_day, priority
                FROM matter_details ORDER BY matter_id
            """.trimIndent()).use { st ->
                while (st.step()) {
                    val id = MatterId.of(st.getText(0))
                    put(id, MatterDetails(
                        priority = MatterPriority.valueOf(st.getText(2)),
                        reviewDate = if (st.isNull(1)) null else LocalDate.ofEpochDay(st.getLong(1)),
                        history = histories.remove(id).orEmpty(),
                    ))
                }
            }
            check(histories.isEmpty()) { "Orphan Matter history" }
        }.toMutableMap()
    }

    /** The canonical matter_record must exist, under the same Reality transaction. */
    fun write(id: MatterId, previous: MatterDetails, updated: MatterDetails) {
        if (updated == previous) return
        require(updated.retainsHistoryOf(previous)) { "Matter history is append-only" }
        if (updated == MatterDetails()) {
            connection.prepare("DELETE FROM matter_details WHERE matter_id = ?").use { st ->
                st.bindText(1, id.value)
                st.step()
            }
            return
        }
        install()
        connection.prepare("""
            INSERT INTO matter_details(matter_id, review_epoch_day, priority)
            VALUES (?, ?, ?)
            ON CONFLICT(matter_id) DO UPDATE SET
                review_epoch_day = excluded.review_epoch_day,
                priority = excluded.priority
        """.trimIndent()).use { st ->
            st.bindText(1, id.value)
            updated.reviewDate?.let { st.bindLong(2, it.toEpochDay()) } ?: st.bindNull(2)
            st.bindText(3, updated.priority.name)
            st.step()
        }
        connection.prepare("""
            INSERT INTO matter_history(matter_id, sequence, recorded_at_ms, kind, resulting_status, reason)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { st ->
            for (i in previous.history.size until updated.history.size) {
                val e = updated.history[i]
                st.bindText(1, id.value)
                st.bindLong(2, e.sequence.toLong())
                st.bindLong(3, e.recordedAtEpochMillis)
                st.bindText(4, e.kind.name)
                st.bindText(5, e.resultingStatus.name)
                st.bindText(6, e.reason)
                st.step()
                st.reset()
            }
        }
    }

    /** 0 tables = legacy; 2 tables = extension; a half-installed schema is corrupt. */
    private fun installed(): Boolean = connection.prepare("""
        SELECT count(*) FROM sqlite_schema WHERE type = 'table'
        AND name IN ('matter_details','matter_history')
    """.trimIndent()).use { st ->
        check(st.step())
        val count = st.getLong(0)
        check(count == 0L || count == 2L) { "Incomplete Matter extension schema" }
        count == 2L
    }

    private fun install() {
        if (installed()) return
        connection.execSQL("""
            CREATE TABLE matter_details (
                matter_id TEXT NOT NULL PRIMARY KEY REFERENCES matter_record(matter_id),
                review_epoch_day INTEGER,
                priority TEXT NOT NULL CHECK(priority IN ('LOW', 'NORMAL', 'HIGH'))
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE matter_history (
                matter_id TEXT NOT NULL REFERENCES matter_details(matter_id),
                sequence INTEGER NOT NULL CHECK(sequence BETWEEN 1 AND 100000),
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms >= 0),
                kind TEXT NOT NULL CHECK(kind IN ('CREATED','EDITED','STATUS_CHANGED')),
                resulting_status TEXT NOT NULL CHECK(
                    resulting_status IN ('OPEN','IN_PROGRESS','WAITING','RESOLVED','ARCHIVED')
                ),
                reason TEXT NOT NULL CHECK(length(reason) <= 512),
                PRIMARY KEY(matter_id, sequence)
            )
        """.trimIndent())
    }
}
