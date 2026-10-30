package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

enum class BackupExternalWriteStatus {
    SUCCESS,
    CANCELLED,
    FAILED,
    EFFECT_UNKNOWN,
}

enum class BackupExternalAttemptPhase {
    AWAITING_DESTINATION,
    WRITE_STARTED,
    TERMINAL,
}

data class BackupExternalEffectReceipt(
    val effectIdentity: String,
    val artifactHandleHex: String,
    val byteCount: Long,
    val sha256Hex: String,
    val status: BackupExternalWriteStatus,
    val reason: String?,
) {
    init {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        require(artifactHandleHex.matches(Regex("[0-9a-f]{64}")))
        require(byteCount in 1..PortableBackupArchiveCrypto.MAX_ARCHIVE_BYTES.toLong())
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
        when (status) {
            BackupExternalWriteStatus.SUCCESS -> require(reason == null)
            else -> require(reason != null && reason.isNotBlank() && reason.length <= 256)
        }
    }
}

data class BackupExternalAttemptRecord(
    val effectIdentity: String,
    val artifactHandleHex: String,
    val byteCount: Long,
    val sha256Hex: String,
    val phase: BackupExternalAttemptPhase,
    val terminalStatus: BackupExternalWriteStatus?,
    val reason: String?,
) {
    init {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        require(artifactHandleHex.matches(Regex("[0-9a-f]{64}")))
        require(byteCount in 1..PortableBackupArchiveCrypto.MAX_ARCHIVE_BYTES.toLong())
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
        require((phase == BackupExternalAttemptPhase.TERMINAL) == (terminalStatus != null))
        require(reason == null || (reason.isNotBlank() && reason.length <= 256))
        if (phase != BackupExternalAttemptPhase.TERMINAL) {
            require(reason == null)
        }
        if (terminalStatus == BackupExternalWriteStatus.SUCCESS) {
            require(reason == null)
        } else if (terminalStatus != null) {
            require(reason != null)
        }
    }

    fun matches(source: PreparedBackupExternalWriteSource): Boolean =
        effectIdentity == source.effectIdentity &&
            artifactHandleHex == source.artifactHandleHex &&
            byteCount == source.byteCount &&
            sha256Hex == source.sha256Hex
}

interface BackupExternalAttemptStore {
    fun read(effectIdentity: String): BackupExternalAttemptRecord?

    fun create(record: BackupExternalAttemptRecord)

    fun compareAndSet(
        expected: BackupExternalAttemptRecord,
        updated: BackupExternalAttemptRecord,
    ): Boolean

    fun pending(): List<BackupExternalAttemptRecord>
}

