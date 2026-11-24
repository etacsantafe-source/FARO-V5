package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal data class FaroV5Household11Fbs2Snapshot(
    val base: FaroV5Household10Fbs2Snapshot,
    val receipts: Household11ReceiptBackupSection,
)

/**
 * FBS2v8: canonical two-part container for a complete v6/v7 Household10 base
 * and a mandatory H11R Reality11 receipt section. The inner SHA-256 checks
 * detect corrupt sections, NOT authenticity; only FAROBAK2 AES-GCM seals them.
 * No SQL11 restore, inventory mutation, Apply, or generation activation.
 */
internal object FaroV5Household11Fbs2Codec {
    private const val MAGIC = 0x46425332 // FBS2
    private const val VERSION = 8
    private const val INNER_V6 = 6
    private const val INNER_V7 = 7
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = Int.SIZE_BYTES * 4 + DIGEST_BYTES * 2
    private const val MAX_RECEIPT_BYTES = 4 * 1024 * 1024
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MIN_SECTION_BYTES = 1

    fun encode(snapshot: FaroV5Household11Fbs2Snapshot): ByteArray {
        val evidenceSchema = snapshot.base.base.base.fourStore.commercialEvidence.schemaVersion
        val inner = when (evidenceSchema) {
            1 -> FaroV5Household10Fbs2Codec.encode(snapshot.base)
            2 -> FaroV5Household10Fbs2Codec.encodeEvidence2(snapshot.base)
            else -> throw IllegalArgumentException("FBS2v8 requires Evidence1 or complete Evidence2")
        }
        try {
            val receiptBytes = FaroV5Household11ReceiptBackupSectionCodec.encode(snapshot.receipts)
            try {
                require(receiptBytes.size in 1..MAX_RECEIPT_BYTES) { "FBS2v8 receipts exceed budget" }
                val total = HEADER_BYTES.toLong() + inner.size.toLong() + receiptBytes.size.toLong()
                require(total <= MAX_BYTES.toLong()) { "FBS2v8 exceeds plaintext budget" }
                return ByteArrayOutputStream(total.toInt()).use { buffer ->
                    DataOutputStream(buffer).use { output ->
                        output.writeInt(MAGIC)
                        output.writeInt(VERSION)
                        output.writeInt(inner.size)
                        output.writeInt(receiptBytes.size)
                        output.write(sha256(inner))
                        output.write(sha256(receiptBytes))
                        output.write(inner)
                        output.write(receiptBytes)
                        output.flush()
                    }
                    buffer.toByteArray()
                }
            } finally {
                receiptBytes.fill(0)
            }
        } finally {
            inner.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household11Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 2 * MIN_SECTION_BYTES)..MAX_BYTES) {
            "FBS2v8 total length invalid"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                "FBS2v8 magic/version invalid"
            }
            val baseSize = input.readInt()
            val receiptsSize = input.readInt()
            require(baseSize >= Int.SIZE_BYTES * 2 &&
                receiptsSize in 1..MAX_RECEIPT_BYTES &&
                HEADER_BYTES.toLong() + baseSize.toLong() + receiptsSize.toLong() == bytes.size.toLong()) {
                "FBS2v8 malformed section lengths"
            }
            val expectedBaseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val expectedReceiptDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val baseBytes = ByteArray(baseSize)
            try {
                input.readFully(baseBytes)
                val receiptBytes = ByteArray(receiptsSize)
                try {
                    input.readFully(receiptBytes)
                    require(input.available() == 0) { "FBS2v8 trailing bytes" }
                    require(MessageDigest.isEqual(expectedBaseDigest, sha256(baseBytes)) &&
                        MessageDigest.isEqual(expectedReceiptDigest, sha256(receiptBytes))) {
                        "FBS2v8 section digest mismatch"
                    }
                    val nested = DataInputStream(ByteArrayInputStream(baseBytes)).use { innerHeader ->
                        require(innerHeader.readInt() == MAGIC) { "FBS2v8 inner magic invalid" }
                        when (innerHeader.readInt()) {
                            INNER_V6 -> FaroV5Household10Fbs2Codec.decode(baseBytes)
                            INNER_V7 -> FaroV5Household10Fbs2Codec.decodeEvidence2(baseBytes)
                            else -> throw IllegalArgumentException("FBS2v8 unsupported nested base version")
                        }
                    }
                    FaroV5Household11Fbs2Snapshot(
                        base = nested,
                        receipts = FaroV5Household11ReceiptBackupSectionCodec.decode(receiptBytes),
                    )
                } finally {
                    receiptBytes.fill(0)
                }
            } finally {
                baseBytes.fill(0)
                expectedBaseDigest.fill(0)
                expectedReceiptDigest.fill(0)
            }
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
