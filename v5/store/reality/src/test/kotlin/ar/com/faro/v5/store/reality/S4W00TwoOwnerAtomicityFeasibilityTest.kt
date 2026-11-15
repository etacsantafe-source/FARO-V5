package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.objectives.api.ObjectiveDetail
import ar.com.faro.v5.owner.objectives.api.ObjectiveId
import ar.com.faro.v5.owner.objectives.api.ObjectiveRecord
import ar.com.faro.v5.owner.objectives.api.ObjectiveStatus
import ar.com.faro.v5.owner.objectives.api.ObjectiveTitle
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * W00-A ONLY: feasibility of two existing owners in one real SQLite transaction.
 * No multi-binding Apply authorization, receipt or Household payment is certified.
 */
class S4W00TwoOwnerAtomicityFeasibilityTest {
    @Test
    fun twoExistingOwnersCommitInOneSqliteScopeAndSurviveReopen() {
        val db = Files.createTempDirectory("faro-s4-w00-two-owner").resolve("reality.db")
        val tx = TransactionId("s4-w00-both-commit")
        RealityStore(db.toString()).use { store ->
            store.run(tx) { scope ->
                store.financePersistence.createAccount(account("durable"), scope)
                store.objectivePersistence.upsert(objective(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals("ok", store.integrityCheck())
            // A raw storage transaction cannot be mistaken for an Apply receipt.
            assertNull(store.executionReceiptLookup.find(tx))
        }
        RealityStore(db.toString()).use { reopened ->
            assertEquals("durable", reopened.financePersistence.read().accounts.single().accountId.value)
            assertEquals(25_000L, reopened.financePersistence.read().accounts.single().openingBalanceMinor)
            assertEquals(objective(), reopened.objectivePersistence.read().objectives.single())
            assertEquals("ok", reopened.integrityCheck())
            assertNull(reopened.executionReceiptLookup.find(tx))
        }
    }

    @Test
    fun exceptionAfterFinanceBeforeObjectivesRollsBackFirstOwner() {
        RealityStore(":memory:").use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("s4-w00-fail-first")) { scope ->
                    store.financePersistence.createAccount(account("failed-first"), scope)
                    error("injected failure before Objectives")
                }
            }
            assertEquals(0L, store.financePersistence.read().revision)
            assertEquals(emptyList<FinanceAccountId>(), store.financePersistence.read().accounts.map { it.accountId })
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun explicitRollbackAfterBothOwnersRevertsBoth() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("s4-w00-explicit-rollback")) { scope ->
                store.financePersistence.createAccount(account("rollback"), scope)
                store.objectivePersistence.upsert(objective(), scope)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(0L, store.financePersistence.read().revision)
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun staleSecondOwnerRejectsAndRollsBackNewFinanceWithoutDeletingOldObjective() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("s4-w00-existing-objective")) { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.run(TransactionId("s4-w00-stale-second")) { scope ->
                    store.financePersistence.createAccount(account("stale"), scope)
                    store.objectivePersistence.upsert(
                        objective(at = 99L, title = "stale update"), scope
                    )
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertEquals(0L, store.financePersistence.read().revision)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals(objective(), store.objectivePersistence.read().objectives.single())
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun forbiddenNestedTransactionAfterFinanceRollsBackOuterScope() {
        RealityStore(":memory:").use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("s4-w00-outer")) { outerScope ->
                    store.financePersistence.createAccount(account("nested"), outerScope)
                    store.run(TransactionId("s4-w00-forbidden-inner")) { innerScope ->
                        store.objectivePersistence.upsert(objective(), innerScope)
                        AtomicApplyTransactionDecision.Commit(Unit)
                    }
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertEquals(0L, store.financePersistence.read().revision)
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertEquals("ok", store.integrityCheck())
        }
    }

    private fun account(id: String) = FinanceAccountCreate(
        accountId = FinanceAccountId.of(id),
        displayName = FinanceAccountDisplayName.of(id),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = 25_000L,
    )

    private fun objective(at: Long = 100L, title: String = "Fondo") = ObjectiveRecord(
        id = ObjectiveId.of("s4-w00-objective"),
        title = ObjectiveTitle.of(title),
        detail = ObjectiveDetail.of("Local only"),
        status = ObjectiveStatus.ACTIVE,
        updatedAtEpochMillis = at,
    )
}
