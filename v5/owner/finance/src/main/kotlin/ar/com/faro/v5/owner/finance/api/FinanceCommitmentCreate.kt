package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

@JvmInline
value class FinanceCommitmentId(val value: String) {
    init {
        require(value.length in 1..128 && value == value.trim())
        require(value.none { it.isISOControl() } && Charsets.UTF_8.newEncoder().canEncode(value))
    }
}

enum class FinanceScheduleRecurrence { ONCE, MONTHLY }

sealed interface FinanceCommitmentCreate {
    val currency: FinanceCurrencyCode
    val recordedAtEpochMillis: Long

    data class Debt(
        val id: FinanceCommitmentId,
        val title: String,
        val creditor: String,
        override val currency: FinanceCurrencyCode,
        val principalMinor: Long,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentCreate {
        init {
            requireDeclarationText(title)
            requireDeclarationText(creditor)
            require(principalMinor > 0 && recordedAtEpochMillis >= 0)
        }
    }

    data class Schedule(
        val id: FinanceCommitmentId,
        val debtId: FinanceCommitmentId?,
        override val currency: FinanceCurrencyCode,
        val installmentMinor: Long,
        val startPeriod: YearMonth,
        val recurrence: FinanceScheduleRecurrence,
        val totalCount: Int?,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentCreate {
        init {
            require(installmentMinor > 0 && recordedAtEpochMillis >= 0)
            require(startPeriod.year in 1..9999)
            require(totalCount == null || totalCount > 0)
            require(recurrence != FinanceScheduleRecurrence.ONCE || totalCount == 1)
            require(totalCount == null || startPeriod.plusMonths(totalCount.toLong() - 1).year <= 9999)
        }
    }

    data class ScheduleReference(val id: FinanceCommitmentId, val version: Long) {
        init {
            require(version > 0)
        }
    }

    data class PlanItem(
        val manualId: FinanceCommitmentId?,
        val schedule: ScheduleReference?,
        val debtId: FinanceCommitmentId?,
        val period: YearMonth,
        val ordinal: Int,
        val dueDate: LocalDate,
        override val currency: FinanceCurrencyCode,
        val expectedMinor: Long,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentCreate {
        init {
            require((manualId == null) != (schedule == null))
            require(manualId == null || !manualId.value.startsWith(DERIVED_PREFIX))
            require(period.year in 1..9999 && YearMonth.from(dueDate) == period)
            require(ordinal > 0 && expectedMinor > 0 && recordedAtEpochMillis >= 0)
        }

        val id: FinanceCommitmentId
            get() {
                manualId?.let { return it }
                val reference = requireNotNull(schedule)
                val key = "${reference.id.value.length}:${reference.id.value}:${reference.version}:$period:$ordinal"
                val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                return FinanceCommitmentId(DERIVED_PREFIX + digest)
            }

        private companion object {
            const val DERIVED_PREFIX = "schedule-item-"
        }
    }
}

private fun requireDeclarationText(value: String) {
    require(value.length in 1..160 && value == value.trim())
    require(value.none { it.isISOControl() } && Charsets.UTF_8.newEncoder().canEncode(value))
}

object FinanceCommitmentCreatePayload {
    fun payload(command: FinanceCommitmentCreate): CanonicalPayload {
        val operationId = when (command) {
            is FinanceCommitmentCreate.Debt -> FinanceOperations.DEBT_CREATE
            is FinanceCommitmentCreate.Schedule -> FinanceOperations.SCHEDULE_CREATE
            is FinanceCommitmentCreate.PlanItem -> FinanceOperations.PLAN_ITEM_CREATE
        }
        return CanonicalPayload.fromEncoded("${operationId.value}.v1", encode(command))
    }

    fun decode(operationId: OperationId, payload: CanonicalPayload): FinanceCommitmentCreate? {
        if (payload.formatId != "${operationId.value}.v1") return null
        val bytes = payload.copyBytes()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val currency = FinanceCurrencyCode.of(input.readUTF())
                val recordedAt = input.readLong()
                val command = when (operationId) {
                    FinanceOperations.DEBT_CREATE -> FinanceCommitmentCreate.Debt(
                        FinanceCommitmentId(input.readUTF()), input.readUTF(), input.readUTF(),
                        currency, input.readLong(), recordedAt,
                    )
                    FinanceOperations.SCHEDULE_CREATE -> FinanceCommitmentCreate.Schedule(
                        FinanceCommitmentId(input.readUTF()), input.readOptionalId(), currency,
                        input.readLong(), YearMonth.parse(input.readUTF()),
                        FinanceScheduleRecurrence.valueOf(input.readUTF()),
                        if (input.readBoolean()) input.readInt() else null, recordedAt,
                    )
                    FinanceOperations.PLAN_ITEM_CREATE -> FinanceCommitmentCreate.PlanItem(
                        input.readOptionalId(),
                        input.readOptionalId()?.let { FinanceCommitmentCreate.ScheduleReference(it, input.readLong()) },
                        input.readOptionalId(), YearMonth.parse(input.readUTF()), input.readInt(),
                        LocalDate.parse(input.readUTF()), currency, input.readLong(), recordedAt,
                    )
                    else -> return null
                }
                require(encode(command).contentEquals(bytes))
                command
            }
        }.getOrNull()
    }

    private fun encode(command: FinanceCommitmentCreate): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(command.currency.value)
                output.writeLong(command.recordedAtEpochMillis)
                when (command) {
                    is FinanceCommitmentCreate.Debt -> {
                        output.writeUTF(command.id.value)
                        output.writeUTF(command.title)
                        output.writeUTF(command.creditor)
                        output.writeLong(command.principalMinor)
                    }
                    is FinanceCommitmentCreate.Schedule -> {
                        output.writeUTF(command.id.value)
                        output.writeOptionalId(command.debtId)
                        output.writeLong(command.installmentMinor)
                        output.writeUTF(command.startPeriod.toString())
                        output.writeUTF(command.recurrence.name)
                        output.writeBoolean(command.totalCount != null)
                        command.totalCount?.let(output::writeInt)
                    }
                    is FinanceCommitmentCreate.PlanItem -> {
                        output.writeOptionalId(command.manualId)
                        output.writeOptionalId(command.schedule?.id)
                        command.schedule?.let { output.writeLong(it.version) }
                        output.writeOptionalId(command.debtId)
                        output.writeUTF(command.period.toString())
                        output.writeInt(command.ordinal)
                        output.writeUTF(command.dueDate.toString())
                        output.writeLong(command.expectedMinor)
                    }
                }
            }
            bytes.toByteArray()
        }

    private fun DataOutputStream.writeOptionalId(id: FinanceCommitmentId?) {
        writeBoolean(id != null)
        id?.let { writeUTF(it.value) }
    }

    private fun DataInputStream.readOptionalId(): FinanceCommitmentId? =
        if (readBoolean()) FinanceCommitmentId(readUTF()) else null
}
