package ar.com.faro.v5.owner.reminders.api

import ar.com.faro.v5.kernel.contracts.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** W03-D1: the four pre-existing N-032 operation identifiers. */
object S4W03ReminderOperations {
    val CREATE = ReminderOperations.CREATE
    val UPDATE = OperationId("reminder.update")
    val CANCEL = OperationId("reminder.cancel")
    val ATTEND = OperationId("reminder.attend")
    val supported = listOf(CREATE, UPDATE, CANCEL, ATTEND)

    fun allows(contract: OperationContractDescriptor): Boolean =
        contract.operationId in supported &&
            contract.capabilityId == ReminderOperations.CAPABILITY_ID &&
            contract.canonicalExecutor == ReminderOperations.EXECUTOR_ID &&
            contract.executionProfile == ExecutionProfile.REALITY_MUTATION &&
            contract.exposure == ContractExposure.BINDABLE
}

sealed interface S4W03ReminderChange {
    data class Create(val rule: ReminderRule) : S4W03ReminderChange
    data class Update(val original: ReminderRule, val revised: ReminderRule) : S4W03ReminderChange
    data class Cancel(val original: ReminderRule, val atEpochMillis: Long) : S4W03ReminderChange
    data class Attend(val acknowledged: ReminderOccurrence) : S4W03ReminderChange
}

/** An immutable reviewed intention, not a current owner-state assumption. */
data class S4W03ReminderIntent(
    val expectedRevision: Long,
    val change: S4W03ReminderChange,
) {
    init { require(expectedRevision in 0 until Long.MAX_VALUE) }

    val operation: OperationId
        get() = when (change) {
            is S4W03ReminderChange.Create -> S4W03ReminderOperations.CREATE
            is S4W03ReminderChange.Update -> S4W03ReminderOperations.UPDATE
            is S4W03ReminderChange.Cancel -> S4W03ReminderOperations.CANCEL
            is S4W03ReminderChange.Attend -> S4W03ReminderOperations.ATTEND
        }

    val ruleId: ReminderRuleId
        get() = when (val c = change) {
            is S4W03ReminderChange.Create -> c.rule.id
            is S4W03ReminderChange.Update -> c.original.id
            is S4W03ReminderChange.Cancel -> c.original.id
            is S4W03ReminderChange.Attend -> c.acknowledged.ruleId
        }

    fun linkedMatterIds(snapshot: ReminderSnapshot): Set<String> = when (val c = change) {
        is S4W03ReminderChange.Create -> listOfNotNull(c.rule.linkedMatterId).toSet()
        is S4W03ReminderChange.Update ->
            listOfNotNull(c.original.linkedMatterId, c.revised.linkedMatterId).toSet()
        is S4W03ReminderChange.Cancel -> listOfNotNull(c.original.linkedMatterId).toSet()
        is S4W03ReminderChange.Attend ->
            snapshot.rules.singleOrNull { it.id == c.acknowledged.ruleId }
                ?.linkedMatterId?.let { setOf(it) } ?: emptySet()
    }

    fun validOn(snapshot: ReminderSnapshot): Boolean = runCatching {
        if (snapshot.revision != expectedRevision) return@runCatching false
        when (val c = change) {
            is S4W03ReminderChange.Create ->
                snapshot.rules.none { it.id == c.rule.id }
            is S4W03ReminderChange.Update ->
                c.original.id == c.revised.id && c.original != c.revised &&
                    c.revised.updatedAtEpochMillis > c.original.updatedAtEpochMillis &&
                    snapshot.rules.singleOrNull { it.id == c.original.id } == c.original
            is S4W03ReminderChange.Cancel ->
                c.original.enabled && c.atEpochMillis > c.original.updatedAtEpochMillis &&
                    snapshot.rules.singleOrNull { it.id == c.original.id } == c.original
            is S4W03ReminderChange.Attend -> {
                val occ = c.acknowledged
                val rule = snapshot.rules.singleOrNull { it.id == occ.ruleId }
                val current = snapshot.occurrences.singleOrNull { it.id == occ.id }
                rule != null && rule.enabled &&
                    occ.status == ReminderOccurrenceStatus.ACKNOWLEDGED &&
                    occ.acknowledgedAtEpochMillis != null &&
                    occ.acknowledgedAtEpochMillis >= occ.dueAtEpochMillis &&
                    occ.updatedAtEpochMillis >= occ.acknowledgedAtEpochMillis &&
                    occ.dueAtEpochMillis < Long.MAX_VALUE &&
                    ReminderCivilExpansion.expand(
                        rule, occ.dueAtEpochMillis, occ.dueAtEpochMillis + 1L,
                        offset = 0, limit = 2,
                    ).singleOrNull()?.let {
                        it.id == occ.id && it.dueAtEpochMillis == occ.dueAtEpochMillis
                    } == true &&
                    (current == null || (
                        current.ruleId == occ.ruleId &&
                            current.dueAtEpochMillis == occ.dueAtEpochMillis &&
                            current.status == ReminderOccurrenceStatus.PENDING
                        ))
            }
        }
    }.getOrDefault(false)
}

