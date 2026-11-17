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
 * M1C1B0 real RealityStore receipt + SQLite9 grant under SAME transaction.
 * STAGED JVM test principal is synthetic: NO Android Keystore, human identity,
 * real UI/Compose Apply host or productive user permission is certified.
 */
class RealityFinance9GrantReceiptStageTest {
    @TempDir lateinit var root: Path
    private val fp = "d".repeat(64)
    private val scope = Finance9AclScope(
        principalHash = "c".repeat(64),
        generationId = "m1c1b0-isolated-stage9",
        pointerSequence = 7L,
        operationId = "finance.account.create",
        objectScope = "account-approved",
    )

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stage(label: String, version: Int = 9): Path {
        val file = root.resolve(label + ".db")
        RealityStore(file.toString(), planningStage8 = version != 7).use {
            assertEquals(if (version == 7) 7 else 8, it.schemaVersion())
        }
        if (version == 9) open(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(
                it, "m1c1b0-" + label, "a".repeat(64), 1000L,
            )
        }
        return file
    }

    private fun <T> read(file: Path, sql: String, get: (SQLiteConnection) -> T): T =
        open(file).use { db -> get(db) }

    private fun count(file: Path, table: String): Long =
        open(file).use { db ->
            db.prepare("SELECT count(*) FROM " + table).use {
                assertTrue(it.step())
                it.getLong(0)
            }
        }

    private fun policy(file: Path): Long = open(file).use { db ->
        RealityFinance9Acl(db, { true }).issueAfterTrustedStageAdmission(
            scope, 1000L, 9000L,
        )
    }

    private fun issue(
        file: Path,
        epoch: Long,
        grantId: String = "grant-1",
        intentKey: String = "review-intent-1",
        expires: Long = 8000L,
        fingerprint: String = fp,
        key: Finance9AclScope = scope,
    ): Boolean = open(file).use { db ->
        RealityFinance9Acl(db, { true }).issueGrantAfterTrustedStageAdmission(
            key, epoch, grantId, fingerprint, intentKey, 1000L, expires,
        )
    }

    private fun identity(tag: String = "first", fingerprint: String = fp) =
        ApplyAttemptIdentity(TransactionId("tx-" + tag), PlanFingerprint(fingerprint))

    private fun prepare(file: Path, attempt: ApplyAttemptIdentity) {
        open(file).use { db ->
            db.prepare(
                "INSERT INTO apply_attempt(transaction_id,plan_fingerprint,state) " +
                    "VALUES(?,?,'PREPARED')"
            ).use { st ->
                st.bindText(1, attempt.transactionId.value)
                st.bindText(2, attempt.planFingerprint.value)
                st.step()
            }
        }
    }

    private fun account() = FinanceAccountCreate(
        FinanceAccountId.of("account-approved"),
        FinanceAccountDisplayName.of("Efectivo"),
        FinanceCurrencyCode.of("ARS"),
        FinanceAccountNature.CASH,
        0L,
    )

    private fun apply(
        file: Path,
        epoch: Long,
        id: ApplyAttemptIdentity = identity(),
        key: Finance9AclScope = scope,
        grantId: String = "grant-1",
        at: Long = 1500L,
        afterGrantBeforeCommit: () -> Unit = {},
    ): ExecutionReceipt? =
        RealityStore(
            file.toString(),
            planningStage8 = true,
            financeStage9 = true,
            receiptIdFactory = { ReceiptId("receipt-" + id.transactionId.value) },
        ).use { store ->
            store.run<ExecutionReceipt?>(id.transactionId) { txScope ->
                store.financePersistence.createAccount(account(), txScope)
                val receipt = store.commit(txScope, id, emptyList())
                val admitted = store.finance9ConsumeGrantAgainstReceiptInsideApplyTransaction(
                    txScope, key, grantId, epoch, id, receipt, at,
                )
                if (!admitted) AtomicApplyTransactionDecision.Rollback(null)
                else {
                    afterGrantBeforeCommit()
                    AtomicApplyTransactionDecision.Commit(receipt)
                }
            }
        }

    private fun grantState(file: Path, grantId: String = "grant-1"): Pair<Long?, String?> =
        open(file).use { db ->
            db.prepare(
                "SELECT used_at_ms,receipt_id FROM finance_apply_grant WHERE grant_id=?"
            ).use { st ->
                st.bindText(1, grantId)
                assertTrue(st.step())
                val state = (if (st.isNull(0)) null else st.getLong(0)) to
                    (if (st.isNull(1)) null else st.getText(1))
                assertFalse(st.step())
                state
            }
        }

