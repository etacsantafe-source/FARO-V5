package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Path

data class RealityHousehold13CompensationRow(
    val compensationId: String,
    val intentKey: String,
    val receiptId: String,
    val purchaseRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val principal: String,
    val recordedAtMs: Long,
    val revision: Long,
)

data class RealityHousehold13CompensationRows(
    val historical: RealityHousehold12PurchaseReceiptRows,
    val compensations: List<RealityHousehold13CompensationRow>,
)

enum class RealityHousehold13RowsRestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/** Only for the generation owner's pinned and sealed inactive SQL13 stage. */
object RealityHousehold13CompensationRowsPort {
    private const val MAX_ROWS = 8_192
    private const val MAX_SECTION_BYTES = 4 * 1024 * 1024
    private const val COLUMNS =
        "compensation_id,intent_key,receipt_id,purchase_ref,quantity_thousandths,unit,principal,recorded_at_ms,revision"

    fun captureTrustedSealedStageThirteen(file: Path): RealityHousehold13CompensationRows {
        val path = Household11ReceiptRowsSql.checkFile(file)
        return BundledSQLiteDriver().open(
            path.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.transaction("BEGIN DEFERRED TRANSACTION") { captureConnection(db) }
        }
    }

    fun restoreOrVerifyTrustedSealedStageThirteen(
        file: Path,
        incoming: RealityHousehold13CompensationRows,
        beforeCommit: () -> Unit = {},
    ): RealityHousehold13RowsRestoreDisposition {
        require(incoming.historical.purchases.size <= MAX_ROWS && incoming.historical.receipts.receipts.size <= MAX_ROWS &&
            incoming.compensations.size <= MAX_ROWS) { "SQL13 incoming snapshot exceeds row budget" }
        val historical = incoming.historical
        val wanted = RealityHousehold13CompensationRows(
            RealityHousehold12PurchaseReceiptRows(historical.purchases.toList(),
                historical.receipts.copy(receipts = historical.receipts.receipts.toList())),
            incoming.compensations.toList(),
        )
        RealityHousehold12PurchaseRowsPort.validate(wanted.historical, enforceGross = false)
        validate(wanted)
        val path = Household11ReceiptRowsSql.checkFile(file)
        val result = BundledSQLiteDriver().open(
            path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            val disposition = db.transaction("BEGIN IMMEDIATE TRANSACTION") {
                val current = captureConnection(db)
                if (current == wanted) {
                    RealityHousehold13RowsRestoreDisposition.ALREADY_IDENTICAL
                } else {
                    val ledger = current.historical.receipts
                    val emptyLedger = ledger.revision == 0L && ledger.receipts.isEmpty()
                    check(current.compensations.isEmpty() && current.historical.purchases.isEmpty() &&
                        (emptyLedger || ledger == wanted.historical.receipts)) { "SQL13 refuses a divergent staged target" }
                    if (emptyLedger) with(RealityHousehold11ReceiptRowsPort) { db.writeReceiptRows(wanted.historical.receipts) }
                    RealityHousehold12PurchaseRowsPort.writePurchases(db, wanted.historical.purchases)
                    writeCompensations(db, wanted.compensations)
                    check(captureConnection(db) == wanted) { "SQL13 staged readback differs within transaction" }
                    beforeCommit()
                    RealityHousehold13RowsRestoreDisposition.RESTORED
                }
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "SQL13 staged checkpoint is busy" }
            }
            disposition
        }
        check(captureTrustedSealedStageThirteen(path) == wanted) { "SQL13 closed staged readback differs" }
        return result
    }

    internal fun captureConnection(db: SQLiteConnection): RealityHousehold13CompensationRows =
        captureHistory(db, RealityHousehold13CompensationSchema.VERSION)

    internal fun captureHistoryOnFourteen(db: SQLiteConnection): RealityHousehold13CompensationRows {
        RealityHousehold14ReservationSchema.requireDefinitions(db)
        return captureHistory(db, RealityHousehold14ReservationSchema.VERSION)
    }

    private fun captureHistory(db: SQLiteConnection, expectedVersion: Int): RealityHousehold13CompensationRows {
        val historical = RealityHousehold12PurchaseRowsPort.readCurrentHouseholdRows(db, expectedVersion)
        RealityHousehold13CompensationSchema.requireDefinitions(db)
        return RealityHousehold13CompensationRows(historical, readCompensations(db)).also(::validate)
    }

    private fun readCompensations(db: SQLiteConnection): List<RealityHousehold13CompensationRow> = db.prepare(
        "SELECT $COLUMNS,CAST(compensation_id AS BLOB),CAST(intent_key AS BLOB),CAST(receipt_id AS BLOB)," +
            "CAST(purchase_ref AS BLOB),CAST(principal AS BLOB) FROM household_stock_compensation",
    ).use { stmt ->
        buildList {
            var encodedBytes = Int.SIZE_BYTES
            while (stmt.step()) {
                check(size < MAX_ROWS) { "SQL13 compensation count exceeds budget" }
                val references = listOf(0, 1, 2, 3, 6).mapIndexed { index, column ->
                    val bytes = stmt.getBlob(9 + index)
                    val value = stmt.getText(column)
                    check(bytes.size in 1..512 && value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                        "SQL13 compensation reference not canonical UTF-8"
                    }
                    encodedBytes += Short.SIZE_BYTES + bytes.size
                    value
                }
                val unit = stmt.getText(5)
                encodedBytes += Int.SIZE_BYTES + 2 * Long.SIZE_BYTES + Short.SIZE_BYTES + unit.length
                check(encodedBytes <= MAX_SECTION_BYTES) { "SQL13 compensation section exceeds budget" }
                val quantity = stmt.getLong(4)
                check(quantity in 1L..Int.MAX_VALUE.toLong()) { "SQL13 compensation quantity outside range" }
                add(RealityHousehold13CompensationRow(
                    references[0], references[1], references[2], references[3], quantity.toInt(), unit,
                    references[4], stmt.getLong(7), stmt.getLong(8),
                ))
            }
        }.sortedBy { it.compensationId }
    }

    private data class LedgerDelta(val revision: Long, val purchaseRef: String, val quantity: Long)

    private fun validate(snapshot: RealityHousehold13CompensationRows) {
        val ledger = snapshot.historical.receipts
        val purchases = snapshot.historical.purchases.associateBy { it.purchaseRef }
        val receipts = ledger.receipts.associateBy { it.receiptId }
        val receiptRevisions = ledger.receipts.mapTo(HashSet()) { it.revision }
        val compensationRevisions = HashSet<Long>()
        val undone = HashSet<String>()
        val intents = HashSet<String>()
        var previous: String? = null
        var encodedSize = Int.SIZE_BYTES
        val events = ledger.receipts.mapNotNullTo(mutableListOf()) {
            if (it.purchaseRef in purchases) LedgerDelta(it.revision, it.purchaseRef, it.quantityThousandths.toLong())
            else null
        }
        for (row in snapshot.compensations) {
            require(previous == null || previous < row.compensationId) { "SQL13 compensation ordering differs" }
            previous = row.compensationId
            for (ref in listOf(row.compensationId, row.intentKey, row.receiptId, row.purchaseRef, row.principal)) {
                val bytes = ref.toByteArray(Charsets.UTF_8)
                require(ref.codePointCount(0, ref.length) in 1..128 && ref == ref.trim() &&
                    '\u0000' !in ref && bytes.toString(Charsets.UTF_8) == ref) { "SQL13 compensation reference invalid" }
                encodedSize += Short.SIZE_BYTES + bytes.size
            }
            require(compensationRevisions.add(row.revision) && row.revision !in receiptRevisions &&
                row.revision in 1L..ledger.revision && undone.add(row.receiptId) && intents.add(row.intentKey)) {
                "SQL13 duplicate compensation or invalid ledger revision"
            }
            val original = requireNotNull(receipts[row.receiptId]) { "SQL13 compensation receipt missing" }
            val purchase = requireNotNull(purchases[row.purchaseRef]) { "SQL13 compensation purchase missing" }
            require(original.purchaseRef == purchase.purchaseRef && row.principal == purchase.confirmedBy &&
                row.unit == original.unit && row.quantityThousandths == original.quantityThousandths &&
                row.recordedAtMs >= original.recordedAtMs && row.revision > original.revision) {
                "SQL13 compensation differs from its confirmed receipt"
            }
            encodedSize += Int.SIZE_BYTES + 2 * Long.SIZE_BYTES + Short.SIZE_BYTES + row.unit.length
            require(encodedSize <= MAX_SECTION_BYTES) { "SQL13 compensation section exceeds budget" }
            events += LedgerDelta(row.revision, row.purchaseRef, -row.quantityThousandths.toLong())
        }
        val received = mutableMapOf<String, Long>()
        for (event in events.sortedBy { it.revision }) {
            val net = received.getOrDefault(event.purchaseRef, 0L) + event.quantity
            require(net in 0L..purchases.getValue(event.purchaseRef).quantityThousandths.toLong()) {
                "SQL13 chronology exceeds purchase or reverses unreceived stock"
            }
            received[event.purchaseRef] = net
        }
    }

    private fun writeCompensations(db: SQLiteConnection, rows: List<RealityHousehold13CompensationRow>) {
        db.prepare("INSERT INTO household_stock_compensation($COLUMNS) VALUES (?,?,?,?,?,?,?,?,?)").use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.compensationId)
                stmt.bindText(2, row.intentKey)
                stmt.bindText(3, row.receiptId)
                stmt.bindText(4, row.purchaseRef)
                stmt.bindLong(5, row.quantityThousandths.toLong())
                stmt.bindText(6, row.unit)
                stmt.bindText(7, row.principal)
                stmt.bindLong(8, row.recordedAtMs)
                stmt.bindLong(9, row.revision)
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun <T> SQLiteConnection.transaction(begin: String, block: () -> T): T {
        execSQL(begin)
        try {
            val result = block()
            execSQL("COMMIT TRANSACTION")
            return result
        } catch (failure: Throwable) {
            try {
                execSQL("ROLLBACK TRANSACTION")
            } catch (rollback: Throwable) {
                failure.addSuppressed(rollback)
            }
            throw failure
        }
    }
}
