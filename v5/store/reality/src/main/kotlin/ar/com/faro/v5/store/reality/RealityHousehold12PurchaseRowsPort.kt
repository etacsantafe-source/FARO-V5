package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Path

data class RealityHousehold12PurchaseRow(
    val purchaseRef: String,
    val stockItemRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val confirmedBy: String,
    val confirmedAtMs: Long,
    val provenance: String,
    val revision: Long,
)

data class RealityHousehold12PurchaseReceiptRows(
    val purchases: List<RealityHousehold12PurchaseRow>,
    val receipts: RealityHousehold11ReceiptRows,
)

enum class RealityHousehold12RowsRestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/**
 * SQL12 rows for a closed, sealed STAGED file. Its generation owner supplies
 * the verified path under an exclusive lease and an unchanged full pointer.
 */
object RealityHousehold12PurchaseRowsPort {
    private const val COLUMNS =
        "purchase_ref,stock_item_ref,quantity_thousandths,unit,confirmed_by,confirmed_at_ms,provenance,revision"
    private const val MAX_ROWS = 8_192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_REF_CHARS = 128
    private const val MANUAL = "MANUAL_EXPLICIT"
    private val units = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")

    fun readTrustedSealedStageTwelve(file: Path): RealityHousehold12PurchaseReceiptRows {
        val path = Household11ReceiptRowsSql.checkFile(file)
        return BundledSQLiteDriver().open(
            path.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            readTrustedTwelveConnection(db)
        }
    }

    internal fun readTrustedTwelveConnection(db: SQLiteConnection): RealityHousehold12PurchaseReceiptRows =
        db.transaction("BEGIN DEFERRED TRANSACTION") {
            readCurrentHouseholdRows(db, expectedVersion = 12)
        }

