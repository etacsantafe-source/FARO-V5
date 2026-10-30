package ar.com.faro.v5.maintenance

import ar.com.faro.v5.kernel.contracts.EffectId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Java-friendly view of one already-prepared A7 portability artifact.
 *
 * The Android adapter sees only bounded metadata plus a streaming copy function. It never receives
 * a staging path and never becomes an owner of the artifact.
 */
interface PortabilityExternalWriteSource {
    val effectIdentity: String
    val exportPlanFingerprintHex: String
    val formatProfile: String
    val artifactHandleHex: String
    val suggestedFileName: String
    val mimeType: String
    val generatedAtEpochMillis: Long
    val byteCount: Long
    val sha256Hex: String

    fun copyTo(output: OutputStream): Long
}

class PreparedPortabilityWriteSource(
    private val artifact: PreparedPortabilityArtifact,
    private val artifactStore: PortabilityArtifactStore,
) : PortabilityExternalWriteSource {
    override val effectIdentity: String = artifact.effectId.value
    override val exportPlanFingerprintHex: String = artifact.exportPlanFingerprint.value
    override val formatProfile: String = artifact.formatProfile
    override val artifactHandleHex: String = artifact.handle.value
    override val suggestedFileName: String = artifact.suggestedFileName
    override val mimeType: String = artifact.mimeType
    override val generatedAtEpochMillis: Long = artifact.generatedAtEpochMillis
    override val byteCount: Long = artifact.byteCount
    override val sha256Hex: String = artifact.sha256Hex

    override fun copyTo(output: OutputStream): Long =
        artifactStore.copyTo(artifact, output)
}

enum class PortabilityExternalAttemptPhase {
    AWAITING_DESTINATION,
    WRITE_STARTED,
    TERMINAL,
}

