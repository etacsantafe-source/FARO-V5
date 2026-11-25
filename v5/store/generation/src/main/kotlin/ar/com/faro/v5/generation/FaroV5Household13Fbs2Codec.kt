package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest

internal data class Household13CompensationBackupRow(
    val compensationId: String,
    val intentKey: String,
    val receiptId: String,
    val purchaseRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val principal: String,
    val recordedAtMs: Long,
    val revision: Long,
)

internal data class FaroV5Household13Fbs2Snapshot(
    val base: FaroV5Household11Fbs2Snapshot,
    val purchases: List<Household12PurchaseBackupRow>,
    val compensations: List<Household13CompensationBackupRow>,
)

/** Three bounded sections: unchanged FBS2v8, purchases and the compensating ledger events. */
internal object FaroV5Household13Fbs2Codec {
    private const val MAGIC = 0x46425332
    private const val VERSION = 10
    private const val SECTION_COUNT = 3
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = (2 + SECTION_COUNT) * Int.SIZE_BYTES + SECTION_COUNT * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MAX_COMPENSATION_BYTES = 4 * 1024 * 1024
    private const val MAX_ROWS = 8_192
    private const val MAX_REF_CHARS = 128
    private const val MAX_REF_BYTES = MAX_REF_CHARS * 4
    private val purchaseCodec = FaroV5Household12Fbs2Codec

