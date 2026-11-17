package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Real SQLite9 staging tests. Synthetic caller is NOT authenticated human,
 * not AndroidKeyStore and NOT production Apply authority.
 */
class RealityFinance9TrustedStageGrantIssuerPortTest {
    @TempDir lateinit var root: Path

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun staged(label: String, version: Int = 9): Path {
        val file = root.resolve(label + ".db")
        RealityStore(file.toString(), planningStage8 = version != 7).use { }
        if (version == 9) open(file).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "m1c1b1c-" + label, "a".repeat(64), 1000L,
            )
        }
        return file
    }

    private fun request(label: String) = Finance9TrustedStageGrantIssueRequest(
        principalHash = "b".repeat(64),
        generationId = "generation-" + label,
        pointerSequence = 7L,
        operationId = "finance.account.create",
        objectScope = "account-" + label,
        fingerprintHex = "c".repeat(64),
        intentKey = "intent-" + label,
        grantId = "grant-" + label,
        issuedAtMs = 1500L, expiresAtMs = 20000L,
    )

    private fun issue(file: Path, req: Finance9TrustedStageGrantIssueRequest):
        Finance9TrustedStageGrant? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use { store ->
            store.finance9TrustedStageGrantIssuerForDevOnly()
                .issueAfterExternallyVerifiedStageAdmission(req)
        }

    private fun count(file: Path, table: String): Long =
        open(file).use { db ->
            db.prepare("SELECT count(*) FROM " + table).use { st ->
                assertTrue(st.step())
                st.getLong(0)
            }
        }

    @Test fun s01FactoryDenyLegacySevenAndPlanningEight() {
        for (version in listOf(7, 8)) {
            val file = staged("legacy-" + version, version)
            RealityStore(file.toString(), planningStage8 = version == 8).use { store ->
                assertThrows(IllegalStateException::class.java) {
                    store.finance9TrustedStageGrantIssuerForDevOnly()
                }
            }
        }
    }

    @Test fun s02RealStageNinePolicyGrantDurableAndUnused() {
        val file = staged("success")
        val req = request("success")
        val grant = requireNotNull(issue(file, req))
        assertEquals(req.principalHash, grant.principalHash)
        assertEquals(req.generationId, grant.generationId)
        assertEquals(req.pointerSequence, grant.pointerSequence)
        assertEquals(req.grantId, grant.grantId)
        assertEquals(1L, grant.expectedPolicyEpoch)
        assertEquals(1L, count(file, "finance_acl_policy"))
        assertEquals(1L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
        open(file).use { db ->
            db.prepare(
                "SELECT policy_epoch,fingerprint,used_at_ms,receipt_id " +
                    "FROM finance_apply_grant WHERE grant_id=?"
            ).use { st ->
                st.bindText(1, req.grantId)
                assertTrue(st.step())
                assertEquals(1L, st.getLong(0))
                assertEquals(req.fingerprintHex, st.getText(1))
                assertTrue(st.isNull(2))
                assertTrue(st.isNull(3))
                assertFalse(st.step())
            }
        }
    }

    @Test fun s03DuplicateGrantFailClosedWithoutSecondUsableGrant() {
        val file = staged("duplicate")
        val req = request("duplicate")
        val prior = requireNotNull(issue(file, req))
        assertNull(issue(file, req))
        assertEquals(1L, count(file, "finance_apply_grant"))
        assertEquals(1L, count(file, "finance_acl_policy"))
        open(file).use { db ->
            assertEquals(prior.expectedPolicyEpoch + 1L,
                RealityFinance9Acl(db, { true }).observedCurrentEpochOrNull(
                    Finance9AclScope(
                        req.principalHash, req.generationId, req.pointerSequence,
                        req.operationId, req.objectScope,
                    ), 1600L,
                ),
            )
            db.prepare(
                "SELECT policy_epoch FROM finance_acl_policy WHERE principal_hash=?"
            ).use { st ->
                st.bindText(1, req.principalHash)
                assertTrue(st.step())
                assertTrue(st.getLong(0) > prior.expectedPolicyEpoch)
            }
        }
    }

    @Test fun s04MalformedScopedRequestNeverCreatesSqlRows() {
        val file = staged("malformed")
        assertThrows(IllegalArgumentException::class.java) {
            request("malformed").copy(principalHash = "bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            request("malformed").copy(expiresAtMs = 100_000L)
        }
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "finance_acl_policy"))
    }
}
