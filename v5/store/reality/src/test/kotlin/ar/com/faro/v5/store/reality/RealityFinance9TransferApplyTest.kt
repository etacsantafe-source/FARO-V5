package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityFinance9TransferApplyTest {
    @TempDir lateinit var root: Path

    private val from = FinanceAccountId.of("origin")
    private val to = FinanceAccountId.of("destination")
    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val catalog = BuiltInContractCatalog.create()

    private fun transfer(id: String, amount: Long = 35L, warning: Boolean = false) =
        FinanceTransferRecord(
            FinanceTransferId(id), from, to, ars, amount, 900L, 1_000L,
            FinanceEntryConcept.of("Traspaso declarado"), warning,
        )

    private fun account(
        store: RealityStore,
        scope: RealityTransactionScope,
        id: FinanceAccountId,
        currency: FinanceCurrencyCode,
        amount: Long,
    ) {
        store.financePersistence.createAccount(
            FinanceAccountCreate(id, FinanceAccountDisplayName.of(id.value), currency, FinanceAccountNature.CASH, amount),
            scope,
        )
    }

    private fun openActive(file: Path) = RealityStore(
        file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(),
        planningStage8 = true, financeActive9 = true,
    )

    private fun fixture(
        targetCurrency: FinanceCurrencyCode = ars,
        legacy: Boolean = false,
        label: String = "finance-transfer",
    ): Path {
        val file = root.resolve(label + ".db")
        RealityStore(file.toString(), planningStage8 = true).use { store ->
            if (legacy) store.run(TransactionId("legacy-seed")) { scope ->
                account(store, scope, from, ars, 100L)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(db, "transfer-stage", "a".repeat(64), 1_000L)
        }
        openActive(file).use {
            if (!legacy) createConfirmed(it, from, ars, 100L)
            createConfirmed(it, to, targetCurrency, 20L)
        }
        return file
    }

    private fun createConfirmed(store: RealityStore, id: FinanceAccountId, currency: FinanceCurrencyCode, amount: Long) {
        val snapshot = store.financePersistence.read()
        val command = FinanceAccountCreate(
            id, FinanceAccountDisplayName.of(id.value), currency, FinanceAccountNature.CASH, amount,
        )
        val plan = plan(
            FinanceOperations.ACCOUNT_CREATE, FinanceAccountCreatePayload.payload(command),
            DependencyStampSet.of(snapshot.accountsDependencyStamp()), IdempotencyKey("seed-" + id.value),
        )
        val identity = store.applyAttemptIdentities.resolve(plan)
        val issuer = store.finance9ActiveDevGrantIssuer("b".repeat(64), "finance-active", 2L, { true }, { 1_000L })
        val grant = requireNotNull(issuer.issue(
            FinanceOperations.ACCOUNT_CREATE, id.value, identity.planFingerprint,
            IdempotencyKey("grant-seed-" + id.value), amount,
        ))
        store.run(identity.transactionId) { scope ->
            account(store, scope, id, currency, amount)
            val receipt = store.commit(scope, identity, emptyList())
            assertTrue(grant.consumeInsideFinalCommit(scope, identity, receipt))
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    private fun plan(
        operation: OperationId,
        payload: CanonicalPayload,
        stamps: DependencyStampSet,
        key: IdempotencyKey,
    ): PreparedPlan =
        PreparedPlan.of(ApplyPlanNode(
            PlanNodeId("finance"),
            PreparedOperation(requireNotNull(catalog.current(operation)), payload, stamps, key),
            emptySet(),
        ))

    private fun prepared(store: RealityStore, transfer: FinanceTransferRecord): PreparedPlan {
        val snapshot = store.financePersistence.read()
        return plan(
            FinanceOperations.TRANSFER_RECORD, FinanceTransferRecordPayload.payload(transfer),
            DependencyStampSet.of(
                snapshot.accountsDependencyStamp(),
                snapshot.accountDependencyStamp(transfer.fromAccountId),
                snapshot.accountDependencyStamp(transfer.toAccountId),
            ),
            IdempotencyKey("transfer-" + transfer.transferId.value),
        )
    }

    private fun execute(store: RealityStore, plan: PreparedPlan): Pair<ApplyAttemptIdentity, ApplyTransactionExecution> {
        val id = store.applyAttemptIdentities.resolve(plan)
        val bindings = ApplyBindingResolutionGate(listOf(
            FinanceTransferRecordBinding(store.finance9TransferWriteForDevOnly()),
        )).resolve(plan) as ApplyBindingResolution.Resolved
        return id to ApplyTransactionExecutor(store, store).execute(plan, id, bindings)
    }

    private fun linkedEffects(file: Path): List<Pair<String, String>> =
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.prepare("SELECT debit_entry_id,credit_entry_id FROM finance_transfer").use { st ->
                buildList {
                    while (st.step()) add(st.getText(0) to st.getText(1))
                }
            }
        }

    @Test fun persistsBothLinkedEffectsAndOneReceiptAcrossReopen() {
        val file = fixture()
        val transfer = transfer("success")
        val receipt = openActive(file).use { store ->
            val plan = prepared(store, transfer)
            val gate = ApplyPreflightGate(
                catalog, store, listOf(FinanceTransferRecordRevalidation(
                    FinanceSnapshotReadApi(store.financePersistence::read),
                )),
            )
            assertTrue(gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)) is ApplyPreflightResult.Accepted)
            val result = execute(store, plan).second as ApplyTransactionExecution.Completed
            val replay = ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup).resolve(plan)
            assertEquals(result.receipt, (replay as ApplyRetryResolution.AlreadyCommitted).receipt)
            result.receipt
        }
        openActive(file).use { store ->
            val snapshot = store.financePersistence.read()
            assertEquals(65L, snapshot.ledgerMinor(from))
            assertEquals(55L, snapshot.ledgerMinor(to))
            assertEquals(setOf(FinanceEntryKind.EXPENSE, FinanceEntryKind.INCOME), snapshot.entries.map { it.kind }.toSet())
            assertEquals(2, snapshot.entries.size)
            assertEquals(receipt, store.executionReceiptLookup.find(receipt.transactionId))
        }
        assertEquals(listOf(transfer.entry(FinanceEntryKind.EXPENSE).entryId.value to
            transfer.entry(FinanceEntryKind.INCOME).entryId.value), linkedEffects(file))
    }

    @Test fun secondEffectFailureRollsBackDebitAndReceipt() {
        val file = fixture()
        val transfer = transfer("collision")
        val credit = transfer.entry(FinanceEntryKind.INCOME)
        openActive(file).use { store ->
            store.run(TransactionId("seed-existing-credit")) { scope ->
                store.financePersistence.recordEntry(credit, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val before = store.financePersistence.read()
            val (id, result) = execute(store, prepared(store, transfer))
            assertFalse(result is ApplyTransactionExecution.Completed)
            assertEquals(before, store.financePersistence.read())
            assertEquals(listOf(credit.entryId), before.entries.map { it.entryId })
            assertNull(store.executionReceiptLookup.find(id.transactionId))
        }
        assertTrue(linkedEffects(file).isEmpty())
    }

    @Test fun rejectsDifferentCurrencyAtPreflightAndInReality() {
        val file = fixture(targetCurrency = usd)
        openActive(file).use { store ->
            val plan = prepared(store, transfer("wrong-currency"))
            val gate = ApplyPreflightGate(
                catalog, store, listOf(FinanceTransferRecordRevalidation(
                    FinanceSnapshotReadApi(store.financePersistence::read),
                )),
            )
            assertTrue(gate.validate(plan, PlanAuthorizationRef(plan.planFingerprint)) is ApplyPreflightResult.Rejected)
            val (id, result) = execute(store, plan)
            assertFalse(result is ApplyTransactionExecution.Completed)
            assertNull(store.executionReceiptLookup.find(id.transactionId))
            assertTrue(store.financePersistence.read().entries.isEmpty())
        }
    }

    @Test fun requiresAcknowledgementWhenSourceOpeningIsUnknownOrBalanceInsufficient() {
        val legacy = fixture(legacy = true)
        openActive(legacy).use { store ->
            val (unknown, denied) = execute(store, prepared(store, transfer("unknown-source")))
            assertFalse(denied is ApplyTransactionExecution.Completed)
            assertNull(store.executionReceiptLookup.find(unknown.transactionId))
            val accepted = execute(store, prepared(store, transfer("unknown-accepted", warning = true))).second
            assertTrue(accepted is ApplyTransactionExecution.Completed)
        }
        val known = fixture(label = "confirmed-opening")
        openActive(known).use { store ->
            val (insufficient, denied) = execute(store, prepared(store, transfer("insufficient", 120L)))
            assertFalse(denied is ApplyTransactionExecution.Completed)
            assertNull(store.executionReceiptLookup.find(insufficient.transactionId))
            assertTrue(store.financePersistence.read().entries.isEmpty())
            val accepted = execute(store, prepared(store, transfer("accepted-negative", 120L, warning = true))).second
            assertTrue(accepted is ApplyTransactionExecution.Completed)
            assertEquals(-20L, store.financePersistence.read().ledgerMinor(from))
        }
    }

    @Test fun rejectsOverflowAndNonCanonicalPayload() {
        val file = fixture()
        val overflows = transfer("overflow", Long.MAX_VALUE, warning = true)
        val wire = FinanceTransferRecordPayload.payload(overflows)
        assertEquals(overflows, FinanceTransferRecordPayload.decode(wire))
        assertNull(FinanceTransferRecordPayload.decode(CanonicalPayload.fromEncoded(
            wire.formatId, wire.copyBytes() + byteArrayOf(0),
        )))
        assertThrows(IllegalArgumentException::class.java) { overflows.copy(amountMinor = 0L) }
        assertThrows(IllegalArgumentException::class.java) { overflows.copy(toAccountId = from) }
        openActive(file).use { store ->
            val (id, result) = execute(store, prepared(store, overflows))
            assertFalse(result is ApplyTransactionExecution.Completed)
            assertNull(store.executionReceiptLookup.find(id.transactionId))
            assertTrue(store.financePersistence.read().entries.isEmpty())
        }
    }
}
