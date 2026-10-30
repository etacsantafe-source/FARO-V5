package ar.com.faro.v5.evidence

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

@JvmInline
value class CommercialProductKey(val value: String) {
    init {
        require(value.isNotBlank()) { "commercial product key must not be blank" }
        require(value == value.trim()) { "commercial product key must be canonical" }
        require(value.length <= 160) { "commercial product key is too long" }
    }
}

@JvmInline
value class CommercialMerchant(val value: String) {
    init {
        require(value.isNotBlank()) { "commercial merchant must not be blank" }
        require(value == value.trim()) { "commercial merchant must be canonical" }
        require(value.length <= 160) { "commercial merchant is too long" }
    }
}

@JvmInline
value class CommercialCurrencyCode(val value: String) {
    init {
        require(value.matches(Regex("[A-Z]{3}"))) {
            "commercial currency code must be exactly three uppercase ASCII letters"
        }
    }
}

@JvmInline
value class CommercialEvidenceId(val value: String) {
    init {
        require(value.isNotBlank()) { "commercial evidence id must not be blank" }
    }
}

enum class CommercialEvidenceSourceKind {
    MANUAL,
    TOOL,
    IMPORT,
}

data class CommercialPriceObservation(
    val productKey: CommercialProductKey,
    val merchant: CommercialMerchant,
    val currencyCode: CommercialCurrencyCode,
    val amountMinor: Long,
    val sourceKind: CommercialEvidenceSourceKind,
    val sourceRef: String,
    val observedAtEpochMillis: Long?,
) {
    init {
        require(amountMinor > 0L) { "observed commercial price must be positive" }
        require(sourceRef.isNotBlank()) { "commercial evidence source ref must not be blank" }
        require(sourceRef.length <= 1024) { "commercial evidence source ref is too long" }
        require(observedAtEpochMillis == null || observedAtEpochMillis >= 0L) {
            "commercial observed time must be non-negative"
        }
    }
}

data class CommercialEvidenceRecord(
    val evidenceId: CommercialEvidenceId,
    val idempotencyKey: IdempotencyKey,
    val payloadFingerprint: PayloadFingerprint,
    val observation: CommercialPriceObservation,
    val acquiredAtEpochMillis: Long,
) {
    init {
        require(acquiredAtEpochMillis >= 0L) {
            "commercial evidence acquisition time must be non-negative"
        }
    }
}

sealed interface CommercialEvidenceRecordOutcome {
    data class Committed(
        val record: CommercialEvidenceRecord,
        val replayed: Boolean,
    ) : CommercialEvidenceRecordOutcome

    data class RejectedIdentityConflict(
        val existing: CommercialEvidenceRecord,
        val idempotencyKey: IdempotencyKey,
    ) : CommercialEvidenceRecordOutcome
}

sealed interface CommercialEvidenceExecution {
    data class Applied(
        val outcome: CommercialEvidenceRecordOutcome,
    ) : CommercialEvidenceExecution

    data object RejectedContract : CommercialEvidenceExecution
    data object RejectedDependency : CommercialEvidenceExecution
    data object RejectedPayload : CommercialEvidenceExecution
}

object CommercialEvidenceOperations {
    val CAPABILITY_ID = CapabilityId("N-034")
    val EXECUTOR_ID = ExecutorId("commercial-evidence")
    val RECORD = OperationId("commercial.evidence.record")
    val PRICE_HISTORY_READ = OperationId("commercial.price_history.read")
}

object CommercialEvidencePayload {
    const val FORMAT_ID = "commercial.evidence.record.v1"
    const val MAX_PAYLOAD_BYTES = 4096

