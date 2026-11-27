package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.household.api.*

/**
 * A2C1E DEV-only SQL15 compensatory writer. The ReviewOnly is NOT independently
 * trusted as COMMITTED provenance; only the future F host can authenticate that.
 * This port is never registered as a public operation or Android entry point.
 */
interface RealityHousehold15ConsumptionUndoApplyPort {
    fun position(consumptionRef: String): HouseholdStockConsumptionUndoCurrentPosition?
    fun apply(
        review: HouseholdStockConsumptionUndoDevDecision.ReviewOnly,
        recordedAtMs: Long,
        scope: RealityTransactionScope,
    ): Boolean
}

/** One surrounding RealityStore.run BEGIN IMMEDIATE; never an independent transaction. */
internal class RealityHousehold15ConsumptionUndoSqlWriter(
    private val db: SQLiteConnection,
    private val principal: String,
    private val generation: HouseholdReviewGeneration,
) {
    private fun token(value: String): Boolean =
        value.codePointCount(0, value.length) in 1..128 &&
            value.none { it <= '\u0020' || it == '\u007f' } &&
            value.toByteArray(Charsets.UTF_8).let {
                it.size <= 384 && it.toString(Charsets.UTF_8) == value
            }

    fun position(consumptionRef: String): HouseholdStockConsumptionUndoCurrentPosition? {
        if (!token(principal) || !token(consumptionRef) || !token(generation.id)) return null
        return position(consumptionRef, RealityHousehold15ConsumptionRowsPort.captureConnection(db))
    }

    private fun position(
        consumptionRef: String,
        rows: RealityHousehold15ConsumptionRows,
    ): HouseholdStockConsumptionUndoCurrentPosition? {
        val original = rows.consumptions.singleOrNull {
            it.eventId == consumptionRef && it.consumptionRef == consumptionRef &&
                it.type == RealityHousehold15ConsumptionType.CONSUME && it.principal == principal &&
                it.reservationRef == null
        } ?: return null
        val purchase = rows.historical.historical.historical.purchases.singleOrNull {
            it.purchaseRef == original.sourceRef && it.stockItemRef == original.stockItemRef &&
                it.unit == original.unit && it.confirmedBy == principal &&
                it.provenance == "MANUAL_EXPLICIT"
        } ?: return null
        val receipts = rows.historical.historical.historical.receipts.receipts
            .filter { it.purchaseRef == purchase.purchaseRef }
        if (receipts.isEmpty() || receipts.sumOf { it.quantityThousandths.toLong() } <= 0L) return null
        val undos = rows.consumptions.filter {
            it.type == RealityHousehold15ConsumptionType.CONSUME_UNDO &&
                it.consumptionRef == consumptionRef
        }
        val remaining = original.quantityThousandths.toLong() -
            undos.sumOf { it.quantityThousandths.toLong() }
        if (remaining < 0L || remaining > original.quantityThousandths.toLong()) return null
        val unit = runCatching { HouseholdNeedUnit.valueOf(original.unit) }.getOrNull() ?: return null
        val revision = maxOf(
            rows.historical.historical.historical.receipts.revision,
            rows.historical.historical.compensations.lastOrNull()?.revision ?: 0L,
            rows.historical.reservations.lastOrNull()?.revision ?: 0L,
            rows.consumptions.lastOrNull()?.revision ?: 0L,
        )
        return HouseholdStockConsumptionUndoCurrentPosition(
            activePointer = generation,
            principal = principal,
            consumptionRef = original.eventId,
            stockItem = original.stockItemRef,
            unit = unit,
            sourceRef = original.sourceRef,
            consumedQuantity = HouseholdNeedQuantity(original.quantityThousandths.toLong(), unit),
            ledgerRevision = revision,
            remainingUndoableThousandths = remaining,
            consumedAtMs = original.occurredAtMs,
            newUndoIntentAvailable = undos.isEmpty(),
        )
    }

    fun apply(
        review: HouseholdStockConsumptionUndoDevDecision.ReviewOnly,
        recordedAtMs: Long,
    ): Boolean {
        val request = review.request
        val intent = request.intent
        val context = request.context
        if (!context.isolatedDev || !context.foreground || context.hostEpoch <= 0L ||
            context.principal != principal || context.activePointer != generation ||
            request.expectedLedgerRevision <= 0L || recordedAtMs < 0L ||
            !token(principal) || !token(request.undoIntentKey) ||
            !token(intent.targetObjectId) || intent.targetObjectId == request.undoIntentKey ||
            intent.sourceOperationId != HouseholdStockConsumptionReview.OPERATION
        ) return false
        val descriptor = review.contract
        if (descriptor.operationId != OperationId("undo.execute") ||
            descriptor.capabilityId != CapabilityId("N-016") ||
            descriptor.canonicalExecutor != ExecutorId("undo-coordinator") ||
            descriptor.executionProfile != ExecutionProfile.REALITY_MUTATION ||
            descriptor.exposure != ContractExposure.BINDABLE
        ) return false
        val origin = review.origin
        val original = origin.stockReview
        if (origin.sourceReceipt != intent.sourceReceipt ||
            origin.sourceReview != intent.sourceReview ||
            origin.sourceReceipt.planFingerprint != origin.sourceReview.planFingerprint ||
            original.payload != intent.originalCreatePayload ||
            HouseholdStockConsumptionReview.decode(intent.originalCreatePayload) != original ||
            original.request.context.principal != principal ||
            original.request.intentKey != intent.targetObjectId ||
            original.request.reservationRef != null ||
            !token(original.request.sourceRef)
        ) return false
        val rows = RealityHousehold15ConsumptionRowsPort.captureConnection(db)
        if (rows.consumptions.size >= 8192 ||
            recordedAtMs < intent.cancelledAtEpochMillis ||
            recordedAtMs < (rows.consumptions.lastOrNull()?.recordedAtMs ?: 0L)
        ) return false
        val actual = position(intent.targetObjectId, rows) ?: return false
        if (actual != review.position || request.expectedLedgerRevision != actual.ledgerRevision ||
            actual.ledgerRevision == Long.MAX_VALUE ||
            actual.remainingUndoableThousandths != actual.consumedQuantity.thousandths ||
            !actual.newUndoIntentAvailable ||
            actual.consumedAtMs > intent.cancelledAtEpochMillis ||
            actual.consumptionRef != original.request.intentKey ||
            actual.stockItem != original.request.stockItem ||
            actual.sourceRef != original.request.sourceRef ||
            actual.unit != original.request.quantity.unit ||
            actual.consumedQuantity != original.request.quantity
        ) return false
        val originRow = rows.consumptions.singleOrNull {
            it.eventId == actual.consumptionRef && it.type == RealityHousehold15ConsumptionType.CONSUME
        } ?: return false
        if (intent.cancelledAtEpochMillis < originRow.recordedAtMs ||
            originRow.quantityThousandths.toLong() != actual.consumedQuantity.thousandths
        ) return false
        val key = request.undoIntentKey
        if (rows.historical.historical.historical.receipts.receipts.any {
                it.receiptId == key || it.intentKey == key
            } || rows.historical.historical.compensations.any {
                it.compensationId == key || it.intentKey == key
            } || rows.historical.reservations.any {
                it.eventId == key || it.intentKey == key
            } || rows.consumptions.any {
                it.eventId == key || it.intentKey == key
            }) return false

        // A FULL undo only: never infer partial compensation from an unextended payload.
        val row = RealityHousehold15ConsumptionRow(
            eventId = key,
            intentKey = key,
            type = RealityHousehold15ConsumptionType.CONSUME_UNDO,
            consumptionRef = actual.consumptionRef,
            stockItemRef = actual.stockItem,
            unit = actual.unit.name,
            quantityThousandths = actual.consumedQuantity.thousandths.toInt(),
            principal = principal,
            sourceRef = actual.sourceRef,
            reservationRef = null,
            occurredAtMs = intent.cancelledAtEpochMillis,
            recordedAtMs = recordedAtMs,
            revision = actual.ledgerRevision + 1L,
        )
        db.prepare(
            "INSERT INTO household_stock_consumption_event(" +
                "event_id,intent_key,event_type,consumption_ref,stock_item_ref,unit," +
                "quantity_thousandths,principal,source_ref,reservation_ref," +
                "occurred_at_ms,recorded_at_ms,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ).use { statement ->
            statement.bindText(1, row.eventId)
            statement.bindText(2, row.intentKey)
            statement.bindText(3, row.type.name)
            statement.bindText(4, row.consumptionRef)
            statement.bindText(5, row.stockItemRef)
            statement.bindText(6, row.unit)
            statement.bindLong(7, row.quantityThousandths.toLong())
            statement.bindText(8, row.principal)
            statement.bindText(9, row.sourceRef)
            statement.bindNull(10)
            statement.bindLong(11, row.occurredAtMs)
            statement.bindLong(12, row.recordedAtMs)
            statement.bindLong(13, row.revision)
            statement.step()
        }
        val after = RealityHousehold15ConsumptionRowsPort.captureConnection(db)
        check(after.historical == rows.historical && after.consumptions == rows.consumptions + row) {
            "SQL15 Undo readback must preserve all historical rows and append once"
        }
        return true
    }
}
