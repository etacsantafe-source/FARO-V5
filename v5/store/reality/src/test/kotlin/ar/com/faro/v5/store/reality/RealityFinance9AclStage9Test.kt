package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M1A: staged SQL policy ONLY, not human auth, product Apply or two OS processes. */
class RealityFinance9AclStage9Test {
    @TempDir lateinit var root: Path

    private fun stageEight(name: String): String {
        val path = root.resolve(name + ".db").toString()
        RealityStore(path, planningStage8 = true).use {
            assertEquals(8, it.schemaVersion())
        }
        return path
    }

    private fun open(file: String): SQLiteConnection =
        BundledSQLiteDriver().open(file).also { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stageNine(name: String): String {
        val file = stageEight(name)
        open(file).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "m1a-" + name, "a".repeat(64), 1000L,
            )
        }
        return file
    }

    private fun owner(
        db: SQLiteConnection,
        admission: () -> Boolean = { true },
        beforeCommit: () -> Unit = {},
    ) = RealityFinance9Acl(db, admission, beforeCommit)

    private val key = Finance9AclScope(
        principalHash = "a".repeat(64),
        generationId = "stage9-finance-local",
        pointerSequence = 3L,
        operationId = "finance.entry.record",
        objectScope = "entry-test-1",
    )

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT count(*) FROM " + table).use {
            assertTrue(it.step())
            it.getLong(0)
        }

    @Test fun stage9WithExactAclTablesAdmitsIssueAndDurableEpoch() {
        val file = stageNine("durable")
        open(file).use { db ->
            val acl = owner(db)
            assertEquals(1L, acl.issueAfterTrustedStageAdmission(key, 100L, 2000L))
            assertEquals(1L, acl.observedCurrentEpochOrNull(key, 101L))
            assertEquals(2L to 1L, acl.observedCounterForStageTest())
            assertEquals(1L, db.count("finance_acl_policy"))
            assertEquals(0L, db.count("finance_apply_grant"))
        }
        open(file).use { db ->
            val reopened = owner(db)
            assertEquals(1L, reopened.observedCurrentEpochOrNull(key, 1999L))
            assertEquals(2L to 1L, reopened.observedCounterForStageTest())
            assertEquals(0L, db.count("finance_apply_grant"))
        }
    }

    @Test fun revokeAndRegrantNeverRecyclePolicyEpoch() {
        val file = stageNine("monotonic")
        open(file).use { db ->
            val acl = owner(db)
            assertEquals(1L, acl.issueAfterTrustedStageAdmission(key, 100L, 9000L))
            assertEquals(2L, acl.revoke(key))
            assertNull(acl.observedCurrentEpochOrNull(key, 101L))
            assertEquals(2L, acl.revoke(key)) // idempotent revoke
            assertEquals(3L, acl.issueAfterTrustedStageAdmission(key, 102L, 9500L))
            assertEquals(3L, acl.observedCurrentEpochOrNull(key, 103L))
            assertEquals(4L to 3L, acl.observedCounterForStageTest())
            assertEquals(1L, db.count("finance_acl_policy")) // no DELETE or epoch recycling
        }
    }

    @Test fun twoSqliteConnectionsSerializeRevocationAndCurrentLookup() {
        val file = stageNine("connections")
        open(file).use { first ->
            open(file).use { second ->
                val a = owner(first)
                val b = owner(second)
                assertEquals(1L, a.issueAfterTrustedStageAdmission(key, 100L, 9000L))
                assertEquals(1L, b.observedCurrentEpochOrNull(key, 200L))
                assertEquals(2L, b.revoke(key))
                assertNull(a.observedCurrentEpochOrNull(key, 201L))
                assertEquals(3L, a.issueAfterTrustedStageAdmission(key, 202L, 8000L))
                assertEquals(3L, b.observedCurrentEpochOrNull(key, 203L))
                assertEquals(4L to 3L, b.observedCounterForStageTest())
                // This test is two SQLite connections, NOT a two-OS-process proof.
            }
        }
    }

    @Test fun wrongSchemaAndDeniedStageFenceNeverMutate() {
        val old = stageEight("old-eight")
        open(old).use { db ->
            assertThrows(Exception::class.java) {
                owner(db).issueAfterTrustedStageAdmission(key, 100L, 9000L)
            }
            db.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(8L, it.getLong(0))
            }
        }
        val stage9 = stageNine("closed-gate")
        open(stage9).use { db ->
            assertThrows(Exception::class.java) {
                owner(db, admission = { false })
                    .issueAfterTrustedStageAdmission(key, 100L, 9000L)
            }
            assertEquals(0L, db.count("finance_acl_policy"))
            assertEquals(1L to 0L, owner(db).observedCounterForStageTest())
        }
    }

    @Test fun epochOrRevisionOverflowFailsClosedAndRollsBack() {
        val stage9 = stageNine("overflow")
        open(stage9).use { db ->
            val acl = owner(db)
            db.execSQL(
                "UPDATE finance_acl_state SET next_policy_epoch=9223372036854775807," +
                    "policy_revision=2 WHERE singleton_id=1"
            )
            assertThrows(Exception::class.java) {
                acl.issueAfterTrustedStageAdmission(key, 100L, 9000L)
            }
            assertEquals(Long.MAX_VALUE to 2L, acl.observedCounterForStageTest())
            assertEquals(0L, db.count("finance_acl_policy"))

            db.execSQL(
                "UPDATE finance_acl_state SET next_policy_epoch=4," +
                    "policy_revision=9223372036854775807 WHERE singleton_id=1"
            )
            assertThrows(Exception::class.java) {
                acl.issueAfterTrustedStageAdmission(key, 100L, 9000L)
            }
            assertEquals(4L to Long.MAX_VALUE, acl.observedCounterForStageTest())
            assertEquals(0L, db.count("finance_acl_policy"))
        }
    }

    @Test fun foreignPrincipalGenerationPointerAndExpiryDeny() {
        val file = stageNine("foreign")
        open(file).use { db ->
            val acl = owner(db)
            assertEquals(1L, acl.issueAfterTrustedStageAdmission(key, 100L, 2000L))
            assertNull(acl.observedCurrentEpochOrNull(
                key.copy(principalHash = "b".repeat(64)), 101L,
            ))
            assertNull(acl.observedCurrentEpochOrNull(
                key.copy(generationId = "untrusted-generation"), 101L,
            ))
            assertNull(acl.observedCurrentEpochOrNull(
                key.copy(pointerSequence = 4L), 101L,
            ))
            assertNull(acl.observedCurrentEpochOrNull(
                key.copy(operationId = "finance.account.create"), 101L,
            ))
            assertNull(acl.observedCurrentEpochOrNull(
                key.copy(objectScope = "another-object"), 101L,
            ))
            assertNull(acl.observedCurrentEpochOrNull(key, 2000L))
            assertNull(acl.revoke(key.copy(pointerSequence = 9L)))
            assertEquals(2L to 1L, acl.observedCounterForStageTest())
            assertThrows(Exception::class.java) {
                acl.issueAfterTrustedStageAdmission(key, 2000L, 2000L)
            }
            assertEquals(1L, db.count("finance_acl_policy"))
            assertEquals(0L, db.count("finance_apply_grant"))
        }
    }

    @Test fun injectedFailureRollsBackPolicyAndCounter() {
        val file = stageNine("crash")
        open(file).use { db ->
            val faulty = owner(db, beforeCommit = {
                error("injected failure immediately before SQLite COMMIT")
            })
            assertThrows(IllegalStateException::class.java) {
                faulty.issueAfterTrustedStageAdmission(key, 100L, 9000L)
            }
            assertEquals(1L to 0L, owner(db).observedCounterForStageTest())
            assertEquals(0L, db.count("finance_acl_policy"))
            assertEquals(1L, owner(db).issueAfterTrustedStageAdmission(key, 100L, 9000L))
            assertEquals(2L to 1L, owner(db).observedCounterForStageTest())
        }
    }
}
