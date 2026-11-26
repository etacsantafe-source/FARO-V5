package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Path

/** The generation owner retains its exclusive lease on this inactive file throughout the call. */
object RealityHousehold14StagedFilePort {
    fun migrateTrustedStagedThirteen(file: Path, requireExclusiveStage: () -> Unit) {
        check(file.toAbsolutePath() == file.toAbsolutePath().normalize()) { "SQL14 rejects staged path aliases" }
        requireExclusiveStage()
        val path = Household11ReceiptRowsSql.checkFile(file)
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.prepare("PRAGMA journal_mode").use {
                check(it.step() && it.getText(0) == "wal") { "SQL14 migration requires a WAL-mode stage" }
            }
            RealityHousehold14ReservationSchema.installOnStagedThirteenConnection(db, requireExclusiveStage)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "SQL14 committed but staged checkpoint is busy" }
            }
        }
    }
}