    fun payload(
        observation: CommercialPriceObservation,
    ): ar.com.faro.v5.kernel.contracts.CanonicalPayload {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(1)
                output.writeUTF(observation.productKey.value)
                output.writeUTF(observation.merchant.value)
                output.writeUTF(observation.currencyCode.value)
                output.writeLong(observation.amountMinor)
                output.writeUTF(observation.sourceKind.name)
                output.writeUTF(observation.sourceRef)
                output.writeBoolean(observation.observedAtEpochMillis != null)
                observation.observedAtEpochMillis?.let(output::writeLong)
            }
            buffer.toByteArray()
        }
        require(bytes.size <= MAX_PAYLOAD_BYTES) {
            "commercial evidence payload exceeds bound"
        }
        return ar.com.faro.v5.kernel.contracts.CanonicalPayload.fromEncoded(
            FORMAT_ID,
            bytes,
        )
    }

    fun decode(
        payload: ar.com.faro.v5.kernel.contracts.CanonicalPayload,
    ): CommercialPriceObservation? {
        if (payload.formatId != FORMAT_ID || payload.size > MAX_PAYLOAD_BYTES) {
            return null
        }
        return runCatching {
            DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                require(input.readInt() == 1)
                val observation = CommercialPriceObservation(
                    productKey = CommercialProductKey(input.readUTF()),
                    merchant = CommercialMerchant(input.readUTF()),
                    currencyCode = CommercialCurrencyCode(input.readUTF()),
                    amountMinor = input.readLong(),
                    sourceKind = CommercialEvidenceSourceKind.valueOf(input.readUTF()),
                    sourceRef = input.readUTF(),
                    observedAtEpochMillis =
                        if (input.readBoolean()) input.readLong() else null,
                )
                require(input.available() == 0) {
                    "commercial evidence payload has trailing bytes"
                }
                observation
            }
        }.getOrNull()
    }
}

