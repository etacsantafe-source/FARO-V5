package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class FinanceAccountCreate(
    val accountId: FinanceAccountId,
    val displayName: FinanceAccountDisplayName,
    val currency: FinanceCurrencyCode,
    val nature: FinanceAccountNature,
    val openingBalanceMinor: Long = 0L,
) {
    init {
        require(openingBalanceMinor >= 0L) {
            "finance opening balance must not be negative"
        }
    }
}

object FinanceAccountCreatePayload {
    const val FORMAT_ID = "finance.account.create.v1"
    private const val MAX_FIELD_BYTES = 4096

    fun payload(create: FinanceAccountCreate): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(create))

    fun encodeBytes(create: FinanceAccountCreate): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeField(create.accountId.value)
                output.writeField(create.displayName.value)
                output.writeField(create.currency.value)
                output.writeField(create.nature.name)
                output.writeLong(create.openingBalanceMinor)
            }
            bytes.toByteArray()
        }

    fun decode(payload: CanonicalPayload): FinanceAccountCreate? {
        if (payload.formatId != FORMAT_ID) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                val decoded = FinanceAccountCreate(
                    accountId = FinanceAccountId.of(input.readField()),
                    displayName = FinanceAccountDisplayName.of(input.readField()),
                    currency = FinanceCurrencyCode.of(input.readField()),
                    nature = FinanceAccountNature.valueOf(input.readField()),
                    openingBalanceMinor = input.readLong(),
                )
                require(input.read() == -1) {
                    "finance account payload contains trailing bytes"
                }
                decoded
            }
        }.getOrNull()
    }

    private fun DataOutputStream.writeField(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_BYTES) { "finance account field is too large" }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readField(): String {
        val size = readInt()
        require(size in 0..MAX_FIELD_BYTES) { "invalid finance account field length" }
        val encoded = ByteArray(size)
        readFully(encoded)
        return String(encoded, Charsets.UTF_8)
    }
}
