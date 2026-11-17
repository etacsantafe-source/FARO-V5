package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Real SQLite DEV/STAGED9 test. Synthetic issuer DOES NOT authenticate a user. */
class RealityFinance9ReadPolicyObservationTest {
    @TempDir lateinit var root: Path

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stageEight(name: String): Path =
        root.resolve(name + ".db").also { f ->
            RealityStore(f.toString(), planningStage8 = true).use {
                assertEquals(8, it.schemaVersion())
            }
        }

    private fun stageNine(name: String): Path = stageEight(name).also { f ->
        open(f).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "b3b1a-" + name, "f".repeat(64), 1000L,
            )
        }
    }

    private fun request() = RealityFinance9ReadPolicyObservationRequest(
        principalHash = "a".repeat(64),
        generationId = "stage9-generation",
        pointerSequence = 7L,
        expectedPolicyEpoch = 1L,
        observedAtMs = 1200L,
    )

    private fun key(req: RealityFinance9ReadPolicyObservationRequest, operation: String =
        RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
    ) = Finance9AclScope(
        req.principalHash, req.generationId, req.pointerSequence,
        operation, RealityFinance9ReadPolicyObservationPort.OBJECT_SCOPE,
    )

    private fun issue(
        file: Path,
        req: RealityFinance9ReadPolicyObservationRequest = request(),
        operation: String = RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
        at: Long = 1000L,
        expiry: Long = 5000L,
    ): Long = open(file).use {
        RealityFinance9Acl(it, { true }).issueAfterTrustedStageAdmission(
            key(req, operation), at, expiry,
        )
    }

    private fun revoke(file: Path, req: RealityFinance9ReadPolicyObservationRequest): Long? =
        open(file).use {
            RealityFinance9Acl(it, { true }).revoke(key(req))
        }

    private fun observe(
        file: Path, req: RealityFinance9ReadPolicyObservationRequest = request(),
    ): RealityFinance9ObservedReadPolicy? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9ReadPolicyObservationForDevOnly().observe(req)
        }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use {
            assertTrue(it.step())
            it.getLong(0)
        }
    }

    @Test fun q01NoReadPolicyIsNotObservedAndDoesNotCreateRows() {
        val file = stageNine("missing")
        assertNull(observe(file))
        assertEquals(0L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun q02ExactExplicitSqlReadPolicyObservedButNeverAuthorizesDisplay() {
        val file = stageNine("read-scope")
        assertEquals(1L, issue(file))
        val observed = requireNotNull(observe(file))
        assertEquals("stage9-generation", observed.generationId)
        assertEquals(7L, observed.pointerSequence)
        assertEquals(1L, observed.policyEpoch)
        assertEquals(1L, observed.globalPolicyRevision)
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun q03ForeignPrincipalGenerationPointerOrEpochAlwaysDenied() {
        val file = stageNine("foreign")
        issue(file)
        assertNull(observe(file, request().copy(principalHash = "b".repeat(64))))
        assertNull(observe(file, request().copy(generationId = "foreign")))
        assertNull(observe(file, request().copy(pointerSequence = 8L)))
        assertNull(observe(file, request().copy(expectedPolicyEpoch = 2L)))
        assertNotNull(observe(file))
    }

    @Test fun q04WriteOnlyPolicyCannotBeSubstitutedForReadOperation() {
        val file = stageNine("write")
        assertEquals(1L, issue(file, operation = "finance.account.create"))
        assertNull(observe(file))
        assertEquals(0L, count(file, "finance_apply_grant"))
        open(file).use { db ->
            db.prepare("SELECT operation_id FROM finance_acl_policy").use {
                assertTrue(it.step())
                assertEquals("finance.account.create", it.getText(0))
            }
        }
    }

    @Test fun q05ExpiryAtBoundaryAndInvalidTimeCannotBeAdmitted() {
        val file = stageNine("expiry")
        issue(file, expiry = 1500L)
        assertNotNull(observe(file, request().copy(observedAtMs = 1499L)))
        assertNull(observe(file, request().copy(observedAtMs = 1500L)))
        assertNull(observe(file, request().copy(observedAtMs = 9999L)))
    }

    @Test fun q06RevocationAndRenewalDoNotResurrectAnOldPolicyEpoch() {
        val file = stageNine("aba")
        val req = request()
        assertEquals(1L, issue(file))
        assertNotNull(observe(file, req))
        assertEquals(2L, revoke(file, req))
        assertNull(observe(file, req))
        assertEquals(3L, issue(file, at = 1201L, expiry = 6000L))
        assertNull(observe(file, req))
        val now = requireNotNull(observe(file, req.copy(
            expectedPolicyEpoch = 3L, observedAtMs = 1300L,
        )))
        assertEquals(3L, now.policyEpoch)
        assertEquals(3L, now.globalPolicyRevision)
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun q07SchemaSevenEightAndClosedRealityPortDeny() {
        val seven = root.resolve("seven.db")
        RealityStore(seven.toString()).use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.finance9ReadPolicyObservationForDevOnly()
            }
        }
        val eight = stageEight("eight")
        RealityStore(eight.toString(), planningStage8 = true).use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.finance9ReadPolicyObservationForDevOnly()
            }
        }
        val nine = stageNine("closed")
        issue(nine)
        val store = RealityStore(nine.toString(), planningStage8 = true, financeStage9 = true)
        val port = store.finance9ReadPolicyObservationForDevOnly()
        store.close()
        assertNull(port.observe(request()))
        open(seven).use { db ->
            db.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(7L, it.getLong(0))
            }
        }
        open(eight).use { db ->
            db.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(8L, it.getLong(0))
            }
        }
    }

    @Test fun q08MalformedRequestAndRepeatedObservationNeverCreateGrants() {
        val req = request()
        assertThrows(IllegalArgumentException::class.java) {
            req.copy(principalHash = "not-a-sha")
        }
        assertThrows(IllegalArgumentException::class.java) {
            req.copy(pointerSequence = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            req.copy(expectedPolicyEpoch = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            req.copy(observedAtMs = -1L)
        }
        val file = stageNine("readonly")
        issue(file)
        val original = requireNotNull(observe(file))
        repeat(3) { assertEquals(original, observe(file)) }
        open(file).use { db ->
            val state = RealityFinance9Acl(db, { true }).observedCounterForStageTest()
            assertEquals(2L to 1L, state)
        }
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }
}
