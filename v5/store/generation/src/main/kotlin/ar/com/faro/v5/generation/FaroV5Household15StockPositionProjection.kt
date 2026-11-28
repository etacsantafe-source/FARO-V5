package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRows
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionType
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationType
import java.util.Collections

enum class FaroV5StockCoverage { READY, EMPTY_CONFIRMED, UNAVAILABLE, DENIED, STALE }

enum class FaroV5StockEventType { RECEIPT, RECEIPT_UNDO, RESERVE, RELEASE, CONSUME, CONSUME_UNDO }

data class FaroV5StockPosition(
    val stockItemRef: String,
    val unit: String,
    val physicalThousandths: Long,
    val reservedThousandths: Long,
    val consumedThousandths: Long,
    val availableThousandths: Long,
    val pendingReceiptThousandths: Long,
)

data class FaroV5StockHistoryEntry(
    val eventId: String,
    val originEventId: String?,
    val stockItemRef: String,
    val unit: String,
    val type: FaroV5StockEventType,
    val quantityThousandths: Long,
    val recordedAtMs: Long,
    val revision: Long,
)

sealed interface FaroV5StockReadResult {
    val coverage: FaroV5StockCoverage

    data class Verified internal constructor(
        val pointer: ActiveGenerationPointer,
        val asOfMs: Long,
        val ledgerRevision: Long,
        val positions: List<FaroV5StockPosition>,
        val history: List<FaroV5StockHistoryEntry>,
    ) : FaroV5StockReadResult {
        override val coverage: FaroV5StockCoverage
            get() = if (positions.isEmpty()) FaroV5StockCoverage.EMPTY_CONFIRMED else FaroV5StockCoverage.READY
    }

    enum class Hidden(override val coverage: FaroV5StockCoverage) : FaroV5StockReadResult {
        UNAVAILABLE(FaroV5StockCoverage.UNAVAILABLE),
        DENIED(FaroV5StockCoverage.DENIED),
        STALE(FaroV5StockCoverage.STALE),
    }
}

/** Pure projection. The generation owner must authenticate the SQL15 snapshot and principal. */
internal object FaroV5Household15StockPositionProjection {
    private data class Key(val item: String, val unit: String, val principal: String)
    private data class Event(val key: Key, val entry: FaroV5StockHistoryEntry, val sourceRef: String?)
    private data class Remaining(val event: Event, var quantity: Long)

    internal data class Balance(val physical: Long = 0, val reserved: Long = 0, val consumed: Long = 0) {
        fun after(type: FaroV5StockEventType, quantity: Long): Balance {
            require(quantity > 0)
            val result = when (type) {
                FaroV5StockEventType.RECEIPT -> copy(physical = Math.addExact(physical, quantity))
                FaroV5StockEventType.RECEIPT_UNDO -> copy(physical = Math.subtractExact(physical, quantity))
                FaroV5StockEventType.RESERVE -> copy(reserved = Math.addExact(reserved, quantity))
                FaroV5StockEventType.RELEASE -> copy(reserved = Math.subtractExact(reserved, quantity))
                FaroV5StockEventType.CONSUME -> copy(
                    physical = Math.subtractExact(physical, quantity),
                    consumed = Math.addExact(consumed, quantity),
                )
                FaroV5StockEventType.CONSUME_UNDO -> copy(
                    physical = Math.addExact(physical, quantity),
                    consumed = Math.subtractExact(consumed, quantity),
                )
            }
            require(result.reserved >= 0 && result.physical >= result.reserved && result.consumed >= 0)
            return result
        }
    }

    fun fromVerifiedHistory(
        rows: RealityHousehold15ConsumptionRows,
        principal: String,
        pointer: ActiveGenerationPointer,
        asOfMs: Long,
    ): FaroV5StockReadResult {
        if (principal.isBlank()) return FaroV5StockReadResult.Hidden.DENIED
        return try {
            project(rows, principal, pointer, asOfMs)
        } catch (_: IllegalArgumentException) {
            FaroV5StockReadResult.Hidden.UNAVAILABLE
        } catch (_: IllegalStateException) {
            FaroV5StockReadResult.Hidden.UNAVAILABLE
        } catch (_: ArithmeticException) {
            FaroV5StockReadResult.Hidden.UNAVAILABLE
        }
    }

