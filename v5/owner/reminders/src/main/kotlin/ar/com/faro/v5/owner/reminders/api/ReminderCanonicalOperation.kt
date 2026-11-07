package ar.com.faro.v5.owner.reminders.api

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
import java.time.LocalTime

/** Existing D-229 operation, not a new catalog entry or an executable binding. */
object ReminderOperations {
    val CAPABILITY_ID = CapabilityId("N-032")
    val EXECUTOR_ID = ExecutorId("planning")
    val CREATE = OperationId("reminder.create")
}

/** Pure owner-side intent codec. It cannot schedule, persist, review or apply. */
object ReminderCreatePayload {
    const val FORMAT_ID = "reminder.rule.create.v1"
    const val MAX_PAYLOAD_BYTES = 8192
    private const val MAX_FIELD_BYTES = 4096
    private const val MAGIC = 0x52435231 // RCR1
    private const val VERSION = 1

    fun payload(rule: ReminderRule): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(rule))

    fun encodeBytes(rule: ReminderRule): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.field(rule.id.value)
            output.field(rule.title)
            output.field(rule.zoneId)
            output.field(rule.anchorLocalDate.toString())
            output.field(rule.anchorLocalTime.toString())
            output.field(rule.recurrence.name)
            output.writeBoolean(rule.enabled)
            output.writeBoolean(rule.linkedMatterId != null)
            rule.linkedMatterId?.let { output.field(it) }
            output.writeLong(rule.updatedAtEpochMillis)
        }
        val encoded = bytes.toByteArray()
        require(encoded.size <= MAX_PAYLOAD_BYTES) { "reminder payload too large" }
        return encoded
    }

    fun decode(payload: CanonicalPayload): ReminderRule? {
        if (payload.formatId != FORMAT_ID || payload.size !in 1..MAX_PAYLOAD_BYTES) return null
        val original = payload.copyBytes()
        return runCatching {
            val rule = DataInputStream(ByteArrayInputStream(original)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val id = ReminderRuleId.of(input.field())
                val title = input.field()
                val zone = input.field()
                val date = LocalDate.parse(input.field())
                val time = LocalTime.parse(input.field())
                val recurrence = ReminderRecurrence.valueOf(input.field())
                val enabled = input.flag()
                val hasMatter = input.flag()
                val matter = if (hasMatter) input.field() else null
                val updated = input.readLong()
                require(input.read() == -1) { "trailing reminder payload bytes" }
                ReminderRule(id, title, zone, date, time, recurrence, enabled, matter, updated)
            }
            require(encodeBytes(rule).contentEquals(original)) { "noncanonical reminder encoding" }
            rule
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
        require(bytes.size <= MAX_FIELD_BYTES) { "reminder field too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.field(): String {
        val length = readInt()
        require(length in 0..MAX_FIELD_BYTES) { "invalid reminder field length" }
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
        else -> error("noncanonical reminder boolean: $byte")
    }
}
