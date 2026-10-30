package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal object MaintenanceRecordEnvelope {
    private const val MAGIC = 0x4641524F // FARO
    private const val VERSION = 1
    private const val DIGEST_BYTES = 32
    private const val MAX_PAYLOAD_BYTES = 32 * 1024

    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty() && payload.size <= MAX_PAYLOAD_BYTES) {
            "maintenance record payload outside bound"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)

        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(payload.size)
                output.write(payload)
                output.write(digest)
            }
            buffer.toByteArray()
        }
    }

    fun decode(record: ByteArray): ByteArray =
        DataInputStream(ByteArrayInputStream(record)).use { input ->
            check(input.readInt() == MAGIC) {
                "maintenance record magic mismatch"
            }
            check(input.readInt() == VERSION) {
                "unsupported maintenance record version"
            }
            val payloadSize = input.readInt()
            check(payloadSize in 1..MAX_PAYLOAD_BYTES) {
                "maintenance record payload size outside bound"
            }
            check(input.available() == payloadSize + DIGEST_BYTES) {
                "maintenance record length mismatch"
            }

            val payload = ByteArray(payloadSize)
            input.readFully(payload)
            val expectedDigest = ByteArray(DIGEST_BYTES)
            input.readFully(expectedDigest)
            check(input.available() == 0) {
                "maintenance record has trailing bytes"
            }

            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            check(MessageDigest.isEqual(expectedDigest, actualDigest)) {
                "maintenance record digest mismatch"
            }
            payload
        }
}

internal object BootstrapJournalCodec {
    fun encode(
        sequence: Long,
        entry: BootstrapJournalEntry,
    ): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(sequence)
                    output.writeUTF(entry.operationId.value)
                    output.writeUTF(entry.kind.name)
                    output.writeBoolean(entry.previousGenerationId != null)
                    entry.previousGenerationId?.let { output.writeUTF(it.value) }
                    output.writeUTF(entry.targetGenerationId.value)
                    output.writeUTF(entry.phase.name)
                    output.writeBoolean(entry.materialWritesAccepted)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): PersistedBootstrapJournal =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val sequence = input.readLong()
            check(sequence > 0L) { "journal sequence must be positive" }
            val entry = BootstrapJournalEntry(
                operationId = MaintenanceOperationId(input.readUTF()),
                kind = MaintenanceOperationKind.valueOf(input.readUTF()),
                previousGenerationId =
                    if (input.readBoolean()) StorageGenerationId(input.readUTF()) else null,
                targetGenerationId = StorageGenerationId(input.readUTF()),
                phase = BootstrapPhase.valueOf(input.readUTF()),
                materialWritesAccepted = input.readBoolean(),
            )
            check(input.available() == 0) { "journal payload has trailing bytes" }
            PersistedBootstrapJournal(sequence, entry)
        }
}

internal data class PersistedBootstrapJournal(
    val sequence: Long,
    val entry: BootstrapJournalEntry,
)

internal object ActiveGenerationPointerCodec {
    fun encode(pointer: ActiveGenerationPointer): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(pointer.sequence)
                    output.writeUTF(pointer.generationId.value)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): ActiveGenerationPointer =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val pointer = ActiveGenerationPointer(
                sequence = input.readLong(),
                generationId = StorageGenerationId(input.readUTF()),
            )
            check(input.available() == 0) { "active pointer payload has trailing bytes" }
            pointer
        }
}

internal object StorageGenerationDescriptorCodec {
    fun encode(descriptor: StorageGenerationDescriptor): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeUTF(descriptor.generationId.value)
                    output.writeUTF(descriptor.schemaSetFingerprint)
                    output.writeLong(descriptor.createdAtEpochMillis)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): StorageGenerationDescriptor =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val descriptor = StorageGenerationDescriptor(
                generationId = StorageGenerationId(input.readUTF()),
                schemaSetFingerprint = input.readUTF(),
                createdAtEpochMillis = input.readLong(),
            )
            check(input.available() == 0) {
                "generation descriptor payload has trailing bytes"
            }
            descriptor
        }
}