    private fun project(
        rows: RealityHousehold15ConsumptionRows,
        principal: String,
        pointer: ActiveGenerationPointer,
        asOfMs: Long,
    ): FaroV5StockReadResult.Verified {
        require(asOfMs >= 0)
        val reservations = rows.historical
        val physicalHistory = reservations.historical
        val purchases = physicalHistory.historical
        // Reuse receipt/compensation ownership and per-purchase conservation checks.
        checkNotNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(physicalHistory))
        val purchaseKeys = purchases.purchases.associate { purchase ->
            require(purchase.confirmedAtMs in 0..asOfMs && purchase.revision > 0)
            purchase.purchaseRef to Key(purchase.stockItemRef, purchase.unit, purchase.confirmedBy)
        }
        val received = mutableMapOf<Key, Long>()
        val purchased = mutableMapOf<Key, Long>()
        for (purchase in purchases.purchases) {
            val key = purchaseKeys.getValue(purchase.purchaseRef)
            purchased[key] = Math.addExact(purchased.getOrDefault(key, 0), purchase.quantityThousandths.toLong())
        }
        val eventIds = mutableSetOf<String>()
        val intentKeys = mutableSetOf<String>()
        fun event(
            key: Key, id: String, intent: String, origin: String?, type: FaroV5StockEventType,
            amount: Int, at: Long, revision: Long, sourceRef: String? = null,
        ): Event {
            require(id.isNotBlank() && intent.isNotBlank() && amount > 0 && at in 0..asOfMs &&
                eventIds.add(id) && intentKeys.add(intent))
            return Event(key, FaroV5StockHistoryEntry(
                id, origin, key.item, key.unit, type, amount.toLong(), at, revision,
            ), sourceRef)
        }
        val events = buildList {
            for (row in purchases.receipts.receipts) {
                val key = requireNotNull(purchaseKeys[row.purchaseRef])
                add(event(key, row.receiptId, row.intentKey, null, FaroV5StockEventType.RECEIPT,
                    row.quantityThousandths, row.recordedAtMs, row.revision))
            }
            for (row in physicalHistory.compensations) {
                val key = requireNotNull(purchaseKeys[row.purchaseRef])
                add(event(key, row.compensationId, row.intentKey, row.receiptId, FaroV5StockEventType.RECEIPT_UNDO,
                    row.quantityThousandths, row.recordedAtMs, row.revision))
            }
            var previousReservation = 0L
            for (row in reservations.reservations) {
                require(row.revision > previousReservation)
                previousReservation = row.revision
                val isReserve = row.type == RealityHousehold14ReservationType.RESERVE
                require(!isReserve || row.reservationRef == row.eventId)
                add(event(Key(row.stockItemRef, row.unit, row.principal), row.eventId, row.intentKey,
                    row.reservationRef.takeUnless { isReserve },
                    if (isReserve) FaroV5StockEventType.RESERVE else FaroV5StockEventType.RELEASE,
                    row.quantityThousandths, row.recordedAtMs, row.revision))
            }
            var previousConsumption = 0L
            for (row in rows.consumptions) {
                require(row.revision > previousConsumption && row.reservationRef == null &&
                    row.sourceRef.isNotBlank() && row.occurredAtMs in 0..row.recordedAtMs)
                previousConsumption = row.revision
                val isConsume = row.type == RealityHousehold15ConsumptionType.CONSUME
                require(!isConsume || row.consumptionRef == row.eventId)
                add(event(Key(row.stockItemRef, row.unit, row.principal), row.eventId, row.intentKey,
                    row.consumptionRef.takeUnless { isConsume },
                    if (isConsume) FaroV5StockEventType.CONSUME else FaroV5StockEventType.CONSUME_UNDO,
                    row.quantityThousandths, row.recordedAtMs, row.revision, row.sourceRef))
            }
        }.sortedBy { it.entry.revision }

        val balances = mutableMapOf<Key, Balance>()
        val openOrigins = mutableMapOf<String, Remaining>()
        val history = mutableListOf<FaroV5StockHistoryEntry>()
        var previousRevision = 0L
        for (event in events) {
            val (key, entry) = event
            require(entry.revision > previousRevision && key in purchased)
            previousRevision = entry.revision
            val quantity = entry.quantityThousandths
            when (entry.type) {
                FaroV5StockEventType.RECEIPT ->
                    received[key] = Math.addExact(received.getOrDefault(key, 0), quantity)
                FaroV5StockEventType.RECEIPT_UNDO ->
                    received[key] = Math.subtractExact(received.getOrDefault(key, 0), quantity)
                FaroV5StockEventType.RESERVE, FaroV5StockEventType.CONSUME ->
                    openOrigins[entry.eventId] = Remaining(event, quantity)
                FaroV5StockEventType.RELEASE, FaroV5StockEventType.CONSUME_UNDO -> {
                    val origin = requireNotNull(openOrigins[entry.originEventId])
                    val originType = if (entry.type == FaroV5StockEventType.RELEASE) {
                        FaroV5StockEventType.RESERVE
                    } else {
                        FaroV5StockEventType.CONSUME
                    }
                    require(origin.event.key == key && origin.event.entry.type == originType &&
                        origin.event.sourceRef == event.sourceRef && origin.event.entry.recordedAtMs <= entry.recordedAtMs &&
                        quantity <= origin.quantity)
                    origin.quantity = Math.subtractExact(origin.quantity, quantity)
                }
            }
            balances[key] = (balances[key] ?: Balance()).after(entry.type, quantity)
            if (key.principal == principal) history.add(entry)
        }
        val positions = purchased.keys.filter { it.principal == principal }
            .sortedWith(compareBy({ it.item }, { it.unit }))
            .map { key ->
                val balance = balances[key] ?: Balance()
                FaroV5StockPosition(
                    key.item, key.unit, balance.physical, balance.reserved, balance.consumed,
                    Math.subtractExact(balance.physical, balance.reserved),
                    Math.subtractExact(purchased.getValue(key), received.getOrDefault(key, 0)),
                )
            }
        val revision = maxOf(
            previousRevision,
            purchases.receipts.revision,
            purchases.purchases.maxOfOrNull { it.revision } ?: 0L,
        )
        return FaroV5StockReadResult.Verified(
            pointer, asOfMs, revision, Collections.unmodifiableList(positions), Collections.unmodifiableList(history),
        )
    }
}