    fun encode(snapshot: FaroV5Household13Fbs2Snapshot): ByteArray {
        require(snapshot.purchases.size <= MAX_ROWS && snapshot.compensations.size <= MAX_ROWS)
        val observed = snapshot.base.receipts
        require(observed.receipts.size <= MAX_ROWS)
        val base = snapshot.base.copy(receipts = observed.copy(receipts = observed.receipts.toList()))
        val purchases = snapshot.purchases.toList()
        val compensations = snapshot.compensations.toList()
        validate(base.receipts, purchases, compensations)
        val sections = ArrayList<ByteArray>(SECTION_COUNT)
        try {
            sections += FaroV5Household11Fbs2Codec.encode(base)
            sections += purchaseCodec.encodePurchases(purchases)
            sections += encodeCompensations(compensations)
            val total = HEADER_BYTES + sections.sumOf { it.size.toLong() }
            require(total <= MAX_BYTES) { "FBS2v10 exceeds plaintext budget" }
            val buffer = ByteArrayOutputStream(total.toInt())
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                sections.forEach { output.writeInt(it.size) }
                sections.forEach { output.write(digest(it)) }
                sections.forEach { output.write(it) }
            }
            return buffer.toByteArray()
        } finally {
            sections.forEach { it.fill(0) }
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household13Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 4 * Int.SIZE_BYTES)..MAX_BYTES) {
            "FBS2v10 size outside plaintext budget"
        }
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION) { "FBS2v10 magic/version invalid" }
                val sizes = IntArray(SECTION_COUNT) { input.readInt() }
                require(sizes[0] >= 2 * Int.SIZE_BYTES &&
                    sizes[1] in Int.SIZE_BYTES..FaroV5Household12Fbs2Codec.MAX_PURCHASE_BYTES &&
                    sizes[2] in Int.SIZE_BYTES..MAX_COMPENSATION_BYTES &&
                    HEADER_BYTES + sizes.sumOf { it.toLong() } == bytes.size.toLong()) {
                    "FBS2v10 malformed section lengths"
                }
                val digests = Array(SECTION_COUNT) { ByteArray(DIGEST_BYTES).also(input::readFully) }
                val sections = Array(SECTION_COUNT) { ByteArray(sizes[it]) }
                try {
                    sections.forEachIndexed { index, section ->
                        input.readFully(section)
                        require(MessageDigest.isEqual(digests[index], digest(section))) {
                            "FBS2v10 section digest mismatch"
                        }
                    }
                    val base = FaroV5Household11Fbs2Codec.decode(sections[0])
                    val canonicalBase = FaroV5Household11Fbs2Codec.encode(base)
                    try {
                        require(sections[0].contentEquals(canonicalBase)) { "FBS2v10 noncanonical v8 section" }
                    } finally {
                        canonicalBase.fill(0)
                    }
                    val purchases = purchaseCodec.decodePurchases(sections[1])
                    val compensations = decodeCompensations(sections[2])
                    validate(base.receipts, purchases, compensations)
                    FaroV5Household13Fbs2Snapshot(base, purchases, compensations)
                } finally {
                    sections.forEach { it.fill(0) }
                }
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("FBS2v10 truncated section", failure)
        }
    }

    private fun encodeCompensations(rows: List<Household13CompensationBackupRow>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(rows.size)
            for (row in rows) {
                output.writeText(row.compensationId)
                output.writeText(row.intentKey)
                output.writeText(row.receiptId)
                output.writeText(row.purchaseRef)
                output.writeInt(row.quantityThousandths)
                output.writeText(row.unit)
                output.writeText(row.principal)
                output.writeLong(row.recordedAtMs)
                output.writeLong(row.revision)
                require(buffer.size() <= MAX_COMPENSATION_BYTES) { "FBS2v10 compensation section exceeds budget" }
            }
        }
        return buffer.toByteArray()
    }

    private fun decodeCompensations(bytes: ByteArray): List<Household13CompensationBackupRow> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_ROWS) { "FBS2v10 compensation count outside budget" }
            List(count) {
                Household13CompensationBackupRow(
                    input.readText(MAX_REF_BYTES), input.readText(MAX_REF_BYTES), input.readText(MAX_REF_BYTES),
                    input.readText(MAX_REF_BYTES), input.readInt(), input.readText(16), input.readText(MAX_REF_BYTES),
                    input.readLong(), input.readLong(),
                )
            }.also { require(input.available() == 0) { "FBS2v10 compensation section has trailing bytes" } }
        }

    private data class LedgerDelta(val revision: Long, val purchaseRef: String, val amount: Long)

    private fun validate(
        ledger: Household11ReceiptBackupSection,
        purchases: List<Household12PurchaseBackupRow>,
        compensation: List<Household13CompensationBackupRow>,
    ) {
        require(compensation.size <= MAX_ROWS)
        purchaseCodec.validatePurchases(purchases)
        val purchaseByRef = purchases.associateBy { it.purchaseRef }
        val receiptById = ledger.receipts.associateBy { it.receiptId }
        val receiptRevisions = ledger.receipts.mapTo(HashSet()) { it.revision }
        val undoRevisions = HashSet<Long>()
        val undoIntents = HashSet<String>()
        val reversed = HashSet<String>()
        val events = mutableListOf<LedgerDelta>()
        for (receipt in ledger.receipts) {
            val purchase = purchaseByRef[receipt.purchaseRef] ?: continue
            require(receipt.stockItemRef == purchase.stockItemRef && receipt.unit == purchase.unit) {
                "FBS2v10 receipt differs from confirmed purchase"
            }
            events += LedgerDelta(receipt.revision, receipt.purchaseRef, receipt.quantityThousandths.toLong())
        }

        var previous: String? = null
        for (row in compensation) {
            require(previous == null || previous < row.compensationId) {
                "FBS2v10 compensation IDs must be ordered and unique"
            }
            previous = row.compensationId
            listOf(row.compensationId, row.intentKey, row.receiptId, row.purchaseRef, row.principal).forEach(::requireRef)
            require(undoIntents.add(row.intentKey) && reversed.add(row.receiptId) &&
                undoRevisions.add(row.revision) && row.revision !in receiptRevisions &&
                row.revision in 1L..ledger.revision) { "FBS2v10 duplicate compensation or invalid revision" }
            val original = requireNotNull(receiptById[row.receiptId]) { "FBS2v10 compensation receipt missing" }
            val purchase = requireNotNull(purchaseByRef[row.purchaseRef]) { "FBS2v10 compensation purchase missing" }
            require(original.purchaseRef == purchase.purchaseRef && row.principal == purchase.confirmedBy &&
                row.unit == original.unit && row.quantityThousandths == original.quantityThousandths &&
                row.recordedAtMs >= original.recordedAtMs && row.revision > original.revision) {
                "FBS2v10 compensation differs from confirmed original receipt"
            }
            events += LedgerDelta(row.revision, row.purchaseRef, -row.quantityThousandths.toLong())
        }

        val received = mutableMapOf<String, Long>()
        for (event in events.sortedBy { it.revision }) {
            val net = received.getOrDefault(event.purchaseRef, 0L) + event.amount
            require(net in 0L..purchaseByRef.getValue(event.purchaseRef).quantityThousandths.toLong()) {
                "FBS2v10 receipt history exceeds confirmed purchase or reverses unreceived stock"
            }
            received[event.purchaseRef] = net
        }
    }

    private fun requireRef(value: String) {
        require(value.codePointCount(0, value.length) in 1..MAX_REF_CHARS &&
            value.trim() == value && '\u0000' !in value &&
            value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value) {
            "FBS2v10 compensation reference invalid"
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(limit: Int): String {
        val size = readUnsignedShort()
        require(size in 1..limit && size <= available()) { "FBS2v10 text length invalid" }
        val encoded = ByteArray(size).also(::readFully)
        return encoded.toString(Charsets.UTF_8).also {
            require(it.toByteArray(Charsets.UTF_8).contentEquals(encoded)) { "FBS2v10 invalid UTF-8" }
        }
    }

    private fun digest(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
}
