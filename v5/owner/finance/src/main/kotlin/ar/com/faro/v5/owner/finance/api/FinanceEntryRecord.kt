package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@JvmInline
value class FinanceEntryId private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 128

        fun of(raw: String): FinanceEntryId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "finance entry id must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "finance entry id must be at most $MAX_LENGTH characters"
            }
            return FinanceEntryId(normalized)
        }
    }
}

@JvmInline
value class FinanceEntryConcept private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 160

        fun of(raw: String): FinanceEntryConcept {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "finance entry concept must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "finance entry concept must be at most $MAX_LENGTH characters"
            }
            return FinanceEntryConcept(normalized)
        }
    }
}

@JvmInline
value class FinanceEntryCategory private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 80

        fun of(raw: String): FinanceEntryCategory {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "finance entry category must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "finance entry category must be at most $MAX_LENGTH characters"
            }
            return FinanceEntryCategory(normalized)
        }
    }
}

enum class FinanceEntryKind {
    INCOME,
    EXPENSE,
}

data class FinanceEntryRecord(
    val entryId: FinanceEntryId,
    val accountId: FinanceAccountId,
    val amountMinor: Long,
    val kind: FinanceEntryKind,
    val concept: FinanceEntryConcept,
    val category: FinanceEntryCategory,
    val effectiveAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val provenanceLabel: String? = null,
) {
    init {
        require(amountMinor > 0L) { "finance entry amount must be greater than zero" }
        require(effectiveAtEpochMillis <= recordedAtEpochMillis) {
            "finance entry effective time cannot be after recorded time"
        }
        provenanceLabel?.let { label ->
            val normalized = label.trim()
            require(normalized.isNotEmpty()) {
                "finance entry provenance label must not be blank when present"
            }
            require(label == normalized) {
                "finance entry provenance label must be canonical"
            }
            require(normalized.length <= MAX_PROVENANCE_LENGTH) {
                "finance entry provenance label must be at most $MAX_PROVENANCE_LENGTH characters"
            }
        }
    }

    companion object {
        const val MAX_PROVENANCE_LENGTH = 160
    }
}

data class FinanceEntrySnapshot(
    val entryId: FinanceEntryId,
    val accountId: FinanceAccountId,
    val amountMinor: Long,
    val kind: FinanceEntryKind,
    val currency: FinanceCurrencyCode,
    val concept: FinanceEntryConcept,
    val category: FinanceEntryCategory,
    val effectiveAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val provenanceLabel: String? = null,
) {
    init {
        require(amountMinor > 0L) { "finance entry amount must be greater than zero" }
        require(effectiveAtEpochMillis <= recordedAtEpochMillis) {
            "finance entry effective time cannot be after recorded time"
        }
    }
}

object FinanceEntryRecordPayload {
    const val FORMAT_ID = "finance.entry.record.v1"
    private const val MAX_FIELD_BYTES = 4096

    fun payload(record: FinanceEntryRecord): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(record))

    fun encodeBytes(record: FinanceEntryRecord): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeField(record.entryId.value)
                output.writeField(record.accountId.value)
                output.writeLong(record.amountMinor)
                output.writeField(record.kind.name)
                output.writeField(record.concept.value)
                output.writeField(record.category.value)
                output.writeLong(record.effectiveAtEpochMillis)
                output.writeLong(record.recordedAtEpochMillis)
                output.writeBoolean(record.provenanceLabel != null)
                record.provenanceLabel?.let { output.writeField(it) }
            }
            bytes.toByteArray()
        }

    fun decode(payload: CanonicalPayload): FinanceEntryRecord? {
        if (payload.formatId != FORMAT_ID) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                val entryId = FinanceEntryId.of(input.readField())
                val accountId = FinanceAccountId.of(input.readField())
                val amountMinor = input.readLong()
                val kind = FinanceEntryKind.valueOf(input.readField())
                val concept = FinanceEntryConcept.of(input.readField())
                val category = FinanceEntryCategory.of(input.readField())
                val effectiveAt = input.readLong()
                val recordedAt = input.readLong()
                val provenance =
                    if (input.readBoolean()) input.readField() else null
                require(input.read() == -1) {
                    "finance entry payload contains trailing bytes"
                }
                FinanceEntryRecord(
                    entryId = entryId,
                    accountId = accountId,
                    amountMinor = amountMinor,
                    kind = kind,
                    concept = concept,
                    category = category,
                    effectiveAtEpochMillis = effectiveAt,
                    recordedAtEpochMillis = recordedAt,
                    provenanceLabel = provenance,
                )
            }
        }.getOrNull()
    }

    private fun DataOutputStream.writeField(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_BYTES) {
            "finance entry field is too large"
        }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readField(): String {
        val size = readInt()
        require(size in 0..MAX_FIELD_BYTES) {
            "invalid finance entry field length"
        }
        val encoded = ByteArray(size)
        readFully(encoded)
        return String(encoded, Charsets.UTF_8)
    }
}
