package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.LocalDate

data class RealityHousehold10Row(
    val id: String,
    val name: String,
    val quantityThousandths: Int,
    val unit: String,
    val priority: String,
    val status: String,
    val revision: Long,
    val dueEpochDay: Long?,
)

data class RealityHousehold10Rows(val revision: Long, val needs: List<RealityHousehold10Row>)

/** The generation owner must admit the sealed non-active file and pin its lease before calling. */
object RealityHousehold10SealedRowsPort {
    fun readTrustedSealedStageTen(file: Path): RealityHousehold10Rows {
        val physical = Household10RowsSql.checkFile(file)
        return BundledSQLiteDriver().open(
            physical.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            Household10RowsSql.checkSchema(db)
            Household10RowsSql.checkIntegrity(db)
            Household10RowsSql.readRows(db)
        }
    }
}

internal object Household10RowsSql {
    const val NEED_COLUMNS = "need_id,name,quantity_thousandths,unit,priority,status,revision,due_epoch_day"
    private const val MAX_ROWS = 8_192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val dueDays = LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")
    private val priorities = setOf("LOW", "NORMAL", "HIGH")
    private val statuses = setOf("OPEN", "COMPLETED")

    fun checkFile(file: Path): Path {
        val physical = file.toAbsolutePath().normalize()
        check(physical.parent != null && physical.parent.toRealPath() == physical.parent &&
            Files.isRegularFile(physical, NOFOLLOW_LINKS)) {
            "Household10 requires a canonical regular SQLite file"
        }
        // The dev generation operates on a filesystem that can prove single-link ownership.
        check((Files.getAttribute(physical, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
            "Household10 refuses linked SQLite files"
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            check(Files.notExists(physical.resolveSibling(physical.fileName.toString() + suffix), NOFOLLOW_LINKS)) {
                "Household10 refuses SQLite sidecars"
            }
        }
        return physical
    }

    fun checkSchema(db: SQLiteConnection) {
        check(db.scalar("PRAGMA user_version") == 10L && db.scalar("PRAGMA foreign_keys") == 1L) {
            "Household10 requires exact schema10 and foreign key enforcement"
        }
        for ((table, expected) in listOf(
            "household_need_state" to listOf("singleton_id", "revision"),
            "household_need" to NEED_COLUMNS.split(','),
        )) {
            db.prepare("PRAGMA table_list($table)").use {
                check(it.step() && it.getText(2) == "table" &&
                    it.getLong(3) == expected.size.toLong() && it.getLong(5) == 1L && !it.step()) {
                    "Household10 requires the STRICT table $table"
                }
            }
            val actual = db.prepare("PRAGMA table_info($table)").use {
                buildList { while (it.step()) add(it.getText(1)) }
            }
            check(actual == expected) { "Household10 column mismatch in $table" }
        }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema WHERE type = 'trigger' AND " +
                "tbl_name COLLATE NOCASE IN ('household_need','household_need_state')",
        ) == 0L) { "Household10 refuses triggers on the restored tables" }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type = 'table' AND fk.\"table\" COLLATE NOCASE IN ('household_need','household_need_state')",
        ) == 0L) { "Household10 refuses inbound foreign keys on the restored tables" }
    }

    fun checkIntegrity(db: SQLiteConnection) {
        check(db.prepare("PRAGMA integrity_check").use { it.step() && it.getText(0) == "ok" && !it.step() }) {
            "Household10 integrity check failed"
        }
        check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
            "Household10 foreign key check failed"
        }
    }

    fun readRows(db: SQLiteConnection): RealityHousehold10Rows {
        val revision = db.prepare("SELECT singleton_id,revision FROM household_need_state").use {
            check(it.step() && it.getLong(0) == 1L && !it.isNull(1)) { "Missing Household10 state" }
            val value = it.getLong(1)
            check(!it.step()) { "Multiple Household10 state rows" }
            value
        }
        val rows = db.prepare(
            "SELECT $NEED_COLUMNS,length(CAST(need_id AS BLOB)),length(CAST(name AS BLOB)) FROM household_need",
        ).use { stmt ->
            buildList {
                while (stmt.step()) {
                    check(size < MAX_ROWS && stmt.getLong(8) in 1..384 && stmt.getLong(9) in 1..480) {
                        "Household10 SQL rows exceed their bounds"
                    }
                    val quantity = stmt.getLong(2)
                    check(quantity in 1..Int.MAX_VALUE.toLong()) { "Invalid Household10 SQL quantity" }
                    add(RealityHousehold10Row(
                        stmt.getText(0), stmt.getText(1), quantity.toInt(), stmt.getText(3), stmt.getText(4),
                        stmt.getText(5), stmt.getLong(6), if (stmt.isNull(7)) null else stmt.getLong(7),
                    ))
                }
            }.sortedBy { it.id }
        }
        return RealityHousehold10Rows(revision, rows).also(::validate)
    }

    fun validate(snapshot: RealityHousehold10Rows) {
        require(snapshot.revision >= 0 && snapshot.needs.size <= MAX_ROWS) { "Invalid Household10 snapshot size or revision" }
        var previousId = ""
        var size = 24
        for (row in snapshot.needs) {
            require(row.id.length in 1..128 && row.id > previousId &&
                row.id.none { it.isWhitespace() || it.isISOControl() }) { "Invalid or unordered Household10 id" }
            require(row.name.length in 1..160 && row.name.isNotBlank() && row.name == row.name.trim() &&
                row.name.none(Char::isISOControl)) { "Invalid Household10 name" }
            require(row.quantityThousandths > 0 && row.unit in units && row.priority in priorities &&
                row.status in statuses && row.revision in 0..snapshot.revision &&
                (row.dueEpochDay == null || row.dueEpochDay in dueDays)) { "Invalid Household10 row" }
            val idBytes = utf8Size(row.id)
            val nameBytes = utf8Size(row.name)
            size += 23 + idBytes + nameBytes + row.unit.length + row.priority.length + row.status.length
            if (row.dueEpochDay != null) size += Long.SIZE_BYTES
            require(size <= MAX_BYTES) { "Household10 snapshot exceeds 4 MiB" }
            previousId = row.id
        }
    }

    private fun utf8Size(text: String): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.toString(Charsets.UTF_8) == text) { "Invalid Household10 UTF-8" }
        return bytes.size
    }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step()) { "Missing Household10 SQLite scalar" }
        it.getLong(0)
    }
}
