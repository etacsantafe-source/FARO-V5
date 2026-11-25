package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.HouseholdReviewGeneration

/** Revision-pinned source: NOT an N-016 consent, Review or Undo capability. */
data class RealityHousehold13CompensationCandidate(
    val generation: HouseholdReviewGeneration,
    val principal: String,
    val receiptId: String,
    val purchaseRef: String,
    val stockItemRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val ledgerRevision: Long,
    val netReceivedThousandths: Long,
    val receivedAtMs: Long,
)

data class RealityHousehold13CompensationRequest(
    val generation: HouseholdReviewGeneration,
    val receiptId: String,
    val purchaseRef: String,
    val compensationId: String,
    val intentKey: String,
    val quantityThousandths: Int,
    val unit: String,
    val expectedLedgerRevision: Long,
    val recordedAtMs: Long,
)

/** Available only through the leased, trusted, SQL13 ACTIVE DEV generation graph. */
interface RealityHousehold13CompensationApplyPort {
    fun position(receiptId: String): RealityHousehold13CompensationCandidate?
    fun compensate(request: RealityHousehold13CompensationRequest, scope: RealityTransactionScope): Boolean
}

/** Does not begin/commit a SQLite transaction. RealityStore checks scope and the live owner fence. */
internal class RealityHousehold13CompensationSqlWriter(
    private val db: SQLiteConnection,
    private val principal: String,
    private val generation: HouseholdReviewGeneration,
) {
    private fun valid(value: String): Boolean =
        value.length in 1..128 && value.none { it.isWhitespace() || it.isISOControl() } &&
            value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value

    fun position(receiptId: String): RealityHousehold13CompensationCandidate? {
        if (!valid(receiptId) || !valid(principal)) return null
        val rows = RealityHousehold13CompensationRowsPort.captureConnection(db)
        if (rows.compensations.any { it.receiptId == receiptId }) return null
        val source = rows.historical.receipts.receipts.singleOrNull { it.receiptId == receiptId } ?: return null
        val purchase = rows.historical.purchases.singleOrNull { it.purchaseRef == source.purchaseRef } ?: return null
        if (purchase.confirmedBy != principal || purchase.provenance != "MANUAL_EXPLICIT" ||
            source.stockItemRef != purchase.stockItemRef || source.unit != purchase.unit) return null
        val gross = rows.historical.receipts.receipts.filter { it.purchaseRef == purchase.purchaseRef }
            .sumOf { it.quantityThousandths.toLong() }
        val reversed = rows.compensations.filter { it.purchaseRef == purchase.purchaseRef }
            .sumOf { it.quantityThousandths.toLong() }
        val net = gross - reversed
        if (net < source.quantityThousandths.toLong() || rows.historical.receipts.revision == Long.MAX_VALUE) return null
        return RealityHousehold13CompensationCandidate(
            generation, principal, receiptId, purchase.purchaseRef, purchase.stockItemRef,
            source.quantityThousandths, source.unit, rows.historical.receipts.revision,
            net, source.recordedAtMs,
        )
    }

    fun compensate(request: RealityHousehold13CompensationRequest): Boolean {
        if (request.generation != generation || !valid(request.receiptId) || !valid(request.purchaseRef) ||
            !valid(request.compensationId) || !valid(request.intentKey) ||
            request.receiptId == request.compensationId || request.receiptId == request.intentKey ||
            request.recordedAtMs < 0L) return false
        val candidate = position(request.receiptId) ?: return false
        if (candidate.purchaseRef != request.purchaseRef ||
            candidate.quantityThousandths != request.quantityThousandths || candidate.unit != request.unit ||
            candidate.ledgerRevision != request.expectedLedgerRevision || request.recordedAtMs < candidate.receivedAtMs
        ) return false
        val before = RealityHousehold13CompensationRowsPort.captureConnection(db)
        if (before.historical.receipts.receipts.any {
                it.receiptId == request.compensationId || it.intentKey == request.intentKey
            } || before.compensations.any {
                it.compensationId == request.compensationId || it.intentKey == request.intentKey
            }) return false
        val next = request.expectedLedgerRevision + 1L
        db.prepare("UPDATE household_stock_receipt_state SET revision=? WHERE singleton_id=1 AND revision=?").use {
            it.bindLong(1, next)
            it.bindLong(2, request.expectedLedgerRevision)
            it.step()
        }
        val changed = db.prepare("SELECT changes()").use { check(it.step()); it.getLong(0) }
        if (changed != 1L) return false
        val row = RealityHousehold13CompensationRow(
            request.compensationId, request.intentKey, request.receiptId, request.purchaseRef,
            request.quantityThousandths, request.unit, principal, request.recordedAtMs, next,
        )
        db.prepare(
            "INSERT INTO household_stock_compensation(" +
                "compensation_id,intent_key,receipt_id,purchase_ref,quantity_thousandths," +
                "unit,principal,recorded_at_ms,revision) VALUES(?,?,?,?,?,?,?,?,?)",
        ).use {
            it.bindText(1, row.compensationId)
            it.bindText(2, row.intentKey)
            it.bindText(3, row.receiptId)
            it.bindText(4, row.purchaseRef)
            it.bindLong(5, row.quantityThousandths.toLong())
            it.bindText(6, row.unit)
            it.bindText(7, row.principal)
            it.bindLong(8, row.recordedAtMs)
            it.bindLong(9, row.revision)
            it.step()
        }
        val after = RealityHousehold13CompensationRowsPort.captureConnection(db)
        check(after.compensations.size == before.compensations.size + 1 &&
            row in after.compensations && after.historical.receipts.revision == next &&
            after.historical.receipts.receipts == before.historical.receipts.receipts &&
            after.historical.purchases == before.historical.purchases) {
            "SQL13 compensation must preserve all original historical events"
        }
        return true
    }
}
