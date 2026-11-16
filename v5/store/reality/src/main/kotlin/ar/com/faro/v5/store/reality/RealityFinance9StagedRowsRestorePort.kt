package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Verified data-section only, NOT a complete five-store restore or active host. */
enum class RealityFinance9SectionRestoreDisposition {
    RESTORED,
    ALREADY_IDENTICAL,
}

/**
 * Exact eight-table Finance9 restore to an independently admitted STAGED9 file.
 * No file is created, no SQLite version is changed, no authorization is imported.
 *
 * The generation owner authenticates FAROBAK2/FBS2v4, checks a NON-ACTIVE
 * five-store canonical target and an exact pointer lease before this bridge.
 * The target's legacy ledger/accounts must already be compatible; all FKs
 * remain enabled, and no partial Finance9 write survives a failed transaction.
 */
object RealityFinance9StagedRowsRestorePort {
    private data class Spec(
        val name: String,
        val columns: List<String>,
        val numeric: Set<Int>,
        val key: List<String>,
    )

    /** Fixed SQL identifiers; no archive-supplied identifier is interpolated. */
    private val specs = listOf(
        Spec("finance_opening_confirmation",
            listOf("account_id", "amount_minor", "confirmation_state",
                "confirmed_at_ms", "provenance"),
            setOf(1, 3), listOf("account_id")),
        Spec("finance_transfer",
            listOf("transfer_id", "from_account_id", "to_account_id",
                "debit_entry_id", "credit_entry_id", "currency_code",
                "amount_minor", "effective_at_epoch_millis", "recorded_at_epoch_millis"),
            setOf(6, 7, 8), listOf("transfer_id")),
        Spec("finance_debt",
            listOf("debt_id", "title", "creditor", "currency_code",
                "principal_minor", "revision", "created_at_ms", "updated_at_ms"),
            setOf(4, 5, 6, 7), listOf("debt_id")),
        Spec("finance_schedule",
            listOf("schedule_id", "debt_id", "currency_code", "installment_minor",
                "start_period", "total_count", "recurrence", "version",
                "active", "updated_at_ms"),
            setOf(3, 5, 7, 8, 9), listOf("schedule_id")),
        Spec("finance_plan_item",
            listOf("plan_item_id", "schedule_id", "schedule_version", "debt_id",
                "period", "ordinal", "due_local_date", "currency_code",
                "expected_minor", "state", "updated_at_ms"),
            setOf(2, 5, 8, 10), listOf("plan_item_id")),
        Spec("finance_plan_item_payment",
            listOf("plan_item_id", "entry_id", "amount_minor"),
            setOf(2), listOf("plan_item_id", "entry_id")),
        Spec("finance_budget_limit",
            listOf("period", "category", "currency_code", "limit_minor",
                "revision", "updated_at_ms", "source"),
            setOf(3, 4, 5), listOf("period", "category", "currency_code")),
        Spec("finance_schema_migration_audit",
            listOf("migration_id", "from_version", "to_version",
                "source_fingerprint", "finished_at_ms"),
            setOf(1, 2, 4), listOf("migration_id")),
    )

