package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.SemanticBackupSet
import ar.com.faro.v5.maintenance.SemanticBackupSetCodec
import ar.com.faro.v5.maintenance.SemanticBackupSetIntegrity
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
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
    /** Null means legacy FBS2 v1 did not carry G4. Null is NOT a verified empty owner. */
    val g4Domains: RealityDomainBackupSnapshot? = null,
    /** Non-null only in FBS2 v3; absent is NOT a certified empty Planning owner. */
    val planningDomains: PlanningDomainSectionSnapshot? = null,
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
    private const val G4_VERSION = 2
    private const val PLANNING_VERSION = 3
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = (4 * 4) + (DIGEST_BYTES * 2)
    private const val G4_HEADER_BYTES = (5 * 4) + (DIGEST_BYTES * 3)
    private const val PLANNING_HEADER_BYTES = (6 * 4) + (DIGEST_BYTES * 4)
    const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024

    fun encode(set: FaroV5FiveStoreSemanticSet): ByteArray {
        check(SemanticBackupSetIntegrity.matches(set.fourStore)) {
            "FBS2 requires a valid FBS1 manifest/authority"
        }
        set.workspace.validateForRestore()
        if (set.planningDomains != null) return encodePlanningV3(set)
        if (set.g4Domains != null) return encodeG4(set)

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
        if (bytes.size >= 8) {
            when (java.nio.ByteBuffer.wrap(bytes).getInt(4)) {
                G4_VERSION -> return decodeG4(bytes)
                PLANNING_VERSION -> return decodePlanningV3(bytes)
            }
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

    /** Version 2 has three explicit bounded sections: legacy, workspace, G4. */
    private fun encodeG4(set: FaroV5FiveStoreSemanticSet): ByteArray {
        val domain = checkNotNull(set.g4Domains)
        val g4Bytes = FaroV5G4DomainSemanticCodec.encode(domain)
        try {
            val fourBytes = SemanticBackupSetCodec.encodeWithin(
                set.fourStore, MAX_PLAINTEXT_BYTES - G4_HEADER_BYTES - g4Bytes.size - 1,
            )
            try {
                val workspaceBytes = WorkspaceSemanticBackupCodec.encodeWithin(
                    set.workspace, MAX_PLAINTEXT_BYTES - G4_HEADER_BYTES -
                        g4Bytes.size - fourBytes.size,
                )
                try {
                    check(G4_HEADER_BYTES.toLong() + fourBytes.size + workspaceBytes.size +
                        g4Bytes.size <= MAX_PLAINTEXT_BYTES.toLong()
                    ) { "FBS2 G4 section budget exceeded" }
                    return ByteArrayOutputStream(
                        G4_HEADER_BYTES + fourBytes.size + workspaceBytes.size + g4Bytes.size,
                    ).use { buffer ->
                        DataOutputStream(buffer).use { output ->
                            output.writeInt(MAGIC)
                            output.writeInt(G4_VERSION)
                            output.writeInt(fourBytes.size)
                            output.writeInt(workspaceBytes.size)
                            output.writeInt(g4Bytes.size)
                            output.write(sha256(fourBytes))
                            output.write(sha256(workspaceBytes))
                            output.write(sha256(g4Bytes))
                            output.write(fourBytes)
                            output.write(workspaceBytes)
                            output.write(g4Bytes)
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
        } finally {
            g4Bytes.fill(0)
        }
    }

    /** SHA-256 detects accidental corruption only; a host must use FAROBAK2 AEAD. */
    private fun decodeG4(bytes: ByteArray): FaroV5FiveStoreSemanticSet {
        check(bytes.size in (G4_HEADER_BYTES + 3)..MAX_PLAINTEXT_BYTES) {
            "FBS2 G4 section header truncated"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC && input.readInt() == G4_VERSION) {
                "invalid FBS2 G4 version"
            }
            val fourSize = input.readInt()
            val workspaceSize = input.readInt()
            val g4Size = input.readInt()
            check(fourSize > 0 && workspaceSize > 0 &&
                g4Size in 1..FaroV5G4DomainSemanticCodec.MAX_BYTES &&
                G4_HEADER_BYTES.toLong() + fourSize.toLong() + workspaceSize.toLong() +
                    g4Size.toLong() == bytes.size.toLong()
            ) { "invalid FBS2 G4 section lengths" }
            val expectedFour = ByteArray(DIGEST_BYTES)
            val expectedWorkspace = ByteArray(DIGEST_BYTES)
            val expectedG4 = ByteArray(DIGEST_BYTES)
            input.readFully(expectedFour)
            input.readFully(expectedWorkspace)
            input.readFully(expectedG4)
            val four = ByteArray(fourSize)
            val workspace = ByteArray(workspaceSize)
            val g4 = ByteArray(g4Size)
            try {
                input.readFully(four)
                input.readFully(workspace)
                input.readFully(g4)
                check(input.available() == 0) { "FBS2 G4 trailing data" }
                check(MessageDigest.isEqual(expectedFour, sha256(four)) &&
                    MessageDigest.isEqual(expectedWorkspace, sha256(workspace)) &&
                    MessageDigest.isEqual(expectedG4, sha256(g4))
                ) { "FBS2 G4 section digest mismatch (not authenticity)" }
                val fourSet = SemanticBackupSetCodec.decode(four)
                val workspaceSet = WorkspaceSemanticBackupCodec.decode(workspace)
                val domain = FaroV5G4DomainSemanticCodec.decode(g4)
                check(SemanticBackupSetIntegrity.matches(fourSet)) {
                    "FBS2 G4 inner four-store manifest invalid"
                }
                workspaceSet.validateForRestore()
                FaroV5FiveStoreSemanticSet(fourSet, workspaceSet, domain)
            } finally {
                four.fill(0)
                workspace.fill(0)
                g4.fill(0)
                expectedFour.fill(0)
                expectedWorkspace.fill(0)
                expectedG4.fill(0)
            }
        }
    }


    /**
     * FBS2 version 3 SEMANTIC ONLY. Four named sections, all mandatory.
     * This method cannot capture Reality, grant host authority or enable export.
     * The authenticated/identity-fenced restore MUST reject this until the
     * schema8 owner and complete Replay/ACL are implemented and tested.
     */
    private fun encodePlanningV3(set: FaroV5FiveStoreSemanticSet): ByteArray {
        val g4Snapshot = checkNotNull(set.g4Domains) {
            "FBS2 v3 cannot omit G4 owner; null is not a verified empty G4"
        }
        val planSnapshot = checkNotNull(set.planningDomains)
        val g4Bytes = FaroV5G4DomainSemanticCodec.encode(g4Snapshot)
        try {
            val planBytes = FaroV5PlanningDomainSemanticCodec.encode(planSnapshot)
            try {
                val remaining = MAX_PLAINTEXT_BYTES.toLong() - PLANNING_HEADER_BYTES -
                    g4Bytes.size - planBytes.size
                check(remaining > 2L) { "FBS2 v3 section budget exhausted" }
                val fourBytes = SemanticBackupSetCodec.encodeWithin(
                    set.fourStore, (remaining - 1L).toInt(),
                )
                try {
                    val workspaceBytes = WorkspaceSemanticBackupCodec.encodeWithin(
                        set.workspace, (remaining - fourBytes.size).toInt(),
                    )
                    try {
                        val total = PLANNING_HEADER_BYTES.toLong() + fourBytes.size +
                            workspaceBytes.size + g4Bytes.size + planBytes.size
                        check(total <= MAX_PLAINTEXT_BYTES) {
                            "FBS2 v3 exceeds global plaintext budget"
                        }
                        return ByteArrayOutputStream(total.toInt()).use { buffer ->
                            DataOutputStream(buffer).use { output ->
                                output.writeInt(MAGIC)
                                output.writeInt(PLANNING_VERSION)
                                output.writeInt(fourBytes.size)
                                output.writeInt(workspaceBytes.size)
                                output.writeInt(g4Bytes.size)
                                output.writeInt(planBytes.size)
                                for (section in arrayOf(fourBytes, workspaceBytes, g4Bytes, planBytes)) {
                                    output.write(sha256(section))
                                }
                                output.write(fourBytes)
                                output.write(workspaceBytes)
                                output.write(g4Bytes)
                                output.write(planBytes)
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
            } finally {
                planBytes.fill(0)
            }
        } finally {
            g4Bytes.fill(0)
        }
    }

    /** Four sections are mandatory; NEVER silently downgrade Planning to FBS2 v1/v2. */
    private fun decodePlanningV3(bytes: ByteArray): FaroV5FiveStoreSemanticSet {
        check(bytes.size in (PLANNING_HEADER_BYTES + 4)..MAX_PLAINTEXT_BYTES) {
            "FBS2 v3 section header or payload truncated"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC && input.readInt() == PLANNING_VERSION) {
                "invalid FBS2 v3 magic/version"
            }
            val lengths = IntArray(4) { input.readInt() }
            check(lengths.all { it > 0 } &&
                lengths[2] <= FaroV5G4DomainSemanticCodec.MAX_BYTES &&
                lengths[3] <= FaroV5PlanningDomainSemanticCodec.MAX_BYTES &&
                PLANNING_HEADER_BYTES.toLong() + lengths.sumOf { it.toLong() } ==
                    bytes.size.toLong()
            ) { "FBS2 v3 invalid section lengths" }
            val expected = Array(4) { ByteArray(DIGEST_BYTES).also { input.readFully(it) } }
            val actual = Array(4) { index -> ByteArray(lengths[index]).also { input.readFully(it) } }
            try {
                check(input.available() == 0) { "FBS2 v3 trailing bytes" }
                check((0 until 4).all { i ->
                    MessageDigest.isEqual(expected[i], sha256(actual[i]))
                }) { "FBS2 v3 digest mismatch (not an authentication proof)" }
                val four = SemanticBackupSetCodec.decode(actual[0])
                val workspace = WorkspaceSemanticBackupCodec.decode(actual[1])
                val g4 = FaroV5G4DomainSemanticCodec.decode(actual[2])
                val planning = FaroV5PlanningDomainSemanticCodec.decode(actual[3])
                check(SemanticBackupSetIntegrity.matches(four)) {
                    "FBS2 v3 four-store integrity invalid"
                }
                workspace.validateForRestore()
                FaroV5FiveStoreSemanticSet(four, workspace, g4, planning)
            } finally {
                actual.forEach { it.fill(0) }
                expected.forEach { it.fill(0) }
            }
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
