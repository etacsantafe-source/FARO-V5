package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Eight NEW STAGED9 real SQLite READ-revocation checks. No Android physical claims. */
class RealityFinance9TrustedReadPolicyRevocationTest {
    @TempDir lateinit var root: Path

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stageEight(name: String): Path =
        root.resolve(name + ".db").also { file ->
            RealityStore(file.toString(), planningStage8 = true).use {
                assertEquals(8, it.schemaVersion())
            }
        }

    private fun stageNine(name: String): Path = stageEight(name).also { file ->
        open(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(
                it, "b3b1b1b1b1a-" + name, "e".repeat(64), 1000L,
            )
        }
    }

    private fun request() = Finance9TrustedReadPolicyRevocationRequest(
        principalHash = "a".repeat(64),
        generationId = "stage9-dev-read-revoker",
        pointerSequence = 9L,
    )

    private fun scope(
        r: Finance9TrustedReadPolicyRevocationRequest = request(),
        operation: String = RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
    ) = Finance9AclScope(
        r.principalHash, r.generationId, r.pointerSequence,
        operation, RealityFinance9ReadPolicyObservationPort.OBJECT_SCOPE,
    )

    private fun issue(
        file: Path,
        r: Finance9TrustedReadPolicyRevocationRequest = request(),
        at: Long = 1000L,
        expiry: Long = 5000L,
    ): Long = open(file).use {
        RealityFinance9Acl(it, { true }).issueAfterTrustedStageAdmission(
            scope(r), at, expiry,
        )
    }

