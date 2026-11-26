package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Path

enum class RealityHousehold15ConsumptionType { CONSUME, CONSUME_UNDO }

data class RealityHousehold15ConsumptionRow(
    val eventId: String,
    val intentKey: String,
    val type: RealityHousehold15ConsumptionType,
    val consumptionRef: String,
    val stockItemRef: String,
    val unit: String,
    val quantityThousandths: Int,
    val principal: String,
    val sourceRef: String,
    val reservationRef: String?,
    val occurredAtMs: Long,
    val recordedAtMs: Long,
    val revision: Long,
)

data class RealityHousehold15ConsumptionRows(
    val historical: RealityHousehold14ReservationRows,
    val consumptions: List<RealityHousehold15ConsumptionRow>,
)

enum class RealityHousehold15RowsRestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/** Only on a pinned, sealed, INACTIVE SQL15 staged file under the generation owner's exclusive lease. */
object RealityHousehold15ConsumptionRowsPort {
    private const val TABLE = "household_stock_consumption_event"
    private const val MAX_ROWS = 8_192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")
    private const val COLUMNS =
        "event_id,intent_key,event_type,consumption_ref,stock_item_ref,unit," +
            "quantity_thousandths,principal,source_ref,reservation_ref,occurred_at_ms,recorded_at_ms,revision"

    fun captureTrustedSealedStageFifteen(
        file: Path,
        requireExclusiveStage: () -> Unit,
    ): RealityHousehold15ConsumptionRows {
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

    fun restoreOrVerifyTrustedSealedStageFifteen(
        file: Path,
        incoming: RealityHousehold15ConsumptionRows,
        requireExclusiveStage: () -> Unit,
    ): RealityHousehold15RowsRestoreDisposition {
        require(incoming.consumptions.size <= MAX_ROWS) { "SQL15 consumption count exceeds bound" }
        val wanted = incoming.copy(consumptions = incoming.consumptions.toList())
        validate(wanted)
        requireExclusiveStage()
        val path = sealedPath(file)
        val result = BundledSQLiteDriver().open(
            path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.prepare("PRAGMA journal_mode").use {
                check(it.step() && it.getText(0) == "wal") { "SQL15 rows require WAL staged file" }
            }
            val disposition = db.transaction("BEGIN IMMEDIATE TRANSACTION") {
                val current = captureConnection(db)
                check(current.historical == wanted.historical) {
                    "SQL15 refuses altered purchase, receipt, compensation or reservation history"
                }
                val result = when {
                    current == wanted -> RealityHousehold15RowsRestoreDisposition.ALREADY_IDENTICAL
                    current.consumptions.isEmpty() -> {
                        writeEvents(db, wanted.consumptions)
                        check(captureConnection(db) == wanted) { "SQL15 staged readback mismatch" }
                        RealityHousehold15RowsRestoreDisposition.RESTORED
                    }
                    else -> error("SQL15 refuses divergent consumption ledger")
                }
                requireExclusiveStage()
                result
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) {
                    "SQL15 committed but staged checkpoint is busy; recover with readback"
                }
            }
            disposition
        }
        check(captureTrustedSealedStageFifteen(path, requireExclusiveStage) == wanted) {
            "SQL15 sealed readback differs"
        }
        return result
    }

    internal fun captureConnection(db: SQLiteConnection): RealityHousehold15ConsumptionRows {
        RealityHousehold15ConsumptionSchema.requireDefinitions(db)
        db.prepare(
            "SELECT 1 FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type='table' AND fk.\"table\" COLLATE NOCASE = '$TABLE' LIMIT 1",
        ).use { check(!it.step()) { "SQL15 unexpected inbound foreign key" } }
        val historical = RealityHousehold14ReservationRowsPort.captureConnectionOnFifteen(db)
        return RealityHousehold15ConsumptionRows(historical, readEvents(db)).also(::validate)
    }

    private fun sealedPath(file: Path): Path {
        check(file.toAbsolutePath() == file.toAbsolutePath().normalize()) {
            "SQL15 rejects aliased staged paths"
        }
        return Household11ReceiptRowsSql.checkFile(file)
    }

