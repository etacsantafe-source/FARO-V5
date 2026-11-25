package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** DDL for an isolated STAGED copy supplied by the closed DEV host. */
internal object RealityHousehold12PurchaseSchema {
    const val VERSION = 12

    fun installOnStagedElevenConnection(
        connection: SQLiteConnection,
        beforeCommit: () -> Unit = {},
    ) {
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            Household11ReceiptRowsSql.checkSchema(connection)
            Household11ReceiptRowsSql.checkIntegrity(connection)
            Household11ReceiptRowsSql.readRows(connection)

            connection.execSQL(
                """
                CREATE TABLE household_confirmed_purchase (
                    purchase_ref TEXT PRIMARY KEY NOT NULL
                        CHECK(length(purchase_ref) BETWEEN 1 AND 128 AND purchase_ref = trim(purchase_ref)
                            AND instr(purchase_ref, char(0)) = 0),
                    stock_item_ref TEXT NOT NULL
                        CHECK(length(stock_item_ref) BETWEEN 1 AND 128 AND stock_item_ref = trim(stock_item_ref)
                            AND instr(stock_item_ref, char(0)) = 0),
                    quantity_thousandths INTEGER NOT NULL
                        CHECK(quantity_thousandths BETWEEN 1 AND ${Int.MAX_VALUE}),
                    unit TEXT NOT NULL CHECK(unit IN
                        ('UNIT', 'PACKAGE', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')),
                    confirmed_by TEXT NOT NULL
                        CHECK(length(confirmed_by) BETWEEN 1 AND 128 AND confirmed_by = trim(confirmed_by)
                            AND instr(confirmed_by, char(0)) = 0),
                    confirmed_at_ms INTEGER NOT NULL CHECK(confirmed_at_ms >= 0),
                    provenance TEXT NOT NULL CHECK(provenance = 'MANUAL_EXPLICIT'),
                    revision INTEGER NOT NULL CHECK(revision > 0)
                ) STRICT
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA user_version = $VERSION")
            Household11ReceiptRowsSql.checkIntegrity(connection)
            beforeCommit()
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            try {
                connection.execSQL("ROLLBACK TRANSACTION")
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }
}
