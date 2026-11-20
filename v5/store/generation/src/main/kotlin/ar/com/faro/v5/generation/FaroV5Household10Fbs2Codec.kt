package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal data class FaroV5Household10Fbs2Snapshot(
    val base: FaroV5Auxiliary4Fbs2Snapshot,
    val household: Household10BackupSection,
)

/** FBS2v6 contains a complete FBS2v5 base and a mandatory Household10 section. */
internal object FaroV5Household10Fbs2Codec {
    private const val MAGIC = 0x46425332 // FBS2
    private const val VERSION = 6
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + 2 * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MAX_HOUSEHOLD_BYTES = 4 * 1024 * 1024

    fun encode(snapshot: FaroV5Household10Fbs2Snapshot): ByteArray {
        val base = FaroV5Auxiliary4Fbs2Codec.encode(snapshot.base)
        try {
            val household = FaroV5Household10BackupSectionCodec.encode(snapshot.household)
            try {
                val length = HEADER_BYTES.toLong() + base.size + household.size
                require(length <= MAX_BYTES) { "FBS2v6 exceeds the plaintext budget" }

                val bytes = ByteArrayOutputStream(length.toInt())
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeInt(base.size)
                    output.writeInt(household.size)
                    output.write(sha256(base))
                    output.write(sha256(household))
                    output.write(base)
                    output.write(household)
                }
                return bytes.toByteArray()
            } finally {
                household.fill(0)
            }
        } finally {
            base.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household10Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 2)..MAX_BYTES) { "FBS2v6 size is invalid" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                "FBS2v6 magic or version mismatch"
            }
            val baseSize = input.readInt()
            val householdSize = input.readInt()
            require(baseSize > 0 && householdSize in 1..MAX_HOUSEHOLD_BYTES &&
                HEADER_BYTES.toLong() + baseSize + householdSize == bytes.size.toLong()) {
                "FBS2v6 section lengths are invalid"
            }

            val baseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val householdDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val base = ByteArray(baseSize)
            try {
                input.readFully(base)
                val household = ByteArray(householdSize)
                try {
                    input.readFully(household)
                    require(MessageDigest.isEqual(baseDigest, sha256(base)) &&
                        MessageDigest.isEqual(householdDigest, sha256(household))) {
                        "FBS2v6 section digest mismatch"
                    }
                    FaroV5Household10Fbs2Snapshot(
                        base = FaroV5Auxiliary4Fbs2Codec.decode(base),
                        household = FaroV5Household10BackupSectionCodec.decode(household),
                    )
                } finally {
                    household.fill(0)
                }
            } finally {
                base.fill(0)
            }
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
