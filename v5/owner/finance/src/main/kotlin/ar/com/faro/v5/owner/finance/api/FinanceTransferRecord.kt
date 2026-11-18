package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

@JvmInline
value class FinanceTransferId(val value: String) {
    init {
        require(value.length in 1..128 && value == value.trim())
        require(value.none { it.code < 32 || it.code == 127 })
    }
}

/** A record of a same-currency transfer between two tracked accounts. */
data class FinanceTransferRecord(
    val transferId: FinanceTransferId,
    val fromAccountId: FinanceAccountId,
    val toAccountId: FinanceAccountId,
    val currency: FinanceCurrencyCode,
    val amountMinor: Long,
    val effectiveAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val concept: FinanceEntryConcept,
    val balanceWarningAccepted: Boolean,
) {
    init {
        require(fromAccountId != toAccountId) { "Transfer accounts must differ" }
        require(amountMinor > 0L) { "Transfer amount must be positive" }
        require(effectiveAtEpochMillis <= recordedAtEpochMillis) { "Transfer date is in the future" }
    }

    fun matchesAccounts(snapshot: FinanceSnapshot): Boolean =
        snapshot.accounts.any { it.accountId == fromAccountId && it.currency == currency } &&
            snapshot.accounts.any { it.accountId == toAccountId && it.currency == currency }

    fun entry(kind: FinanceEntryKind): FinanceEntryRecord {
        val digest = MessageDigest.getInstance("SHA-256").digest(transferId.value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val account = when (kind) {
            FinanceEntryKind.EXPENSE -> fromAccountId
            FinanceEntryKind.INCOME -> toAccountId
        }
        return FinanceEntryRecord(
            entryId = FinanceEntryId.of("transfer-" + kind.name.lowercase(java.util.Locale.ROOT) + "-" + digest),
            accountId = account,
            amountMinor = amountMinor,
            kind = kind,
            concept = concept,
            category = FinanceEntryCategory.of("Transferencia"),
            effectiveAtEpochMillis = effectiveAtEpochMillis,
            recordedAtEpochMillis = recordedAtEpochMillis,
            provenanceLabel = "transfer:" + transferId.value,
        )
    }
}

object FinanceTransferRecordPayload {
    const val FORMAT_ID = "finance.transfer.record.v1"

    fun payload(transfer: FinanceTransferRecord): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encode(transfer))

    private fun encode(transfer: FinanceTransferRecord): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(transfer.transferId.value)
                output.writeUTF(transfer.fromAccountId.value)
                output.writeUTF(transfer.toAccountId.value)
                output.writeUTF(transfer.currency.value)
                output.writeLong(transfer.amountMinor)
                output.writeLong(transfer.effectiveAtEpochMillis)
                output.writeLong(transfer.recordedAtEpochMillis)
                output.writeUTF(transfer.concept.value)
                output.writeBoolean(transfer.balanceWarningAccepted)
            }
            bytes.toByteArray()
        }

    fun decode(payload: CanonicalPayload): FinanceTransferRecord? {
        if (payload.formatId != FORMAT_ID) return null
        val bytes = payload.copyBytes()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val transfer = FinanceTransferRecord(
                    FinanceTransferId(input.readUTF()),
                    FinanceAccountId.of(input.readUTF()),
                    FinanceAccountId.of(input.readUTF()),
                    FinanceCurrencyCode.of(input.readUTF()),
                    input.readLong(), input.readLong(), input.readLong(),
                    FinanceEntryConcept.of(input.readUTF()),
                    input.readBoolean(),
                )
                require(encode(transfer).contentEquals(bytes)) { "Transfer payload is not canonical" }
                transfer
            }
        }.getOrNull()
    }
}
