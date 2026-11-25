package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest

internal data class Household12PurchaseBackupRow(
    val purchaseRef: String,
    val stockItemRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val confirmedBy: String,
    val confirmedAtMs: Long,
    val provenance: String,
    val revision: Long,
)

internal data class FaroV5Household12Fbs2Snapshot(
    val base: FaroV5Household11Fbs2Snapshot,
    val purchases: List<Household12PurchaseBackupRow>,
)

/** FBS2v9 contains an unchanged v8 payload and a bounded Reality12 purchase section. */
internal object FaroV5Household12Fbs2Codec {
    private const val MAGIC = 0x46425332 // FBS2
    private const val VERSION = 9
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + 2 * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    const val MAX_PURCHASE_BYTES = 4 * 1024 * 1024
    private const val MAX_ROWS = 8_192
    private const val REF_MAX_CHARS = 128
    private const val REF_MAX_BYTES = 4 * REF_MAX_CHARS
    private const val MANUAL = "MANUAL_EXPLICIT"
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")

    fun encode(snapshot: FaroV5Household12Fbs2Snapshot): ByteArray {
        require(snapshot.purchases.size <= MAX_ROWS) { "FBS2v9 purchase count outside budget" }
        val purchases = snapshot.purchases.toList()
        validate(purchases, snapshot.base.receipts)
        val baseBytes = FaroV5Household11Fbs2Codec.encode(snapshot.base)
        try {
            val purchaseBytes = encodePurchases(purchases)
            try {
                val total = HEADER_BYTES.toLong() + baseBytes.size + purchaseBytes.size
                require(total <= MAX_BYTES) { "FBS2v9 exceeds plaintext budget" }
                val buffer = ByteArrayOutputStream(total.toInt())
                DataOutputStream(buffer).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeInt(baseBytes.size)
                    output.writeInt(purchaseBytes.size)
                    output.write(sha256(baseBytes))
                    output.write(sha256(purchaseBytes))
                    output.write(baseBytes)
                    output.write(purchaseBytes)
                }
                return buffer.toByteArray()
            } finally {
                purchaseBytes.fill(0)
            }
        } finally {
            baseBytes.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household12Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 3 * Int.SIZE_BYTES)..MAX_BYTES) {
            "FBS2v9 size outside plaintext budget"
        }
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                    "FBS2v9 magic/version invalid"
                }
                val baseSize = input.readInt()
                val purchaseSize = input.readInt()
                require(baseSize >= 2 * Int.SIZE_BYTES &&
                    purchaseSize in Int.SIZE_BYTES..MAX_PURCHASE_BYTES &&
                    HEADER_BYTES.toLong() + baseSize + purchaseSize == bytes.size.toLong()) {
                    "FBS2v9 malformed section lengths"
                }
                val baseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val purchaseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val baseBytes = ByteArray(baseSize)
                val purchaseBytes = ByteArray(purchaseSize)
                try {
                    input.readFully(baseBytes)
                    input.readFully(purchaseBytes)
                    require(MessageDigest.isEqual(baseDigest, sha256(baseBytes)) &&
                        MessageDigest.isEqual(purchaseDigest, sha256(purchaseBytes))) {
                        "FBS2v9 section digest mismatch"
                    }
                    val base = FaroV5Household11Fbs2Codec.decode(baseBytes)
                    val canonicalBase = FaroV5Household11Fbs2Codec.encode(base)
                    try {
                        require(baseBytes.contentEquals(canonicalBase)) { "FBS2v9 noncanonical v8 base" }
                    } finally {
                        canonicalBase.fill(0)
                    }
                    val purchases = decodePurchases(purchaseBytes)
                    validate(purchases, base.receipts)
                    FaroV5Household12Fbs2Snapshot(base, purchases)
                } finally {
                    baseBytes.fill(0)
                    purchaseBytes.fill(0)
                }
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("FBS2v9 truncated section", failure)
        }
    }

    fun encodePurchases(purchases: List<Household12PurchaseBackupRow>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(purchases.size)
            for (purchase in purchases) {
                output.writeText(purchase.purchaseRef)
                output.writeText(purchase.stockItemRef)
                output.writeInt(purchase.quantityThousandths)
                output.writeText(purchase.unit)
                output.writeText(purchase.confirmedBy)
                output.writeLong(purchase.confirmedAtMs)
                output.writeText(purchase.provenance)
                output.writeLong(purchase.revision)
                require(buffer.size() <= MAX_PURCHASE_BYTES) { "FBS2v9 purchase section exceeds budget" }
            }
        }
        return buffer.toByteArray()
    }

    fun decodePurchases(bytes: ByteArray): List<Household12PurchaseBackupRow> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_ROWS) { "FBS2v9 purchase count outside budget" }
            List(count) {
                Household12PurchaseBackupRow(
                    purchaseRef = input.readText(REF_MAX_BYTES),
                    stockItemRef = input.readText(REF_MAX_BYTES),
                    quantityThousandths = input.readInt(),
                    unit = input.readText(16),
                    confirmedBy = input.readText(REF_MAX_BYTES),
                    confirmedAtMs = input.readLong(),
                    provenance = input.readText(MANUAL.length),
                    revision = input.readLong(),
                )
            }.also {
                require(input.available() == 0) { "FBS2v9 purchase section has trailing bytes" }
            }
        }

    private fun validate(
        purchases: List<Household12PurchaseBackupRow>,
        receipts: Household11ReceiptBackupSection,
    ) {
        validatePurchases(purchases)
        val byReference = purchases.associateBy { it.purchaseRef }
        val receivedByPurchase = mutableMapOf<String, Long>()
        for (receipt in receipts.receipts) {
            val purchase = byReference[receipt.purchaseRef] ?: continue
            require(receipt.stockItemRef == purchase.stockItemRef && receipt.unit == purchase.unit) {
                "FBS2v9 receipt differs from confirmed purchase item or unit"
            }
            val received = receivedByPurchase.getOrDefault(purchase.purchaseRef, 0L) +
                receipt.quantityThousandths
            require(received <= purchase.quantityThousandths.toLong()) {
                "FBS2v9 receipts exceed confirmed purchase quantity"
            }
            receivedByPurchase[purchase.purchaseRef] = received
        }
    }

    fun validatePurchases(purchases: List<Household12PurchaseBackupRow>) {
        require(purchases.size <= MAX_ROWS) { "FBS2v9 purchase count outside budget" }
        var previous: String? = null
        for (purchase in purchases) {
            require(previous == null || previous < purchase.purchaseRef) {
                "FBS2v9 purchase references must be ordered and unique"
            }
            requireReference(purchase.purchaseRef)
            requireReference(purchase.stockItemRef)
            requireReference(purchase.confirmedBy)
            require(purchase.quantityThousandths > 0 && purchase.unit in units &&
                purchase.confirmedAtMs >= 0 && purchase.revision > 0 && purchase.provenance == MANUAL) {
                "FBS2v9 purchase fields invalid"
            }
            previous = purchase.purchaseRef
        }
    }

    private fun requireReference(value: String) {
        require(value.codePointCount(0, value.length) in 1..REF_MAX_CHARS &&
            value == value.trim() && '\u0000' !in value) { "FBS2v9 reference invalid" }
    }

    private fun DataOutputStream.writeText(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.toString(Charsets.UTF_8) == value) { "FBS2v9 text cannot be encoded as UTF-8" }
        writeShort(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readText(maxBytes: Int): String {
        val size = readUnsignedShort()
        require(size in 1..maxBytes && size <= available()) { "FBS2v9 text length invalid" }
        val encoded = ByteArray(size).also(::readFully)
        val value = encoded.toString(Charsets.UTF_8)
        require(value.toByteArray(Charsets.UTF_8).contentEquals(encoded)) { "FBS2v9 text is invalid UTF-8" }
        return value
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
