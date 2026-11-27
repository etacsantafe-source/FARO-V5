package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.*

/** DEV observation: neither consent nor an Apply receipt. */
interface RealityHousehold15ConsumptionApplyPort {
    fun position(request: HouseholdStockConsumptionDevRequest): HouseholdStockConsumptionPosition?
    fun apply(review: HouseholdStockConsumptionReview, recordedAtMs: Long, scope: RealityTransactionScope): Boolean
}

/** Uses the surrounding RealityStore BEGIN IMMEDIATE; no independent transaction. */
internal class RealityHousehold15ConsumptionSqlWriter(
    private val db: SQLiteConnection,
    private val principal: String,
    private val generation: HouseholdReviewGeneration,
) {
    private fun valid(s: String): Boolean =
        s.codePointCount(0, s.length) in 1..128 &&
            s.none { it <= '\u0020' || it == '\u007f' } &&
            s.toByteArray(Charsets.UTF_8).let {
                it.size <= 384 && it.toString(Charsets.UTF_8) == s
            }

    private fun admitted(request: HouseholdStockConsumptionDevRequest): Boolean =
        valid(principal) && valid(request.stockItem) && valid(request.sourceRef) &&
            valid(request.intentKey) && request.reservationRef == null &&
            request.context.isolatedDev && request.context.foreground &&
            request.context.hostEpoch > 0 && request.context.principal == principal &&
            request.context.activePointer == generation && request.ledgerRevision >= 0 &&
            request.quantity.thousandths in 1L..Int.MAX_VALUE.toLong()

    fun position(request: HouseholdStockConsumptionDevRequest): HouseholdStockConsumptionPosition? {
        if (!admitted(request)) return null
        return position(request, RealityHousehold15ConsumptionRowsPort.captureConnection(db))
    }

    private fun position(
        request: HouseholdStockConsumptionDevRequest,
        rows: RealityHousehold15ConsumptionRows,
    ): HouseholdStockConsumptionPosition? {
        val unit = request.quantity.unit.name
        val purchases = rows.historical.historical.historical.purchases.filter {
            it.confirmedBy == principal && it.stockItemRef == request.stockItem &&
                it.unit == unit && it.provenance == "MANUAL_EXPLICIT"
        }.associateBy { it.purchaseRef }
        if (purchases.isEmpty()) return null
        val receipts = rows.historical.historical.historical.receipts.receipts
        val undos = rows.historical.historical.compensations
        val reservations = rows.historical.reservations
        val physical = receipts.filter { it.purchaseRef in purchases }
            .sumOf { it.quantityThousandths.toLong() } -
            undos.filter { it.purchaseRef in purchases }
                .sumOf { it.quantityThousandths.toLong() } +
            rows.consumptions.filter {
                it.principal == principal && it.stockItemRef == request.stockItem && it.unit == unit
            }.sumOf {
                if (it.type == RealityHousehold15ConsumptionType.CONSUME)
                    -it.quantityThousandths.toLong() else it.quantityThousandths.toLong()
            }
        val open = linkedMapOf<String, Long>()
        for (event in reservations) {
            if (event.principal != principal || event.stockItemRef != request.stockItem ||
                event.unit != unit) continue
            when (event.type) {
                RealityHousehold14ReservationType.RESERVE ->
                    open[event.eventId] = event.quantityThousandths.toLong()
                RealityHousehold14ReservationType.RELEASE ->
                    open[event.reservationRef] = checkNotNull(open[event.reservationRef]) -
                        event.quantityThousandths.toLong()
            }
        }
        val reserved = open.values.sum()
        check(physical >= 0 && reserved in 0L..physical) { "SQL15 corrupt stock position" }
        val sourceReceipts = receipts.filter { it.purchaseRef == request.sourceRef }
        val source = if (request.sourceRef !in purchases || sourceReceipts.isEmpty() ||
            rows.consumptions.any {
                it.sourceRef == request.sourceRef &&
                    (it.principal != principal || it.stockItemRef != request.stockItem || it.unit != unit)
            }) null else {
            val remaining = sourceReceipts.sumOf { it.quantityThousandths.toLong() } -
                undos.filter { it.purchaseRef == request.sourceRef }
                    .sumOf { it.quantityThousandths.toLong() } +
                rows.consumptions.filter { it.sourceRef == request.sourceRef }.sumOf {
                    if (it.type == RealityHousehold15ConsumptionType.CONSUME)
                        -it.quantityThousandths.toLong() else it.quantityThousandths.toLong()
                }
            if (remaining < 0 || remaining > physical) null
            else HouseholdStockConsumptionSourcePosition(
                request.sourceRef, principal, request.stockItem, request.quantity.unit, remaining)
        }
        val revision = maxOf(
            rows.historical.historical.historical.receipts.revision,
            undos.lastOrNull()?.revision ?: 0L,
            reservations.lastOrNull()?.revision ?: 0L,
            rows.consumptions.lastOrNull()?.revision ?: 0L,
        )
        return HouseholdStockConsumptionPosition(principal, generation, request.stockItem,
            request.quantity.unit, revision, physical, reserved, source)
    }

    fun apply(review: HouseholdStockConsumptionReview, recordedAtMs: Long): Boolean {
        val request = review.request
        if (!admitted(request) || recordedAtMs < 0L ||
            HouseholdStockConsumptionReview.decode(review.payload) != review) return false
        val before = RealityHousehold15ConsumptionRowsPort.captureConnection(db)
        if (before.consumptions.size >= 8_192) return false
        val intent = request.intentKey
        if (before.historical.historical.historical.receipts.receipts.any {
                it.receiptId == intent || it.intentKey == intent
            } || before.historical.historical.compensations.any {
                it.compensationId == intent || it.intentKey == intent
            } || before.historical.reservations.any {
                it.eventId == intent || it.intentKey == intent
            } || before.consumptions.any {
                it.eventId == intent || it.intentKey == intent
            }) return false
        if (recordedAtMs < (before.consumptions.lastOrNull()?.recordedAtMs ?: 0L)) return false
        val current = position(request, before) ?: return false
        val quantity = request.quantity.thousandths
        if (current.revision != request.ledgerRevision || current.revision == Long.MAX_VALUE ||
            current.source == null || quantity > current.source.remainingThousandths ||
            quantity > current.physicalThousandths - current.reservedThousandths) return false
        val row = RealityHousehold15ConsumptionRow(intent, intent,
            RealityHousehold15ConsumptionType.CONSUME, intent, request.stockItem,
            request.quantity.unit.name, quantity.toInt(), principal, request.sourceRef,
            null, recordedAtMs, recordedAtMs, current.revision + 1)
        db.prepare("INSERT INTO household_stock_consumption_event(" +
            "event_id,intent_key,event_type,consumption_ref,stock_item_ref,unit," +
            "quantity_thousandths,principal,source_ref,reservation_ref," +
            "occurred_at_ms,recorded_at_ms,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)").use { stmt ->
            stmt.bindText(1, row.eventId)
            stmt.bindText(2, row.intentKey)
            stmt.bindText(3, row.type.name)
            stmt.bindText(4, row.consumptionRef)
            stmt.bindText(5, row.stockItemRef)
            stmt.bindText(6, row.unit)
            stmt.bindLong(7, row.quantityThousandths.toLong())
            stmt.bindText(8, row.principal)
            stmt.bindText(9, row.sourceRef)
            stmt.bindNull(10)
            stmt.bindLong(11, row.occurredAtMs)
            stmt.bindLong(12, row.recordedAtMs)
            stmt.bindLong(13, row.revision)
            stmt.step()
        }
        val after = RealityHousehold15ConsumptionRowsPort.captureConnection(db)
        check(after.historical == before.historical &&
            after.consumptions == before.consumptions + row) {
            "SQL15 readback changed legacy history or appended unexpected events"
        }
        return true
    }
}