    private fun revoke(
        file: Path,
        r: Finance9TrustedReadPolicyRevocationRequest = request(),
    ): Finance9ObservedRevokedReadPolicyRecord? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9TrustedReadPolicyRevokerForDevOnly()
                .revokeAfterExternallyVerifiedReadAdmission(r)
        }

    private fun observe(
        file: Path, epoch: Long, r: Finance9TrustedReadPolicyRevocationRequest = request(),
        at: Long = 1200L,
    ): RealityFinance9ObservedReadPolicy? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9ReadPolicyObservationForDevOnly().observe(
                RealityFinance9ReadPolicyObservationRequest(
                    r.principalHash, r.generationId, r.pointerSequence, epoch, at,
                ),
            )
        }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use {
            assertTrue(it.step())
            it.getLong(0)
        }
    }

    private fun counters(file: Path): Pair<Long, Long> = open(file).use { db ->
        db.prepare(
            "SELECT next_policy_epoch,policy_revision FROM finance_acl_state WHERE singleton_id=1"
        ).use {
            assertTrue(it.step())
            it.getLong(0) to it.getLong(1)
        }
    }

    private fun assertNoApplyEffects(file: Path) {
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(0L, count(file, "finance_account"))
    }

    @Test fun r01SchemaSevenEightClosedAndActiveApplyFailClosed() {
        val seven = root.resolve("seven.db")
        RealityStore(seven.toString()).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9TrustedReadPolicyRevokerForDevOnly()
            }
        }
        val eight = stageEight("eight")
        RealityStore(eight.toString(), planningStage8 = true).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9TrustedReadPolicyRevokerForDevOnly()
            }
        }
        val file = stageNine("closed-active")
        issue(file)
        val store = RealityStore(file.toString(), planningStage8 = true, financeStage9 = true)
        val port = store.finance9TrustedReadPolicyRevokerForDevOnly()
        store.run(TransactionId("read-revoke-active")) {
            assertNull(port.revokeAfterExternallyVerifiedReadAdmission(request()))
            AtomicApplyTransactionDecision.Rollback(Unit)
        }
        assertNotNull(observe(file, 1L))
        store.close()
        assertNull(port.revokeAfterExternallyVerifiedReadAdmission(request()))
        assertNotNull(observe(file, 1L))
        assertNoApplyEffects(file)
    }

    @Test fun r02FixedReadScopeRevokesWithoutApplyOrLedgerEffects() {
        val file = stageNine("fixed")
        assertEquals(1L, issue(file))
        val record = requireNotNull(revoke(file))
        assertEquals(2L, record.observedRevokedPolicyEpoch)
        assertEquals(request().principalHash, record.principalHash)
        assertNull(observe(file, 1L))
        open(file).use { db ->
            db.prepare("SELECT operation_id,object_scope,revoked,policy_epoch FROM finance_acl_policy")
                .use {
                    assertTrue(it.step())
                    assertEquals("finance.snapshot.read", it.getText(0))
                    assertEquals("finance:all", it.getText(1))
                    assertEquals(1L, it.getLong(2))
                    assertEquals(2L, it.getLong(3))
                    assertFalse(it.step())
                }
        }
        assertNoApplyEffects(file)
    }

    @Test fun r03AbsentAndIdempotentRepeatDoNotAdvanceCounter() {
        val file = stageNine("idempotent")
        assertNull(revoke(file))
        assertEquals(1L to 0L, counters(file))
        issue(file)
        val rev = requireNotNull(revoke(file)).observedRevokedPolicyEpoch
        assertEquals(2L, rev)
        val after = counters(file)
        assertEquals(2L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        assertEquals(after, counters(file))
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertNoApplyEffects(file)
    }

    @Test fun r04ForeignPrincipalPointerGenerationAndApplyScopeAreIsolated() {
        val file = stageNine("foreign")
        issue(file)
        assertNull(revoke(file, request().copy(principalHash = "b".repeat(64))))
        assertNull(revoke(file, request().copy(pointerSequence = 11L)))
        assertNull(revoke(file, request().copy(generationId = "foreign")))
        open(file).use {
            assertEquals(2L, RealityFinance9Acl(it, { true })
                .issueAfterTrustedStageAdmission(scope(operation = "finance.account.create"), 1000L, 5000L))
        }
        assertEquals(3L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        open(file).use {
            assertEquals(2L, RealityFinance9Acl(it, { true })
                .observedCurrentEpochOrNull(scope(operation = "finance.account.create"), 1200L))
        }
        assertNull(observe(file, 1L))
        assertEquals(3L, count(file, "finance_acl_state") + count(file, "finance_acl_policy") - 0L)
        assertNoApplyEffects(file)
    }

    @Test fun r05ReissueAbaOldEpochNeverResurrects() {
        val file = stageNine("aba")
        val first = issue(file)
        assertEquals(2L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        val renewal = issue(file, at = 1200L, expiry = 6000L)
        assertEquals(3L, renewal)
        assertNull(observe(file, first))
        assertNotNull(observe(file, renewal, at = 1300L))
        assertEquals(4L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        assertNull(observe(file, renewal, at = 1300L))
        assertNoApplyEffects(file)
    }

    @Test fun r06ExpiryNeverGrantsReadOrRestartsOldEpoch() {
        val file = stageNine("expires")
        val epoch = issue(file, expiry = 1500L)
        assertNull(observe(file, epoch, at = 1500L))
        assertEquals(2L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        assertNull(observe(file, epoch, at = 1400L))
        assertNoApplyEffects(file)
    }

    @Test fun r07RealSecondOsPidRevocationAndNewOwnerRevocationPersist() {
        val file = stageNine("two-pid")
        val first = issue(file)
        assertNotNull(observe(file, first))
        RealityFinance9AclProcessWorker.start(root, file, "REVOKE", scope()).use { child ->
            child.beginAttempt()
            assertTrue(child.finish().contains("DONE:" + child.pid))
        }
        assertNull(observe(file, first))
        assertEquals(3L, issue(file, at = 1200L, expiry = 6000L))
        assertNotNull(observe(file, 3L, at = 1300L))
        assertEquals(4L, requireNotNull(revoke(file)).observedRevokedPolicyEpoch)
        assertNull(observe(file, 3L, at = 1300L))
        assertNoApplyEffects(file)
    }

    @Test fun r08MalformedRequestAndSqlAbortRollbackNeverReportSuccess() {
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(principalHash = "invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(pointerSequence = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(generationId = "")
        }
        val file = stageNine("sql-rollback")
        val epoch = issue(file)
        val before = counters(file)
        open(file).use {
            it.execSQL(
                """CREATE TRIGGER block_read_revoke BEFORE UPDATE OF revoked ON finance_acl_policy
                   WHEN NEW.operation_id='finance.snapshot.read'
                   BEGIN SELECT RAISE(ABORT,'test rollback'); END""".trimIndent(),
            )
        }
        assertNull(revoke(file))
        assertEquals(before, counters(file))
        assertNotNull(observe(file, epoch))
        assertNoApplyEffects(file)
    }
}
