package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityFinance9ActiveDevGrantPortTest {
    @TempDir lateinit var root: Path

    private val fingerprint = PlanFingerprint("a".repeat(64))
    private val account = FinanceAccountId.of("cash")
    private var admitted = true
    private var clock = 1_000L

    private fun database(seedPlanning: (RealityStore) -> Unit = {}): Path {
        val file = root.resolve("reality.db")
        RealityStore(file.toString(), planningStage8 = true).use(seedPlanning)
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(
                db, "stage-finance", "b".repeat(64), clock,
            )
        }
        return file
    }

    private fun active(file: Path) = RealityStore(
        file.toString(),
        materialWriteGate = SingleProcessMaterialWriteGate(),
        planningStage8 = true,
        financeActive9 = true,
    )

    private fun issuer(store: RealityStore) = store.finance9ActiveDevGrantIssuer(
        "c".repeat(64), "finance-dev", 7L, { admitted }, { clock },
    )

    private fun accountGrant(issuer: RealityFinance9ActiveDevGrantIssuer, opening: Long? = 0L) = issuer.issue(
        FinanceOperations.ACCOUNT_CREATE, account.value, fingerprint, IdempotencyKey("account-intent"), opening,
    )

    private fun prepare(file: Path, label: String, hash: PlanFingerprint = fingerprint): ApplyAttemptIdentity {
        val id = ApplyAttemptIdentity(TransactionId("tx-" + label), hash)
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.prepare(
                "INSERT INTO apply_attempt(transaction_id,plan_fingerprint,state) VALUES(?,?,'PREPARED')",
            ).use { statement ->
                statement.bindText(1, id.transactionId.value)
                statement.bindText(2, hash.value)
                statement.step()
            }
        }
        return id
    }

    private fun createAccount(
        store: RealityStore,
        scope: RealityTransactionScope,
        opening: Long = 0L,
        id: FinanceAccountId = account,
    ) {
        store.financePersistence.createAccount(FinanceAccountCreate(
            id, FinanceAccountDisplayName.of(id.value), FinanceCurrencyCode.of("ARS"),
            FinanceAccountNature.CASH, opening,
        ), scope)
    }

    private fun apply(
        store: RealityStore,
        id: ApplyAttemptIdentity,
        grant: RealityFinance9ActiveDevGrant,
        effect: (RealityTransactionScope) -> Unit,
    ): ExecutionReceipt? = store.run<ExecutionReceipt?>(id.transactionId) { scope ->
        effect(scope)
        val receipt = store.commit(scope, id, emptyList())
        if (grant.consumeInsideFinalCommit(scope, id, receipt)) AtomicApplyTransactionDecision.Commit(receipt)
        else AtomicApplyTransactionDecision.Rollback(null)
    }

    @Test fun deniesLegacyAndStageStores() {
        val seven = root.resolve("legacy.db")
        RealityStore(seven.toString()).use { store ->
            assertThrows(IllegalStateException::class.java) { issuer(store) }
        }
        val eight = root.resolve("planning.db")
        RealityStore(eight.toString(), planningStage8 = true).use { store ->
            assertThrows(IllegalStateException::class.java) { issuer(store) }
        }
        RealityStore(database().toString(), planningStage8 = true, financeStage9 = true).use { store ->
            assertThrows(IllegalStateException::class.java) { issuer(store) }
            assertThrows(IllegalStateException::class.java) {
                store.finance9ActiveDevOpeningProvenanceRead { true }
            }
        }
    }

    @Test fun commitsAccountAndEntryAsSeparateDurableReceipts() {
        val legacy = FinanceAccountId.of("legacy")
        val file = database { store ->
            store.run(TransactionId("seed-before-migration")) { scope ->
                createAccount(store, scope, opening = 70L, id = legacy)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        val receipts = active(file).use { store ->
            val issuer = issuer(store)
            val accountId = prepare(file, "account")
            val create = requireNotNull(accountGrant(issuer))
            requireNotNull(apply(store, accountId, create) { createAccount(store, it) })

            val entryId = prepare(file, "entry", PlanFingerprint("d".repeat(64)))
            val record = requireNotNull(issuer.issue(
                FinanceOperations.ENTRY_RECORD, "income", entryId.planFingerprint, IdempotencyKey("entry-intent"), null,
            ))
            requireNotNull(apply(store, entryId, record) { scope ->
                store.financePersistence.recordEntry(FinanceEntryRecord(
                    FinanceEntryId.of("income"), account, 340L, FinanceEntryKind.INCOME,
                    FinanceEntryConcept.of("Ingreso local"), FinanceEntryCategory.of("Otros"), clock, clock,
                ), scope)
            })
            listOf(accountId.transactionId, entryId.transactionId).map { transaction ->
                requireNotNull(store.executionReceiptLookup.find(transaction))
            }
        }

        active(file).use { store ->
            assertEquals(340L, store.financePersistence.read().ledgerMinor(account))
            receipts.forEach { assertEquals(it, store.executionReceiptLookup.find(it.transactionId)) }
            val observed = requireNotNull(store.finance9ActiveDevOpeningProvenanceRead { admitted }.read())
            val zero = observed.accounts.single { it.accountId == account.value }
            assertEquals(RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL, zero.state)
            assertEquals(0L, zero.confirmedOpeningMinor)
            assertEquals(340L, zero.ledgerBalanceMinor)
            val inherited = observed.accounts.single { it.accountId == legacy.value }
            assertEquals(RealityFinance9OpeningProvenanceState.LEGACY_UNVERIFIED, inherited.state)
            assertNull(inherited.confirmedOpeningMinor)
            assertNull(inherited.ledgerBalanceMinor)
        }
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.prepare("SELECT receipt_id FROM finance_apply_grant WHERE used_at_ms IS NOT NULL").use { st ->
                val linked = buildSet { while (st.step()) add(st.getText(0)) }
                assertEquals(receipts.map { it.receiptId.value }.toSet(), linked)
            }
        }
    }

    @Test fun duplicateIntentDoesNotInvalidateTheOriginalGrant() {
        val file = database()
        active(file).use { store ->
            val issuer = issuer(store)
            val original = requireNotNull(accountGrant(issuer))
            assertNull(accountGrant(issuer))
            val id = prepare(file, "original")
            val receipt = store.run(id.transactionId) { scope ->
                createAccount(store, scope)
                val issuedReceipt = store.commit(scope, id, emptyList())
                assertTrue(original.consumeInsideFinalCommit(scope, id, issuedReceipt))
                assertFalse(original.consumeInsideFinalCommit(scope, id, issuedReceipt))
                AtomicApplyTransactionDecision.Commit(issuedReceipt)
            }
            assertEquals(receipt, store.executionReceiptLookup.find(id.transactionId))
            assertEquals(listOf(account), store.financePersistence.read().accounts.map { it.accountId })
        }
    }

    @Test fun deniesUnsupportedOperationsAndLostForeground() {
        val file = database()
        active(file).use { store ->
            val issuer = issuer(store)
            assertNull(issuer.issue(
                OperationId("finance.transfer.record"), "another", fingerprint, IdempotencyKey("transfer-intent"), null,
            ))
            assertNull(accountGrant(issuer, opening = null))
            assertNull(issuer.issue(
                FinanceOperations.ENTRY_RECORD, "income", fingerprint, IdempotencyKey("entry-intent"), 0L,
            ))
            val reader = store.finance9ActiveDevOpeningProvenanceRead { admitted }
            val grant = requireNotNull(accountGrant(issuer))
            val id = prepare(file, "lost-foreground")
            assertNull(apply(store, id, grant) {
                createAccount(store, it)
                admitted = false
            })
            assertNull(accountGrant(issuer))
            assertNull(reader.read())
            assertNull(store.executionReceiptLookup.find(id.transactionId))
            assertTrue(store.financePersistence.read().accounts.isEmpty())
        }
    }

    @Test fun mismatchedOpeningRollsBackAccountGrantAndReceipt() {
        val file = database()
        active(file).use { store ->
            val grant = requireNotNull(accountGrant(issuer(store), opening = 50L))
            val id = prepare(file, "opening-mismatch")
            assertNull(apply(store, id, grant) { createAccount(store, it, opening = 49L) })
            assertNull(store.executionReceiptLookup.find(id.transactionId))
            assertTrue(store.financePersistence.read().accounts.isEmpty())

            val receipt = requireNotNull(apply(store, id, grant) { createAccount(store, it, opening = 50L) })
            assertEquals(receipt, store.executionReceiptLookup.find(id.transactionId))
            val reader = store.finance9ActiveDevOpeningProvenanceRead { admitted }
            val confirmed = requireNotNull(reader.read()).accounts.single()
            assertEquals(50L, confirmed.confirmedOpeningMinor)
            assertEquals(50L, confirmed.ledgerBalanceMinor)
        }
    }

    @Test fun deniesWrongFingerprintAndExpiredGrantWithoutPublishingEffects() {
        val file = database()
        active(file).use { store ->
            val grant = requireNotNull(accountGrant(issuer(store)))
            val wrong = prepare(file, "wrong", PlanFingerprint("f".repeat(64)))
            assertNull(apply(store, wrong, grant) { createAccount(store, it) })
            assertNull(store.executionReceiptLookup.find(wrong.transactionId))

            clock += 30_000L
            val expired = prepare(file, "expired")
            assertNull(apply(store, expired, grant) { createAccount(store, it) })
            assertNull(store.executionReceiptLookup.find(expired.transactionId))
            assertTrue(store.financePersistence.read().accounts.isEmpty())
        }
    }
}
