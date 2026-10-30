package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

data class UpdateControlEntry(
    val expectedInstall: ExpectedInstall,
    val phase: UpdateLifecyclePhase,
    val platformSessionIdentity: String? = null,
    val failure: UpdateFailure? = null,
) {
    init {
        require(
            phase in setOf(
                UpdateLifecyclePhase.INSTALL_PREPARED,
                UpdateLifecyclePhase.OS_HANDOFF,
                UpdateLifecyclePhase.AWAITING_USER_ACTION,
                UpdateLifecyclePhase.RECONCILE_REQUIRED,
                UpdateLifecyclePhase.INSTALLED,
                UpdateLifecyclePhase.CANCELLED,
                UpdateLifecyclePhase.FAILED,
                UpdateLifecyclePhase.EFFECT_UNKNOWN,
            )
        ) {
            "update control requires an expected-install phase"
        }

        if (platformSessionIdentity != null) {
            requireControlString(
                platformSessionIdentity,
                maxLength = 256,
                label = "platform install session identity",
            )
        }

        when (phase) {
            UpdateLifecyclePhase.INSTALL_PREPARED -> {
                require(failure == null) {
                    "prepared install cannot carry a failure"
                }
            }

            UpdateLifecyclePhase.OS_HANDOFF,
            UpdateLifecyclePhase.AWAITING_USER_ACTION,
            UpdateLifecyclePhase.RECONCILE_REQUIRED,
            UpdateLifecyclePhase.INSTALLED,
            -> {
                require(platformSessionIdentity != null) {
                    "$phase requires a durable platform session identity"
                }
                require(failure == null) {
                    "$phase cannot carry a failure"
                }
            }

            UpdateLifecyclePhase.EFFECT_UNKNOWN -> {
                require(platformSessionIdentity != null) {
                    "effect-unknown requires the handed-off platform session identity"
                }
                require(
                    failure?.kind in
                        setOf(
                            UpdateFailureKind.EFFECT_UNKNOWN,
                            UpdateFailureKind.SESSION_LOST,
                        )
                ) {
                    "effect-unknown requires an uncertainty failure"
                }
            }

            UpdateLifecyclePhase.FAILED -> {
                require(failure != null) {
                    "failed update control requires a typed failure"
                }
                require(
                    failure.kind != UpdateFailureKind.CANCELLED &&
                        failure.kind != UpdateFailureKind.EFFECT_UNKNOWN
                ) {
                    "failed update control cannot encode cancellation or uncertainty"
                }
            }

            UpdateLifecyclePhase.CANCELLED -> {
                require(failure == null) {
                    "cancelled update control does not carry a failure"
                }
            }

            else -> error("unreachable update control phase: $phase")
        }
    }

    val expectedInstallId: ExpectedInstallId
        get() = expectedInstall.expectedInstallId
}

interface UpdateControlStorePort {
    fun read(expectedInstallId: ExpectedInstallId): UpdateControlEntry?

    fun pending(): List<UpdateControlEntry>

    fun create(expectedInstall: ExpectedInstall)

    fun compareAndSet(
        expected: UpdateControlEntry,
        updated: UpdateControlEntry,
    ): Boolean
}

