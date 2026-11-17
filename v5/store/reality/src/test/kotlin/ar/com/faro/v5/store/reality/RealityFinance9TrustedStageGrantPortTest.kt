package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * DEV/JVM only: proves the NEW cross-module port delegates to the same REAL
 * SQL grant/receipt transaction; no Android, BiometricPrompt or human principal.
 */
class RealityFinance9TrustedStageGrantPortTest {
    @TempDir lateinit var root: Path

    private val fp = "d".repeat(64)
    private val grant = Finance9TrustedStageGrant(
        principalHash = "c".repeat(64),
        generationId = "stage-g9",
        pointerSequence = 7L,
        operationId = "finance.account.create",
        objectScope = "account-approved",
        grantId = "stage-grant",
        expectedPolicyEpoch = 1L,
    )

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun file(label: String, version: Int = 9): Path {
        val out = root.resolve(label + ".db")
        RealityStore(out.toString(), planningStage8 = version != 7).use { }
        if (version == 9) open(out).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "stage-" + label, "a".repeat(64), 1000L,
            )
        }
        return out
    }

    private fun policy(file: Path): Long = open(file).use { db ->
        RealityFinance9Acl(db, { true }).issueAfterTrustedStageAdmission(
            Finance9AclScope(
                grant.principalHash, grant.generationId, grant.pointerSequence,
                grant.operationId, grant.objectScope,
            ), 1000L, 9000L,
        )
    }

    private fun issue(file: Path, epoch: Long) = open(file).use { db ->
        RealityFinance9Acl(db, { true }).issueGrantAfterTrustedStageAdmission(
            Finance9AclScope(
                grant.principalHash, grant.generationId, grant.pointerSequence,
                grant.operationId, grant.objectScope,
            ), epoch, grant.grantId, fp, "intent-stage-1", 1000L, 8000L,
        )
    }

    private fun identity(label: String) =
        ApplyAttemptIdentity(TransactionId("tx-" + label), PlanFingerprint(fp))

    private fun prepare(file: Path, id: ApplyAttemptIdentity) {
        open(file).use { db ->
            db.prepare(
                "INSERT INTO apply_attempt(transaction_id,plan_fingerprint,state) " +
                    "VALUES(?,?,'PREPARED')"
            ).use { st ->
                st.bindText(1, id.transactionId.value)
                st.bindText(2, id.planFingerprint.value)
                st.step()
            }
        }
    }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use {
            assertTrue(it.step())
            it.getLong(0)
        }
    }

    private fun apply(
        file: Path, label: String,
        presented: Finance9TrustedStageGrant = grant,
        whenMs: Long = 1500L,
    ): ExecutionReceipt? {
        val id = identity(label)
        prepare(file, id)
        return RealityStore(
            file.toString(), planningStage8 = true, financeStage9 = true,
            receiptIdFactory = { ReceiptId("receipt-" + label) },
        ).use { store ->
            val port = store.finance9TrustedStageGrantPortForDevOnly()
            store.run<ExecutionReceipt?>(id.transactionId) { scope ->
                store.financePersistence.createAccount(
                    FinanceAccountCreate(
                        FinanceAccountId.of("account-approved"),
                        FinanceAccountDisplayName.of("Efectivo"),
                        FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 0L,
                    ), scope,
                )
                val receipt = store.commit(scope, id, emptyList())
                if (port.consumeInsideFinalCommit(scope, presented, id, receipt, whenMs))
                    AtomicApplyTransactionDecision.Commit(receipt)
                else AtomicApplyTransactionDecision.Rollback(null)
            }
        }
    }

    @Test fun g01FactoryRejectsNormalSevenAndPlanningEight() {
        for (version in listOf(7, 8)) {
            val file = file("wrong-" + version, version)
            RealityStore(file.toString(), planningStage8 = version == 8).use { store ->
                assertThrows(IllegalStateException::class.java) {
                    store.finance9TrustedStageGrantPortForDevOnly()
                }
            }
        }
        val nine = file("allowed")
        RealityStore(nine.toString(), planningStage8 = true, financeStage9 = true).use {
            assertNotNull(it.finance9TrustedStageGrantPortForDevOnly())
        }
    }

    @Test fun g02StagePortConsumesRealSqlReceiptAndFinanceEffectAtomically() {
        val file = file("success")
        val epoch = policy(file)
        assertEquals(1L, epoch)
        assertTrue(issue(file, epoch))
        val receipt = requireNotNull(apply(file, "success"))
        assertEquals("receipt-success", receipt.receiptId.value)
        assertEquals(1L, count(file, "execution_receipt"))
        assertEquals(1L, count(file, "finance_account"))
        open(file).use { db ->
            db.prepare(
                "SELECT receipt_id,used_at_ms FROM finance_apply_grant WHERE grant_id=?"
            ).use { st ->
                st.bindText(1, grant.grantId)
                assertTrue(st.step())
                assertEquals(receipt.receiptId.value, st.getText(0))
                assertEquals(1500L, st.getLong(1))
            }
        }
    }

    @Test fun g03RevocationBeforeFinalGateRollsBackFinanceAndReceipt() {
        val file = file("revoke")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        open(file).use { db ->
            RealityFinance9Acl(db, { true }).revoke(
                Finance9AclScope(
                    grant.principalHash, grant.generationId, grant.pointerSequence,
                    grant.operationId, grant.objectScope,
                ),
            )
        }
        assertNull(apply(file, "revoke"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(0L, count(file, "finance_account"))
    }

    @Test fun g04ForeignGenerationOrScopeRollsBackAll() {
        val file = file("wrong-grant")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        assertNull(apply(file, "wrong-grant", presented = grant.copy(pointerSequence = 8L)))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(0L, count(file, "finance_account"))
    }

    @Test fun g05ExpiredGrantCannotPublishReceipt() {
        val file = file("expired")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        assertNull(apply(file, "expired", whenMs = 8000L))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(0L, count(file, "finance_account"))
    }
}
