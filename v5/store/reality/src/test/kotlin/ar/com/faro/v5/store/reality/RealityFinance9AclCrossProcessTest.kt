package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Real two distinct OS PIDs with one SQLite9 STAGED database:
 * Kotlin/RealityStore process A, Python sqlite3 process B.
 * The test is NOT human auth nor product Compose/Apply authority.
 */
class RealityFinance9AclCrossProcessTest {
    @TempDir lateinit var root: Path
    private val policy = Finance9AclScope(
        principalHash = "c".repeat(64),
        generationId = "staged-m1c0-isolated-1",
        pointerSequence = 7L,
        operationId = "finance.account.create",
        objectScope = "account-approved",
    )
    private var seq = 0
    private fun tx(): TransactionId = TransactionId("m1c0-tx-" + (++seq))

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stage(name: String, version: Int = 9): Path {
        val file = root.resolve(name + ".db")
        RealityStore(file.toString(), planningStage8 = version != 7).use {
            assertEquals(if (version == 7) 7 else 8, it.schemaVersion())
        }
        if (version == 9) {
            open(file).use {
                RealityFinance9Schema.installOnStagedEightConnection(
                    it, "m1c0-" + name, "b".repeat(64), 1000L,
                )
            }
        }
        return file
    }

    private fun issue(file: Path, key: Finance9AclScope = policy): Long =
        open(file).use {
            RealityFinance9Acl(it, { true })
                .issueAfterTrustedStageAdmission(key, 1000L, 9000L)
        }

