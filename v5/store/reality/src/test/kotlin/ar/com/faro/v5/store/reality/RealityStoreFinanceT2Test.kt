package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreFinanceT2Test {
    @Test
    fun accountAndOpeningCausePersistAcrossRealFileReopen() {
        val db = Files.createTempDirectory("faro-v5-finance").resolve("reality.db")
        val create = accountCreate(openingBalanceMinor = 125_000L)

        RealityStore(db.toString()).use { store ->
            val created = store.run(TransactionId("tx-finance-account")) { scope ->
                AtomicApplyTransactionDecision.Commit(
                    store.financePersistence.createAccount(create, scope)
                )
            }

            assertEquals(create.accountId, created.accountId)
            assertEquals(125_000L, created.openingBalanceMinor)
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("ok", store.integrityCheck())
        }

        RealityStore(db.toString()).use { reopened ->
            val snapshot = reopened.financePersistence.read()
            assertEquals(1L, snapshot.revision)
            assertEquals(1, snapshot.accounts.size)
            assertEquals(create.accountId, snapshot.accounts.single().accountId)
            assertEquals("Principal", snapshot.accounts.single().displayName.value)
            assertEquals("ARS", snapshot.accounts.single().currency.value)
            assertEquals(FinanceAccountNature.BANK, snapshot.accounts.single().nature)
            assertEquals(125_000L, snapshot.accounts.single().openingBalanceMinor)
            assertEquals("ok", reopened.integrityCheck())
        }

        val connection = BundledSQLiteDriver().open(db.toString())
        try {
            connection.prepare(
                """
                SELECT opening_cause_id, amount_minor
                FROM finance_account_opening
                WHERE account_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, create.accountId.value)
                assertEquals(true, statement.step())
                assertEquals(
                    "finance-opening:" + create.accountId.value,
                    statement.getText(0),
                )
                assertEquals(125_000L, statement.getLong(1))
                assertEquals(false, statement.step())
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun zeroOpeningBalanceCreatesNoSyntheticMoneyCause() {
        val db = Files.createTempDirectory("faro-v5-finance-zero").resolve("reality.db")
        val create = accountCreate(openingBalanceMinor = 0L)

        RealityStore(db.toString()).use { store ->
            store.run(TransactionId("tx-finance-zero")) { scope ->
                store.financePersistence.createAccount(create, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(0L, store.financePersistence.read().accounts.single().openingBalanceMinor)
        }

        val connection = BundledSQLiteDriver().open(db.toString())
        try {
            connection.prepare(
                "SELECT COUNT(*) FROM finance_account_opening"
            ).use { statement ->
                assertEquals(true, statement.step())
                assertEquals(0L, statement.getLong(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun thrownFailureRollsBackAccountOpeningCauseAndFinanceRevision() {
        RealityStore(":memory:").use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("tx-finance-rollback")) { scope ->
                    store.financePersistence.createAccount(
                        accountCreate(openingBalanceMinor = 50_000L),
                        scope,
                    )
                    error("fail after Finance mutation")
                }
            }

            val snapshot = store.financePersistence.read()
            assertEquals(0, snapshot.accounts.size)
            assertEquals(0L, snapshot.revision)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun financeWriteOutsideRealityTransactionIsRejected() {
        RealityStore(":memory:").use { store ->
            val fakeScope = object : RealityTransactionScope {
                override val transactionId = TransactionId("fake")
            }

            assertThrows(IllegalStateException::class.java) {
                store.financePersistence.createAccount(
                    accountCreate(openingBalanceMinor = 1L),
                    fakeScope,
                )
            }

            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0L, store.financePersistence.read().revision)
        }
    }

    @Test
    fun financeDependencyStampReadsRealAggregateRevision() {
        RealityStore(":memory:").use { store ->
            assertEquals(
                DependencyStampValue("revision:0"),
                store.current(
                    FinanceSnapshot.FINANCE_AUTHORITY,
                    FinanceSnapshot.FINANCE_ACCOUNTS_KEY,
                ),
            )

            store.run(TransactionId("tx-finance-stamp")) { scope ->
                store.financePersistence.createAccount(accountCreate(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            assertEquals(
                DependencyStampValue("revision:1"),
                store.current(
                    FinanceSnapshot.FINANCE_AUTHORITY,
                    FinanceSnapshot.FINANCE_ACCOUNTS_KEY,
                ),
            )
        }
    }

    @Test
    fun schemaV3MigratesToV4WithoutLosingExistingRealityInfrastructure() {
        val db = Files.createTempDirectory("faro-v5-finance-migrate").resolve("reality.db")
        seedSchemaV3(db.toString())

        RealityStore(db.toString()).use { store ->
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("Diego", store.read().displayName!!.value)
            assertEquals(7L, store.read().revision)
            assertEquals(
                ar.com.faro.v5.kernel.contracts.ApplyAttemptJournalState.PREPARED,
                store.applyAttemptLookup
                    .find(TransactionId("tx-v3-prepared"))!!
                    .state,
            )
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0L, store.financePersistence.read().revision)

            store.run(TransactionId("tx-finance-after-v3")) { scope ->
                store.financePersistence.createAccount(
                    accountCreate(openingBalanceMinor = 10_000L),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            assertEquals(1, store.financePersistence.read().accounts.size)
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals("ok", store.integrityCheck())
        }
    }

    private fun accountCreate(
        openingBalanceMinor: Long = 0L,
    ) = FinanceAccountCreate(
        accountId = FinanceAccountId.of("account-principal"),
        displayName = FinanceAccountDisplayName.of("Principal"),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = openingBalanceMinor,
    )

    private fun seedSchemaV3(fileName: String) {
        val connection = BundledSQLiteDriver().open(fileName)
        try {
            connection.execSQL(
                """
                CREATE TABLE profile_state (
                    singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                    display_name TEXT NOT NULL DEFAULT '',
                    provenance TEXT NOT NULL DEFAULT '',
                    revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),
                    CHECK(
                        (display_name = '' AND provenance = '')
                        OR
                        (display_name <> '' AND provenance = 'DECLARED_BY_USER')
                    )
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO profile_state(
                    singleton_id,
                    display_name,
                    provenance,
                    revision
                ) VALUES (1, 'Diego', 'DECLARED_BY_USER', 7)
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE apply_attempt (
                    transaction_id TEXT NOT NULL PRIMARY KEY,
                    plan_fingerprint TEXT NOT NULL UNIQUE,
                    state TEXT NOT NULL CHECK(state IN ('PREPARED', 'COMMITTED')),
                    UNIQUE(transaction_id, plan_fingerprint)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO apply_attempt(
                    transaction_id,
                    plan_fingerprint,
                    state
                ) VALUES (
                    'tx-v3-prepared',
                    'plan-v3-prepared',
                    'PREPARED'
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE execution_receipt (
                    receipt_id TEXT NOT NULL PRIMARY KEY,
                    transaction_id TEXT NOT NULL UNIQUE,
                    plan_fingerprint TEXT NOT NULL,
                    FOREIGN KEY(transaction_id, plan_fingerprint)
                        REFERENCES apply_attempt(transaction_id, plan_fingerprint)
                        ON UPDATE RESTRICT
                        ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE reality_outbox (
                    effect_id TEXT NOT NULL PRIMARY KEY,
                    source_transaction_id TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL UNIQUE,
                    payload_format_id TEXT NOT NULL,
                    payload_bytes BLOB NOT NULL,
                    FOREIGN KEY(source_transaction_id)
                        REFERENCES apply_attempt(transaction_id)
                        ON UPDATE RESTRICT
                        ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            connection.execSQL("PRAGMA user_version = 3")
        } finally {
            connection.close()
        }
    }
}
