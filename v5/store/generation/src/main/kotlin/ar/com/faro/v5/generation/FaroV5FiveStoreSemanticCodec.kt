package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.SemanticBackupSet
import ar.com.faro.v5.maintenance.SemanticBackupSetCodec
import ar.com.faro.v5.maintenance.SemanticBackupSetIntegrity
import ar.com.faro.v5.workspace.WorkspaceBackupSnapshot
import ar.com.faro.v5.workspace.WorkspaceSemanticBackupCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** The complete five-store semantic snapshot, not an activated generation. */
data class FaroV5FiveStoreSemanticSet(
    val fourStore: SemanticBackupSet,
    val workspace: WorkspaceBackupSnapshot,
)

/**
 * Distinct, bounded five-store FBS2 PLAINTEXT format.
 *
 * It embeds one already-certified FBS1 semantic set (four stores) and one WSB2
 * Workspace snapshot. The two SHA-256 section digests catch accidental corruption
 * but are NOT authenticity proofs. Callers MUST NOT export or restore these bytes
 * without a separately certified AEAD authenticated five-store archive, generation
 * identity, replay fence, quiescent read, and staged restore.
 *
 * In particular, FBS1 and FAROBAK1 remain exactly four-store/V1. Never route
 * this format through the old PortableBackupArchiveCrypto or the old restore.
 */
object FaroV5FiveStoreSemanticCodec {
    private const val MAGIC = 0x46425332 // FBS2, deliberately distinct from FBS1
    private const val VERSION = 1
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = (4 * 4) + (DIGEST_BYTES * 2)
    const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024

    fun encode(set: FaroV5FiveStoreSemanticSet): ByteArray {
        check(SemanticBackupSetIntegrity.matches(set.fourStore)) {
            "FBS2 requires a valid FBS1 manifest/authority"
        }
        set.workspace.validateForRestore()

        val fourBytes = SemanticBackupSetCodec.encodeWithin(
            set.fourStore, MAX_PLAINTEXT_BYTES - HEADER_BYTES - 1,
        )
        try {
            val workspaceBudget = MAX_PLAINTEXT_BYTES - HEADER_BYTES - fourBytes.size
            val workspaceBytes = WorkspaceSemanticBackupCodec.encodeWithin(
                set.workspace, workspaceBudget,
            )
            try {
                check(HEADER_BYTES + fourBytes.size + workspaceBytes.size <= MAX_PLAINTEXT_BYTES)
                return ByteArrayOutputStream(
                    HEADER_BYTES + fourBytes.size + workspaceBytes.size
                ).use { buffer ->
                    DataOutputStream(buffer).use { output ->
                        output.writeInt(MAGIC)
                        output.writeInt(VERSION)
                        output.writeInt(fourBytes.size)
                        output.writeInt(workspaceBytes.size)
                        output.write(sha256(fourBytes))
                        output.write(sha256(workspaceBytes))
                        output.write(fourBytes)
                        output.write(workspaceBytes)
                        output.flush()
                    }
                    buffer.toByteArray()
                }
            } finally {
                workspaceBytes.fill(0)
            }
        } finally {
            fourBytes.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5FiveStoreSemanticSet {
        check(bytes.size in (HEADER_BYTES + 2)..MAX_PLAINTEXT_BYTES) {
            "FBS2 outside bounded five-store plaintext"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC) { "FBS2 magic mismatch" }
            check(input.readInt() == VERSION) { "FBS2 version unsupported" }
            val fourLength = input.readInt()
            val workspaceLength = input.readInt()
            check(fourLength > 0 && workspaceLength > 0 &&
                fourLength <= MAX_PLAINTEXT_BYTES - HEADER_BYTES - 1 &&
                workspaceLength <= MAX_PLAINTEXT_BYTES - HEADER_BYTES - fourLength &&
                bytes.size == HEADER_BYTES + fourLength + workspaceLength
            ) { "FBS2 malformed section lengths" }
            val expectedFourDigest = ByteArray(DIGEST_BYTES)
            val expectedWorkspaceDigest = ByteArray(DIGEST_BYTES)
            input.readFully(expectedFourDigest)
            input.readFully(expectedWorkspaceDigest)
            val fourBytes = ByteArray(fourLength)
            val workspaceBytes = ByteArray(workspaceLength)
            try {
                input.readFully(fourBytes)
                input.readFully(workspaceBytes)
                check(input.available() == 0) { "FBS2 trailing bytes" }
                check(MessageDigest.isEqual(expectedFourDigest, sha256(fourBytes)) &&
                    MessageDigest.isEqual(expectedWorkspaceDigest, sha256(workspaceBytes))) {
                    "FBS2 section digest mismatch (not an authentication check)"
                }
                val fourStore = SemanticBackupSetCodec.decode(fourBytes)
                val workspace = WorkspaceSemanticBackupCodec.decode(workspaceBytes)
                FaroV5FiveStoreSemanticSet(fourStore, workspace)
            } finally {
                fourBytes.fill(0)
                workspaceBytes.fill(0)
                expectedFourDigest.fill(0)
                expectedWorkspaceDigest.fill(0)
            }
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
