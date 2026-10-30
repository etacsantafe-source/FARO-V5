package ar.com.faro.v5.evidence

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
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
import java.security.MessageDigest
import java.util.UUID

@JvmInline
value class WebEvidenceId(val value: String) {
    init {
        require(value.isNotBlank()) { "web evidence id must not be blank" }
    }
}

data class WebEvidenceRecord(
    val evidenceId: WebEvidenceId,
    val idempotencyKey: IdempotencyKey,
    val payloadFingerprint: PayloadFingerprint,
    val requestedUrl: String,
    val finalUrl: String,
    val method: WebFetchMethod,
    val statusCode: Int,
    val mediaType: String?,
    val redirectCount: Int,
    val acquiredAtEpochMillis: Long,
    val contentSha256Hex: String?,
    val contentBytes: Int?,
    val policyVersion: String,
    val fetcherVersion: String,
    val trust: EvidenceContentTrust = EvidenceContentTrust.UNTRUSTED_CONTENT,
) {
    init {
        require(requestedUrl.isNotBlank() && requestedUrl.length <= WebFetchRequest.MAX_URL_CHARS)
        require(finalUrl.isNotBlank() && finalUrl.length <= WebFetchRequest.MAX_URL_CHARS)
        require(statusCode in 200..299)
        require(redirectCount in 0..WebFetchPolicy.MAX_REDIRECTS)
        require(acquiredAtEpochMillis >= 0L)
        require(
            mediaType == null ||
                (
                    mediaType == mediaType.trim().lowercase() &&
                        mediaType.length <= WebFetchPolicy.MAX_MEDIA_TYPE_CHARS
                )
        )
        require(
            contentSha256Hex == null ||
                SHA256_HEX.matches(contentSha256Hex)
        )
        require(contentBytes == null || contentBytes >= 0)
        require((contentSha256Hex == null) == (contentBytes == null))
        require(
            method != WebFetchMethod.GET ||
                (contentSha256Hex != null && contentBytes != null)
        )
        require(policyVersion.isNotBlank() && policyVersion.length <= WebFetchPolicy.MAX_VERSION_CHARS)
        require(fetcherVersion.isNotBlank() && fetcherVersion.length <= WebFetchPolicy.MAX_VERSION_CHARS)
        require(trust == EvidenceContentTrust.UNTRUSTED_CONTENT)
    }

    val bodyRetained: Boolean
        get() = false

    override fun toString(): String =
        "WebEvidenceRecord(evidenceId=" + evidenceId.value +
            ", method=" + method +
            ", statusCode=" + statusCode +
            ", mediaType=" + mediaType +
            ", redirectCount=" + redirectCount +
            ", contentBytes=" + contentBytes +
            ", bodyRetained=false, trust=" + trust + ")"

    companion object {
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

sealed interface WebEvidenceRecordOutcome {
    data class Committed(
        val record: WebEvidenceRecord,
        val replayed: Boolean,
    ) : WebEvidenceRecordOutcome

    data class RejectedIdentityConflict(
        val existing: WebEvidenceRecord,
        val idempotencyKey: IdempotencyKey,
    ) : WebEvidenceRecordOutcome
}

sealed interface WebEvidenceFetchExecution {
    data class Applied(
        val outcome: WebEvidenceRecordOutcome.Committed,
        val fetchedSource: WebFetchedSource?,
    ) : WebEvidenceFetchExecution

    data class IdentityConflict(
        val outcome: WebEvidenceRecordOutcome.RejectedIdentityConflict,
    ) : WebEvidenceFetchExecution

    data class FetchFailed(
        val failure: WebFetchResult.Failure,
    ) : WebEvidenceFetchExecution

    data object RejectedContract : WebEvidenceFetchExecution
    data object RejectedDependency : WebEvidenceFetchExecution
    data object RejectedPayload : WebEvidenceFetchExecution
}

object WebEvidenceOperations {
    val CAPABILITY_ID = CapabilityId("N-048")
    val EXECUTOR_ID = ExecutorId("web-evidence")
    val FETCH = OperationId("web.fetch")
    val SEARCH = OperationId("web.search")
}

object WebEvidenceFetchPayload {
    const val FORMAT_ID = "web.evidence.fetch.v1"
    const val MAX_PAYLOAD_BYTES = 8192

    fun payload(request: WebFetchRequest): ar.com.faro.v5.kernel.contracts.CanonicalPayload {
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(1)
                    output.writeUTF(request.url)
                    output.writeUTF(request.method.name)
                }
                buffer.toByteArray()
            }
        require(bytes.size <= MAX_PAYLOAD_BYTES) {
            "web evidence fetch payload exceeds bound"
        }
        return ar.com.faro.v5.kernel.contracts.CanonicalPayload.fromEncoded(
            FORMAT_ID,
            bytes,
        )
    }

    fun decode(
        payload: ar.com.faro.v5.kernel.contracts.CanonicalPayload,
    ): WebFetchRequest? {
        if (payload.formatId != FORMAT_ID || payload.size > MAX_PAYLOAD_BYTES) {
            return null
        }
        return runCatching {
            DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                require(input.readInt() == 1)
                val request =
                    WebFetchRequest(
                        url = input.readUTF(),
                        method = WebFetchMethod.valueOf(input.readUTF()),
                    )
                require(input.available() == 0) {
                    "web evidence fetch payload has trailing bytes"
                }
                request
            }
        }.getOrNull()
    }
}

