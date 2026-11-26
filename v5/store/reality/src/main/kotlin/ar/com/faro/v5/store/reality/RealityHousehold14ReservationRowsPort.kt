package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Path

enum class RealityHousehold14ReservationType { RESERVE, RELEASE }

data class RealityHousehold14ReservationRow(
    val eventId: String,
    val intentKey: String,
    val type: RealityHousehold14ReservationType,
    val reservationRef: String,
    val stockItemRef: String,
    val unit: String,
    val quantityThousandths: Int,
    val principal: String,
    val recordedAtMs: Long,
    val revision: Long,
)

data class RealityHousehold14ReservationRows(
    val historical: RealityHousehold13CompensationRows,
    val reservations: List<RealityHousehold14ReservationRow>,
)

enum class RealityHousehold14RowsRestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/** The generation owner keeps the exclusive lease on the inactive SQL14 file throughout each call. */
object RealityHousehold14ReservationRowsPort {
    private const val TABLE = "household_stock_reservation_event"
    private const val MAX_ROWS = 8_192
    private const val MAX_SECTION_BYTES = 4 * 1024 * 1024
    private const val COLUMNS = "event_id,intent_key,event_type,reservation_ref,stock_item_ref,unit," +
        "quantity_thousandths,principal,recorded_at_ms,revision"

    fun captureTrustedSealedStageFourteen(
        file: Path,
        requireExclusiveStage: () -> Unit,
    ): RealityHousehold14ReservationRows {
        requireExclusiveStage()
        val path = sealedPath(file)
        return BundledSQLiteDriver().open(
            path.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.transaction("BEGIN DEFERRED TRANSACTION") {
                captureConnection(db).also { requireExclusiveStage() }
            }
        }
    }