class CommercialEvidenceStore(
    fileName: String,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val evidenceIdFactory: () -> CommercialEvidenceId = {
        CommercialEvidenceId("evidence-" + UUID.randomUUID())
    },
    private val materialWriteGate: MaterialWriteGate? = null,
) : AutoCloseable {
    private val lock = Any()
    /** Opt-in staged V2: the host must inject its single shared gate. */
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false

    val maintenance: CommercialEvidenceMaintenancePort =
        CommercialEvidenceMaintenancePort(::inspectForMaintenance)

    val semanticMigration: CommercialEvidenceSemanticMigrationPort =
        object : CommercialEvidenceSemanticMigrationPort {
            override fun snapshot(): List<CommercialEvidenceMigrationRecord> =
                readSemanticMigrationSnapshot()

            override fun importFresh(
                records: List<CommercialEvidenceMigrationRecord>,
            ): List<CommercialEvidenceMigrationRecord> =
                importFreshSemanticMigration(records)
        }

    val backup: CommercialEvidenceBackupPort =
        object : CommercialEvidenceBackupPort {
            override fun snapshot(): CommercialEvidenceBackupSnapshot =
                readBackupSnapshot()

            override fun restoreOrVerify(
                snapshot: CommercialEvidenceBackupSnapshot,
            ): CommercialEvidenceBackupSnapshot = restoreOrVerifyBackup(snapshot)
        }

    val portability: CommercialEvidencePortabilityPort =
        CommercialEvidencePortabilityPort(::readPortabilitySnapshot)

    init {
        withMaterialAdmission {
            synchronized(lock) {
                configure(fileName)
                initializeSchema()
            }
        }
    }

    fun record(
        observation: CommercialPriceObservation,
        idempotencyKey: IdempotencyKey,
        payloadFingerprint: PayloadFingerprint,
    ): CommercialEvidenceRecordOutcome = withMaterialAdmission { synchronized(lock) {
        checkOpen()

        findByIdempotencyKey(idempotencyKey)?.let { existing ->
            return@synchronized if (existing.payloadFingerprint == payloadFingerprint) {
                CommercialEvidenceRecordOutcome.Committed(
                    record = existing,
                    replayed = true,
                )
            } else {
                CommercialEvidenceRecordOutcome.RejectedIdentityConflict(
                    existing = existing,
                    idempotencyKey = idempotencyKey,
                )
            }
        }

        val acquiredAt = clock()
        require(acquiredAt >= 0L) {
            "commercial evidence acquisition clock returned a negative value"
        }
        val record = CommercialEvidenceRecord(
            evidenceId = evidenceIdFactory(),
            idempotencyKey = idempotencyKey,
            payloadFingerprint = payloadFingerprint,
            observation = observation,
            acquiredAtEpochMillis = acquiredAt,
        )

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            insert(record)
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }

        CommercialEvidenceRecordOutcome.Committed(
            record = record,
            replayed = false,
        )
    } }

    fun priceHistory(
        productKey: CommercialProductKey,
        limit: Int = 50,
    ): List<CommercialEvidenceRecord> = synchronized(lock) {
        checkOpen()
        require(limit in 1..200) { "commercial price history limit must be 1..200" }

        buildList {
            connection.prepare(
                """
                SELECT evidence_id,
                       idempotency_key,
                       payload_fingerprint,
                       product_key,
                       merchant,
                       currency_code,
                       amount_minor,
                       source_kind,
                       source_ref,
                       observed_at_epoch_millis,
                       acquired_at_epoch_millis
                FROM commercial_evidence
                WHERE product_key = ?
                ORDER BY COALESCE(
                    observed_at_epoch_millis,
                    acquired_at_epoch_millis
                ) DESC,
                acquired_at_epoch_millis DESC,
                evidence_id DESC
                LIMIT ?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, productKey.value)
                statement.bindLong(2, limit.toLong())
                while (statement.step()) {
                    add(statement.toRecord())
                }
            }
        }
    }

    internal fun recordCount(): Long = synchronized(lock) {
        checkOpen()
        connection.prepare(
            "SELECT COUNT(*) FROM commercial_evidence"
        ).use { statement ->
            check(statement.step()) { "commercial evidence count returned no row" }
            statement.getLong(0)
        }
    }

    internal fun integrityCheck(): String = synchronized(lock) {
        checkOpen()
        readIntegrityCheck()
    }

    private fun inspectForMaintenance(): CommercialEvidenceMaintenanceInspection =
        synchronized(lock) {
            checkOpen()
            CommercialEvidenceMaintenanceInspection(
                schemaVersion = readSchemaVersion(),
                integrityHealthy = readIntegrityCheck().equals("ok", ignoreCase = true),
                foreignKeysHealthy = readForeignKeysHealthy(),
                recordCount = readRecordCount(),
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

    private fun readRecordCount(): Long =
        connection.prepare("SELECT COUNT(*) FROM commercial_evidence").use { statement ->
            check(statement.step()) { "commercial evidence count returned no row" }
            statement.getLong(0)
        }

    private fun readAllRecords(): List<CommercialEvidenceRecord> =
        buildList {
            connection.prepare(
                """
                SELECT evidence_id,
                       idempotency_key,
                       payload_fingerprint,
                       product_key,
                       merchant,
                       currency_code,
                       amount_minor,
                       source_kind,
                       source_ref,
                       observed_at_epoch_millis,
                       acquired_at_epoch_millis
                FROM commercial_evidence
                ORDER BY evidence_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(statement.toRecord())
                }
            }
        }

    private fun readSemanticMigrationSnapshot(): List<CommercialEvidenceMigrationRecord> =
        synchronized(lock) {
            checkOpen()
            readAllRecords().map(CommercialEvidenceMigrationIdentity::fromStored)
        }

    private fun importFreshSemanticMigration(
        records: List<CommercialEvidenceMigrationRecord>,
    ): List<CommercialEvidenceMigrationRecord> =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            require(
                records.map { it.sourceIdentityFingerprint }.distinct().size == records.size
            ) {
                "commercial semantic migration cannot contain duplicate source identities"
            }
            val before = inspectForMaintenance()
            check(before.integrityHealthy && before.foreignKeysHealthy) {
                "Commercial Evidence target must be healthy before semantic migration"
            }
            check(before.freshForSemanticMigration) {
                "Commercial Evidence target must be fresh before semantic migration"
            }

            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                records.forEach { source ->
                    insert(CommercialEvidenceMigrationIdentity.toStored(source))
                }
                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }

            val after = inspectForMaintenance()
            check(after.integrityHealthy && after.foreignKeysHealthy) {
                "Commercial Evidence target failed post-migration integrity checks"
            }
            readSemanticMigrationSnapshot()
        } }


    private fun readBackupSnapshot(): CommercialEvidenceBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            CommercialEvidenceBackupSnapshot(
                schemaVersion = readSchemaVersion(),
                records = readAllRecords(),
            )
        }

    private fun readPortabilitySnapshot(): CommercialEvidencePortabilitySnapshot =
        synchronized(lock) {
            checkOpen()
            EvidencePortabilityProjection.commercial(
                schemaVersion = readSchemaVersion(),
                records = readAllRecords(),
            )
        }

    private fun restoreOrVerifyBackup(
        snapshot: CommercialEvidenceBackupSnapshot,
    ): CommercialEvidenceBackupSnapshot =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(snapshot.schemaVersion == SCHEMA_VERSION) {
                "Commercial Evidence backup schema requires explicit migration"
            }
            val current = readBackupSnapshot()
            if (current == snapshot) {
                return@synchronized current
            }
            val before = inspectForMaintenance()
            check(before.integrityHealthy && before.foreignKeysHealthy) {
                "Commercial Evidence restore target must be healthy"
            }
            check(before.recordCount == 0L) {
                "Commercial Evidence restore target must be fresh or exactly match backup"
            }

            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                snapshot.records.forEach(::insert)
                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
            val after = inspectForMaintenance()
            check(after.integrityHealthy && after.foreignKeysHealthy) {
                "Commercial Evidence restore target failed integrity checks"
            }
            val restored = readBackupSnapshot()
            check(restored == snapshot) {
                "Commercial Evidence restore verification failed"
            }
            restored
        } }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        connection.close()
        closed = true
    }

    private fun insert(record: CommercialEvidenceRecord) {
        connection.prepare(
            """
            INSERT INTO commercial_evidence(
                evidence_id,
                idempotency_key,
                payload_fingerprint,
                product_key,
                merchant,
                currency_code,
                amount_minor,
                source_kind,
                source_ref,
                observed_at_epoch_millis,
                acquired_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.evidenceId.value)
            statement.bindText(2, record.idempotencyKey.value)
            statement.bindText(3, record.payloadFingerprint.value)
            statement.bindText(4, record.observation.productKey.value)
            statement.bindText(5, record.observation.merchant.value)
            statement.bindText(6, record.observation.currencyCode.value)
            statement.bindLong(7, record.observation.amountMinor)
            statement.bindText(8, record.observation.sourceKind.name)
            statement.bindText(9, record.observation.sourceRef)
            record.observation.observedAtEpochMillis
                ?.let { statement.bindLong(10, it) }
                ?: statement.bindNull(10)
            statement.bindLong(11, record.acquiredAtEpochMillis)
            statement.step()
        }
    }

    private fun findByIdempotencyKey(
        idempotencyKey: IdempotencyKey,
    ): CommercialEvidenceRecord? =
        connection.prepare(
            """
            SELECT evidence_id,
                   idempotency_key,
                   payload_fingerprint,
                   product_key,
                   merchant,
                   currency_code,
                   amount_minor,
                   source_kind,
                   source_ref,
                   observed_at_epoch_millis,
                   acquired_at_epoch_millis
            FROM commercial_evidence
            WHERE idempotency_key = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, idempotencyKey.value)
            if (!statement.step()) null else statement.toRecord()
        }

    private fun androidx.sqlite.SQLiteStatement.toRecord(): CommercialEvidenceRecord =
        CommercialEvidenceRecord(
            evidenceId = CommercialEvidenceId(getText(0)),
            idempotencyKey = IdempotencyKey(getText(1)),
            payloadFingerprint = PayloadFingerprint(getText(2)),
            observation = CommercialPriceObservation(
                productKey = CommercialProductKey(getText(3)),
                merchant = CommercialMerchant(getText(4)),
                currencyCode = CommercialCurrencyCode(getText(5)),
                amountMinor = getLong(6),
                sourceKind = CommercialEvidenceSourceKind.valueOf(getText(7)),
                sourceRef = getText(8),
                observedAtEpochMillis = if (isNull(9)) null else getLong(9),
            ),
            acquiredAtEpochMillis = getLong(10),
        )

    private fun configure(fileName: String) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = FULL")
        if (fileName != ":memory:") {
            connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
                check(statement.step()) { "journal_mode returned no row" }
                check(statement.getText(0).equals("wal", ignoreCase = true)) {
                    "CommercialEvidenceStore requires WAL journal mode"
                }
            }
        }
    }

    private fun initializeSchema() {
        val version = readSchemaVersion()
        check(version in 0..SCHEMA_VERSION) {
            "unsupported CommercialEvidenceStore schema version: $version"
        }
        if (version == 0) {
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                connection.execSQL(
                    """
                    CREATE TABLE commercial_evidence(
                        evidence_id TEXT NOT NULL PRIMARY KEY,
                        idempotency_key TEXT NOT NULL UNIQUE,
                        payload_fingerprint TEXT NOT NULL,
                        product_key TEXT NOT NULL,
                        merchant TEXT NOT NULL,
                        currency_code TEXT NOT NULL
                            CHECK(currency_code GLOB '[A-Z][A-Z][A-Z]'),
                        amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
                        source_kind TEXT NOT NULL
                            CHECK(source_kind IN ('MANUAL', 'TOOL', 'IMPORT')),
                        source_ref TEXT NOT NULL,
                        observed_at_epoch_millis INTEGER,
                        acquired_at_epoch_millis INTEGER NOT NULL
                            CHECK(acquired_at_epoch_millis >= 0),
                        CHECK(length(trim(evidence_id)) > 0),
                        CHECK(length(trim(idempotency_key)) > 0),
                        CHECK(length(trim(payload_fingerprint)) > 0),
                        CHECK(length(trim(product_key)) BETWEEN 1 AND 160),
                        CHECK(length(trim(merchant)) BETWEEN 1 AND 160),
                        CHECK(length(source_ref) BETWEEN 1 AND 1024),
                        CHECK(
                            observed_at_epoch_millis IS NULL OR
                            observed_at_epoch_millis >= 0
                        )
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE INDEX commercial_evidence_product_history
                    ON commercial_evidence(
                        product_key,
                        observed_at_epoch_millis,
                        acquired_at_epoch_millis
                    )
                    """.trimIndent()
                )
                connection.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        }

        check(
            connection.prepare(
                """
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table' AND name = 'commercial_evidence'
                """.trimIndent()
            ).use { it.step() }
        ) {
            "CommercialEvidenceStore required table missing"
        }
    }

    private fun checkOpen() {
        check(!closed) { "CommercialEvidenceStore is closed" }
    }

    private fun readSchemaVersion(): Int =
        connection.prepare("PRAGMA user_version").use { statement ->
            check(statement.step()) { "user_version returned no row" }
            statement.getLong(0).toInt()
        }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