    private fun readEvents(db: SQLiteConnection): List<RealityHousehold15ConsumptionRow> {
        val select = "event_id,intent_key,event_type,consumption_ref,stock_item_ref,unit," +
            "quantity_thousandths,principal,source_ref,COALESCE(reservation_ref,'')," +
            "occurred_at_ms,recorded_at_ms,revision"
        val refs = "CAST(event_id AS BLOB),CAST(intent_key AS BLOB),CAST(consumption_ref AS BLOB)," +
            "CAST(stock_item_ref AS BLOB),CAST(principal AS BLOB),CAST(source_ref AS BLOB)," +
            "CAST(COALESCE(reservation_ref,'') AS BLOB)"
        return db.prepare(
            "SELECT $select,$refs FROM $TABLE ORDER BY revision LIMIT ${MAX_ROWS + 1}",
        ).use { stmt ->
            buildList {
                var bytesBudget = Int.SIZE_BYTES
                val referenceColumns = listOf(0, 1, 3, 4, 7, 8, 9)
                while (stmt.step()) {
                    check(size < MAX_ROWS) { "SQL15 consumption rows exceed count budget" }
                    val refs = referenceColumns.mapIndexed { index, column ->
                        val raw = stmt.getBlob(13 + index)
                        val value = stmt.getText(column)
                        check(raw.size <= 384 && value.toByteArray(Charsets.UTF_8).contentEquals(raw)) {
                            "SQL15 consumption reference has malformed UTF-8"
                        }
                        bytesBudget += Short.SIZE_BYTES + raw.size
                        value
                    }
                    val qty = stmt.getLong(6)
                    check(qty in 1L..Int.MAX_VALUE.toLong()) { "SQL15 quantity outside Int range" }
                    bytesBudget += 1 + Int.SIZE_BYTES + 3 * Long.SIZE_BYTES + 2 + stmt.getText(5).length
                    check(bytesBudget <= MAX_BYTES) { "SQL15 consumption bytes exceed budget" }
                    add(RealityHousehold15ConsumptionRow(
                        refs[0], refs[1], RealityHousehold15ConsumptionType.valueOf(stmt.getText(2)),
                        refs[2], refs[3], stmt.getText(5), qty.toInt(), refs[4], refs[5],
                        refs[6].takeIf { it.isNotEmpty() }, stmt.getLong(10), stmt.getLong(11),
                        stmt.getLong(12),
                    ))
                }
            }
        }
    }

    private data class Key(val item: String, val unit: String, val principal: String)
    private data class Delta(val revision: Long, val key: Key, val amount: Long, val isReservation: Boolean)
    private fun RealityHousehold15ConsumptionRow.key() = Key(stockItemRef, unit, principal)
    private fun RealityHousehold14ReservationRow.key() = Key(stockItemRef, unit, principal)