class FileUpdateControlStore(
    root: Path,
) : UpdateControlStorePort {
    private val directory =
        root.toAbsolutePath().normalize().resolve("update").resolve("control")
    private val records = DurableRecordDirectory(directory)

    override fun read(expectedInstallId: ExpectedInstallId): UpdateControlEntry? =
        readPersisted(expectedInstallId)?.entry

    override fun pending(): List<UpdateControlEntry> {
        val prefixes =
            Files.newDirectoryStream(directory).use { entries ->
                entries.mapNotNull { path ->
                    CONTROL_FILE.matchEntire(path.fileName.toString())
                        ?.groupValues
                        ?.get(1)
                }.toSet()
            }

        return prefixes.map { prefix ->
            val durable =
                checkNotNull(records.readLatest(prefix)) {
                    "update control disappeared during pending scan"
                }
            val persisted = UpdateControlCodec.decode(durable.bytes)
            check(persisted.sequence == durable.sequence) {
                "update control sequence mismatch during pending scan"
            }
            check(prefix(persisted.entry.expectedInstallId) == prefix) {
                "update control path identity mismatch"
            }
            persisted.entry
        }
            .filterNot { UpdateLifecycleTransitionPolicy.isTerminal(it.phase) }
            .sortedBy { it.expectedInstallId.value }
    }

    override fun create(expectedInstall: ExpectedInstall) {
        val prefix = prefix(expectedInstall.expectedInstallId)
        check(records.readLatest(prefix) == null) {
            "expected install control already exists"
        }
        val entry =
            UpdateControlEntry(
                expectedInstall = expectedInstall,
                phase = UpdateLifecyclePhase.INSTALL_PREPARED,
            )
        records.publish(
            prefix = prefix,
            sequence = 1L,
            bytes = UpdateControlCodec.encode(1L, entry),
        )
    }

    override fun compareAndSet(
        expected: UpdateControlEntry,
        updated: UpdateControlEntry,
    ): Boolean {
        requireLegalMutation(expected, updated)

        val current = readPersisted(expected.expectedInstallId) ?: return false
        if (current.entry != expected) {
            return false
        }

        val nextSequence = Math.addExact(current.sequence, 1L)
        records.publish(
            prefix = prefix(expected.expectedInstallId),
            sequence = nextSequence,
            bytes = UpdateControlCodec.encode(nextSequence, updated),
        )
        return true
    }

    private fun readPersisted(
        expectedInstallId: ExpectedInstallId,
    ): PersistedUpdateControl? {
        val durable = records.readLatest(prefix(expectedInstallId)) ?: return null
        val persisted = UpdateControlCodec.decode(durable.bytes)
        check(persisted.sequence == durable.sequence) {
            "update control sequence mismatch"
        }
        check(persisted.entry.expectedInstallId == expectedInstallId) {
            "update control expected-install identity mismatch"
        }
        return persisted
    }

    private fun requireLegalMutation(
        expected: UpdateControlEntry,
        updated: UpdateControlEntry,
    ) {
        require(updated.expectedInstall == expected.expectedInstall) {
            "expected install identity and payload are immutable"
        }

        val existingSession = expected.platformSessionIdentity
        val updatedSession = updated.platformSessionIdentity

        if (updated.phase == expected.phase) {
            require(expected.phase == UpdateLifecyclePhase.INSTALL_PREPARED) {
                "same-phase update control mutation is only valid while install is prepared"
            }
            require(existingSession == null && updatedSession != null) {
                "same-phase prepared mutation must bind the platform session exactly once"
            }
            require(updated.failure == null) {
                "prepared session binding cannot carry a failure"
            }
            return
        }

        val legalPhase =
            UpdateLifecycleTransitionPolicy.transition(
                current = expected.phase,
                next = updated.phase,
            )
        require(legalPhase == updated.phase) {
            "update control phase mutation is not canonical"
        }

        if (existingSession == null && updatedSession != null) {
            require(expected.phase == UpdateLifecyclePhase.INSTALL_PREPARED) {
                "platform session may bind only while install is prepared"
            }
        } else if (existingSession != null) {
            require(updatedSession == existingSession) {
                "platform session identity is immutable once bound"
            }
        }
    }

    private fun prefix(expectedInstallId: ExpectedInstallId): String =
        "update-control-" + MaintenancePathIdentity.sha256Hex(expectedInstallId.value)

    private companion object {
        val CONTROL_FILE =
            Regex("^(update-control-[0-9a-f]{64})-[0-9]{20}\\.bin$")
    }
}

internal data class PersistedUpdateControl(
    val sequence: Long,
    val entry: UpdateControlEntry,
)

internal object UpdateControlCodec {
    private const val SIGNING_LINEAGE = 1
    private const val MULTIPLE_SIGNERS = 2
    private const val MAX_SIGNERS = 32

