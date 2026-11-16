package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * W01-M0A: schema-only installer for a caller-certified, isolated STAGED Reality8 file.
 *
 * This object deliberately does not open files, own a generation, modify a pointer,
 * bind an operation, or grant access. M0B must establish the staged-generation fence
 * before handing its already-open connection to this installer.
 *
 * Legacy RealityStore openers still accept only their existing schema 7 / 8.
 */
internal object RealityFinance9Schema {
    const val VERSION = 9
    private const val SOURCE_VERSION = 8
    private val fingerprintFormat = Regex("[0-9a-f]{64}")

    fun installOnStagedEightConnection(
        connection: SQLiteConnection,
        migrationId: String,
        sourceFingerprint: String,
        finishedAtEpochMillis: Long,
        afterStatement: ((Int) -> Unit)? = null,
    ) {
        require(migrationId.isNotBlank() && migrationId.length <= 128) {
            "migration identity must be bounded and nonblank"
        }
        require(fingerprintFormat.matches(sourceFingerprint)) {
            "source fingerprint must be canonical SHA-256"
        }
        require(finishedAtEpochMillis >= 0L) { "migration time must be nonnegative" }

        check(connection.scalarLong("PRAGMA user_version") == SOURCE_VERSION.toLong()) {
            "Finance9 installer accepts only an already staged Reality8"
        }
        check(connection.scalarLong("PRAGMA foreign_keys") == 1L) {
            "Finance9 requires foreign-key enforcement before BEGIN"
        }
        val required = listOf(
            "profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
            "finance_state", "finance_account", "finance_account_opening", "finance_entry",
            "objective_state", "objective_record", "matter_state", "matter_record",
            "reminder_state", "reminder_rule", "reminder_occurrence",
            "monthly_plan_state", "monthly_plan_item",
        )
        check(required.all { connection.tableExists(it) }) {
            "Finance9 requires the complete staged Reality8 schema"
        }
        check(connection.integrityHealthy()) { "Reality8 source failed integrity checks" }
        check(!connection.tableExists("finance_acl_state")) {
            "Finance9 objects already present on a schema8 candidate"
        }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            statements.forEachIndexed { index, statement ->
                connection.execSQL(statement)
                afterStatement?.invoke(index + 1)
            }
            connection.execSQL(
                """
                INSERT INTO finance_opening_confirmation(
                    account_id, amount_minor, confirmation_state, confirmed_at_ms, provenance
                )
                SELECT account_id, NULL, 'LEGACY_UNVERIFIED', NULL, 'MIGRATED_FROM_8'
                FROM finance_account
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO finance_acl_state(singleton_id, next_policy_epoch, policy_revision)
                VALUES (1, 1, 0)
                """.trimIndent()
            )
            connection.prepare(
                """
                INSERT INTO finance_schema_migration_audit(
                    migration_id, from_version, to_version, source_fingerprint, finished_at_ms
                ) VALUES (?, 8, 9, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, migrationId)
                statement.bindText(2, sourceFingerprint)
                statement.bindLong(3, finishedAtEpochMillis)
                statement.step()
            }
            connection.execSQL("PRAGMA user_version = 9")
            check(connection.integrityHealthy()) {
                "Finance9 staged result failed SQLite integrity or foreign keys"
            }
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
        check(connection.scalarLong("PRAGMA user_version") == VERSION.toLong()) {
            "Finance9 staged schema was not durably installed"
        }
    }

    private fun SQLiteConnection.tableExists(name: String): Boolean =
        prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use {
            it.bindText(1, name)
            it.step()
        }

    private fun SQLiteConnection.scalarLong(sql: String): Long =
        prepare(sql).use { statement ->
            check(statement.step()) { "missing scalar value: " + sql }
            statement.getLong(0)
        }

    private fun SQLiteConnection.integrityHealthy(): Boolean {
        val integrity = prepare("PRAGMA integrity_check").use {
            check(it.step()) { "integrity_check missing result" }
            it.getText(0) == "ok"
        }
        val foreignKeysHealthy = prepare("PRAGMA foreign_key_check").use { !it.step() }
        return integrity && foreignKeysHealthy
    }

    private val statements: List<String> = listOf(
        """
        CREATE TABLE finance_opening_confirmation (
            account_id TEXT NOT NULL PRIMARY KEY,
            amount_minor INTEGER CHECK(amount_minor IS NULL OR amount_minor >= 0),
            confirmation_state TEXT NOT NULL
                CHECK(confirmation_state IN ('LEGACY_UNVERIFIED','CONFIRMED')),
            confirmed_at_ms INTEGER CHECK(confirmed_at_ms IS NULL OR confirmed_at_ms >= 0),
            provenance TEXT NOT NULL CHECK(length(trim(provenance)) BETWEEN 1 AND 160),
            CHECK(
                (confirmation_state='LEGACY_UNVERIFIED' AND amount_minor IS NULL
                    AND confirmed_at_ms IS NULL)
                OR
                (confirmation_state='CONFIRMED' AND amount_minor IS NOT NULL
                    AND confirmed_at_ms IS NOT NULL)
            ),
            FOREIGN KEY(account_id) REFERENCES finance_account(account_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_acl_state (
            singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id=1),
            next_policy_epoch INTEGER NOT NULL CHECK(next_policy_epoch>0),
            policy_revision INTEGER NOT NULL CHECK(policy_revision>=0)
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_acl_policy (
            principal_hash TEXT NOT NULL,
            generation_id TEXT NOT NULL,
            pointer_sequence INTEGER NOT NULL CHECK(pointer_sequence>0),
            operation_id TEXT NOT NULL,
            object_scope TEXT NOT NULL,
            policy_epoch INTEGER NOT NULL CHECK(policy_epoch>0),
            revoked INTEGER NOT NULL CHECK(revoked IN (0,1)),
            expires_at_ms INTEGER NOT NULL CHECK(expires_at_ms>0),
            CHECK(length(principal_hash)=64),
            CHECK(length(trim(generation_id))>0),
            CHECK(length(trim(operation_id))>0),
            CHECK(length(trim(object_scope))>0),
            PRIMARY KEY(principal_hash, generation_id, pointer_sequence,
                        operation_id, object_scope)
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_apply_grant (
            grant_id TEXT NOT NULL PRIMARY KEY,
            principal_hash TEXT NOT NULL,
            generation_id TEXT NOT NULL,
            pointer_sequence INTEGER NOT NULL CHECK(pointer_sequence>0),
            operation_id TEXT NOT NULL,
            object_scope TEXT NOT NULL,
            policy_epoch INTEGER NOT NULL CHECK(policy_epoch>0),
            fingerprint TEXT NOT NULL CHECK(length(fingerprint)=64),
            intent_key TEXT NOT NULL UNIQUE CHECK(length(trim(intent_key))>0),
            issued_at_ms INTEGER NOT NULL CHECK(issued_at_ms>=0),
            expires_at_ms INTEGER NOT NULL CHECK(expires_at_ms>=issued_at_ms),
            used_at_ms INTEGER CHECK(used_at_ms IS NULL OR used_at_ms>=issued_at_ms),
            receipt_id TEXT UNIQUE,
            CHECK((used_at_ms IS NULL)=(receipt_id IS NULL)),
            FOREIGN KEY(principal_hash,generation_id,pointer_sequence,operation_id,object_scope)
                REFERENCES finance_acl_policy(
                    principal_hash,generation_id,pointer_sequence,operation_id,object_scope
                ) ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(receipt_id) REFERENCES execution_receipt(receipt_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE UNIQUE INDEX finance_entry_account_identity
        ON finance_entry(entry_id, account_id)
        """.trimIndent(),
        """
        CREATE TABLE finance_transfer (
            transfer_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(transfer_id))>0),
            from_account_id TEXT NOT NULL,
            to_account_id TEXT NOT NULL,
            debit_entry_id TEXT NOT NULL UNIQUE,
            credit_entry_id TEXT NOT NULL UNIQUE,
            currency_code TEXT NOT NULL CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
            amount_minor INTEGER NOT NULL CHECK(amount_minor>0),
            effective_at_epoch_millis INTEGER NOT NULL,
            recorded_at_epoch_millis INTEGER NOT NULL,
            CHECK(from_account_id<>to_account_id),
            CHECK(debit_entry_id<>credit_entry_id),
            CHECK(effective_at_epoch_millis<=recorded_at_epoch_millis),
            FOREIGN KEY(debit_entry_id,from_account_id)
                REFERENCES finance_entry(entry_id,account_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(credit_entry_id,to_account_id)
                REFERENCES finance_entry(entry_id,account_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(from_account_id) REFERENCES finance_account(account_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(to_account_id) REFERENCES finance_account(account_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TRIGGER finance_transfer_guard BEFORE INSERT ON finance_transfer
        BEGIN
            SELECT CASE WHEN NOT EXISTS (
                SELECT 1 FROM finance_entry d
                JOIN finance_entry c ON c.entry_id=NEW.credit_entry_id
                JOIN finance_account a ON a.account_id=NEW.from_account_id
                JOIN finance_account b ON b.account_id=NEW.to_account_id
                WHERE d.entry_id=NEW.debit_entry_id
                  AND d.account_id=NEW.from_account_id
                  AND c.account_id=NEW.to_account_id
                  AND d.kind='EXPENSE' AND c.kind='INCOME'
                  AND d.amount_minor=NEW.amount_minor
                  AND c.amount_minor=NEW.amount_minor
                  AND d.currency_code=NEW.currency_code
                  AND c.currency_code=NEW.currency_code
                  AND a.currency_code=NEW.currency_code
                  AND b.currency_code=NEW.currency_code
            ) THEN RAISE(ABORT,'finance transfer effects inconsistent') END;
        END
        """.trimIndent(),
        """
        CREATE TABLE finance_debt (
            debt_id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),
            creditor TEXT NOT NULL CHECK(length(trim(creditor)) BETWEEN 1 AND 160),
            currency_code TEXT NOT NULL CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
            principal_minor INTEGER NOT NULL CHECK(principal_minor>0),
            revision INTEGER NOT NULL CHECK(revision>=0),
            created_at_ms INTEGER NOT NULL CHECK(created_at_ms>=0),
            updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms>=created_at_ms)
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_schedule (
            schedule_id TEXT NOT NULL PRIMARY KEY,
            debt_id TEXT,
            currency_code TEXT NOT NULL CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
            installment_minor INTEGER NOT NULL CHECK(installment_minor>0),
            start_period TEXT NOT NULL CHECK(length(start_period)=7),
            total_count INTEGER CHECK(total_count IS NULL OR total_count>0),
            recurrence TEXT NOT NULL CHECK(recurrence IN ('ONCE','MONTHLY')),
            version INTEGER NOT NULL CHECK(version>0),
            active INTEGER NOT NULL CHECK(active IN (0,1)),
            updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms>=0),
            FOREIGN KEY(debt_id) REFERENCES finance_debt(debt_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_plan_item (
            plan_item_id TEXT NOT NULL PRIMARY KEY,
            schedule_id TEXT,
            schedule_version INTEGER CHECK(schedule_version IS NULL OR schedule_version>0),
            debt_id TEXT,
            period TEXT NOT NULL CHECK(length(period)=7),
            ordinal INTEGER NOT NULL CHECK(ordinal>0),
            due_local_date TEXT NOT NULL CHECK(length(due_local_date)=10),
            currency_code TEXT NOT NULL CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
            expected_minor INTEGER NOT NULL CHECK(expected_minor>0),
            state TEXT NOT NULL CHECK(state IN ('PLANNED','CANCELLED')),
            updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms>=0),
            CHECK((schedule_id IS NULL)=(schedule_version IS NULL)),
            UNIQUE(schedule_id,schedule_version,period,ordinal),
            FOREIGN KEY(schedule_id) REFERENCES finance_schedule(schedule_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(debt_id) REFERENCES finance_debt(debt_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_plan_item_payment (
            plan_item_id TEXT NOT NULL,
            entry_id TEXT NOT NULL UNIQUE,
            amount_minor INTEGER NOT NULL CHECK(amount_minor>0),
            PRIMARY KEY(plan_item_id,entry_id),
            FOREIGN KEY(plan_item_id) REFERENCES finance_plan_item(plan_item_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            FOREIGN KEY(entry_id) REFERENCES finance_entry(entry_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TRIGGER finance_plan_payment_guard
        BEFORE INSERT ON finance_plan_item_payment
        BEGIN
            SELECT CASE WHEN NOT EXISTS (
                SELECT 1 FROM finance_entry e
                JOIN finance_plan_item p ON p.plan_item_id=NEW.plan_item_id
                WHERE e.entry_id=NEW.entry_id
                  AND e.kind='EXPENSE'
                  AND e.amount_minor=NEW.amount_minor
                  AND e.currency_code=p.currency_code
            ) THEN RAISE(ABORT,'finance payment is not matching expense') END;
        END
        """.trimIndent(),
        """
        CREATE TABLE finance_budget_limit (
            period TEXT NOT NULL CHECK(length(period)=7),
            category TEXT NOT NULL CHECK(length(trim(category)) BETWEEN 1 AND 80),
            currency_code TEXT NOT NULL CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
            limit_minor INTEGER NOT NULL CHECK(limit_minor>=0),
            revision INTEGER NOT NULL CHECK(revision>0),
            updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms>=0),
            source TEXT NOT NULL CHECK(length(trim(source))>0),
            PRIMARY KEY(period,category,currency_code)
        )
        """.trimIndent(),
        """
        CREATE TABLE finance_schema_migration_audit (
            migration_id TEXT NOT NULL PRIMARY KEY,
            from_version INTEGER NOT NULL CHECK(from_version IN (7,8)),
            to_version INTEGER NOT NULL CHECK(to_version=9),
            source_fingerprint TEXT NOT NULL CHECK(length(source_fingerprint)=64),
            finished_at_ms INTEGER NOT NULL CHECK(finished_at_ms>=0)
        )
        """.trimIndent(),
        "CREATE INDEX finance_acl_expiry_idx ON finance_acl_policy(revoked,expires_at_ms)",
        "CREATE INDEX finance_apply_fingerprint_idx ON finance_apply_grant(fingerprint,intent_key)",
        "CREATE INDEX finance_plan_due_idx ON finance_plan_item(due_local_date,period)",
        "CREATE INDEX finance_plan_debt_idx ON finance_plan_item(debt_id,period)",
    )
}