class WebEvidenceStore(
    fileName: String,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    private val evidenceIdFactory: () -> WebEvidenceId = {
        WebEvidenceId("web-evidence-" + UUID.randomUUID())
    },
    private val materialWriteGate: MaterialWriteGate? = null,
) : AutoCloseable {
    private val lock = Any()
    /** Opt-in staged V2: the host must inject its single shared gate. */
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false

    val maintenance: WebEvidenceMaintenancePort =
        WebEvidenceMaintenancePort(::inspectForMaintenance)

    val backup: WebEvidenceBackupPort =
        object : WebEvidenceBackupPort {
            override fun snapshot(): WebEvidenceBackupSnapshot = readBackupSnapshot()

            override fun restoreOrVerify(
                snapshot: WebEvidenceBackupSnapshot,
            ): WebEvidenceBackupSnapshot = restoreOrVerifyBackup(snapshot)
        }

    val portability: WebEvidencePortabilityPort =
        WebEvidencePortabilityPort(::readPortabilitySnapshot)

    init {
        withMaterialAdmission {
            synchronized(lock) {
                configure(fileName)
                initializeSchema()
            }
        }
    }

    fun findByIdempotencyKey(
        idempotencyKey: IdempotencyKey,
    ): WebEvidenceRecord? = synchronized(lock) {
        checkOpen()
        findByIdempotencyKeyLocked(idempotencyKey)
    }

    fun record(
        source: WebFetchedSource,
        idempotencyKey: IdempotencyKey,
        payloadFingerprint: PayloadFingerprint,
    ): WebEvidenceRecordOutcome = withMaterialAdmission { synchronized(lock) {
        checkOpen()

        findByIdempotencyKeyLocked(idempotencyKey)?.let { existing ->
            return@synchronized if (existing.payloadFingerprint == payloadFingerprint) {
                WebEvidenceRecordOutcome.Committed(
                    record = existing,
                    replayed = true,
                )
            } else {
                WebEvidenceRecordOutcome.RejectedIdentityConflict(
                    existing = existing,
                    idempotencyKey = idempotencyKey,
                )
            }
        }

        val record =
            WebEvidenceRecord(
                evidenceId = evidenceIdFactory(),
                idempotencyKey = idempotencyKey,
                payloadFingerprint = payloadFingerprint,
                requestedUrl = source.requestedUrl,
                finalUrl = source.finalUrl,
                method = source.method,
                statusCode = source.statusCode,
                mediaType = source.mediaType,
                redirectCount = source.redirectCount,
                acquiredAtEpochMillis = source.acquiredAtEpochMillis,
                contentSha256Hex = source.contentSha256Hex,
                contentBytes = source.body?.size,
                policyVersion = source.policyVersion,
                fetcherVersion = source.fetcherVersion,
                trust = source.trust,
            )

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            insert(record)
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }

        WebEvidenceRecordOutcome.Committed(
            record = record,
            replayed = false,
        )
    } }

    fun findSearchByIdempotencyKey(
        idempotencyKey: IdempotencyKey,
    ): WebSearchEvidenceRecord? = synchronized(lock) {
        checkOpen()
        findSearchByIdempotencyKeyLocked(idempotencyKey)
    }

    fun recordSearch(
        request: WebSearchRequest,
        response: WebSearchResponse,
        idempotencyKey: IdempotencyKey,
        payloadFingerprint: PayloadFingerprint,
    ): WebSearchEvidenceRecordOutcome = withMaterialAdmission { synchronized(lock) {
        checkOpen()

        findSearchByIdempotencyKeyLocked(idempotencyKey)?.let { existing ->
            return@synchronized if (existing.payloadFingerprint == payloadFingerprint) {
                WebSearchEvidenceRecordOutcome.Committed(
                    record = existing,
                    replayed = true,
                )
            } else {
                WebSearchEvidenceRecordOutcome.RejectedIdentityConflict(
                    existing = existing,
                    idempotencyKey = idempotencyKey,
                )
            }
        }

        require(request.sensitivity == EgressSensitivity.PUBLIC) {
            "only public web search may be persisted"
        }
        require(response.query == request.query) {
            "web search response query must match request"
        }
        require(response.hits.size <= request.maxResults) {
            "web search response exceeds requested result bound"
        }

        val record =
            WebSearchEvidenceRecord(
                evidenceId = evidenceIdFactory(),
                idempotencyKey = idempotencyKey,
                payloadFingerprint = payloadFingerprint,
                query = request.query,
                maxResults = request.maxResults,
                sensitivity = request.sensitivity,
                providerId = response.providerId,
                providerRequestId = response.providerRequestId,
                providerUsage = response.providerUsage,
                acquiredAtEpochMillis = response.acquiredAtEpochMillis,
                policyVersion = response.policyVersion,
                adapterVersion = response.adapterVersion,
                hits = response.hits.toList(),
                trust = EvidenceContentTrust.UNTRUSTED_CONTENT,
            )
        val encodedHits = WebSearchEvidenceHitsCodec.encode(record.hits)

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            insertSearch(record, encodedHits)
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }

        WebSearchEvidenceRecordOutcome.Committed(
            record = record,
            replayed = false,
        )
    } }

    internal fun searchRecordCount(): Long = synchronized(lock) {
        checkOpen()
        connection.prepare("SELECT COUNT(*) FROM web_search_evidence").use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }

    internal fun recordCount(): Long = synchronized(lock) {
        checkOpen()
        connection.prepare("SELECT COUNT(*) FROM web_evidence").use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }

    internal fun integrityCheck(): String = synchronized(lock) {
        checkOpen()
        connection.prepare("PRAGMA integrity_check").use { statement ->
            check(statement.step())
            statement.getText(0)
        }
    }


    private fun inspectForMaintenance(): WebEvidenceMaintenanceInspection =
        synchronized(lock) {
            checkOpen()
            val schemaVersion =
                connection.prepare("PRAGMA user_version").use { statement ->
                    check(statement.step())
                    statement.getLong(0).toInt()
                }
            val integrityHealthy = integrityCheck().equals("ok", ignoreCase = true)
            val foreignKeysHealthy =
                connection.prepare("PRAGMA foreign_key_check").use { statement ->
                    !statement.step()
                }
            WebEvidenceMaintenanceInspection(
                schemaVersion = schemaVersion,
                integrityHealthy = integrityHealthy,
                foreignKeysHealthy = foreignKeysHealthy,
                fetchRecordCount = recordCount(),
                searchRecordCount = searchRecordCount(),
            )
        }

    private fun readBackupSnapshot(): WebEvidenceBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            WebEvidenceBackupSnapshot(
                schemaVersion =
                    connection.prepare("PRAGMA user_version").use { statement ->
                        check(statement.step())
                        statement.getLong(0).toInt()
                    },
                fetchRecords = readAllFetchRecords(),
                searchRecords = readAllSearchRecords(),
            )
        }

    private fun readPortabilitySnapshot(): WebEvidencePortabilitySnapshot =
        synchronized(lock) {
            checkOpen()
            val schemaVersion =
                connection.prepare("PRAGMA user_version").use { statement ->
                    check(statement.step())
                    statement.getLong(0).toInt()
                }
            EvidencePortabilityProjection.web(
                schemaVersion = schemaVersion,
                fetchRecords = readAllFetchRecords(),
                searchRecords = readAllSearchRecords(),
            )
        }

    private fun restoreOrVerifyBackup(
        snapshot: WebEvidenceBackupSnapshot,
    ): WebEvidenceBackupSnapshot =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(snapshot.schemaVersion == SCHEMA_VERSION) {
                "Web Evidence backup schema requires explicit migration"
            }
            val current = readBackupSnapshot()
            if (current == snapshot) {
                return@synchronized current
            }
            check(recordCount() == 0L && searchRecordCount() == 0L) {
                "Web Evidence restore target must be fresh or exactly match backup"
            }
            check(integrityCheck().equals("ok", ignoreCase = true)) {
                "Web Evidence restore target must be healthy"
            }

            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                snapshot.fetchRecords.forEach(::insert)
                snapshot.searchRecords.forEach { record ->
                    insertSearch(record, WebSearchEvidenceHitsCodec.encode(record.hits))
                }
                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
            check(integrityCheck().equals("ok", ignoreCase = true)) {
                "Web Evidence restore target failed integrity checks"
            }
            val restored = readBackupSnapshot()
            check(restored == snapshot) {
                "Web Evidence restore verification failed"
            }
            restored
        } }

    private fun readAllFetchRecords(): List<WebEvidenceRecord> =
        buildList {
            connection.prepare(
                """
                SELECT evidence_id,
                       idempotency_key,
                       payload_fingerprint,
                       requested_url,
                       final_url,
                       method,
                       status_code,
                       media_type,
                       redirect_count,
                       acquired_at_epoch_millis,
                       content_sha256_hex,
                       content_bytes,
                       policy_version,
                       fetcher_version,
                       trust
                FROM web_evidence
                ORDER BY evidence_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    add(statement.toRecord())
                }
            }
        }

    private fun readAllSearchRecords(): List<WebSearchEvidenceRecord> =
        buildList {
            connection.prepare(
                """
                SELECT evidence_id,
                       idempotency_key,
                       payload_fingerprint,
                       query_text,
                       max_results,
                       sensitivity,
                       provider_id,
                       provider_request_id,
                       usage_unit_id,
                       usage_amount,
                       acquired_at_epoch_millis,
                       policy_version,
                       adapter_version,
                       hits_b64,
                       trust
                FROM web_search_evidence
                ORDER BY evidence_id
                """.trimIndent()
            ).use { statement ->
                while (statement.step()) {
                    val hits =
                        WebSearchEvidenceHitsCodec.decode(statement.getText(13))
                            ?: error("corrupt web search Evidence hits")
                    val usage =
                        if (statement.isNull(8) && statement.isNull(9)) {
                            null
                        } else {
                            check(!statement.isNull(8) && !statement.isNull(9))
                            WebProviderUsage(
                                unitId = statement.getText(8),
                                amount = statement.getLong(9),
                            )
                        }
                    add(
                        WebSearchEvidenceRecord(
                            evidenceId = WebEvidenceId(statement.getText(0)),
                            idempotencyKey = IdempotencyKey(statement.getText(1)),
                            payloadFingerprint = PayloadFingerprint(statement.getText(2)),
                            query = statement.getText(3),
                            maxResults = statement.getLong(4).toInt(),
                            sensitivity = EgressSensitivity.valueOf(statement.getText(5)),
                            providerId = statement.getText(6),
                            providerRequestId =
                                if (statement.isNull(7)) null else statement.getText(7),
                            providerUsage = usage,
                            acquiredAtEpochMillis = statement.getLong(10),
                            policyVersion = statement.getText(11),
                            adapterVersion = statement.getText(12),
                            hits = hits,
                            trust = EvidenceContentTrust.valueOf(statement.getText(14)),
                        )
                    )
                }
            }
        }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        connection.close()
        closed = true
    }

    private fun insert(record: WebEvidenceRecord) {
        connection.prepare(
            """
            INSERT INTO web_evidence(
                evidence_id,
                idempotency_key,
                payload_fingerprint,
                requested_url,
                final_url,
                method,
                status_code,
                media_type,
                redirect_count,
                acquired_at_epoch_millis,
                content_sha256_hex,
                content_bytes,
                policy_version,
                fetcher_version,
                trust
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.evidenceId.value)
            statement.bindText(2, record.idempotencyKey.value)
            statement.bindText(3, record.payloadFingerprint.value)
            statement.bindText(4, record.requestedUrl)
            statement.bindText(5, record.finalUrl)
            statement.bindText(6, record.method.name)
            statement.bindLong(7, record.statusCode.toLong())
            record.mediaType?.let { statement.bindText(8, it) } ?: statement.bindNull(8)
            statement.bindLong(9, record.redirectCount.toLong())
            statement.bindLong(10, record.acquiredAtEpochMillis)
            record.contentSha256Hex?.let { statement.bindText(11, it) } ?: statement.bindNull(11)
            record.contentBytes?.let { statement.bindLong(12, it.toLong()) } ?: statement.bindNull(12)
            statement.bindText(13, record.policyVersion)
            statement.bindText(14, record.fetcherVersion)
            statement.bindText(15, record.trust.name)
            statement.step()
        }
    }

    private fun findByIdempotencyKeyLocked(
        idempotencyKey: IdempotencyKey,
    ): WebEvidenceRecord? =
        connection.prepare(
            """
            SELECT evidence_id,
                   idempotency_key,
                   payload_fingerprint,
                   requested_url,
                   final_url,
                   method,
                   status_code,
                   media_type,
                   redirect_count,
                   acquired_at_epoch_millis,
                   content_sha256_hex,
                   content_bytes,
                   policy_version,
                   fetcher_version,
                   trust
            FROM web_evidence
            WHERE idempotency_key = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, idempotencyKey.value)
            if (!statement.step()) null else statement.toRecord()
        }

    private fun androidx.sqlite.SQLiteStatement.toRecord(): WebEvidenceRecord =
        WebEvidenceRecord(
            evidenceId = WebEvidenceId(getText(0)),
            idempotencyKey = IdempotencyKey(getText(1)),
            payloadFingerprint = PayloadFingerprint(getText(2)),
            requestedUrl = getText(3),
            finalUrl = getText(4),
            method = WebFetchMethod.valueOf(getText(5)),
            statusCode = getLong(6).toInt(),
            mediaType = if (isNull(7)) null else getText(7),
            redirectCount = getLong(8).toInt(),
            acquiredAtEpochMillis = getLong(9),
            contentSha256Hex = if (isNull(10)) null else getText(10),
            contentBytes = if (isNull(11)) null else getLong(11).toInt(),
            policyVersion = getText(12),
            fetcherVersion = getText(13),
            trust = EvidenceContentTrust.valueOf(getText(14)),
        )

    private fun insertSearch(
        record: WebSearchEvidenceRecord,
        encodedHits: String,
    ) {
        connection.prepare(
            """
            INSERT INTO web_search_evidence(
                evidence_id,
                idempotency_key,
                payload_fingerprint,
                query_text,
                max_results,
                sensitivity,
                provider_id,
                provider_request_id,
                usage_unit_id,
                usage_amount,
                acquired_at_epoch_millis,
                policy_version,
                adapter_version,
                hits_b64,
                trust
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, record.evidenceId.value)
            statement.bindText(2, record.idempotencyKey.value)
            statement.bindText(3, record.payloadFingerprint.value)
            statement.bindText(4, record.query)
            statement.bindLong(5, record.maxResults.toLong())
            statement.bindText(6, record.sensitivity.name)
            statement.bindText(7, record.providerId)
            record.providerRequestId?.let { statement.bindText(8, it) } ?: statement.bindNull(8)
            record.providerUsage?.unitId?.let { statement.bindText(9, it) } ?: statement.bindNull(9)
            record.providerUsage?.amount?.let { statement.bindLong(10, it) } ?: statement.bindNull(10)
            statement.bindLong(11, record.acquiredAtEpochMillis)
            statement.bindText(12, record.policyVersion)
            statement.bindText(13, record.adapterVersion)
            statement.bindText(14, encodedHits)
            statement.bindText(15, record.trust.name)
            statement.step()
        }
    }

    private fun findSearchByIdempotencyKeyLocked(
        idempotencyKey: IdempotencyKey,
    ): WebSearchEvidenceRecord? =
        connection.prepare(
            """
            SELECT evidence_id,
                   idempotency_key,
                   payload_fingerprint,
                   query_text,
                   max_results,
                   sensitivity,
                   provider_id,
                   provider_request_id,
                   usage_unit_id,
                   usage_amount,
                   acquired_at_epoch_millis,
                   policy_version,
                   adapter_version,
                   hits_b64,
                   trust
            FROM web_search_evidence
            WHERE idempotency_key = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, idempotencyKey.value)
            if (!statement.step()) {
                null
            } else {
                val hits =
                    WebSearchEvidenceHitsCodec.decode(statement.getText(13))
                        ?: error("corrupt web search Evidence hits")
                val usage =
                    if (statement.isNull(8) && statement.isNull(9)) {
                        null
                    } else {
                        check(!statement.isNull(8) && !statement.isNull(9)) {
                            "corrupt web search Evidence usage"
                        }
                        WebProviderUsage(
                            unitId = statement.getText(8),
                            amount = statement.getLong(9),
                        )
                    }
                WebSearchEvidenceRecord(
                    evidenceId = WebEvidenceId(statement.getText(0)),
                    idempotencyKey = IdempotencyKey(statement.getText(1)),
                    payloadFingerprint = PayloadFingerprint(statement.getText(2)),
                    query = statement.getText(3),
                    maxResults = statement.getLong(4).toInt(),
                    sensitivity = EgressSensitivity.valueOf(statement.getText(5)),
                    providerId = statement.getText(6),
                    providerRequestId =
                        if (statement.isNull(7)) null else statement.getText(7),
                    providerUsage = usage,
                    acquiredAtEpochMillis = statement.getLong(10),
                    policyVersion = statement.getText(11),
                    adapterVersion = statement.getText(12),
                    hits = hits,
                    trust = EvidenceContentTrust.valueOf(statement.getText(14)),
                )
            }
        }

    private fun configure(fileName: String) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = FULL")
        if (fileName != ":memory:") {
            connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
                check(statement.step()) { "journal_mode returned no row" }
                check(statement.getText(0).equals("wal", ignoreCase = true)) {
                    "WebEvidenceStore requires WAL journal mode"
                }
            }
        }
    }

    private fun initializeSchema() {
        val version =
            connection.prepare("PRAGMA user_version").use { statement ->
                check(statement.step())
                statement.getLong(0).toInt()
            }
        check(version in 0..SCHEMA_VERSION) {
            "unsupported WebEvidenceStore schema version: $version"
        }

        when (version) {
            0 -> {
                connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
                try {
                    createFetchTable()
                    createSearchTable()
                    connection.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
                    connection.execSQL("COMMIT TRANSACTION")
                } catch (failure: Throwable) {
                    runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                    throw failure
                }
            }

            1 -> {
                connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
                try {
                    createSearchTable()
                    connection.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
                    connection.execSQL("COMMIT TRANSACTION")
                } catch (failure: Throwable) {
                    runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                    throw failure
                }
            }
        }

        checkRequiredTable("web_evidence")
        checkRequiredTable("web_search_evidence")
    }

    private fun createFetchTable() {
        connection.execSQL(
            """
            CREATE TABLE web_evidence(
                evidence_id TEXT NOT NULL PRIMARY KEY,
                idempotency_key TEXT NOT NULL UNIQUE,
                payload_fingerprint TEXT NOT NULL,
                requested_url TEXT NOT NULL,
                final_url TEXT NOT NULL,
                method TEXT NOT NULL CHECK(method IN ('GET','HEAD')),
                status_code INTEGER NOT NULL CHECK(status_code BETWEEN 200 AND 299),
                media_type TEXT,
                redirect_count INTEGER NOT NULL CHECK(redirect_count BETWEEN 0 AND 8),
                acquired_at_epoch_millis INTEGER NOT NULL CHECK(acquired_at_epoch_millis >= 0),
                content_sha256_hex TEXT,
                content_bytes INTEGER,
                policy_version TEXT NOT NULL,
                fetcher_version TEXT NOT NULL,
                trust TEXT NOT NULL CHECK(trust = 'UNTRUSTED_CONTENT'),
                CHECK(length(trim(evidence_id)) > 0),
                CHECK(length(trim(idempotency_key)) > 0),
                CHECK(length(trim(payload_fingerprint)) > 0),
                CHECK(length(requested_url) BETWEEN 1 AND 4096),
                CHECK(length(final_url) BETWEEN 1 AND 4096),
                CHECK(media_type IS NULL OR length(media_type) BETWEEN 1 AND 127),
                CHECK(length(policy_version) BETWEEN 1 AND 64),
                CHECK(length(fetcher_version) BETWEEN 1 AND 64),
                CHECK(
                    (content_sha256_hex IS NULL AND content_bytes IS NULL) OR
                    (
                        length(content_sha256_hex) = 64 AND
                        content_bytes IS NOT NULL AND
                        content_bytes >= 0
                    )
                ),
                CHECK(
                    method != 'GET' OR
                    (content_sha256_hex IS NOT NULL AND content_bytes IS NOT NULL)
                )
            )
            """.trimIndent()
        )
    }

    private fun createSearchTable() {
        connection.execSQL(
            """
            CREATE TABLE web_search_evidence(
                evidence_id TEXT NOT NULL PRIMARY KEY,
                idempotency_key TEXT NOT NULL UNIQUE,
                payload_fingerprint TEXT NOT NULL,
                query_text TEXT NOT NULL,
                max_results INTEGER NOT NULL CHECK(max_results BETWEEN 1 AND 20),
                sensitivity TEXT NOT NULL CHECK(sensitivity = 'PUBLIC'),
                provider_id TEXT NOT NULL,
                provider_request_id TEXT,
                usage_unit_id TEXT,
                usage_amount INTEGER,
                acquired_at_epoch_millis INTEGER NOT NULL CHECK(acquired_at_epoch_millis >= 0),
                policy_version TEXT NOT NULL,
                adapter_version TEXT NOT NULL,
                hits_b64 TEXT NOT NULL,
                trust TEXT NOT NULL CHECK(trust = 'UNTRUSTED_CONTENT'),
                CHECK(length(trim(evidence_id)) > 0),
                CHECK(length(trim(idempotency_key)) > 0),
                CHECK(length(trim(payload_fingerprint)) > 0),
                CHECK(length(query_text) BETWEEN 1 AND 400),
                CHECK(length(provider_id) BETWEEN 1 AND 64),
                CHECK(provider_request_id IS NULL OR length(provider_request_id) BETWEEN 1 AND 256),
                CHECK(
                    (usage_unit_id IS NULL AND usage_amount IS NULL) OR
                    (
                        usage_unit_id IS NOT NULL AND
                        usage_amount IS NOT NULL AND
                        usage_amount >= 0
                    )
                ),
                CHECK(length(policy_version) BETWEEN 1 AND 64),
                CHECK(length(adapter_version) BETWEEN 1 AND 64),
                CHECK(length(hits_b64) BETWEEN 1 AND 1398104)
            )
            """.trimIndent()
        )
    }

    private fun checkRequiredTable(tableName: String) {
        val exists =
            connection.prepare(
                """
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, tableName)
                statement.step()
            }
        check(exists) {
            "WebEvidenceStore required table missing: $tableName"
        }
    }

    private fun checkOpen() {
        check(!closed) { "WebEvidenceStore is closed" }
    }

    private companion object {
        const val SCHEMA_VERSION = 2
    }
}

