package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** The generation owner supplies an isolated, admitted, non-active file under its lease. */
object RealityHousehold10StagedFilePort {
    fun migrateTrustedStagedNine(file: Path) {
        val absolute = file.toAbsolutePath().normalize()
        val physical = absolute.parent.toRealPath().resolve(absolute.fileName)
        check(Files.isRegularFile(physical, NOFOLLOW_LINKS)) {
            "Household10 requires a regular staged Reality file"
        }
        BundledSQLiteDriver().open(
            physical.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { result ->
                check(result.step() && result.getLong(0) == 0L) {
                    "Household10 staged WAL checkpoint failed or is busy"
                }
            }
        }
    }
}
