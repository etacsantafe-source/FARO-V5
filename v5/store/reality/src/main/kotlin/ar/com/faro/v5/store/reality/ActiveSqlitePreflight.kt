package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Existing ACTIVE SQLite authority preflight, distinct from sealed STAGED inspection.
 * SQLite must recover/check live WAL via normal read-only opening; NEVER use
 * immutable=1 on active data (it can silently ignore committed WAL contents).
 *
 * This is a schema/integrity probe only, not a writer or a migration engine.
 * The caller must own the maintenance quiescence lease and active pointer checks.
 */
object ActiveSqlitePreflight {
    fun requireAll(
        directory: Path,
        schemas: List<StagedSqlitePreflight.Schema>,
    ) {
        check(schemas.isNotEmpty() &&
            schemas.map { it.name }.distinct().size == schemas.size)
        // Do not allow SQLite to follow attacker-controlled sidecar symlinks.
        for (schema in schemas) {
            check(schema.name.matches(Regex("[a-z][a-z0-9_-]*\\.db")) && schema.version > 0)
            val file = directory.resolve(schema.name)
            check(!Files.isSymbolicLink(file) &&
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "active generation SQLite store must be an existing regular file"
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                val sidecar = directory.resolve(schema.name + suffix)
                check(!Files.isSymbolicLink(sidecar)) {
                    "active generation SQLite sidecar must never be a symlink"
                }
                if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
                    check(Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)) {
                        "active generation SQLite sidecar must be a regular file"
                    }
                }
            }
        }
        // Resolve the trusted Android app-directory alias before NOFOLLOW.
        // Do not resolve the final DB file: a symlink there stays forbidden.
        check(!Files.isSymbolicLink(directory) &&
            Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "active generation directory must be a real directory"
        }
        val physicalDirectory = directory.toRealPath()
        val driver = BundledSQLiteDriver()
        for (schema in schemas) {
            // Unlike staged immutable=1, this read observes valid committed WAL.
            driver.open(
                physicalDirectory.resolve(schema.name).toString(),
                SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
            ).use { db ->
                val version = db.prepare("PRAGMA user_version").use { statement ->
                    check(statement.step()) { "missing active SQLite user_version" }
                    statement.getLong(0).toInt()
                }
                check(version == schema.version) {
                    "active ${schema.name} schema mismatch: expected ${schema.version}, got $version"
                }
                for (table in schema.tables) {
                    val found = db.prepare(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?"
                    ).use { statement ->
                        statement.bindText(1, table)
                        statement.step()
                    }
                    check(found) { "active ${schema.name} required table missing: $table" }
                }
                check(db.prepare("PRAGMA quick_check").use {
                    it.step() && it.getText(0) == "ok" && !it.step()
                }) { "active ${schema.name} SQLite quick_check failed" }
                check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
                    "active ${schema.name} SQLite foreign key violation"
                }
            }
        }
    }
}
