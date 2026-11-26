package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest

internal enum class Household15ConsumptionEventType { CONSUME, CONSUME_UNDO }

internal data class Household15ConsumptionBackupRow(
    val eventId: String,
    val intentKey: String,
    val type: Household15ConsumptionEventType,
    val consumptionRef: String,
    val stockItemRef: String,
    val unit: String,
    val quantityThousandths: Int,
    val principal: String,
    val sourceRef: String,
    val reservationRef: String?,
    val occurredAtMs: Long,
    val recordedAtMs: Long,
    val revision: Long,
)

internal data class FaroV5Household15Fbs2Snapshot(
    val base: FaroV5Household14Fbs2Snapshot,
    val consumptions: List<Household15ConsumptionBackupRow>,
)

/** FBS2v12: immutable canonical FBS2v11 + SQL15 events. No SQL15 activation or Apply. */
internal object FaroV5Household15Fbs2Codec {
    private const val MAGIC = 0x46425332
    private const val VERSION = 12
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + 2 * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MAX_CONSUMPTION_BYTES = 4 * 1024 * 1024
    private const val MAX_ROWS = 8_192
    private const val MAX_REF_BYTES = 384
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")

    fun encode(snapshot: FaroV5Household15Fbs2Snapshot): ByteArray {
        require(snapshot.consumptions.size <= MAX_ROWS) { "FBS2v12 too many consumption events" }
        val historical = FaroV5Household14Fbs2Codec.encode(snapshot.base)
        try {
            validate(snapshot)
            val ledger = encodeEvents(snapshot.consumptions)
            try {
                val total = HEADER_BYTES.toLong() + historical.size + ledger.size
                require(total <= MAX_BYTES) { "FBS2v12 plaintext budget exceeded" }
                return ByteArrayOutputStream(total.toInt()).use { buffer ->
                    DataOutputStream(buffer).use { output ->
                        output.writeInt(MAGIC)
                        output.writeInt(VERSION)
                        output.writeInt(historical.size)
                        output.writeInt(ledger.size)
                        output.write(digest(historical))
                        output.write(digest(ledger))
                        output.write(historical)
                        output.write(ledger)
                    }
                    buffer.toByteArray()
                }
            } finally {
                ledger.fill(0)
            }
        } finally {
            historical.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Household15Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 3 * Int.SIZE_BYTES)..MAX_BYTES) {
            "FBS2v12 size exceeds bounded format"
        }
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                    "FBS2v12 magic/version mismatch"
                }
                val historicalSize = input.readInt()
                val ledgerSize = input.readInt()
                require(historicalSize >= 2 * Int.SIZE_BYTES &&
                    ledgerSize in Int.SIZE_BYTES..MAX_CONSUMPTION_BYTES &&
                    HEADER_BYTES.toLong() + historicalSize.toLong() + ledgerSize.toLong() == bytes.size.toLong()) {
                    "FBS2v12 malformed section lengths"
                }
                val historyDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val ledgerDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
                val history = ByteArray(historicalSize)
                val ledger = ByteArray(ledgerSize)
                try {
                    input.readFully(history)
                    input.readFully(ledger)
                    require(MessageDigest.isEqual(historyDigest, digest(history)) &&
                        MessageDigest.isEqual(ledgerDigest, digest(ledger))) {
                        "FBS2v12 section digest mismatch"
                    }
                    val base = FaroV5Household14Fbs2Codec.decode(history)
                    val canonical = FaroV5Household14Fbs2Codec.encode(base)
                    try {
                        require(history.contentEquals(canonical)) { "FBS2v12 nested v11 not canonical" }
                    } finally {
                        canonical.fill(0)
                    }
                    val rows = decodeEvents(ledger)
                    val snapshot = FaroV5Household15Fbs2Snapshot(base, rows)
                    validate(snapshot)
                    val canonicalLedger = encodeEvents(rows)
                    try {
                        require(ledger.contentEquals(canonicalLedger)) { "FBS2v12 ledger not canonical" }
                    } finally {
                        canonicalLedger.fill(0)
                    }
                    snapshot
                } finally {
                    history.fill(0)
                    ledger.fill(0)
                }
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("Truncated FBS2v12 payload", failure)
        }
    }

    private fun encodeEvents(rows: List<Household15ConsumptionBackupRow>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(rows.size)
            for (r in rows) {
                out.writeRef(r.eventId)
                out.writeRef(r.intentKey)
                out.writeByte(if (r.type == Household15ConsumptionEventType.CONSUME) 1 else 2)
                out.writeRef(r.consumptionRef)
                out.writeRef(r.stockItemRef)
                out.writeRef(r.unit)
                out.writeInt(r.quantityThousandths)
                out.writeRef(r.principal)
                out.writeRef(r.sourceRef)
                out.writeByte(if (r.reservationRef == null) 0 else 1)
                if (r.reservationRef != null) out.writeRef(r.reservationRef)
                out.writeLong(r.occurredAtMs)
                out.writeLong(r.recordedAtMs)
                out.writeLong(r.revision)
                require(buffer.size() <= MAX_CONSUMPTION_BYTES) { "FBS2v12 ledger exceeds section budget" }
            }
        }
        return buffer.toByteArray()
    }

    private fun decodeEvents(bytes: ByteArray): List<Household15ConsumptionBackupRow> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_ROWS) { "FBS2v12 consumption count invalid" }
            List(count) {
                val id = input.readRef()
                val intent = input.readRef()
                val type = when (input.readUnsignedByte()) {
                    1 -> Household15ConsumptionEventType.CONSUME
                    2 -> Household15ConsumptionEventType.CONSUME_UNDO
                    else -> throw IllegalArgumentException("FBS2v12 consumption type invalid")
                }
                val ref = input.readRef()
                val item = input.readRef()
                val unit = input.readRef(16)
                val amount = input.readInt()
                val principal = input.readRef()
                val source = input.readRef()
                val reservation = when (input.readUnsignedByte()) {
                    0 -> null
                    1 -> input.readRef()
                    else -> throw IllegalArgumentException("FBS2v12 reservation marker invalid")
                }
                Household15ConsumptionBackupRow(
                    id, intent, type, ref, item, unit, amount, principal, source, reservation,
                    input.readLong(), input.readLong(), input.readLong(),
                )
            }.also { require(input.available() == 0) { "FBS2v12 trailing ledger bytes" } }
        }

    private data class StockKey(val item: String, val unit: String, val principal: String)
    private data class Delta(val revision: Long, val key: StockKey, val amount: Long, val reserved: Boolean = false)
    private fun Household15ConsumptionBackupRow.key() = StockKey(stockItemRef, unit, principal)
    private fun Household14ReservationBackupRow.key() = StockKey(stockItemRef, unit, principal)

    private fun validate(snapshot: FaroV5Household15Fbs2Snapshot) {
        val rows = snapshot.consumptions
        require(rows.size <= MAX_ROWS)
        val purchases = snapshot.base.base.purchases.associateBy { it.purchaseRef }
        val receipts = snapshot.base.base.base.receipts.receipts
        val reversals = snapshot.base.base.compensations
        val reservations = snapshot.base.reservations
        val revisions = HashSet<Long>()
        val ids = HashSet<String>()
        val intents = HashSet<String>()
        val events = mutableListOf<Delta>()
        for (receipt in receipts) {
            revisions.add(receipt.revision)
            ids.add(receipt.receiptId)
            intents.add(receipt.intentKey)
            val purchase = purchases[receipt.purchaseRef] ?: continue
            events += Delta(receipt.revision,
                StockKey(purchase.stockItemRef, purchase.unit, purchase.confirmedBy),
                receipt.quantityThousandths.toLong())
        }
        for (undo in reversals) {
            revisions.add(undo.revision)
            ids.add(undo.compensationId)
            intents.add(undo.intentKey)
            val purchase = purchases[undo.purchaseRef] ?: continue
            events += Delta(undo.revision,
                StockKey(purchase.stockItemRef, purchase.unit, purchase.confirmedBy),
                -undo.quantityThousandths.toLong())
        }
        for (r in reservations) {
            revisions.add(r.revision)
            ids.add(r.eventId)
            intents.add(r.intentKey)
            val amount = r.quantityThousandths.toLong() *
                (if (r.type == Household14ReservationEventType.RESERVE) 1 else -1)
            events += Delta(r.revision, r.key(), amount, reserved = true)
        }
        val origins = mutableMapOf<String, Household15ConsumptionBackupRow>()
        val remaining = mutableMapOf<String, Long>()
        var previousRevision = 0L
        for (r in rows) {
            listOf(r.eventId, r.intentKey, r.consumptionRef, r.stockItemRef,
                r.principal, r.sourceRef).forEach(::requireRef)
            require(r.reservationRef == null) { "FBS2v12 reserved stock consumption awaits A2C1" }
            require(r.unit in units && r.quantityThousandths > 0 &&
                r.occurredAtMs >= 0 && r.recordedAtMs >= r.occurredAtMs) {
                "FBS2v12 invalid unit, amount or timestamps"
            }
            require(r.revision > previousRevision && revisions.add(r.revision) &&
                ids.add(r.eventId) && intents.add(r.intentKey)) {
                "FBS2v12 duplicate ID/intent/revision or unordered event"
            }
            previousRevision = r.revision
            val amount = r.quantityThousandths.toLong()
            when (r.type) {
                Household15ConsumptionEventType.CONSUME -> {
                    require(r.consumptionRef == r.eventId) { "FBS2v12 invalid CONSUME self-reference" }
                    origins[r.eventId] = r
                    remaining[r.eventId] = amount
                    events += Delta(r.revision, r.key(), -amount)
                }
                Household15ConsumptionEventType.CONSUME_UNDO -> {
                    require(r.consumptionRef != r.eventId) { "FBS2v12 invalid Undo self-reference" }
                    val origin = requireNotNull(origins[r.consumptionRef]) {
                        "FBS2v12 missing CONSUME origin"
                    }
                    require(origin.key() == r.key() && origin.sourceRef == r.sourceRef &&
                        origin.reservationRef == r.reservationRef &&
                        r.occurredAtMs >= origin.occurredAtMs &&
                        r.recordedAtMs >= origin.recordedAtMs &&
                        amount <= requireNotNull(remaining[r.consumptionRef])) {
                        "FBS2v12 invalid consumption compensation"
                    }
                    remaining[r.consumptionRef] = remaining.getValue(r.consumptionRef) - amount
                    events += Delta(r.revision, r.key(), amount)
                }
            }
        }
        val physical = mutableMapOf<StockKey, Long>()
        val reserved = mutableMapOf<StockKey, Long>()
        var lastRevision = 0L
        for (event in events.sortedBy { it.revision }) {
            require(event.revision > lastRevision) { "FBS2v12 colliding historical revisions" }
            lastRevision = event.revision
            val key = event.key
            if (event.reserved) {
                reserved[key] = reserved.getOrDefault(key, 0L) + event.amount
            } else {
                physical[key] = physical.getOrDefault(key, 0L) + event.amount
            }
            val p = physical.getOrDefault(key, 0L)
            val s = reserved.getOrDefault(key, 0L)
            require(p >= 0L && s >= 0L && s <= p) {
                "FBS2v12 consumption/reservation chronology breaches physical stock"
            }
        }
    }

    private fun requireRef(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(value.codePointCount(0, value.length) in 1..128 &&
            bytes.size <= MAX_REF_BYTES && value.none { it <= ' ' || it == '\u007f' } &&
            bytes.toString(Charsets.UTF_8) == value) { "FBS2v12 invalid reference" }
    }

    private fun DataOutputStream.writeRef(value: String) {
        requireRef(value)
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readRef(limit: Int = MAX_REF_BYTES): String {
        val size = readUnsignedShort()
        require(size in 1..limit && size <= available()) { "FBS2v12 reference length invalid" }
        val bytes = ByteArray(size).also(::readFully)
        return bytes.toString(Charsets.UTF_8).also {
            require(it.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                "FBS2v12 invalid reference UTF-8"
            }
        }
    }

    private fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
}