    fun restoreOrVerifyTrustedStagedNine(
        file: Path,
        snapshot: RealityFinance9SealedSnapshot,
    ): RealityFinance9SectionRestoreDisposition {
        require(snapshot.schemaVersion == 9 && snapshot.tables.size == specs.size &&
            snapshot.tables.map { it.table } == specs.map { it.name }) {
            "Finance9 input must contain exactly eight whitelisted tables"
        }
        require(snapshot.tables.zip(specs).all { (table, spec) ->
            table.rows.all { row -> row.size == spec.columns.size }
        }) { "Finance9 input column count is not canonical" }
        val normalized = file.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized) &&
            Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Finance9 stage restore requires existing regular non-symlink SQLite"
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            check(!Files.exists(normalized.resolveSibling(
                normalized.fileName.toString() + suffix,
            ))) { "Finance9 stage restore refuses uncheckpointed sidecars" }
        }
        return BundledSQLiteDriver().open(
            normalized.parent.toRealPath().resolve(normalized.fileName).toString(),
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            check(db.scalar("PRAGMA foreign_keys") == 1L &&
                db.scalar("PRAGMA user_version") == 9L) {
                "Finance9 stage restore requires schema9 with FK ON"
            }
            requireShape(db)
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            val disposition: RealityFinance9SectionRestoreDisposition
            try {
                val old = currentSnapshot(db)
                if (old == snapshot) {
                    disposition = RealityFinance9SectionRestoreDisposition.ALREADY_IDENTICAL
                } else {
                    requireFreshTarget(db, old)
                    // These two tables are auto-seeded by M0A/M0B; their old
                    // stage-only provenance is replaced by authenticated data.
                    // The other six data tables MUST be empty at this point.
                    db.execSQL("DELETE FROM finance_opening_confirmation")
                    db.execSQL("DELETE FROM finance_schema_migration_audit")
                    snapshot.tables.zip(specs).forEach { (table, spec) ->
                        val placeholders = List(spec.columns.size) { "?" }.joinToString(",")
                        val sql = "INSERT INTO " + spec.name + "(" +
                            spec.columns.joinToString(",") + ") VALUES (" +
                            placeholders + ")"
                        table.rows.forEach { row ->
                            db.prepare(sql).use { stmt ->
                                row.forEachIndexed { index, value ->
                                    when {
                                        value == null -> stmt.bindNull(index + 1)
                                        index in spec.numeric -> {
                                            val n = value.toLongOrNull()
                                            check(n != null && n >= 0L &&
                                                n.toString() == value) {
                                                "Finance9 imported numeric noncanonical"
                                            }
                                            stmt.bindLong(index + 1, n)
                                        }
                                        else -> stmt.bindText(index + 1, value)
                                    }
                                }
                                stmt.step()
                            }
                        }
                    }
                    check(currentSnapshot(db) == snapshot) {
                        "Finance9 staged restore not exact after SQL insertion"
                    }
                    disposition = RealityFinance9SectionRestoreDisposition.RESTORED
                }
                check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
                    "Finance9 staged restore foreign key violations"
                }
                check(db.prepare("PRAGMA integrity_check").use {
                    it.step() && it.getText(0) == "ok" && !it.step()
                }) { "Finance9 staged restore SQLite integrity failure" }
                db.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { db.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) {
                    "Finance9 staged restore WAL checkpoint unavailable"
                }
            }
            disposition
        }.also { disposition ->
            // A failed or interrupted post-COMMIT verification MUST leave
            // the stage quarantined until exact replay/verification.
            val observed = RealityFinance9SealedReadPort.readTrustedSealedStage9(normalized)
            check(observed == snapshot) {
                "Finance9 stage restore post-close readback differs from archive"
            }
        }
    }

    private fun requireShape(db: SQLiteConnection) {
        specs.forEach { spec ->
            val actual = db.prepare("PRAGMA table_info(" + spec.name + ")").use { stmt ->
                val found = mutableListOf<String>()
                while (stmt.step()) found.add(stmt.getText(1))
                found.toList()
            }
            check(actual == spec.columns) { "Finance9 target schema mismatch: " + spec.name }
        }
    }

    private fun currentSnapshot(db: SQLiteConnection): RealityFinance9SealedSnapshot =
        RealityFinance9SealedSnapshot(9, specs.map { spec ->
            val sql = "SELECT " + spec.columns.joinToString(",") +
                " FROM " + spec.name + " ORDER BY " +
                spec.key.joinToString(",") { it + " COLLATE BINARY" }
            val result = db.prepare(sql).use { stmt ->
                val rows = mutableListOf<List<String?>>()
                while (stmt.step()) {
                    check(rows.size < 20_000) { "Finance9 row budget exceeded" }
                    rows.add(spec.columns.indices.map { i ->
                        when {
                            stmt.isNull(i) -> null
                            i in spec.numeric -> stmt.getLong(i).toString()
                            else -> stmt.getText(i)
                        }
                    })
                }
                rows.toList()
            }
            RealityFinance9SealedTableRows(spec.name, result)
        })

    private fun requireFreshTarget(
        db: SQLiteConnection,
        current: RealityFinance9SealedSnapshot,
    ) {
        check(db.scalar("SELECT count(*) FROM finance_acl_policy") == 0L &&
            db.scalar("SELECT count(*) FROM finance_apply_grant") == 0L &&
            db.scalar("SELECT count(*) FROM finance_acl_state WHERE " +
                "singleton_id=1 AND next_policy_epoch=1 AND policy_revision=0") == 1L) {
            "Finance9 target has authorization state: deny foreign restore"
        }
        current.tables.forEach { table ->
            when (table.table) {
                "finance_opening_confirmation" ->
                    check(table.rows.all {
                        it[1] == null && it[2] == "LEGACY_UNVERIFIED" && it[3] == null
                    }) { "Finance9 opening already human-confirmed" }
                "finance_schema_migration_audit" ->
                    check(table.rows.size == 1) {
                        "Finance9 staged migration origin must be fresh"
                    }
                else -> check(table.rows.isEmpty()) {
                    "Finance9 target contains previously committed money data"
                }
            }
        }
    }

    private fun SQLiteConnection.scalar(sql: String): Long =
        prepare(sql).use {
            check(it.step()) { "Finance9 SQL scalar missing" }
            it.getLong(0)
        }
}
