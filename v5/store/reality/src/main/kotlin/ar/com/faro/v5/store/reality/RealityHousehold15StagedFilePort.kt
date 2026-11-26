package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Path

/** Generation owner must hold exclusive lease for this INACTIVE, sealed file. */
object RealityHousehold15StagedFilePort {
    fun migrateOrVerifyTrustedStagedFifteen(file: Path, requireExclusiveStage: () -> Unit) {
        requireExclusiveStage()
        when (RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file)) {
            14L -> migrateTrustedStagedFourteen(file, requireExclusiveStage)
            15L -> {
                val path = sealedPath(file)
                BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW).use { db ->
                    db.execSQL("PRAGMA foreign_keys = ON")
                    RealityHousehold15ConsumptionSchema.requireDefinitions(db)
                    db.prepare("SELECT 1 FROM household_stock_consumption_event LIMIT 1").use {
                        check(!it.step()) { "SQL15 stage-only admission requires an empty ledger" }
                    }
                    requireExclusiveStage()
                }
            }
            else -> error("SQL15 stage requires sealed SQL14 or SQL15")
        }
    }

    fun migrateTrustedStagedFourteen(file: Path, requireExclusiveStage: () -> Unit) {
        val path = sealedPath(file)
        requireExclusiveStage()
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            db.prepare("PRAGMA journal_mode").use {
                check(it.step() && it.getText(0) == "wal") { "SQL15 migration requires a WAL-mode stage" }
            }
            RealityHousehold15ConsumptionSchema.installOnStagedFourteenConnection(db, requireExclusiveStage)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "SQL15 committed but staged checkpoint is busy" }
            }
        }
    }

    private fun sealedPath(file: Path): Path {
        check(file.toAbsolutePath() == file.toAbsolutePath().normalize()) { "SQL15 rejects staged path aliases" }
        return Household11ReceiptRowsSql.checkFile(file)
    }
}
