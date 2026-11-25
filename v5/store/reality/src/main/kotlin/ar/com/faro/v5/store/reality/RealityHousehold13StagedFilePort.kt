package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Path

/** The generation owner supplies an exclusively leased, inactive, sealed SQL12 file. */
object RealityHousehold13StagedFilePort {
    fun migrateTrustedStagedTwelve(file: Path, beforeCommit: () -> Unit = {}) {
        val path = Household11ReceiptRowsSql.checkFile(file)
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            RealityHousehold13CompensationSchema.installOnStagedTwelveConnection(db, beforeCommit)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "SQL13 staged checkpoint is busy" }
            }
        }
    }
}
