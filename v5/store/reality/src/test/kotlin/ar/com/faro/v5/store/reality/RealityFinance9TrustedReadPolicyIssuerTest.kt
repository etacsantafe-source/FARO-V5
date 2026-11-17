package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Real SQLite9 stage-only tests. The test principal is SYNTHETIC; none of
 * these JUnit cases certifies Android BiometricPrompt, UID, user or UI access.
 */
class RealityFinance9TrustedReadPolicyIssuerTest {
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
        open(file).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "b3b1b1b1a-" + name, "e".repeat(64), 1000L,
            )
        }
    }

    private fun request() = Finance9TrustedReadPolicyIssueRequest(
        principalHash = "a".repeat(64),
        generationId = "read-only-stage9-generation",
        pointerSequence = 9L,
        issuedAtMs = 1000L,
        expiresAtMs = 5000L,
    )

    private fun issue(
        file: Path,
        r: Finance9TrustedReadPolicyIssueRequest = request(),
    ): Finance9ReadPolicyIssueRecord? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9TrustedReadPolicyIssuerForDevOnly()
                .issueAfterExternallyVerifiedReadAdmission(r)
        }

    private fun observe(
        file: Path,
        r: Finance9TrustedReadPolicyIssueRequest = request(),
        epoch: Long = 1L,
        at: Long = 1200L,
    ): RealityFinance9ObservedSourceRead? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9ObservedSourceReadForDevOnly().observe(
                RealityFinance9ReadPolicyObservationRequest(
                    principalHash = r.principalHash,
                    generationId = r.generationId,
                    pointerSequence = r.pointerSequence,
                    expectedPolicyEpoch = epoch,
                    observedAtMs = at,
                ),
            )
        }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use { st ->
            assertTrue(st.step())
            st.getLong(0)
        }
    }

    private fun scope(
        r: Finance9TrustedReadPolicyIssueRequest = request(),
        operation: String = RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
    ) = Finance9AclScope(
        r.principalHash, r.generationId, r.pointerSequence,
        operation, RealityFinance9ReadPolicyObservationPort.OBJECT_SCOPE,
    )

    @Test fun r01SchemaSevenEightCannotCreateReadPolicyIssuer() {
        val seven = root.resolve("seven.db")
        RealityStore(seven.toString()).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9TrustedReadPolicyIssuerForDevOnly()
            }
        }
        val eight = stageEight("eight")
        RealityStore(eight.toString(), planningStage8 = true).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9TrustedReadPolicyIssuerForDevOnly()
            }
        }
        assertEquals(0L, count(seven, "finance_account"))
    }

    @Test fun r02ReadPolicyIsDurableButDoesNotIssueAnApplyGrant() {
        val file = stageNine("read")
        val record = requireNotNull(issue(file))
        assertEquals(request().principalHash, record.principalHash)
        assertEquals(request().generationId, record.generationId)
        assertEquals(9L, record.pointerSequence)
        assertEquals(1L, record.policyEpoch)
        assertEquals(5000L, record.expiresAtMs)
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
        open(file).use { db ->
            db.prepare("SELECT operation_id,object_scope,revoked FROM finance_acl_policy")
                .use { st ->
                    assertTrue(st.step())
                    assertEquals("finance.snapshot.read", st.getText(0))
                    assertEquals("finance:all", st.getText(1))
                    assertEquals(0L, st.getLong(2))
                    assertFalse(st.step())
                }
        }
    }

    @Test fun r03AtomicReadObservesOnlyTheFixedReadScope() {
        val file = stageNine("observed")
        open(file).use {
            it.execSQL(
                "INSERT INTO finance_account(account_id,display_name,currency_code,nature) " +
                    "VALUES('cash','Prueba','ARS','CASH')"
            )
        }
        assertEquals(1L, requireNotNull(issue(file)).policyEpoch)
        val source = requireNotNull(observe(file))
        assertEquals(1L, source.observedPolicy.policyEpoch)
        assertEquals("read-only-stage9-generation", source.observedPolicy.generationId)
        assertEquals(9L, source.observedPolicy.pointerSequence)
        assertEquals(1, source.openingSource.accounts.size)
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun r04ForeignPrincipalPointerGenerationAndWriteScopeNeverRead() {
        val file = stageNine("foreign")
        assertEquals(1L, requireNotNull(issue(file)).policyEpoch)
        assertNull(observe(file, request().copy(principalHash = "b".repeat(64))))
        assertNull(observe(file, request().copy(pointerSequence = 10L)))
        assertNull(observe(file, request().copy(generationId = "wrong")))
        assertNull(observe(file, epoch = 2L))
        open(file).use { db ->
            assertNull(RealityFinance9Acl(db, { true }).observedCurrentEpochOrNull(
                scope(operation = "finance.account.create"), 1200L,
            ))
        }
        assertNotNull(observe(file))
    }

    @Test fun r05TtlAndExpiryBoundaryFailClosed() {
        val invalid = request()
        assertThrows(IllegalArgumentException::class.java) {
            invalid.copy(expiresAtMs = 31_001L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            invalid.copy(issuedAtMs = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            invalid.copy(expiresAtMs = 1000L)
        }
        val file = stageNine("expiry")
        val r = invalid.copy(expiresAtMs = 1500L)
        assertEquals(1L, requireNotNull(issue(file, r)).policyEpoch)
        assertNotNull(observe(file, r, at = 1499L))
        assertNull(observe(file, r, at = 1500L))
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun r06RenewalNeverResurrectsTheOldEpoch() {
        val file = stageNine("renew")
        assertEquals(1L, requireNotNull(issue(file)).policyEpoch)
        val second = requireNotNull(issue(file, request().copy(issuedAtMs = 1200L, expiresAtMs = 6000L)))
        assertEquals(2L, second.policyEpoch)
        assertNull(observe(file, epoch = 1L))
        assertEquals(2L, requireNotNull(observe(file, epoch = 2L)).observedPolicy.policyEpoch)
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun r07ExternalSqlRevokeDeniesAndNextIssueUsesFreshEpoch() {
        val file = stageNine("revoke")
        assertEquals(1L, requireNotNull(issue(file)).policyEpoch)
        assertNotNull(observe(file))
        open(file).use {
            assertEquals(2L, RealityFinance9Acl(it, { true }).revoke(scope()))
        }
        assertNull(observe(file))
        val renewed = request().copy(issuedAtMs = 1300L, expiresAtMs = 7000L)
        assertEquals(3L, requireNotNull(issue(file, renewed)).policyEpoch)
        assertNull(observe(file, epoch = 1L))
        assertEquals(3L, requireNotNull(observe(file, renewed, epoch = 3L, at = 1400L)).observedPolicy.policyEpoch)
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun r08ClosedIssuerMalformedRequestAndAllSideEffectsDeny() {
        val file = stageNine("closed")
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(principalHash = "bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(pointerSequence = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(generationId = "")
        }
        val store = RealityStore(file.toString(), planningStage8 = true, financeStage9 = true)
        val port = store.finance9TrustedReadPolicyIssuerForDevOnly()
        store.close()
        assertNull(port.issueAfterExternallyVerifiedReadAdmission(request()))
        assertEquals(0L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }
}
