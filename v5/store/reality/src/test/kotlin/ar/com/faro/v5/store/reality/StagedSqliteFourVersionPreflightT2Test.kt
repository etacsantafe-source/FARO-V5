package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Strict read-only admission ONLY; these synthetic fixtures are not Reality user data. */
class StagedSqliteFourVersionPreflightT2Test {
    @TempDir lateinit var root: Path

    private val v5 = listOf("base_state")
    private val v6 = v5 + "domain_state"
    private val v7 = v6 + "receipt_time_state"
    private val v8 = v7 + "planning_state"

    private fun schema8() = StagedSqlitePreflight.Schema(
        "reality.db", 8, v8,
        legacyVersion = 7, legacyTables = v7,
        olderLegacyVersion = 6, olderLegacyTables = v6,
        oldestLegacyVersion = 5, oldestLegacyTables = v5,
    )

    private fun fixture(label: String, version: Int, tables: List<String>): Path {
        val dir = Files.createDirectory(root.resolve(label))
        BundledSQLiteDriver().open(dir.resolve("reality.db").toString()).use { db ->
            for (table in tables) {
                db.execSQL("CREATE TABLE $table (id INTEGER NOT NULL PRIMARY KEY)")
            }
            db.execSQL("PRAGMA user_version = $version")
        }
        return dir
    }

    @Test fun schemaEightExplicitlyRetainsSevenSixFiveOnly() {
        val s = schema8()
        assertEquals(v8, s.requiredTables(8))
        assertEquals(v7, s.requiredTables(7))
        assertEquals(v6, s.requiredTables(6))
        assertEquals(v5, s.requiredTables(5))
        for (version in listOf(4, 9, 0, 999)) {
            assertThrows(IllegalStateException::class.java) { s.requiredTables(version) }
        }
    }

    @Test fun existingSevenSixFiveProfilesRemainUnchangedByDefault() {
        val old = StagedSqlitePreflight.Schema(
            "reality.db", 7, v7,
            legacyVersion = 6, legacyTables = v6,
            olderLegacyVersion = 5, olderLegacyTables = v5,
        )
        assertEquals(v7, old.requiredTables(7))
        assertEquals(v6, old.requiredTables(6))
        assertEquals(v5, old.requiredTables(5))
        assertThrows(IllegalStateException::class.java) { old.requiredTables(8) }
        assertThrows(IllegalStateException::class.java) { old.requiredTables(4) }
    }

    @Test fun invalidOldestProfilesOrOrderingAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StagedSqlitePreflight.Schema("reality.db", 8, v8,
                oldestLegacyVersion=5, oldestLegacyTables=v5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            schema8().copy(oldestLegacyVersion=6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            schema8().copy(oldestLegacyTables=emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            schema8().copy(oldestLegacyVersion=null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            schema8().copy(oldestLegacyTables=listOf("base_state", "base_state"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            schema8().copy(olderLegacyVersion=7)
        }
    }

    @Test fun sealedStagedAndActiveProbesReadFourExactVersionsWithoutMutation() {
        val profiles=listOf(5 to v5,6 to v6,7 to v7,8 to v8)
        for ((version,tables) in profiles) {
            val dir=fixture("valid-$version",version,tables)
            val db=dir.resolve("reality.db")
            val bytesBefore=Files.readAllBytes(db)
            StagedSqlitePreflight.requireAll(dir,listOf(schema8()))
            ActiveSqlitePreflight.requireAll(dir,listOf(schema8()))
            assertArrayEquals(bytesBefore,Files.readAllBytes(db))
            val entries=Files.list(dir).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
            assertEquals(listOf("reality.db"),entries)
        }
    }

    @Test fun missingRequiredTableIsRejectedForCurrentAndOldest() {
        val missingCurrent=fixture("missing-current",8,v7)
        assertThrows(IllegalStateException::class.java) {
            StagedSqlitePreflight.requireAll(missingCurrent,listOf(schema8()))
        }
        val missingOldest=fixture("missing-oldest",5,emptyList())
        assertThrows(IllegalStateException::class.java) {
            ActiveSqlitePreflight.requireAll(missingOldest,listOf(schema8()))
        }
        assertTrue(Files.isRegularFile(missingCurrent.resolve("reality.db")))
    }

    @Test fun unknownVersionsUnsealedWalAndFinalSymlinkFailClosed() {
        for (version in listOf(4,9)) {
            val dir=fixture("bad-version-$version",version,v8)
            assertThrows(IllegalStateException::class.java) {
                StagedSqlitePreflight.requireAll(dir,listOf(schema8()))
            }
        }
        val walDir=fixture("unsealed-wal",8,v8)
        val wal=Files.writeString(walDir.resolve("reality.db-wal"),"not a sealed WAL")
        assertThrows(IllegalStateException::class.java) {
            StagedSqlitePreflight.requireAll(walDir,listOf(schema8()))
        }
        assertEquals("not a sealed WAL",Files.readString(wal))
        val symlinkDir=Files.createDirectory(root.resolve("symlink-db"))
        Files.createSymbolicLink(symlinkDir.resolve("reality.db"),
            fixture("symlink-target",8,v8).resolve("reality.db"))
        assertThrows(IllegalStateException::class.java) {
            StagedSqlitePreflight.requireAll(symlinkDir,listOf(schema8()))
        }
        assertThrows(IllegalStateException::class.java) {
            ActiveSqlitePreflight.requireAll(symlinkDir,listOf(schema8()))
        }
    }
}
