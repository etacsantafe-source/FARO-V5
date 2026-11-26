package ar.com.faro.v5.generation

import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRows

/**
 * SQL13 DEV physical receipt balance only. Reservations, consumption and
 * availability remain UNKNOWN until later independently authorized ledgers.
 */
internal data class FaroV5DevStockPhysicalPosition(
    val stockItemRef: String,
    val unit: String,
    val physicalNetThousandths: Long,
    val reservedThousandths: Long? = null,
    val consumedThousandths: Long? = null,
    val availableThousandths: Long? = null,
)

internal data class FaroV5DevStockPhysicalSnapshot(
    val ledgerRevision: Long,
    val positions: List<FaroV5DevStockPhysicalPosition>,
    val source: String = "SQL13_CONFIRMED_RECEIPTS_LESS_UNDO",
)

/** No database handle, no grant creation and no Apply capability. */
internal object FaroV5Household13DevStockSnapshot {
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")
    private data class Event(val purchaseRef: String, val revision: Long, val delta: Long)

    /** Caller must independently authorize access to the SQL13 host for this owner. */
    fun read(host: FaroV5Household13ActiveDevReadHost): FaroV5DevStockPhysicalSnapshot? {
        val before = host.readHistory() ?: return null
        val projected = fromVerifiedHistory(before) ?: return null
        // Recheck the generation fence/grant after projecting, not just before.
        return projected.takeIf { host.readHistory() == before }
    }

    /** Pure adversarial projection over the same validated shape returned by SQL13. */
    internal fun fromVerifiedHistory(
        rows: RealityHousehold13CompensationRows,
    ): FaroV5DevStockPhysicalSnapshot? = runCatching {
        val historical = rows.historical
        val purchases = historical.purchases.associateBy { it.purchaseRef }
        check(purchases.size == historical.purchases.size)
        for (purchase in purchases.values) {
            check(purchase.provenance == "MANUAL_EXPLICIT" &&
                purchase.stockItemRef.isNotBlank() && purchase.unit in units &&
                purchase.quantityThousandths > 0 && purchase.confirmedBy.isNotBlank())
        }
        val receiptById = historical.receipts.receipts.associateBy { it.receiptId }
        check(receiptById.size == historical.receipts.receipts.size)
        check(historical.receipts.revision >= 0)
        val eventRevisions = HashSet<Long>()
        val events = mutableListOf<Event>()
        val receiptPurchaseRefs = HashSet<String>()
        for (receipt in historical.receipts.receipts) {
            val purchase = checkNotNull(purchases[receipt.purchaseRef])
            check(receipt.stockItemRef == purchase.stockItemRef && receipt.unit == purchase.unit &&
                receipt.quantityThousandths > 0 && receipt.recordedAtMs >= purchase.confirmedAtMs &&
                receipt.revision in 1L..historical.receipts.revision && eventRevisions.add(receipt.revision))
            receiptPurchaseRefs.add(receipt.purchaseRef)
            events += Event(receipt.purchaseRef, receipt.revision, receipt.quantityThousandths.toLong())
        }
        val reversed = HashSet<String>()
        for (undo in rows.compensations) {
            val receipt = checkNotNull(receiptById[undo.receiptId])
            val purchase = checkNotNull(purchases[undo.purchaseRef])
            check(reversed.add(undo.receiptId) && undo.purchaseRef == receipt.purchaseRef &&
                undo.quantityThousandths == receipt.quantityThousandths &&
                undo.unit == receipt.unit && undo.principal == purchase.confirmedBy &&
                undo.recordedAtMs >= receipt.recordedAtMs &&
                undo.revision > receipt.revision && undo.revision <= historical.receipts.revision &&
                eventRevisions.add(undo.revision))
            events += Event(undo.purchaseRef, undo.revision, -undo.quantityThousandths.toLong())
        }
        val perPurchase = HashMap<String, Long>()
        for (event in events.sortedBy { it.revision }) {
            val previous = perPurchase[event.purchaseRef] ?: 0L
            val next = Math.addExact(previous, event.delta)
            check(next >= 0L && next <= purchases.getValue(event.purchaseRef).quantityThousandths.toLong())
            perPurchase[event.purchaseRef] = next
        }
        val byItemAndUnit = HashMap<Pair<String, String>, Long>()
        for (purchaseRef in receiptPurchaseRefs) {
            val purchase = purchases.getValue(purchaseRef)
            val key = purchase.stockItemRef to purchase.unit
            byItemAndUnit[key] = Math.addExact(
                byItemAndUnit[key] ?: 0L, perPurchase[purchaseRef] ?: 0L,
            )
        }
        FaroV5DevStockPhysicalSnapshot(
            ledgerRevision = historical.receipts.revision,
            positions = byItemAndUnit.entries.sortedWith(
                compareBy({ it.key.first }, { it.key.second }),
            ).map { (key, net) ->
                FaroV5DevStockPhysicalPosition(key.first, key.second, net)
            },
        )
    }.getOrNull()
}
