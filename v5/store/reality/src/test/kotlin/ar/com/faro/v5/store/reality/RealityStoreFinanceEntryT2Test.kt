package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreFinanceEntryT2Test {
    @Test
    fun incomeAndExpensePersistAcrossReopenAndDeriveLedgerFromAccountCurrency() {
        val db = Files.createTempDirectory("faro-v5-finance-entry").resolve("reality.db")
        val accountId = FinanceAccountId.of("account-primary")

        RealityStore(db.toString()).use { store ->
            store.run(TransactionId("tx-account")) { scope ->
                store.financePersistence.createAccount(
                    accountCreate(accountId, openingBalanceMinor = 100_000L),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            store.run(TransactionId("tx-income")) { scope ->
                store.financePersistence.recordEntry(
                    entryRecord(
                        id = "entry-income",
                        accountId = accountId,
                        amountMinor = 50_000L,
                        kind = FinanceEntryKind.INCOME,
                    ),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            store.run(TransactionId("tx-expense")) { scope ->
                store.financePersistence.recordEntry(
                    entryRecord(
                        id = "entry-expense",
                        accountId = accountId,
                        amountMinor = 20_000L,
                        kind = FinanceEntryKind.EXPENSE,
                    ),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            val snapshot = store.financePersistence.read()
            assertEquals(3L, snapshot.revision)
            assertEquals(2, snapshot.entries.size)
            assertEquals(130_000L, snapshot.ledgerMinor(accountId))
            assertEquals(
                setOf("ARS"),
                snapshot.entries.map { it.currency.value }.toSet(),
            )
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("ok", store.integrityCheck())
        }

        RealityStore(db.toString()).use { reopened ->
            val snapshot = reopened.financePersistence.read()
            assertEquals(3L, snapshot.revision)
            assertEquals(2, snapshot.entries.size)
            assertEquals(130_000L, snapshot.ledgerMinor(accountId))
            val expense = snapshot.entries.single {
                it.entryId == FinanceEntryId.of("entry-expense")
            }
            assertEquals("Supermercado", expense.concept.value)
            assertEquals("Alimentos", expense.category.value)
            assertEquals("Declarado por usuario", expense.provenanceLabel)
            assertEquals(1_000L, expense.effectiveAtEpochMillis)
            assertEquals(2_000L, expense.recordedAtEpochMillis)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun thrownFailureRollsBackEntryAndFinanceRevision() {
        RealityStore(":memory:").use { store ->
            val accountId = FinanceAccountId.of("account-primary")
            store.run(TransactionId("tx-account")) { scope ->
                store.financePersistence.createAccount(
                    accountCreate(accountId, openingBalanceMinor = 10_000L),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("tx-entry-fail")) { scope ->
                    store.financePersistence.recordEntry(
                        entryRecord(
                            id = "entry-fail",
                            accountId = accountId,
                            amountMinor = 5_000L,
                            kind = FinanceEntryKind.EXPENSE,
                        ),
                        scope,
                    )
                    error("fail after Finance entry mutation")
                }
            }

            val snapshot = store.financePersistence.read()
            assertEquals(1L, snapshot.revision)
            assertEquals(0, snapshot.entries.size)
            assertEquals(10_000L, snapshot.ledgerMinor(accountId))
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun entryWriteOutsideRealityTransactionIsRejected() {
        RealityStore(":memory:").use { store ->
            val accountId = FinanceAccountId.of("account-primary")
            store.run(TransactionId("tx-account")) { scope ->
                store.financePersistence.createAccount(accountCreate(accountId), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val fakeScope = object : RealityTransactionScope {
                override val transactionId = TransactionId("fake")
            }

            assertThrows(IllegalStateException::class.java) {
                store.financePersistence.recordEntry(
                    entryRecord(
                        id = "entry-outside",
                        accountId = accountId,
                        amountMinor = 1L,
                        kind = FinanceEntryKind.INCOME,
                    ),
                    fakeScope,
                )
            }

            assertEquals(0, store.financePersistence.read().entries.size)
            assertEquals(1L, store.financePersistence.read().revision)
        }
    }

    @Test
    fun perAccountDependencyStampRemainsStableAcrossIndependentEntries() {
        RealityStore(":memory:").use { store ->
            val accountId = FinanceAccountId.of("account-primary")
            store.run(TransactionId("tx-account")) { scope ->
                store.financePersistence.createAccount(accountCreate(accountId), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            val key = FinanceSnapshot.accountDependencyKey(accountId)
            val before = store.current(FinanceSnapshot.FINANCE_AUTHORITY, key)

            store.run(TransactionId("tx-entry")) { scope ->
                store.financePersistence.recordEntry(
                    entryRecord(
                        id = "entry-independent",
                        accountId = accountId,
                        amountMinor = 1_000L,
                        kind = FinanceEntryKind.INCOME,
                    ),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            assertEquals(DependencyStampValue("present:currency=ARS"), before)
            assertEquals(before, store.current(FinanceSnapshot.FINANCE_AUTHORITY, key))
        }
    }

    @Test
    fun schemaV4MigratesToV5PreservingFinanceAccountAndOpeningCause() {
        val db = Files.createTempDirectory("faro-v5-finance-entry-migrate").resolve("reality.db")
        val accountId = FinanceAccountId.of("account-primary")

        RealityStore(db.toString()).use { store ->
            store.run(TransactionId("tx-seed-v4")) { scope ->
                store.financePersistence.createAccount(
                    accountCreate(accountId, openingBalanceMinor = 77_000L),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }

        BundledSQLiteDriver().open(db.toString()).use { connection ->
            connection.execSQL("DROP TABLE finance_entry")
            connection.execSQL("PRAGMA user_version = 4")
        }

        RealityStore(db.toString()).use { migrated ->
            assertEquals(RealityStore.SCHEMA_VERSION, migrated.schemaVersion())
            val snapshot = migrated.financePersistence.read()
            assertEquals(1L, snapshot.revision)
            assertEquals(1, snapshot.accounts.size)
            assertEquals(77_000L, snapshot.accounts.single().openingBalanceMinor)
            assertEquals(0, snapshot.entries.size)
            assertEquals(77_000L, snapshot.ledgerMinor(accountId))
            assertEquals("ok", migrated.integrityCheck())
        }
    }

    private fun accountCreate(
        accountId: FinanceAccountId,
        openingBalanceMinor: Long = 0L,
    ) = FinanceAccountCreate(
        accountId = accountId,
        displayName = FinanceAccountDisplayName.of("Principal"),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = openingBalanceMinor,
    )

    private fun entryRecord(
        id: String,
        accountId: FinanceAccountId,
        amountMinor: Long,
        kind: FinanceEntryKind,
    ) = FinanceEntryRecord(
        entryId = FinanceEntryId.of(id),
        accountId = accountId,
        amountMinor = amountMinor,
        kind = kind,
        concept = FinanceEntryConcept.of("Supermercado"),
        category = FinanceEntryCategory.of("Alimentos"),
        effectiveAtEpochMillis = 1_000L,
        recordedAtEpochMillis = 2_000L,
        provenanceLabel = "Declarado por usuario",
    )
}