class WebEvidenceFetchExecutor(
    private val store: WebEvidenceStore,
    private val fetcher: SafeHttpFetcher,
    private val policy: WebFetchPolicy,
    private val canonicalContract: OperationContractDescriptor,
) : OperationExecutionPort {
    private val lock = Any()

    init {
        require(canonicalContract.operationId == WebEvidenceOperations.FETCH)
        require(canonicalContract.capabilityId == WebEvidenceOperations.CAPABILITY_ID)
        require(canonicalContract.canonicalExecutor == WebEvidenceOperations.EXECUTOR_ID)
        require(canonicalContract.executionProfile == ExecutionProfile.OBSERVATIONAL_WRITE)
        require(canonicalContract.exposure == ContractExposure.BINDABLE)
    }

    override val descriptor =
        OperationBindingDescriptor(
            operationId = canonicalContract.operationId,
            canonicalExecutor = canonicalContract.canonicalExecutor,
            executionProfile = canonicalContract.executionProfile,
        )

    fun execute(
        preparedOperation: PreparedOperation,
        cancellationSignal: CancellationSignal,
    ): WebEvidenceFetchExecution = synchronized(lock) {
        if (preparedOperation.contract != canonicalContract) {
            return@synchronized WebEvidenceFetchExecution.RejectedContract
        }
        if (preparedOperation.dependencyStamps.asCanonicalList().isNotEmpty()) {
            return@synchronized WebEvidenceFetchExecution.RejectedDependency
        }
        val request =
            WebEvidenceFetchPayload.decode(preparedOperation.payload)
                ?: return@synchronized WebEvidenceFetchExecution.RejectedPayload

        val existing =
            store.findByIdempotencyKey(preparedOperation.idempotencyKey)
        if (existing != null) {
            return@synchronized if (
                existing.payloadFingerprint == preparedOperation.payloadFingerprint
            ) {
                WebEvidenceFetchExecution.Applied(
                    outcome =
                        WebEvidenceRecordOutcome.Committed(
                            record = existing,
                            replayed = true,
                        ),
                    fetchedSource = null,
                )
            } else {
                WebEvidenceFetchExecution.IdentityConflict(
                    WebEvidenceRecordOutcome.RejectedIdentityConflict(
                        existing = existing,
                        idempotencyKey = preparedOperation.idempotencyKey,
                    )
                )
            }
        }

        when (
            val fetchResult =
                fetcher.fetch(
                    request = request,
                    policy = policy,
                    cancellationSignal = cancellationSignal,
                )
        ) {
            is WebFetchResult.Failure ->
                WebEvidenceFetchExecution.FetchFailed(fetchResult)

            is WebFetchResult.Success -> {
                val source = fetchResult.source
                if (
                    source.requestedUrl != request.url ||
                    source.method != request.method ||
                    source.policyVersion != policy.policyVersion ||
                    source.fetcherVersion != policy.fetcherVersion ||
                    !sourceMatchesPolicy(source)
                ) {
                    return@synchronized WebEvidenceFetchExecution.FetchFailed(
                        WebFetchResult.Failure(
                            WebFetchFailureKind.INVALID_RESPONSE
                        )
                    )
                }

                when (
                    val outcome =
                        store.record(
                            source = source,
                            idempotencyKey = preparedOperation.idempotencyKey,
                            payloadFingerprint = preparedOperation.payloadFingerprint,
                        )
                ) {
                    is WebEvidenceRecordOutcome.Committed ->
                        WebEvidenceFetchExecution.Applied(
                            outcome = outcome,
                            fetchedSource = source,
                        )

                    is WebEvidenceRecordOutcome.RejectedIdentityConflict ->
                        WebEvidenceFetchExecution.IdentityConflict(outcome)
                }
            }
        }
    }

    private fun sourceMatchesPolicy(source: WebFetchedSource): Boolean {
        if (source.redirectCount > policy.maxRedirects) {
            return false
        }
        val mediaType = source.mediaType
        if (mediaType != null && mediaType !in policy.allowedMediaTypes) {
            return false
        }

        val body = source.body
        if (body == null) {
            return source.method == WebFetchMethod.HEAD &&
                source.contentSha256Hex == null
        }
        if (source.method != WebFetchMethod.GET) {
            return false
        }
        if (body.size > policy.maxDecodedBytes) {
            return false
        }
        if (mediaType == null || body.formatId != mediaType) {
            return false
        }

        val bytes = body.copyBytes()
        return try {
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xff)
                    }
            digest == source.contentSha256Hex
        } finally {
            bytes.fill(0)
        }
    }

}
