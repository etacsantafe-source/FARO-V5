package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.LocalDate

data class RealityHousehold11ReceiptRow(
    val receiptId: String,
    val intentKey: String,
    val purchaseRef: String,
    val stockItemRef: String,
    val quantityThousandths: Int,
    val unit: String,
    val receivedEpochDay: Long,
    val recordedAtMs: Long,
    val revision: Long,
)

data class RealityHousehold11ReceiptRows(
    val revision: Long,
    val receipts: List<RealityHousehold11ReceiptRow>,
)

enum class RealityHousehold11ReceiptRestoreDisposition { RESTORED, ALREADY_IDENTICAL }

/**
 * W05-A1B2B: row-level SQL11 capture and restore for a SEALED, INACTIVE staged
 * Reality file. Only a pinned generation owner may invoke this trusted port.
 * This port NEVER opens the active database or performs FBS2 cutover.
 */
object RealityHousehold11ReceiptRowsPort {
    fun readTrustedSealedStageEleven(file: Path): RealityHousehold11ReceiptRows {
        val physical = Household11ReceiptRowsSql.checkFile(file)
        return BundledSQLiteDriver().open(
            physical.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            Household11ReceiptRowsSql.checkSchema(db)
            Household11ReceiptRowsSql.checkIntegrity(db)
            Household11ReceiptRowsSql.readRows(db)
        }
    }

    fun restoreOrVerifyTrustedStagedEleven(
        file: Path,
        snapshot: RealityHousehold11ReceiptRows,
        beforeCommit: () -> Unit = {},
    ): RealityHousehold11ReceiptRestoreDisposition {
        val expected = snapshot.copy(receipts = snapshot.receipts.toList())
        Household11ReceiptRowsSql.validate(expected)
        val physical = Household11ReceiptRowsSql.checkFile(file)
        val disposition = BundledSQLiteDriver().open(
            physical.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            val result = try {
                Household11ReceiptRowsSql.checkSchema(db)
                Household11ReceiptRowsSql.checkIntegrity(db)
                val current = Household11ReceiptRowsSql.readRows(db)
                val outcome = if (current == expected) {
                    RealityHousehold11ReceiptRestoreDisposition.ALREADY_IDENTICAL
                } else {
                    check(current.revision == 0L && current.receipts.isEmpty()) {
                        "Reality11 restore refuses a revised or nonempty target"
                    }
                    db.writeReceiptRows(expected)
                    check(Household11ReceiptRowsSql.readRows(db) == expected) {
                        "Reality11 in-transaction readback differs"
                    }
                    Household11ReceiptRowsSql.checkIntegrity(db)
                    beforeCommit()
                    RealityHousehold11ReceiptRestoreDisposition.RESTORED
                }
                db.execSQL("COMMIT TRANSACTION")
                outcome
            } catch (failure: Throwable) {
                try {
                    db.execSQL("ROLLBACK TRANSACTION")
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressed(rollbackFailure)
                }
                throw failure
            }
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) {
                    "Reality11 WAL checkpoint failed or busy"
                }
            }
            result
        }
        check(readTrustedSealedStageEleven(physical) == expected) {
            "Reality11 closed SQL readback differs after transaction"
        }
        return disposition
    }

    private fun SQLiteConnection.writeReceiptRows(snapshot: RealityHousehold11ReceiptRows) {
        prepare("UPDATE household_stock_receipt_state SET revision = ? WHERE singleton_id = 1").use {
            it.bindLong(1, snapshot.revision)
            it.step()
        }
        prepare(
            "INSERT INTO household_stock_receipt(" + Household11ReceiptRowsSql.COLUMNS +
                ") VALUES (?,?,?,?,?,?,?,?,?)",
        ).use { stmt ->
            for (row in snapshot.receipts) {
                stmt.bindText(1, row.receiptId)
                stmt.bindText(2, row.intentKey)
                stmt.bindText(3, row.purchaseRef)
                stmt.bindText(4, row.stockItemRef)
                stmt.bindLong(5, row.quantityThousandths.toLong())
                stmt.bindText(6, row.unit)
                stmt.bindLong(7, row.receivedEpochDay)
                stmt.bindLong(8, row.recordedAtMs)
                stmt.bindLong(9, row.revision)
                stmt.step()
                stmt.reset()
            }
        }
    }
}

internal object Household11ReceiptRowsSql {
    const val COLUMNS =
        "receipt_id,intent_key,purchase_ref,stock_item_ref,quantity_thousandths," +
            "unit,received_epoch_day,recorded_at_ms,revision"
    private const val MAX_ROWS = 8192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val validDays = LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()
    private val validUnits = setOf("UNIT", "PACKAGE", "GRAM", "KILOGRAM", "MILLILITER", "LITER")

