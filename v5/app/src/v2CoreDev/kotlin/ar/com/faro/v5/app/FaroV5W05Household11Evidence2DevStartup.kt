package ar.com.faro.v5.app

import android.database.sqlite.SQLiteDatabase
import ar.com.faro.v5.generation.FaroV5Household11Evidence2ActiveDevReadHost
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import java.nio.file.Path

/**
 * Isolated v2CoreDev only. A verified foreground gesture may INSPECT an
 * already active SQL11 generation; no Apply, stock or payment port exists.
 */
internal class FaroV5W05Household11Evidence2DevStartup(
    private val root: Path,
    private val applicationId: String,
    private val foreground: () -> Boolean,
    private val closedChatHost: () -> Boolean,
    private val isDebuggable: Boolean,
) : AutoCloseable {
    data class Snapshot(
        val status: FaroV5Household11Evidence2ActiveDevReadHost.Status,
        val needs: Long = 0,
        val receipts: Long = 0,
        val needRevision: Long = 0,
        val receiptRevision: Long = 0,
    )
    companion object {
        // Same process-wide gate for all DEV probes, no parallel permissions.
        private val gate = SingleProcessMaterialWriteGate()
        fun matchesAuthorizedRows(
            expected: FaroV5Household11Evidence2ActiveDevReadHost.AuthorizedRows,
            needs: Long, receipts: Long, needRevision: Long, receiptRevision: Long,
        ): Boolean = needs == expected.needCount.toLong() &&
            receipts == expected.receiptCount.toLong() &&
            needRevision == expected.needRevision &&
            receiptRevision == expected.receiptRevision
    }
    @Volatile private var closed = false
    private var lease: FaroV5Household11Evidence2ActiveDevReadHost.Session? = null

    @Synchronized
    fun reopenReadOnly(): Snapshot {
        if (closed || !foreground() || !closedChatHost() ||
            !FaroV5W05Household11DevStartup.permitsIsolatedDev(applicationId, isDebuggable,
                FaroV5HostCertification.V2_ACTIVE_ENABLED)) {
            lease?.close()
            lease = null
            return Snapshot(FaroV5Household11Evidence2ActiveDevReadHost.Status.BLOCKED)
        }
        lease?.close()
        lease = null
        val result = FaroV5Household11Evidence2ActiveDevReadHost.reopen(
            root, gate, { !closed && foreground() && closedChatHost() })
        val session = result.session ?: return Snapshot(result.status)
        lease = session
        return try {
            session.readPinnedReality { reality ->
                SQLiteDatabase.openDatabase(
                    reality.toString(), null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                ).use { db ->
                    check(db.isReadOnly && !closed && foreground())
                    fun one(sql: String): Long = db.rawQuery(sql, null).use { cursor ->
                        check(cursor.moveToFirst() && !cursor.isNull(0))
                        val value = cursor.getLong(0)
                        check(!cursor.moveToNext())
                        value
                    }
                    val needs = one("SELECT count(*) FROM household_need")
                    val receipts = one("SELECT count(*) FROM household_stock_receipt")
                    val needRevision = one(
                        "SELECT revision FROM household_need_state WHERE singleton_id=1")
                    val receiptRevision = one(
                        "SELECT revision FROM household_stock_receipt_state WHERE singleton_id=1")
                    check(needs in 0..8192 && receipts in 0..8192 &&
                        needRevision >= 0 && receiptRevision >= 0 &&
                        matchesAuthorizedRows(session.expectedRows(),
                            needs, receipts, needRevision, receiptRevision)) {
                        "SQL11 ACTIVE data differ from the durable pre-cutover authorization"
                    }
                    Snapshot(result.status, needs, receipts, needRevision, receiptRevision)
                }
            }.also { check(!closed && foreground() && closedChatHost()) }
        } catch (_: Exception) {
            lease?.close()
            lease = null
            Snapshot(FaroV5Household11Evidence2ActiveDevReadHost.Status.BLOCKED)
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        try { lease?.close() } finally { lease = null }
    }
}
