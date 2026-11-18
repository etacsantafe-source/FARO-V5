package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityFinance9BudgetTest {
    @TempDir lateinit var root: Path

    private val catalog = BuiltInContractCatalog.create()
    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val arsAccount = FinanceAccountId.of("cash")
    private val arsDestination = FinanceAccountId.of("other-cash")
    private val usdAccount = FinanceAccountId.of("dollars")
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val month = YearMonth.of(2026, 11)
    private val category = FinanceEntryCategory.of("Cuotas")
    private val key = FinanceBudgetKey(month, category, ars)
    private val monthStart = Instant.parse("2026-11-01T03:00:00Z").toEpochMilli()

    private fun fixture(): RealityStore {
        val file = root.resolve("budget.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(db, "budget-stage", "a".repeat(64), 1000)
        }
        return RealityStore(
            file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(),
            planningStage8 = true, financeActive9 = true,
        ).apply {
            run(TransactionId("seed-accounts")) { scope ->
                val accounts = listOf(arsAccount to ars, arsDestination to ars, usdAccount to usd)
                for ((id, currency) in accounts) {
                    financePersistence.createAccount(FinanceAccountCreate(
                        id, FinanceAccountDisplayName.of(id.value), currency, FinanceAccountNature.CASH, 1000,
                    ), scope)
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
    }

    private fun expense(
        id: String, amount: Long, account: FinanceAccountId = arsAccount,
        effective: Long = monthStart, expenseCategory: FinanceEntryCategory = category,
    ) = FinanceEntryRecord(
        FinanceEntryId.of(id), account, amount, FinanceEntryKind.EXPENSE,
        FinanceEntryConcept.of("Ejemplo de gasto"), expenseCategory, effective, monthStart,
    )

    private fun change(
        store: RealityStore, limit: Long?, budgetKey: FinanceBudgetKey = key,
        expected: Long = store.financePersistence.read().revision,
    ) = FinanceBudgetChange(budgetKey, expected, limit, monthStart)

    private fun prepare(command: FinanceBudgetChange, key: String): PreparedPlan {
        val operation = if (command.limitMinor == null) FinanceOperations.BUDGET_CLEAR else FinanceOperations.BUDGET_SET
        return PreparedPlan.of(ApplyPlanNode(
            PlanNodeId("budget"),
            PreparedOperation(requireNotNull(catalog.current(operation)),
                FinanceBudgetChangePayload.payload(command), DependencyStampSet.empty(), IdempotencyKey(key)),
            emptySet(),
        ))
    }

    private fun apply(store: RealityStore, plan: PreparedPlan): ApplyTransactionExecution {
        val id = store.applyAttemptIdentities.resolve(plan)
        val gate = ApplyBindingResolutionGate(FinanceBudgetBinding.all(store.finance9BudgetWriteForDevOnly()))
        return ApplyTransactionExecutor(store, store).execute(plan, id, gate.resolve(plan) as ApplyBindingResolution.Resolved)
    }

    private fun observation(store: RealityStore, lookupKey: FinanceBudgetKey = key) =
        store.finance9ActiveDevBudgetRead { true }.read(FinanceBudgetQuery(lookupKey, zone))

    @Test fun keepsZeroAbsentCurrencyAndRevisionDistinct() {
        fixture().use { store ->
            val before = store.financePersistence.read().revision
            val usdKey = key.copy(currency = usd)
            val zero = prepare(change(store, 0), "set-zero")
            val receipt = (apply(store, zero) as ApplyTransactionExecution.Completed).receipt
            assertEquals(0L, observation(store).limitMinor)
            assertNull(observation(store, usdKey).limitMinor)
            assertEquals(before + 1, observation(store).revisionOfLimit)
            assertTrue(apply(store, prepare(change(store, 300, usdKey), "set-usd")) is ApplyTransactionExecution.Completed)
            assertEquals(300L, observation(store, usdKey).limitMinor)
            val clear = prepare(change(store, null), "clear-ars")
            assertTrue(apply(store, clear) is ApplyTransactionExecution.Completed)
            assertNull(observation(store).limitMinor)
            assertFalse(apply(store, prepare(change(store, null), "clear-absent")) is ApplyTransactionExecution.Completed)
            assertTrue(apply(store, prepare(change(store, 100), "recreate-ars")) is ApplyTransactionExecution.Completed)
            assertTrue(requireNotNull(observation(store).revisionOfLimit) > before + 1)
            assertFalse(apply(store, prepare(change(store, 450, expected = before + 1), "stale-ars")) is ApplyTransactionExecution.Completed)
            assertEquals(100L, observation(store).limitMinor)
            val retry = ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup).resolve(zero)
            assertEquals(receipt, (retry as ApplyRetryResolution.AlreadyCommitted).receipt)
            assertTrue(store.financePersistence.read().entries.isEmpty())
        }
    }

    @Test fun usesCivilMonthAndExcludesOnlyLinkedInternalTransfers() {
        fixture().use { store ->
            val transferCategory = FinanceEntryCategory.of("Transferencia")
            val transferKey = key.copy(category = transferCategory)
            val transfer = FinanceTransferRecord(
                FinanceTransferId("internal"), arsAccount, arsDestination, ars, 200,
                monthStart, monthStart, FinanceEntryConcept.of("Traspaso"), true,
            )
            val transferWriter = store.finance9TransferWriteForDevOnly()
            store.run(TransactionId("seed-ledger")) { scope ->
                transferWriter.record(transfer, scope)
                store.financePersistence.recordEntry(expense("ars-real", 65, expenseCategory = transferCategory), scope)
                store.financePersistence.recordEntry(expense("usd-real", 30, usdAccount, expenseCategory = transferCategory), scope)
                store.financePersistence.recordEntry(
                    expense("previous-month", 120, effective = monthStart - 1, expenseCategory = transferCategory), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(65L, observation(store, transferKey).actualSpentMinor)
            assertEquals(30L, observation(store, transferKey.copy(currency = usd)).actualSpentMinor)
            assertEquals(120L, observation(store, transferKey.copy(period = month.minusMonths(1))).actualSpentMinor)
        }
    }

    @Test fun reportsUncategorizedResidualAndUsesOnlyPostedPaymentsAsActual() {
        fixture().use { store ->
            val planId = FinanceCommitmentId("planned")
            val declare = store.finance9CommitmentWriteForDevOnly()
            val pay = store.finance9CommitmentPaymentForDevOnly()
            val budget = prepare(change(store, 80), "reserve")
            assertTrue(apply(store, budget) is ApplyTransactionExecution.Completed)
            store.run(TransactionId("seed-plan")) { scope ->
                declare.create(FinanceCommitmentCreate.PlanItem(
                    planId, null, null, month, 1, month.atDay(10), ars, 100, monthStart,
                ), scope)
                pay.record(FinanceCommitmentPayment(planId, null, ars, 0, expense("part", 30)), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val partial = observation(store)
            assertEquals(FinanceBudgetCoverage.PARTIAL, partial.coverage)
            assertEquals(30L, partial.actualSpentMinor)
            assertEquals(70L, partial.uncategorizedCommitmentsMinor)
            assertNull(partial.estimatedAvailableMinor)
            assertEquals(FinanceBudgetCoverage.READY, observation(store, key.copy(currency = usd)).coverage)
            store.run(TransactionId("seed-rest")) { scope ->
                pay.record(FinanceCommitmentPayment(planId, null, ars, 30, expense("rest", 70)), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val settled = observation(store)
            assertEquals(FinanceBudgetCoverage.READY, settled.coverage)
            assertEquals(100L, settled.actualSpentMinor)
            assertEquals(0L, settled.uncategorizedCommitmentsMinor)
            assertEquals(-20L, settled.estimatedAvailableMinor)
        }
    }

    @Test fun deniesBeforeAfterAndHidesOverflow() {
        fixture().use { store ->
            val query = FinanceBudgetQuery(key, zone)
            val denied = FinanceBudgetObservation(FinanceBudgetCoverage.DENIED)
            assertEquals(denied, store.finance9ActiveDevBudgetRead { false }.read(query))
            var calls = 0
            val revoked = store.finance9ActiveDevBudgetRead { ++calls == 1 }.read(query)
            assertEquals(denied, revoked)
            store.run(TransactionId("overflow")) { scope ->
                store.financePersistence.recordEntry(expense("max", Long.MAX_VALUE), scope)
                store.financePersistence.recordEntry(expense("overflow", 1, arsDestination), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(FinanceBudgetObservation(FinanceBudgetCoverage.UNAVAILABLE), observation(store))
        }
    }
}