    fun checkFile(file: Path): Path {
        val physical = file.toAbsolutePath().normalize()
        check(physical.parent != null && physical.parent.toRealPath() == physical.parent &&
            Files.isRegularFile(physical, NOFOLLOW_LINKS) && !Files.isSymbolicLink(physical)) {
            "Reality11 needs a canonical regular staged file"
        }
        check((Files.getAttribute(physical, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
            "Reality11 staged file has multiple hard links"
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            check(Files.notExists(physical.resolveSibling(physical.fileName.toString() + suffix), NOFOLLOW_LINKS)) {
                "Reality11 staged file has SQLite sidecar"
            }
        }
        return physical
    }

    fun checkSchema(db: SQLiteConnection) {
        check(db.scalar("PRAGMA user_version") == 11L &&
            db.scalar("PRAGMA foreign_keys") == 1L) {
            "Reality11 requires exact SQL11 with foreign keys enabled"
        }
        for ((table, columns) in listOf(
            "household_stock_receipt_state" to listOf("singleton_id", "revision"),
            "household_stock_receipt" to COLUMNS.split(','),
        )) {
            db.prepare("PRAGMA table_list(" + table + ")").use {
                check(it.step() && it.getText(2) == "table" &&
                    it.getLong(3) == columns.size.toLong() &&
                    it.getLong(5) == 1L && !it.step()) {
                    "Reality11 requires exact STRICT table: " + table
                }
            }
            val actual = db.prepare("PRAGMA table_info(" + table + ")").use {
                buildList { while (it.step()) add(it.getText(1)) }
            }
            check(actual == columns) { "Reality11 columns mismatch: " + table }
        }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema WHERE type = 'trigger' AND " +
                "tbl_name COLLATE NOCASE IN ('household_stock_receipt','household_stock_receipt_state')",
        ) == 0L) { "Reality11 receipts reject unexpected SQL triggers" }
        check(db.scalar(
            "SELECT count(*) FROM sqlite_schema AS s, pragma_foreign_key_list(s.name) AS fk " +
                "WHERE s.type = 'table' AND fk.\"table\" COLLATE NOCASE IN " +
                "('household_stock_receipt','household_stock_receipt_state')",
        ) == 0L) { "Reality11 receipts reject inbound SQL foreign keys" }
    }

    fun checkIntegrity(db: SQLiteConnection) {
        check(db.prepare("PRAGMA integrity_check").use {
            it.step() && it.getText(0) == "ok" && !it.step()
        }) { "Reality11 SQLite integrity check failed" }
        check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
            "Reality11 SQLite foreign key check failed"
        }
    }

    fun readRows(db: SQLiteConnection): RealityHousehold11ReceiptRows {
        val revision = db.prepare("SELECT singleton_id,revision FROM household_stock_receipt_state").use {
            check(it.step() && it.getLong(0) == 1L && !it.isNull(1)) {
                "Reality11 receipt singleton missing"
            }
            val number = it.getLong(1)
            check(!it.step()) { "Reality11 has multiple receipt state rows" }
            number
        }
        val rows = db.prepare(
            "SELECT " + COLUMNS + "," +
                "length(CAST(receipt_id AS BLOB)),length(CAST(intent_key AS BLOB))," +
                "length(CAST(purchase_ref AS BLOB)),length(CAST(stock_item_ref AS BLOB))," +
                "length(CAST(unit AS BLOB)) FROM household_stock_receipt",
        ).use { stmt ->
            buildList {
                while (stmt.step()) {
                    check(size < MAX_ROWS) { "Reality11 SQL row budget exceeded" }
                    for (index in 9..12) {
                        check(!stmt.isNull(index) && stmt.getLong(index) in 1L..384L) {
                            "Reality11 SQL receipt reference exceeds byte budget"
                        }
                    }
                    check(!stmt.isNull(13) && stmt.getLong(13) in 1L..16L) {
                        "Reality11 SQL receipt unit too large"
                    }
                    val amount = stmt.getLong(4)
                    check(amount in 1..Int.MAX_VALUE.toLong()) {
                        "Reality11 SQL receipt quantity invalid"
                    }
                    add(RealityHousehold11ReceiptRow(
                        receiptId = stmt.getText(0),
                        intentKey = stmt.getText(1),
                        purchaseRef = stmt.getText(2),
                        stockItemRef = stmt.getText(3),
                        quantityThousandths = amount.toInt(),
                        unit = stmt.getText(5),
                        receivedEpochDay = stmt.getLong(6),
                        recordedAtMs = stmt.getLong(7),
                        revision = stmt.getLong(8),
                    ))
                }
            }.sortedBy { it.receiptId }
        }
        return RealityHousehold11ReceiptRows(revision, rows).also(::validate)
    }

    fun validate(snapshot: RealityHousehold11ReceiptRows) {
        require(snapshot.revision >= 0 && snapshot.receipts.size <= MAX_ROWS) {
            "Reality11 SQL snapshot revision or count invalid"
        }
        var previousId: String? = null
        val intents = HashSet<String>()
        var totalBytes = 24L
        for (row in snapshot.receipts) {
            val fields = listOf(row.receiptId, row.intentKey, row.purchaseRef, row.stockItemRef)
            val lengths = fields.map { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                require(value.length in 1..128 && value.trim().isNotEmpty() &&
                    bytes.size in 1..384 && bytes.toString(Charsets.UTF_8) == value) {
                    "Reality11 SQL receipt reference invalid"
                }
                bytes.size
            }
            require(previousId == null || previousId < row.receiptId) {
                "Reality11 SQL receipts must be sorted and unique"
            }
            require(intents.add(row.intentKey)) { "Reality11 SQL duplicate intent key" }
            require(row.quantityThousandths > 0 && row.unit in validUnits &&
                row.receivedEpochDay in validDays && row.recordedAtMs >= 0L &&
                row.revision in 1L..snapshot.revision) {
                "Reality11 SQL receipt field invalid"
            }
            totalBytes += 38L + lengths.sum() + row.unit.toByteArray(Charsets.UTF_8).size
            require(totalBytes <= MAX_BYTES.toLong()) { "Reality11 SQL receipt section exceeds 4MiB" }
            previousId = row.receiptId
        }
    }

    private fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
        check(it.step()) { "Reality11 SQL scalar missing" }
        it.getLong(0)
    }
}
