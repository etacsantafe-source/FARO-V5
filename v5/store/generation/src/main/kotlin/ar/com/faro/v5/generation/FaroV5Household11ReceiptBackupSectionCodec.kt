package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.time.LocalDate

/** Semantic receipt-only snapshot for a FUTURE authenticated Reality11 archive. */
internal data class Household11ReceiptBackupRow(
    val receiptId: String,
    val intentKey: String,
    val purchaseRef: String,
    val stockItemRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val receivedEpochDay: Long,
    val recordedAtMs: Long,
    val revision: Long,
)

internal data class Household11ReceiptBackupSection(
    val revision: Long,
    val receipts: List<Household11ReceiptBackupRow>,
)

/**
 * W05-A1B1: bounded and canonical portable bytes ONLY.
 * Does NOT seal/restore FBS2, write SQL, Apply receipts or activate Reality11.
 */
internal object FaroV5Household11ReceiptBackupSectionCodec {
    private const val MAGIC = 0x48313152 // H11R, distinct from Household10 H10S
    private const val FORMAT = 1
    private const val SCHEMA = 11
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + Long.SIZE_BYTES
    private const val MAX_ROWS = 8_192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val REF_MAX_CHARS = 128
    private const val REF_MAX_BYTES = 384
    private const val UNIT_MAX_BYTES = 16
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")
    private val dayRange =
        LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()

    fun encode(section: Household11ReceiptBackupSection): ByteArray {
        requireCanonical(section)
        val result = ByteArrayOutputStream()
        DataOutputStream(result).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT)
            output.writeInt(SCHEMA)
            output.writeLong(section.revision)
            output.writeInt(section.receipts.size)
            for (row in section.receipts) {
                output.writeText(row.receiptId, REF_MAX_BYTES)
                output.writeText(row.intentKey, REF_MAX_BYTES)
                output.writeText(row.purchaseRef, REF_MAX_BYTES)
                output.writeText(row.stockItemRef, REF_MAX_BYTES)
                output.writeInt(row.quantityThousandths)
                output.writeText(row.unit, UNIT_MAX_BYTES)
                output.writeLong(row.receivedEpochDay)
                output.writeLong(row.recordedAtMs)
                output.writeLong(row.revision)
                require(result.size() <= MAX_BYTES) { "Reality11 receipt section exceeds byte budget" }
            }
        }
        return result.toByteArray()
    }

    fun decode(bytes: ByteArray): Household11ReceiptBackupSection {
        require(bytes.size in HEADER_BYTES..MAX_BYTES) {
            "Reality11 receipt section size outside bounded envelope"
        }
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC &&
                    input.readInt() == FORMAT && input.readInt() == SCHEMA) {
                    "Reality11 receipt section header invalid"
                }
                val revision = input.readLong()
                val count = input.readInt()
                require(revision >= 0L && count in 0..MAX_ROWS) {
                    "Reality11 receipt section revision/count invalid"
                }
                val rows = List(count) {
                    Household11ReceiptBackupRow(
                        receiptId = input.readText(REF_MAX_BYTES),
                        intentKey = input.readText(REF_MAX_BYTES),
                        purchaseRef = input.readText(REF_MAX_BYTES),
                        stockItemRef = input.readText(REF_MAX_BYTES),
                        quantityThousandths = input.readInt(),
                        unit = input.readText(UNIT_MAX_BYTES),
                        receivedEpochDay = input.readLong(),
                        recordedAtMs = input.readLong(),
                        revision = input.readLong(),
                    )
                }
                require(input.available() == 0) { "Reality11 receipt section has trailing bytes" }
                val section = Household11ReceiptBackupSection(revision, rows)
                requireCanonical(section)
                require(encode(section).contentEquals(bytes)) {
                    "Reality11 receipt section is not canonically encoded"
                }
                section
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("Reality11 receipt section truncated", failure)
        }
    }

    private fun requireCanonical(section: Household11ReceiptBackupSection) {
        require(section.revision >= 0L && section.receipts.size <= MAX_ROWS) {
            "Reality11 receipt section revision/count outside limits"
        }
        var previous: String? = null
        val intents = HashSet<String>()
        for (row in section.receipts) {
            for (ref in listOf(row.receiptId, row.intentKey, row.purchaseRef, row.stockItemRef)) {
                val raw = ref.toByteArray(Charsets.UTF_8)
                require(ref.length in 1..REF_MAX_CHARS && ref.trim().isNotEmpty() &&
                    raw.size in 1..REF_MAX_BYTES && raw.toString(Charsets.UTF_8) == ref) {
                    "Reality11 receipt reference is invalid or overlong UTF-8"
                }
            }
            require(previous == null || previous < row.receiptId) {
                "Reality11 receipts must have strictly ordered unique IDs"
            }
            require(intents.add(row.intentKey)) { "Reality11 receipt intents must be unique" }
            require(row.quantityThousandths > 0 && row.unit in units) {
                "Reality11 receipt quantity/unit invalid"
            }
            require(row.receivedEpochDay in dayRange && row.recordedAtMs >= 0L) {
                "Reality11 receipt date/time invalid"
            }
            require(row.revision in 1L..section.revision) {
                "Reality11 receipt row is newer than snapshot or lacks positive revision"
            }
            previous = row.receiptId
        }
    }

    private fun DataOutputStream.writeText(value: String, max: Int) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size in 1..max && encoded.toString(Charsets.UTF_8) == value) {
            "Reality11 receipt text cannot be encoded as bounded UTF-8"
        }
        writeShort(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readText(max: Int): String {
        val size = readUnsignedShort()
        require(size in 1..max && size <= available()) {
            "Reality11 receipt UTF-8 length invalid"
        }
        val bytes = ByteArray(size).also(::readFully)
        val value = bytes.toString(Charsets.UTF_8)
        require(value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
            "Reality11 receipt UTF-8 is invalid"
        }
        return value
    }
}
