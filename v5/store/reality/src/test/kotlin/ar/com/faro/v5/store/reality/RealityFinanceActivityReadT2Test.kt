package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealityFinanceActivityReadT2Test {
    private val accountA = FinanceAccountId.of("account-a")
    private val accountB = FinanceAccountId.of("account-b")

    @Test
    fun realLedgerReadOrdersByRecordedTimeThenIdWithStableCursorAndFilters() {
        val db = Files.createTempDirectory("faro-v5-finance-activity").resolve("reality.db")
        RealityStore(db.toString()).use { store ->
            seed(store)
            val first = store.financeActivity.query(
                RealityFinanceActivityQuery(accountId = "account-a", limit = 2)
            )
            assertEquals(listOf("entry-c", "entry-a"), first.entries.map { it.entryId.value })
            assertEquals(RealityFinanceActivityCursor(2000L, "entry-a"), first.nextCursor)
            assertEquals("reality.finance_entry", first.canonicalSource)
            assertEquals("finance.recorded_at_epoch_millis", first.timeProvenance)
            assertFalse(first.isGlobalHistoryComplete)
            assertFalse(first.certifiesApplyCommit)
            val second = store.financeActivity.query(
                RealityFinanceActivityQuery(accountId = "account-a", after = first.nextCursor)
            )
            assertEquals(listOf("entry-b"), second.entries.map { it.entryId.value })
            assertNull(second.nextCursor)
            val income = store.financeActivity.query(
                RealityFinanceActivityQuery(kind = FinanceEntryKind.INCOME)
            )
            assertEquals(
                listOf("entry-d", "entry-c", "entry-a"),
                income.entries.map { it.entryId.value },
            )
            assertTrue(first.financeRevision > 0)
        }
        RealityStore(db.toString()).use { reopened ->
            assertEquals(
                listOf("entry-d", "entry-c", "entry-a", "entry-b"),
                reopened.financeActivity.query(RealityFinanceActivityQuery())
                    .entries.map { it.entryId.value },
            )
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun rolledBackOrInFlightEntryNeverEntersAuthorizedFinanceActivityRead() {
        RealityStore(":memory:").use { store ->
            store.run(TransactionId("tx-account")) { scope ->
                store.financePersistence.createAccount(account(accountA), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("tx-uncommitted")) { scope ->
                    store.financePersistence.recordEntry(entry("entry-x", accountA, 500L), scope)
                    // The current transaction can still roll back.
                    store.financeActivity.query(RealityFinanceActivityQuery())
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertTrue(store.financeActivity.query(RealityFinanceActivityQuery()).entries.isEmpty())
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun invalidBoundsAndClosedOwnerFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            RealityFinanceActivityQuery(limit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RealityFinanceActivityQuery(accountId = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RealityFinanceActivityCursor(1, "")
        }
        val store = RealityStore(":memory:")
        store.close()
        assertThrows(IllegalStateException::class.java) {
            store.financeActivity.query(RealityFinanceActivityQuery())
        }
    }

    private fun seed(store: RealityStore) {
        store.run(TransactionId("tx-create-accounts")) { scope ->
            store.financePersistence.createAccount(account(accountA), scope)
            store.financePersistence.createAccount(account(accountB), scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
        listOf(
            Triple("entry-a", accountA, 2000L),
            Triple("entry-b", accountA, 2000L),
            Triple("entry-c", accountA, 3000L),
            Triple("entry-d", accountB, 4000L),
        ).forEach { (id, account, time) ->
            store.run(TransactionId("tx-" + id)) { scope ->
                store.financePersistence.recordEntry(
                    entry(id, account, time, kind =
                        if (id == "entry-b") FinanceEntryKind.EXPENSE else FinanceEntryKind.INCOME),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
    }

    private fun account(id: FinanceAccountId) = FinanceAccountCreate(
        accountId = id,
        displayName = FinanceAccountDisplayName.of(id.value),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = 0L,
    )

    private fun entry(
        id: String, account: FinanceAccountId, recordedAt: Long,
        kind: FinanceEntryKind = FinanceEntryKind.INCOME,
    ) = FinanceEntryRecord(
        entryId = FinanceEntryId.of(id),
        accountId = account,
        amountMinor = 500L,
        kind = kind,
        concept = FinanceEntryConcept.of("Movimiento " + id),
        category = FinanceEntryCategory.of("Varios"),
        effectiveAtEpochMillis = 100L,
        recordedAtEpochMillis = recordedAt,
    )
}
