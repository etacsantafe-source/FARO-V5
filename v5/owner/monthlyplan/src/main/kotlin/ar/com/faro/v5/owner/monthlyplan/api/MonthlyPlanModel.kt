package ar.com.faro.v5.owner.monthlyplan.api

import java.time.LocalDate
import java.time.YearMonth

/** Contracts only: no Reality database owner or host grant yet. */
@JvmInline value class MonthlyPlanItemId private constructor(val value: String) {
    companion object {
        fun of(raw: String): MonthlyPlanItemId {
            val v = raw.trim()
            require(v.isNotEmpty() && v.length <= 128)
            return MonthlyPlanItemId(v)
        }
    }
}
enum class MonthlyPlanItemStatus { PLANNED, PAID_CONFIRMED, CANCELLED }

/** A due bill does NOT imply a confirmed Finance payment. */
data class MonthlyPlanItem(
    val id: MonthlyPlanItemId,
    val title: String,
    val period: YearMonth,
    val dueDate: LocalDate,
    val status: MonthlyPlanItemStatus,
    val expectedAmountMinor: Long? = null,
    val currencyCode: String? = null,
    val paymentConfirmedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank() && title.length <= 160)
        require((expectedAmountMinor == null) == (currencyCode == null))
        require(expectedAmountMinor == null || expectedAmountMinor >= 0L)
        require(currencyCode == null || Regex("[A-Z]{3}").matches(currencyCode))
        require((status == MonthlyPlanItemStatus.PAID_CONFIRMED) ==
            (paymentConfirmedAtEpochMillis != null))
        require(paymentConfirmedAtEpochMillis == null ||
            paymentConfirmedAtEpochMillis >= 0L)
        require(updatedAtEpochMillis >= 0L)
    }
}
data class MonthlyPlanSnapshot(
    val revision: Long,
    val items: List<MonthlyPlanItem>,
) {
    init {
        require(revision >= 0L)
        val ids = items.map { it.id }
        require(ids.toSet().size == ids.size)
    }
}
/** Not bound to Android; never report nonexistent owner as zero pending. */
fun interface MonthlyPlanSnapshotReadApi {
    fun read(): MonthlyPlanSnapshot
}