    private fun observed(file: Path, key: Finance9AclScope = policy): Long? =
        open(file).use {
            RealityFinance9Acl(it, { true }).observedCurrentEpochOrNull(key, 1100L)
        }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use { st ->
            assertTrue(st.step())
            st.getLong(0)
        }
    }

    private fun account(): FinanceAccountCreate = FinanceAccountCreate(
        FinanceAccountId.of("account-approved"),
        FinanceAccountDisplayName.of("Cuenta confirmada"),
        FinanceCurrencyCode.of("ARS"),
        FinanceAccountNature.CASH,
    )

    private fun tryCreate(
        file: Path,
        key: Finance9AclScope,
        expectedEpoch: Long,
        atMs: Long = 1500L,
    ): Boolean = RealityStore(file.toString(), planningStage8 = true, financeStage9 = true)
        .use { store ->
            store.run(tx()) { scope ->
                if (!store.finance9AclCurrentInsideApplyTransaction(
                        scope, key, expectedEpoch, atMs,
                    )
                ) AtomicApplyTransactionDecision.Rollback(false)
                else {
                    store.financePersistence.createAccount(account(), scope)
                    AtomicApplyTransactionDecision.Commit(true)
                }
            }
        }

    @Test fun remoteRevocationCommittedBeforeBeginDeniesLedgerAndReceipt() {
        val file = stage("revoke-before-begin")
        val epoch = issue(file)
        RealityFinance9AclProcessWorker.start(root, file, "REVOKE", policy).use { child ->
            child.beginAttempt()
            assertTrue(child.finish().contains("DONE:" + child.pid))
        }
        assertFalse(tryCreate(file, policy, epoch))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertNull(observed(file))
    }

    @Test fun beginImmediateBlocksSecondOsPidRevocationUntilLedgerCommit() {
        val file = stage("begin-first")
        val epoch = issue(file)
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use { store ->
            RealityFinance9AclProcessWorker.start(root, file, "REVOKE", policy).use { child ->
                val committed = store.run(tx()) { scope ->
                    assertTrue(store.finance9AclCurrentInsideApplyTransaction(
                        scope, policy, epoch, 1300L,
                    ))
                    store.financePersistence.createAccount(account(), scope)
                    child.beginAttempt()
                    // Process B's BEGIN IMMEDIATE must wait while A owns write
                    // transaction; this is a bounded negative witness.
                    assertFalse(child.process.waitFor(300, TimeUnit.MILLISECONDS))
                    AtomicApplyTransactionDecision.Commit(true)
                }
                assertTrue(committed)
                assertTrue(child.finish().contains("DONE:" + child.pid))
            }
            assertEquals(1, store.financePersistence.read().accounts.size)
        }
        assertNull(observed(file)) // B revoked *after* A committed
        assertEquals(1L, count(file, "finance_account"))
    }

    @Test fun reviewObservedBeforeOtherOsRevokeDoesNotAuthorizeLaterApply() {
        val file = stage("review-then-revoke")
        val epoch = issue(file)
        assertEquals(epoch, observed(file)) // review read alone is not a grant
        RealityFinance9AclProcessWorker.start(root, file, "REVOKE", policy).use { child ->
            child.beginAttempt()
            child.finish()
        }
        assertFalse(tryCreate(file, policy, epoch))
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun realSecondOsProcessDeathBeforeSqliteCommitRollsBackFinance() {
        val file = stage("crash-second-process")
        issue(file)
        RealityFinance9AclProcessWorker.start(root, file, "CRASH", policy).use { child ->
            child.beginAttempt()
            child.finish(expectedExit = 77)
        }
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun realSqliteGrantAndReceiptBindOnceWithForeignKeysAndUniqueIntent() {
        val file = stage("single-use-receipt")
        val epoch = issue(file)
        val fp = "d".repeat(64)
        open(file).use { db ->
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                db.execSQL(
                    "INSERT INTO apply_attempt(transaction_id,plan_fingerprint,state) " +
                        "VALUES('tx-real1','" + fp + "','COMMITTED')"
                )
                db.execSQL(
                    "INSERT INTO execution_receipt(receipt_id,transaction_id,plan_fingerprint) " +
                        "VALUES('receipt-real1','tx-real1','" + fp + "')"
                )
                db.prepare(
                    """
                    INSERT INTO finance_apply_grant(
                        grant_id,principal_hash,generation_id,pointer_sequence,
                        operation_id,object_scope,policy_epoch,fingerprint,intent_key,
                        issued_at_ms,expires_at_ms,used_at_ms,receipt_id
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent()
                ).use { st ->
                    st.bindText(1, "grant-real1")
                    st.bindText(2, policy.principalHash)
                    st.bindText(3, policy.generationId)
                    st.bindLong(4, policy.pointerSequence)
                    st.bindText(5, policy.operationId)
                    st.bindText(6, policy.objectScope)
                    st.bindLong(7, epoch)
                    st.bindText(8, fp)
                    st.bindText(9, "intent-once")
                    st.bindLong(10, 1000L)
                    st.bindLong(11, 9000L)
                    st.bindLong(12, 1500L)
                    st.bindText(13, "receipt-real1")
                    st.step()
                }
                db.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { db.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
            assertEquals(1L, count(file, "finance_apply_grant"))
            db.execSQL(
                "UPDATE finance_apply_grant SET used_at_ms=NULL,receipt_id=NULL " +
                    "WHERE grant_id='grant-real1' AND used_at_ms IS NULL"
            )
            val changed = db.prepare("SELECT changes()").use {
                assertTrue(it.step())
                it.getLong(0)
            }
            assertEquals(0L, changed, "a previously consumed grant cannot update")
            db.prepare(
                "SELECT used_at_ms,receipt_id FROM finance_apply_grant WHERE grant_id='grant-real1'"
            ).use {
                assertTrue(it.step())
                assertEquals(1500L, it.getLong(0))
                assertEquals("receipt-real1", it.getText(1))
            }
            assertThrows(Exception::class.java) {
                db.execSQL(
                    "INSERT INTO execution_receipt(receipt_id,transaction_id,plan_fingerprint) " +
                        "VALUES('receipt-fraud2','tx-real1','" + fp + "')"
                )
            }
        }
        assertEquals(1L, count(file, "finance_apply_grant"))
        assertEquals(1L, count(file, "execution_receipt"))
    }

    @Test fun revokeRegrantEpochAndPointerAbaRejectOldPermission() {
        val file = stage("epoch-aba")
        val first = issue(file)
        open(file).use { db ->
            val acl = RealityFinance9Acl(db, { true })
            assertEquals(first + 1, acl.revoke(policy))
            assertEquals(first + 2, acl.issueAfterTrustedStageAdmission(policy, 1300L, 9000L))
        }
        assertFalse(tryCreate(file, policy, first))
        assertFalse(tryCreate(file, policy.copy(pointerSequence = 8L), first + 2))
        assertFalse(tryCreate(file, policy.copy(generationId = "foreign"), first + 2))
        assertEquals(0L, count(file, "finance_account"))
    }

    @Test fun legacySevenAndPlanningEightRejectFinance9ApplyPolicy() {
        val seven = stage("schema-seven", 7)
        val eight = stage("schema-eight", 8)
        for ((file, version) in listOf(seven to 7, eight to 8)) {
            RealityStore(file.toString(), planningStage8 = version == 8).use { store ->
                assertThrows(IllegalStateException::class.java) {
                    store.run(tx()) { scope ->
                        store.finance9AclCurrentInsideApplyTransaction(
                            scope, policy, 1L, 1000L,
                        )
                        AtomicApplyTransactionDecision.Commit(Unit)
                    }
                }
                assertEquals(0, store.financePersistence.read().accounts.size)
            }
        }
    }

    @Test fun foreignScopeAndExpiredEpochNeverGrantWithinCurrentSqliteTransaction() {
        val file = stage("foreign-expired")
        val epoch = issue(file)
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use { store ->
            val accepted = store.run(tx()) { scope ->
                assertFalse(store.finance9AclCurrentInsideApplyTransaction(
                    scope, policy.copy(principalHash = "f".repeat(64)), epoch, 1500L,
                ))
                assertFalse(store.finance9AclCurrentInsideApplyTransaction(
                    scope, policy.copy(operationId = "finance.entry.record"), epoch, 1500L,
                ))
                assertFalse(store.finance9AclCurrentInsideApplyTransaction(
                    scope, policy, epoch, 9000L,
                ))
                assertFalse(store.finance9AclCurrentInsideApplyTransaction(
                    scope, policy, 0L, 1500L,
                ))
                AtomicApplyTransactionDecision.Rollback(false)
            }
            assertFalse(accepted)
        }
        assertEquals(0L, count(file, "finance_account"))
        assertEquals(0L, count(file, "execution_receipt"))
    }
}
