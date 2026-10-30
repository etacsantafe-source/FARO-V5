package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Immutable identity pin for one authenticated FAROBAK2 restore attempt.
 *
 * The first accepted exact encrypted archive is recorded durably BEFORE the
 * first material store write. A retry of the same operation and target MUST use
 * the exact same archive bytes, even across process restarts; archive substitution
 * or target substitution fails closed. No passphrase, plaintext, or archive
 * content is stored here.
 *
 * Call under the one host-wide material-write lease and after authenticated
 * archive opening. Not a replacement for a bootstrap journal or replay fence.
 */
fun interface RestoreArchiveIdentityPort {
    fun pinOrVerify(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        encryptedArchive: ByteArray,
    )
}

/**
 * Verification-only counterpart for a completed staged restore. It MUST refuse
 * a missing pin instead of accepting and publishing a new archive identity.
 * Call under the same host-wide exclusive maintenance lease as journal validation.
 */
fun interface ExistingRestoreArchiveIdentityVerificationPort {
    fun requireExistingPinnedArchive(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        encryptedArchive: ByteArray,
    )
}

class FileRestoreArchiveIdentity(root: Path) :
    RestoreArchiveIdentityPort, ExistingRestoreArchiveIdentityVerificationPort {
    private val records = DurableRecordDirectory(
        root.toAbsolutePath().normalize()
            .resolve("bootstrap").resolve("restore-archive-identities")
    )

    override fun pinOrVerify(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        encryptedArchive: ByteArray,
    ) {
        require(encryptedArchive.isNotEmpty()) { "restore archive must not be empty" }
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(encryptedArchive)
        val prefix = "archive-" + MaintenancePathIdentity.sha256Hex(operationId.value)
        val existing = records.readLatest(prefix)
        if (existing != null) {
            check(existing.sequence == 1L) { "restore archive identity must be immutable" }
            val observed = decode(existing.bytes)
            check(observed.operationId == operationId &&
                observed.targetGenerationId == targetGenerationId &&
                MessageDigest.isEqual(observed.archiveSha256, expectedDigest)
            ) { "restore operation attempted archive/target identity substitution" }
            return
        }
        records.publish(
            prefix = prefix,
            sequence = 1L,
            bytes = encode(operationId, targetGenerationId, expectedDigest),
        )
    }

    override fun requireExistingPinnedArchive(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        encryptedArchive: ByteArray,
    ) {
        require(encryptedArchive.isNotEmpty()) { "restore archive must not be empty" }
        val prefix = "archive-" + MaintenancePathIdentity.sha256Hex(operationId.value)
        val existing = checkNotNull(records.readLatest(prefix)) {
            "restore cannot be validated without an existing durable archive pin"
        }
        check(existing.sequence == 1L) { "restore archive identity must be immutable" }
        val observed = decode(existing.bytes)
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(encryptedArchive)
        check(
            observed.operationId == operationId &&
                observed.targetGenerationId == targetGenerationId &&
                MessageDigest.isEqual(observed.archiveSha256, expectedDigest)
        ) { "existing restore archive/target/operation identity mismatch" }
    }

    private data class Record(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val archiveSha256: ByteArray,
    )

    private fun encode(
        op: MaintenanceOperationId,
        target: StorageGenerationId,
        digest: ByteArray,
    ): ByteArray = MaintenanceRecordEnvelope.encode(
        ByteArrayOutputStream().use { buf ->
            DataOutputStream(buf).use { output ->
                output.writeInt(1)
                output.writeUTF(op.value)
                output.writeUTF(target.value)
                output.writeInt(digest.size)
                output.write(digest)
            }
            buf.toByteArray()
        }
    )

    private fun decode(bytes: ByteArray): Record =
        DataInputStream(ByteArrayInputStream(MaintenanceRecordEnvelope.decode(bytes))).use { input ->
            check(input.readInt() == 1) { "unknown restore archive identity version" }
            val op = MaintenanceOperationId(input.readUTF())
            val target = StorageGenerationId(input.readUTF())
            check(input.readInt() == 32) { "restore archive digest length invalid" }
            val digest = ByteArray(32)
            input.readFully(digest)
            check(input.available() == 0) { "restore archive record has trailing bytes" }
            Record(op, target, digest)
        }
}