    fun restoreOrVerifyTrustedSealedStageFourteen(
        file: Path,
        incoming: RealityHousehold14ReservationRows,
        requireExclusiveStage: () -> Unit,
    ): RealityHousehold14RowsRestoreDisposition {
        val history = incoming.historical
        val historical = history.historical
        require(incoming.reservations.size <= MAX_ROWS && history.compensations.size <= MAX_ROWS &&
            historical.purchases.size <= MAX_ROWS && historical.receipts.receipts.size <= MAX_ROWS)
        val wanted = RealityHousehold14ReservationRows(
            history.copy(historical = historical.copy(purchases = historical.purchases.toList(),
                receipts = historical.receipts.copy(receipts = historical.receipts.receipts.toList())),
                compensations = history.compensations.toList()),
            incoming.reservations.toList(),
        )
        requireExclusiveStage()
        val path = sealedPath(file)
        val result = BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.prepare("PRAGMA journal_mode").use {
                check(it.step() && it.getText(0) == "wal") { "SQL14 requires a WAL-mode stage" }
            }
            val disposition = db.transaction("BEGIN IMMEDIATE TRANSACTION") {
                val current = captureConnection(db)
                val output = if (current == wanted) {
                    RealityHousehold14RowsRestoreDisposition.ALREADY_IDENTICAL
                } else {
                    check(current.historical == wanted.historical && current.reservations.isEmpty()) {
                        "SQL14 refuses a divergent staged target"
                    }
                    writeRows(db, wanted.reservations)
                    check(captureConnection(db) == wanted) { "SQL14 staged readback differs within transaction" }
                    RealityHousehold14RowsRestoreDisposition.RESTORED
                }
                requireExclusiveStage()
                output
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "SQL14 committed but staged checkpoint is busy" }
            }
            disposition
        }
        check(captureTrustedSealedStageFourteen(path, requireExclusiveStage) == wanted) {
            "SQL14 closed staged readback differs"
        }
        return result
    }

    internal fun captureConnection(db: SQLiteConnection): RealityHousehold14ReservationRows {
        val history = RealityHousehold13CompensationRowsPort.captureHistoryOnFourteen(db)
        db.prepare(
            "SELECT 1 FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type='table' AND fk.\"table\" COLLATE NOCASE = '$TABLE' LIMIT 1",
        ).use { check(!it.step()) { "SQL14 reservation table has unexpected inbound foreign keys" } }
        return RealityHousehold14ReservationRows(history, readRows(db)).also(::validateBalances)
    }

    /** Historical SQL14 rows on a verified SQL15 STAGED connection; never used by the SQL14 API. */
    internal fun captureConnectionOnFifteen(db: SQLiteConnection): RealityHousehold14ReservationRows {
        RealityHousehold14ReservationSchema.requireDefinitions(db, RealityHousehold15ConsumptionSchema.VERSION)
        val history = RealityHousehold13CompensationRowsPort.captureHistoryOnFifteen(db)
        db.prepare(
            "SELECT 1 FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type='table' AND fk.\"table\" COLLATE NOCASE = '$TABLE' LIMIT 1",
        ).use { check(!it.step()) { "SQL15 reservation table has unexpected inbound foreign keys" } }
        return RealityHousehold14ReservationRows(history, readRows(db)).also(::validateBalances)
    }

    private fun sealedPath(file: Path): Path {
        check(file.toAbsolutePath() == file.toAbsolutePath().normalize()) { "SQL14 rejects staged path aliases" }
        return Household11ReceiptRowsSql.checkFile(file)
    }

    private fun readRows(db: SQLiteConnection): List<RealityHousehold14ReservationRow> = db.prepare(
        "SELECT $COLUMNS,CAST(event_id AS BLOB),CAST(intent_key AS BLOB),CAST(reservation_ref AS BLOB)," +
            "CAST(stock_item_ref AS BLOB),CAST(principal AS BLOB) FROM $TABLE ORDER BY revision LIMIT ${MAX_ROWS + 1}",
    ).use { stmt ->
        buildList {
            var encodedBytes = Int.SIZE_BYTES
            val refColumns = listOf(0, 1, 3, 4, 7)
            while (stmt.step()) {
                check(size < MAX_ROWS) { "SQL14 reservation count exceeds budget" }
                val refs = refColumns.mapIndexed { index, column ->
                    val bytes = stmt.getBlob(10 + index)
                    val value = stmt.getText(column)
                    check(bytes.size in 1..384 && value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                        "SQL14 reservation reference has invalid UTF-8"
                    }
                    encodedBytes += Short.SIZE_BYTES + bytes.size
                    value
                }
                val unit = stmt.getText(5)
                encodedBytes += 1 + Int.SIZE_BYTES + 2 * Long.SIZE_BYTES + Short.SIZE_BYTES + unit.length
                check(encodedBytes <= MAX_SECTION_BYTES) { "SQL14 reservation section exceeds budget" }
                val quantity = stmt.getLong(6)
                check(quantity in 1L..Int.MAX_VALUE.toLong()) { "SQL14 reservation quantity outside range" }
                add(RealityHousehold14ReservationRow(
                    refs[0], refs[1], RealityHousehold14ReservationType.valueOf(stmt.getText(2)),
                    refs[2], refs[3], unit, quantity.toInt(), refs[4], stmt.getLong(8), stmt.getLong(9),
                ))
            }
        }
    }

    private data class StockKey(val item: String, val unit: String, val principal: String)
    private data class Delta(val revision: Long, val key: StockKey, val amount: Long, val physical: Boolean)
    private data class OpenReservation(val origin: RealityHousehold14ReservationRow, var remaining: Long)

    private fun validateBalances(value: RealityHousehold14ReservationRows) {
        if (value.reservations.isEmpty()) return
        val receipts = value.historical.historical.receipts.receipts
        val compensation = value.historical.compensations
        val purchases = value.historical.historical.purchases.associate { it.purchaseRef to
            StockKey(it.stockItemRef, it.unit, it.confirmedBy) }
        val historyRevisions = receipts.mapTo(HashSet()) { it.revision }
        compensation.mapTo(historyRevisions) { it.revision }
        val open = mutableMapOf<String, OpenReservation>()
        val changes = buildList {
            for (receipt in receipts) {
                val key = purchases[receipt.purchaseRef] ?: continue
                add(Delta(receipt.revision, key, receipt.quantityThousandths.toLong(), physical = true))
            }
            for (undo in compensation) {
                add(Delta(undo.revision, purchases.getValue(undo.purchaseRef), -undo.quantityThousandths.toLong(),
                    physical = true))
            }
            for (row in value.reservations) {
                check(row.revision > 0 && row.revision !in historyRevisions) { "SQL14 reservation revision collides" }
                val key = StockKey(row.stockItemRef, row.unit, row.principal)
                val amount = row.quantityThousandths.toLong()
                when (row.type) {
                    RealityHousehold14ReservationType.RESERVE -> {
                        check(row.reservationRef == row.eventId && open.put(row.eventId, OpenReservation(row, amount)) == null) {
                            "SQL14 invalid reservation origin"
                        }
                        add(Delta(row.revision, key, amount, physical = false))
                    }
                    RealityHousehold14ReservationType.RELEASE -> {
                        val prior = checkNotNull(open[row.reservationRef]) { "SQL14 invalid release origin" }
                        check(row.eventId != row.reservationRef && prior.origin.stockItemRef == row.stockItemRef &&
                            prior.origin.unit == row.unit && prior.origin.principal == row.principal &&
                            prior.origin.revision < row.revision && prior.origin.recordedAtMs <= row.recordedAtMs &&
                            amount <= prior.remaining) { "SQL14 invalid release quantity or identity" }
                        prior.remaining -= amount
                        add(Delta(row.revision, key, -amount, physical = false))
                    }
                }
            }
        }.sortedBy { it.revision }
        val physical = mutableMapOf<StockKey, Long>()
        val reserved = mutableMapOf<StockKey, Long>()
        for (delta in changes) {
            val balances = if (delta.physical) physical else reserved
            balances[delta.key] = balances.getOrDefault(delta.key, 0L) + delta.amount
            check(reserved.getOrDefault(delta.key, 0L) in 0L..physical.getOrDefault(delta.key, 0L)) {
                "SQL14 reservations exceed physical ledger balance"
            }
        }
    }

    private fun writeRows(db: SQLiteConnection, rows: List<RealityHousehold14ReservationRow>) {
        db.prepare("INSERT INTO $TABLE($COLUMNS) VALUES (?,?,?,?,?,?,?,?,?,?)").use { stmt ->
            var previous = 0L
            for (row in rows) {
                require(row.revision > previous) { "SQL14 reservations must be ordered by revision" }
                for (ref in listOf(row.eventId, row.intentKey, row.reservationRef, row.stockItemRef, row.principal)) {
                    val bytes = ref.toByteArray(Charsets.UTF_8)
                    require(bytes.size in 1..384 && bytes.toString(Charsets.UTF_8) == ref) {
                        "SQL14 reservation reference cannot be encoded as UTF-8"
                    }
                }
                previous = row.revision
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
                stmt.reset()
            }
        }
    }

    private fun <T> SQLiteConnection.transaction(begin: String, block: () -> T): T {
        execSQL(begin)
        try {
            val value = block()
            execSQL("COMMIT TRANSACTION")
            return value
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
