package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.household.api.HouseholdManualPurchaseReview

interface RealityHousehold12PurchaseApplyPort {
    fun find(purchaseRef: String): RealityHousehold12PurchaseRow?

    fun insert(
        purchase: HouseholdManualPurchaseReview,
        confirmedAtMs: Long,
        scope: RealityTransactionScope,
    ): Boolean
}

internal class RealityHousehold12PurchaseRowsWriter(private val connection: SQLiteConnection) {
    fun find(purchaseRef: String): RealityHousehold12PurchaseRow? =
        connection.prepare(
            "SELECT purchase_ref,stock_item_ref,quantity_thousandths,unit,confirmed_by," +
                "confirmed_at_ms,provenance,revision FROM household_confirmed_purchase WHERE purchase_ref=?",
        ).use { stmt ->
            stmt.bindText(1, purchaseRef)
            if (!stmt.step()) return@use null
            RealityHousehold12PurchaseRow(stmt.getText(0), stmt.getText(1), stmt.getLong(2).toInt(),
                stmt.getText(3), stmt.getText(4), stmt.getLong(5), stmt.getText(6), stmt.getLong(7))
        }

    fun insert(purchase: HouseholdManualPurchaseReview, confirmedAtMs: Long): Boolean {
        require(confirmedAtMs >= 0L)
        if (find(purchase.purchaseRef) != null) return false
        connection.prepare(
            "INSERT INTO household_confirmed_purchase(purchase_ref,stock_item_ref,quantity_thousandths,unit," +
                "confirmed_by,confirmed_at_ms,provenance,revision) VALUES(?,?,?,?,?,?,?,1)",
        ).use { stmt ->
            stmt.bindText(1, purchase.purchaseRef)
            stmt.bindText(2, purchase.itemRef)
            stmt.bindLong(3, purchase.quantity.thousandths)
            stmt.bindText(4, purchase.quantity.unit.name)
            stmt.bindText(5, purchase.principal)
            stmt.bindLong(6, confirmedAtMs)
            stmt.bindText(7, HouseholdManualPurchaseReview.PROVENANCE)
            stmt.step()
        }
        return true
    }
}