    private fun validate(value: RealityHousehold15ConsumptionRows) {
        val rows = value.consumptions
        require(rows.size <= MAX_ROWS) { "SQL15 consumption count exceeds bounded ledger" }
        val history = value.historical
        val purchases = history.historical.historical.purchases.associateBy { it.purchaseRef }
        val receipts = history.historical.historical.receipts.receipts
        val compensations = history.historical.compensations
        val reservations = history.reservations
        val revisions = HashSet<Long>()
        val ids = HashSet<String>()
        val intents = HashSet<String>()
        val deltas = mutableListOf<Delta>()
        for (receipt in receipts) {
            check(revisions.add(receipt.revision) && ids.add(receipt.receiptId) &&
                intents.add(receipt.intentKey)) { "SQL15 duplicate historical receipt" }
            val purchase = purchases[receipt.purchaseRef] ?: continue
            deltas += Delta(receipt.revision, Key(purchase.stockItemRef, purchase.unit, purchase.confirmedBy),
                receipt.quantityThousandths.toLong(), false)
        }
        for (undo in compensations) {
            check(revisions.add(undo.revision) && ids.add(undo.compensationId) &&
                intents.add(undo.intentKey)) { "SQL15 duplicate historical receipt undo" }
            val purchase = requireNotNull(purchases[undo.purchaseRef]) { "SQL15 undo purchase missing" }
            deltas += Delta(undo.revision, Key(purchase.stockItemRef, purchase.unit, purchase.confirmedBy),
                -undo.quantityThousandths.toLong(), false)
        }
        for (reserve in reservations) {
            check(revisions.add(reserve.revision) && ids.add(reserve.eventId) &&
                intents.add(reserve.intentKey)) { "SQL15 duplicate historical reservation" }
            val signedAmount = reserve.quantityThousandths.toLong() *
                (if (reserve.type == RealityHousehold14ReservationType.RESERVE) 1 else -1)
            deltas += Delta(reserve.revision, reserve.key(), signedAmount, true)
        }

        var lastConsumptionRevision = 0L
        val origins = mutableMapOf<String, RealityHousehold15ConsumptionRow>()
        val remaining = mutableMapOf<String, Long>()
        var bytesBudget = Int.SIZE_BYTES
        for (row in rows) {
            val names = listOf(row.eventId, row.intentKey, row.consumptionRef,
                row.stockItemRef, row.principal, row.sourceRef)
            names.forEach(::requireReference)
            bytesBudget += names.sumOf { Short.SIZE_BYTES + it.toByteArray(Charsets.UTF_8).size }
            bytesBudget += Short.SIZE_BYTES + 1 + Int.SIZE_BYTES + 3 * Long.SIZE_BYTES + row.unit.length + 2
            require(bytesBudget <= MAX_BYTES) { "SQL15 consumption snapshot exceeds byte budget" }
            require(row.reservationRef == null) {
                "SQL15 consumption of reserved stock awaits separate A2C1 contract"
            }
            require(row.unit in units && row.quantityThousandths > 0 &&
                row.occurredAtMs >= 0 && row.recordedAtMs >= row.occurredAtMs) {
                "SQL15 consumption malformed fields"
            }
            require(row.revision > lastConsumptionRevision && revisions.add(row.revision) &&
                ids.add(row.eventId) && intents.add(row.intentKey)) {
                "SQL15 unordered or colliding consumption event"
            }
            lastConsumptionRevision = row.revision
            val quantity = row.quantityThousandths.toLong()
            when (row.type) {
                RealityHousehold15ConsumptionType.CONSUME -> {
                    require(row.consumptionRef == row.eventId) { "SQL15 invalid consumption reference" }
                    origins[row.eventId] = row
                    remaining[row.eventId] = quantity
                    deltas += Delta(row.revision, row.key(), -quantity, false)
                }
                RealityHousehold15ConsumptionType.CONSUME_UNDO -> {
                    require(row.consumptionRef != row.eventId) { "SQL15 Undo cannot refer to itself" }
                    val origin = requireNotNull(origins[row.consumptionRef]) {
                        "SQL15 Undo origin absent or not earlier"
                    }
                    require(origin.key() == row.key() && origin.sourceRef == row.sourceRef &&
                        origin.reservationRef == row.reservationRef &&
                        row.occurredAtMs >= origin.occurredAtMs &&
                        row.recordedAtMs >= origin.recordedAtMs &&
                        quantity <= requireNotNull(remaining[row.consumptionRef])) {
                        "SQL15 Undo origin or balance invalid"
                    }
                    remaining[row.consumptionRef] = remaining.getValue(row.consumptionRef) - quantity
                    deltas += Delta(row.revision, row.key(), quantity, false)
                }
            }
        }

        val physical = mutableMapOf<Key, Long>()
        val reserved = mutableMapOf<Key, Long>()
        var lastRevision = 0L
        for (event in deltas.sortedBy { it.revision }) {
            require(event.revision > lastRevision) { "SQL15 global revision collision" }
            lastRevision = event.revision
            val totals = if (event.isReservation) reserved else physical
            totals[event.key] = totals.getOrDefault(event.key, 0L) + event.amount
            val p = physical.getOrDefault(event.key, 0L)
            val r = reserved.getOrDefault(event.key, 0L)
            require(p >= 0L && r >= 0L && r <= p) {
                "SQL15 consumption exceeds non-reserved physical stock"
            }
        }
    }

    private fun requireReference(value: String) {
        val raw = value.toByteArray(Charsets.UTF_8)
        require(value.codePointCount(0, value.length) in 1..128 && raw.size <= 384 &&
            raw.toString(Charsets.UTF_8) == value &&
            value.none { it <= ' ' || it == '\u007f' }) {
            "SQL15 consumption reference invalid"
        }
    }

    private fun writeEvents(db: SQLiteConnection, rows: List<RealityHousehold15ConsumptionRow>) {
        db.prepare("INSERT INTO $TABLE ($COLUMNS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)").use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.eventId)
                stmt.bindText(2, row.intentKey)
                stmt.bindText(3, row.type.name)
                stmt.bindText(4, row.consumptionRef)
                stmt.bindText(5, row.stockItemRef)
                stmt.bindText(6, row.unit)
                stmt.bindLong(7, row.quantityThousandths.toLong())
                stmt.bindText(8, row.principal)
                stmt.bindText(9, row.sourceRef)
                if (row.reservationRef == null) stmt.bindNull(10)
                else stmt.bindText(10, row.reservationRef)
                stmt.bindLong(11, row.occurredAtMs)
                stmt.bindLong(12, row.recordedAtMs)
                stmt.bindLong(13, row.revision)
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
