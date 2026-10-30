package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path

data class GenerationReplayFenceRecord(
    val kind: MaintenanceOperationKind,
    val generationId: StorageGenerationId,
)

class FileGenerationReplayFence(
    root: Path,
) : GenerationReplayFencePort {
    private val records =
        DurableRecordDirectory(
            root.toAbsolutePath().normalize()
                .resolve("bootstrap")
                .resolve("replay-fences")
        )

    override fun install(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    ) {
        require(kind != MaintenanceOperationKind.RESET) {
            "reset does not use restore/migration replay fence"
        }
        val expected = GenerationReplayFenceRecord(kind, targetGenerationId)
        val prefix = prefix(targetGenerationId)
        val existing = records.readLatest(prefix)
        if (existing != null) {
            check(existing.sequence == 1L) {
                "generation replay fence must be immutable"
            }
            check(decode(existing.bytes) == expected) {
                "generation replay fence conflicts with existing activation"
            }
            return
        }

        records.publish(
            prefix = prefix,
            sequence = 1L,
            bytes = encode(expected),
        )
    }

    fun isInstalled(
        kind: MaintenanceOperationKind,
        generationId: StorageGenerationId,
    ): Boolean {
        val durable = records.readLatest(prefix(generationId)) ?: return false
        check(durable.sequence == 1L)
        return decode(durable.bytes) == GenerationReplayFenceRecord(kind, generationId)
    }

    private fun prefix(generationId: StorageGenerationId): String =
        "fence-" + MaintenancePathIdentity.sha256Hex(generationId.value)

    private fun encode(record: GenerationReplayFenceRecord): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeUTF(record.kind.name)
                    output.writeUTF(record.generationId.value)
                }
                buffer.toByteArray()
            }
        )

    private fun decode(bytes: ByteArray): GenerationReplayFenceRecord =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(bytes))
        ).use { input ->
            val record =
                GenerationReplayFenceRecord(
                    kind = MaintenanceOperationKind.valueOf(input.readUTF()),
                    generationId = StorageGenerationId(input.readUTF()),
                )
            check(input.available() == 0) {
                "generation replay fence has trailing bytes"
            }
            record
        }
}