    @Test fun r01PolicyBoundGrantIssuedAndDurablyUnusedAcrossReopen() {
        val file = stage("issue")
        val epoch = policy(file)
        assertEquals(1L, epoch)
        assertTrue(issue(file, epoch))
        assertEquals(1L, count(file, "finance_apply_grant"))
        assertEquals(null to null, grantState(file))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun r02RealRealityCommitAndFinanceEffectAtomicallyConsumeGrantWithReceipt() {
        val file = stage("commit")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        val id = identity()
        prepare(file, id)
        val receipt = requireNotNull(apply(file, epoch, id))
        assertEquals(id.transactionId, receipt.transactionId)
        assertEquals(id.planFingerprint, receipt.planFingerprint)
        assertEquals(1L, count(file, "finance_account"))
        assertEquals(1L, count(file, "execution_receipt"))
        assertEquals(1500L to receipt.receiptId.value, grantState(file))
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use { store ->
            assertEquals(receipt, store.executionReceiptLookup.find(id.transactionId))
            assertEquals(
                ApplyAttemptJournalState.COMMITTED,
                store.applyAttemptLookup.find(id.transactionId)?.state,
            )
        }
    }

    @Test fun r03RevocationBeforeApplyForcesRollbackOfFinanceAndRealReceipt() {
        val file = stage("revoke")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        val id = identity()
        prepare(file, id)
        open(file).use { db ->
            assertEquals(epoch + 1L, RealityFinance9Acl(db, { true }).revoke(scope))
        }
        assertNull(apply(file, epoch, id))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(null to null, grantState(file))
    }

    @Test fun r04ForeignScopeFingerprintAndGrantIdDenyWithoutReceipt() {
        val file = stage("foreign")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        val id = identity()
        prepare(file, id)
        assertNull(apply(file, epoch, id, grantId = "unissued-grant"))
        assertNull(apply(file, epoch, id, key = scope.copy(pointerSequence = 99L)))
        assertNull(apply(file, epoch, id, key = scope.copy(objectScope = "another")))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(null to null, grantState(file))
        val other = stage("other-fingerprint")
        val policyEpoch = policy(other)
        assertTrue(issue(other, policyEpoch))
        val mismatched = identity("foreign-fingerprint", "e".repeat(64))
        prepare(other, mismatched)
        assertNull(apply(other, policyEpoch, mismatched))
        assertEquals(0L, count(other, "execution_receipt"))
    }

    @Test fun r05ExpiredGrantOrPolicyDenyAndLeaveGrantUnused() {
        val file = stage("expiry")
        val epoch = policy(file)
        assertTrue(issue(file, epoch, expires = 2000L))
        val id = identity()
        prepare(file, id)
        assertNull(apply(file, epoch, id, at = 2000L))
        assertNull(apply(file, epoch, id, at = 9000L))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(null to null, grantState(file))
    }

    @Test fun r06ConsumedGrantAndOriginalAttemptCannotBeReplayed() {
        val file = stage("replay")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        val id = identity()
        prepare(file, id)
        val receipt = requireNotNull(apply(file, epoch, id))
        assertEquals(1500L to receipt.receiptId.value, grantState(file))
        open(file).use { db ->
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                db.execSQL(
                    "UPDATE finance_apply_grant SET used_at_ms=NULL,receipt_id=NULL " +
                        "WHERE grant_id='grant-1' AND used_at_ms IS NULL"
                )
                val changed = db.prepare("SELECT changes()").use {
                    assertTrue(it.step())
                    it.getLong(0)
                }
                assertEquals(0L, changed)
                db.execSQL("ROLLBACK TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { db.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        }
        assertThrows(Exception::class.java) { prepare(file, id) }
        assertEquals(1L, count(file, "execution_receipt"))
        assertEquals(1L, count(file, "finance_account"))
    }

    @Test fun r07FailureAfterGrantLinkBeforeSqlCommitRollsBackAllAndMayRetry() {
        val file = stage("rollback")
        val epoch = policy(file)
        assertTrue(issue(file, epoch))
        val id = identity()
        prepare(file, id)
        assertThrows(IllegalStateException::class.java) {
            apply(file, epoch, id, afterGrantBeforeCommit = { error("crash before SQL COMMIT") })
        }
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(null to null, grantState(file))
        val receipt = requireNotNull(apply(file, epoch, id))
        assertEquals(1L, count(file, "execution_receipt"))
        assertEquals(1500L to receipt.receiptId.value, grantState(file))
    }

    @Test fun r08LegacyStageDeniedAndUniqueIntentOrAdmissionCannotBypass() {
        val seven = stage("seven", version = 7)
        val eight = stage("eight", version = 8)
        for (file in listOf(seven, eight)) {
            open(file).use { db ->
                assertThrows(Exception::class.java) {
                    RealityFinance9Acl(db, { true }).issueGrantAfterTrustedStageAdmission(
                        scope, 1L, "forged", fp, "forged-intent", 1000L, 2000L,
                    )
                }
            }
        }
        val stage9 = stage("guarded")
        val epoch = policy(stage9)
        open(stage9).use { db ->
            assertThrows(IllegalStateException::class.java) {
                RealityFinance9Acl(db, { false }).issueGrantAfterTrustedStageAdmission(
                    scope, epoch, "forged", fp, "intent-forged", 1000L, 2000L,
                )
            }
        }
        assertTrue(issue(stage9, epoch))
        assertThrows(Exception::class.java) {
            issue(stage9, epoch, grantId = "second", intentKey = "review-intent-1")
        }
        assertEquals(1L, count(stage9, "finance_apply_grant"))
        assertEquals(null to null, grantState(stage9))
    }
}
