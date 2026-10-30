package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentityPort
import ar.com.faro.v5.kernel.contracts.ApplyAttemptJournalState
import ar.com.faro.v5.kernel.contracts.ApplyAttemptLookupPort
import ar.com.faro.v5.kernel.contracts.ApplyAttemptSnapshot
import ar.com.faro.v5.kernel.contracts.ApplyCommitPort
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionPort
import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStampLookupPort
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.ExecutionReceiptLookupPort
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OutboxEffectIntent
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedPlanView
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.ReceiptId
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.profile.api.ProfileFieldProvenance
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot
import ar.com.faro.v5.owner.profile.spi.ProfilePersistencePort
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecord
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceEntryId
import ar.com.faro.v5.owner.finance.api.FinanceEntryKind
import ar.com.faro.v5.owner.finance.api.FinanceEntryConcept
import ar.com.faro.v5.owner.finance.api.FinanceEntryCategory
import ar.com.faro.v5.owner.finance.spi.FinancePersistencePort
import java.util.UUID

class RealityStore(
    fileName: String,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    private val transactionIdFactory: () -> TransactionId = {
        TransactionId("tx-" + UUID.randomUUID())
    },
    private val receiptIdFactory: () -> ReceiptId = {
        ReceiptId("receipt-" + UUID.randomUUID())
    },
    private val materialWriteGate: MaterialWriteGate? = null,
) : AtomicApplyTransactionPort,
    ApplyCommitPort,
    DependencyStampLookupPort,
    ProfilePersistencePort,
    AutoCloseable {

    private val lock = Any()
    /** Opt-in staged V2; never activate without one shared five-owner gate. */
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false
    private var activeTransactionId: TransactionId? = null

    /**
     * Durable Apply ports are exposed as adapters instead of implemented
     * directly because ApplyAttemptLookupPort and ExecutionReceiptLookupPort
     * intentionally share the same find(TransactionId) JVM signature with
     * different return types.
     */
    val applyAttemptIdentities: ApplyAttemptIdentityPort =
        ApplyAttemptIdentityPort(::resolveAttemptIdentity)

    val applyAttemptLookup: ApplyAttemptLookupPort =
        ApplyAttemptLookupPort(::findAttempt)

    val executionReceiptLookup: ExecutionReceiptLookupPort =
        ExecutionReceiptLookupPort(::findReceipt)

    val financePersistence: FinancePersistencePort =
        object : FinancePersistencePort {
            override fun read(): FinanceSnapshot = readFinance()

            override fun createAccount(
                create: FinanceAccountCreate,
                transactionScope: RealityTransactionScope,
            ): FinanceAccountSnapshot =
                createFinanceAccount(create, transactionScope)

            override fun recordEntry(
                record: FinanceEntryRecord,
                transactionScope: RealityTransactionScope,
            ): FinanceEntrySnapshot =
                recordFinanceEntry(record, transactionScope)
        }

    val maintenance: RealityMaintenancePort =
        RealityMaintenancePort(::inspectForMaintenance)

    val semanticMigration: RealitySemanticMigrationPort =
        object : RealitySemanticMigrationPort {
            override fun snapshot(): RealitySemanticMigrationData =
                readSemanticMigrationSnapshot()

            override fun importFresh(
                data: RealitySemanticMigrationData,
            ): RealitySemanticMigrationData =
                importFreshSemanticMigration(data)
        }

    val backup: RealityBackupPort =
        object : RealityBackupPort {
            override fun snapshot(): RealityBackupSnapshot = readBackupSnapshot()

            override fun restoreOrVerify(
                snapshot: RealityBackupSnapshot,
            ): RealityBackupSnapshot = restoreOrVerifyBackup(snapshot)
        }

    init {
        withMaterialAdmission {
            synchronized(lock) {
                configure(fileName)
                initializeSchema()
            }
        }
    }

    override fun <T> run(
        transactionId: TransactionId,
        block: (RealityTransactionScope) -> AtomicApplyTransactionDecision<T>,
    ): T = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        check(activeTransactionId == null) {
            "nested Reality transactions are forbidden"
        }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        activeTransactionId = transactionId
        val scope = SqliteRealityTransactionScope(
            transactionId = transactionId,
            owner = this,
        )

        try {
            when (val decision = block(scope)) {
                is AtomicApplyTransactionDecision.Commit -> {
                    connection.execSQL("COMMIT TRANSACTION")
                    decision.value
                }

                is AtomicApplyTransactionDecision.Rollback -> {
                    connection.execSQL("ROLLBACK TRANSACTION")
                    decision.value
                }
            }
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        } finally {
            activeTransactionId = null
        }
    } }

    override fun read(): ProfileSnapshot = synchronized(lock) {
        checkOpen()
        readProfile()
    }

    override fun updateDeclared(
        displayName: DeclaredDisplayName,
        transactionScope: RealityTransactionScope,
    ): ProfileSnapshot = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)

        connection.prepare(
            """
            UPDATE profile_state
            SET display_name = ?,
                provenance = ?,
                revision = revision + 1
            WHERE singleton_id = 1
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, displayName.value)
            statement.bindText(2, ProfileFieldProvenance.DECLARED_BY_USER.name)
            statement.step()
        }

        readProfile()
    }

    override fun current(
        authorityBoundary: AuthorityBoundaryId,
        dependencyKey: DependencyKey,
    ): DependencyStampValue? = synchronized(lock) {
        checkOpen()
        when {
            authorityBoundary == ProfileSnapshot.PROFILE_AUTHORITY &&
                dependencyKey == ProfileSnapshot.PROFILE_DECLARED_KEY ->
                readProfile().dependencyStamp().observedValue

            authorityBoundary == FinanceSnapshot.FINANCE_AUTHORITY &&
                dependencyKey == FinanceSnapshot.FINANCE_ACCOUNTS_KEY ->
                readFinance().accountsDependencyStamp().observedValue

            authorityBoundary == FinanceSnapshot.FINANCE_AUTHORITY &&
                dependencyKey.value.startsWith("finance:account:") ->
                readFinanceAccountDependencyValue(dependencyKey)

            else -> null
        }
    }


    override fun commit(
        transactionScope: RealityTransactionScope,
        identity: ApplyAttemptIdentity,
        outboxIntents: List<OutboxEffectIntent>,
    ): ExecutionReceipt = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)
        check(transactionScope.transactionId == identity.transactionId) {
            "Apply commit transaction identity mismatch"
        }

        val attempt = findAttempt(identity.transactionId)
            ?: error("Apply commit requires an existing durable attempt")
        check(attempt.planFingerprint == identity.planFingerprint) {
            "Apply commit plan fingerprint mismatch"
        }
        check(attempt.state == ApplyAttemptJournalState.PREPARED) {
            "Apply commit requires PREPARED attempt"
        }
        check(findReceipt(identity.transactionId) == null) {
            "Apply commit receipt already exists"
        }
        check(outboxIntents.all { it.sourceTransactionId == identity.transactionId }) {
            "Reality Outbox source transaction mismatch"
        }

        val receipt = ExecutionReceipt(
            receiptId = receiptIdFactory(),
            transactionId = identity.transactionId,
            planFingerprint = identity.planFingerprint,
        )

        connection.prepare(
            """
            INSERT INTO execution_receipt(
                receipt_id,
                transaction_id,
                plan_fingerprint
            ) VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, receipt.receiptId.value)
            statement.bindText(2, receipt.transactionId.value)
            statement.bindText(3, receipt.planFingerprint.value)
            statement.step()
        }

        outboxIntents.forEach { intent ->
            connection.prepare(
                """
                INSERT INTO reality_outbox(
                    effect_id,
                    source_transaction_id,
                    idempotency_key,
                    payload_format_id,
                    payload_bytes
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, intent.effectId.value)
                statement.bindText(2, intent.sourceTransactionId.value)
                statement.bindText(3, intent.idempotencyKey.value)
                statement.bindText(4, intent.payload.formatId)
                statement.bindBlob(5, intent.payload.copyBytes())
                statement.step()
            }
        }

        connection.prepare(
            """
            UPDATE apply_attempt
            SET state = ?
            WHERE transaction_id = ?
              AND plan_fingerprint = ?
              AND state = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, ApplyAttemptJournalState.COMMITTED.name)
            statement.bindText(2, identity.transactionId.value)
            statement.bindText(3, identity.planFingerprint.value)
            statement.bindText(4, ApplyAttemptJournalState.PREPARED.name)
            statement.step()
        }

        val committed = findAttempt(identity.transactionId)
            ?: error("Apply attempt disappeared during commit")
        check(committed.state == ApplyAttemptJournalState.COMMITTED) {
            "Apply attempt did not transition to COMMITTED"
        }
        check(committed.planFingerprint == identity.planFingerprint) {
            "Committed Apply attempt fingerprint mismatch"
        }

        receipt
    }

    internal fun readOutboxEffect(
        effectId: EffectId,
    ): OutboxEffectIntent? = synchronized(lock) {
        checkOpen()
        connection.prepare(
            """
            SELECT source_transaction_id,
                   idempotency_key,
                   payload_format_id,
                   payload_bytes
            FROM reality_outbox
            WHERE effect_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, effectId.value)
            if (!statement.step()) {
                return@synchronized null
            }
            OutboxEffectIntent(
                effectId = effectId,
                sourceTransactionId = TransactionId(statement.getText(0)),
                idempotencyKey = IdempotencyKey(statement.getText(1)),
                payload = CanonicalPayload.fromEncoded(
                    statement.getText(2),
                    statement.getBlob(3),
                ),
            )
        }
    }

    internal fun integrityCheck(): String = synchronized(lock) {
        checkOpen()
        readIntegrityCheck()
    }

    private fun inspectForMaintenance(): RealityMaintenanceInspection =
        synchronized(lock) {
            checkOpen()
            RealityMaintenanceInspection(
                schemaVersion = readSchemaVersion(),
                integrityHealthy = readIntegrityCheck().equals("ok", ignoreCase = true),
                foreignKeysHealthy = readForeignKeysHealthy(),
                profilePresent = readProfile().displayName != null,
                financeAccountCount = readRowCount("finance_account"),
                financeEntryCount = readRowCount("finance_entry"),
                applyAttemptCount = readRowCount("apply_attempt"),
                executionReceiptCount = readRowCount("execution_receipt"),
                outboxEffectCount = readRowCount("reality_outbox"),
                profileRevision = readProfile().revision,
                financeRevision = readFinance().revision,
            )
        }

    private fun readIntegrityCheck(): String =
        connection.prepare("PRAGMA integrity_check").use { statement ->
            check(statement.step()) { "integrity_check returned no row" }
            statement.getText(0)
        }

    private fun readForeignKeysHealthy(): Boolean =
        connection.prepare("PRAGMA foreign_key_check").use { statement ->
            !statement.step()
        }

    private fun readRowCount(table: String): Long {
        require(
            table in
                setOf(
                    "finance_account",
                    "finance_entry",
                    "apply_attempt",
                    "execution_receipt",
                    "reality_outbox",
                )
        ) {
            "unsupported Reality maintenance table"
        }
        return connection.prepare("SELECT COUNT(*) FROM $table").use { statement ->
            check(statement.step()) { "row count returned no row for $table" }
            statement.getLong(0)
        }
    }

    internal fun schemaVersion(): Int = synchronized(lock) {
        checkOpen()
        readSchemaVersion()
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        check(activeTransactionId == null) {
            "cannot close RealityStore during active transaction"
        }
        connection.close()
        closed = true
    }

    private fun resolveAttemptIdentity(
        preparedPlan: PreparedPlanView,
    ): ApplyAttemptIdentity = synchronized(lock) {
        checkOpen()
        check(activeTransactionId == null) {
            "Apply attempt identity must be resolved before Reality transaction entry"
        }

        findAttemptByPlan(preparedPlan.planFingerprint)?.let { existing ->
            return@synchronized ApplyAttemptIdentity(
                transactionId = existing.transactionId,
                planFingerprint = existing.planFingerprint,
            )
        }

        val identity = ApplyAttemptIdentity(
            transactionId = transactionIdFactory(),
            planFingerprint = preparedPlan.planFingerprint,
        )

        connection.prepare(
            """
            INSERT INTO apply_attempt(
                transaction_id,
                plan_fingerprint,
                state
            ) VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, identity.transactionId.value)
            statement.bindText(2, identity.planFingerprint.value)
            statement.bindText(3, ApplyAttemptJournalState.PREPARED.name)
            statement.step()
        }

        identity
    }

    private fun findAttempt(
        transactionId: TransactionId,
    ): ApplyAttemptSnapshot? = synchronized(lock) {
        checkOpen()
        connection.prepare(
            """
            SELECT plan_fingerprint, state
            FROM apply_attempt
            WHERE transaction_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, transactionId.value)
            if (!statement.step()) {
                return@synchronized null
            }
            ApplyAttemptSnapshot(
                transactionId = transactionId,
                planFingerprint = PlanFingerprint(statement.getText(0)),
                state = parseAttemptState(statement.getText(1)),
            )
        }
    }

    private fun findAttemptByPlan(
        planFingerprint: PlanFingerprint,
    ): ApplyAttemptSnapshot? {
        connection.prepare(
            """
            SELECT transaction_id, state
            FROM apply_attempt
            WHERE plan_fingerprint = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, planFingerprint.value)
            if (!statement.step()) {
                return null
            }
            return ApplyAttemptSnapshot(
                transactionId = TransactionId(statement.getText(0)),
                planFingerprint = planFingerprint,
                state = parseAttemptState(statement.getText(1)),
            )
        }
    }

    private fun findReceipt(
        transactionId: TransactionId,
    ): ExecutionReceipt? = synchronized(lock) {
        checkOpen()
        connection.prepare(
            """
            SELECT receipt_id, plan_fingerprint
            FROM execution_receipt
            WHERE transaction_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, transactionId.value)
            if (!statement.step()) {
                return@synchronized null
            }
            ExecutionReceipt(
                receiptId = ReceiptId(statement.getText(0)),
                transactionId = transactionId,
                planFingerprint = PlanFingerprint(statement.getText(1)),
            )
        }
    }

    private fun parseAttemptState(value: String): ApplyAttemptJournalState =
        runCatching { ApplyAttemptJournalState.valueOf(value) }
            .getOrElse { error("unsupported ApplyAttempt state: $value") }

    private fun configure(fileName: String) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = FULL")

        if (fileName != ":memory:") {
            connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
                check(statement.step()) { "journal_mode returned no row" }
                check(statement.getText(0).equals("wal", ignoreCase = true)) {
                    "RealityStore requires WAL journal mode"
                }
            }
        }
    }

    private fun initializeSchema() {
        val version = readSchemaVersion()
        check(version in 0..SCHEMA_VERSION) {
            "unsupported RealityStore schema version: $version"
        }

        when (version) {
            0 -> schemaTransaction {
                createProfileSchema()
                createApplyRecoverySchema()
                createApplyCommitSchema()
                createFinanceSchema()
                createFinanceEntrySchema()
                setSchemaVersion(SCHEMA_VERSION)
            }

            1 -> schemaTransaction {
                createApplyRecoverySchema()
                createApplyCommitSchema()
                createFinanceSchema()
                createFinanceEntrySchema()
                setSchemaVersion(SCHEMA_VERSION)
            }

            2 -> schemaTransaction {
                createApplyCommitSchema()
                createFinanceSchema()
                createFinanceEntrySchema()
                setSchemaVersion(SCHEMA_VERSION)
            }

            3 -> schemaTransaction {
                createFinanceSchema()
                createFinanceEntrySchema()
                setSchemaVersion(SCHEMA_VERSION)
            }

            4 -> schemaTransaction {
                createFinanceEntrySchema()
                setSchemaVersion(SCHEMA_VERSION)
            }

            SCHEMA_VERSION -> Unit
        }

        validateRequiredSchema()
    }

    private fun createProfileSchema() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profile_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                display_name TEXT NOT NULL DEFAULT '',
                provenance TEXT NOT NULL DEFAULT '',
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),
                CHECK(
                    (display_name = '' AND provenance = '')
                    OR
                    (display_name <> '' AND provenance = 'DECLARED_BY_USER')
                )
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO profile_state(
                singleton_id,
                display_name,
                provenance,
                revision
            ) VALUES (1, '', '', 0)
            """.trimIndent()
        )
    }

    private fun createApplyRecoverySchema() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS apply_attempt (
                transaction_id TEXT NOT NULL PRIMARY KEY,
                plan_fingerprint TEXT NOT NULL UNIQUE,
                state TEXT NOT NULL CHECK(state IN ('PREPARED', 'COMMITTED')),
                UNIQUE(transaction_id, plan_fingerprint)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS execution_receipt (
                receipt_id TEXT NOT NULL PRIMARY KEY,
                transaction_id TEXT NOT NULL UNIQUE,
                plan_fingerprint TEXT NOT NULL,
                FOREIGN KEY(transaction_id, plan_fingerprint)
                    REFERENCES apply_attempt(transaction_id, plan_fingerprint)
                    ON UPDATE RESTRICT
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }


    private fun createApplyCommitSchema() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reality_outbox (
                effect_id TEXT NOT NULL PRIMARY KEY,
                source_transaction_id TEXT NOT NULL,
                idempotency_key TEXT NOT NULL UNIQUE,
                payload_format_id TEXT NOT NULL,
                payload_bytes BLOB NOT NULL,
                FOREIGN KEY(source_transaction_id)
                    REFERENCES apply_attempt(transaction_id)
                    ON UPDATE RESTRICT
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }

    private fun createFinanceSchema() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS finance_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO finance_state(singleton_id, revision)
            VALUES (1, 0)
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS finance_account (
                account_id TEXT NOT NULL PRIMARY KEY,
                display_name TEXT NOT NULL,
                currency_code TEXT NOT NULL
                    CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
                nature TEXT NOT NULL
                    CHECK(nature IN ('CASH', 'BANK', 'WALLET', 'INVESTMENT', 'OTHER')),
                CHECK(length(trim(account_id)) > 0),
                CHECK(length(trim(display_name)) > 0)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS finance_account_opening (
                account_id TEXT NOT NULL PRIMARY KEY,
                opening_cause_id TEXT NOT NULL UNIQUE,
                amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
                FOREIGN KEY(account_id)
                    REFERENCES finance_account(account_id)
                    ON UPDATE RESTRICT
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }

    private fun createFinanceEntrySchema() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS finance_entry (
                entry_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
                kind TEXT NOT NULL CHECK(kind IN ('INCOME', 'EXPENSE')),
                currency_code TEXT NOT NULL
                    CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
                concept TEXT NOT NULL,
                category TEXT NOT NULL,
                effective_at_epoch_millis INTEGER NOT NULL,
                recorded_at_epoch_millis INTEGER NOT NULL,
                provenance_label TEXT,
                CHECK(effective_at_epoch_millis <= recorded_at_epoch_millis),
                CHECK(length(trim(entry_id)) > 0),
                CHECK(length(trim(concept)) > 0),
                CHECK(length(trim(category)) > 0),
                FOREIGN KEY(account_id)
                    REFERENCES finance_account(account_id)
                    ON UPDATE RESTRICT
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }

    private fun validateRequiredSchema() {
        check(readSchemaVersion() == SCHEMA_VERSION) {
            "RealityStore schema version mismatch"
        }
        listOf(
            "profile_state",
            "apply_attempt",
            "execution_receipt",
            "reality_outbox",
            "finance_state",
            "finance_account",
            "finance_account_opening",
            "finance_entry",
        ).forEach { table ->
            check(tableExists(table)) {
                "RealityStore required table missing: $table"
            }
        }
    }

    private fun tableExists(table: String): Boolean =
        connection.prepare(
            """
            SELECT 1
            FROM sqlite_master
            WHERE type = 'table' AND name = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, table)
            statement.step()
        }

    private fun readSchemaVersion(): Int =
        connection.prepare("PRAGMA user_version").use { statement ->
            check(statement.step()) { "user_version returned no row" }
            statement.getLong(0).toInt()
        }

    private fun setSchemaVersion(version: Int) {
        connection.execSQL("PRAGMA user_version = $version")
    }

    private inline fun schemaTransaction(block: () -> Unit) {
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            block()
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    }

    private fun readProfile(): ProfileSnapshot =
        connection.prepare(
            """
            SELECT display_name, provenance, revision
            FROM profile_state
            WHERE singleton_id = 1
            """.trimIndent()
        ).use { statement ->
            check(statement.step()) { "profile singleton row missing" }
            val displayName = statement.getText(0)
            val provenance = statement.getText(1)
            val revision = statement.getLong(2)

            if (displayName.isEmpty()) {
                check(provenance.isEmpty()) {
                    "empty profile name cannot carry provenance"
                }
                ProfileSnapshot(
                    displayName = null,
                    revision = revision,
                    provenance = null,
                )
            } else {
                check(provenance == ProfileFieldProvenance.DECLARED_BY_USER.name) {
                    "unsupported profile provenance: $provenance"
                }
                ProfileSnapshot(
                    displayName = DeclaredDisplayName.of(displayName),
                    revision = revision,
                    provenance = ProfileFieldProvenance.DECLARED_BY_USER,
                )
            }
        }

    private fun readFinance(): FinanceSnapshot = synchronized(lock) {
        checkOpen()
        val revision = connection.prepare(
            "SELECT revision FROM finance_state WHERE singleton_id = 1"
        ).use { statement ->
            check(statement.step()) { "finance singleton row missing" }
            statement.getLong(0)
        }

        val accounts = buildList {
            connection.prepare(
                """
                SELECT a.account_id,
                       a.display_name,
                       a.currency_code,
                       a.nature,
                       COALESCE(o.amount_minor, 0)
                FROM finance_account a
                LEFT JOIN finance_account_opening o
                  ON o.account_id = a.account_id
                ORDER BY a.account_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(
                        FinanceAccountSnapshot(
                            accountId = FinanceAccountId.of(statement.getText(0)),
                            displayName = FinanceAccountDisplayName.of(statement.getText(1)),
                            currency = FinanceCurrencyCode.of(statement.getText(2)),
                            nature = FinanceAccountNature.valueOf(statement.getText(3)),
                            openingBalanceMinor = statement.getLong(4),
                        )
                    )
                }
            }
        }

        val entries = buildList {
            connection.prepare(
                """
                SELECT entry_id,
                       account_id,
                       amount_minor,
                       kind,
                       currency_code,
                       concept,
                       category,
                       effective_at_epoch_millis,
                       recorded_at_epoch_millis,
                       provenance_label
                FROM finance_entry
                ORDER BY effective_at_epoch_millis,
                         recorded_at_epoch_millis,
                         entry_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(
                        FinanceEntrySnapshot(
                            entryId = FinanceEntryId.of(statement.getText(0)),
                            accountId = FinanceAccountId.of(statement.getText(1)),
                            amountMinor = statement.getLong(2),
                            kind = FinanceEntryKind.valueOf(statement.getText(3)),
                            currency = FinanceCurrencyCode.of(statement.getText(4)),
                            concept = FinanceEntryConcept.of(statement.getText(5)),
                            category = FinanceEntryCategory.of(statement.getText(6)),
                            effectiveAtEpochMillis = statement.getLong(7),
                            recordedAtEpochMillis = statement.getLong(8),
                            provenanceLabel =
                                if (statement.isNull(9)) null else statement.getText(9),
                        )
                    )
                }
            }
        }

        FinanceSnapshot(
            accounts = accounts,
            revision = revision,
            entries = entries,
        )
    }

    private fun readFinanceAccountDependencyValue(
        dependencyKey: DependencyKey,
    ): DependencyStampValue? {
        val raw = dependencyKey.value
        val prefix = "finance:account:"
        if (!raw.startsWith(prefix)) return null
        val accountId = runCatching {
            FinanceAccountId.of(raw.removePrefix(prefix))
        }.getOrNull() ?: return null
        val finance = readFinance()
        val account = finance.accounts.singleOrNull { it.accountId == accountId }
            ?: return null
        return finance.accountDependencyStamp(account.accountId).observedValue
    }

    private fun createFinanceAccount(
        create: FinanceAccountCreate,
        transactionScope: RealityTransactionScope,
    ): FinanceAccountSnapshot = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)

        connection.prepare(
            """
            INSERT INTO finance_account(
                account_id,
                display_name,
                currency_code,
                nature
            ) VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, create.accountId.value)
            statement.bindText(2, create.displayName.value)
            statement.bindText(3, create.currency.value)
            statement.bindText(4, create.nature.name)
            statement.step()
        }

        if (create.openingBalanceMinor > 0L) {
            connection.prepare(
                """
                INSERT INTO finance_account_opening(
                    account_id,
                    opening_cause_id,
                    amount_minor
                ) VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, create.accountId.value)
                statement.bindText(
                    2,
                    "finance-opening:" + create.accountId.value,
                )
                statement.bindLong(3, create.openingBalanceMinor)
                statement.step()
            }
        }

        connection.execSQL(
            """
            UPDATE finance_state
            SET revision = revision + 1
            WHERE singleton_id = 1
            """.trimIndent()
        )

        FinanceAccountSnapshot(
            accountId = create.accountId,
            displayName = create.displayName,
            currency = create.currency,
            nature = create.nature,
            openingBalanceMinor = create.openingBalanceMinor,
        )
    }

    private fun recordFinanceEntry(
        record: FinanceEntryRecord,
        transactionScope: RealityTransactionScope,
    ): FinanceEntrySnapshot = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)

        val account = readFinance().accounts
            .singleOrNull { it.accountId == record.accountId }
            ?: error("Finance entry requires existing account")

        connection.prepare(
            """
            INSERT INTO finance_entry(
                entry_id,
                account_id,
                amount_minor,
                kind,
                currency_code,
                concept,
                category,
                effective_at_epoch_millis,
                recorded_at_epoch_millis,
                provenance_label
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.entryId.value)
            statement.bindText(2, record.accountId.value)
            statement.bindLong(3, record.amountMinor)
            statement.bindText(4, record.kind.name)
            statement.bindText(5, account.currency.value)
            statement.bindText(6, record.concept.value)
            statement.bindText(7, record.category.value)
            statement.bindLong(8, record.effectiveAtEpochMillis)
            statement.bindLong(9, record.recordedAtEpochMillis)
            record.provenanceLabel?.let { statement.bindText(10, it) }
                ?: statement.bindNull(10)
            statement.step()
        }

        connection.execSQL(
            """
            UPDATE finance_state
            SET revision = revision + 1
            WHERE singleton_id = 1
            """.trimIndent()
        )

        FinanceEntrySnapshot(
            entryId = record.entryId,
            accountId = record.accountId,
            amountMinor = record.amountMinor,
            kind = record.kind,
            currency = account.currency,
            concept = record.concept,
            category = record.category,
            effectiveAtEpochMillis = record.effectiveAtEpochMillis,
            recordedAtEpochMillis = record.recordedAtEpochMillis,
            provenanceLabel = record.provenanceLabel,
        )
    }

    private fun readSemanticMigrationSnapshot(): RealitySemanticMigrationData =
        synchronized(lock) {
            checkOpen()
            val profile = readProfile()
            val finance = readFinance()
            RealitySemanticMigrationData(
                declaredDisplayName = profile.displayName?.value,
                accounts =
                    finance.accounts.map { account ->
                        RealityMigrationAccount(
                            accountId = account.accountId.value,
                            displayName = account.displayName.value,
                            currencyCode = account.currency.value,
                            nature = account.nature.name,
                            openingBalanceMinor = account.openingBalanceMinor,
                        )
                    },
                entries =
                    finance.entries.map { entry ->
                        RealityMigrationEntry(
                            entryId = entry.entryId.value,
                            accountId = entry.accountId.value,
                            amountMinor = entry.amountMinor,
                            kind = entry.kind.name,
                            concept = entry.concept.value,
                            category = entry.category.value,
                            effectiveAtEpochMillis = entry.effectiveAtEpochMillis,
                            recordedAtEpochMillis = entry.recordedAtEpochMillis,
                            provenanceLabel = entry.provenanceLabel,
                        )
                    },
            )
        }

    private fun importFreshSemanticMigration(
        data: RealitySemanticMigrationData,
    ): RealitySemanticMigrationData =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(activeTransactionId == null) {
                "semantic migration cannot run inside an active Reality transaction"
            }

            val before = inspectForMaintenance()
            check(before.integrityHealthy && before.foreignKeysHealthy) {
                "Reality target must be healthy before semantic migration"
            }
            check(before.freshForSemanticMigration) {
                "Reality target must be fresh before semantic migration"
            }

            val accountById = data.accounts.associateBy { it.accountId }

            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                data.declaredDisplayName?.let { displayName ->
                    connection.prepare(
                        """
                        UPDATE profile_state
                        SET display_name = ?,
                            provenance = ?,
                            revision = 1
                        WHERE singleton_id = 1
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, displayName)
                        statement.bindText(2, ProfileFieldProvenance.DECLARED_BY_USER.name)
                        statement.step()
                    }
                }

                data.accounts.forEach { account ->
                    connection.prepare(
                        """
                        INSERT INTO finance_account(
                            account_id,
                            display_name,
                            currency_code,
                            nature
                        ) VALUES (?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, account.accountId)
                        statement.bindText(2, account.displayName)
                        statement.bindText(3, account.currencyCode)
                        statement.bindText(4, account.nature)
                        statement.step()
                    }

                    if (account.openingBalanceMinor > 0L) {
                        connection.prepare(
                            """
                            INSERT INTO finance_account_opening(
                                account_id,
                                opening_cause_id,
                                amount_minor
                            ) VALUES (?, ?, ?)
                            """.trimIndent()
                        ).use { statement ->
                            statement.bindText(1, account.accountId)
                            statement.bindText(
                                2,
                                "migration-opening:" + account.accountId,
                            )
                            statement.bindLong(3, account.openingBalanceMinor)
                            statement.step()
                        }
                    }
                }

                data.entries.forEach { entry ->
                    val account =
                        checkNotNull(accountById[entry.accountId]) {
                            "migration finance entry references missing account"
                        }
                    connection.prepare(
                        """
                        INSERT INTO finance_entry(
                            entry_id,
                            account_id,
                            amount_minor,
                            kind,
                            currency_code,
                            concept,
                            category,
                            effective_at_epoch_millis,
                            recorded_at_epoch_millis,
                            provenance_label
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, entry.entryId)
                        statement.bindText(2, entry.accountId)
                        statement.bindLong(3, entry.amountMinor)
                        statement.bindText(4, entry.kind)
                        statement.bindText(5, account.currencyCode)
                        statement.bindText(6, entry.concept)
                        statement.bindText(7, entry.category)
                        statement.bindLong(8, entry.effectiveAtEpochMillis)
                        statement.bindLong(9, entry.recordedAtEpochMillis)
                        entry.provenanceLabel
                            ?.let { statement.bindText(10, it) }
                            ?: statement.bindNull(10)
                        statement.step()
                    }
                }

                if (data.accounts.isNotEmpty() || data.entries.isNotEmpty()) {
                    connection.execSQL(
                        """
                        UPDATE finance_state
                        SET revision = 1
                        WHERE singleton_id = 1
                        """.trimIndent()
                    )
                }

                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }

            val after = inspectForMaintenance()
            check(after.integrityHealthy && after.foreignKeysHealthy) {
                "Reality target failed post-migration integrity checks"
            }
            readSemanticMigrationSnapshot()
        } }


    private fun readBackupSnapshot(): RealityBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            val profile = readProfile()
            val finance = readFinance()
            val receipts =
                buildList {
                    connection.prepare(
                        """
                        SELECT r.receipt_id,
                               r.transaction_id,
                               r.plan_fingerprint
                        FROM execution_receipt r
                        JOIN apply_attempt a
                          ON a.transaction_id = r.transaction_id
                         AND a.plan_fingerprint = r.plan_fingerprint
                        WHERE a.state = 'COMMITTED'
                        ORDER BY r.transaction_id
                        """.trimIndent()
                    ).use { statement ->
                        while (statement.step()) {
                            add(
                                RealityCommittedReceiptBackup(
                                    receiptId = statement.getText(0),
                                    transactionId = statement.getText(1),
                                    planFingerprint = statement.getText(2),
                                )
                            )
                        }
                    }
                }
            RealityBackupSnapshot(
                schemaVersion = readSchemaVersion(),
                profileRevision = profile.revision,
                financeRevision = finance.revision,
                domain = readSemanticMigrationSnapshot(),
                committedReceipts = receipts,
            )
        }

    private fun restoreOrVerifyBackup(
        snapshot: RealityBackupSnapshot,
    ): RealityBackupSnapshot =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(activeTransactionId == null) {
                "backup restore cannot run inside an active Reality transaction"
            }
            check(snapshot.schemaVersion == SCHEMA_VERSION) {
                "Reality backup schema requires explicit migration"
            }

            val current = readBackupSnapshot()
            if (current == snapshot) {
                return@synchronized current
            }

            val before = inspectForMaintenance()
            check(before.integrityHealthy && before.foreignKeysHealthy) {
                "Reality restore target must be healthy"
            }
            check(before.freshForSemanticMigration) {
                "Reality restore target must be fresh or exactly match the backup"
            }

            val accountById = snapshot.domain.accounts.associateBy { it.accountId }
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                connection.prepare(
                    """
                    UPDATE profile_state
                    SET display_name = ?,
                        provenance = ?,
                        revision = ?
                    WHERE singleton_id = 1
                    """.trimIndent()
                ).use { statement ->
                    val displayName = snapshot.domain.declaredDisplayName
                    statement.bindText(1, displayName ?: "")
                    statement.bindText(
                        2,
                        if (displayName == null) "" else ProfileFieldProvenance.DECLARED_BY_USER.name,
                    )
                    statement.bindLong(3, snapshot.profileRevision)
                    statement.step()
                }

                snapshot.domain.accounts.forEach { account ->
                    connection.prepare(
                        """
                        INSERT INTO finance_account(
                            account_id, display_name, currency_code, nature
                        ) VALUES (?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, account.accountId)
                        statement.bindText(2, account.displayName)
                        statement.bindText(3, account.currencyCode)
                        statement.bindText(4, account.nature)
                        statement.step()
                    }
                    if (account.openingBalanceMinor > 0L) {
                        connection.prepare(
                            """
                            INSERT INTO finance_account_opening(
                                account_id, opening_cause_id, amount_minor
                            ) VALUES (?, ?, ?)
                            """.trimIndent()
                        ).use { statement ->
                            statement.bindText(1, account.accountId)
                            statement.bindText(2, "backup-opening:" + account.accountId)
                            statement.bindLong(3, account.openingBalanceMinor)
                            statement.step()
                        }
                    }
                }

                snapshot.domain.entries.forEach { entry ->
                    val account =
                        checkNotNull(accountById[entry.accountId]) {
                            "backup finance entry references missing account"
                        }
                    connection.prepare(
                        """
                        INSERT INTO finance_entry(
                            entry_id, account_id, amount_minor, kind, currency_code,
                            concept, category, effective_at_epoch_millis,
                            recorded_at_epoch_millis, provenance_label
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, entry.entryId)
                        statement.bindText(2, entry.accountId)
                        statement.bindLong(3, entry.amountMinor)
                        statement.bindText(4, entry.kind)
                        statement.bindText(5, account.currencyCode)
                        statement.bindText(6, entry.concept)
                        statement.bindText(7, entry.category)
                        statement.bindLong(8, entry.effectiveAtEpochMillis)
                        statement.bindLong(9, entry.recordedAtEpochMillis)
                        entry.provenanceLabel?.let { statement.bindText(10, it) }
                            ?: statement.bindNull(10)
                        statement.step()
                    }
                }
                connection.prepare(
                    "UPDATE finance_state SET revision = ? WHERE singleton_id = 1"
                ).use { statement ->
                    statement.bindLong(1, snapshot.financeRevision)
                    statement.step()
                }

                snapshot.committedReceipts.forEach { receipt ->
                    connection.prepare(
                        """
                        INSERT INTO apply_attempt(
                            transaction_id, plan_fingerprint, state
                        ) VALUES (?, ?, 'COMMITTED')
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, receipt.transactionId)
                        statement.bindText(2, receipt.planFingerprint)
                        statement.step()
                    }
                    connection.prepare(
                        """
                        INSERT INTO execution_receipt(
                            receipt_id, transaction_id, plan_fingerprint
                        ) VALUES (?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, receipt.receiptId)
                        statement.bindText(2, receipt.transactionId)
                        statement.bindText(3, receipt.planFingerprint)
                        statement.step()
                    }
                }

                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }

            val afterInspection = inspectForMaintenance()
            check(afterInspection.integrityHealthy && afterInspection.foreignKeysHealthy) {
                "Reality restore target failed integrity checks"
            }
            check(afterInspection.outboxEffectCount == 0L) {
                "Reality restore must not recreate Class D Outbox effects"
            }
            val restored = readBackupSnapshot()
            check(restored == snapshot) {
                "Reality restore semantic verification failed"
            }
            restored
        } }

    private fun requireActiveScope(scope: RealityTransactionScope) {
        val sqliteScope = scope as? SqliteRealityTransactionScope
            ?: error("Reality write requires RealityStore transaction scope")
        check(sqliteScope.owner === this) {
            "Reality transaction scope belongs to another store"
        }
        check(activeTransactionId == sqliteScope.transactionId) {
            "Reality transaction identity mismatch"
        }
    }

    private fun checkOpen() {
        check(!closed) { "RealityStore is closed" }
    }

    private class SqliteRealityTransactionScope(
        override val transactionId: TransactionId,
        val owner: RealityStore,
    ) : RealityTransactionScope

    companion object {
        const val SCHEMA_VERSION: Int = 5
    }
}
