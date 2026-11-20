package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Raw, strictly whitelisted data. Not an authenticated backup or an ACL grant. */
data class RealityFinance9SealedTableRows(
    val table: String,
    val rows: List<List<String?>>,
)

data class RealityFinance9SealedSnapshot(
    val schemaVersion: Int,
    val tables: List<RealityFinance9SealedTableRows>,
)

/**
 * M0C2A: immutable, READ-ONLY extraction of exactly eight Finance9 DATA tables.
 *
 * The generation owner, not this file port, certifies a canonical non-ACTIVE
 * five-store stage and a pinned pointer before and after this read.
 * No app/Chat or user-facing backup/export calls this adapter.
 */
object RealityFinance9SealedReadPort {
    private data class Spec(
        val name: String,
        val columns: List<String>,
        val numeric: Set<Int>,
        val key: List<String>,
    )

    // Must match RealityFinance9Schema and the independent F9S1 data whitelist.
    private val specs = listOf(
        Spec("finance_opening_confirmation",
            listOf("account_id","amount_minor","confirmation_state",
                "confirmed_at_ms","provenance"), setOf(1,3), listOf("account_id")),
        Spec("finance_transfer",
            listOf("transfer_id","from_account_id","to_account_id",
                "debit_entry_id","credit_entry_id","currency_code","amount_minor",
                "effective_at_epoch_millis","recorded_at_epoch_millis"),
            setOf(6,7,8), listOf("transfer_id")),
        Spec("finance_debt",
            listOf("debt_id","title","creditor","currency_code",
                "principal_minor","revision","created_at_ms","updated_at_ms"),
            setOf(4,5,6,7), listOf("debt_id")),
        Spec("finance_schedule",
            listOf("schedule_id","debt_id","currency_code","installment_minor",
                "start_period","total_count","recurrence","version","active",
                "updated_at_ms"),
            setOf(3,5,7,8,9), listOf("schedule_id")),
        Spec("finance_plan_item",
            listOf("plan_item_id","schedule_id","schedule_version","debt_id",
                "period","ordinal","due_local_date","currency_code","expected_minor",
                "state","updated_at_ms"),
            setOf(2,5,8,10), listOf("plan_item_id")),
        Spec("finance_plan_item_payment",
            listOf("plan_item_id","entry_id","amount_minor"),
            setOf(2), listOf("plan_item_id","entry_id")),
        Spec("finance_budget_limit",
            listOf("period","category","currency_code","limit_minor",
                "revision","updated_at_ms","source"),
            setOf(3,4,5), listOf("period","category","currency_code")),
        Spec("finance_schema_migration_audit",
            listOf("migration_id","from_version","to_version",
                "source_fingerprint","finished_at_ms"),
            setOf(1,2,4), listOf("migration_id")),
    )

    fun readTrustedSealedStage9(file: Path): RealityFinance9SealedSnapshot = read(file, 9)

    /** The physical schema is 10; the Finance9 DATA section format is still 9. */
    fun readTrustedSealedStage10(file: Path): RealityFinance9SealedSnapshot =
        read(Household10RowsSql.checkFile(file), 10)

    private fun read(file: Path, expectedSchema: Long): RealityFinance9SealedSnapshot {
        val normalized = file.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized) &&
            Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Finance9 sealed read requires an existing regular SQLite file"
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            check(!Files.exists(normalized.resolveSibling(
                normalized.fileName.toString() + suffix,
            ))) { "Finance9 sealed read refuses SQLite sidecars" }
        }
        // Physicalize parent only; preserve NOFOLLOW fence on SQLite final file.
        val physical = normalized.parent.toRealPath().resolve(normalized.fileName)
        return BundledSQLiteDriver().open(
            physical.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            check(db.scalarLong("PRAGMA user_version") == expectedSchema) {
                "Finance9 sealed reader requires the exact admitted schema"
            }
            check(db.prepare("PRAGMA quick_check").use { stmt ->
                stmt.step() && stmt.getText(0) == "ok" && !stmt.step()
            }) { "Finance9 sealed read integrity failure" }
            check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
                "Finance9 sealed read foreign keys unhealthy"
            }
            var totalRows = 0
            var budgetBytes = 64L
            val data = specs.map { spec ->
                val columns = db.prepare("PRAGMA table_info(" + spec.name + ")").use {
                    val names = mutableListOf<String>()
                    while (it.step()) names.add(it.getText(1))
                    names.toList()
                }
                check(columns == spec.columns) {
                    "Finance9 sealed table shape mismatch: " + spec.name
                }
                val query = "SELECT " + spec.columns.joinToString(",") +
                    " FROM " + spec.name + " ORDER BY " +
                    spec.key.joinToString(",") { it + " COLLATE BINARY" }
                val rows = db.prepare(query).use { stmt ->
                    val result = mutableListOf<List<String?>>()
                    while (stmt.step()) {
                        totalRows++
                        check(totalRows <= 20_000) {
                            "Finance9 sealed rows exceed bounded budget"
                        }
                        val row = spec.columns.indices.map { i ->
                            val value = when {
                                stmt.isNull(i) -> null
                                i in spec.numeric -> stmt.getLong(i).toString()
                                else -> stmt.getText(i)
                            }
                            budgetBytes += 4L + (value?.toByteArray(Charsets.UTF_8)?.size ?: 0)
                            check(budgetBytes <= 4L * 1024 * 1024) {
                                "Finance9 sealed data exceeds 4MiB budget"
                            }
                            value
                        }
                        result.add(row)
                    }
                    result.toList()
                }
                RealityFinance9SealedTableRows(spec.name, rows)
            }
            RealityFinance9SealedSnapshot(9, data)
        }
    }

    private fun SQLiteConnection.scalarLong(sql: String): Long =
        prepare(sql).use {
            check(it.step()) { "missing SQLite scalar: " + sql }
            it.getLong(0)
        }
}
