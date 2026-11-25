package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.*
import java.time.LocalDate

data class RealityHousehold12StockReceiptPosition(
    val purchase: ConfirmedStockPurchaseLine,
    val ledgerRevision: Long,
    val pendingThousandths: Long,
)

interface RealityHousehold12StockReceiveApplyPort {
    fun position(purchaseRef: String): RealityHousehold12StockReceiptPosition?

    fun receive(
        review: HouseholdStockReceiveReview,
        receivedDay: Long,
        recordedAtMs: Long,
        scope: RealityTransactionScope,
    ): Boolean
}

internal class RealityHousehold12StockReceiveRows(
    private val db: SQLiteConnection,
    private val principal: String,
    private val generation: HouseholdReviewGeneration,
) {
    private val purchases = RealityHousehold12PurchaseRowsWriter(db)

    fun position(purchaseRef: String): RealityHousehold12StockReceiptPosition? =
        position(purchaseRef, Household11ReceiptRowsSql.readRows(db))

    private fun position(
        purchaseRef: String,
        ledger: RealityHousehold11ReceiptRows,
    ): RealityHousehold12StockReceiptPosition? {
        val row = purchases.find(purchaseRef)?.takeIf {
            it.confirmedBy == principal && it.provenance == HouseholdManualPurchaseReview.PROVENANCE
        } ?: return null
        val purchased = HouseholdNeedQuantity(row.quantityThousandths.toLong(), HouseholdNeedUnit.valueOf(row.unit))
        val matching = ledger.receipts.filter { it.purchaseRef == purchaseRef }
        check(matching.all { it.stockItemRef == row.stockItemRef && it.unit == row.unit })
        val received = matching.sumOf { it.quantityThousandths.toLong() }
        check(received in 0L..purchased.thousandths)
        val purchase = ConfirmedStockPurchaseLine(
            principal, generation, row.stockItemRef, row.purchaseRef, row.revision, purchased, received,
        )
        return RealityHousehold12StockReceiptPosition(purchase, ledger.revision, purchased.thousandths - received)
    }

    fun receive(review: HouseholdStockReceiveReview, day: Long, atMs: Long): Boolean {
        val request = review.request
        if (request.context.principal != principal || request.context.activePointer != generation ||
            day !in DAYS || atMs < 0) return false
        val ledger = Household11ReceiptRowsSql.readRows(db)
        if (ledger.revision != review.ledgerRevision || ledger.revision == Long.MAX_VALUE ||
            ledger.receipts.any { it.intentKey == request.intentKey || it.receiptId == request.intentKey }) return false
        val purchase = position(request.purchaseReference, ledger)?.purchase ?: return false
        if (purchase.revision != request.purchaseRevision || purchase.stockItem != request.stockItem) return false
        val preview = HouseholdStockReceiptPreview.calculate(HouseholdStockReceiptInput(
            purchase.purchaseReference, purchase.purchased, purchase.receivedThousandths, request.quantity,
        ))
        if (preview !is HouseholdStockReceiptDecision.Ready) return false
        val next = ledger.revision + 1
        val row = RealityHousehold11ReceiptRow(
            request.intentKey, request.intentKey, request.purchaseReference, request.stockItem,
            request.quantity.thousandths.toInt(), request.quantity.unit.name, day, atMs, next,
        )
        val candidate = RealityHousehold11ReceiptRows(next, (ledger.receipts + row).sortedBy { it.receiptId })
        if (runCatching { Household11ReceiptRowsSql.validate(candidate) }.isFailure) return false
        db.prepare("UPDATE household_stock_receipt_state SET revision=? WHERE singleton_id=1 AND revision=?").use {
            it.bindLong(1, next)
            it.bindLong(2, ledger.revision)
            it.step()
        }
        val changed = db.prepare("SELECT changes()").use { check(it.step()); it.getLong(0) }
        if (changed != 1L) return false
        db.prepare("INSERT INTO household_stock_receipt(" + Household11ReceiptRowsSql.COLUMNS +
            ") VALUES(?,?,?,?,?,?,?,?,?)").use {
            it.bindText(1, row.receiptId)
            it.bindText(2, row.intentKey)
            it.bindText(3, row.purchaseRef)
            it.bindText(4, row.stockItemRef)
            it.bindLong(5, row.quantityThousandths.toLong())
            it.bindText(6, row.unit)
            it.bindLong(7, row.receivedEpochDay)
            it.bindLong(8, row.recordedAtMs)
            it.bindLong(9, row.revision)
            it.step()
        }
        return true
    }

    private companion object {
        val DAYS = LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()
    }
}