class FileBackupExternalAttemptStore(
    root: Path,
) : BackupExternalAttemptStore {
    private val lock = Any()
    private val directory =
        root.toAbsolutePath().normalize()
            .resolve("backup")
            .resolve("external-attempts")
    private val records = DurableRecordDirectory(directory)

    override fun read(effectIdentity: String): BackupExternalAttemptRecord? =
        synchronized(lock) {
            readPersisted(effectIdentity)?.record
        }

    override fun create(record: BackupExternalAttemptRecord) {
        synchronized(lock) {
            require(record.phase == BackupExternalAttemptPhase.AWAITING_DESTINATION)
            require(record.terminalStatus == null)
            val prefix = prefix(record.effectIdentity)
            check(records.readLatest(prefix) == null) {
                "backup external attempt already exists"
            }
            records.publish(
                prefix = prefix,
                sequence = 1L,
                bytes = BackupExternalAttemptCodec.encode(1L, record),
            )
        }
    }

    override fun compareAndSet(
        expected: BackupExternalAttemptRecord,
        updated: BackupExternalAttemptRecord,
    ): Boolean =
        synchronized(lock) {
            requireLegalMutation(expected, updated)
            val current = readPersisted(expected.effectIdentity) ?: return@synchronized false
            if (current.record != expected) return@synchronized false
            val next = Math.addExact(current.sequence, 1L)
            records.publish(
                prefix = prefix(expected.effectIdentity),
                sequence = next,
                bytes = BackupExternalAttemptCodec.encode(next, updated),
            )
            true
        }

    override fun pending(): List<BackupExternalAttemptRecord> =
        synchronized(lock) {
            if (!Files.exists(directory)) return@synchronized emptyList()
            val prefixes =
                Files.newDirectoryStream(directory).use { entries ->
                    entries.mapNotNull { path ->
                        FILE.matchEntire(path.fileName.toString())
                            ?.groupValues
                            ?.get(1)
                    }.toSet()
                }
            prefixes.map { prefix ->
                val durable =
                    checkNotNull(records.readLatest(prefix)) {
                        "backup attempt disappeared during scan"
                    }
                val decoded = BackupExternalAttemptCodec.decode(durable.bytes)
                check(decoded.sequence == durable.sequence)
                check(prefix(decoded.record.effectIdentity) == prefix)
                decoded.record
            }
                .filter { it.phase != BackupExternalAttemptPhase.TERMINAL }
                .sortedBy { it.effectIdentity }
        }

    private fun readPersisted(effectIdentity: String): PersistedBackupExternalAttempt? {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        val durable = records.readLatest(prefix(effectIdentity)) ?: return null
        val decoded = BackupExternalAttemptCodec.decode(durable.bytes)
        check(decoded.sequence == durable.sequence)
        check(decoded.record.effectIdentity == effectIdentity)
        return decoded
    }

    private fun requireLegalMutation(
        expected: BackupExternalAttemptRecord,
        updated: BackupExternalAttemptRecord,
    ) {
        require(
            expected.copy(
                phase = updated.phase,
                terminalStatus = updated.terminalStatus,
                reason = updated.reason,
            ) == updated
        ) {
            "backup external attempt metadata is immutable"
        }
        require(expected.phase != BackupExternalAttemptPhase.TERMINAL) {
            "terminal backup external attempt is immutable"
        }
        when (expected.phase) {
            BackupExternalAttemptPhase.AWAITING_DESTINATION -> {
                require(
                    updated.phase == BackupExternalAttemptPhase.WRITE_STARTED ||
                        updated.phase == BackupExternalAttemptPhase.TERMINAL
                )
                if (updated.phase == BackupExternalAttemptPhase.TERMINAL) {
                    require(
                        updated.terminalStatus == BackupExternalWriteStatus.CANCELLED ||
                            updated.terminalStatus == BackupExternalWriteStatus.FAILED
                    )
                }
            }

            BackupExternalAttemptPhase.WRITE_STARTED -> {
                require(updated.phase == BackupExternalAttemptPhase.TERMINAL)
                require(
                    updated.terminalStatus == BackupExternalWriteStatus.SUCCESS ||
                        updated.terminalStatus == BackupExternalWriteStatus.FAILED ||
                        updated.terminalStatus == BackupExternalWriteStatus.EFFECT_UNKNOWN
                )
            }

            BackupExternalAttemptPhase.TERMINAL ->
                error("terminal backup external attempt is immutable")
        }
    }

    private fun prefix(effectIdentity: String): String =
        "backup-" + MaintenancePathIdentity.sha256Hex(effectIdentity)

    private companion object {
        val FILE = Regex("^(backup-[0-9a-f]{64})-[0-9]{20}\\.bin$")
    }
}

