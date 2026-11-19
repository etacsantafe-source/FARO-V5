package ar.com.faro.v5.generation

import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Complete Finance9 data plus the mandatory Auxiliary4 ledger, even when empty. */
internal data class FaroV5Auxiliary4Fbs2Snapshot(
    val base: FaroV5FiveStoreSemanticSet,
    val receipts: List<S4W03NoticeAttentionReceiptV4>,
)

/**
 * FBS2v5 semantic bytes. The legacy FBS2 readers reject this version. Only an
 * authenticated and fenced restore coordinator may use the decoded snapshot.
 */
internal object FaroV5Auxiliary4Fbs2Codec {
    private const val MAGIC = 0x46425332 // FBS2
    private const val VERSION = 5
    private const val DIGEST_BYTES = 32
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + 2 * DIGEST_BYTES
    private const val MAX_BYTES = FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES
    private const val MAX_LEDGER_BYTES = 4 * 1024 * 1024
    private const val MAX_RECEIPTS = 8_192
    private const val MAX_FIELD_BYTES = 192

    fun encode(snapshot: FaroV5Auxiliary4Fbs2Snapshot): ByteArray {
        validate(snapshot)
        val base = FaroV5FiveStoreSemanticCodec.encode(snapshot.base)
        try {
            val ledger = encodeLedger(snapshot.receipts)
            try {
                val length = HEADER_BYTES.toLong() + base.size + ledger.size
                require(length <= MAX_BYTES) { "FBS2v5 exceeds the plaintext budget" }
                val bytes = ByteArrayOutputStream(length.toInt())
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeInt(base.size)
                    output.writeInt(ledger.size)
                    output.write(sha256(base))
                    output.write(sha256(ledger))
                    output.write(base)
                    output.write(ledger)
                }
                return bytes.toByteArray()
            } finally {
                ledger.fill(0)
            }
        } finally {
            base.fill(0)
        }
    }

    fun decode(bytes: ByteArray): FaroV5Auxiliary4Fbs2Snapshot {
        require(bytes.size in (HEADER_BYTES + 1 + Int.SIZE_BYTES)..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                "FBS2v5 magic or version mismatch"
            }
            val baseSize = input.readInt()
            val ledgerSize = input.readInt()
            require(baseSize > 0 && ledgerSize in Int.SIZE_BYTES..MAX_LEDGER_BYTES &&
                HEADER_BYTES.toLong() + baseSize + ledgerSize == bytes.size.toLong()
            ) { "FBS2v5 section lengths invalid" }
            val baseDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val ledgerDigest = ByteArray(DIGEST_BYTES).also(input::readFully)
            val base = ByteArray(baseSize).also(input::readFully)
            try {
                val ledger = ByteArray(ledgerSize).also(input::readFully)
                try {
                    require(MessageDigest.isEqual(baseDigest, sha256(base)) &&
                        MessageDigest.isEqual(ledgerDigest, sha256(ledger))
                    ) { "FBS2v5 section digest mismatch" }
                    FaroV5Auxiliary4Fbs2Snapshot(
                        FaroV5FiveStoreSemanticCodec.decode(base),
                        decodeLedger(ledger),
                    ).also(::validate)
                } finally {
                    ledger.fill(0)
                }
            } finally {
                base.fill(0)
            }
        }
    }

    private fun validate(snapshot: FaroV5Auxiliary4Fbs2Snapshot) {
        val base = snapshot.base
        val auxiliary = base.fourStore.auxiliary
        require(base.finance9 != null && base.planningDomains != null &&
            base.g4Domains != null && auxiliary.schemaVersion == 3 &&
            auxiliary.bookmarks != null
        ) { "FBS2v5 requires the complete Finance9 base and Auxiliary3 projection" }
        val receipts = snapshot.receipts
        require(receipts.size <= MAX_RECEIPTS &&
            receipts.zipWithNext().all { (left, right) -> left.requestKey < right.requestKey }
        ) { "Auxiliary4 ledger must contain bounded, sorted, unique request keys" }

        val noticeById = auxiliary.notices.associateBy { it.noticeId }
        val revisions = mutableSetOf<Pair<NoticeId, Long>>()
        for (receipt in receipts) {
            val notice = requireNotNull(noticeById[receipt.noticeId]) {
                "Auxiliary4 receipt lacks its Notice"
            }
            require(notice.sourceRef.value.startsWith("reminder:") &&
                notice.noticeKey.value.startsWith("reminder:") &&
                notice.destinationRef.value == "faro:avisos.local"
            ) { "Auxiliary4 receipt refers to a foreign Notice" }
            require(receipt.resultRevision in 2..notice.revision &&
                revisions.add(receipt.noticeId to receipt.resultRevision) &&
                (receipt.resultRevision != notice.revision || receipt.target == notice.attention)
            ) { "Auxiliary4 receipt revision is inconsistent with Notice history" }
            val hash = CanonicalHashBuilder.domain("faro-notice-attention-receipt-v4")
                .string("noticeId", receipt.noticeId.value)
                .string("expectedRevision", receipt.expectedRevision.toString())
                .string("targetAttention", receipt.target.name)
                .sha256Hex()
            require(hash == receipt.requestHash) { "Auxiliary4 receipt fingerprint mismatch" }
        }
    }

    private fun encodeLedger(receipts: List<S4W03NoticeAttentionReceiptV4>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(receipts.size)
            for (receipt in receipts) {
                output.writeAscii(receipt.requestKey)
                output.writeAscii(receipt.requestHash)
                output.writeAscii(receipt.noticeId.value)
                output.writeLong(receipt.expectedRevision)
                output.writeAscii(receipt.target.name)
                output.writeLong(receipt.resultRevision)
                require(bytes.size() <= MAX_LEDGER_BYTES) { "Auxiliary4 ledger exceeds its byte budget" }
            }
        }
        return bytes.toByteArray()
    }

    private fun decodeLedger(bytes: ByteArray): List<S4W03NoticeAttentionReceiptV4> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_RECEIPTS)
            List(count) {
                S4W03NoticeAttentionReceiptV4(
                    requestKey = input.readAscii(),
                    requestHash = input.readAscii(),
                    noticeId = NoticeId.of(input.readAscii()),
                    expectedRevision = input.readLong(),
                    target = NoticeAttention.valueOf(input.readAscii()),
                    resultRevision = input.readLong(),
                )
            }.also {
                require(input.available() == 0) { "Auxiliary4 ledger contains trailing bytes" }
            }
        }

    private fun DataOutputStream.writeAscii(value: String) {
        require(value.length in 1..MAX_FIELD_BYTES && value.all { it.code in 33..126 })
        writeShort(value.length)
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun DataInputStream.readAscii(): String {
        val length = readUnsignedShort()
        require(length in 1..MAX_FIELD_BYTES && length <= available())
        val bytes = ByteArray(length).also(::readFully)
        require(bytes.all { it.toInt() in 33..126 })
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