class CommercialEvidenceRecordExecutor(
    private val store: CommercialEvidenceStore,
    private val canonicalContract: OperationContractDescriptor,
) : OperationExecutionPort {
    init {
        requireCanonicalContract(
            contract = canonicalContract,
            expectedOperation = CommercialEvidenceOperations.RECORD,
            expectedProfile = ExecutionProfile.OBSERVATIONAL_WRITE,
        )
    }

    override val descriptor = OperationBindingDescriptor(
        operationId = canonicalContract.operationId,
        canonicalExecutor = canonicalContract.canonicalExecutor,
        executionProfile = canonicalContract.executionProfile,
    )

    fun execute(
        preparedOperation: PreparedOperation,
    ): CommercialEvidenceExecution {
        if (preparedOperation.contract != canonicalContract) {
            return CommercialEvidenceExecution.RejectedContract
        }
        if (preparedOperation.dependencyStamps.asCanonicalList().isNotEmpty()) {
            return CommercialEvidenceExecution.RejectedDependency
        }
        val observation = CommercialEvidencePayload.decode(preparedOperation.payload)
            ?: return CommercialEvidenceExecution.RejectedPayload

        return CommercialEvidenceExecution.Applied(
            store.record(
                observation = observation,
                idempotencyKey = preparedOperation.idempotencyKey,
                payloadFingerprint = preparedOperation.payloadFingerprint,
            )
        )
    }
}

