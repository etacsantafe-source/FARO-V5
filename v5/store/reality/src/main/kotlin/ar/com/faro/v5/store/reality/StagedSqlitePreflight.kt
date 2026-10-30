package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared low-level SQLite-only inspection for a sealed, quiesced and currently
 * inactive generation. Owns no Reality or Workspace write authority.
 * `immutable=1` MUST NEVER be used for active/concurrently written stores.
 *
 * Kept under the existing SQLite storage adapter dependency boundary so the
 * generation owner does not depend directly on SQL/native driver APIs.
 */
object StagedSqlitePreflight {
    data class Schema(
        val name: String,
        val version: Int,
        val tables: List<String>,
    )

    fun requireAll(directory: Path, schemas: List<Schema>) {
        check(schemas.isNotEmpty() && schemas.map { it.name }.distinct().size == schemas.size)
        schemas.forEach { schema ->
            check(schema.name.matches(Regex("[a-z][a-z0-9_-]*\\.db")) && schema.version > 0)
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(!Files.exists(directory.resolve(schema.name + suffix))) {
                    "staged generation store has uncheckpointed SQLite sidecar: ${schema.name}${suffix}"
                }
            }
        }
        val driver = BundledSQLiteDriver()
        schemas.forEach { schema ->
            val file = directory.resolve(schema.name)
            check(!Files.isSymbolicLink(file) && Files.isRegularFile(file)) {
                "staged generation SQLite file must be regular: ${schema.name}"
            }
            // Immutable read-only URI cannot create shm/WAL. It is valid only
            // because the staged file set is sealed and not concurrently edited.
            driver.open(
                file.toUri().toString() + "?mode=ro&immutable=1",
                SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
            ).use { db ->
                val version = db.prepare("PRAGMA user_version").use { statement ->
                    check(statement.step()) { "missing SQLite user_version: ${schema.name}" }
                    statement.getLong(0).toInt()
                }
                check(version == schema.version) {
                    "staged ${schema.name} schema mismatch: expected ${schema.version}, got $version"
                }
                for (table in schema.tables) {
                    val found = db.prepare(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?"
                    ).use { statement ->
                        statement.bindText(1, table)
                        statement.step()
                    }
                    check(found) { "staged ${schema.name} required table missing: $table" }
                }
                check(db.prepare("PRAGMA quick_check").use {
                    it.step() && it.getText(0) == "ok" && !it.step()
                }) { "staged ${schema.name} SQLite integrity failed" }
                check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
                    "staged ${schema.name} foreign key failure"
                }
            }
        }
    }
}