    fun restoreOrVerifyTrustedStagedTwelve(
        file: Path,
        rows: RealityHousehold12PurchaseReceiptRows,
        beforeCommit: () -> Unit = {},
    ): RealityHousehold12RowsRestoreDisposition {
        require(rows.purchases.size <= MAX_ROWS && rows.receipts.receipts.size <= MAX_ROWS)
        val expected = rows.copy(
            purchases = rows.purchases.toList(),
            receipts = rows.receipts.copy(receipts = rows.receipts.receipts.toList()),
        )
        validate(expected)
        val path = Household11ReceiptRowsSql.checkFile(file)
        val result = BundledSQLiteDriver().open(
            path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            val disposition = db.transaction("BEGIN IMMEDIATE TRANSACTION") {
                checkStageSchema(db)
                Household11ReceiptRowsSql.checkIntegrity(db)
                val current = readRows(db)
                if (current == expected) {
                    RealityHousehold12RowsRestoreDisposition.ALREADY_IDENTICAL
                } else {
                    check(current.purchases.isEmpty() && current.receipts.revision == 0L &&
                        current.receipts.receipts.isEmpty()) { "Reality12 refuses a divergent staged target" }
                    writePurchases(db, expected.purchases)
                    with(RealityHousehold11ReceiptRowsPort) { db.writeReceiptRows(expected.receipts) }
                    check(readRows(db) == expected) { "Reality12 staged readback differs within transaction" }
                    Household11ReceiptRowsSql.checkIntegrity(db)
                    beforeCommit()
                    RealityHousehold12RowsRestoreDisposition.RESTORED
                }
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "Reality12 staged WAL checkpoint is busy" }
            }
            disposition
        }
        check(readTrustedSealedStageTwelve(path) == expected) { "Reality12 closed staged readback differs" }
        return result
    }

    internal fun readCurrentHouseholdRows(
        db: SQLiteConnection,
        expectedVersion: Int,
    ): RealityHousehold12PurchaseReceiptRows {
        require(expectedVersion == 12 || expectedVersion == RealityHousehold13CompensationSchema.VERSION ||
            expectedVersion == RealityHousehold14ReservationSchema.VERSION)
        checkStageSchema(db, expectedVersion)
        Household11ReceiptRowsSql.checkIntegrity(db)
        return readRows(db, enforceGross = expectedVersion == 12)
    }

    private fun checkStageSchema(db: SQLiteConnection, expectedVersion: Int = 12) {
        check(db.scalar("PRAGMA user_version") == expectedVersion.toLong() && db.scalar("PRAGMA foreign_keys") == 1L) {
            "Household purchase rows require exact SQL$expectedVersion with foreign keys enabled"
        }
        Household11ReceiptRowsSql.checkTables(db)
        db.prepare("SELECT sql FROM sqlite_schema WHERE type='table' AND name='household_confirmed_purchase'").use {
            check(it.step() && it.getText(0) == RealityHousehold12PurchaseSchema.purchaseTableSql && !it.step()) {
                "Reality12 confirmed-purchase table differs from canonical SQL12"
            }
        }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema WHERE type='trigger' " +
                "AND tbl_name COLLATE NOCASE = 'household_confirmed_purchase'",
        ) == 0L) { "Reality12 purchases reject unexpected triggers" }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type='table' AND fk.\"table\" COLLATE NOCASE = 'household_confirmed_purchase'",
        ) == 0L) { "Reality12 purchases reject unexpected inbound foreign keys" }
    }

    private fun readRows(db: SQLiteConnection, enforceGross: Boolean = true): RealityHousehold12PurchaseReceiptRows {
        val purchases = db.prepare(
            "SELECT $COLUMNS, CAST(purchase_ref AS BLOB), CAST(stock_item_ref AS BLOB), " +
                "CAST(confirmed_by AS BLOB) FROM household_confirmed_purchase LIMIT ${MAX_ROWS + 1}",
        ).use { stmt ->
            buildList {
                while (stmt.step()) {
                    val quantity = stmt.getLong(2)
                    check(quantity in 1L..Int.MAX_VALUE.toLong()) { "Reality12 purchase quantity outside range" }
                    val purchaseRef = stmt.getText(0)
                    val itemRef = stmt.getText(1)
                    val principal = stmt.getText(4)
                    check(
                        purchaseRef.toByteArray(Charsets.UTF_8).contentEquals(stmt.getBlob(8)) &&
                            itemRef.toByteArray(Charsets.UTF_8).contentEquals(stmt.getBlob(9)) &&
                            principal.toByteArray(Charsets.UTF_8).contentEquals(stmt.getBlob(10)),
                    ) { "Reality12 purchase reference UTF-8 is malformed" }
                    add(RealityHousehold12PurchaseRow(
                        purchaseRef, itemRef, quantity.toInt(), stmt.getText(3), principal,
                        stmt.getLong(5), stmt.getText(6), stmt.getLong(7),
                    ))
                }
            }.sortedBy { it.purchaseRef }
        }
        return RealityHousehold12PurchaseReceiptRows(purchases, Household11ReceiptRowsSql.readRows(db))
            .also { validate(it, enforceGross) }
    }

    internal fun validate(rows: RealityHousehold12PurchaseReceiptRows, enforceGross: Boolean = true) {
        require(rows.purchases.size <= MAX_ROWS) { "Reality12 purchase count outside budget" }
        Household11ReceiptRowsSql.validate(rows.receipts)
        var previous: String? = null
        var encodedSize = Int.SIZE_BYTES
        val index = HashMap<String, RealityHousehold12PurchaseRow>()
        for (row in rows.purchases) {
            require(previous == null || previous < row.purchaseRef) { "Reality12 purchases must be unique and sorted" }
            for (ref in listOf(row.purchaseRef, row.stockItemRef, row.confirmedBy)) {
                val bytes = ref.toByteArray(Charsets.UTF_8)
                require(ref.codePointCount(0, ref.length) in 1..MAX_REF_CHARS && ref.trim() == ref &&
                    '\u0000' !in ref && bytes.toString(Charsets.UTF_8) == ref) {
                    "Reality12 purchase reference invalid"
                }
                encodedSize += bytes.size
            }
            require(row.quantityThousandths > 0 && row.unit in units && row.confirmedAtMs >= 0 &&
                row.revision > 0 && row.provenance == MANUAL) { "Reality12 purchase fields invalid" }
            encodedSize += 5 * Short.SIZE_BYTES + Int.SIZE_BYTES + 2 * Long.SIZE_BYTES +
                row.unit.length + row.provenance.length
            require(encodedSize <= MAX_BYTES) { "Reality12 purchases exceed section budget" }
            index[row.purchaseRef] = row
            previous = row.purchaseRef
        }
        val totals = HashMap<String, Long>()
        for (receipt in rows.receipts.receipts) {
            val purchase = index[receipt.purchaseRef] ?: continue
            require(purchase.stockItemRef == receipt.stockItemRef && purchase.unit == receipt.unit) {
                "Reality12 receipts must match confirmed purchase item and unit"
            }
            if (!enforceGross) continue
            val total = totals.getOrDefault(purchase.purchaseRef, 0L) + receipt.quantityThousandths
            require(total <= purchase.quantityThousandths) { "Reality12 receipts exceed confirmed purchase" }
            totals[purchase.purchaseRef] = total
        }
    }

    internal fun writePurchases(db: SQLiteConnection, rows: List<RealityHousehold12PurchaseRow>) {
        db.prepare("INSERT INTO household_confirmed_purchase($COLUMNS) VALUES(?,?,?,?,?,?,?,?)").use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.purchaseRef)
                stmt.bindText(2, row.stockItemRef)
                stmt.bindLong(3, row.quantityThousandths.toLong())
                stmt.bindText(4, row.unit)
                stmt.bindText(5, row.confirmedBy)
                stmt.bindLong(6, row.confirmedAtMs)
                stmt.bindText(7, row.provenance)
                stmt.bindLong(8, row.revision)
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

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step())
        it.getLong(0)
    }
}