/** Canonical bounded review payload, intentionally different from CREATE's original codec. */
object S4W03ReminderMutationPayload {
    const val FORMAT_ID = "reminder.review-mutation.v1"
    private const val MAGIC = 0x52334431
    private const val MAX_BYTES = 22_000
    private const val MAX_RULE_BYTES = 8192

    fun encode(intent: S4W03ReminderIntent): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, bytes(intent))

    private fun bytes(intent: S4W03ReminderIntent): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeLong(intent.expectedRevision)
            when (val c = intent.change) {
                is S4W03ReminderChange.Create -> {
                    out.writeByte(1)
                    out.rule(c.rule)
                }
                is S4W03ReminderChange.Update -> {
                    out.writeByte(2)
                    out.rule(c.original)
                    out.rule(c.revised)
                }
                is S4W03ReminderChange.Cancel -> {
                    out.writeByte(3)
                    out.rule(c.original)
                    out.writeLong(c.atEpochMillis)
                }
                is S4W03ReminderChange.Attend -> {
                    out.writeByte(4)
                    val o = c.acknowledged
                    out.writeUTF(o.id.value)
                    out.writeUTF(o.ruleId.value)
                    out.writeLong(o.dueAtEpochMillis)
                    out.writeLong(o.acknowledgedAtEpochMillis ?: -1L)
                    out.writeLong(o.updatedAtEpochMillis)
                    out.writeUTF(o.status.name)
                }
            }
        }
        return buffer.toByteArray().also {
            require(it.size in 1..MAX_BYTES)
        }
    }

    fun decode(payload: CanonicalPayload): S4W03ReminderIntent? {
        if (payload.formatId != FORMAT_ID || payload.size !in 1..MAX_BYTES) return null
        val source = payload.copyBytes()
        return runCatching {
            val intent = DataInputStream(ByteArrayInputStream(source)).use { input ->
                require(input.readInt() == MAGIC)
                val expected = input.readLong()
                val change: S4W03ReminderChange = when (input.readUnsignedByte()) {
                    1 -> S4W03ReminderChange.Create(input.rule())
                    2 -> S4W03ReminderChange.Update(input.rule(), input.rule())
                    3 -> S4W03ReminderChange.Cancel(input.rule(), input.readLong())
                    4 -> {
                        val id = ReminderOccurrenceId.of(input.readUTF())
                        val ruleId = ReminderRuleId.of(input.readUTF())
                        val due = input.readLong()
                        val acknowledged = input.readLong()
                        val updated = input.readLong()
                        val status = ReminderOccurrenceStatus.valueOf(input.readUTF())
                        S4W03ReminderChange.Attend(ReminderOccurrence(
                            id, ruleId, due, status, acknowledged, updated,
                        ))
                    }
                    else -> error("Unknown Reminder action")
                }
                require(input.read() == -1)
                S4W03ReminderIntent(expected, change)
            }
            require(bytes(intent).contentEquals(source)) { "noncanonical payload" }
            intent
        }.getOrNull()
    }

    private fun DataOutputStream.rule(rule: ReminderRule) {
        val b = ReminderCreatePayload.encodeBytes(rule)
        require(b.size in 1..MAX_RULE_BYTES)
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.rule(): ReminderRule {
        val n = readInt()
        require(n in 1..MAX_RULE_BYTES)
        val b = ByteArray(n)
        readFully(b)
        return requireNotNull(ReminderCreatePayload.decode(
            CanonicalPayload.fromEncoded(ReminderCreatePayload.FORMAT_ID, b),
        ))
    }
}
