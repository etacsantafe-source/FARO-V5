package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * M0B plumbing, NOT a product opener or a substitute for generation admission.
 * The generation owner must prove that this file is inside a sealed,
 * non-ACTIVE canonical STAGED Reality8 generation BEFORE calling.
 */
object RealityFinance9StagedFilePort {
    fun migrateTrustedStagedEight(
        file: Path,
        migrationId: String,
        sourceFingerprint: String,
        finishedAtEpochMillis: Long,
    ) {
        val normalized = file.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized) &&
            Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Finance9 stage requires an existing regular, non-symlink SQLite file"
        }
        // READWRITE + NOFOLLOW, deliberately excluding CREATE.
        BundledSQLiteDriver().open(
            normalized.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA synchronous = FULL")
            RealityFinance9Schema.installOnStagedEightConnection(
                connection = db,
                migrationId = migrationId,
                sourceFingerprint = sourceFingerprint,
                finishedAtEpochMillis = finishedAtEpochMillis,
            )
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { statement ->
                check(statement.step()) { "Finance9 WAL checkpoint missing response" }
                check(statement.getLong(0) == 0L) {
                    "Finance9 WAL checkpoint must not report busy writers"
                }
            }
        }
    }
}
