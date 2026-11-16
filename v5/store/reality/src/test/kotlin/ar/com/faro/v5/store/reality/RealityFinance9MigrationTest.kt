package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0A checks SQL only. Generation ownership, ACL host, backup and Android are not certified. */
class RealityFinance9MigrationTest {
    @TempDir lateinit var root: Path

    private fun base(name: String, version: Int = 8): String {
        val file = root.resolve(name).toString()
        RealityStore(file, planningStage8 = version == 8).use { store ->
            assertEquals(version, store.schemaVersion())
        }
        return file
    }

    private fun open(file: String): SQLiteConnection =
        BundledSQLiteDriver().open(file).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT count(*) FROM " + table).use {
            check(it.step())
            it.getLong(0)
        }

    private fun SQLiteConnection.version(): Long =
        prepare("PRAGMA user_version").use {
            check(it.step())
            it.getLong(0)
        }

    private fun SQLiteConnection.hasTable(table: String): Boolean =
        prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use {
            it.bindText(1, table)
            it.step()
        }

    private fun SQLiteConnection.text(sql: String): String =
        prepare(sql).use { check(it.step()); it.getText(0) }

    private fun install(file: String) {
        open(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(
                it, "migration-test", "a".repeat(64), 1000L,
            )
        }
    }

    @Test fun sourceSevenIsUnchangedWhileIndependentSevenToEightToNineStageIsInstalled() {
        val source = base("active-seven.db", 7)
        val stage = base("separate-stage.db", 7)
        RealityStore(stage, planningStage8 = true).use { assertEquals(8, it.schemaVersion()) }
        install(stage)
        open(stage).use {
            assertEquals(9L, it.version())
            assertTrue(it.hasTable("finance_plan_item"))
        }
        RealityStore(source).use { assertEquals(7, it.schemaVersion()) }
        open(source).use { assertFalse(it.hasTable("finance_acl_state")) }
    }

    @Test fun stagedEightBecomesNineWithoutChangingLegacyOpeners() {
        val stage = base("eight.db")
        install(stage)
        open(stage).use {
            assertEquals(9L, it.version())
            assertTrue(it.hasTable("reminder_rule"))
            assertTrue(it.hasTable("monthly_plan_item"))
            assertEquals(1L, it.count("finance_acl_state"))
            assertEquals("ok", it.text("PRAGMA integrity_check"))
        }
        assertThrows(IllegalStateException::class.java) { RealityStore(stage).use { } }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(stage, planningStage8 = true).use { }
        }
    }

    @Test fun legacyOpeningRemainsUnknownRatherThanInferredZero() {
        val file = base("opening.db")
        open(file).use {
            it.execSQL("INSERT INTO finance_account VALUES ('acct-1','Efectivo','ARS','CASH')")
            it.execSQL("INSERT INTO finance_account_opening VALUES ('acct-1','cause-1',500)")
        }
        install(file)
        open(file).use { db ->
            db.prepare(
                "SELECT confirmation_state,amount_minor,confirmed_at_ms " +
                    "FROM finance_opening_confirmation WHERE account_id='acct-1'"
            ).use {
                assertTrue(it.step())
                assertEquals("LEGACY_UNVERIFIED", it.getText(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
            assertEquals(500L, db.prepare(
                "SELECT amount_minor FROM finance_account_opening WHERE account_id='acct-1'"
            ).use { assertTrue(it.step()); it.getLong(0) })
        }
    }

    @Test fun ddlFailureRollsBackEverythingIncludingSchemaVersion() {
        val file = base("rollback.db")
        open(file).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityFinance9Schema.installOnStagedEightConnection(
                    db, "fault", "b".repeat(64), 100L,
                    afterStatement = { index -> if (index == 5) error("injected DDL fault") },
                )
            }
            assertEquals(8L, db.version())
            assertFalse(db.hasTable("finance_acl_state"))
            assertFalse(db.hasTable("finance_opening_confirmation"))
            assertEquals("ok", db.text("PRAGMA integrity_check"))
        }
    }

    @Test fun foreignKeysAndTransferTriggerRejectUnmatchedEffects() {
        val file = base("foreign.db")
        install(file)
        open(file).use { db ->
            db.execSQL("INSERT INTO finance_account VALUES ('from','Cuenta origen','ARS','CASH')")
            db.execSQL("INSERT INTO finance_account VALUES ('to','Cuenta destino','ARS','CASH')")
            db.execSQL(
                "INSERT INTO finance_entry(entry_id,account_id,amount_minor,kind,currency_code," +
                    "concept,category,effective_at_epoch_millis,recorded_at_epoch_millis)" +
                    " VALUES('credit','to',100,'INCOME','ARS','Test','Transfer',10,10)"
            )
            assertThrows(Exception::class.java) {
                db.execSQL(
                    "INSERT INTO finance_transfer(transfer_id,from_account_id,to_account_id," +
                        "debit_entry_id,credit_entry_id,currency_code,amount_minor," +
                        "effective_at_epoch_millis,recorded_at_epoch_millis) VALUES " +
                        "('t1','from','to','missing','credit','ARS',100,10,10)"
                )
            }
            assertEquals(0L, db.count("finance_transfer"))
            assertThrows(Exception::class.java) {
                db.execSQL(
                    "INSERT INTO finance_plan_item_payment VALUES('missing-plan','credit',100)"
                )
            }
            assertEquals(0L, db.count("finance_plan_item_payment"))
        }
    }

    @Test fun durablePolicyEpochIsPresentButNoApplicationGrantIsCreated() {
        val file = base("epoch.db")
        install(file)
        open(file).use { db ->
            assertEquals(1L, db.prepare("SELECT next_policy_epoch FROM finance_acl_state").use {
                assertTrue(it.step())
                it.getLong(0)
            })
            db.execSQL(
                "UPDATE finance_acl_state SET next_policy_epoch=2,policy_revision=1 " +
                    "WHERE singleton_id=1"
            )
            assertEquals(0L, db.count("finance_apply_grant"))
        }
        open(file).use { db ->
            assertEquals(2L, db.prepare("SELECT next_policy_epoch FROM finance_acl_state").use {
                assertTrue(it.step())
                it.getLong(0)
            })
        }
    }

    @Test fun unexpectedSchemaOrMissingPlanningTableFailsWithoutWrites() {
        val old = base("old.db", 7)
        open(old).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityFinance9Schema.installOnStagedEightConnection(
                    db, "wrong-version", "c".repeat(64), 0L,
                )
            }
            assertEquals(7L, db.version())
            assertFalse(db.hasTable("finance_acl_state"))
        }
        val damaged = base("missing-planning.db")
        open(damaged).use { db ->
            db.execSQL("DROP TABLE monthly_plan_item")
            assertThrows(IllegalStateException::class.java) {
                RealityFinance9Schema.installOnStagedEightConnection(
                    db, "missing-table", "d".repeat(64), 0L,
                )
            }
            assertEquals(8L, db.version())
            assertFalse(db.hasTable("finance_acl_state"))
        }
    }
}
