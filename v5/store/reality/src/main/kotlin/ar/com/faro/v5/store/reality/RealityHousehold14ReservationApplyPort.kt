package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.HouseholdReviewGeneration
import ar.com.faro.v5.owner.household.api.HouseholdStockReservationAction
import ar.com.faro.v5.owner.household.api.HouseholdStockReservationReview

/** A position is an observation, not consent or an execution receipt. */
data class RealityHousehold14ReservationPosition(
    val generation: HouseholdReviewGeneration,
    val principal: String,
    val stockItemRef: String,
    val unit: String,
    val ledgerRevision: Long,
    val physicalNetThousandths: Long,
    val reservedThousandths: Long,
    val remainingByReservation: Map<String, Long>,
)

interface RealityHousehold14ReservationApplyPort {
    fun position(stockItemRef: String, unit: String): RealityHousehold14ReservationPosition?
    fun apply(review: HouseholdStockReservationReview, recordedAtMs: Long, scope: RealityTransactionScope): Boolean
}

/** No independent SQL BEGIN/COMMIT: uses the surrounding RealityStore Apply transaction. */
internal class RealityHousehold14ReservationSqlWriter(
    private val db: SQLiteConnection,
    private val principal: String,
    private val generation: HouseholdReviewGeneration,
) {
    private fun valid(value: String) = value.codePointCount(0, value.length) in 1..128 &&
        value.none { it <= '\u0020' || it == '\u007f' } &&
        value.toByteArray(Charsets.UTF_8).let { it.size <= 384 && it.toString(Charsets.UTF_8) == value }

    fun position(item: String, unit: String): RealityHousehold14ReservationPosition? {
        if (!valid(principal) || !valid(item) || unit !in UNITS) return null
        return position(item, unit, RealityHousehold14ReservationRowsPort.captureConnection(db))
    }

    private fun position(
        item: String, unit: String, rows: RealityHousehold14ReservationRows,
    ): RealityHousehold14ReservationPosition? {
        val purchases = rows.historical.historical.purchases.filter {
            it.stockItemRef == item && it.unit == unit && it.confirmedBy == principal &&
                it.provenance == "MANUAL_EXPLICIT"
        }.associateBy { it.purchaseRef }
        if (purchases.isEmpty()) return null
        val physical = rows.historical.historical.receipts.receipts
            .filter { it.purchaseRef in purchases }.sumOf { it.quantityThousandths.toLong() } -
            rows.historical.compensations.filter { it.purchaseRef in purchases }
                .sumOf { it.quantityThousandths.toLong() }
        val remaining = linkedMapOf<String, Long>()
        for (event in rows.reservations) {
            if (event.principal != principal || event.stockItemRef != item || event.unit != unit) continue
            when (event.type) {
                RealityHousehold14ReservationType.RESERVE ->
                    remaining[event.eventId] = event.quantityThousandths.toLong()
                RealityHousehold14ReservationType.RELEASE ->
                    remaining[event.reservationRef] =
                        checkNotNull(remaining[event.reservationRef]) - event.quantityThousandths
            }
        }
        val reserved = remaining.values.sum()
        check(physical >= 0 && reserved in 0L..physical) { "SQL14 inconsistent physical/reserved ledger" }
        val revision = maxOf(rows.historical.historical.receipts.revision,
            rows.reservations.lastOrNull()?.revision ?: 0L)
        return RealityHousehold14ReservationPosition(
            generation, principal, item, unit, revision, physical, reserved,
            remaining.filterValues { it > 0L }.toMap())
    }

    fun apply(review: HouseholdStockReservationReview, recordedAtMs: Long): Boolean {
        val request = review.request
        val context = request.context
        if (!valid(principal) || !valid(request.stockItem) || !valid(request.intentKey) ||
            !context.isolatedDev || !context.foreground || context.hostEpoch <= 0 ||
            context.principal != principal || context.activePointer != generation ||
            request.quantity.unit.name !in UNITS ||
            request.quantity.thousandths !in 1L..Int.MAX_VALUE.toLong() ||
            recordedAtMs < 0 ||
            (request.reservationRef != null && !valid(request.reservationRef))) return false

        val before = RealityHousehold14ReservationRowsPort.captureConnection(db)
        if (before.reservations.size >= 8_192 ||
            before.historical.historical.receipts.receipts.any {
                it.receiptId == request.intentKey || it.intentKey == request.intentKey
            } || before.historical.compensations.any {
                it.compensationId == request.intentKey || it.intentKey == request.intentKey
            } || before.reservations.any {
                it.eventId == request.intentKey || it.intentKey == request.intentKey
            }) return false
        if (recordedAtMs < (before.reservations.lastOrNull()?.recordedAtMs ?: 0L)) return false
        val observed = position(request.stockItem, request.quantity.unit.name, before) ?: return false
        if (observed.ledgerRevision != request.ledgerRevision ||
            observed.ledgerRevision == Long.MAX_VALUE) return false
        val quantity = request.quantity.thousandths
        val type = when (request.action) {
            HouseholdStockReservationAction.RESERVE -> {
                if (request.reservationRef != null ||
                    quantity > observed.physicalNetThousandths - observed.reservedThousandths) return false
                RealityHousehold14ReservationType.RESERVE
            }
            HouseholdStockReservationAction.RELEASE -> {
                val origin = request.reservationRef ?: return false
                if (origin == request.intentKey ||
                    quantity > (observed.remainingByReservation[origin] ?: 0L)) return false
                RealityHousehold14ReservationType.RELEASE
            }
        }
        val row = RealityHousehold14ReservationRow(
            eventId = request.intentKey, intentKey = request.intentKey, type = type,
            reservationRef = if (type == RealityHousehold14ReservationType.RESERVE) request.intentKey
                else checkNotNull(request.reservationRef),
            stockItemRef = request.stockItem, unit = request.quantity.unit.name,
            quantityThousandths = quantity.toInt(), principal = principal,
            recordedAtMs = recordedAtMs, revision = observed.ledgerRevision + 1L,
        )
        db.prepare("INSERT INTO household_stock_reservation_event(" +
            "event_id,intent_key,event_type,reservation_ref,stock_item_ref,unit," +
            "quantity_thousandths,principal,recorded_at_ms,revision) VALUES(?,?,?,?,?,?,?,?,?,?)").use { stmt ->
            stmt.bindText(1, row.eventId)
            stmt.bindText(2, row.intentKey)
            stmt.bindText(3, row.type.name)
            stmt.bindText(4, row.reservationRef)
            stmt.bindText(5, row.stockItemRef)
            stmt.bindText(6, row.unit)
            stmt.bindLong(7, row.quantityThousandths.toLong())
            stmt.bindText(8, row.principal)
            stmt.bindLong(9, row.recordedAtMs)
            stmt.bindLong(10, row.revision)
            stmt.step()
        }
        val after = RealityHousehold14ReservationRowsPort.captureConnection(db)
        check(after.historical == before.historical &&
            after.reservations == before.reservations + row) {
            "SQL14 Apply changed historical rows or inserted an unexpected event"
        }
        return true
    }

    private companion object {
        val UNITS = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")
    }
}
