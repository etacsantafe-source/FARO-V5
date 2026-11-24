package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/**
 * W05-A1B0: low-level file adapter ONLY for a generation-owner admitted,
 * isolated and sealed STAGED Reality10 file. Not an active-db opener.
 */
object RealityHousehold11StagedFilePort {
    fun migrateTrustedStagedTen(file: Path) {
        val absolute = file.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(absolute)) {
            "Household11 staged Reality symlink rejected"
        }
        val physical = absolute.parent.toRealPath().resolve(absolute.fileName)
        check(Files.isRegularFile(physical, NOFOLLOW_LINKS)) {
            "Household11 requires an existing regular staged Reality file"
        }
        BundledSQLiteDriver().open(
            physical.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            RealityHousehold11ReceiptSchema.installOnStagedTenConnection(db)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) {
                    "Household11 staged WAL checkpoint failed or busy"
                }
            }
        }
    }
}