class BackupExternalEffectCoordinator(
    private val store: BackupExternalAttemptStore,
) {
    fun begin(source: PreparedBackupExternalWriteSource): BackupExternalAttemptRecord {
        val existing = store.read(source.effectIdentity)
        if (existing != null) {
            check(existing.matches(source)) {
                "backup effect identity already belongs to another artifact"
            }
            return existing
        }
        val created =
            BackupExternalAttemptRecord(
                effectIdentity = source.effectIdentity,
                artifactHandleHex = source.artifactHandleHex,
                byteCount = source.byteCount,
                sha256Hex = source.sha256Hex,
                phase = BackupExternalAttemptPhase.AWAITING_DESTINATION,
                terminalStatus = null,
                reason = null,
            )
        store.create(created)
        return created
    }

    fun pickerCancelled(
        source: PreparedBackupExternalWriteSource,
    ): BackupExternalEffectReceipt {
        val current = requireCurrent(source)
        if (current.phase == BackupExternalAttemptPhase.TERMINAL) {
            check(current.terminalStatus == BackupExternalWriteStatus.CANCELLED)
            return receipt(current)
        }
        check(current.phase == BackupExternalAttemptPhase.AWAITING_DESTINATION)
        return terminalize(
            current,
            BackupExternalWriteStatus.CANCELLED,
            "picker_cancelled",
        )
    }

    fun failBeforeWrite(
        source: PreparedBackupExternalWriteSource,
        reason: String,
    ): BackupExternalEffectReceipt {
        val current = requireCurrent(source)
        check(current.phase == BackupExternalAttemptPhase.AWAITING_DESTINATION)
        return terminalize(current, BackupExternalWriteStatus.FAILED, bounded(reason))
    }

    fun markWriteStarted(
        source: PreparedBackupExternalWriteSource,
    ): BackupExternalAttemptRecord {
        val current = requireCurrent(source)
        check(current.phase == BackupExternalAttemptPhase.AWAITING_DESTINATION) {
            "backup external write cannot restart after durable write start"
        }
        val updated = current.copy(phase = BackupExternalAttemptPhase.WRITE_STARTED)
        check(store.compareAndSet(current, updated)) {
            "backup external attempt changed before write start"
        }
        return updated
    }

    fun completeVerifiedSuccess(
        source: PreparedBackupExternalWriteSource,
    ): BackupExternalEffectReceipt =
        completeStarted(source, BackupExternalWriteStatus.SUCCESS, null)

    fun completeKnownFailureAfterStart(
        source: PreparedBackupExternalWriteSource,
        reason: String,
    ): BackupExternalEffectReceipt =
        completeStarted(source, BackupExternalWriteStatus.FAILED, bounded(reason))

    fun completeUnknown(
        source: PreparedBackupExternalWriteSource,
        reason: String,
    ): BackupExternalEffectReceipt =
        completeStarted(source, BackupExternalWriteStatus.EFFECT_UNKNOWN, bounded(reason))

    fun reconcileInterruptedWrites(): List<BackupExternalEffectReceipt> =
        store.pending().map { current ->
            when (current.phase) {
                BackupExternalAttemptPhase.AWAITING_DESTINATION ->
                    terminalize(
                        current,
                        BackupExternalWriteStatus.CANCELLED,
                        "process_interrupted_before_write_start",
                    )
                BackupExternalAttemptPhase.WRITE_STARTED ->
                    terminalize(
                        current,
                        BackupExternalWriteStatus.EFFECT_UNKNOWN,
                        "process_interrupted_after_write_start",
                    )
                BackupExternalAttemptPhase.TERMINAL ->
                    error("terminal backup attempt cannot be pending")
            }
        }

    private fun completeStarted(
        source: PreparedBackupExternalWriteSource,
        status: BackupExternalWriteStatus,
        reason: String?,
    ): BackupExternalEffectReceipt {
        val current = requireCurrent(source)
        if (current.phase == BackupExternalAttemptPhase.TERMINAL) {
            check(current.terminalStatus == status && current.reason == reason)
            return receipt(current)
        }
        check(current.phase == BackupExternalAttemptPhase.WRITE_STARTED)
        return terminalize(current, status, reason)
    }

    private fun requireCurrent(
        source: PreparedBackupExternalWriteSource,
    ): BackupExternalAttemptRecord {
        val current =
            store.read(source.effectIdentity)
                ?: error("backup external attempt was not begun")
        check(current.matches(source)) {
            "backup external attempt metadata mismatch"
        }
        return current
    }

    private fun terminalize(
        current: BackupExternalAttemptRecord,
        status: BackupExternalWriteStatus,
        reason: String?,
    ): BackupExternalEffectReceipt {
        val updated =
            current.copy(
                phase = BackupExternalAttemptPhase.TERMINAL,
                terminalStatus = status,
                reason = reason,
            )
        check(store.compareAndSet(current, updated)) {
            "backup external attempt changed before terminal outcome"
        }
        return receipt(updated)
    }

    private fun receipt(record: BackupExternalAttemptRecord): BackupExternalEffectReceipt =
        BackupExternalEffectReceipt(
            effectIdentity = record.effectIdentity,
            artifactHandleHex = record.artifactHandleHex,
            byteCount = record.byteCount,
            sha256Hex = record.sha256Hex,
            status = checkNotNull(record.terminalStatus),
            reason = record.reason,
        )

    private fun bounded(reason: String): String {
        require(reason.isNotBlank() && reason.length <= 256)
        return reason
    }
}

private object BackupExternalAttemptCodec {
    fun encode(
        sequence: Long,
        record: BackupExternalAttemptRecord,
    ): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(sequence)
                    output.writeUTF(record.effectIdentity)
                    output.writeUTF(record.artifactHandleHex)
                    output.writeLong(record.byteCount)
                    output.writeUTF(record.sha256Hex)
                    output.writeUTF(record.phase.name)
                    output.writeBoolean(record.terminalStatus != null)
                    record.terminalStatus?.let { output.writeUTF(it.name) }
                    output.writeBoolean(record.reason != null)
                    record.reason?.let(output::writeUTF)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): PersistedBackupExternalAttempt =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val sequence = input.readLong()
            check(sequence > 0L)
            val decoded =
                BackupExternalAttemptRecord(
                    effectIdentity = input.readUTF(),
                    artifactHandleHex = input.readUTF(),
                    byteCount = input.readLong(),
                    sha256Hex = input.readUTF(),
                    phase = BackupExternalAttemptPhase.valueOf(input.readUTF()),
                    terminalStatus =
                        if (input.readBoolean()) {
                            BackupExternalWriteStatus.valueOf(input.readUTF())
                        } else {
                            null
                        },
                    reason =
                        if (input.readBoolean()) {
                            input.readUTF()
                        } else {
                            null
                        },
                )
            check(input.available() == 0)
            PersistedBackupExternalAttempt(sequence, decoded)
        }
}

private data class PersistedBackupExternalAttempt(
    val sequence: Long,
    val record: BackupExternalAttemptRecord,
)
