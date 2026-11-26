package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest

internal enum class Household14ReservationEventType { RESERVE, RELEASE }

internal data class Household14ReservationBackupRow(
    val eventId: String,
    val intentKey: String,
    val type: Household14ReservationEventType,
    val reservationRef: String,
    val stockItemRef: String,
    val unit: String,
    val quantityThousandths: Int,
    val principal: String,
    val recordedAtMs: Long,
    val revision: Long,
)

internal data class FaroV5Household14Fbs2Snapshot(
    val base: FaroV5Household13Fbs2Snapshot,
    val reservations: List<Household14ReservationBackupRow>,
)

/** FBS2v11 wraps the unchanged, canonical FBS2v10 and the SQL14 reservation ledger. */
internal object FaroV5Household14Fbs2Codec {
    private const val MAGIC = 0x46425332
    private const val VERSION = 11
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + 2 * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MAX_RESERVATION_BYTES = 4 * 1024 * 1024
    private const val MAX_ROWS = 8_192
    private const val MAX_REFERENCE_BYTES = 384
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")

    fun encode(snapshot: FaroV5Household14Fbs2Snapshot): ByteArray {
        require(snapshot.reservations.size <= MAX_ROWS)
        val base = FaroV5Household13Fbs2Codec.encode(snapshot.base)
        try {
            val reservations = snapshot.reservations.toList()
            validate(FaroV5Household13Fbs2Codec.decode(base), reservations)
            val ledger = encodeReservations(reservations)
            try {
                val total = HEADER_BYTES + base.size.toLong() + ledger.size
                require(total <= MAX_BYTES) { "FBS2v11 exceeds plaintext budget" }
                val buffer = ByteArrayOutputStream(total.toInt())
                DataOutputStream(buffer).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeInt(base.size)
                    output.writeInt(ledger.size)
                    output.write(digest(base))
                    output.write(digest(ledger))
                    output.write(base)
                    output.write(ledger)
                }
                return buffer.toByteArray()
            } finally {
                ledger.fill(0)
            }
        } finally {
            base.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household14Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 3 * Int.SIZE_BYTES)..MAX_BYTES)
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION) { "Invalid FBS2v11 magic/version" }
                val baseSize = input.readInt()
                val ledgerSize = input.readInt()
                require(baseSize >= 2 * Int.SIZE_BYTES && ledgerSize in Int.SIZE_BYTES..MAX_RESERVATION_BYTES &&
                    HEADER_BYTES + baseSize.toLong() + ledgerSize == bytes.size.toLong()) {
                    "Invalid FBS2v11 section lengths"
                }
                val baseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val ledgerDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val base = ByteArray(baseSize)
                val ledger = ByteArray(ledgerSize)
                try {
                    input.readFully(base)
                    input.readFully(ledger)
                    require(MessageDigest.isEqual(baseDigest, digest(base)) &&
                        MessageDigest.isEqual(ledgerDigest, digest(ledger))) { "FBS2v11 section digest mismatch" }
                    val historical = FaroV5Household13Fbs2Codec.decode(base)
                    val canonical = FaroV5Household13Fbs2Codec.encode(historical)
                    try {
                        require(base.contentEquals(canonical)) { "FBS2v11 contains a noncanonical FBS2v10 section" }
                    } finally {
                        canonical.fill(0)
                    }
                    val reservations = decodeReservations(ledger)
                    validate(historical, reservations)
                    FaroV5Household14Fbs2Snapshot(historical, reservations)
                } finally {
                    base.fill(0)
                    ledger.fill(0)
                }
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("Truncated FBS2v11 section", failure)
        }
    }

    private fun encodeReservations(rows: List<Household14ReservationBackupRow>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(rows.size)
            for (row in rows) {
                output.writeText(row.eventId)
                output.writeText(row.intentKey)
                output.writeByte(if (row.type == Household14ReservationEventType.RESERVE) 1 else 2)
                output.writeText(row.reservationRef)
                output.writeText(row.stockItemRef)
                output.writeText(row.unit)
                output.writeInt(row.quantityThousandths)
                output.writeText(row.principal)
                output.writeLong(row.recordedAtMs)
                output.writeLong(row.revision)
                require(buffer.size() <= MAX_RESERVATION_BYTES) { "FBS2v11 reservation section exceeds budget" }
            }
        }
        return buffer.toByteArray()
    }

    private fun decodeReservations(bytes: ByteArray): List<Household14ReservationBackupRow> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_ROWS) { "Invalid FBS2v11 reservation count" }
            List(count) {
                val eventId = input.readText()
                val intentKey = input.readText()
                val type = when (input.readUnsignedByte()) {
                    1 -> Household14ReservationEventType.RESERVE
                    2 -> Household14ReservationEventType.RELEASE
                    else -> throw IllegalArgumentException("Invalid FBS2v11 reservation event type")
                }
                Household14ReservationBackupRow(
                    eventId, intentKey, type, input.readText(), input.readText(), input.readText(16),
                    input.readInt(), input.readText(), input.readLong(), input.readLong(),
                )
            }.also { require(input.available() == 0) { "FBS2v11 reservation section has trailing bytes" } }
        }

    private data class StockKey(val item: String, val unit: String, val principal: String)
    private data class PhysicalDelta(val revision: Long, val key: StockKey, val quantity: Long)
    private data class Reservation(val origin: Household14ReservationBackupRow, var remaining: Long)

    private fun validate(base: FaroV5Household13Fbs2Snapshot, rows: List<Household14ReservationBackupRow>) {
        require(rows.size <= MAX_ROWS)
        if (rows.isEmpty()) return
        val purchases = base.purchases.associateBy { it.purchaseRef }
        val receipts = base.base.receipts.receipts
        val physicalRevisions = receipts.mapTo(HashSet()) { it.revision }
        base.compensations.mapTo(physicalRevisions) { it.revision }
        val physicalEvents = buildList {
            for (receipt in receipts) {
                val purchase = purchases[receipt.purchaseRef] ?: continue
                add(PhysicalDelta(receipt.revision, purchase.key(), receipt.quantityThousandths.toLong()))
            }
            for (compensation in base.compensations) {
                val purchase = purchases.getValue(compensation.purchaseRef)
                add(PhysicalDelta(compensation.revision, purchase.key(), -compensation.quantityThousandths.toLong()))
            }
        }.sortedBy { it.revision }

        val ids = HashSet<String>()
        val intents = HashSet<String>()
        val physical = mutableMapOf<StockKey, Long>()
        val reserved = mutableMapOf<StockKey, Long>()
        val reservations = mutableMapOf<String, Reservation>()
        var previousRevision = 0L
        var physicalIndex = 0

        fun applyPhysical(delta: PhysicalDelta) {
            val net = physical.getOrDefault(delta.key, 0L) + delta.quantity
            require(net >= reserved.getOrDefault(delta.key, 0L)) { "FBS2v11 physical stock below outstanding reservations" }
            physical[delta.key] = net
        }

        for (row in rows) {
            listOf(row.eventId, row.intentKey, row.reservationRef, row.stockItemRef, row.principal).forEach(::requireReference)
            require(row.revision > previousRevision && row.revision !in physicalRevisions &&
                ids.add(row.eventId) && intents.add(row.intentKey)) { "FBS2v11 duplicate or unordered reservation event" }
            require(row.unit in units && row.quantityThousandths > 0 && row.recordedAtMs >= 0) {
                "Invalid FBS2v11 reservation row"
            }
            previousRevision = row.revision
            while (physicalIndex < physicalEvents.size && physicalEvents[physicalIndex].revision < row.revision) {
                applyPhysical(physicalEvents[physicalIndex++])
            }

            val key = row.key()
            val amount = row.quantityThousandths.toLong()
            val prior = reserved.getOrDefault(key, 0L)
            when (row.type) {
                Household14ReservationEventType.RESERVE -> {
                    require(row.reservationRef == row.eventId && prior + amount <= physical.getOrDefault(key, 0L)) {
                        "FBS2v11 reservation exceeds received stock or has an invalid reference"
                    }
                    reservations[row.eventId] = Reservation(row, amount)
                    reserved[key] = prior + amount
                }
                Household14ReservationEventType.RELEASE -> {
                    val reservation = requireNotNull(reservations[row.reservationRef]) { "FBS2v11 release origin missing" }
                    require(reservation.origin.key() == key && row.recordedAtMs >= reservation.origin.recordedAtMs &&
                        amount <= reservation.remaining) { "FBS2v11 release differs from its reservation" }
                    reservation.remaining -= amount
                    reserved[key] = prior - amount
                }
            }
        }
        while (physicalIndex < physicalEvents.size) applyPhysical(physicalEvents[physicalIndex++])
    }

    private fun Household12PurchaseBackupRow.key() = StockKey(stockItemRef, unit, confirmedBy)
    private fun Household14ReservationBackupRow.key() = StockKey(stockItemRef, unit, principal)

    private fun requireReference(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(value.codePointCount(0, value.length) in 1..128 && encoded.size <= MAX_REFERENCE_BYTES &&
            value.none { it <= '\u0020' || it == '\u007f' } && encoded.toString(Charsets.UTF_8) == value) {
            "Invalid FBS2v11 reservation reference"
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(limit: Int = MAX_REFERENCE_BYTES): String {
        val size = readUnsignedShort()
        require(size in 1..limit && size <= available()) { "Invalid FBS2v11 reservation text length" }
        val encoded = ByteArray(size).also(::readFully)
        return encoded.toString(Charsets.UTF_8).also {
            require(it.toByteArray(Charsets.UTF_8).contentEquals(encoded)) { "Invalid FBS2v11 reservation UTF-8" }
        }
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
