package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Eight NEW real SQLite9 post-COMMIT transition tests. No Android IPC claim. */
class RealityFinance9CommittedReadPolicyChangeTest {
    @TempDir lateinit var root: Path

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stageNine(name: String): Path =
        root.resolve(name + ".db").also { file ->
            RealityStore(file.toString(), planningStage8 = true).use {
                assertEquals(8, it.schemaVersion())
            }
            open(file).use {
                RealityFinance9Schema.installOnStagedEightConnection(
                    it, "post-commit-" + name, "e".repeat(64), 1000L,
                )
            }
        }

    private fun issue() = Finance9TrustedReadPolicyIssueRequest(
        principalHash = "a".repeat(64),
        generationId = "committed-read-policy",
        pointerSequence = 9L,
        issuedAtMs = 1000L,
        expiresAtMs = 5000L,
    )

    private fun revoke() = Finance9TrustedReadPolicyRevocationRequest(
        issue().principalHash, issue().generationId, issue().pointerSequence,
    )

    private fun counter(file: Path): Pair<Long, Long> = open(file).use {
        it.prepare(
            "SELECT next_policy_epoch,policy_revision FROM finance_acl_state WHERE singleton_id=1"
        ).use { st ->
            assertTrue(st.step())
            st.getLong(0) to st.getLong(1)
        }
    }

    private fun policy(file: Path): Pair<Long, Long>? = open(file).use { db ->
        db.prepare(
            "SELECT policy_epoch,revoked FROM finance_acl_policy WHERE " +
                "operation_id='finance.snapshot.read' AND object_scope='finance:all'"
        ).use { st ->
            if (!st.step()) null else st.getLong(0) to st.getLong(1)
        }
    }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use { st ->
            assertTrue(st.step())
            st.getLong(0)
        }
    }

    private fun port(file: Path, action: (RealityFinance9CommittedReadPolicyChangePort) -> Unit) {
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            action(it.finance9CommittedReadPolicyChangesForDevOnly())
        }
    }

    @Test fun c01IssueResultIsVisibleOnIndependentConnectionAfterCommit() {
        val file = stageNine("issue")
        port(file) {
            assertEquals(Finance9CommittedReadPolicyChange(1L, true),
                it.issueAfterExternallyVerifiedReadAdmission(issue()))
            assertEquals(1L to 0L, policy(file))
            assertEquals(2L to 1L, counter(file))
        }
    }

    @Test fun c02FirstRevokeIsOneCommittedTransition() {
        val file = stageNine("first-revoke")
        port(file) {
            assertEquals(1L, it.issueAfterExternallyVerifiedReadAdmission(issue())?.observedPolicyEpoch)
            assertEquals(Finance9CommittedReadPolicyChange(2L, true),
                it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            assertEquals(2L to 1L, policy(file))
            assertEquals(3L to 2L, counter(file))
        }
    }

    @Test fun c03SecondRevokeIsNoOpAndDoesNotAdvanceEpoch() {
        val file = stageNine("repeat")
        port(file) {
            it.issueAfterExternallyVerifiedReadAdmission(issue())
            assertEquals(Finance9CommittedReadPolicyChange(2L, true),
                it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            val before = counter(file)
            assertEquals(Finance9CommittedReadPolicyChange(2L, false),
                it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            assertEquals(before, counter(file))
        }
    }

    @Test fun c04AbsentOrForeignScopeReturnsNullWithoutAdvancingState() {
        val file = stageNine("foreign")
        port(file) {
            assertNull(it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            assertEquals(1L to 0L, counter(file))
            it.issueAfterExternallyVerifiedReadAdmission(issue())
            assertNull(it.revokeAfterExternallyVerifiedReadAdmission(
                revoke().copy(principalHash = "b".repeat(64))))
            assertNull(it.revokeAfterExternallyVerifiedReadAdmission(
                revoke().copy(pointerSequence = 11L)))
            assertEquals(2L to 1L, counter(file))
        }
    }

    @Test fun c05ReissueCreatesNewEpochAndNewRevocation() {
        val file = stageNine("reissue")
        port(file) {
            assertEquals(1L, it.issueAfterExternallyVerifiedReadAdmission(issue())?.observedPolicyEpoch)
            assertEquals(2L, it.revokeAfterExternallyVerifiedReadAdmission(revoke())?.observedPolicyEpoch)
            assertEquals(Finance9CommittedReadPolicyChange(3L, true),
                it.issueAfterExternallyVerifiedReadAdmission(issue().copy(
                    issuedAtMs = 1200L, expiresAtMs = 6000L,
                )))
            assertEquals(Finance9CommittedReadPolicyChange(4L, true),
                it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            assertEquals(4L to 1L, policy(file))
            assertEquals(5L to 4L, counter(file))
        }
    }

    @Test fun c06SqlAbortRollsBackAndReturnsNoCommittedResult() {
        val file = stageNine("abort")
        port(file) {
            it.issueAfterExternallyVerifiedReadAdmission(issue())
            val before = counter(file)
            open(file).use { db ->
                db.execSQL(
                    """CREATE TRIGGER block_committed_read_revoke
                       BEFORE UPDATE OF revoked ON finance_acl_policy
                       WHEN NEW.operation_id='finance.snapshot.read'
                       BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END""".trimIndent()
                )
            }
            assertNull(it.revokeAfterExternallyVerifiedReadAdmission(revoke()))
            assertEquals(before, counter(file))
            assertEquals(1L to 0L, policy(file))
        }
    }

    @Test fun c07SchemaEightAndClosedStoreNeverIssueCommittedResult() {
        val eight = root.resolve("eight.db")
        RealityStore(eight.toString(), planningStage8 = true).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9CommittedReadPolicyChangesForDevOnly()
            }
        }
        val file = stageNine("closed")
        val store = RealityStore(file.toString(), planningStage8 = true, financeStage9 = true)
        val port = store.finance9CommittedReadPolicyChangesForDevOnly()
        store.close()
        assertNull(port.issueAfterExternallyVerifiedReadAdmission(issue()))
        assertNull(port.revokeAfterExternallyVerifiedReadAdmission(revoke()))
        assertEquals(1L to 0L, counter(file))
    }

    @Test fun c08PoliciesNeverCreateApplyGrantsLedgerOrReceipts() {
        val file = stageNine("no-effects")
        port(file) {
            it.issueAfterExternallyVerifiedReadAdmission(issue())
            it.revokeAfterExternallyVerifiedReadAdmission(revoke())
            it.revokeAfterExternallyVerifiedReadAdmission(revoke())
        }
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
    }
}
