package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Not a backed-up owner: only a candidate FBS2v3 private section. */
data class PlanningDomainSectionSnapshot(
    val schemaVersion: Int,
    val reminders: ReminderSnapshot,
    val monthlyPlan: MonthlyPlanSnapshot,
)

/** Plaintext section codec; NEVER a portable archive or authenticated source. */
object FaroV5PlanningDomainSemanticCodec {
    private const val MAGIC = 0x504C4E31 // PLN1, distinct from G4B1 and FBS2
    private const val VERSION = 1
    private const val PLANNING_SCHEMA = 8
    private const val MAX_ROWS = 20_000
    private const val MAX_FIELD_BYTES = 4_096
    const val MAX_BYTES = 4 * 1024 * 1024

    fun encode(snapshot: PlanningDomainSectionSnapshot): ByteArray {
        validate(snapshot)
        val b = BoundedBytes(MAX_BYTES)
        DataOutputStream(b).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(snapshot.schemaVersion)
            out.writeLong(snapshot.reminders.revision)
            out.writeInt(snapshot.reminders.rules.size)
            snapshot.reminders.rules.forEach { rule ->
                putText(out, rule.id.value)
                putText(out, rule.title)
                putText(out, rule.zoneId)
                out.writeLong(rule.anchorLocalDate.toEpochDay())
                out.writeLong(rule.anchorLocalTime.toNanoOfDay())
                putText(out, rule.recurrence.name)
                out.writeBoolean(rule.enabled)
                out.writeBoolean(rule.linkedMatterId != null)
                rule.linkedMatterId?.let { putText(out, it) }
                out.writeLong(rule.updatedAtEpochMillis)
            }
            out.writeInt(snapshot.reminders.occurrences.size)
            snapshot.reminders.occurrences.forEach { occ ->
                putText(out, occ.id.value)
                putText(out, occ.ruleId.value)
                out.writeLong(occ.dueAtEpochMillis)
                putText(out, occ.status.name)
                out.writeBoolean(occ.acknowledgedAtEpochMillis != null)
                occ.acknowledgedAtEpochMillis?.let(out::writeLong)
                out.writeLong(occ.updatedAtEpochMillis)
            }
            out.writeLong(snapshot.monthlyPlan.revision)
            out.writeInt(snapshot.monthlyPlan.items.size)
            snapshot.monthlyPlan.items.forEach { item ->
                putText(out, item.id.value)
                putText(out, item.title)
                out.writeInt(item.period.year)
                out.writeInt(item.period.monthValue)
                out.writeLong(item.dueDate.toEpochDay())
                putText(out, item.status.name)
                out.writeBoolean(item.expectedAmountMinor != null)
                item.expectedAmountMinor?.let(out::writeLong)
                item.currencyCode?.let { putText(out, it) }
                out.writeBoolean(item.paymentConfirmedAtEpochMillis != null)
                item.paymentConfirmedAtEpochMillis?.let(out::writeLong)
                out.writeLong(item.updatedAtEpochMillis)
            }
        }
        return b.toByteArray()
    }

    fun decode(bytes: ByteArray): PlanningDomainSectionSnapshot {
        check(bytes.size in 1..MAX_BYTES) { "planning section outside byte bound" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC) { "planning section magic mismatch" }
            check(input.readInt() == VERSION) { "unsupported planning section version" }
            val schema = input.readInt()
            check(schema == PLANNING_SCHEMA) { "unknown planning schema" }
            val reminderRevision = input.readLong()
            val rules = buildList {
                repeat(getCount(input)) {
                    add(ReminderRule(
                        ReminderRuleId.of(getText(input)),
                        getText(input), getText(input),
                        LocalDate.ofEpochDay(input.readLong()),
                        LocalTime.ofNanoOfDay(input.readLong()),
                        ReminderRecurrence.valueOf(getText(input)),
                        readFlag(input),
                        if (readFlag(input)) getText(input) else null,
                        input.readLong(),
                    ))
                }
            }
            val occurrences = buildList {
                repeat(getCount(input)) {
                    add(ReminderOccurrence(
                        ReminderOccurrenceId.of(getText(input)),
                        ReminderRuleId.of(getText(input)),
                        input.readLong(), ReminderOccurrenceStatus.valueOf(getText(input)),
                        if (readFlag(input)) input.readLong() else null, input.readLong(),
                    ))
                }
            }
            val planRevision = input.readLong()
            val items = buildList {
                repeat(getCount(input)) {
                    val id = MonthlyPlanItemId.of(getText(input))
                    val title = getText(input)
                    val period = YearMonth.of(input.readInt(), input.readInt())
                    val due = LocalDate.ofEpochDay(input.readLong())
                    val status = MonthlyPlanItemStatus.valueOf(getText(input))
                    val hasAmount = readFlag(input)
                    val amount = if (hasAmount) input.readLong() else null
                    val currency = if (hasAmount) getText(input) else null
                    val paid = if (readFlag(input)) input.readLong() else null
                    add(MonthlyPlanItem(
                        id, title, period, due, status, amount, currency, paid,
                        input.readLong(),
                    ))
                }
            }
            check(input.available() == 0) { "planning section trailing bytes" }
            PlanningDomainSectionSnapshot(
                schema, ReminderSnapshot(reminderRevision, rules, occurrences),
                MonthlyPlanSnapshot(planRevision, items),
            ).also(::validate)
        }
    }

    private fun validate(s: PlanningDomainSectionSnapshot) {
        check(s.schemaVersion == PLANNING_SCHEMA)
        check(s.reminders.rules.size <= MAX_ROWS &&
            s.reminders.occurrences.size <= MAX_ROWS &&
            s.monthlyPlan.items.size <= MAX_ROWS)
        check(ordered(s.reminders.rules.map { it.id.value }) &&
            ordered(s.reminders.occurrences.map { it.id.value }) &&
            ordered(s.monthlyPlan.items.map { it.id.value })) {
            "planning section IDs not in canonical UTF8 binary order"
        }
    }

    private fun ordered(ids: List<String>): Boolean = ids.zipWithNext().all { (a,b) ->
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        var i = 0
        while (i < minOf(left.size, right.size) && left[i] == right[i]) i++
        if (i == minOf(left.size, right.size)) left.size < right.size
        else (left[i].toInt() and 255) < (right[i].toInt() and 255)
    }

    private fun putText(out: DataOutputStream, text: String) {
        val data = text.toByteArray(Charsets.UTF_8)
        check(data.size <= MAX_FIELD_BYTES) { "planning field exceeds budget" }
        out.writeInt(data.size)
        out.write(data)
    }
    private fun getText(input: DataInputStream): String {
        val n = input.readInt()
        check(n in 0..MAX_FIELD_BYTES && n <= input.available()) {
            "planning field malformed"
        }
        val data = ByteArray(n)
        input.readFully(data)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data)).toString()
    }
    private fun getCount(input: DataInputStream): Int =
        input.readInt().also { n ->
            check(n in 0..MAX_ROWS && n <= input.available()) {
                "planning row count invalid"
            }
        }
    private fun readFlag(input: DataInputStream): Boolean =
        when (input.readUnsignedByte()) {
            0 -> false
            1 -> true
            else -> error("invalid planning presence flag")
        }

    private class BoundedBytes(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(b: Int) {
            check(size() < limit)
            super.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            check(len >= 0 && size().toLong() + len <= limit.toLong())
            super.write(b, off, len)
        }
    }
}
