package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.YearMonth
import java.time.ZoneId

data class FinanceBudgetKey(
    val period: YearMonth,
    val category: FinanceEntryCategory,
    val currency: FinanceCurrencyCode,
) {
    init {
        require(period.year in 1..9999)
        require(category.value.none { it.isISOControl() } && Charsets.UTF_8.newEncoder().canEncode(category.value))
    }
}

/** Null clears an existing limit. Zero is an explicit configured limit. */
data class FinanceBudgetChange(
    val key: FinanceBudgetKey,
    val expectedFinanceRevision: Long,
    val limitMinor: Long?,
    val recordedAtEpochMillis: Long,
) {
    init {
        require(expectedFinanceRevision >= 0 && recordedAtEpochMillis >= 0)
        require(limitMinor == null || limitMinor >= 0)
    }
}

data class FinanceBudgetQuery(val key: FinanceBudgetKey, val civilZone: ZoneId)

enum class FinanceBudgetCoverage { READY, PARTIAL, DENIED, UNAVAILABLE }

data class FinanceBudgetObservation(
    val coverage: FinanceBudgetCoverage,
    val sourceRevision: Long? = null,
    val revisionOfLimit: Long? = null,
    val limitMinor: Long? = null,
    val actualSpentMinor: Long? = null,
    val uncategorizedCommitmentsMinor: Long? = null,
    val estimatedAvailableMinor: Long? = null,
)

fun interface FinanceBudgetReadPort {
    fun read(query: FinanceBudgetQuery): FinanceBudgetObservation
}

object FinanceBudgetChangePayload {
    fun payload(change: FinanceBudgetChange): CanonicalPayload {
        val action = if (change.limitMinor == null) FinanceOperations.BUDGET_CLEAR else FinanceOperations.BUDGET_SET
        return CanonicalPayload.fromEncoded("${action.value}.v1", encode(change))
    }

    fun decode(action: OperationId, payload: CanonicalPayload): FinanceBudgetChange? {
        if (action != FinanceOperations.BUDGET_SET && action != FinanceOperations.BUDGET_CLEAR) return null
        if (payload.formatId != "${action.value}.v1") return null
        val bytes = payload.copyBytes()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val key = FinanceBudgetKey(
                    YearMonth.parse(input.readUTF()),
                    FinanceEntryCategory.of(input.readUTF()),
                    FinanceCurrencyCode.of(input.readUTF()),
                )
                val change = FinanceBudgetChange(
                    key, input.readLong(),
                    if (action == FinanceOperations.BUDGET_SET) input.readLong() else null,
                    input.readLong(),
                )
                require(encode(change).contentEquals(bytes))
                change
            }
        }.getOrNull()
    }

    private fun encode(change: FinanceBudgetChange): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(change.key.period.toString())
                output.writeUTF(change.key.category.value)
                output.writeUTF(change.key.currency.value)
                output.writeLong(change.expectedFinanceRevision)
                change.limitMinor?.let(output::writeLong)
                output.writeLong(change.recordedAtEpochMillis)
            }
            bytes.toByteArray()
        }
}
