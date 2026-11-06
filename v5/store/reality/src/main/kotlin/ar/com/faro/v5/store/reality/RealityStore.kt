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
import ar.com.faro.v5.owner.objectives.api.ObjectiveDetail
import ar.com.faro.v5.owner.objectives.api.ObjectiveId
import ar.com.faro.v5.owner.objectives.api.ObjectiveRecord
import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot
import ar.com.faro.v5.owner.objectives.api.ObjectiveStatus
import ar.com.faro.v5.owner.objectives.api.ObjectiveTitle
import ar.com.faro.v5.owner.objectives.spi.ObjectivePersistencePort
import ar.com.faro.v5.owner.matters.api.MatterDetail
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterRecord
import ar.com.faro.v5.owner.matters.api.MatterSnapshot
import ar.com.faro.v5.owner.matters.api.MatterStatus
import ar.com.faro.v5.owner.matters.api.MatterTitle
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    private val receiptClockEpochMillis: () -> Long = System::currentTimeMillis,
    /** Explicit quarantined STAGED schema8 only; default schema7 remains untouched. */
    private val planningStage8: Boolean = false,
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

    /**
     * N-015 foundation: canonical Reality receipts only, never a chronological
     * cross-owner activity view. Read-only even when the MaterialWriteGate is closed.
     */
    val receiptHistory: RealityReceiptHistoryReadPort =
        RealityReceiptHistoryReadPort(::queryCommittedReceipts)

    /** Reality-only dated projection. No global history or Home ACL admission. */
    val timedReceiptHistory: RealityTimedReceiptHistoryReadPort =
        RealityTimedReceiptHistoryReadPort(::queryTimedReceipts)

    /** N015 owner feed: finance ledger dates, not globally completed Apply. */
    val financeActivity: RealityFinanceActivityReadPort =
        RealityFinanceActivityReadPort(::queryFinanceActivity)

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


    /** Real owner ports bound to the existing single Reality transaction gate. */
    val objectivePersistence: ObjectivePersistencePort =
        object : ObjectivePersistencePort {
            override fun read(): ObjectiveSnapshot = readObjectives()
            override fun upsert(
                record: ObjectiveRecord,
                transactionScope: RealityTransactionScope,
            ): ObjectiveRecord = upsertObjective(record, transactionScope)
        }

    val matterPersistence: MatterPersistencePort =
        object : MatterPersistencePort {
            override fun read(): MatterSnapshot = readMatters()
            override fun upsert(
                record: MatterRecord,
                transactionScope: RealityTransactionScope,
            ): MatterRecord = upsertMatter(record, transactionScope)
        }

    /** Internal-only snapshot; no export/restore contract yet. */
    val domainBackup: RealityDomainBackupReadPort =
        RealityDomainBackupReadPort {
            synchronized(lock) {
                checkOpen()
                RealityDomainBackupSnapshot(
                    schemaVersion = SCHEMA_VERSION,
                    objectives = readObjectives(),
                    matters = readMatters(),
                )
            }
        }

    /** Atomic paired read for FBS2 only; never use it to export FBS1. */
    val fbs2Backup: RealityFbs2BackupReadPort = RealityFbs2BackupReadPort {
        synchronized(lock) {
            checkOpen()
            check(activeTransactionId == null) {
                "FBS2 Reality snapshot cannot read an active write transaction"
            }
            RealityFbs2BackupSnapshot(
                legacy = readBackupSnapshot(allowG4 = true),
                g4Domains = domainBackup.snapshot(),
                planningDomains = if (planningStage8) readPlanningSnapshot() else null,
            )
        }
    }

    /** Private G4 restore primitive; external FBS2 binding is not implemented. */
    val domainBackupRestore: RealityDomainBackupRestorePort =
        RealityDomainBackupRestorePort(::restoreOrVerifyG4DomainBackup)

    /** Only available in isolated schema8 STAGED mode; NEVER a user CRUD API. */
    val planningBackup: RealityPlanningBackupReadPort =
        RealityPlanningBackupReadPort { synchronized(lock) {
            check(planningStage8) { "Planning Reality owner not mounted" }
            readPlanningSnapshot()
        } }

    /** Stage-only, authenticated caller must pin archive before this port is used. */
    val planningBackupRestore: RealityPlanningBackupRestorePort =
        RealityPlanningBackupRestorePort(::restoreOrVerifyPlanningStage8)

    /**
     * N032 strictly internal staging seam. Does not mint Apply receipts or
     * authorize Chat/Android operations. Serialized under the Reality mutex.
     */
    internal val planningStageInsert: RealityPlanningStageInsertPort =
        RealityPlanningStageInsertPort(::insertPlanningStage8)

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
                if (planningStage8) initializePlanningStage8() else initializeSchema()
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

        val receiptRecordedAt = receiptClockEpochMillis()
        require(receiptRecordedAt >= 0L) { "receipt clock must be nonnegative" }
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
                plan_fingerprint,
                commit_recorded_at_epoch_millis
            ) VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, receipt.receiptId.value)
            statement.bindText(2, receipt.transactionId.value)
            statement.bindText(3, receipt.planFingerprint.value)
            statement.bindLong(4, receiptRecordedAt)
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
                objectiveCount = readRowCount("objective_record"),
                matterCount = readRowCount("matter_record"),
                objectiveRevision = readObjectives().revision,
                matterRevision = readMatters().revision,
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
                    "objective_record",
                    "matter_record",
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

    /**
     * A committed Reality receipt is authoritative only when the paired Apply
     * attempt is COMMITTED. SQLite's same-connection lock supplies a coherent
     * page; pagination is lexical by transaction ID, NOT by commit time.
     * The current durable schema stores no commit timestamp.
     */
    private fun queryCommittedReceipts(
        query: RealityReceiptHistoryQuery,
    ): RealityReceiptHistoryPage = synchronized(lock) {
        checkOpen()
        // A same-thread observer must not expose a receipt that COMMIT can still
        // roll back. Other threads already wait for the Reality store lock.
        check(activeTransactionId == null) {
            "receipt history is unavailable during an uncommitted Reality transaction"
        }
        val rows = mutableListOf<ExecutionReceipt>()
        connection.prepare(
            """
            SELECT r.receipt_id, r.transaction_id, r.plan_fingerprint
            FROM execution_receipt r
            JOIN apply_attempt a
              ON a.transaction_id = r.transaction_id
             AND a.plan_fingerprint = r.plan_fingerprint
            WHERE a.state = 'COMMITTED'
              AND (? IS NULL OR r.transaction_id > ?)
            ORDER BY r.transaction_id ASC
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            val cursor = query.afterTransactionId
            if (cursor == null) {
                statement.bindNull(1)
                statement.bindNull(2)
            } else {
                statement.bindText(1, cursor)
                statement.bindText(2, cursor)
            }
            statement.bindLong(3, query.limit.toLong() + 1L)
            while (statement.step()) {
                rows += ExecutionReceipt(
                    receiptId = ReceiptId(statement.getText(0)),
                    transactionId = TransactionId(statement.getText(1)),
                    planFingerprint = PlanFingerprint(statement.getText(2)),
                )
            }
        }
        val page = rows.take(query.limit)
        RealityReceiptHistoryPage(
            receipts = page,
            nextCursor = if (rows.size > query.limit) page.last().transactionId.value else null,
        )
    }

    /** Receipt times are observed within the same COMMITTED Reality transaction. */
    private fun queryTimedReceipts(
        query: RealityTimedReceiptQuery,
    ): RealityTimedReceiptPage = synchronized(lock) {
        checkOpen()
        check(activeTransactionId == null) {
            "timed receipt history is unavailable inside an uncommitted transaction"
        }
        val legacyUndated = connection.prepare(
            """
            SELECT 1 FROM execution_receipt r
            JOIN apply_attempt a ON a.transaction_id = r.transaction_id
                AND a.plan_fingerprint = r.plan_fingerprint
            WHERE a.state = 'COMMITTED' AND r.commit_recorded_at_epoch_millis IS NULL
            LIMIT 1
            """.trimIndent()
        ).use { it.step() }
        val rows = mutableListOf<RealityTimedReceipt>()
        connection.prepare(
            """
            SELECT r.receipt_id, r.transaction_id, r.plan_fingerprint,
                   r.commit_recorded_at_epoch_millis
            FROM execution_receipt r
            JOIN apply_attempt a ON a.transaction_id = r.transaction_id
                AND a.plan_fingerprint = r.plan_fingerprint
            WHERE a.state = 'COMMITTED'
              AND r.commit_recorded_at_epoch_millis IS NOT NULL
              AND (? IS NULL OR r.commit_recorded_at_epoch_millis < ?
                   OR (r.commit_recorded_at_epoch_millis = ? AND r.transaction_id > ?))
            ORDER BY r.commit_recorded_at_epoch_millis DESC, r.transaction_id ASC
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            val cursor = query.before
            if (cursor == null) {
                for (index in 1..4) statement.bindNull(index)
            } else {
                statement.bindLong(1, cursor.recordedAtEpochMillis)
                statement.bindLong(2, cursor.recordedAtEpochMillis)
                statement.bindLong(3, cursor.recordedAtEpochMillis)
                statement.bindText(4, cursor.transactionId)
            }
            statement.bindLong(5, query.limit.toLong() + 1L)
            while (statement.step()) {
                rows += RealityTimedReceipt(
                    receipt = ExecutionReceipt(
                        receiptId = ReceiptId(statement.getText(0)),
                        transactionId = TransactionId(statement.getText(1)),
                        planFingerprint = PlanFingerprint(statement.getText(2)),
                    ),
                    recordedAtEpochMillis = statement.getLong(3),
                )
            }
        }
        val page = rows.take(query.limit)
        val last = page.lastOrNull()
        RealityTimedReceiptPage(
            receipts = page,
            nextCursor = if (rows.size > query.limit && last != null)
                RealityTimedReceiptCursor(last.recordedAtEpochMillis, last.receipt.transactionId.value)
                else null,
            legacyUndated = legacyUndated,
        )
    }

    private fun queryFinanceActivity(
        query: RealityFinanceActivityQuery,
    ): RealityFinanceActivityPage = synchronized(lock) {
        checkOpen()
        check(activeTransactionId == null) {
            "finance activity cannot expose uncommitted Reality transaction content"
        }
        val owner = readFinance()
        val ordered = owner.entries.asSequence()
            .filter { query.accountId == null || it.accountId.value == query.accountId }
            .filter { query.kind == null || it.kind == query.kind }
            .filter { item ->
                val cursor = query.after
                cursor == null ||
                    item.recordedAtEpochMillis < cursor.recordedAtEpochMillis ||
                    (item.recordedAtEpochMillis == cursor.recordedAtEpochMillis &&
                        item.entryId.value > cursor.entryId)
            }
            .sortedWith(
                compareByDescending<FinanceEntrySnapshot> { it.recordedAtEpochMillis }
                    .thenBy { it.entryId.value }
            )
            .take(query.limit + 1)
            .toList()
        val selected = ordered.take(query.limit)
        val tail = selected.lastOrNull()
        RealityFinanceActivityPage(
            entries = selected,
            nextCursor =
                if (ordered.size > query.limit && tail != null) {
                    RealityFinanceActivityCursor(
                        recordedAtEpochMillis = tail.recordedAtEpochMillis,
                        entryId = tail.entryId.value,
                    )
                } else null,
            financeRevision = owner.revision,
        )
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
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            1 -> schemaTransaction {
                createApplyRecoverySchema()
                createApplyCommitSchema()
                createFinanceSchema()
                createFinanceEntrySchema()
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            2 -> schemaTransaction {
                createApplyCommitSchema()
                createFinanceSchema()
                createFinanceEntrySchema()
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            3 -> schemaTransaction {
                createFinanceSchema()
                createFinanceEntrySchema()
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            4 -> schemaTransaction {
                createFinanceEntrySchema()
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            5 -> schemaTransaction {
                createDomainSchemas()
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }

            6 -> schemaTransaction {
                ensureReceiptTimeColumn()
                setSchemaVersion(SCHEMA_VERSION)
            }
            SCHEMA_VERSION -> Unit
        }

        validateRequiredSchema()
    }

    /**
     * Stage-only opt-in. From historical 5/6/7 migrate to 7 with the existing
     * tested semantics first, then add 8. A crash after the first transaction
     * leaves valid schema7 and the explicit staged opener may resume.
     * Ordinary RealityStore remains schema7 and rejects schema8.
     */
    private fun initializePlanningStage8() {
        val observed = readSchemaVersion()
        check(observed in 0..PLANNING_SCHEMA_VERSION) {
            "unknown Reality schema for isolated Planning stage"
        }
        if (observed != PLANNING_SCHEMA_VERSION) {
            initializeSchema()
            schemaTransaction {
                createPlanningStage8Tables()
                setSchemaVersion(PLANNING_SCHEMA_VERSION)
            }
        }
        validatePlanningStage8Tables()
    }

    private fun createPlanningStage8Tables() {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS reminder_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id=1),
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0)
            )
        """.trimIndent())
        connection.execSQL("INSERT OR IGNORE INTO reminder_state VALUES (1,0)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS reminder_rule (
                rule_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(rule_id)) BETWEEN 1 AND 128),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),
                zone_id TEXT NOT NULL CHECK(length(zone_id) BETWEEN 1 AND 128),
                anchor_local_date TEXT NOT NULL,
                anchor_local_time TEXT NOT NULL,
                recurrence TEXT NOT NULL CHECK(recurrence IN ('ONCE','DAILY','WEEKLY','MONTHLY')),
                enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
                linked_matter_id TEXT,
                updated_at_epoch_millis INTEGER NOT NULL CHECK(updated_at_epoch_millis>=0),
                FOREIGN KEY(linked_matter_id) REFERENCES matter_record(matter_id) ON DELETE RESTRICT
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS reminder_occurrence (
                occurrence_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(occurrence_id)) BETWEEN 1 AND 128),
                rule_id TEXT NOT NULL,
                due_at_epoch_millis INTEGER NOT NULL CHECK(due_at_epoch_millis>=0),
                status TEXT NOT NULL CHECK(status IN ('PENDING','ACKNOWLEDGED','CANCELLED')),
                acknowledged_at_epoch_millis INTEGER CHECK(acknowledged_at_epoch_millis IS NULL OR acknowledged_at_epoch_millis>=0),
                updated_at_epoch_millis INTEGER NOT NULL CHECK(updated_at_epoch_millis>=0),
                CHECK((status='ACKNOWLEDGED')=(acknowledged_at_epoch_millis IS NOT NULL)),
                FOREIGN KEY(rule_id) REFERENCES reminder_rule(rule_id) ON DELETE RESTRICT
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS monthly_plan_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id=1),
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0)
            )
        """.trimIndent())
        connection.execSQL("INSERT OR IGNORE INTO monthly_plan_state VALUES (1,0)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS monthly_plan_item (
                item_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(item_id)) BETWEEN 1 AND 128),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),
                period TEXT NOT NULL,
                due_local_date TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('PLANNED','PAID_CONFIRMED','CANCELLED')),
                expected_amount_minor INTEGER CHECK(expected_amount_minor IS NULL OR expected_amount_minor>=0),
                currency_code TEXT CHECK(currency_code IS NULL OR currency_code GLOB '[A-Z][A-Z][A-Z]'),
                payment_confirmed_at_epoch_millis INTEGER CHECK(payment_confirmed_at_epoch_millis IS NULL OR payment_confirmed_at_epoch_millis>=0),
                updated_at_epoch_millis INTEGER NOT NULL CHECK(updated_at_epoch_millis>=0),
                CHECK((expected_amount_minor IS NULL)=(currency_code IS NULL)),
                CHECK((status='PAID_CONFIRMED')=(payment_confirmed_at_epoch_millis IS NOT NULL))
            )
        """.trimIndent())
    }

    private fun validatePlanningStage8Tables() {
        check(readSchemaVersion() == PLANNING_SCHEMA_VERSION) {
            "isolated Reality Planning schema must be exactly 8"
        }
        check(receiptTimeColumnExists()) { "historical commit-time column missing" }
        listOf("profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
            "finance_state", "finance_account", "finance_account_opening", "finance_entry",
            "objective_state", "objective_record", "matter_state", "matter_record",
            "reminder_state", "reminder_rule", "reminder_occurrence",
            "monthly_plan_state", "monthly_plan_item"
        ).forEach { table ->
            check(tableExists(table)) { "Reality8 staged table missing: $table" }
        }
        check(readIntegrityCheck() == "ok" && readForeignKeysHealthy()) {
            "isolated Reality8 stage integrity or FK invalid"
        }
        readPlanningSnapshot().requireRestorable()
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


    private fun createDomainSchemas() {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS objective_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)
            )
            """.trimIndent()
        )
        connection.execSQL("INSERT OR IGNORE INTO objective_state VALUES (1, 0)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS objective_record (
                objective_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(objective_id)) BETWEEN 1 AND 128),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),
                detail TEXT NOT NULL CHECK(length(detail) <= 4096),
                status TEXT NOT NULL CHECK(status IN ('PLANNED','ACTIVE','PAUSED','COMPLETED','ARCHIVED')),
                updated_at_epoch_millis INTEGER NOT NULL CHECK(updated_at_epoch_millis >= 0)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS matter_state (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)
            )
            """.trimIndent()
        )
        connection.execSQL("INSERT OR IGNORE INTO matter_state VALUES (1, 0)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS matter_record (
                matter_id TEXT NOT NULL PRIMARY KEY CHECK(length(trim(matter_id)) BETWEEN 1 AND 128),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),
                detail TEXT NOT NULL CHECK(length(detail) <= 4096),
                status TEXT NOT NULL CHECK(status IN ('OPEN','IN_PROGRESS','WAITING','RESOLVED','ARCHIVED')),
                due_at_epoch_millis INTEGER CHECK(due_at_epoch_millis IS NULL OR due_at_epoch_millis >= 0),
                updated_at_epoch_millis INTEGER NOT NULL CHECK(updated_at_epoch_millis >= 0)
            )
            """.trimIndent()
        )
    }

    /** Existing rows remain NULL: no historical commit time can be recovered from IDs. */
    private fun ensureReceiptTimeColumn() {
        check(tableExists("execution_receipt")) { "receipt history schema missing" }
        if (!receiptTimeColumnExists()) {
            connection.execSQL(
                "ALTER TABLE execution_receipt ADD COLUMN " +
                    "commit_recorded_at_epoch_millis INTEGER " +
                    "CHECK(commit_recorded_at_epoch_millis IS NULL OR commit_recorded_at_epoch_millis >= 0)"
            )
        }
    }

    private fun receiptTimeColumnExists(): Boolean =
        connection.prepare("PRAGMA table_info(execution_receipt)").use { statement ->
            var found = false
            while (statement.step()) {
                if (statement.getText(1) == "commit_recorded_at_epoch_millis") found = true
            }
            found
        }

    private fun validateRequiredSchema() {
        check(readSchemaVersion() == SCHEMA_VERSION) {
            "RealityStore schema version mismatch"
        }
        check(receiptTimeColumnExists()) { "Reality receipt time column missing" }
        listOf(
            "profile_state",
            "apply_attempt",
            "execution_receipt",
            "reality_outbox",
            "finance_state",
            "finance_account",
            "finance_account_opening",
            "finance_entry",
            "objective_state",
            "objective_record",
            "matter_state",
            "matter_record",
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


    private fun readObjectives(): ObjectiveSnapshot = synchronized(lock) {
        checkOpen()
        val revision = connection.prepare(
            "SELECT revision FROM objective_state WHERE singleton_id = 1"
        ).use { statement ->
            check(statement.step()) { "objective state missing" }
            statement.getLong(0)
        }
        val records = buildList {
            connection.prepare(
                """
                SELECT objective_id, title, detail, status, updated_at_epoch_millis
                FROM objective_record ORDER BY objective_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(ObjectiveRecord(
                        id = ObjectiveId.of(statement.getText(0)),
                        title = ObjectiveTitle.of(statement.getText(1)),
                        detail = ObjectiveDetail.of(statement.getText(2)),
                        status = ObjectiveStatus.valueOf(statement.getText(3)),
                        updatedAtEpochMillis = statement.getLong(4),
                    ))
                }
            }
        }
        ObjectiveSnapshot(revision, records)
    }

    private fun readMatters(): MatterSnapshot = synchronized(lock) {
        checkOpen()
        val revision = connection.prepare(
            "SELECT revision FROM matter_state WHERE singleton_id = 1"
        ).use { statement ->
            check(statement.step()) { "matter state missing" }
            statement.getLong(0)
        }
        val records = buildList {
            connection.prepare(
                """
                SELECT matter_id, title, detail, status, due_at_epoch_millis,
                       updated_at_epoch_millis
                FROM matter_record ORDER BY matter_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(MatterRecord(
                        id = MatterId.of(statement.getText(0)),
                        title = MatterTitle.of(statement.getText(1)),
                        detail = MatterDetail.of(statement.getText(2)),
                        status = MatterStatus.valueOf(statement.getText(3)),
                        dueAtEpochMillis = if (statement.isNull(4)) null else statement.getLong(4),
                        updatedAtEpochMillis = statement.getLong(5),
                    ))
                }
            }
        }
        MatterSnapshot(revision, records)
    }

    private fun upsertObjective(
        record: ObjectiveRecord,
        transactionScope: RealityTransactionScope,
    ): ObjectiveRecord = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)
        val existing = readObjectives().objectives.firstOrNull { it.id == record.id }
        if (existing == record) return@synchronized record
        if (existing != null) {
            require(record.updatedAtEpochMillis > existing.updatedAtEpochMillis) {
                "objective update timestamp must advance"
            }
        }
        connection.prepare(
            """
            INSERT INTO objective_record(
                objective_id, title, detail, status, updated_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(objective_id) DO UPDATE SET
                title=excluded.title, detail=excluded.detail,
                status=excluded.status,
                updated_at_epoch_millis=excluded.updated_at_epoch_millis
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.id.value)
            statement.bindText(2, record.title.value)
            statement.bindText(3, record.detail.value)
            statement.bindText(4, record.status.name)
            statement.bindLong(5, record.updatedAtEpochMillis)
            statement.step()
        }
        connection.execSQL(
            "UPDATE objective_state SET revision = revision + 1 WHERE singleton_id = 1"
        )
        record
    }

    private fun upsertMatter(
        record: MatterRecord,
        transactionScope: RealityTransactionScope,
    ): MatterRecord = synchronized(lock) {
        checkOpen()
        requireActiveScope(transactionScope)
        val existing = readMatters().matters.firstOrNull { it.id == record.id }
        if (existing == record) return@synchronized record
        if (existing != null) {
            require(record.updatedAtEpochMillis > existing.updatedAtEpochMillis) {
                "matter update timestamp must advance"
            }
        }
        connection.prepare(
            """
            INSERT INTO matter_record(
                matter_id, title, detail, status, due_at_epoch_millis,
                updated_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(matter_id) DO UPDATE SET
                title=excluded.title, detail=excluded.detail,
                status=excluded.status,
                due_at_epoch_millis=excluded.due_at_epoch_millis,
                updated_at_epoch_millis=excluded.updated_at_epoch_millis
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.id.value)
            statement.bindText(2, record.title.value)
            statement.bindText(3, record.detail.value)
            statement.bindText(4, record.status.name)
            record.dueAtEpochMillis?.let { statement.bindLong(5, it) }
                ?: statement.bindNull(5)
            statement.bindLong(6, record.updatedAtEpochMillis)
            statement.step()
        }
        connection.execSQL(
            "UPDATE matter_state SET revision = revision + 1 WHERE singleton_id = 1"
        )
        record
    }

    /** Atomic, idempotent internal restore of the two G4 Reality domains.
     * The caller must authenticate and pin the staged archive before invoking.
     * This method deliberately does not modify the old FBS1 wire protocol.
     */
    private fun restoreOrVerifyG4DomainBackup(
        backup: RealityDomainBackupSnapshot,
    ): RealityDomainBackupSnapshot = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        check(activeTransactionId == null) {
            "G4 domain restore cannot run in an active Reality transaction"
        }
        check(backup.schemaVersion == SCHEMA_VERSION) { "G4 schema version mismatch" }
        check(backup.objectives.objectives.size <= 100_000 &&
            backup.matters.matters.size <= 100_000
        ) { "G4 backup has too many records" }
        check(backup.objectives.revision >= backup.objectives.objectives.size.toLong() &&
            backup.matters.revision >= backup.matters.matters.size.toLong()
        ) { "G4 owner revision predates record count" }
        check(isCanonicalG4Order(backup.objectives.objectives.map { it.id.value }) &&
            isCanonicalG4Order(backup.matters.matters.map { it.id.value })
        ) { "G4 record order not canonical" }

        val before = inspectForMaintenance()
        check(before.integrityHealthy && before.foreignKeysHealthy) {
            "unhealthy Reality restore target"
        }
        val existing = domainBackup.snapshot()
        if (existing == backup) return@synchronized existing
        check(before.objectiveCount == 0L && before.matterCount == 0L &&
            before.objectiveRevision == 0L && before.matterRevision == 0L
        ) { "nonempty G4 target cannot be replaced" }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            for (record in backup.objectives.objectives) {
                connection.prepare(
                    """
                    INSERT INTO objective_record(
                        objective_id, title, detail, status, updated_at_epoch_millis
                    ) VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, record.id.value)
                    statement.bindText(2, record.title.value)
                    statement.bindText(3, record.detail.value)
                    statement.bindText(4, record.status.name)
                    statement.bindLong(5, record.updatedAtEpochMillis)
                    statement.step()
                }
            }
            for (record in backup.matters.matters) {
                connection.prepare(
                    """
                    INSERT INTO matter_record(
                        matter_id, title, detail, status, due_at_epoch_millis,
                        updated_at_epoch_millis
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, record.id.value)
                    statement.bindText(2, record.title.value)
                    statement.bindText(3, record.detail.value)
                    statement.bindText(4, record.status.name)
                    record.dueAtEpochMillis?.let { statement.bindLong(5, it) }
                        ?: statement.bindNull(5)
                    statement.bindLong(6, record.updatedAtEpochMillis)
                    statement.step()
                }
            }
            connection.prepare(
                "UPDATE objective_state SET revision = ? WHERE singleton_id = 1"
            ).use { statement ->
                statement.bindLong(1, backup.objectives.revision)
                statement.step()
            }
            connection.prepare(
                "UPDATE matter_state SET revision = ? WHERE singleton_id = 1"
            ).use { statement ->
                statement.bindLong(1, backup.matters.revision)
                statement.step()
            }
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
        val after = inspectForMaintenance()
        check(after.integrityHealthy && after.foreignKeysHealthy &&
            domainBackup.snapshot() == backup
        ) { "G4 domain exact restore verification failed" }
        domainBackup.snapshot()
    } }

    /** Direct SQL snapshot under the SAME Reality mutex as legacy and G4. */
    private fun readPlanningSnapshot(): RealityPlanningBackupSnapshot = synchronized(lock) {
        checkOpen()
        check(planningStage8 && readSchemaVersion() == PLANNING_SCHEMA_VERSION) {
            "Planning snapshot only available in isolated Reality8 stage"
        }
        fun revision(table: String): Long =
            connection.prepare("SELECT revision FROM $table WHERE singleton_id=1").use { st ->
                check(st.step()) { "planning owner revision row missing" }
                st.getLong(0)
            }
        val rules = buildList {
            connection.prepare("""
                SELECT rule_id,title,zone_id,anchor_local_date,anchor_local_time,
                    recurrence,enabled,linked_matter_id,updated_at_epoch_millis
                FROM reminder_rule ORDER BY rule_id COLLATE BINARY
            """.trimIndent()).use { st ->
                while (st.step()) {
                    val enabled = st.getLong(6)
                    check(enabled in 0L..1L)
                    add(ReminderRule(
                        ReminderRuleId.of(st.getText(0)), st.getText(1), st.getText(2),
                        LocalDate.parse(st.getText(3)), LocalTime.parse(st.getText(4)),
                        ReminderRecurrence.valueOf(st.getText(5)), enabled == 1L,
                        if (st.isNull(7)) null else st.getText(7), st.getLong(8),
                    ))
                }
            }
        }
        val occurrences = buildList {
            connection.prepare("""
                SELECT occurrence_id,rule_id,due_at_epoch_millis,status,
                    acknowledged_at_epoch_millis,updated_at_epoch_millis
                FROM reminder_occurrence ORDER BY occurrence_id COLLATE BINARY
            """.trimIndent()).use { st ->
                while (st.step()) {
                    add(ReminderOccurrence(
                        ReminderOccurrenceId.of(st.getText(0)), ReminderRuleId.of(st.getText(1)),
                        st.getLong(2), ReminderOccurrenceStatus.valueOf(st.getText(3)),
                        if (st.isNull(4)) null else st.getLong(4), st.getLong(5),
                    ))
                }
            }
        }
        val items = buildList {
            connection.prepare("""
                SELECT item_id,title,period,due_local_date,status,expected_amount_minor,
                    currency_code,payment_confirmed_at_epoch_millis,updated_at_epoch_millis
                FROM monthly_plan_item ORDER BY item_id COLLATE BINARY
            """.trimIndent()).use { st ->
                while (st.step()) {
                    add(MonthlyPlanItem(
                        MonthlyPlanItemId.of(st.getText(0)), st.getText(1),
                        YearMonth.parse(st.getText(2)), LocalDate.parse(st.getText(3)),
                        MonthlyPlanItemStatus.valueOf(st.getText(4)),
                        if (st.isNull(5)) null else st.getLong(5),
                        if (st.isNull(6)) null else st.getText(6),
                        if (st.isNull(7)) null else st.getLong(7), st.getLong(8),
                    ))
                }
            }
        }
        RealityPlanningBackupSnapshot(
            PLANNING_SCHEMA_VERSION,
            ReminderSnapshot(revision("reminder_state"), rules, occurrences),
            MonthlyPlanSnapshot(revision("monthly_plan_state"), items),
        )
    }

    /**
     * STAGED-ONLY atomic create. This does NOT substitute for AtomicApply:
     * no user-facing actor, receipt, Review, or catalog operation is exposed.
     * BEGIN IMMEDIATE precedes all revision reads; complete PLN1 size and
     * FK validation happens before COMMIT, with whole-transaction rollback.
     */
    private fun insertPlanningStage8(
        command: RealityPlanningStageInsert,
    ): RealityPlanningBackupSnapshot = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        check(planningStage8 && readSchemaVersion() == PLANNING_SCHEMA_VERSION) {
            "Planning insert requires an explicit isolated schema8 stage"
        }
        check(activeTransactionId == null) {
            "Planning insert cannot nest inside AtomicApply"
        }
        require(command.expectedOwnerRevision in 0 until Long.MAX_VALUE) {
            "Planning owner CAS revision cannot be negative or overflow"
        }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val before = readPlanningSnapshot()
            val stateTable = when (command) {
                is RealityPlanningStageInsert.Rule -> {
                    check(before.reminders.revision == command.expectedOwnerRevision) {
                        "stale Reminder owner revision"
                    }
                    check(before.reminders.rules.none { it.id == command.record.id }) {
                        "duplicate Reminder rule ID"
                    }
                    val rule = command.record
                    connection.prepare("""
                        INSERT INTO reminder_rule(
                            rule_id,title,zone_id,anchor_local_date,anchor_local_time,
                            recurrence,enabled,linked_matter_id,updated_at_epoch_millis
                        ) VALUES (?,?,?,?,?,?,?,?,?)
                    """.trimIndent()).use { st ->
                        st.bindText(1, rule.id.value)
                        st.bindText(2, rule.title)
                        st.bindText(3, rule.zoneId)
                        st.bindText(4, rule.anchorLocalDate.toString())
                        st.bindText(5, rule.anchorLocalTime.toString())
                        st.bindText(6, rule.recurrence.name)
                        st.bindLong(7, if (rule.enabled) 1L else 0L)
                        rule.linkedMatterId?.let { st.bindText(8, it) } ?: st.bindNull(8)
                        st.bindLong(9, rule.updatedAtEpochMillis)
                        st.step()
                    }
                    "reminder_state"
                }
                is RealityPlanningStageInsert.Occurrence -> {
                    check(before.reminders.revision == command.expectedOwnerRevision) {
                        "stale Reminder owner revision"
                    }
                    check(before.reminders.occurrences.none { it.id == command.record.id }) {
                        "duplicate Reminder occurrence ID"
                    }
                    check(before.reminders.rules.any { it.id == command.record.ruleId }) {
                        "Reminder occurrence requires a canonical source rule"
                    }
                    val occurrence = command.record
                    connection.prepare("""
                        INSERT INTO reminder_occurrence(
                            occurrence_id,rule_id,due_at_epoch_millis,status,
                            acknowledged_at_epoch_millis,updated_at_epoch_millis
                        ) VALUES (?,?,?,?,?,?)
                    """.trimIndent()).use { st ->
                        st.bindText(1, occurrence.id.value)
                        st.bindText(2, occurrence.ruleId.value)
                        st.bindLong(3, occurrence.dueAtEpochMillis)
                        st.bindText(4, occurrence.status.name)
                        occurrence.acknowledgedAtEpochMillis?.let {
                            st.bindLong(5, it)
                        } ?: st.bindNull(5)
                        st.bindLong(6, occurrence.updatedAtEpochMillis)
                        st.step()
                    }
                    "reminder_state"
                }
                is RealityPlanningStageInsert.MonthlyItem -> {
                    check(before.monthlyPlan.revision == command.expectedOwnerRevision) {
                        "stale MonthlyPlan owner revision"
                    }
                    check(before.monthlyPlan.items.none { it.id == command.record.id }) {
                        "duplicate MonthlyPlan item ID"
                    }
                    val item = command.record
                    connection.prepare("""
                        INSERT INTO monthly_plan_item(
                            item_id,title,period,due_local_date,status,
                            expected_amount_minor,currency_code,
                            payment_confirmed_at_epoch_millis,updated_at_epoch_millis
                        ) VALUES (?,?,?,?,?,?,?,?,?)
                    """.trimIndent()).use { st ->
                        st.bindText(1, item.id.value)
                        st.bindText(2, item.title)
                        st.bindText(3, item.period.toString())
                        st.bindText(4, item.dueDate.toString())
                        st.bindText(5, item.status.name)
                        item.expectedAmountMinor?.let { st.bindLong(6, it) } ?: st.bindNull(6)
                        item.currencyCode?.let { st.bindText(7, it) } ?: st.bindNull(7)
                        item.paymentConfirmedAtEpochMillis?.let {
                            st.bindLong(8, it)
                        } ?: st.bindNull(8)
                        st.bindLong(9, item.updatedAtEpochMillis)
                        st.step()
                    }
                    "monthly_plan_state"
                }
            }
            // Only the two closed, hard-coded table names selected above.
            connection.prepare(
                "UPDATE $stateTable SET revision=revision+1 " +
                    "WHERE singleton_id=1 AND revision=?"
            ).use { st ->
                st.bindLong(1, command.expectedOwnerRevision)
                st.step()
            }
            connection.prepare("SELECT changes()").use { st ->
                check(st.step() && st.getLong(0) == 1L) {
                    "Planning CAS update missed the expected owner revision"
                }
            }
            // Read AFTER SQL but BEFORE COMMIT. Constructor validates full
            // canonical PLN1 budget and all owner rows; failure ROLLBACKs.
            val after = readPlanningSnapshot()
            check(readForeignKeysHealthy()) { "Planning stage has broken foreign keys" }
            check(
                if (stateTable == "reminder_state") {
                    after.reminders.revision == before.reminders.revision + 1 &&
                        after.monthlyPlan == before.monthlyPlan
                } else {
                    after.monthlyPlan.revision == before.monthlyPlan.revision + 1 &&
                        after.reminders == before.reminders
                }
            ) { "Planning write changed another owner or missed revision" }
            connection.execSQL("COMMIT TRANSACTION")
            after
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    } }

    /** Private stage-only port, always invoked AFTER an authenticated archive pin. */
    private fun restoreOrVerifyPlanningStage8(
        backup: RealityPlanningBackupSnapshot,
    ): RealityPlanningBackupSnapshot = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        check(planningStage8 && readSchemaVersion() == PLANNING_SCHEMA_VERSION) {
            "Planning restore requires isolated Reality8 stage"
        }
        check(activeTransactionId == null) { "Planning stage restore inside active Apply" }
        backup.requireRestorable() // wire size, row limits, revisions, FK intent, order
        val existing = readPlanningSnapshot()
        if (existing == backup) return@synchronized existing
        check(existing.isFresh) { "foreign or nonfresh Planning owner cannot be overwritten" }
        val before = inspectForMaintenance()
        check(before.integrityHealthy && before.foreignKeysHealthy) {
            "unhealthy Reality staged target"
        }
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            for (rule in backup.reminders.rules) {
                connection.prepare("""
                    INSERT INTO reminder_rule(rule_id,title,zone_id,anchor_local_date,
                        anchor_local_time,recurrence,enabled,linked_matter_id,updated_at_epoch_millis)
                    VALUES(?,?,?,?,?,?,?,?,?)
                """.trimIndent()).use { st ->
                    st.bindText(1,rule.id.value)
                    st.bindText(2,rule.title)
                    st.bindText(3,rule.zoneId)
                    st.bindText(4,rule.anchorLocalDate.toString())
                    st.bindText(5,rule.anchorLocalTime.toString())
                    st.bindText(6,rule.recurrence.name)
                    st.bindLong(7,if(rule.enabled) 1L else 0L)
                    rule.linkedMatterId?.let { st.bindText(8,it) } ?: st.bindNull(8)
                    st.bindLong(9,rule.updatedAtEpochMillis)
                    st.step()
                }
            }
            for (occ in backup.reminders.occurrences) {
                connection.prepare("""
                    INSERT INTO reminder_occurrence(occurrence_id,rule_id,due_at_epoch_millis,
                        status,acknowledged_at_epoch_millis,updated_at_epoch_millis)
                    VALUES(?,?,?,?,?,?)
                """.trimIndent()).use { st ->
                    st.bindText(1,occ.id.value)
                    st.bindText(2,occ.ruleId.value)
                    st.bindLong(3,occ.dueAtEpochMillis)
                    st.bindText(4,occ.status.name)
                    occ.acknowledgedAtEpochMillis?.let { st.bindLong(5,it) } ?: st.bindNull(5)
                    st.bindLong(6,occ.updatedAtEpochMillis)
                    st.step()
                }
            }
            for (item in backup.monthlyPlan.items) {
                connection.prepare("""
                    INSERT INTO monthly_plan_item(item_id,title,period,due_local_date,status,
                        expected_amount_minor,currency_code,payment_confirmed_at_epoch_millis,
                        updated_at_epoch_millis)
                    VALUES(?,?,?,?,?,?,?,?,?)
                """.trimIndent()).use { st ->
                    st.bindText(1,item.id.value)
                    st.bindText(2,item.title)
                    st.bindText(3,item.period.toString())
                    st.bindText(4,item.dueDate.toString())
                    st.bindText(5,item.status.name)
                    item.expectedAmountMinor?.let { st.bindLong(6,it) } ?: st.bindNull(6)
                    item.currencyCode?.let { st.bindText(7,it) } ?: st.bindNull(7)
                    item.paymentConfirmedAtEpochMillis?.let { st.bindLong(8,it) } ?: st.bindNull(8)
                    st.bindLong(9,item.updatedAtEpochMillis)
                    st.step()
                }
            }
            connection.prepare("UPDATE reminder_state SET revision=? WHERE singleton_id=1").use {
                it.bindLong(1,backup.reminders.revision)
                it.step()
            }
            connection.prepare("UPDATE monthly_plan_state SET revision=? WHERE singleton_id=1").use {
                it.bindLong(1,backup.monthlyPlan.revision)
                it.step()
            }
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
        check(readIntegrityCheck()=="ok" && readForeignKeysHealthy() &&
            readPlanningSnapshot()==backup) { "Planning stage restore failed exact verification" }
        readPlanningSnapshot()
    } }

    /** SQLite BINARY comparison is unsigned UTF-8, not UTF-16 String ordering. */
    private fun isCanonicalG4Order(ids: List<String>): Boolean =
        ids.zipWithNext().all { (left, right) ->
            val a = left.toByteArray(Charsets.UTF_8)
            val b = right.toByteArray(Charsets.UTF_8)
            val n = minOf(a.size, b.size)
            var i = 0
            while (i < n && a[i] == b[i]) i++
            if (i == n) a.size < b.size
            else (a[i].toInt() and 255) < (b[i].toInt() and 255)
        }

    /** The unchanged FBS1 format CANNOT carry Objective/Matter data. */
    private fun requireLegacyDomainEmpty() {
        if (planningStage8) {
            check(readPlanningSnapshot().isFresh) {
                "FBS1/legacy Reality backup cannot represent Planning rows"
            }
        }
        val inspection = inspectForMaintenance()
        check(inspection.objectiveCount == 0L && inspection.matterCount == 0L &&
            inspection.objectiveRevision == 0L && inspection.matterRevision == 0L) {
            "legacy Reality/FBS1 backup cannot represent G4 rows"
        }
    }

    private fun readSemanticMigrationSnapshot(
        allowG4: Boolean = false,
    ): RealitySemanticMigrationData =
        synchronized(lock) {
            checkOpen()
            if (!allowG4) requireLegacyDomainEmpty()
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


    private fun readBackupSnapshot(
        allowG4: Boolean = false,
    ): RealityBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            if (!allowG4) requireLegacyDomainEmpty()
            val profile = readProfile()
            val finance = readFinance()
            val receipts =
                buildList {
                    connection.prepare(
                        """
                        SELECT r.receipt_id,
                               r.transaction_id,
                               r.plan_fingerprint,
                               r.commit_recorded_at_epoch_millis
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
                                    commitRecordedAtEpochMillis = if (statement.isNull(3)) null else statement.getLong(3),
                                )
                            )
                        }
                    }
                }
            RealityBackupSnapshot(
                schemaVersion = LEGACY_BACKUP_SCHEMA_VERSION,
                profileRevision = profile.revision,
                financeRevision = finance.revision,
                domain = readSemanticMigrationSnapshot(allowG4 = allowG4),
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
            check(snapshot.schemaVersion == LEGACY_BACKUP_SCHEMA_VERSION) {
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
                            receipt_id, transaction_id, plan_fingerprint,
                            commit_recorded_at_epoch_millis
                        ) VALUES (?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, receipt.receiptId)
                        statement.bindText(2, receipt.transactionId)
                        statement.bindText(3, receipt.planFingerprint)
                        receipt.commitRecordedAtEpochMillis?.let { statement.bindLong(4, it) }
                            ?: statement.bindNull(4)
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
        const val SCHEMA_VERSION: Int = 7
        const val PLANNING_SCHEMA_VERSION: Int = 8
        const val LEGACY_BACKUP_SCHEMA_VERSION: Int = 5
    }
}