data class PortabilityExternalAttemptRecord(
    val effectIdentity: String,
    val exportPlanFingerprintHex: String,
    val formatProfile: String,
    val artifactHandleHex: String,
    val generatedAtEpochMillis: Long,
    val byteCount: Long,
    val sha256Hex: String,
    val destinationKind: PortabilityDestinationKind,
    val phase: PortabilityExternalAttemptPhase,
    val terminalStatus: PortabilityExternalStatus?,
    val reason: String?,
) {
    init {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256) {
            "portability effect identity must be bounded"
        }
        require(exportPlanFingerprintHex.matches(SHA_256)) {
            "portability plan fingerprint must be lowercase SHA-256"
        }
        require(formatProfile == PortabilityPreview.FORMAT_PROFILE)
        require(artifactHandleHex.matches(SHA_256)) {
            "portability artifact handle must be lowercase SHA-256"
        }
        require(generatedAtEpochMillis >= 0L)
        require(byteCount in 1..FilePortabilityArtifactStore.MAX_ARTIFACT_BYTES.toLong())
        require(sha256Hex.matches(SHA_256)) {
            "portability artifact digest must be lowercase SHA-256"
        }
        require(destinationKind == PortabilityDestinationKind.USER_SELECTED_DOCUMENT)
        require((phase == PortabilityExternalAttemptPhase.TERMINAL) == (terminalStatus != null)) {
            "terminal portability attempt must have exactly one terminal status"
        }
        require(reason == null || (reason.isNotBlank() && reason.length <= 256)) {
            "portability external reason must be null or bounded"
        }
        when (terminalStatus) {
            null -> require(reason == null) {
                "non-terminal portability attempt cannot carry a reason"
            }
            PortabilityExternalStatus.SUCCESS -> require(reason == null) {
                "successful portability write cannot carry a failure reason"
            }
            PortabilityExternalStatus.CANCELLED,
            PortabilityExternalStatus.FAILED,
            PortabilityExternalStatus.EFFECT_UNKNOWN,
            -> require(reason != null) {
                "non-success portability outcome requires a reason"
            }
        }
    }

    fun matches(source: PortabilityExternalWriteSource): Boolean =
        effectIdentity == source.effectIdentity &&
            exportPlanFingerprintHex == source.exportPlanFingerprintHex &&
            formatProfile == source.formatProfile &&
            artifactHandleHex == source.artifactHandleHex &&
            generatedAtEpochMillis == source.generatedAtEpochMillis &&
            byteCount == source.byteCount &&
            sha256Hex == source.sha256Hex

    private companion object {
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

interface PortabilityExternalAttemptStore {
    fun read(effectIdentity: String): PortabilityExternalAttemptRecord?

    fun create(record: PortabilityExternalAttemptRecord)

    fun compareAndSet(
        expected: PortabilityExternalAttemptRecord,
        updated: PortabilityExternalAttemptRecord,
    ): Boolean

    fun pending(): List<PortabilityExternalAttemptRecord>
}

/**
 * App-private operational journal for the one-shot SAF handoff.
 *
 * It deliberately persists no URI/path/provider identity. That makes process-death recovery
 * conservative: a started write without a terminal record becomes EFFECT_UNKNOWN rather than
 * enabling a blind replay to a remembered external destination.
 */
class FilePortabilityExternalAttemptStore(
    root: Path,
) : PortabilityExternalAttemptStore {
    private val lock = Any()
    private val directory =
        root.toAbsolutePath().normalize()
            .resolve("portability")
            .resolve("external-attempts")
    private val records = DurableRecordDirectory(directory)

    override fun read(effectIdentity: String): PortabilityExternalAttemptRecord? =
        synchronized(lock) {
            readPersisted(effectIdentity)?.record
        }

    override fun create(record: PortabilityExternalAttemptRecord) {
        synchronized(lock) {
            require(record.phase == PortabilityExternalAttemptPhase.AWAITING_DESTINATION) {
                "new portability external attempt must await destination"
            }
            require(record.terminalStatus == null)
            val prefix = prefix(record.effectIdentity)
            check(records.readLatest(prefix) == null) {
                "portability external attempt already exists"
            }
            records.publish(
                prefix = prefix,
                sequence = 1L,
                bytes = PortabilityExternalAttemptCodec.encode(1L, record),
            )
        }
    }

    override fun compareAndSet(
        expected: PortabilityExternalAttemptRecord,
        updated: PortabilityExternalAttemptRecord,
    ): Boolean =
        synchronized(lock) {
            requireLegalMutation(expected, updated)
            val current = readPersisted(expected.effectIdentity) ?: return@synchronized false
            if (current.record != expected) {
                return@synchronized false
            }
            val nextSequence = Math.addExact(current.sequence, 1L)
            records.publish(
                prefix = prefix(expected.effectIdentity),
                sequence = nextSequence,
                bytes = PortabilityExternalAttemptCodec.encode(nextSequence, updated),
            )
            true
        }

    override fun pending(): List<PortabilityExternalAttemptRecord> =
        synchronized(lock) {
            if (!Files.exists(directory)) return@synchronized emptyList()
            val prefixes =
                Files.newDirectoryStream(directory).use { entries ->
                    entries.mapNotNull { path ->
                        ATTEMPT_FILE.matchEntire(path.fileName.toString())
                            ?.groupValues
                            ?.get(1)
                    }.toSet()
                }

            prefixes.map { prefix ->
                val durable =
                    checkNotNull(records.readLatest(prefix)) {
                        "portability attempt disappeared during pending scan"
                    }
                val persisted = PortabilityExternalAttemptCodec.decode(durable.bytes)
                check(persisted.sequence == durable.sequence) {
                    "portability external attempt sequence mismatch"
                }
                check(prefix(persisted.record.effectIdentity) == prefix) {
                    "portability external attempt identity/path mismatch"
                }
                persisted.record
            }
                .filter { it.phase != PortabilityExternalAttemptPhase.TERMINAL }
                .sortedBy { it.effectIdentity }
        }

    private fun readPersisted(effectIdentity: String): PersistedPortabilityExternalAttempt? {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        val durable = records.readLatest(prefix(effectIdentity)) ?: return null
        val persisted = PortabilityExternalAttemptCodec.decode(durable.bytes)
        check(persisted.sequence == durable.sequence) {
            "portability external attempt sequence mismatch"
        }
        check(persisted.record.effectIdentity == effectIdentity) {
            "portability external attempt identity mismatch"
        }
        return persisted
    }

    private fun requireLegalMutation(
        expected: PortabilityExternalAttemptRecord,
        updated: PortabilityExternalAttemptRecord,
    ) {
        require(expected.effectIdentity == updated.effectIdentity) {
            "portability external effect identity is immutable"
        }
        require(expected.copy(phase = updated.phase, terminalStatus = updated.terminalStatus, reason = updated.reason) == updated) {
            "portability external attempt metadata is immutable"
        }
        require(expected.phase != PortabilityExternalAttemptPhase.TERMINAL) {
            "terminal portability external attempt is immutable"
        }

        when (expected.phase) {
            PortabilityExternalAttemptPhase.AWAITING_DESTINATION -> {
                require(
                    updated.phase == PortabilityExternalAttemptPhase.WRITE_STARTED ||
                        updated.phase == PortabilityExternalAttemptPhase.TERMINAL
                ) {
                    "awaiting portability attempt can only start write or terminate"
                }
                if (updated.phase == PortabilityExternalAttemptPhase.TERMINAL) {
                    require(
                        updated.terminalStatus == PortabilityExternalStatus.CANCELLED ||
                            updated.terminalStatus == PortabilityExternalStatus.FAILED
                    ) {
                        "pre-write portability termination must be CANCELLED or FAILED"
                    }
                }
            }

            PortabilityExternalAttemptPhase.WRITE_STARTED -> {
                require(updated.phase == PortabilityExternalAttemptPhase.TERMINAL) {
                    "started portability write must terminate"
                }
                require(
                    updated.terminalStatus == PortabilityExternalStatus.SUCCESS ||
                        updated.terminalStatus == PortabilityExternalStatus.FAILED ||
                        updated.terminalStatus == PortabilityExternalStatus.EFFECT_UNKNOWN
                ) {
                    "started portability write has invalid terminal status"
                }
            }

            PortabilityExternalAttemptPhase.TERMINAL ->
                error("terminal portability external attempt is immutable")
        }
    }

    private fun prefix(effectIdentity: String): String =
        "portability-" + MaintenancePathIdentity.sha256Hex(effectIdentity)

    private companion object {
        val ATTEMPT_FILE =
            Regex("^(portability-[0-9a-f]{64})-[0-9]{20}\\.bin$")
    }
}

class PortabilityExternalEffectCoordinator(
    private val store: PortabilityExternalAttemptStore,
) {
    fun begin(source: PortabilityExternalWriteSource): PortabilityExternalAttemptRecord {
        validateSource(source)
        val existing = store.read(source.effectIdentity)
        if (existing != null) {
            check(existing.matches(source)) {
                "portability effect identity already belongs to a different artifact"
            }
            return existing
        }

        val created =
            PortabilityExternalAttemptRecord(
                effectIdentity = source.effectIdentity,
                exportPlanFingerprintHex = source.exportPlanFingerprintHex,
                formatProfile = source.formatProfile,
                artifactHandleHex = source.artifactHandleHex,
                generatedAtEpochMillis = source.generatedAtEpochMillis,
                byteCount = source.byteCount,
                sha256Hex = source.sha256Hex,
                destinationKind = PortabilityDestinationKind.USER_SELECTED_DOCUMENT,
                phase = PortabilityExternalAttemptPhase.AWAITING_DESTINATION,
                terminalStatus = null,
                reason = null,
            )
        store.create(created)
        return created
    }

    fun pickerCancelled(source: PortabilityExternalWriteSource): PortabilityExternalEffectReceipt {
        val current = requireCurrent(source)
        if (current.phase == PortabilityExternalAttemptPhase.TERMINAL) {
            check(current.terminalStatus == PortabilityExternalStatus.CANCELLED) {
                "portability attempt already terminated differently"
            }
            return receipt(current)
        }
        check(current.phase == PortabilityExternalAttemptPhase.AWAITING_DESTINATION) {
            "picker cancellation is only valid before external write start"
        }
        return terminalize(
            current = current,
            status = PortabilityExternalStatus.CANCELLED,
            reason = "picker_cancelled",
        )
    }

    fun failBeforeWrite(
        source: PortabilityExternalWriteSource,
        reason: String,
    ): PortabilityExternalEffectReceipt {
        val current = requireCurrent(source)
        check(current.phase == PortabilityExternalAttemptPhase.AWAITING_DESTINATION) {
            "pre-write failure is only valid while awaiting destination"
        }
        return terminalize(current, PortabilityExternalStatus.FAILED, boundedReason(reason))
    }

    fun markWriteStarted(
        source: PortabilityExternalWriteSource,
    ): PortabilityExternalAttemptRecord {
        val current = requireCurrent(source)
        check(current.phase == PortabilityExternalAttemptPhase.AWAITING_DESTINATION) {
            "portability external write cannot start from terminal state"
        }
        val updated =
            current.copy(
                phase = PortabilityExternalAttemptPhase.WRITE_STARTED,
            )
        check(store.compareAndSet(current, updated)) {
            "portability external attempt changed before write start"
        }
        return updated
    }

    fun completeVerifiedSuccess(
        source: PortabilityExternalWriteSource,
    ): PortabilityExternalEffectReceipt =
        completeStarted(
            source = source,
            status = PortabilityExternalStatus.SUCCESS,
            reason = null,
        )

    fun completeKnownFailureAfterStart(
        source: PortabilityExternalWriteSource,
        reason: String,
    ): PortabilityExternalEffectReceipt =
        completeStarted(
            source = source,
            status = PortabilityExternalStatus.FAILED,
            reason = boundedReason(reason),
        )

    fun completeUnknown(
        source: PortabilityExternalWriteSource,
        reason: String,
    ): PortabilityExternalEffectReceipt =
        completeStarted(
            source = source,
            status = PortabilityExternalStatus.EFFECT_UNKNOWN,
            reason = boundedReason(reason),
        )

    /**
     * Startup/reopen recovery fence.
     *
     * A write that reached WRITE_STARTED but lacks a terminal record is never replayed. Because no
     * external URI is persisted, recovery cannot accidentally overwrite the destination again.
     */
    fun reconcileInterruptedWrites(): List<PortabilityExternalEffectReceipt> =
        store.pending()
            .filter { it.phase == PortabilityExternalAttemptPhase.WRITE_STARTED }
            .map { current ->
                terminalize(
                    current = current,
                    status = PortabilityExternalStatus.EFFECT_UNKNOWN,
                    reason = "process_interrupted_after_write_start",
                )
            }

    private fun completeStarted(
        source: PortabilityExternalWriteSource,
        status: PortabilityExternalStatus,
        reason: String?,
    ): PortabilityExternalEffectReceipt {
        val current = requireCurrent(source)
        if (current.phase == PortabilityExternalAttemptPhase.TERMINAL) {
            check(current.terminalStatus == status && current.reason == reason) {
                "portability attempt already terminated differently"
            }
            return receipt(current)
        }
        check(current.phase == PortabilityExternalAttemptPhase.WRITE_STARTED) {
            "portability outcome requires a durable write-start marker"
        }
        return terminalize(current, status, reason)
    }

    private fun requireCurrent(
        source: PortabilityExternalWriteSource,
    ): PortabilityExternalAttemptRecord {
        validateSource(source)
        val current =
            store.read(source.effectIdentity)
                ?: error("portability external attempt was not begun")
        check(current.matches(source)) {
            "portability external attempt metadata mismatch"
        }
        return current
    }

    private fun terminalize(
        current: PortabilityExternalAttemptRecord,
        status: PortabilityExternalStatus,
        reason: String?,
    ): PortabilityExternalEffectReceipt {
        val updated =
            current.copy(
                phase = PortabilityExternalAttemptPhase.TERMINAL,
                terminalStatus = status,
                reason = reason,
            )
        check(store.compareAndSet(current, updated)) {
            "portability external attempt changed before terminal outcome"
        }
        return receipt(updated)
    }

    private fun receipt(record: PortabilityExternalAttemptRecord): PortabilityExternalEffectReceipt {
        check(record.phase == PortabilityExternalAttemptPhase.TERMINAL)
        return PortabilityExternalEffectReceipt(
            effectId = EffectId(record.effectIdentity),
            exportPlanFingerprint = ExportPlanFingerprint(record.exportPlanFingerprintHex),
            formatProfile = record.formatProfile,
            generatedAtEpochMillis = record.generatedAtEpochMillis,
            byteCount = record.byteCount,
            sha256Hex = record.sha256Hex,
            destinationKind = record.destinationKind,
            status = checkNotNull(record.terminalStatus),
            reason = record.reason,
        )
    }

    private fun validateSource(source: PortabilityExternalWriteSource) {
        require(source.effectIdentity.isNotBlank() && source.effectIdentity.length <= 256)
        require(source.exportPlanFingerprintHex.matches(SHA_256))
        require(source.formatProfile == PortabilityPreview.FORMAT_PROFILE)
        require(source.artifactHandleHex.matches(SHA_256))
        require(source.suggestedFileName.matches(Regex("[A-Za-z0-9._-]{1,160}")))
        require(source.mimeType == "application/json")
        require(source.generatedAtEpochMillis >= 0L)
        require(source.byteCount in 1..FilePortabilityArtifactStore.MAX_ARTIFACT_BYTES.toLong())
        require(source.sha256Hex.matches(SHA_256))
    }

    private fun boundedReason(reason: String): String {
        require(reason.isNotBlank() && reason.length <= 256)
        return reason
    }

    private companion object {
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

private object PortabilityExternalAttemptCodec {
    fun encode(
        sequence: Long,
        record: PortabilityExternalAttemptRecord,
    ): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(sequence)
                    output.writeUTF(record.effectIdentity)
                    output.writeUTF(record.exportPlanFingerprintHex)
                    output.writeUTF(record.formatProfile)
                    output.writeUTF(record.artifactHandleHex)
                    output.writeLong(record.generatedAtEpochMillis)
                    output.writeLong(record.byteCount)
                    output.writeUTF(record.sha256Hex)
                    output.writeUTF(record.destinationKind.name)
                    output.writeUTF(record.phase.name)
                    output.writeBoolean(record.terminalStatus != null)
                    record.terminalStatus?.let { output.writeUTF(it.name) }
                    output.writeBoolean(record.reason != null)
                    record.reason?.let(output::writeUTF)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): PersistedPortabilityExternalAttempt =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val sequence = input.readLong()
            check(sequence > 0L) {
                "portability external attempt sequence must be positive"
            }
            val attempt =
                PortabilityExternalAttemptRecord(
                    effectIdentity = input.readUTF(),
                    exportPlanFingerprintHex = input.readUTF(),
                    formatProfile = input.readUTF(),
                    artifactHandleHex = input.readUTF(),
                    generatedAtEpochMillis = input.readLong(),
                    byteCount = input.readLong(),
                    sha256Hex = input.readUTF(),
                    destinationKind = PortabilityDestinationKind.valueOf(input.readUTF()),
                    phase = PortabilityExternalAttemptPhase.valueOf(input.readUTF()),
                    terminalStatus =
                        if (input.readBoolean()) {
                            PortabilityExternalStatus.valueOf(input.readUTF())
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
            check(input.available() == 0) {
                "portability external attempt payload has trailing bytes"
            }
            PersistedPortabilityExternalAttempt(sequence, attempt)
        }
}

private data class PersistedPortabilityExternalAttempt(
    val sequence: Long,
    val record: PortabilityExternalAttemptRecord,
)