    fun encode(
        sequence: Long,
        entry: UpdateControlEntry,
    ): ByteArray {
        require(sequence > 0L) { "update control sequence must be positive" }
        return MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(sequence)
                    writeExpectedInstall(output, entry.expectedInstall)
                    output.writeUTF(entry.phase.name)
                    writeNullableString(output, entry.platformSessionIdentity)
                    output.writeBoolean(entry.failure != null)
                    entry.failure?.let { failure ->
                        output.writeUTF(failure.kind.name)
                        output.writeUTF(failure.reason)
                    }
                }
                buffer.toByteArray()
            }
        )
    }

    fun decode(record: ByteArray): PersistedUpdateControl =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val sequence = input.readLong()
            check(sequence > 0L) {
                "update control sequence must be positive"
            }
            val entry =
                UpdateControlEntry(
                    expectedInstall = readExpectedInstall(input),
                    phase = UpdateLifecyclePhase.valueOf(input.readUTF()),
                    platformSessionIdentity = readNullableString(input),
                    failure =
                        if (input.readBoolean()) {
                            UpdateFailure(
                                kind = UpdateFailureKind.valueOf(input.readUTF()),
                                reason = input.readUTF(),
                            )
                        } else {
                            null
                        },
                )
            check(input.available() == 0) {
                "update control payload has trailing bytes"
            }
            PersistedUpdateControl(sequence, entry)
        }

    private fun writeExpectedInstall(
        output: DataOutputStream,
        expected: ExpectedInstall,
    ) {
        output.writeUTF(expected.expectedInstallId.value)
        output.writeUTF(expected.operationId.value)
        writeInstalledIdentity(output, expected.installedIdentityAtPreparation)
        output.writeUTF(expected.staged.artifactId.value)
        writeCandidate(output, expected.staged.candidate)
        writeArtifactIdentity(output, expected.staged.artifactIdentity)
    }

    private fun readExpectedInstall(input: DataInputStream): ExpectedInstall {
        val expectedInstallId = ExpectedInstallId(input.readUTF())
        val operationId = UpdateOperationId(input.readUTF())
        val installedIdentity = readInstalledIdentity(input)
        val artifactId = StagedUpdateArtifactId(input.readUTF())
        val candidate = readCandidate(input)
        val artifactIdentity = readArtifactIdentity(input)
        return ExpectedInstall(
            expectedInstallId = expectedInstallId,
            operationId = operationId,
            installedIdentityAtPreparation = installedIdentity,
            staged =
                VerifiedStagedUpdate(
                    artifactId = artifactId,
                    candidate = candidate,
                    artifactIdentity = artifactIdentity,
                ),
        )
    }

    private fun writeInstalledIdentity(
        output: DataOutputStream,
        identity: InstalledUpdateIdentity,
    ) {
        output.writeUTF(identity.packageName)
        output.writeLong(identity.versionCode)
        writeSigningIdentity(output, identity.signingIdentity)
    }

    private fun readInstalledIdentity(input: DataInputStream): InstalledUpdateIdentity =
        InstalledUpdateIdentity(
            packageName = input.readUTF(),
            versionCode = input.readLong(),
            signingIdentity = readSigningIdentity(input),
        )

    private fun writeCandidate(
        output: DataOutputStream,
        candidate: UpdateManifestCandidate,
    ) {
        output.writeUTF(candidate.sourceIdentity)
        output.writeUTF(candidate.channel)
        output.writeUTF(candidate.packageName)
        output.writeLong(candidate.versionCode)
        output.writeUTF(candidate.versionName)
        output.writeLong(candidate.minimumSourceVersionCode)
        output.writeUTF(candidate.artifactUri)
        output.writeUTF(candidate.artifactSha256.value)
        output.writeLong(candidate.declaredSizeBytes)
    }

    private fun readCandidate(input: DataInputStream): UpdateManifestCandidate =
        UpdateManifestCandidate(
            sourceIdentity = input.readUTF(),
            channel = input.readUTF(),
            packageName = input.readUTF(),
            versionCode = input.readLong(),
            versionName = input.readUTF(),
            minimumSourceVersionCode = input.readLong(),
            artifactUri = input.readUTF(),
            artifactSha256 = UpdateSha256(input.readUTF()),
            declaredSizeBytes = input.readLong(),
        )

    private fun writeArtifactIdentity(
        output: DataOutputStream,
        identity: UpdateArtifactIdentity,
    ) {
        output.writeUTF(identity.packageName)
        output.writeLong(identity.versionCode)
        output.writeUTF(identity.versionName)
        writeSigningIdentity(output, identity.signingIdentity)
        output.writeLong(identity.byteCount)
        output.writeUTF(identity.sha256.value)
    }

    private fun readArtifactIdentity(input: DataInputStream): UpdateArtifactIdentity =
        UpdateArtifactIdentity(
            packageName = input.readUTF(),
            versionCode = input.readLong(),
            versionName = input.readUTF(),
            signingIdentity = readSigningIdentity(input),
            byteCount = input.readLong(),
            sha256 = UpdateSha256(input.readUTF()),
        )

    private fun writeSigningIdentity(
        output: DataOutputStream,
        signingIdentity: UpdateSigningIdentity,
    ) {
        when (signingIdentity) {
            is UpdateSigningIdentity.Lineage -> {
                require(signingIdentity.certificatesSha256.size <= MAX_SIGNERS) {
                    "signing lineage exceeds persistence bound"
                }
                output.writeByte(SIGNING_LINEAGE)
                output.writeInt(signingIdentity.certificatesSha256.size)
                signingIdentity.certificatesSha256.forEach { output.writeUTF(it.value) }
            }

            is UpdateSigningIdentity.MultipleSigners -> {
                require(signingIdentity.certificatesSha256.size <= MAX_SIGNERS) {
                    "multiple signers exceed persistence bound"
                }
                output.writeByte(MULTIPLE_SIGNERS)
                val sorted = signingIdentity.certificatesSha256.sortedBy { it.value }
                output.writeInt(sorted.size)
                sorted.forEach { output.writeUTF(it.value) }
            }
        }
    }

    private fun readSigningIdentity(input: DataInputStream): UpdateSigningIdentity {
        val kind = input.readUnsignedByte()
        val count = input.readInt()
        check(count in 1..MAX_SIGNERS) {
            "signing identity count outside persistence bound"
        }
        val certificates =
            List(count) {
                UpdateSha256(input.readUTF())
            }
        return when (kind) {
            SIGNING_LINEAGE ->
                UpdateSigningIdentity.Lineage(certificates)

            MULTIPLE_SIGNERS ->
                UpdateSigningIdentity.MultipleSigners(certificates.toSet())

            else -> error("unsupported signing identity kind: $kind")
        }
    }

    private fun writeNullableString(
        output: DataOutputStream,
        value: String?,
    ) {
        output.writeBoolean(value != null)
        value?.let(output::writeUTF)
    }

    private fun readNullableString(input: DataInputStream): String? =
        if (input.readBoolean()) input.readUTF() else null
}

private fun requireControlString(
    value: String,
    maxLength: Int,
    label: String,
) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require(value == value.trim()) { "$label must be canonical" }
    require(value.length <= maxLength) { "$label is too long" }
}
