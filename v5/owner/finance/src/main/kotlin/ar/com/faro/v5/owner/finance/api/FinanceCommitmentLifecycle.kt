package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

sealed interface FinanceCommitmentLifecycle {
    val id: FinanceCommitmentId
    val recordedAtEpochMillis: Long

    data class Edit(val replacement: FinanceCommitmentCreate.Schedule, val expectedVersion: Long) : FinanceCommitmentLifecycle {
        override val id get() = replacement.id
        override val recordedAtEpochMillis get() = replacement.recordedAtEpochMillis
        init { require(expectedVersion in 1 until Long.MAX_VALUE) }
    }

    data class SetActive(
        override val id: FinanceCommitmentId,
        val expectedVersion: Long,
        val active: Boolean,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentLifecycle {
        init { require(expectedVersion in 1 until Long.MAX_VALUE && recordedAtEpochMillis >= 0) }
    }

    data class Retire(
        override val id: FinanceCommitmentId,
        val expectedVersion: Long,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentLifecycle {
        init { require(expectedVersion in 1 until Long.MAX_VALUE && recordedAtEpochMillis >= 0) }
    }

    data class Cancel(
        override val id: FinanceCommitmentId,
        override val recordedAtEpochMillis: Long,
    ) : FinanceCommitmentLifecycle {
        init { require(recordedAtEpochMillis >= 0) }
    }
}

data class FinanceCommitmentCalendarQuery(
    val from: YearMonth,
    val through: YearMonth,
    val asOf: LocalDate,
    val currency: FinanceCurrencyCode,
) {
    init {
        require(from.year in 1..9999 && through.year in 1..9999 && asOf.year in 1..9999)
        require(ChronoUnit.MONTHS.between(from, through) in 0..120L)
    }
}

enum class FinanceCommitmentState { PLANNED, DUE, PARTIAL, PAID_CONFIRMED, CANCELLED }

data class FinanceCalendarSchedule(
    val definition: FinanceCommitmentCreate.Schedule,
    val version: Long,
    val active: Boolean,
)

data class FinanceCalendarItem(
    val id: FinanceCommitmentId,
    val schedule: FinanceCommitmentCreate.ScheduleReference?,
    val debtId: FinanceCommitmentId?,
    val dueDate: LocalDate,
    val expectedMinor: Long,
    val paidMinor: Long,
    val outstandingMinor: Long,
    val state: FinanceCommitmentState,
    val due: Boolean,
)

data class FinanceCommitmentCalendar(
    val debts: List<FinanceCommitmentCreate.Debt>,
    val schedules: List<FinanceCalendarSchedule>,
    val items: List<FinanceCalendarItem>,
)

fun interface FinanceCommitmentCalendarReadPort {
    fun read(query: FinanceCommitmentCalendarQuery): FinanceCommitmentCalendar?
}

object FinanceCommitmentLifecyclePayload {
    fun operation(command: FinanceCommitmentLifecycle): OperationId = when (command) {
        is FinanceCommitmentLifecycle.Edit -> FinanceOperations.SCHEDULE_UPDATE
        is FinanceCommitmentLifecycle.SetActive -> FinanceOperations.SCHEDULE_SET_ACTIVE
        is FinanceCommitmentLifecycle.Retire -> FinanceOperations.SCHEDULE_DELETE
        is FinanceCommitmentLifecycle.Cancel -> FinanceOperations.PLAN_ITEM_CANCEL
    }

    fun payload(command: FinanceCommitmentLifecycle): CanonicalPayload =
        CanonicalPayload.fromEncoded("${operation(command).value}.v1", encode(command))

    fun decode(operation: OperationId, payload: CanonicalPayload): FinanceCommitmentLifecycle? {
        if (payload.formatId != "${operation.value}.v1") return null
        val bytes = payload.copyBytes()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val command = when (operation) {
                    FinanceOperations.SCHEDULE_UPDATE -> {
                        val version = input.readLong()
                        val data = ByteArray(input.available()).also(input::readFully)
                        val schedule = FinanceCommitmentCreatePayload.decode(
                            FinanceOperations.SCHEDULE_CREATE,
                            CanonicalPayload.fromEncoded("${FinanceOperations.SCHEDULE_CREATE.value}.v1", data),
                        ) as? FinanceCommitmentCreate.Schedule ?: return null
                        FinanceCommitmentLifecycle.Edit(schedule, version)
                    }
                    FinanceOperations.SCHEDULE_SET_ACTIVE -> FinanceCommitmentLifecycle.SetActive(
                        FinanceCommitmentId(input.readUTF()), input.readLong(), input.readBoolean(), input.readLong(),
                    )
                    FinanceOperations.SCHEDULE_DELETE -> FinanceCommitmentLifecycle.Retire(
                        FinanceCommitmentId(input.readUTF()), input.readLong(), input.readLong(),
                    )
                    FinanceOperations.PLAN_ITEM_CANCEL -> FinanceCommitmentLifecycle.Cancel(
                        FinanceCommitmentId(input.readUTF()), input.readLong(),
                    )
                    else -> return null
                }
                require(encode(command).contentEquals(bytes))
                command
            }
        }.getOrNull()
    }

    private fun encode(command: FinanceCommitmentLifecycle): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                when (command) {
                    is FinanceCommitmentLifecycle.Edit -> {
                        output.writeLong(command.expectedVersion)
                        output.write(FinanceCommitmentCreatePayload.payload(command.replacement).copyBytes())
                    }
                    is FinanceCommitmentLifecycle.SetActive -> {
                        output.writeUTF(command.id.value)
                        output.writeLong(command.expectedVersion)
                        output.writeBoolean(command.active)
                        output.writeLong(command.recordedAtEpochMillis)
                    }
                    is FinanceCommitmentLifecycle.Retire -> {
                        output.writeUTF(command.id.value)
                        output.writeLong(command.expectedVersion)
                        output.writeLong(command.recordedAtEpochMillis)
                    }
                    is FinanceCommitmentLifecycle.Cancel -> {
                        output.writeUTF(command.id.value)
                        output.writeLong(command.recordedAtEpochMillis)
                    }
                }
            }
            bytes.toByteArray()
        }
}