class CommercialPriceHistoryReadExecutor(
    private val store: CommercialEvidenceStore,
    canonicalContract: OperationContractDescriptor,
) : OperationExecutionPort {
    init {
        requireCanonicalContract(
            contract = canonicalContract,
            expectedOperation = CommercialEvidenceOperations.PRICE_HISTORY_READ,
            expectedProfile = ExecutionProfile.READ_ONLY,
        )
    }

    override val descriptor = OperationBindingDescriptor(
        operationId = canonicalContract.operationId,
        canonicalExecutor = canonicalContract.canonicalExecutor,
        executionProfile = canonicalContract.executionProfile,
    )

    fun execute(
        productKey: CommercialProductKey,
        limit: Int = 50,
    ): List<CommercialEvidenceRecord> =
        store.priceHistory(productKey, limit)
}

private fun requireCanonicalContract(
    contract: OperationContractDescriptor,
    expectedOperation: OperationId,
    expectedProfile: ExecutionProfile,
) {
    require(contract.operationId == expectedOperation) {
        "commercial evidence executor received wrong operation contract"
    }
    require(contract.capabilityId == CommercialEvidenceOperations.CAPABILITY_ID) {
        "commercial evidence executor received wrong capability"
    }
    require(contract.canonicalExecutor == CommercialEvidenceOperations.EXECUTOR_ID) {
        "commercial evidence executor received wrong canonical executor"
    }
    require(contract.executionProfile == expectedProfile) {
        "commercial evidence executor received wrong execution profile"
    }
    require(contract.exposure == ContractExposure.BINDABLE) {
        "commercial evidence slice requires bindable frozen contract"
    }
}
