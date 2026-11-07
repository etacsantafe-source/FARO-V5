package ar.com.faro.v5.owner.monthlyplan.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth

/** Existing D-229 operation, not a new catalog entry or an executable binding. */
object MonthlyPlanOperations {
    val CAPABILITY_ID = CapabilityId("N-028")
    val EXECUTOR_ID = ExecutorId("planning")
    val ITEM_ADD = OperationId("monthly_plan.item.add")
}

/** Pure intent codec. Expected amount is not a Finance payment. */
object MonthlyPlanItemAddPayload {
    const val FORMAT_ID = "monthly_plan.item.add.v1"
    const val MAX_PAYLOAD_BYTES = 8192
    private const val MAX_FIELD_BYTES = 4096
    private const val MAGIC = 0x4d435231 // MCR1
    private const val VERSION = 1

    fun payload(item: MonthlyPlanItem): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(item))

    fun encodeBytes(item: MonthlyPlanItem): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.field(item.id.value)
            output.field(item.title)
            output.field(item.period.toString())
            output.field(item.dueDate.toString())
            output.field(item.status.name)
            output.writeBoolean(item.expectedAmountMinor != null)
            if (item.expectedAmountMinor != null) {
                output.writeLong(item.expectedAmountMinor)
                output.field(requireNotNull(item.currencyCode))
            }
            output.writeBoolean(item.paymentConfirmedAtEpochMillis != null)
            item.paymentConfirmedAtEpochMillis?.let { output.writeLong(it) }
            output.writeLong(item.updatedAtEpochMillis)
        }
        val encoded = bytes.toByteArray()
        require(encoded.size <= MAX_PAYLOAD_BYTES) { "monthly plan payload too large" }
        return encoded
    }

    fun decode(payload: CanonicalPayload): MonthlyPlanItem? {
        if (payload.formatId != FORMAT_ID || payload.size !in 1..MAX_PAYLOAD_BYTES) return null
        val original = payload.copyBytes()
        return runCatching {
            val item = DataInputStream(ByteArrayInputStream(original)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val id = MonthlyPlanItemId.of(input.field())
                val title = input.field()
                val period = YearMonth.parse(input.field())
                val due = LocalDate.parse(input.field())
                val status = MonthlyPlanItemStatus.valueOf(input.field())
                val hasAmount = input.flag()
                val amount = if (hasAmount) input.readLong() else null
                val currency = if (hasAmount) input.field() else null
                val hasPaymentTime = input.flag()
                val paymentTime = if (hasPaymentTime) input.readLong() else null
                val updated = input.readLong()
                require(input.read() == -1) { "trailing monthly plan payload bytes" }
                MonthlyPlanItem(id, title, period, due, status, amount, currency, paymentTime, updated)
            }
            require(encodeBytes(item).contentEquals(original)) { "noncanonical monthly plan encoding" }
            item
        }.getOrNull()
    }

    private fun strictUtf8(value: String): ByteArray {
        val buffer = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun DataOutputStream.field(value: String) {
        val bytes = strictUtf8(value)
        require(bytes.size <= MAX_FIELD_BYTES) { "monthly plan field too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.field(): String {
        val length = readInt()
        require(length in 0..MAX_FIELD_BYTES) { "invalid monthly plan field length" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun DataInputStream.flag(): Boolean = when (val byte = readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> error("noncanonical monthly plan boolean: $byte")
    }
}
